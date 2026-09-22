#!/usr/bin/env python3
"""文章のリズムを測る。WRITING-STYLE.md の根拠になっている指標。

  python3 tools/style/rhythm.py                # docs/ の主要ページ
  python3 tools/style/rhythm.py path [path...] # 任意のファイル

平均文長が揃っていて「80字超」が0本だと、平坦で目が滑る文章になる。
比較用に本人の文章 (~/Downloads/qiita_article.md) を一緒に測るとよい。
"""
import os
import re
import statistics as st
import sys

DEFAULT = ["docs/intro-sandbox.html", "docs/chapter1-process.html",
           "docs/chapter2-ipc.html", "docs/chapter3-network.html",
           "docs/chapter4-tee.html", "docs/closing.html"]

PATTERNS = {
    "対句(〜ではない。)": r"ではない。|ではありません。",
    "体言止め(〜こと。)": r"こと。",
    "ダッシュ(——)": r"——",
    "常体(〜だ。/である)": r"[^。]だ。|である。",
}


def sentences(text):
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    text = re.sub(r"<pre>.*?</pre>", "", text, flags=re.S)
    text = re.sub(r"<[^>]+>", "", text)
    # HTML/Markdown のソース上の改行は文の切れ目ではない。段落境界(空行)だけを切れ目とし、
    # 段落内の改行は詰める。これをやらないと、折り返しただけの1文が複数文に数えられる。
    text = re.sub(r"\n\s*\n", "\u3002\n", text)
    text = re.sub(r"\n[ \t]*", "", text)
    return [s.strip() for s in re.split(r"[。\n]", text) if len(s.strip()) > 4]


def main(paths):
    print(f"{'file':26}{'文数':>5}{'平均':>7}{'ばらつき':>9}{'80字超':>7}{'強調/100文':>11}")
    for p in paths:
        if not os.path.exists(p):
            print(f"{p:26} (見つかりません)")
            continue
        raw = open(p).read()
        s = sentences(raw)
        if not s:
            continue
        L = [len(x) for x in s]
        emph = len(re.findall(r"\*\*|<b>", raw))
        print(f"{os.path.basename(p):26}{len(s):5}{st.mean(L):7.1f}"
              f"{st.pstdev(L):9.1f}{sum(1 for x in L if x > 80):7}"
              f"{emph / len(s) * 100:11.1f}")
        hits = {n: len(re.findall(r, raw)) for n, r in PATTERNS.items()}
        hits = {n: c for n, c in hits.items() if c}
        if hits:
            print("      " + "  ".join(f"{n}:{c}" for n, c in hits.items()))


if __name__ == "__main__":
    main(sys.argv[1:] or DEFAULT)
