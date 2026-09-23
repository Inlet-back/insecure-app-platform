/* 研究参加に明示同意した場合だけ、構造化イベントを外部送信する。
   通常の学習記録 (learn.js / localStorage) とは別の保存領域を使う。 */
(() => {
  "use strict";

  if (window.InsecureStudy) return;

  const KEY = "insecureapp.study.v1";
  const ALLOWED = new Set([
    "consent_granted", "consent_withdrawn", "page_view", "chapter_start",
    "prediction_submit", "observation_submit", "practice_quiz_start",
    "practice_quiz_answer", "practice_quiz_complete", "chapter_complete",
    "hint_open", "answer_open", "ai_prompt_copy", "technical_error",
    "assessment_submit",
  ]);
  const META_KEYS = new Set([
    "page", "matched", "expected", "practice_phase", "score", "total",
    "error_kind", "network_state", "referrer_kind",
    // 誤解タグ。どの筋で間違えたかが分かる。正誤だけでは分からない。
    "misconception", "phase", "item_kind",
  ]);
  const sessionId = uuid();
  const pageStarted = performance.now();
  let config = null;
  let flushing = false;

  function uuid() {
    if (crypto.randomUUID) return crypto.randomUUID();
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const h = [...bytes].map((b) => b.toString(16).padStart(2, "0"));
    return `${h.slice(0, 4).join("")}-${h.slice(4, 6).join("")}-${h.slice(6, 8).join("")}-${h.slice(8, 10).join("")}-${h.slice(10).join("")}`;
  }

  const empty = () => ({
    participant_id: null,
    consent: "none",
    consent_version: null,
    consented_at: null,
    sequence: 0,
    queue: [],
  });

  function load() {
    try { return Object.assign(empty(), JSON.parse(localStorage.getItem(KEY) || "{}")); }
    catch (_) { return empty(); }
  }

  function save(state) {
    localStorage.setItem(KEY, JSON.stringify(state));
  }

  function enabled() {
    return !!(config && config.protocol_approved && config.collection_enabled && config.endpoint);
  }

  function chapterFromPath() {
    const match = location.pathname.match(/chapter([1-4])-/);
    return match ? `ch${match[1]}` : "";
  }

  function safeMetadata(value) {
    const out = {};
    if (!value || typeof value !== "object") return out;
    Object.entries(value).forEach(([key, val]) => {
      if (META_KEYS.has(key) && val != null) out[key] = String(val).slice(0, 200);
    });
    return out;
  }

  function record(eventType, fields = {}) {
    if (!ALLOWED.has(eventType) || !enabled()) return false;
    const state = load();
    if (state.consent !== "granted" || !state.participant_id) return false;
    state.sequence += 1;
    state.queue.push({
      event_id: uuid(),
      participant_id: state.participant_id,
      session_id: sessionId,
      content_version: config.content_version,
      study_group: config.study_group || "unassigned",
      client_timestamp: new Date().toISOString(),
      sequence: state.sequence,
      event_type: eventType,
      chapter_id: String(fields.chapter_id || chapterFromPath()).slice(0, 20),
      item_id: String(fields.item_id || "").slice(0, 80),
      attempt: Number.isInteger(fields.attempt) ? fields.attempt : null,
      response: String(fields.response || "").slice(0, 80),
      confidence: Number.isFinite(fields.confidence) ? fields.confidence : null,
      duration_ms: Number.isInteger(fields.duration_ms) ? fields.duration_ms : null,
      metadata: safeMetadata(fields.metadata),
    });
    if (state.queue.length > 500) state.queue = state.queue.slice(-500);
    save(state);
    void flush();
    return true;
  }

  /* queue が空になるまで送り切る。1回の呼び出しで1バッチだけ送ると、
     テストの回答のように一度に何件も積まれたとき残りが滞留し、
     «そのままタブを閉じた参加者の回答が届かない» ことになる。 */
  async function flush() {
    if (flushing || !enabled()) return;
    flushing = true;
    try {
      for (;;) {
        const state = load();
        if (state.consent !== "granted" || !state.queue.length) break;
        const batch = state.queue.slice(0, 50);
        /* no-cors だと応答が読めない。受け口が落ちていて 502 が返っても
           «届いた» と区別できず、queue から消してしまう。
           受け口は CORS を許可しているので、普通のリクエストにして応答を見る。
           Content-Type が text/plain なので preflight は起きない。 */
        const response = await fetch(config.endpoint, {
          method: "POST",
          cache: "no-store",
          headers: { "Content-Type": "text/plain;charset=utf-8" },
          body: JSON.stringify({ study_id: config.study_id, events: batch }),
        });
        if (!response.ok) throw new Error(`受け口が ${response.status} を返した`);
        const latest = load();
        const sent = new Set(batch.map((event) => event.event_id));
        latest.queue = latest.queue.filter((event) => !sent.has(event.event_id));
        save(latest);
      }
    } catch (_) {
      // オフライン時はqueueを残し、onlineイベントまたは次の操作で再送する。
    } finally {
      flushing = false;
    }
  }

  function grantConsent() {
    const state = load();
    state.participant_id = state.participant_id || uuid();
    state.consent = "granted";
    state.consent_version = config ? config.consent_version : null;
    state.consented_at = new Date().toISOString();
    save(state);
    record("consent_granted", { metadata: { page: location.pathname } });
    recordPageView();
    return state.participant_id;
  }

  async function withdraw() {
    const state = load();
    if (state.consent === "granted") {
      record("consent_withdrawn", { metadata: { page: location.pathname } });
      await flush();
    }
    const latest = load();
    latest.consent = "withdrawn";
    latest.queue = [];
    save(latest);
  }

  /* テストは同梱の study-assessment.html で行う。
     回答は assessment_submit イベントとして、章内のログと同じ経路で送る。
     外部フォーム（Google Forms 等）には依存しない。 */
  function formUrl(phase) {
    const state = load();
    if (state.consent !== "granted" || !state.participant_id) return null;
    return `study-assessment.html?phase=${encodeURIComponent(phase)}`;
  }

  function status() {
    const state = load();
    return {
      configured: enabled(),
      collection_enabled: !!(config && config.collection_enabled),
      participant_id: state.participant_id,
      consent: state.consent,
      consent_version: state.consent_version,
      queued: state.queue.length,
      content_version: config ? config.content_version : null,
      contact: config ? config.contact : null,
      retention: config ? config.retention : null,
      ethics_review: config ? config.ethics_review : null,
    };
  }

  function recordPageView() {
    const state = load();
    if (state.consent !== "granted") return;
    record("page_view", {
      metadata: {
        page: location.pathname.split("/").pop() || "index.html",
        network_state: navigator.onLine ? "online" : "offline",
        referrer_kind: document.referrer ? "present" : "none",
      },
    });
  }

  const ready = fetch("content/study-config.json", { cache: "no-cache" })
    .then((response) => {
      if (!response.ok) throw new Error("study-config.jsonを読めません。");
      return response.json();
    })
    .then((value) => {
      config = value;
      if (load().consent === "granted") {
        recordPageView();
        const chapter = chapterFromPath();
        if (chapter) record("chapter_start", { chapter_id: chapter });
      }
      void flush();
      window.dispatchEvent(new CustomEvent("insecure-study-ready"));
      return status();
    })
    .catch((error) => {
      console.warn("研究モードは無効です:", error.message);
      window.dispatchEvent(new CustomEvent("insecure-study-ready"));
      return status();
    });

  /* ページを離れる瞬間の fetch は打ち切られる。sendBeacon なら離脱後も送られる。
     送れたことを確認できないので、queue はそのまま残して次回に再送させる。
     受け側は event_id で重複を落とす。 */
  function beacon() {
    if (!enabled() || !navigator.sendBeacon) return;
    const state = load();
    if (state.consent !== "granted" || !state.queue.length) return;
    const body = new Blob(
      [JSON.stringify({ study_id: config.study_id, events: state.queue.slice(0, 50) })],
      { type: "text/plain;charset=utf-8" },
    );
    navigator.sendBeacon(config.endpoint, body);
  }

  window.addEventListener("online", () => { void flush(); });
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") { void flush(); beacon(); }
  });
  window.addEventListener("pagehide", beacon);
  document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("details").forEach((details, index) => {
      details.addEventListener("toggle", () => {
        if (!details.open) return;
        const summary = details.querySelector(":scope > summary");
        const isAnswer = summary && /答え|判定/.test(summary.textContent || "");
        record(isAnswer ? "answer_open" : "hint_open", { item_id: `details-${index + 1}` });
      });
    });
    document.addEventListener("click", (event) => {
      const link = event.target.closest(".pager a");
      const chapter = chapterFromPath();
      if (!link || !chapter) return;
      record("chapter_complete", {
        chapter_id: chapter,
        duration_ms: Math.round(performance.now() - pageStarted),
      });
    });
  });

  window.InsecureStudy = { ready, status, record, flush, grantConsent, withdraw, formUrl };
})();
