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

Kernel, DTB and modules ship prebuilt in the device tree (`prebuilts/`), so no kernel source is needed to build. The separate `android_device_lenovo_TB375FC-kernel` checkout contains reference prebuilts; the build does not consume it.

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

### Device parts
`DeviceParts` collects the device-specific settings below. All of them are on the "Device parts" entry of the Settings homepage; the pen and touch switches are also under Settings → Display → "Pen and touch", and the cover switch on the lock screen settings page ("Smart cover"). The Quick Settings tiles show the same state.

### Refresh rate and the active pen
The panel supports 144 / 120 / 60 / 30 Hz, but the NVT active pen is reported to work only up to 120 Hz. Stock keeps 144 available and steers the panel to 120 Hz while the pen is in use (a 10 s pen timer inside MediaTek's SurfaceFlinger, which AOSP does not have). `DeviceParts` does the same with `DisplayManager.requestMaxRefreshRate()`: it watches stylus events (hover included), caps the display at 120 Hz, and lifts the cap 10 s after the last event or when the screen turns off. The cap is on by default and can be turned off in Device parts. The default peak stays 120 Hz; 144 Hz can be picked in Settings → Display.

### Reading mode
Reading mode is the LineageOS one (Quick Settings "Reading mode" tile or LiveDisplay), which renders in grayscale. For the warm paper tint of stock's black-and-white mode, also enable Night Light at a low intensity; Night Light is applied after the grayscale matrix, so the tint is kept. The optional "Reading 30 Hz" Quick Settings tile (off by default, like stock) caps the display at 30 Hz, or at the minimum refresh rate if that is higher, while reading mode is on.

### Folio cover
The stock hall sensor driver reports the cover as two key presses that only ZUI understands, so `hall.ko` is patched to report a lid switch (`SW_LID`) instead (see below). `PhoneWindowManager` then tracks the lid and wakes the tablet whenever the cover opens. Lid behaviour stays "none"; `DeviceParts` applies stock's policy on lid close instead: with the screen on, the tablet goes to sleep once the light sensor reads at most 20 lux (so a cover folded behind the tablet does not count) and locks 2 s later. The "Smart cover" switch turns this off.

### Lenovo keyboards and pens
`configs/keylayout` and `configs/idc` carry stock's `Vendor_17ef_Product_*` files for Lenovo keyboard covers and pens. Stock uses ZUI-only key codes for the special keys; LineageOS's input stack rejects a whole layout file on an unknown label, so those lines are commented out, except that Lenovo mute, screen lock, search and settings keys are mapped to the AOSP `VOLUME_MUTE`, `LOCK`, `SEARCH` and `SETTINGS` keys. Touchpad mute, Fn lock, full screen, split screen, PC mode, the app keys, keyboard backlight levels and the pen button gestures have no AOSP equivalent and do nothing.

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
- TEE: beanpod (MediaTek); EGL: meow (MediaTek)

## License

Apache-2.0. See file headers for details.
