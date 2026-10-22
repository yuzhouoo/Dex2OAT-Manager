#!/system/bin/sh
MODDIR=${0%/*}
chmod 0700 "$MODDIR/bin/control" "$MODDIR/bin/daemon"
mkdir -p /data/adb/dex2oat-manager
chmod 0700 /data/adb/dex2oat-manager
"$MODDIR/bin/daemon" >/dev/null 2>&1 </dev/null &
DAEMON_PID=$!
# The parent shell owns the child PID; never depend on PID expansion in the daemon script.
for I in 1 2 3 4 5; do
 if test -d /data/adb/dex2oat-manager/daemon.lock && kill -0 "$DAEMON_PID" 2>/dev/null;then
  printf '%s\n' "$DAEMON_PID" > /data/adb/dex2oat-manager/daemon.lock/pid
  break
 fi
 sleep 1
done
