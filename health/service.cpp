/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.health-service.tb375fc"

#include "ChargingControl.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include <cstdlib>
#include <string>

using aidl::vendor::lineage::health::ChargingControl;

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(0);

    const auto chargingControl = ndk::SharedRefBase::make<ChargingControl>();
    const std::string instance = std::string(ChargingControl::descriptor) + "/default";
    const binder_status_t status =
            AServiceManager_addService(chargingControl->asBinder().get(), instance.c_str());
    CHECK_EQ(status, STATUS_OK);

    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // should not reach
}
