/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.health-service.tb375fc"

#include "ChargingControl.h"

#include <aidl/vendor/lineage/health/ChargingControlSupportedMode.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <sys/epoll.h>
#include <sys/timerfd.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <string>
#include <thread>

namespace aidl::vendor::lineage::health {

namespace {

constexpr char kChargingEnabledPath[] = "/sys/class/wt-battery/charging_enabled";
constexpr char kLimitPath[] = "/sys/class/power_supply/battery/protection_setting_eu";

// The driver takes a limit only in [100000, 199100]: 100000 + resume level * 1000 + stop level.
constexpr int kLimitBase = 100'000;
constexpr int kLimitMax = 199'100;
// What stock writes for no limit (stop at 100, resume at 95): outside the range, so off.
constexpr int kLimitOff = 95'100;
// Stock resumes charging at 95% of the limit (config_recharging_level_percent): 80 -> 76.
constexpr int kResumePercent = 95;

// Until the deadline is near, the battery is held where the framework's own time-based provider
// waits (Toggle's CHARGE_CTRL_MIN_LEVEL).
constexpr int kDeadlineHoldLevel = 80;
// The limit is lifted this long before the deadline: a margin for the last 24% (from the 76%
// resume level) of the ~10,200 mAh battery, the slow constant-voltage end of the charge
// included, while the adapter also powers the tablet.
constexpr int64_t kDeadlineLeadSeconds = 2 * 60 * 60;
// Longer deadlines are held as this one; keeps the nanosecond arithmetic in range.
constexpr int64_t kDeadlineMaxSeconds = 365 * 24 * 60 * 60;
constexpr int64_t kNsPerSecond = 1'000'000'000;

ndk::ScopedAStatus unsupported() {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

bool readInt(const char* path, int* value) {
    std::string content;
    if (!android::base::ReadFileToString(path, &content)) {
        PLOG(ERROR) << "Failed to read " << path;
        return false;
    }
    if (!android::base::ParseInt(android::base::Trim(content), value)) {
        LOG(ERROR) << "Unexpected value in " << path << ": " << content;
        return false;
    }
    return true;
}

bool writeInt(const char* path, int value) {
    if (!android::base::WriteStringToFile(std::to_string(value), path)) {
        PLOG(ERROR) << "Failed to write " << value << " to " << path;
        return false;
    }
    return true;
}

bool isLimitOn(int value) {
    return value >= kLimitBase && value <= kLimitMax;
}

// The driver's value for stopping at stopLevel and resuming at 95% of it, as stock does.
int limitValue(int stopLevel) {
    const int resume = stopLevel * kResumePercent / 100;
    return kLimitBase + resume * 1000 + stopLevel;
}

int64_t bootTimeNs() {
    timespec now;
    clock_gettime(CLOCK_BOOTTIME, &now);
    return now.tv_sec * kNsPerSecond + now.tv_nsec;
}

// Arms timerFd to expire at whenNs (CLOCK_BOOTTIME), or disarms it if whenNs is 0.
bool setTimer(int timerFd, int64_t whenNs) {
    itimerspec spec = {};
    spec.it_value.tv_sec = whenNs / kNsPerSecond;
    spec.it_value.tv_nsec = whenNs % kNsPerSecond;
    if (timerfd_settime(timerFd, TFD_TIMER_ABSTIME, &spec, nullptr) != 0) {
        PLOG(ERROR) << "Failed to set the deadline timer";
        return false;
    }
    return true;
}

}  // namespace

ChargingControl::ChargingControl() {
    // CLOCK_BOOTTIME_ALARM wakes the tablet when the limit is to be lifted (CAP_WAKE_ALARM);
    // without it, the timer still counts suspend but fires only at the next wakeup.
    mTimerFd.reset(timerfd_create(CLOCK_BOOTTIME_ALARM, TFD_NONBLOCK | TFD_CLOEXEC));
    if (mTimerFd < 0) {
        PLOG(ERROR) << "No wakeup alarm for the charging deadline";
        mTimerFd.reset(timerfd_create(CLOCK_BOOTTIME, TFD_NONBLOCK | TFD_CLOEXEC));
    }
    PCHECK(mTimerFd >= 0) << "Failed to create the deadline timer";
    std::thread(&ChargingControl::timerLoop, this).detach();
}

void ChargingControl::timerLoop() {
    android::base::unique_fd epollFd(epoll_create1(EPOLL_CLOEXEC));
    PCHECK(epollFd >= 0) << "Failed to create the deadline epoll";
    // EPOLLWAKEUP (CAP_BLOCK_SUSPEND) keeps the tablet awake from the alarm until the limit has
    // been lifted and this waits again.
    epoll_event event = {};
    event.events = EPOLLIN | EPOLLWAKEUP;
    PCHECK(epoll_ctl(epollFd, EPOLL_CTL_ADD, mTimerFd, &event) == 0)
            << "Failed to watch the deadline timer";

    while (true) {
        epoll_event ready;
        if (TEMP_FAILURE_RETRY(epoll_wait(epollFd, &ready, 1, -1)) < 0) {
            PLOG(ERROR) << "Failed to wait for the deadline timer";
            continue;
        }
        uint64_t expirations;
        // Fails with EAGAIN if the timer was set again since it expired.
        if (TEMP_FAILURE_RETRY(read(mTimerFd, &expirations, sizeof(expirations))) < 0) {
            continue;
        }

        std::lock_guard lock(mLock);
        // A deadline set again since the timer expired has its own release time.
        if (mReleaseNs == 0 || bootTimeNs() < mReleaseNs) {
            continue;
        }
        mReleaseNs = 0;
        LOG(INFO) << "Charging deadline in "
                  << std::max<int64_t>(0, (mDeadlineNs - bootTimeNs()) / kNsPerSecond)
                  << " s, charging to full";
        clearLimitLocked();
    }
}

void ChargingControl::cancelDeadlineLocked() {
    if (mReleaseNs != 0) {
        setTimer(mTimerFd, 0);
    }
    mDeadlineNs = 0;
    mReleaseNs = 0;
}

// Turns the driver's limit off if it is on, whichever mode set it.
bool ChargingControl::clearLimitLocked() {
    int value;
    if (!readInt(kLimitPath, &value)) {
        return false;
    }
    return !isLimitOn(value) || writeInt(kLimitPath, kLimitOff);
}

ndk::ScopedAStatus ChargingControl::getChargingEnabled(bool* _aidl_return) {
    int value;
    if (!readInt(kChargingEnabledPath, &value)) {
        return unsupported();
    }
    *_aidl_return = value != 0;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::setChargingEnabled(bool enabled) {
    if (!writeInt(kChargingEnabledPath, enabled ? 1 : 0)) {
        return unsupported();
    }
    LOG(INFO) << "Charging " << (enabled ? "enabled" : "disabled");
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::setChargingDeadline(int64_t deadline) {
    std::lock_guard lock(mLock);
    cancelDeadlineLocked();

    // The framework sets -1 when the charger is unplugged, charging control is turned off or
    // the mode changes; any limit on the node is then a leftover of this or the limit mode.
    if (deadline <= 0) {
        LOG(INFO) << "Charging deadline cleared";
        return clearLimitLocked() ? ndk::ScopedAStatus::ok() : unsupported();
    }

    deadline = std::min(deadline, kDeadlineMaxSeconds);
    const int64_t now = bootTimeNs();
    mDeadlineNs = now + deadline * kNsPerSecond;
    if (deadline <= kDeadlineLeadSeconds) {
        LOG(INFO) << "Charging deadline in " << deadline << " s, charging to full";
        return clearLimitLocked() ? ndk::ScopedAStatus::ok() : unsupported();
    }

    const int value = limitValue(kDeadlineHoldLevel);
    if (!writeInt(kLimitPath, value)) {
        mDeadlineNs = 0;
        return unsupported();
    }
    mReleaseNs = mDeadlineNs - kDeadlineLeadSeconds * kNsPerSecond;
    if (!setTimer(mTimerFd, mReleaseNs)) {
        // Rather charge to full now than hold the battery past the deadline.
        mDeadlineNs = 0;
        mReleaseNs = 0;
        clearLimitLocked();
        return unsupported();
    }
    LOG(INFO) << "Charging deadline in " << deadline << " s: holding at " << kDeadlineHoldLevel
              << "% (" << value << ") for " << deadline - kDeadlineLeadSeconds << " s";
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::getChargingDeadline(int64_t* _aidl_return) {
    std::lock_guard lock(mLock);
    *_aidl_return = mDeadlineNs == 0
                            ? -1
                            : std::max<int64_t>(0, (mDeadlineNs - bootTimeNs()) / kNsPerSecond);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::getSupportedMode(int* _aidl_return) {
    *_aidl_return = static_cast<int>(ChargingControlSupportedMode::TOGGLE) |
                    static_cast<int>(ChargingControlSupportedMode::BYPASS) |
                    static_cast<int>(ChargingControlSupportedMode::DEADLINE) |
                    static_cast<int>(ChargingControlSupportedMode::LIMIT);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::getChargingLimit(ChargingLimitInfo* _aidl_return) {
    std::lock_guard lock(mLock);
    int value;
    if (!readInt(kLimitPath, &value)) {
        return unsupported();
    }
    if (isLimitOn(value)) {
        _aidl_return->min = value / 1000 - 100;
        _aidl_return->max = value % 1000;
    } else {
        _aidl_return->min = 0;
        _aidl_return->max = 100;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::setChargingLimit(const ChargingLimitInfo& limit) {
    std::lock_guard lock(mLock);
    cancelDeadlineLocked();

    // With bypass supported, the framework resumes one percent below the limit (limit.min);
    // the resume level follows stock instead, so the battery is not topped up for every
    // percent it loses.
    int value;
    if (limit.max >= 100) {
        value = kLimitOff;
    } else if (limit.max >= 2) {
        value = limitValue(limit.max);
    } else {
        LOG(ERROR) << "Unsupported charging limit " << limit.max;
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    if (!writeInt(kLimitPath, value)) {
        return unsupported();
    }
    LOG(INFO) << "Charging limit " << limit.max << "% (" << value << ")";
    return ndk::ScopedAStatus::ok();
}

binder_status_t ChargingControl::dump(int fd, const char** /* args */, uint32_t /* numArgs */) {
    int enabled = -1;
    int limit = -1;
    readInt(kChargingEnabledPath, &enabled);
    readInt(kLimitPath, &limit);
    dprintf(fd, "%s: %d\n%s: %d\n", kChargingEnabledPath, enabled, kLimitPath, limit);

    std::lock_guard lock(mLock);
    const int64_t now = bootTimeNs();
    if (mDeadlineNs == 0) {
        dprintf(fd, "Charging deadline: none\n");
    } else {
        dprintf(fd, "Charging deadline: in %lld s\n",
                static_cast<long long>((mDeadlineNs - now) / kNsPerSecond));
    }
    if (mReleaseNs != 0) {
        dprintf(fd, "Holding at %d%% until: in %lld s\n", kDeadlineHoldLevel,
                static_cast<long long>((mReleaseNs - now) / kNsPerSecond));
    }
    return STATUS_OK;
}

}  // namespace aidl::vendor::lineage::health
