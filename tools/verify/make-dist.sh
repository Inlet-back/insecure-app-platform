#!/usr/bin/env bash
#
# 配布用の ZIP を作る。
#
#   tools/verify/make-dist.sh [出力先ディレクトリ]
#
# Git が追跡しているファイルだけを入れる (git archive)。
# そのため次のものは構造的に入らない:
#   build/ .gradle/ .idea/workspace.xml .DS_Store __pycache__/ local.properties
#   (いずれも .gitignore 済み)
#
# ただし keys/*.p12 «だけ» は、git archive のあとから足す。
# 配った APK と、参加者が第3章で再ビルドした APK の署名を «一致させる» ため。
# 鍵が無いと TeachingKeystore が各自の環境で新しい鍵を作り、署名が変わるので、
# 再インストールが INSTALL_FAILED_UPDATE_INCOMPATIBLE で落ちる。
# ここで配るのは教材専用の使い捨ての鍵であり、本番には使わない。
#
# 作ったあと、別のディレクトリへ展開してビルドが通るところまで確かめる。
set -euo pipefail
cd "$(dirname "$0")/../.."

OUT_DIR="${1:-dist}"
NAME="InsecureAppPlatform-$(date +%Y%m%d)"
ZIP="$OUT_DIR/$NAME.zip"

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "[!] コミットしていない変更がある。git archive は HEAD の内容だけを入れる。" >&2
  echo "    このまま続けると、いま手元にある変更は配布物に入らない。" >&2
  read -r -p "    続ける? [y/N] " ans
  [[ "$ans" == "y" || "$ans" == "Y" ]] || exit 1
fi

# 追跡されていないのに残っていると事故になるものを先に知らせる
leftovers=$(git status --porcelain --ignored 2>/dev/null \
  | awk '$1=="!!"{print $2}' \
  | grep -E '(^|/)(build|\.gradle|__pycache__)/' || true)
if [[ -n "$leftovers" ]]; then
  echo "[*] 次は .gitignore 済みなので配布物には入らない:"
  echo "$leftovers" | sed 's/^/      /'
fi

mkdir -p "$OUT_DIR"
git archive --format=zip --prefix="$NAME/" -o "$ZIP" HEAD
echo "[*] 作成: $ZIP"

# 署名鍵を足す (上のヘッダの理由)。無ければ先にビルドしてもらう。
KEYS=(keys/victim-teaching.p12 keys/attacker-teaching.p12)
for k in "${KEYS[@]}"; do
  if [[ ! -f "$k" ]]; then
    echo "[NG] $k が無い。配布物の APK と参加者の再ビルドで署名が食い違う。" >&2
    echo "     先に ./gradlew :app:assembleDebug :attacker:assembleDebug を実行すること。" >&2
    rm -f "$ZIP"
    exit 1
  fi
done
ZIP_ABS="$(cd "$(dirname "$ZIP")" && pwd)/$(basename "$ZIP")"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/$NAME/keys"
cp "${KEYS[@]}" "$STAGE/$NAME/keys/"
( cd "$STAGE" && zip -q -r "$ZIP_ABS" "$NAME/keys" )
echo "[*] 教材用の署名鍵を同梱した: keys/"

echo
echo "[*] 中身に入ってはいけないものが無いか確かめる"
# keys/*.p12 は «意図して» 入れている。ここで見張るのはそれ以外。
bad=$(unzip -Z1 "$ZIP" | grep -E '(/build/|/\.gradle/|workspace\.xml|\.DS_Store|__pycache__|local\.properties|ca-key\.pem)' || true)
if [[ -n "$bad" ]]; then
  echo "[NG] 次が入っている:" >&2
  echo "$bad" | sed 's/^/      /' >&2
  exit 1
fi
echo "[OK] ビルド成果物・IDE の状態・環境固有ファイルは入っていない。"

echo
echo "次にやること: クリーンな別ディレクトリへ展開し、そこでビルドが通るまで確かめる。"
echo "  unzip -q $ZIP -d /tmp/dist-check && cd /tmp/dist-check/$NAME && ./gradlew assembleDebug"
