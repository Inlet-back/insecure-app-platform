#!/usr/bin/env bash
#
# エミュレータ / 実機が要る回帰テストを流す。
#
#   tools/verify/connected-tests.sh
#
# 確かめること:
#   app      : Android Keystore の鍵は取り出せないが使えること、
#              鍵を作り直すと古い暗号文が復号できなくなること
#   attacker : 2つのアプリの署名が一致しないこと、
#              signature permission が «要求しても» 付与されないこと、
#              permission なしの Provider は読めて、permission 付きは止まること、
#              別 UID のアプリから被害者の private data を開けないこと
#
# 認証つきの鍵 (setUserAuthenticationRequired) は人間の操作が要るので自動化しない。
# 手順はガイドの第4章 Deep を参照。
set -euo pipefail
cd "$(dirname "$0")/../.."

if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  export JAVA_HOME
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "[!] 端末が繋がっていない。AVD を起動してから実行すること。" >&2
  exit 1
fi

echo "[*] 被害者アプリを入れる（attacker 側のテストが前提にしている）"
./gradlew --quiet :app:installDebug

echo "[*] app の instrumentation test"
./gradlew :app:connectedDebugAndroidTest

echo "[*] attacker の instrumentation test"
./gradlew :attacker:connectedDebugAndroidTest

echo
echo "すべて通った。"
