# Lenovo Xiaoxin Pad Pro 12.7 (2025) - TB375FC

LineageOS 24.0 (Android 17) device tree for the Lenovo Xiaoxin Pad Pro 12.7 (2025), `TB375FC`, the PRC Wi-Fi variant. ODM is Wingtech; the internal project codename is `peridot`.

## Specifications

| Component | Detail |
|-----------|--------|
| SoC | MediaTek Dimensity 8300 (MT6897) |
| GPU | Mali-G615 |
| RAM / Storage | 8 GB / 128 GB, 8 GB / 256 GB, or 12 GB / 256 GB (UFS) |
| Display | 12.7" 3K (2944 x 1840) IPS LCD, 144 Hz (capped to 120, see Notes) |
| Battery | ~10200 mAh |
| Connectivity | Wi-Fi 6E, Bluetooth 5.4 (MediaTek connsys), no cellular |
| Cameras | 13 MP rear, 8 MP front |
| Fingerprint | Goodix |
| Stylus | Lenovo Tab Pen Plus (active pen) |
| Stock | Android 16 (ZUXOS 1.5.10.103); launched on Android 14 (API 34) |

## Build

```
repo init -u https://github.com/LineageOS/android.git -b lineage-24.0 --git-lfs
```

Add the manifest below as `.repo/local_manifests/TB375FC.xml`, then:

```
repo sync
source build/envsetup.sh
lunch lineage_TB375FC-cp2a-user
mka bacon
```

