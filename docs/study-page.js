(() => {
  "use strict";

  const byId = (id) => document.getElementById(id);

  async function copy(text, button) {
    try {
      await navigator.clipboard.writeText(text);
      const label = button.textContent;
      button.textContent = "コピーしました";
      setTimeout(() => { button.textContent = label; }, 1400);
    } catch (_) {
      window.prompt("このIDをコピーしてください。", text);
    }
  }

  function render() {
    const api = window.InsecureStudy;
    if (!api) return;
    const state = api.status();
    const status = byId("study-status");
    const consent = byId("study-consent");
    const dashboard = byId("study-dashboard");

    const terms = [
      ["study-contact", "study-contact-row", state.contact],
      ["study-retention", "study-retention-row", state.retention],
      ["study-ethics", "study-ethics-row", state.ethics_review]
    ];
    let hasTerms = false;
    terms.forEach(([valueId, rowId, value]) => {
      const visible = Boolean(value && value.trim());
      byId(rowId).hidden = !visible;
      if (visible) {
        byId(valueId).textContent = value;
        hasTerms = true;
      }
    });
    byId("study-terms").hidden = !hasTerms;

    byId("study-agree").textContent = "同意して参加者IDを作る";

    if (state.consent === "withdrawn") {
      status.className = "callout note";
      status.innerHTML = "研究参加は停止されています。新しい研究イベントは送信しません。";
      consent.hidden = true;
      dashboard.hidden = true;
      return;
    }

    status.className = "callout note";
    status.textContent = state.consent === "granted"
      ? "研究参加への同意が記録されています。"
      : "同意するまで研究データは送信されません。";
    consent.hidden = state.consent === "granted";
    dashboard.hidden = state.consent !== "granted";

    if (state.consent === "granted") {
      byId("study-participant-id").textContent = state.participant_id;
      byId("study-queued").textContent = String(state.queued);
      byId("study-queue-status").hidden = !state.configured;
      [["study-pre", "pre"], ["study-post", "post"], ["study-delayed", "delayed"]]
        .forEach(([id, phase]) => { byId(id).href = api.formUrl(phase); });
    }
  }

  document.addEventListener("DOMContentLoaded", async () => {
    if (!window.InsecureStudy) return;
    await window.InsecureStudy.ready;
    const checks = [...document.querySelectorAll("[data-consent-check]")];
    const agree = byId("study-agree");
    checks.forEach((check) => check.addEventListener("change", () => {
      agree.disabled = !checks.every((item) => item.checked);
    }));
    agree.addEventListener("click", () => {
      try { window.InsecureStudy.grantConsent(); render(); }
      catch (error) { alert(error.message); }
    });
    byId("study-copy-id").addEventListener("click", () => {
      void copy(window.InsecureStudy.status().participant_id, byId("study-copy-id"));
    });
    byId("study-withdraw").addEventListener("click", async () => {
      if (!confirm("研究参加を停止します。送信済みデータは自動削除されません。続けますか。")) return;
      await window.InsecureStudy.withdraw();
      render();
    });
    render();
  });
})();
