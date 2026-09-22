#!/usr/bin/env bash
#
# 被害者アプリと攻撃者アプリが «別々の証明書» で署名されていることを確かめる。
#
# 第2章の結論（signature permission を宣言しても攻撃者アプリには付与されない）は、
# この1点に依存している。両方を既定の debug keystore で署名すると証明書が一致し、
# permission が付与されて結論が逆転する。
#
#   tools/verify/verify-signatures.sh
#
# 前提: APK がビルド済みであること。無ければ自動でビルドする。
set -euo pipefail
cd "$(dirname "$0")/../.."

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
ATTACKER_APK="attacker/build/outputs/apk/debug/attacker-debug.apk"

if [[ ! -f "$APP_APK" || ! -f "$ATTACKER_APK" ]]; then
  echo "[*] APK がないのでビルドする"
  ./gradlew --quiet :app:assembleDebug :attacker:assembleDebug
fi

find_apksigner() {
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return
  fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  # build-tools の中でいちばん新しいものを使う
  local found
  found=$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
  if [[ -z "$found" ]]; then
    echo "[!] apksigner が見つからない。Android SDK の build-tools を入れること。" >&2
    exit 1
  fi
  echo "$found"
}

APKSIGNER=$(find_apksigner)

cert_sha256() { # $1 = apk
  "$APKSIGNER" verify --print-certs "$1" 2>/dev/null \
    | awk -F': ' '/certificate SHA-256 digest/ { print $2; exit }'
}

APP_SHA=$(cert_sha256 "$APP_APK")
ATTACKER_SHA=$(cert_sha256 "$ATTACKER_APK")

echo "被害者アプリ   : $APP_SHA"
echo "攻撃者アプリ   : $ATTACKER_SHA"
echo

if [[ -z "$APP_SHA" || -z "$ATTACKER_SHA" ]]; then
  echo "[NG] 証明書を読み取れなかった。" >&2
  exit 1
fi

if [[ "$APP_SHA" == "$ATTACKER_SHA" ]]; then
  cat >&2 <<'MSG'
[NG] 2つの APK が同じ証明書で署名されている。

     このままだと、攻撃者アプリが宣言した READ_SESSION が «付与されてしまい» 、
     第2章 P4 が成功してしまう。教材の結論と逆になる。

     直し方:
       rm -rf keys/
       ./gradlew :app:assembleDebug :attacker:assembleDebug
MSG
  exit 1
fi

echo "[OK] 2つの APK の証明書は別物。第2章の signature permission の実験が成立する。"
