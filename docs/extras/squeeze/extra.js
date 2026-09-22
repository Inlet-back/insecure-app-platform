(() => {
  const pages = [
    ["index.html", "概要"], ["setup.html", "準備"], ["storage.html", "保存領域"],
    ["ipc.html", "IPC"], ["network.html", "通信"], ["crypto.html", "鍵管理"]
  ];
  const here = location.pathname.split("/").pop() || "index.html";
  document.querySelectorAll("nav[data-extra-nav]").forEach((nav) => {
    nav.innerHTML = `<a class="brand" href="../../index.html">InsecureAppPlatform</a>` +
      `<a href="../../index.html">Core教材へ戻る</a>` +
      pages.map(([href, label]) =>
        `<a href="${href}"${href === here ? ' aria-current="page"' : ""}>Extra: ${label}</a>`
      ).join("");
  });
})();