Kernel prebuilts in `prebuilts/` target Android 14 GKI `6.1.176-android14-11`
(Google build `g6d8630162a37-ab16707926`, tag
[`android14-6.1-2026-09_r5`](https://android.googlesource.com/kernel/common/+/refs/tags/android14-6.1-2026-09_r5/)).
The kernel image comes from Google's
[certified GKI archive](https://dl.google.com/android/gki/gki-certified-boot-android14-6.1-2026-09_r5-gz.zip);
the GKI modules come from
[CI build 16707926](https://ci.android.com/builds/submitted/16707926/kernel_aarch64/latest).
The separate `android_device_lenovo_TB375FC-kernel` checkout contains reference
prebuilts; the build does not consume it.

When updating the GKI, keep the kernel image and GKI `system_dlkm`/`vendor_dlkm`
modules from the same release. The device-specific 426 modules (222
`vendor_ramdisk`, 204 `vendor_dlkm`) remain from stock; the resulting 490-module
set is mixed Google GKI and stock device drivers, not an all-stock module set.
The GKI copies in `vendor_dlkm` are `bluetooth.ko`, `mii.ko`, `rfkill.ko`, and
`usbnet.ko`; all 60 `system_dlkm` modules come from the same Google build.
Preserve module load lists and refresh generated module metadata with the GKI
update. The current `prebuilts/Image.gz` SHA-256 is
`5206d3e7d7bc279c12cc12ecfb48e600822ab776fc8f02590e6d8ed38bb837b3`;
the source certified ZIP SHA-256 is
`c8e0741628cd484ecf60767c53c77cceabbe75c34432749381e2143da3488228`;
the source modules archive SHA-256 is
`4d6d58d726d0b13525ed0cc7a1a7e1c29bf43effcbc8487fae5ab54c05fa9ece`.

A temporary boot-v4 image was packed and unpacked with byte-exact kernel recovery.
Module imports, CRCs, protected exports and GKI signatures were checked;
`depmod` completed without diagnostics, and isolated `modprobe --show-depends`
dependency-resolution smoke checks passed for selected drivers. No modules were
loaded. This does not constitute a complete Android build, device boot, or
hardware test.

### local_manifests/TB375FC.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <project name="etnperlong/android_device_lenovo_TB375FC" path="device/lenovo/TB375FC" remote="github" revision="lineage-24.0" />
  <project name="etnperlong/android_vendor_lenovo_TB375FC" path="vendor/lenovo/TB375FC" remote="github" revision="lineage-24.0" />
  <project name="LineageOS/android_hardware_mediatek" path="hardware/mediatek" remote="github" revision="lineage-24.0" />
</manifest>
```

`hardware/mediatek` is the upstream LineageOS common tree (the device inherits it). The device and vendor `lineage-24.0` branches must be used together.

## Notes

### Device name
The default device name is `Lenovo Xiaoxin Pad Pro 12.7`, matching the English market name in stock ZUXOS `.103`. The SettingsProvider overlay supplies this name without changing `PRODUCT_MODEL`, which remains `TB375FC`, as do the stock vendor model and its boot-time `ro.product.model_for_attestation` assignment. Existing saved device names are preserved; change them in Settings → About tablet → Device name.

### Play Integrity

- `remote_provisioning.hostname` is set to `remoteprovisioning.googleapis.com`,
  as in stock ZUXOS `.103` `/product/etc/build.prop`, so rkpd fetches
  Google-rooted attestation keys for the beanpod KeyMint. Without it keystore2
  attests with the KeyMint factory key, and on Android 13+
  `MEETS_BASIC_INTEGRITY` requires a Google attestation root.
  `remote_provisioning.tee.rkp_only` stays unset, as on stock.
- `PRODUCT_MANUFACTURER` is `LENOVO` and `ro.product.vendor.name` is `TB375FC`
  (`DeviceProduct` override), matching stock. The stock vendor init copies
  `ro.product.vendor.{brand,model,device,manufacturer}` and `ro.vendor.pn`
  into `ro.product.*_for_attestation`, which keystore sends to KeyMint for
  device ID attestation.

With an unlocked bootloader at most `MEETS_BASIC_INTEGRITY` is expected. None of
this has been validated on the device yet.

### DRM
ClearKey uses AOSP's Android 17 `com.android.hardware.drm.clearkey` service
APEX, selected through `android.hardware.drm@latest-service.clearkey`.
The ClearKey CAS plugin is also the AOSP build (installed by `base_vendor.mk`);
the stock one linked `libprotobuf-cpp-*-21.7.so`, which Android 17 lacks.
Stock Widevine and its OEMCrypto library remain independently shipped;
the original ZUI `.103` Widevine components are retained. The vendor linker
configuration exports `liboemcrypto.so` to the Widevine APEX, matching stock;
without this namespace link, the CDM can fall back to L3 even though the
library is installed. L1 provisioning, relocked-bootloader behavior and
protected playback still require on-device validation.

### Vendor binder
`/dev/vndbinder` is still used by stock vendor services such as the MediaTek
audio, Codec2 and camera HALs and the Dolby codec service, so the stock
`vndservicemanager` and `vndservice` are kept. The AOSP Android 17
`vndservicemanager` is not a drop-in replacement: its `IServiceManager`
transaction codes differ from the v34 `libbinder` these clients load, and it
needs `Looper::repoll`, which the v34 `libutils` lacks.

### Device parts
`DeviceParts` collects the device-specific settings below. The "Device parts" entry of the Settings homepage lists its pages in groups: "MediaTek MiraVision™" ("Screen color" and "Video picture"), "Dolby Atmos®", "Performance and memory" ("Performance tuning" and "RAM expansion"), "Accessories" (pen, folio keyboard and magnetic cover) and "Other" ("Refresh rate and touch": reading mode 30 Hz and touch sensitivity). Each page is also placed in the Settings page it belongs to, through the `com.android.settings.group_key` and `order` of its entry: "Screen color" and "Video picture" first in the Color category of Settings → Display, "Refresh rate and touch" after "Screen refresh rate" on the same page, "Dolby Atmos®" first in the Audio category of Settings → Sound, "Performance profiles" in the first category of Settings → Battery, "RAM expansion" before "Automation & intelligence" on Settings → System, and "Accessories" on Settings → Connected devices. Quick Settings tiles cover the settings changed most often and show the same state: on/off tiles for "Reading 30 Hz", "Magnetic cover", "Adapt to ambient light" (unavailable while screen color is disabled), "Video picture enhancement" and "Performance tuning" (adjusting performance by app), and tiles that show the current choice and pick another in a dialog over the shade for "Dolby Atmos" (Dynamic, Movie, Music or Off), "Screen color" (Standard, Vivid or Disabled) and "Touch sensitivity". The Dolby and screen color dialogs note that apps with their own mode or color setting keep it. Long-pressing any of these tiles opens its settings page. The per-app settings of screen color, Dolby and performance each have their own list page with the feature's switch on top; Settings' App info page takes no injected entries, so they cannot be shown there.

### Active pen
The pen writes through the Novatek touch controller; Bluetooth only carries its buttons and battery level. The controller looks for the pen only while its pen mode is on: `nt36532.ko` takes the mode from `/proc/support_pen`, starts with it off, turns it off again after the boot firmware update, and re-sends the last value every time the screen turns on. Stock ZUI's `services.jar` (`BluetoothPenConnectPolicy`) switches it through the Lenovo touchscreen HAL when a Lenovo pen connects over Bluetooth HID; LineageOS has no such code, so without it the pen pairs but draws nothing.

`DeviceParts` writes the same values: `1` then `3` when a Lenovo pen (stock's Bluetooth name list, e.g. "Lenovo Tab Pen Plus") connects, and `1` while a pen that has connected before is paired but disconnected, so the mode survives the pen going to sleep; stock writes `0` there and reconnects the pen when the controller reports its address, which AOSP does not do. Unpairing the last pen writes `0`. A notification shows when a Lenovo pen connects or disconnects; "Pen connection notifications" in Accessories turns it off. Pen operation still needs device validation.

### Refresh rate and the active pen
The panel supports 144 / 120 / 60 / 30 Hz, but the NVT active pen is reported to work only up to 120 Hz. Stock keeps 144 available and steers the panel to 120 Hz while the pen is in use (a 10 s pen timer inside MediaTek's SurfaceFlinger, which AOSP does not have). `DeviceParts` does the same with `DisplayManager.requestMaxRefreshRate()`: it watches stylus events (hover included), caps the display at 120 Hz, and lifts the cap 10 s after the last event or when the screen turns off. The cap applies to any stylus, is on by default and can be turned off in Accessories. The default peak stays 120 Hz; 144 Hz can be picked in Settings → Display.

### Reading mode
Reading mode is the LineageOS one (Quick Settings "Reading mode" tile or LiveDisplay), which renders in grayscale. For the warm paper tint of stock's black-and-white mode, also enable Night Light at a low intensity; Night Light is applied after the grayscale matrix, so the tint is kept. The optional "Reading 30 Hz" Quick Settings tile (off by default, like stock) caps the display at 30 Hz, or at the minimum refresh rate if that is higher, while reading mode is on.

### Screen color
Stock ZUI's color settings (Settings → Display → Screen color mode) drive MediaTek's picture quality engine: `ZuiDisplayService` in `services.jar` calls `com.mediatek.pq.PictureQuality` (`mediatek-framework.jar`, on stock's boot classpath), whose `libjni_pq.so` calls the vendor `vendor.mediatek.hardware.pq_aidl` service. MediaTek's own MiraVision app is installed on stock but never exposed. LineageOS keeps the vendor service but has nothing that calls it, so `DeviceParts` ("Screen color") calls it directly and sends what stock sends on this panel (`peridot`):

| Setting | Picture quality calls |
|---------|-----------------------|
| Vivid (default, as on stock) | gamma table 7, identity color matrix, skin/grass tone and partial Y indexes 0 |
| Standard | gamma table 8, stock's peridot color matrix |
| Disabled (not on stock) | the vendor defaults: gamma table 7 (the service's default), identity color matrix, skin/grass tone and partial Y indexes back to the panel PQ XML values (12, 10, 9, 0, 6), neutral white, no Chameleon, default picture levels; Settings → Display → Colors then decides the look. The stored temperature, ambient adaptation and adjustments are kept for when a mode is chosen again |
| Color temperature: Standard / Warm / Cool / point on the wheel | RGB gains (stock's presets 255/255/255, 255/238/215, 228/229/255; the wheel maps a point to gains the way stock's picker does) |
| Adapt to ambient light (stock "Adapt color to environment") | Chameleon on/off; the vendor service then reads the RGBW light sensor itself through system_server's sensor manager, which `tb375fc_screen_color.te` lets it call, as stock does |
| Saturation, contrast, sharpness (not on stock; MiraVision has them) | picture indexes 1, 9 and 0, levels 0–9 |
| Video picture enhancement On / Off, and while On: sharpening, dynamic sharpening, noise-adaptive sharpening, ultra resolution, dynamic contrast, color enhancement | `setFeatureSwitch` features 3, 5, 9, 10, 4 + 13 and 2 (the enable methods of MediaTek's class); Off turns them all off |

The color temperature wheel and the picture adjustments are folded under "Advanced adjustments". Apps can have their own color setting ("Per-app settings"): the screen color settings, Standard, Vivid or Disabled, used while the app is in front with the screen on and unlocked; Standard and Vivid keep the screen's color temperature and adjustments, Disabled sends the vendor defaults as above. The settings are reapplied at boot and whenever the vendor service restarts. The top of the page is a 16:9 gallery of sample pictures to judge the colors by, swiped sideways and viewable in full screen, where the photographer of each picture is shown; the pictures and their credits are listed in `DeviceParts/res/values/screen_color.xml` (`screen_color_gallery`, `screen_color_gallery_credits`) and cropped to the card's centre inline, shown whole in full screen. The pictures are 2944 × 1840 (the panel's resolution), sRGB, JPEG quality 85 without chroma subsampling, so colored edges stay accurate. They are from Pexels, under the Pexels license, in `DeviceParts/res/drawable-nodpi/`:

| File | Picture | Photographer | Pexels photo |
|------|---------|--------------|--------------|
| `screen_color_gallery_1.jpg` | Aerial view of a beach and village | Abdulaziz Hasan | 38127334 |
| `screen_color_gallery_2.jpg` | Dutch windmills in a snowy landscape at sunrise | Ruben Boekeloo | 35611413 |
| `screen_color_gallery_3.jpg` | Skyline view from Kowloon Peak at sunset | Alex M | 40418485 |
| `screen_color_gallery_4.jpg` | Illuminated neon signs in Tokyo at night | Jerry Wang | 20392882 |

Colors still need device validation, in particular together with Night Light and reading mode, which reach the same color hardware through the composer.

The video picture switches, on their own "Video picture" page, cover MediaTek's processing of video layers in the display hardware (MDP/MML), which only hardware-decoded video gets: software-decoded video, or any video once hardware overlays are disabled in developer options, is composed by the GPU without it. The vendor service turns every one of them on when nothing has set it. Stock users report over-sharpened edges, amplified noise and off colors with hardware decoding only, which these switches are meant to remove; which feature causes what still needs device testing. The defaults follow stock: ultra resolution off (stock turns it off at boot unless its "video quality enhance" switch is on), the others on. Stock's video quality enhancement itself (Lenovo's video super-resolution, `persist.sys.vsr.enable`) and video color enhancement (a no-op on stock, its method is missing from MediaTek's class) are not reproduced. The video switches do not take part in app color settings.

The top of the video page is a 16:9 player for sample clips, so the switches can be judged while a clip plays: the clips are swiped sideways, nothing plays until the play button is tapped, and the position slider scrubs (opening the clip paused if needed). Clips loop; tapping the clip shows or hides the controls, which hide by themselves while it plays. Full screen (landscape, showing who filmed the clip) continues the clip where it is, and the page continues where full screen left. It plays with `MediaPlayer` onto a `SurfaceView`, so the clip goes through the hardware decoder and reaches the composer as a video layer of its own, like any video app's, and MediaTek's processing applies to it; a `TextureView` would have the GPU draw the video into the app's window without it. For the same reason the layer itself gets no rounded corners: SurfaceFlinger composes layers with rounded corners with the GPU. The card's rounded corners only clip the hole the window leaves above the video. Only the current clip holds a player, so there is at most one hardware decoder, released when another clip is shown, the card scrolls off, the page is left or full screen takes over; the clip keeps its position meanwhile and shows that frame paused when it is back. Another clip starts from the beginning.

The clips are in `DeviceParts/res/raw/`, each with its first frame as a poster in `DeviceParts/res/drawable-nodpi/`, listed with their credits in `DeviceParts/res/values/video_picture.xml` (`video_picture_samples`, `video_picture_posters`, `video_picture_credits`). They are 1920 × 1080 (the player expects 16:9) SDR (BT.709, limited range) without audio. Clips 1 and 3 keep Pexels' H.264 stream untouched (clip 3 cut at the 15.2 s keyframe), since re-encoding would only add a generation of loss; clip 2, whose source is 11 Mbit/s, is the first 15 s re-encoded to HEVC Main at CRF 28 (VMAF 97 against the source). Posters are full-range sRGB JPEG, quality 85 without chroma subsampling. The clips are from Pexels, under the Pexels license:

| File | Clip | Videographer | Pexels video |
|------|------|--------------|--------------|
| `video_picture_sample_1.mp4` | Boats on a waterway in a city, 12.4 s, H.264 | Cihat Dede | 18644122 |
| `video_picture_sample_2.mp4` | Heavy machinery loading logs in a forest, 15 s, HEVC | Casey Whalen | 36130784 |
| `video_picture_sample_3.mp4` | Time-lapse of a night sky with stars and clouds, 15.2 s, H.264 | Roy S. | 10719866 |

### Folio cover
The stock hall sensor driver reports the cover as two key presses that only ZUI understands, so `hall.ko` is patched to report a lid switch (`SW_LID`) instead (see below). `PhoneWindowManager` then tracks the lid and wakes the tablet whenever the cover opens. Lid behaviour stays "none"; `DeviceParts` applies stock's policy on lid close instead: with the screen on, the tablet goes to sleep once the light sensor reads at most 20 lux (so a cover folded behind the tablet does not count) and locks 2 s later. Any magnetic cover that triggers the hall sensor works, not only Lenovo's. The "Magnetic cover" switch in Accessories turns this off.

### Lenovo keyboards and pens
`configs/keylayout` and `configs/idc` carry stock's `Vendor_17ef_Product_*` files for Lenovo keyboard covers and pens. Stock uses ZUI-only key codes for the special keys; LineageOS's input stack rejects a whole layout file on an unknown label, so those lines are commented out, except that Lenovo mute, screen lock, search and settings keys are mapped to the AOSP `VOLUME_MUTE`, `LOCK`, `SEARCH` and `SETTINGS` keys. Touchpad mute, Fn lock, full screen, split screen, PC mode, the app keys, keyboard backlight levels and the pen button gestures have no AOSP equivalent and do nothing.

The folio keyboard UART `/dev/ttyS3` uses an ODM ueventd rule to apply stock
`0666 system:system` permissions when the node is created. This avoids relying
on the timing of the one-shot init `chown`/`chmod`; the existing SELinux label
and HAL access rules are unchanged. Keyboard operation still needs device validation.

The keyboard keeps typing and its touchpad keeps tracking when it is folded
behind the tablet; `tn_keyboard.ko` only reports the fold on its `tn_wakeup`
device (scan code 655, and 654 once it is folded back). Stock ZUI then
disables the keyboard and lets the on-screen keyboard show. `DeviceParts` does
the same from a `DeviceKeyHandler` that `PhoneWindowManager` loads into
system_server (`LineageSdkDeviceOverlay` sets lineage-sdk
`config_deviceKeyHandlerLibs`/`Classes`, replacing the LineageParts
touchscreen gesture handler, which has nothing to do without a
`vendor.lineage.touch` gesture HAL): it disables `tn_keyboard` and
`tn_touchpad` and, unless it is already on,
turns on "Use on-screen keyboard" (`show_ime_with_hard_keyboard`) until the
keyboard is folded back or goes away. Whether the firmware reports both scan
codes still needs device validation.

A notification also shows when the folio keyboard connects or disconnects
("Keyboard connection notifications" in Accessories turns it off), following
its `tn_keyboard` input device. `tn_keyboard.ko` cuts the keyboard's power a
minute after the cover closes, so changes while the screen is off, and the
reconnection that follows them, are not announced.

### Wi-Fi supplicant
`wpa_supplicant` and `hostapd` are the stock MediaTek builds. Stock's
supplicant rc started the daemon as root and let it `setuid` to `wifi`, which
Android 17 SELinux no longer allows, so the rc in `rootdir` starts it as `wifi`
with `NET_ADMIN`/`NET_RAW`, like AOSP's rc. Connection behaviour still needs
device validation.

### Bluetooth classic profiles
`tb375fc-stock-vendor-props.mk` explicitly enables A2DP source, both HFP roles,
AVRCP controller/target, HID host, OPP, PBAP server and MAP server for the AOSP
Bluetooth stack. HID device mode remains disabled, as do A2DP hardware offload
and the existing disabled LE profiles. These gates enable the profile services;
they are not a claim of verified headset playback or keyboard connectivity on
every build.

### Speaker channels
The four speakers are driven by Awinic amplifiers whose channel layout follows the screen rotation. The MediaTek audio HAL takes the rotation as the `gsensor_rotation` parameter (it ignores AOSP's `rotation`), which stock ZUI's AudioService sends on every rotation. `DeviceParts` does the same (display rotation 0/90/180/270 → 270/0/90/180, as on stock), also after an audioserver restart, so left and right stay on the correct side in every orientation.

### Dolby Atmos
The Dolby DAX3 effect and its vendor `daxService` (switch, profile) come from stock, but stock's settings screen lives in ZUI Settings. "Dolby Atmos" in `DeviceParts` rebuilds it: the mode as cards (Dynamic, Movie, Music, or Off for Dolby off), then the entry to the equalizer, then the setting stock offers for the chosen mode, for Music the sound effect (none, detailed, balanced or warm intelligent equalizer preset) and for Movie the dialogue enhancer (0 = off, 1–9); Dynamic keeps its own tuning from `dax-default.xml` (media-steered intelligent equalizer, dialogue enhancer at 4). A 10-band graphic equalizer (±6 dB, drawn as a draggable response curve) applies to all three modes, which all enable it and start flat; it has a page of its own ("Equalizer settings"), whose toolbar action resets it to flat after asking, shown only while it is not flat. On/off and the active mode go through `daxService`'s `dolby_dap_state` / `dolby_dap_profile` settings, as on stock; the other values are written to the effect for their profiles, so they apply whenever that profile is active, and are applied again at boot and after an audioserver restart. While Dolby is off, the equalizer entry is hidden and the rest of the page is disabled. `daxService` also has a Quick Settings tile that only switches Dolby on and off; DeviceParts disables it at boot, and its own "Dolby Atmos" tile, which also picks the mode, takes its place in the default tile layout (`SystemUIDeviceOverlay`). On an existing install the old tile disappears from the panel and the new one has to be added once.

Apps can have their own mode. Stock ZUI's AudioService switches the profile while an app from `/system/etc/audioeffect_whitelist.xml` plays media (music apps → Music, video apps → Movie), but only while the chosen mode is Dynamic. `DeviceParts` does the same with a user-editable list ("App modes") that applies whatever the default mode is: while an app plays media, game or unspecified audio, its mode is used; otherwise the default mode. If several apps play, the most important one (e.g. in the foreground) counts. Stock's list is the starting point, with the LineageOS gallery and music player in place of ZUI's apps, and "Switch mode by app" at the top of that list turns the feature off.

### MediaTek power management
The power stack is stock MediaTek's and runs from the vendor partition: `vendor.mediatek.hardware.mtkpower-service.mediatek` serves both AOSP `IPower` (V4) and MediaTek's `IMtkPowerService`, and its `libpowerhal` turns hints into CPU/DRAM/scheduler settings through `/vendor/etc/powerscntbl.xml` (scenarios), `powercontable.xml` and `command.csv` (resources), and the `perf_ioctl` / `powerhal_cpu_ctrl` kernel modules. It must run in its own `mtk_hal_power` domain (the kernel module checks the caller), which is why the vendor policy is compiled at boot (`tb375fc-sepolicy-precompiled.mk`). The FPSGO, GBE, `touch_boost`, `thermal_core` and FRS daemons are stock as well. Only the scenario table differs from stock: `configs/power/powerscntbl.xml` adds two scenarios (below).

What drives it on LineageOS:

| Source | Path | Effect |
|--------|------|--------|
| Activity launches | AOSP `Mode.LAUNCH` → HAL hint 68 → `LAUNCH` scenario | All three CPU clusters held at their maximum frequency, top-app `uclamp.min` 100, task turbo and the highest DRAM OPP until the launch completes |
| Cold starts | `DeviceParts` → `IMtkPowerService.mtkCusPowerHint(EXT_LAUNCH, 15 s)` when the app's process started less than 10 s before it came to the front | Lighter boost while the app loads: top-app `uclamp.min` 40, DRAM OPP 2. Stock's framework holds it for 30 s after its process-create hint |
| Interaction | AOSP `Boost.INTERACTION` → hint 57 → `INTERACTION` scenario (added) | 1.1 GHz floors on all clusters and top-app `uclamp.min` 40, like stock's `UX_SCROLLING`, for 80 ms or the fling duration |
| Game loading | AOSP `Mode.GAME_LOADING` (GameManager) → hint 79 → `GAME_LOADING` scenario (added) | Stock's `PROCESS_CREATE` boost, held up to 5 s |
| Touch | kernel `touch_boost` module and vendor daemon, independent of the framework | 200 ms boost on touch-down (cluster OPP floors, top-app/foreground uclamp 80) |
| Rendering | ADPF hint sessions from HWUI (`debug.hwui.use_hint_manager`) and SurfaceFlinger (`debug.sf.enable_adpf_cpu_hint`) → `/proc/perfmgr_powerhal/ioctl_powerhal_adpf` → FPSGO | Per-frame CPU sizing of the UI, render and composition threads; stands in for stock's frame reporting from MediaTek's patched `libgui`/`libhwui` and `libsf_cpupolicy`, which AOSP lacks |
| Foreground app | `DeviceParts` → `IMtkpower_applist` / `IMtkPowerService` (see Performance profiles) | Vendor or recommended per-app rules: thermal policy, FPSGO/FRS, CPU limits |
| Screen state | AOSP `Mode.INTERACTIVE` | Screen off suspends the power locks clients hold; screen on restores them |

Lenovo's table defines no scenario for `DISPLAY_UPDATE_IMMINENT` (58) or `SUSTAINED_PERFORMANCE` (65), so those do nothing, as on stock; the other modes are ignored by the HAL (`DeviceParts` passes `DOUBLE_TAP_TO_WAKE` to the touch driver itself). Stock's own touch hint (`APP_TOUCH`, sent by its patched inputflinger) has no scenario either, so on stock touch boosting is the kernel module's alone. A scenario's hold time and follow-up hint (`PERF_RES_POWER_HINT_HOLD_TIME`, `PERF_RES_POWER_HINT_EXT_HINT`) only apply to hints released through `IMtkPowerService.mtkPowerHint`, not to the AOSP `IPower` path, which is why the launch tail comes from `DeviceParts` rather than from the `LAUNCH` scenario.

Not restored, because they live in stock's framework (MediaTek's `mediatek-framework.jar` / `mediatek-services.jar` hooks and its patched SurfaceFlinger, inputflinger and audio policy): the ActivityManager process-create and app-switch hints themselves (their full-speed 6 s hold after cold starts; the lighter tail is sent by `DeviceParts` as above); MediaTek's BoostFWK scroll/touch/frame hints; the package-install boost; SurfaceFlinger multi-display hints and the audio routing boost. MAGT (MediaTek's game frame tuning) stays disabled because it needs the removed `android.hardware.thermal@2.0` HIDL interface, and ZUI's own performance service (per-app core binding) has no counterpart. Lenovo's `IPerformance` HAL still runs; it only offers CPU governor switching and frequency/temperature readings, and stock's framework never used its setters.

### Performance profiles
On stock, MediaTek's system_server hooks (`AmsExtImpl` in `mediatek-services.jar`, through `libpowerhalwrap`) tell the vendor `IMtkpower_applist` service which app is in front; it then applies that app's rules from `/vendor/etc/power_app_cfg.xml` (58 packages: mostly a thermal policy per kind of app, e.g. 10 for video and 13 for messaging, plus FPSGO/FRS tuning and CPU limits for games) and releases them when the app is paused. LineageOS has no such hook, so `DeviceParts` follows the focused task instead and sends the same notification ("Follow vendor", the default for listed apps).

The vendor list is old and misses many apps, so `DeviceParts` adds recommended presets (`DeviceParts/res/values/perf_recommended.xml`) for 88 apps, each taking the rules of a listed app: international, regional and Chinese channel (Bilibili, Xiaomi) editions of the listed games take their listed edition's rules (Honor of Kings, Genshin Impact, Wuthering Waves, Honkai Impact 3rd and Star Rail, Zenless Zone Zero, Delta Force, Tower of Fantasy, Infinity Nikki, Crossfire: Legends, Sword of Justice, Golden Spatula, Call of Duty: Mobile KR/VN); popular international and Chinese video, live-streaming, messaging, work-chat and music/audio apps take the rules of the listed apps of the same kind; and the LineageOS camera, Google Camera, two package names used by GCam ports and popular manual camera apps take ZUI Camera's. The package names were checked on Google Play, the Xiaomi and Tencent app stores, game download sites and in the LineageOS sources. To recommend more apps at build time, add `package|listed package` lines to that file. Apps that need rules of their own (rather than a listed app's) would need a modified `power_app_cfg.xml`, installed from the device tree the way `configs/power/powerscntbl.xml` is.

Each app can instead use a custom profile: one of the 27 vendor rule sets as a preset, optionally with advanced parameters (per CPU cluster one range slider with minimum and maximum dropdowns along the cpufreq table, and a refresh-rate lock folded into the `DeviceParts` refresh-rate request), held as an `IMtkPowerService` power lock while the app is in front; or no rules at all. Recommended presets are applied the same way. Nothing is applied while the screen is off or locked. "Adjust performance by app" turns it all off.

### RAM expansion
zram is set up by AOSP's mmd (`system/memory/mmd`) after boot instead of stock's `swapon_all`, which needs Lenovo's patched `fs_mgr`: it covers all of RAM, idle pages are recompressed with zstd, and pages left idle for about a day are written back to a file in `/data/per_boot/zram` ("RAM expansion"). Stock ZUI offers the same file sizes (`persist.sys.zram_wb_size`) but leaves `config_zramWriteback` off, so it never writes anything back. "RAM expansion" in `DeviceParts` (Settings → System) sets the file size on a slider from off to 8 GB in 2 GB steps (it stops at the largest size that leaves 1.5 GB of storage free), the primary compression algorithm (LZ4 by default; zstd stores about 50 % more in the same RAM at roughly twice the CPU time) and whether idle pages are recompressed with zstd (on by default; not offered while zstd is the primary algorithm, as it gains nothing then). Changes are kept on the page until saved with the toolbar's save action, or on leaving the page with unsaved changes, which asks whether to save or discard them; saving offers a restart, as `rootdir/etc/init/init.tb375fc-zram.rc` hands the choices to mmd at boot. The page also shows how much data zram holds, the RAM it uses and how much has been moved to storage.

mmd needs features stock's `zram_ext.ko` lacks, so it is rebuilt (see below). The kernel writes the backing file from its own threads, hence `allow kernel zram_data_file:file { read write };` in `sepolicy/private/tb375fc_zram.te`.

### Touch edge rejection
The Novatek touch controller ignores touches along the edges the hands hold, and needs to know the screen orientation to pick those edges. Stock ZUI passes the display rotation (0–3) through the Lenovo touchscreen HAL to `/proc/panel_direction`; `DeviceParts` writes the same value on every rotation. The driver re-sends it after the screen turns back on.

### Touch sensitivity
The Novatek touch driver sets the controller's palm rejection through `/proc/game_mode` (command `0x79`): `0` is normal, `1` ("pen mode") strengthens palm rejection and `2` ("game mode") removes it; other values are ignored. These are the levels the stock Lenovo touchscreen HAL offers through `setBigPalm`, which writes its argument to the same node, and stock's game assistant on the Legion tablets labels the removed rejection its most responsive swipe setting. On this tablet nothing in stock writes the node: the framework only does so on another product (`ro.product.name` `P85802AA1`), and the game assistant's touch adjustment is limited to the Legion tablets.

DeviceParts offers the three levels as "Touch sensitivity" on the "Refresh rate and touch" page and as a Quick Settings tile that shows the level and picks another in a dialog: "Palm rejection (low)" (`1`), "Standard (medium)" (`0`, the default) and "Responsive (high)" (`2`). The choice is stored in the Secure setting `tb375fc_touch_sensitivity` and written back at boot, since the driver starts at normal on every boot; it re-sends the level itself after the screen turns back on.

### Charging control
Stock's Lenovo battery HAL stops and restarts charging through `/sys/class/wt-battery/charging_enabled` (`1` = charge, `0` = stop). The LineageOS health HAL toggles the same node, which enables Settings -> Battery -> Charging control. Only the on/off toggle is configured, so a charge limit works by stopping the charger at the limit and restarting it a few percent below. Bypass mode stays off: it is not known whether the adapter alone powers the tablet while the charger is stopped.

### Patched prebuilt modules
Three stock kernel modules are modified and one is rebuilt; all are required, and extracting from a stock device gives the stock versions.

| Module | Change |
|--------|--------|
| `nt36532.ko` | Touch firmware loads on the first attempt in recovery instead of timing out for ~15 s |
| `wlan_drv_gen4m_6897.ko` | Fixes a WPA3 association failure in the stock driver |
| `hall.ko` | Reports the folio cover as `SW_LID` instead of key codes 252/253; reproducible with `tools/patch_hall_ko.py`, details in `docs/hall-ko-lid-switch.md` |
| `zram_ext.ko` | Rebuilt from source. Lenovo's (MediaTek's `zram_ext/zram_601`, GPL source in the [mt8792](https://github.com/mt8792/android_vendor_mediatek_kernel-modules) kernel package) is GKI zram with writeback and its own `wb_size=` mode, but without the `huge_idle` writeback mode mmd uses first, so every mmd writeback failed. The replacement is android14-6.1 (`android14-6.1-2026-09_r5`) zram as `drivers/block/zram_ext`, branch `patch/zram_ext` at `e084d821a192`: writeback, Lenovo's `wb_size=` and `idle_page_size`, access-time tracking (`idle <seconds>`), recompression and incompressible writeback backported from 6.2/6.3, and compressed writeback. Built against GKI build 16707926's `Module.symvers`, so its symbol CRCs and vermagic match the stock kernel; it is unsigned, which GKI allows for zram |

### Signing
Builds from this tree are signed with the public AOSP test keys, which is fine for personal use but not something to trust for anything security sensitive. Generate your own keys and sign your own build.

## Technical

- Boot image header v4: split `boot` / `vendor_boot` / `init_boot`
- erofs on the super sub-partitions, f2fs on `/data` and `/metadata`
- VINTF target level 8
- Native arm64 target: `armv9-a/generic`; ARM32 remains `armv8-2a/generic`.
- ART runtime CPU preset: `cortex-a55`; build-time CPU variants remain `generic`.
  ART does not recognize `cortex-a715`; its ARM64 A55 and A76 presets enable the same instruction-set features.
- TEE: beanpod (MediaTek); EGL: meow (MediaTek)

## License

Apache-2.0. See file headers for details.
