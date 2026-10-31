#
# Stock TB375FC vendor.img keeps the bulk of vendor libraries in
# /vendor/lib64/mt6897/ and /vendor/lib/mt6897/ (and the hw/ and egl/
# equivalents) and exposes them at the canonical /vendor/lib64/ and
# /vendor/lib/ locations via symlinks. The arch subdir lets Lenovo reuse one
# image across mt6897-class boards; vendor binaries DT_NEEDED bare basenames,
# not subdir paths, so the top-level path is what the linker resolves against.
#
# extract-files only captures the real files (in mt6897/) and drops the
# symlinks. This makefile materialises them into $(TARGET_OUT_VENDOR)/lib64/
# (381 links) and $(TARGET_OUT_VENDOR)/lib/ (97 links) as real symlinks
# (relative targets so they resolve inside the partition). vendor.img
# packaging preserves symlinks, so the on-device layout matches stock. The
# targets are added to ALL_DEFAULT_INSTALLED_MODULES so vendor.img picks them
# up; INTERNAL_VENDORIMAGE_FILES ensures the image-build rule waits for them.
# Both manifests equal the symlinks of the stock ZUXOS 1.5.10.103 and .123
# vendor images (stock's /vendor/lib/modules link is installed separately).
#
# Stock never loads the 32-bit libraries: its product props make the system
# 64-bit only (ro.zygote=zygote64, mediaserver64). This build keeps the 32-bit
# ABI (TARGET_2ND_ARCH, zygote64_32), so 32-bit processes need the lib links:
# without /vendor/lib/hw/mapper.mediatek.so libui finds no gralloc mapper, and
# the 32-bit mediaserver aborts with "gralloc-mapper is missing" as soon as
# MediaPlayer renders video to a surface; without egl/ and hw/vulkan.mali.so a
# 32-bit app gets no GPU driver and HWUI aborts at its first frame.
#
# Manifest format (configs/vendor_lib64_symlinks.txt, configs/vendor_lib_symlinks.txt):
#   <link-rel-path>|<target-rel-path>
# e.g.
#   arm.graphics-V4-ndk.so|mt6897/arm.graphics-V4-ndk.so
#   egl/libGLES_mali.so|mt6897/libGLES_mali.so
#   hw/audio.primary.mt6897.so|audio.primary.mediatek.so
#
# Every file under a mt6897/ subdir gets a link one level up with the same
# name; lib64 also has three hw/*.mt6897.so aliases, which stock does not
# carry for lib.

# Per-symlink rule. $(1)=lib dir (lib or lib64), $(2)=link path relative to
# /vendor/$(1), $(3)=symlink target (relative path stored verbatim from stock),
# $(4)=manifest the link comes from.
#
# Whitespace and expansion gotchas:
#  1. $(strip) around $(call) args: Make doesn't trim whitespace from arg
#     values; without strip the line-continuation indentation leaves a leading
#     space in $(2), making the rule target "<path>/lib64/ <name>".
#  2. $$(TARGET_OUT_VENDOR), not $(TARGET_OUT_VENDOR). device.mk is processed
#     during product-config, BEFORE envsetup.mk sets TARGET_OUT_VENDOR. The
#     double-$ defers the variable to kati's second pass.
define tb375fc-vendor-lib-symlink
$$(TARGET_OUT_VENDOR)/$(1)/$(2): | $(4)
	@mkdir -p $$(dir $$@)
	@rm -f $$@
	$$(hide) ln -sfn $(3) $$@
ALL_DEFAULT_INSTALLED_MODULES += $$(TARGET_OUT_VENDOR)/$(1)/$(2)
INTERNAL_VENDORIMAGE_FILES   += $$(TARGET_OUT_VENDOR)/$(1)/$(2)
endef

# Expand each manifest at parse time via $(shell) so kati reads it once during
# analysis rather than per-rule. $(1)=lib dir, $(2)=manifest.
define tb375fc-vendor-lib-symlinks
$(foreach _pair,$(shell cat $(2)),$(eval $(call tb375fc-vendor-lib-symlink,$(1),$(strip $(word 1,$(subst |, ,$(_pair)))),$(strip $(word 2,$(subst |, ,$(_pair)))),$(2))))
endef

$(call tb375fc-vendor-lib-symlinks,lib64,$(LOCAL_PATH)/configs/vendor_lib64_symlinks.txt)
$(call tb375fc-vendor-lib-symlinks,lib,$(LOCAL_PATH)/configs/vendor_lib_symlinks.txt)
