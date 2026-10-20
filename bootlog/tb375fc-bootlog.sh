#!/system/bin/sh
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Boot logger for userdebug and eng builds, started by tb375fc-bootlog.rc.
# Keeps logcat, dmesg, properties and the process list of the current and
# the previous boot in /metadata/tb375fc_bootlog, so that a boot that never
# brings up ADB can still be examined from LineageOS Recovery. pstore does
# not help here: ramoops from a normal boot is empty after a forced reset, and
# the bootloader may instead restore an older ramoops copy saved in expdb.

dir=/metadata/tb375fc_bootlog
mkdir -p "$dir"

rm -rf "$dir/boot.1"
[ -d "$dir/boot.0" ] && mv "$dir/boot.0" "$dir/boot.1"
out="$dir/boot.0"
mkdir "$out"

# logd is started before this service but may not accept readers yet, so
# restart logcat until it stays up. It appends to an existing file.
(
    while true; do
        logcat -b all -v threadtime -v uptime -f "$out/logcat.txt" -r 1024 -n 2
        sleep 1
    done
) &

while true; do
    dmesg | tail -c 2097152 > "$out/dmesg.tmp" && mv "$out/dmesg.tmp" "$out/dmesg.txt"
    getprop > "$out/getprop.txt"
    ps -A > "$out/ps.txt"
    sleep 10
done
