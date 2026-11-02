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

#include <cstdio>
#include <string>

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

}  // namespace

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

ndk::ScopedAStatus ChargingControl::setChargingDeadline(int64_t /* deadline */) {
    return unsupported();
}

ndk::ScopedAStatus ChargingControl::getChargingDeadline(int64_t* /* _aidl_return */) {
    return unsupported();
}

ndk::ScopedAStatus ChargingControl::getSupportedMode(int* _aidl_return) {
    *_aidl_return = static_cast<int>(ChargingControlSupportedMode::TOGGLE) |
                    static_cast<int>(ChargingControlSupportedMode::BYPASS) |
                    static_cast<int>(ChargingControlSupportedMode::LIMIT);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::getChargingLimit(ChargingLimitInfo* _aidl_return) {
    int value;
    if (!readInt(kLimitPath, &value)) {
        return unsupported();
    }
    if (value >= kLimitBase && value <= kLimitMax) {
        _aidl_return->min = value / 1000 - 100;
        _aidl_return->max = value % 1000;
    } else {
        _aidl_return->min = 0;
        _aidl_return->max = 100;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::setChargingLimit(const ChargingLimitInfo& limit) {
    // With bypass supported, the framework resumes one percent below the limit (limit.min);
    // the resume level follows stock instead, so the battery is not topped up for every
    // percent it loses.
    int value;
    if (limit.max >= 100) {
        value = kLimitOff;
    } else if (limit.max >= 2) {
        const int resume = limit.max * kResumePercent / 100;
        value = kLimitBase + resume * 1000 + limit.max;
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
    return STATUS_OK;
}

}  // namespace aidl::vendor::lineage::health
