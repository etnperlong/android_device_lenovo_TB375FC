# Google Apps on TB375FC

DerpFest 17 builds for TB375FC include Google Mobile Services by default
(`WITH_GMS:=true`). No separate GApps package or post-install sideload is
needed or documented for this build.

The device configuration selects tablet GMS behavior before common product
inheritance, disables the communications suite for this Wi-Fi-only tablet,
and sets `PRODUCT_GMS_CLIENTID_BASE` to `android-lenovo-rev2`. The upstream
DerpFest manifest and its GMS integration are documented at
<https://github.com/DerpFest-AOSP/android_manifest/tree/17> and
<https://github.com/DerpFest-AOSP/android_manifest/blob/17/snippets/derp.xml>.
The device configuration is at
<https://github.com/etnperlong/android_device_lenovo_TB375FC/tree/derpfest-17>.

Including GMS does not mean this community ROM or device is Google-certified,
and does not guarantee Play Integrity verdicts or that banking, payment,
streaming, or other apps will work. Those outcomes depend on Google's services,
device/build status, app policy, and may change independently of this project.
