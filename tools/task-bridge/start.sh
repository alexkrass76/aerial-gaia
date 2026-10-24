#!/system/bin/sh
# Run explicitly through ADB. This script does not register any boot hook.
set -eu
umask 077
bridge_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$bridge_dir/bridge.dex" nohup app_process /system/bin gaia.bridge.TaskBridge "$@" \
  >> "$bridge_dir/helper.log" 2>&1 < /dev/null &
