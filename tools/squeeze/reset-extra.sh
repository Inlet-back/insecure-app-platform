#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
"$ADB" shell pm clear com.example.insecureappplatform.squeeze >/dev/null
"$ADB" shell pm clear com.example.squeeze.attacker >/dev/null
echo "Squeeze Extra のアプリデータを初期化しました。"
