/* サイトのナビ。ここが唯一の定義で、各ページは <nav class="topnav" data-nav></nav> だけ置く。
   ページを増やしたときに直す場所を1箇所にするためのもの。
   fetch を使わないので file:// でも描画される（設問の JSON とは事情が違う）。 */
(() => {
  "use strict";

  const BRAND = { href: "index.html", label: "InsecureAppPlatform" };

  const PAGES = [
    { href: "study.html", label: "研究参加" },
    { href: "intro-sandbox.html", label: "導入" },
    { href: "setup.html", label: "環境構築" },
    { href: "learning-loop.html", label: "学習ループ" },
    { href: "chapter1-process.html", label: "Ch1: プロセス境界" },
    { href: "chapter2-ipc.html", label: "Ch2: IPC境界" },
    { href: "chapter3-network.html", label: "Ch3: ネットワーク境界" },
    { href: "chapter4-tee.html", label: "Ch4: Keystoreと鍵の使用" },
    { href: "surface-map.html", label: "攻撃面マップ" },
    { href: "closing.html", label: "終章" },
    { href: "progress.html", label: "記録" },
    { href: "https://github.com/Inlet-back/insecure-app-platform", label: "リポジトリ" },
  ];

  const here = location.pathname.split("/").pop() || "index.html";

  function link(page, isBrand) {
    const a = document.createElement("a");
    a.href = page.href;
    a.textContent = page.label;
    // 外部リンク（リポジトリ）は別タブ。読んでいる途中で教材から出ていかないようにする
    if (/^https?:/.test(page.href)) { a.target = "_blank"; a.rel = "noopener"; }
    if (isBrand) a.className = "brand";
    // 「今いるページ」に印を付ける。色は CSS 側が付ける
    else if (page.href === here) a.setAttribute("aria-current", "page");
    return a;
  }

  document.querySelectorAll("nav[data-nav]").forEach((nav) => {
    nav.textContent = "";
    nav.appendChild(link(BRAND, true));
    PAGES.forEach((p) => nav.appendChild(link(p)));
  });

  // 研究ログは別ファイルに隔離する。同意・送信先がない既定状態では外部送信しない。
  if (!window.InsecureStudy && !document.querySelector('script[src^="study.js"]')) {
    const script = document.createElement("script");
    script.src = "study.js?v=20260922i";
    script.defer = true;
    document.head.appendChild(script);
  }
})();
