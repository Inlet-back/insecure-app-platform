#!/usr/bin/env bash
#
# 勉強会の受け口を、公開サイトに «付ける» / «外す»。
#
#   tools/study/set-endpoint.sh https://xxxx.trycloudflare.com/   受け口を付ける
#   tools/study/set-endpoint.sh off                               外す（勉強会のあと必ず）
#
# やること:
#   1. docs/content/study-config.json を書き換える
#   2. その1ファイルだけをコミットする（他の作業中の変更は巻き込まない）
#   3. 公開スナップショットを作り直す
#   4. 最後に push のコマンドを表示する（push はこちらではやらない）
#
# トンネルのURLは起動ごとに変わる。開催直前にこれを実行すること。
set -euo pipefail
cd "$(dirname "$0")/../.."

CONFIG="docs/content/study-config.json"
URL="${1:-}"

if [[ -z "$URL" ]]; then
  echo "使い方: $0 <https://...> | off" >&2
  exit 2
fi

if [[ "$URL" != "off" ]]; then
  if [[ "$URL" != https://* ]]; then
    echo "[NG] https で始まるURLを渡すこと。公開サイトは https なので、http だと弾かれる。" >&2
    exit 1
  fi
  # 合言葉。公開URLなので、これが無いと誰の送信でも受かってしまう。
  # od は必要な3バイトだけ読む。tr|head の形は head がパイプを閉じた瞬間に
  # tr が SIGPIPE で落ち、pipefail と set -e で «無言のまま» 終了してしまう。
  STUDY_ID="workshop-$(date +%Y%m%d)-$(od -An -tx1 -N3 /dev/urandom | tr -d ' \n')"
fi

python3 - "$CONFIG" "$URL" "${STUDY_ID:-}" <<'PY'
import json, sys
path, url, study_id = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, encoding="utf-8") as fp:
    config = json.load(fp)

if url == "off":
    config["protocol_approved"] = False
    config["collection_enabled"] = False
    config["endpoint"] = ""
    config["study_id"] = "insecureappplatform-pilot"
else:
    config["protocol_approved"] = True
    config["collection_enabled"] = True
    config["endpoint"] = url
    config["study_id"] = study_id

with open(path, "w", encoding="utf-8") as fp:
    json.dump(config, fp, ensure_ascii=False, indent=2)
    fp.write("\n")
PY

if [[ "$URL" == "off" ]]; then
  MESSAGE="勉強会の受け口を外す"
else
  MESSAGE="勉強会の受け口を付ける"
fi

# --only を付けて、このファイル «だけ» をコミットする。
# 作業中の他の変更を巻き込むと、意図しないものが公開される。
git commit --only "$CONFIG" -m "$MESSAGE" --quiet
echo "[*] コミットした: $(git log --oneline -1)"
echo

bash tools/verify/make-public-snapshot.sh >/dev/null
echo "[*] 公開スナップショットを作り直した"
echo

echo "== 次にやること =="
echo
echo "  cd ../insecure-app-platform && git add -A && git commit -m \"$MESSAGE\" && git push"
echo

if [[ "$URL" == "off" ]]; then
  echo "これで収集は止まる。同意ページは «送信しない» 状態に戻る。"
  echo "確かめる: python3 -m unittest discover -s study/tests -p 'test_*.py'"
else
  echo "受け口を起動するコマンド（合言葉つき）:"
  echo
  echo "  python3 study/collector/receive.py serve --port 8010 --study-id $STUDY_ID"
  echo
  echo "[!] 勉強会が終わったら必ず  $0 off  を実行すること。"
  echo "    戻し忘れると、同意ページが死んだ受け口に «収集します» と言い続ける。"
  echo "    study/tests のテストが赤いままなら、戻し忘れている。"
fi
