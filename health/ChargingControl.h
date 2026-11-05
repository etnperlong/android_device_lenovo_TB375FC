/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lineage/health/BnChargingControl.h>
#include <android-base/unique_fd.h>

#include <cstdint>
#include <mutex>

namespace aidl::vendor::lineage::health {

/**
 * Charging control on the stock Wingtech charger driver (mtk_charger_framework.ko and
 * lenovo_charge_sysfs.ko), the same nodes stock's Lenovo battery HAL drives:
 *
 * - limit: /sys/class/power_supply/battery/protection_setting_eu, the driver's charge limit that
 *   stock's "Maximum charge limit" uses (setMaxBatteryChargingLevel). 100000 + resume level *
 *   1000 + stop level turns it on; the charger thread then stops charging once the battery
 *   reaches the stop level and resumes at or below the resume level. Any other value turns it
 *   off. While stopped, the adapter keeps powering the tablet.
 * - toggle: /sys/class/wt-battery/charging_enabled, 0 stops charging the battery with the adapter
 *   still powering the tablet (stock's setBatteryChargeDisabled, used for bypass charging), 1
 *   charges again. The charger thread keeps charging off while it is 0.
 * - deadline: the framework's time-based modes (automatic, from the next alarm, and custom
 *   schedule). The driver has no deadline, so this holds the battery at a fixed level through
 *   the limit node and lifts the limit a fixed time before the deadline, on an alarm timer that
 *   wakes the tablet. Without it, the framework would hand these modes to its limit provider,
 *   which does not implement them.
 *
 * The framework drives either the limit or the deadline, depending on the mode, so setting one
 * cancels the other. The toggle is independent of both in the driver, so the LineageOS charge
 * limit and DeviceParts' bypass charging do not undo each other.
 */
class ChargingControl : public BnChargingControl {
  public:
    // Starts the deadline timer thread, which uses this object for the life of the process.
    ChargingControl();

    ndk::ScopedAStatus getChargingEnabled(bool* _aidl_return) override;
    ndk::ScopedAStatus setChargingEnabled(bool enabled) override;
    ndk::ScopedAStatus setChargingDeadline(int64_t deadline) override;
    ndk::ScopedAStatus getSupportedMode(int* _aidl_return) override;
    ndk::ScopedAStatus getChargingDeadline(int64_t* _aidl_return) override;
    ndk::ScopedAStatus getChargingLimit(ChargingLimitInfo* _aidl_return) override;
    ndk::ScopedAStatus setChargingLimit(const ChargingLimitInfo& limit) override;
    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;

  private:
    void timerLoop();
    void cancelDeadlineLocked();
    bool clearLimitLocked();

    std::mutex mLock;
    android::base::unique_fd mTimerFd;
    // CLOCK_BOOTTIME nanoseconds, guarded by mLock. mDeadlineNs is 0 without a deadline;
    // mReleaseNs is when the hold is lifted, 0 if no hold is pending.
    int64_t mDeadlineNs = 0;
    int64_t mReleaseNs = 0;
};

}  // namespace aidl::vendor::lineage::health
