#!/usr/bin/env bash
# PR #821 adapted for Aerial UI. Only shell-authorized ConfigReceiver operations.
set -euo pipefail
adb_bin=${ADB:-adb}
adb_args=()
if [[ -n ${TV:-} ]]; then
  target=$TV
  [[ $target == *:* ]] || target="$target:5555"
  "$adb_bin" connect "$target" >/dev/null
  adb_args=(-s "$target")
fi
cmd=${1:-help}
args=(--es agent "$cmd")
case "$cmd" in
  set) [[ $# == 3 ]] || { echo 'usage: agent.sh set <key> <value>' >&2; exit 2; }; args+=(--es key "$2" --es value "$3");;
  open|focus) [[ $# == 2 ]] || { echo 'usage: agent.sh open|focus <target>' >&2; exit 2; }; args+=(--es target "$2");;
  state|settings|help) [[ $# -le 1 ]] || exit 2;;
  *) echo 'unknown command' >&2; exit 2;;
esac
# adb shell joins arguments into a shell command. Accept only token characters, never shell syntax.
for token in "${args[@]}"; do [[ $token =~ ^[a-zA-Z0-9_.:/-]+$ ]] || { echo 'invalid argument' >&2; exit 2; }; done
"$adb_bin" "${adb_args[@]}" shell am broadcast -a app.orchard.tv.CONFIG \
  -n app.orchard.tv.gaia/app.orchard.tv.guard.ConfigReceiver "${args[@]}"
