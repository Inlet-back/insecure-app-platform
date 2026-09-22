/* ページの操作まわり。学習ループ (learn.js) とは独立していて、どちらが欠けても動く。
   fetch を使わないので、配信の仕方に関係なく動く。

   1. コードブロックのコピーボタン
   2. macOS / Windows の切り替えタブ

   どちらも、対象がページに無ければ何もしない。 */
(() => {
  "use strict";

  /* ============================================================ コピーボタン */

  /* 出力例 (pre.out) には付けない。打つものと、返ってくるものを取り違えさせないため。 */
  function addCopyButtons() {
    document.querySelectorAll("main pre:not(.out)").forEach((pre) => {
      if (pre.parentElement && pre.parentElement.classList.contains("code-wrap")) return;

      /* pre の中にボタンを置くと、長いコマンドを横スクロールしたときに
         ボタンも一緒に流れて画面の外へ出る。スクロールしない «外側» に置く。 */
      const wrap = document.createElement("div");
      wrap.className = "code-wrap";
      pre.parentNode.insertBefore(wrap, pre);
      wrap.appendChild(pre);

      const btn = document.createElement("button");
      btn.className = "copy-btn";
      btn.type = "button";
      btn.textContent = "コピー";
      btn.setAttribute("aria-label", "このコマンドをコピーする");

      btn.addEventListener("click", async () => {
        const code = pre.querySelector("code");
        const text = (code ? code.textContent : pre.textContent).replace(/\s+$/, "");
        const ok = await copy(text);
        btn.textContent = ok ? "コピーした" : "できなかった";
        btn.classList.add(ok ? "is-done" : "is-failed");
        setTimeout(() => {
          btn.textContent = "コピー";
          btn.classList.remove("is-done", "is-failed");
        }, 1800);
      });

      wrap.appendChild(btn);
    });
  }

  async function copy(text) {
    try {
      if (!navigator.clipboard) throw new Error("no clipboard api");
      // 安全なコンテキスト (https と localhost) でだけ使える。
      // 許可待ちなどで «いつまでも返ってこない» ことがあるので、待つ時間を区切る。
      // 区切らないとボタンが「コピー」のまま固まり、押せたのかどうか分からなくなる。
      await Promise.race([
        navigator.clipboard.writeText(text),
        new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), 1200)),
      ]);
      return true;
    } catch (e) {
      // 古いブラウザや、http で配信された場合はこちらへ落ちる
      try {
        const ta = document.createElement("textarea");
        ta.value = text;
        ta.setAttribute("readonly", "");
        ta.style.position = "fixed";
        ta.style.top = "-1000px";
        document.body.appendChild(ta);
        ta.select();
        const ok = document.execCommand("copy");
        document.body.removeChild(ta);
        return ok;
      } catch (e2) {
        return false;
      }
    }
  }

  /* ============================================================ OS の切り替え */

  const OS_KEY = "insecureapp.os";
  const OS = [
    { id: "mac", label: "macOS" },
    { id: "win", label: "Windows" },
  ];

  function guessOS() {
    const p = (navigator.userAgentData && navigator.userAgentData.platform) ||
              navigator.platform || "";
    if (/win/i.test(p)) return "win";
    return "mac";
  }

  function readOS() {
    try {
      const v = localStorage.getItem(OS_KEY);
      if (OS.some((o) => o.id === v)) return v;
    } catch (e) { /* 保存が止められていても動かす */ }
    return guessOS();
  }

  function writeOS(id) {
    try { localStorage.setItem(OS_KEY, id); }
    catch (e) { /* 覚えられないだけで、今の表示は切り替わる */ }
  }

  /* data-os を持つ要素の出し入れ。
     隠す指定は html.os-ready の下にしか書いていないので、
     JS が動かなければ macOS と Windows の «両方» が見えたままになる。 */
  function applyOS(id) {
    document.querySelectorAll("[data-os]").forEach((el) => {
      el.classList.toggle("is-on", el.dataset.os === id);
    });
    document.querySelectorAll("[data-os-switch] .os-tab").forEach((b) => {
      const on = b.dataset.osTarget === id;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
  }

  function setupOSSwitch() {
    const holders = document.querySelectorAll("[data-os-switch]");
    if (!holders.length) return;
    if (!document.querySelector("[data-os]")) return;

    holders.forEach((holder) => {
      holder.textContent = "";
      holder.setAttribute("role", "tablist");
      OS.forEach((o) => {
        const b = document.createElement("button");
        b.className = "os-tab";
        b.type = "button";
        b.dataset.osTarget = o.id;
        b.textContent = o.label;
        b.setAttribute("role", "tab");
        b.addEventListener("click", () => {
          writeOS(o.id);
          applyOS(o.id);
        });
        holder.appendChild(b);
      });
    });

    document.documentElement.classList.add("os-ready");
    applyOS(readOS());
  }

  /* ============================================================ 起動 */

  setupOSSwitch();
  addCopyButtons();
})();
