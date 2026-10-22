# rootdir wiring:
#   - fstab.* prebuilt_etc Soong modules declared in rootdir/Android.bp
#   - init scripts owned by the device tree, via PRODUCT_COPY_FILES (avoids
#     Soong module name clashes with AOSP HAL packages)
#
# Only scripts that differ from stock or have no stock counterpart live here.
# Unmodified stock scripts ship from proprietary-files.txt. Do not copy a
# stock script here unchanged: this file is inherited before the vendor
# makefile and the first PRODUCT_COPY_FILES entry for a path wins, so the copy
# would silently shadow later stock updates.

PRODUCT_PACKAGES += \
    fstab.mt6897 \
    fstab.mt8792

PRODUCT_COPY_FILES += \
    device/lenovo/TB375FC/rootdir/etc/init/android.hardware.wifi.supplicant-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/android.hardware.wifi.supplicant-service.rc \
    device/lenovo/TB375FC/rootdir/etc/init/dynamicdata.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/dynamicdata.rc \
    device/lenovo/TB375FC/rootdir/etc/init/init.fpsgo.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.fpsgo.rc \
    device/lenovo/TB375FC/rootdir/etc/init/vendor.lenovo.hardware.factory-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.lenovo.hardware.factory-service.rc \
    device/lenovo/TB375FC/rootdir/etc/init/vendor.lenovo.hardware.keyboard-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.lenovo.hardware.keyboard-service.rc \
    device/lenovo/TB375FC/rootdir/etc/init/vendor.mediatek.hardware.tinno_keyboard@1.0-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.mediatek.hardware.tinno_keyboard@1.0-service.rc \
    device/lenovo/TB375FC/rootdir/etc/init/init.tb375fc-lenovocust.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.tb375fc-lenovocust.rc \
    device/lenovo/TB375FC/rootdir/etc/init/init.tb375fc-charging.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.tb375fc-charging.rc
