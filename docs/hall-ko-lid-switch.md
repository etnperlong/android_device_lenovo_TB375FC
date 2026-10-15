# hall.ko: report the folio cover as a lid switch

`prebuilts/modules/vendor_ramdisk/hall.ko` is the stock MediaTek Hall sensor
driver (compatible `mediatek,hall_1`, sensor `AS1912NWRN`, vermagic
`6.1.138-android14-11-gfdf3de8ccf1b`), binary-patched so that Android sees the
folio cover as `EV_SW`/`SW_LID` instead of two key presses.

| | sha256 |
|---|---|
| Stock | `d408d33835673cebd37e7ed67885310582839b717c0ce9c97d15092bb0593c6c` |
| Patched | `aefb56828a7f364d26a79934857f1da3a6ca18478ea5ab45d6484b5fe2bce7f9` |

To redo the patch, for example after re-extracting the stock modules:

```
tools/patch_hall_ko.py <stock hall.ko> prebuilts/modules/vendor_ramdisk/hall.ko
```

The script refuses any input other than the stock module and checks every
patched instruction against its stock encoding before writing.

## Why

The stock driver registers an input device named `hall` with only two key
capabilities and reports the cover as key presses:

| Cover | GPIO | Stock events (`hall_eint_work`) |
|---|---|---|
| Closed | low | `EV_KEY 252` down, `SYN`, `EV_KEY 252` up, `SYN` |
| Opened | high | `EV_KEY 253` down, `SYN`, `EV_KEY 253` up, `SYN` |

Stock ZUI maps 252/253 to its private `HALL_NEAR`/`HALL_FAR` key codes in
`Generic.kl` and turns them into lid changes in `ZuiPhoneWindowManager`.
LineageOS has neither, so it never learns the lid state. With `SW_LID`,
InputReader's switch mapper reports the lid to `PhoneWindowManager`
(`notifyLidSwitchChanged`), which keeps the lid state, wakes the device when
the cover opens and sends `lineageos.intent.action.LID_STATE_CHANGED`
(`DeviceParts` applies the stock sleep/lock policy on top; see README).

## Patch

All changes are same-size `mov`/`orr` immediates in `.text` (file offset
`0x11c8`); none of the patched words carries a relocation. The module is not
signed (no `~Module signature appended~` trailer), so it loads as before.

| `.text` | Function | Stock | Patched |
|---|---|---|---|
| `0x1a4` | `hall_pdrv_probe` | `mov w1, #1` (`EV_KEY`) | `mov w1, #5` (`EV_SW`) |
| `0x1a8` | `hall_pdrv_probe` | `mov w2, #252` | `mov w2, #0` (`SW_LID`) |
| `0x1b8` | `hall_pdrv_probe` | `orr x8, x8, #0x2` (`evbit \|= EV_KEY`) | `orr x8, x8, #0x20` (`EV_SW`) |
| `0x1c8` | `hall_pdrv_probe` | `mov w1, #1` | `mov w1, #5` |
| `0x1cc` | `hall_pdrv_probe` | `mov w2, #253` | `mov w2, #0` |
| `0x340` | `hall_eint_work`, closed | `mov w1, #1` | `mov w1, #5` |
| `0x344` | | `mov w2, #252` | `mov w2, #0` |
| `0x348` | | `mov w3, #1` | unchanged: `SW_LID` = 1 |
| `0x374` | `hall_eint_work`, closed (key up) | `mov w1, #1` | `mov w1, #5` |
| `0x378` | | `mov w2, #252` | `mov w2, #0` |
| `0x37c` | | `mov w3, wzr` | `mov w3, #1` |
| `0x3d0` | `hall_eint_work`, opened | `mov w1, #1` | `mov w1, #5` |
| `0x3d4` | | `mov w2, #253` | `mov w2, #0` |
| `0x3d8` | | `mov w3, #1` | `mov w3, wzr`: `SW_LID` = 0 |
| `0x400` | `hall_eint_work`, opened (key up) | `mov w1, #1` | `mov w1, #5` |
| `0x404` | | `mov w2, #253` | `mov w2, #0` |
| `0x408` | | `mov w3, wzr` | unchanged |

Each `input_event()` keeps its arguments in `w1` (type), `w2` (code) and `w3`
(value). The second event of each pair (the former key release) now repeats
the new switch value; the input core drops a switch event whose value did not
change, so userspace sees one `SW_LID` change per cover movement. The probe
writes `dev->evbit[0]` (`struct input_dev` offset `0x28`) directly besides
calling `input_set_capability()`, so that bit is moved to `EV_SW` too; the
device no longer advertises `EV_KEY` at all.

Everything else is unchanged: the 100 ms debounce, the edge flip of the
interrupt, the wake-capable interrupt, `pad_hall_status_call_back()`, the
touch panel power-off on close and the `hall_status` driver attribute.

## Known limits

- The driver reports nothing at probe, so if the tablet boots with the cover
  closed the lid reads as open until the cover is opened and closed again
  (same as stock, which also reacts to changes only).
- `PhoneWindowManager` wakes the device on every lid open; the "Smart cover"
  switch in DeviceParts cannot suppress that.

## Verifying on the device

`getevent` needs `adb root` (userdebug); the shell user cannot open `/dev/input`.

```
adb shell getevent -lp            # "hall" lists SW (0005): SW_LID
adb shell getevent -l             # close/open the cover: EV_SW SW_LID 1 / 0
adb shell dumpsys input | grep -A3 ": hall"   # Classes: SWITCH
adb shell dumpsys window | grep mLidState     # LID_CLOSED / LID_OPEN
```
