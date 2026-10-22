#!/system/bin/sh
MODDIR=${0%/*}
chmod 0700 "$MODDIR/bin/control" "$MODDIR/bin/daemon"
mkdir -p /data/adb/dex2oat-manager
chmod 0700 /data/adb/dex2oat-manager
("$MODDIR/bin/daemon" >/dev/null 2>&1 </dev/null &)
