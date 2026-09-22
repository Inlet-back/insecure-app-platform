#!/usr/bin/env bash
# 第3章で使う教材専用の証明書一式を作り直す。
#
# ここで作る鍵と証明書は «使い捨ての教材用» であり、リポジトリにそのまま入っている。
# 秘密鍵が公開されている前提の材料なので、本番・検証環境を含め、
# この教材のモックサーバ (10.0.2.2 のループバック) 以外に絶対に使わないこと。
#
# 生成物:
#   ca-cert.pem            教材用 CA。アプリ側が trust anchor として同梱する
#   ca-key.pem             その秘密鍵。実行のたびに作り直すのでコミットも配布もしない
#                          (これがあると、アプリが信頼する CA の «正規の» 証明書を
#                           誰でも作れてしまう)
#   server-cert.pem        10.0.2.2 のサーバ証明書 (CA が署名、有効)
#   server-cert-expired.pem 同じ鍵で作った «期限切れ» のサーバ証明書
#   server-key.pem         サーバの秘密鍵 (有効版・期限切れ版で共通)
#
# 有効版と期限切れ版で鍵を共有しているのは、
# 「公開鍵ピンは一致するのに、通常の TLS 検証で落ちる」状態を作るため。
set -euo pipefail
cd "$(dirname "$0")"

SUBJ_CA="/CN=InsecureAppPlatform Teaching CA/O=Teaching Material (throwaway)/C=JP"
SUBJ_SRV="/CN=10.0.2.2/O=Teaching Material (throwaway)/C=JP"
SAN="subjectAltName=IP:10.0.2.2,IP:127.0.0.1,DNS:localhost"

openssl req -x509 -newkey rsa:2048 -nodes -sha256 \
  -keyout ca-key.pem -out ca-cert.pem -days 3650 \
  -subj "$SUBJ_CA" -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"

openssl req -new -newkey rsa:2048 -nodes -sha256 \
  -keyout server-key.pem -out server.csr -subj "$SUBJ_SRV"

sign() { # $1=出力 $2..=追加の openssl x509 オプション
  local out="$1"; shift
  openssl x509 -req -in server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out "$out" -sha256 \
    -extfile <(printf '%s\nbasicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n' "$SAN") \
    "$@"
}

sign server-cert.pem -days 3650
# 期限切れ版: 2年前に発行され、1年前に切れている
sign server-cert-expired.pem \
  -not_before "$(date -u -v-2y +%Y%m%d%H%M%SZ 2>/dev/null || date -u -d '2 years ago' +%Y%m%d%H%M%SZ)" \
  -not_after  "$(date -u -v-1y +%Y%m%d%H%M%SZ 2>/dev/null || date -u -d '1 year ago'  +%Y%m%d%H%M%SZ)"

rm -f server.csr ca-cert.srl

# アプリが trust anchor として同梱している CA を、いま作ったものへ差し替える。
# ここを忘れると、アプリは «古い CA» を信頼したまま «新しい CA» が署名した
# サーバ証明書に出会うことになり、第3章 手順4 と同じ Trust anchor not found が出る。
# 原因がガイドの想定と同じ見た目になるので、たどり着くのが難しい。
APP_CA="../../app/src/main/res/raw/mockserver_ca.pem"
cp ca-cert.pem "$APP_CA"
echo "[*] アプリ同梱の CA を更新した: app/src/main/res/raw/mockserver_ca.pem"
echo "[!] アプリの «再ビルドと再インストール» が要る:"
echo "      ./gradlew :app:installDebug"
echo "    これをやらないと Trust anchor not found で落ちる。"

echo
echo "[*] 公開鍵ピン (NetworkActivity.expectedPin に貼る):"
openssl x509 -in server-cert.pem -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | base64
