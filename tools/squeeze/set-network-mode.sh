#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-}"
PACKAGE="com.example.insecureappplatform.squeeze"
ADB="${ADB:-adb}"

case "$MODE" in
  http|https|pinned) ;;
  *) echo "使い方: $0 http|https|pinned" >&2; exit 2 ;;
esac

DATA_DIR="$("$ADB" shell run-as "$PACKAGE" pwd | tr -d '\r')"
if [[ "$DATA_DIR" != */"$PACKAGE" ]]; then
  echo "Squeezeのデータディレクトリを確認できませんでした: $DATA_DIR" >&2
  exit 1
fi

PREFS_DIR="$DATA_DIR/shared_prefs"
PREFS_FILE="$PREFS_DIR/lab_config.xml"
"$ADB" shell run-as "$PACKAGE" mkdir -p "$PREFS_DIR"
printf '%s' "<map><string name=\"network_mode\">$MODE</string></map>" |
  "$ADB" shell run-as "$PACKAGE" tee "$PREFS_FILE" >/dev/null
"$ADB" shell am force-stop "$PACKAGE"
echo "Squeeze の通信モードを $MODE に設定しました。"
