#
# /odm/etc/ueventd.rc — device-node permissions stock applies from init rc
# builtins that race node creation on this tree.
#
# Stock ZUI vendor/etc/init/hw/init.project.rc ("on early-init"):
#     insmod /vendor/etc/tn_keyboard.ko
#     chown system system /dev/ttyS3
#     chmod 0666 /dev/ttyS3
#
# The one-shot init chown/chmod can run before ueventd creates the node,
# leaving it at the default 0600 root:root. Apply the stock owner/mode in
# ueventd when the UART node is created or recreated. Keep the existing
# tinno_keyboard_device SELinux label and HAL access rules.
#
# system/etc/ueventd.rc (AOSP rootdir) already imports /odm/etc/ueventd.rc;
# this is the only producer of that path.
#

# (Product-config .mk: $(call my-dir) is empty here - use the literal tree
# path, same convention as tb375fc-rootdir-packages.mk.)

PRODUCT_COPY_FILES += \
    device/lenovo/TB375FC/rootdir/etc/ueventd.odm.rc:$(TARGET_COPY_OUT_ODM)/etc/ueventd.rc
