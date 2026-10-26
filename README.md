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
(Google build `g6937a39e99f7-ab16619797`, tag
[`android14-6.1-2026-09_r4`](https://android.googlesource.com/kernel/common/+/refs/tags/android14-6.1-2026-09_r4/)).
The kernel image comes from Google's
[certified GKI archive](https://dl.google.com/android/gki/gki-certified-boot-android14-6.1-2026-09_r4-gz.zip);
the GKI modules come from
[CI build 16619797](https://ci.android.com/builds/submitted/16619797/kernel_aarch64/latest).
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
`962efcb3f29385373ac1da62779543b30a9e3cb1b6d14855f763f4811e11c1e6`;
the source certified ZIP SHA-256 is
`23a511948ab741a09d1c9dd435d014378fffad869d9178dfeb9339de43148f4b`;
the source modules archive SHA-256 is
`87cc88e6e26e102b10371227d403e075b123a5fed72ac09cfc0f3d28bd1689e0`.

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

### DRM
ClearKey uses AOSP's Android 17 `com.android.hardware.drm.clearkey` service
APEX, selected through `android.hardware.drm@latest-service.clearkey`.
The ClearKey CAS plugin is also the AOSP build (installed by `base_vendor.mk`);
the stock one linked `libprotobuf-cpp-*-21.7.so`, which Android 17 lacks.
Stock Widevine and its OEMCrypto library remain independently shipped;
the original ZUI `.103` Widevine components are retained. This describes
the component selection, not L1 or playback validation.

### Vendor binder
`/dev/vndbinder` is still used by stock vendor services such as the MediaTek
audio, Codec2 and camera HALs and the Dolby codec service, so the stock
`vndservicemanager` and `vndservice` are kept. The AOSP Android 17
`vndservicemanager` is not a drop-in replacement: its `IServiceManager`
transaction codes differ from the v34 `libbinder` these clients load, and it
needs `Looper::repoll`, which the v34 `libutils` lacks.

### Device parts
`DeviceParts` collects the device-specific settings below. All of them are on the "Device parts" entry of the Settings homepage, where the pen, folio keyboard and cover switches share the "Official accessories" category; the reading mode refresh rate and touch switches are also under Settings → Display → "Refresh rate and touch", the Dolby settings under Settings → Sound → "Dolby Atmos", the performance profiles under Settings → Battery → "Performance profiles", and the cover switch on the lock screen settings page ("Smart cover"). The Quick Settings tiles show the same state.

### Active pen
The pen writes through the Novatek touch controller; Bluetooth only carries its buttons and battery level. The controller looks for the pen only while its pen mode is on: `nt36532.ko` takes the mode from `/proc/support_pen`, starts with it off, turns it off again after the boot firmware update, and re-sends the last value every time the screen turns on. Stock ZUI's `services.jar` (`BluetoothPenConnectPolicy`) switches it through the Lenovo touchscreen HAL when a Lenovo pen connects over Bluetooth HID; LineageOS has no such code, so without it the pen pairs but draws nothing.

`DeviceParts` writes the same values: `1` then `3` when a Lenovo pen (stock's Bluetooth name list, e.g. "Lenovo Tab Pen Plus") connects, and `1` while a pen that has connected before is paired but disconnected, so the mode survives the pen going to sleep; stock writes `0` there and reconnects the pen when the controller reports its address, which AOSP does not do. Unpairing the last pen writes `0`. A notification shows when a pen connects or disconnects; "Pen connection notifications" in Device parts turns it off. Pen operation still needs device validation.

### Refresh rate and the active pen
The panel supports 144 / 120 / 60 / 30 Hz, but the NVT active pen is reported to work only up to 120 Hz. Stock keeps 144 available and steers the panel to 120 Hz while the pen is in use (a 10 s pen timer inside MediaTek's SurfaceFlinger, which AOSP does not have). `DeviceParts` does the same with `DisplayManager.requestMaxRefreshRate()`: it watches stylus events (hover included), caps the display at 120 Hz, and lifts the cap 10 s after the last event or when the screen turns off. The cap is on by default and can be turned off in Device parts. The default peak stays 120 Hz; 144 Hz can be picked in Settings → Display.

### Reading mode
Reading mode is the LineageOS one (Quick Settings "Reading mode" tile or LiveDisplay), which renders in grayscale. For the warm paper tint of stock's black-and-white mode, also enable Night Light at a low intensity; Night Light is applied after the grayscale matrix, so the tint is kept. The optional "Reading 30 Hz" Quick Settings tile (off by default, like stock) caps the display at 30 Hz, or at the minimum refresh rate if that is higher, while reading mode is on.

### Folio cover
The stock hall sensor driver reports the cover as two key presses that only ZUI understands, so `hall.ko` is patched to report a lid switch (`SW_LID`) instead (see below). `PhoneWindowManager` then tracks the lid and wakes the tablet whenever the cover opens. Lid behaviour stays "none"; `DeviceParts` applies stock's policy on lid close instead: with the screen on, the tablet goes to sleep once the light sensor reads at most 20 lux (so a cover folded behind the tablet does not count) and locks 2 s later. The "Smart cover" switch turns this off.

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
("Keyboard connection notifications" in Device parts turns it off), following
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
The Dolby DAX3 effect and its vendor `daxService` (switch, profile, Quick Settings tile) come from stock, but stock's settings screen lives in ZUI Settings. "Dolby Atmos" in `DeviceParts` rebuilds it: Dolby on/off, the mode (Dynamic, Movie or Music), and for Music the sound effect (none, detailed, balanced or warm intelligent equalizer preset) and a 10-band graphic equalizer (±6 dB), for Movie the dialogue enhancer (0 = off, 1–9). On/off and the active mode go through `daxService`'s `dolby_dap_state` / `dolby_dap_profile` settings, as on stock; the Music and Movie values are written to the effect for their own profile, so they apply whenever that profile is active, and are applied again at boot and after an audioserver restart.

Apps can have their own mode. Stock ZUI's AudioService switches the profile while an app from `/system/etc/audioeffect_whitelist.xml` plays media (music apps → Music, video apps → Movie), but only while the chosen mode is Dynamic. `DeviceParts` does the same with a user-editable list ("App modes") that applies whatever the default mode is: while an app plays media, game or unspecified audio, its mode is used; otherwise the default mode. If several apps play, the most important one (e.g. in the foreground) counts. Stock's list is the starting point, with the LineageOS gallery and music player in place of ZUI's apps, and "Switch mode by app" turns the feature off.

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
| Rendering | ADPF hint sessions from HWUI (`debug.hwui.use_hint_manager`) and SurfaceFlinger → `/proc/perfmgr_powerhal/ioctl_powerhal_adpf` | Per-frame CPU sizing of the UI and render threads |
| Foreground app | `DeviceParts` → `IMtkpower_applist` / `IMtkPowerService` (see Performance profiles) | Vendor or recommended per-app rules: thermal policy, FPSGO/FRS, CPU limits |
| Screen state | AOSP `Mode.INTERACTIVE` | Screen off suspends the power locks clients hold; screen on restores them |

Lenovo's table defines no scenario for `DISPLAY_UPDATE_IMMINENT` (58) or `SUSTAINED_PERFORMANCE` (65), so those do nothing, as on stock; the other modes are ignored by the HAL (`DeviceParts` passes `DOUBLE_TAP_TO_WAKE` to the touch driver itself). Stock's own touch hint (`APP_TOUCH`, sent by its patched inputflinger) has no scenario either, so on stock touch boosting is the kernel module's alone. A scenario's hold time and follow-up hint (`PERF_RES_POWER_HINT_HOLD_TIME`, `PERF_RES_POWER_HINT_EXT_HINT`) only apply to hints released through `IMtkPowerService.mtkPowerHint`, not to the AOSP `IPower` path, which is why the launch tail comes from `DeviceParts` rather than from the `LAUNCH` scenario.

Not restored, because they live in stock's framework (MediaTek's `mediatek-framework.jar` / `mediatek-services.jar` hooks and its patched SurfaceFlinger, inputflinger and audio policy): the ActivityManager process-create and app-switch hints themselves (their full-speed 6 s hold after cold starts; the lighter tail is sent by `DeviceParts` as above); MediaTek's BoostFWK scroll/touch/frame hints; the package-install boost; SurfaceFlinger multi-display hints and the audio routing boost. MAGT (MediaTek's game frame tuning) stays disabled because it needs the removed `android.hardware.thermal@2.0` HIDL interface, and ZUI's own performance service (per-app core binding) has no counterpart. Lenovo's `IPerformance` HAL still runs; it only offers CPU governor switching and frequency/temperature readings, and stock's framework never used its setters.

### Performance profiles
On stock, MediaTek's system_server hooks (`AmsExtImpl` in `mediatek-services.jar`, through `libpowerhalwrap`) tell the vendor `IMtkpower_applist` service which app is in front; it then applies that app's rules from `/vendor/etc/power_app_cfg.xml` (58 packages: mostly a thermal policy per kind of app, e.g. 10 for video and 13 for messaging, plus FPSGO/FRS tuning and CPU limits for games) and releases them when the app is paused. LineageOS has no such hook, so `DeviceParts` follows the focused task instead and sends the same notification ("Follow vendor", the default for listed apps).

The vendor list is old and misses many apps, so `DeviceParts` adds recommended presets (`DeviceParts/res/values/perf_recommended.xml`) for 88 apps, each taking the rules of a listed app: international, regional and Chinese channel (Bilibili, Xiaomi) editions of the listed games take their listed edition's rules (Honor of Kings, Genshin Impact, Wuthering Waves, Honkai Impact 3rd and Star Rail, Zenless Zone Zero, Delta Force, Tower of Fantasy, Infinity Nikki, Crossfire: Legends, Sword of Justice, Golden Spatula, Call of Duty: Mobile KR/VN); popular international and Chinese video, live-streaming, messaging, work-chat and music/audio apps take the rules of the listed apps of the same kind; and the LineageOS camera, Google Camera, two package names used by GCam ports and popular manual camera apps take ZUI Camera's. The package names were checked on Google Play, the Xiaomi and Tencent app stores, game download sites and in the LineageOS sources. To recommend more apps at build time, add `package|listed package` lines to that file. Apps that need rules of their own (rather than a listed app's) would need a modified `power_app_cfg.xml`, installed from the device tree the way `configs/power/powerscntbl.xml` is.

Each app can instead use a custom profile: one of the 27 vendor rule sets as a preset, optionally with advanced parameters (minimum and maximum frequency per CPU cluster along the cpufreq table, and a refresh-rate lock folded into the `DeviceParts` refresh-rate request), held as an `IMtkPowerService` power lock while the app is in front; or no rules at all. Recommended presets are applied the same way. Nothing is applied while the screen is off or locked. "Adjust performance by app" turns it all off.

### Touch edge rejection
The Novatek touch controller ignores touches along the edges the hands hold, and needs to know the screen orientation to pick those edges. Stock ZUI passes the display rotation (0–3) through the Lenovo touchscreen HAL to `/proc/panel_direction`; `DeviceParts` writes the same value on every rotation. The driver re-sends it after the screen turns back on.

### Touch game mode
The Novatek touch driver has a game mode (`/proc/game_mode`, sent to the touch controller as command `0x79`). Stock framework code only switches it on another Lenovo product (`ro.product.name` `P85802AA1`); on this tablet, stock's game assistant tunes touch through the Lenovo touchscreen HAL instead. The "Touch game mode" Quick Settings tile (in `DeviceParts`) switches the driver mode by hand. The driver keeps the mode across screen off/on but not across a reboot.

### Charging control
Stock's Lenovo battery HAL stops and restarts charging through `/sys/class/wt-battery/charging_enabled` (`1` = charge, `0` = stop). The LineageOS health HAL toggles the same node, which enables Settings -> Battery -> Charging control. Only the on/off toggle is configured, so a charge limit works by stopping the charger at the limit and restarting it a few percent below. Bypass mode stays off: it is not known whether the adapter alone powers the tablet while the charger is stopped.

### Patched prebuilt modules
Three stock kernel modules are modified; all are required, and extracting from a stock device gives the unpatched versions.

| Module | Change |
|--------|--------|
| `nt36532.ko` | Touch firmware loads on the first attempt in recovery instead of timing out for ~15 s |
| `wlan_drv_gen4m_6897.ko` | Fixes a WPA3 association failure in the stock driver |
| `hall.ko` | Reports the folio cover as `SW_LID` instead of key codes 252/253; reproducible with `tools/patch_hall_ko.py`, details in `docs/hall-ko-lid-switch.md` |

### Signing
Builds from this tree are signed with the public AOSP test keys, which is fine for personal use but not something to trust for anything security sensitive. Generate your own keys and sign your own build.

## Technical

- Boot image header v4: split `boot` / `vendor_boot` / `init_boot`
- erofs on the super sub-partitions, f2fs on `/data` and `/metadata`
- VINTF target level 8
- ART runtime CPU preset: `cortex-a55`; build-time CPU variants remain `generic`.
  ART does not recognize `cortex-a715`; its ARM64 A55 and A76 presets enable the same instruction-set features.
- TEE: beanpod (MediaTek); EGL: meow (MediaTek)

## License

Apache-2.0. See file headers for details.
