#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# TB375FC blob extraction + makefile generation entry point.
# Vendor updates use PRC .103/P16; retain existing Lineage adaptations.
# Run --regenerate_makefiles (-m) against the maintained proprietary/ tree.
# Use a disposable dump and isolated checkout for full re-extraction.
# No source argument extracts over adb.

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)

from extract_utils.fixups_lib import (
    lib_fixup_remove,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)


def lib_fixup_stock_suffix(lib: str, *args, **kwargs):
    return f'{lib}_stock'


lib_fixups: lib_fixups_user_type = {
    # MediaTek's GNSS library vendor/lib*/mt6897/libmnl.so (needed by mnld)
    # shares its name with AOSP's netfilter external/libmnl, which is
    # vendor_available since Android 17. proprietary-files.txt builds the
    # blob as libmnl_stock (stem libmnl); point dependents at it so the
    # unrelated AOSP library is never pulled onto /vendor.
    'libmnl': lib_fixup_stock_suffix,
    # Stock libraries that LineageOS 24.0 hardware/mediatek also builds at the
    # same install path. Libraries cannot shadow those across the namespace
    # cleanly, so proprietary-files.txt ships these as MAKE_COPY_RULE_ONLY
    # copies; drop the now module-less build-time edges (check_elf is off, the
    # copied files satisfy DT_NEEDED at runtime).
    (
        'libaedv',
        'libion_mtk',
        'libladder',
        'libmtkperf_client',
        'libmtkperf_client_vendor',
        'libperfctl_vendor',
        'libpowerhalwrap',
        'libpowerhalwrap_vendor',
        'vendor.mediatek.hardware.bluetooth.audio@2.1',
        'vendor.mediatek.hardware.bluetooth.audio@2.2',
        'vendor.mediatek.hardware.bluetooth.audio-V1-ndk',
        'vendor.mediatek.hardware.mtkpower-V1-ndk',
        'vendor.mediatek.hardware.mtkpower-V2-ndk',
    ): lib_fixup_remove,
}
blob_fixups: blob_fixups_user_type = {
    'vendor/etc/init/hw/meta_init.vendor.rc': blob_fixup().regex_replace(
        r'(?m)^import /vendor/etc/init/android\.hardware\.drm-service\.clearkey\.rc\n',
        '',
    ),
}


# The stock HAL binaries that hardware/mediatek also builds (audio, memtrack,
# thermal, vibrator, wlan_assistant) are generated as same-name preferred
# prebuilts. Importing the hardware/mediatek namespace lets Soong pair them
# with the source modules and hide those, so the source modules' init_rc,
# vintf_fragments and shared_libs are never installed. A Make copy rule over
# the same path would merge those as prerequisites into the staging dir,
# which target_files copies wholesale (duplicate IThermal/IVibrator VINTF
# fragments).
module = ExtractUtilsModule(
    'TB375FC',
    'lenovo',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=['hardware/mediatek'],
)


if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()

    # extract-utils has no overlay/ handler (its write_product_packages
    # categorises lib/app/priv-app/framework/etc/bin/apex only), so the
    # prebuilt RRO overlay APKs are supplemented after the standard pass.
    # See tools/gen_overlay_bp.py.
    import os
    import sys

    sys.path.insert(
        0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'tools')
    )
    import gen_overlay_bp

    gen_overlay_bp.main()
