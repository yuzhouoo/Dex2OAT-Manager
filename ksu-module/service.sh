#!/system/bin/sh
MODDIR=${0%/*}
ROOT=/data/adb/dex2oat-manager
mkdir -p "$ROOT"
chmod 0700 "$ROOT"
chmod 0700 "$MODDIR/bin/control" "$MODDIR/bin/daemon"
printf '%s service-launch\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$ROOT/startup.log"
"$MODDIR/bin/daemon" >> "$ROOT/startup.log" 2>&1 </dev/null &
