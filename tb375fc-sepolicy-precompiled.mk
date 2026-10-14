#
# Override Soong's install rule for /odm/etc/selinux/precompiled_sepolicy.*
# sha256 files with empty content, breaking the hash match. Forces init to
# fall back to runtime compilation of the maintained vendor_sepolicy.cil
# (which has the MTK domain definitions) from /vendor.
#
# With BOARD_USES_ODMIMAGE := true, Soong's precompiled_sepolicy_prebuilts
# defaults install precompiled_sepolicy + sha256 files to /odm/etc/selinux/.
# precompiled_sepolicy is built from:
#   plat_sepolicy.cil  (LOS-built from system/sepolicy/*)
# + vendor_sepolicy.cil (LOS-built from system/sepolicy/vendor/* + our
#                        device/lenovo/TB375FC/sepolicy/{vendor,vendor_mtk_prebuilt}/)
# + mapping/<vendor_policy_version>.cil (LOS-built)
#
# Before the vendor_mtk_prebuilt import, LOS policy lacked ~250 MTK domains
# (mtk_hal_power, mtk_hal_pq, mtk_hal_mmlpq, mtk_hal_camera, mtk_hal_c2, etc.).
# Those came from stock vendor_sepolicy.cil and are now maintained in
# device/lenovo/TB375FC/sepolicy/vendor_mtk_prebuilt/.
#
# init's sepolicy loading order (system/core/init/selinux.cpp):
#   1. Try /odm/etc/selinux/precompiled_sepolicy first (preferred when ODM
#      partition exists). Verify the sha256 inside .../precompiled_sepolicy.plat_sepolicy_and_mapping.sha256
#      matches /system/etc/selinux/plat_sepolicy_and_mapping.sha256. Soong
#      builds both files in the same step from the same plat_sepolicy.cil
#      so they match -> init loads /odm's precompiled. During bring-up, that
#      policy had NO MTK types -> type_transitions for vendor services never fired ->
#      they stayed in scontext=u:r:init:s0.
#
# Bring-up symptom: ~20 "SELinux: Context u:object_r:X:s0 is not valid (left unmapped)"
# warnings in dmesg for MTK types (nvdata_file, camera_dpe_device, etc.).
# Downstream: kernel modules like mtk_ioctl_powerhal check the caller's
# SELinux domain inside the ioctl handler. mtkpower-service running as
# init:s0 (instead of mtk_hal_power:s0) fails the check, ioctl returns -EACCES,
# libpowerhal doesn't check the retval, dereferences NULL handle, SIGSEGV
# at offset init()+1048.
#
# Fix: override Soong's install rule for the sha256 files only. Replace with
# empty content so init's hash check returns "actual_id is empty" (treated as
# mismatch -> error -> fall through to runtime compile). The precompiled file
# itself can stay, just won't be used.
#
# Runtime compile includes:
#   /system/etc/selinux/plat_sepolicy.cil (LOS-built)
#   /system/etc/selinux/mapping/<vendor_policy_version>.cil (LOS-built)
#   /vendor/etc/selinux/vendor_sepolicy.cil (LOS-built - has MTK types)
#   /vendor/etc/selinux/plat_pub_versioned.cil
# secilc compiles them into a policy with all MTK types and type_transitions.
# mtkpower transitions to mtk_hal_power, ioctl succeeds, boot proceeds.
#
# Bring-up cost: ~1s extra boot time for secilc compile (libsepol parsing ~1.5 MB of
# CIL on Cortex-A55 at 1.8 GHz).

LOCAL_PATH := $(call my-dir)

# Override Soong's install rule. When Make sees two rules for the same target,
# the later rule wins (with a harmless "overriding commands for target" warning).
# Our Android.mk include happens after Soong's installs-*.mk include in
# build/make/core/Makefile, so our rule wins.
$(TARGET_OUT_ODM)/etc/selinux/precompiled_sepolicy.plat_sepolicy_and_mapping.sha256:
	@echo "SEPOL_OVERRIDE: writing empty $@ (forces init runtime compilation of maintained LOS split policy)"
	@mkdir -p $(dir $@)
	$(hide) : > $@

$(TARGET_OUT_ODM)/etc/selinux/precompiled_sepolicy.system_ext_sepolicy_and_mapping.sha256:
	@echo "SEPOL_OVERRIDE: writing empty $@"
	@mkdir -p $(dir $@)
	$(hide) : > $@

$(TARGET_OUT_ODM)/etc/selinux/precompiled_sepolicy.product_sepolicy_and_mapping.sha256:
	@echo "SEPOL_OVERRIDE: writing empty $@"
	@mkdir -p $(dir $@)
	$(hide) : > $@
