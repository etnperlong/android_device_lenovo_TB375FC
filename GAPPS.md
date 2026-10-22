# Google Apps on TB375FC

The Evolution X build includes Google Mobile Services (GMS). Evolution X's
`vendor/lineage/config/common_full_tablet_wifionly.mk` sets `WITH_GMS ?= true`
and inherits `vendor/gms/gms_full_tablet_wifionly.mk`, so Play Services, the
Play Store, the Pixel launcher and Pixel setup wizard ship in the image. Do not
flash a separate gapps package on top of it.

## Vanilla build

Building with `WITH_GMS=false` leaves GMS out and produces the Vanilla package
(`EvolutionX-...-<version>-Vanilla-...zip`) with the AOSP and LineageOS apps
instead. To add gapps to a Vanilla install:

1. Boot Evolution X, complete the setup wizard.
2. Reboot to recovery (`adb reboot recovery` or hold power+volume up).
3. Sideload a gapps package built for Android 17 (arm64), for example
   MindTheGapps:
   ```
   adb sideload MindTheGapps-17.0.0-arm64-YYYYMMDD.zip
   ```
4. Reboot, sign in to your Google account.

## Play Integrity

Evolution X overrides the system build fingerprint on devices other than the
supported Pixels (`TARGET_ENABLE_FP_OVERRIDE`, default `true`; see
`vendor/lineage/config/evolution.mk`) for RCS and Wallet. The vendor
fingerprint stays Lenovo's. Play Integrity results on this tablet have not been
checked.

With the bootloader unlocked, hardware-backed key attestation still reports the
verified-boot state, which build.prop spoofing cannot change. If an app needs
`MEETS_DEVICE_INTEGRITY` and the built-in spoofing does not pass, the usual
workaround is Magisk with the Play Integrity Fix Zygisk module:

1. Pull `init_boot.img` from the ROM zip (or `fastboot getvar partition-size:init_boot`
   reads the partition).
2. Patch it with the Magisk app (Install -> Select and Patch a File).
3. Flash the patched image: `fastboot flash init_boot magisk_patched.img`
4. Reboot, open Magisk, install the **PlayIntegrityFix** module.
5. Reboot.
