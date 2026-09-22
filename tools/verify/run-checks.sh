#!/usr/bin/env bash
#
# 教材の結論を守るチェックを、1コマンドでまとめて回す。
#
#   tools/verify/run-checks.sh
#
# ここに入るのは «端末なしで» 確かめられるものだけ。
# エミュレータが要るものは connected-tests.sh のほうに置いてある。
set -euo pipefail
cd "$(dirname "$0")/../.."

if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  # JDK 25 などが既定になっていると Gradle が一行だけのエラーで落ちる
  JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  export JAVA_HOME
fi
[[ -n "${JAVA_HOME:-}" ]] && echo "[*] JAVA_HOME = $JAVA_HOME"

fail=0
step() {
  echo
  echo "=============================================================="
  echo "== $1"
  echo "=============================================================="
  shift
  if "$@"; then
    echo "[OK]"
  else
    echo "[NG] 上のコマンドが失敗した"
    fail=1
  fi
}

step "ビルド・ユニットテスト・lint" \
  ./gradlew --quiet clean test lint :app:assembleDebug :attacker:assembleDebug

step "2つの APK の署名が別物であること" \
  ./tools/verify/verify-signatures.sh

step "ガイドの JavaScript が構文的に妥当であること" \
  bash -c 'for f in docs/*.js tools/frida/*.js; do node --check "$f" || exit 1; done'

step "モックサーバが構文的に妥当であること" \
  python3 -m py_compile tools/mockserver/server.py

step "教材の証明書が揃っていること" \
  bash -c 'for f in ca-cert.pem server-cert.pem server-cert-expired.pem server-key.pem; do
             [[ -f "tools/mockserver/$f" ]] || { echo "ない: $f"; exit 1; }
           done'

echo
if [[ $fail -eq 0 ]]; then
  echo "すべて通った。"
  echo "エミュレータ上の確認は tools/verify/connected-tests.sh を実行すること。"
else
  echo "失敗がある。上を見ること。" >&2
  exit 1
fi
