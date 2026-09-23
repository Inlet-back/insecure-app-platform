(() => {
  "use strict";

  const STORE_KEY = "insecureapp.assessments.v1";
  const WORKSHOP_FEEDBACK_URL = "https://docs.google.com/forms/d/e/1FAIpQLSfPBHEn_a5iDwuQMrTgk1vdRUSMyYysfhZgRgfFN5l2-eOTzg/viewform?usp=header";
  // 二重送信よけ。押しても画面が変わらないように見えて2回押される事故が起きる。
  let submitting = false;
  const phase = new URLSearchParams(location.search).get("phase");
  const allowedPhases = new Set(["pre", "post", "delayed", "survey"]);
  const storagePhase = phase === "survey" ? "survey_post_workshop_v3" : phase;
  const byId = (id) => document.getElementById(id);

  function loadSaved() {
    try { return JSON.parse(localStorage.getItem(STORE_KEY) || "{}"); }
    catch (_) { return {}; }
  }

  function saveAnswers(value) {
    const saved = loadSaved();
    if (saved[storagePhase]) throw new Error("別の画面で回答が確定されています。ページを再読み込みしてください。");
    saved[storagePhase] = value;
    localStorage.setItem(STORE_KEY, JSON.stringify(saved));
  }

  /* 回答を1項目1件のイベントとして送る。研究モードが無効なら record() が何もしない。
     送るのは選択した値と確信度だけで、自由記述は持たせない。 */
  function sendAnswers(instruments, instrument, background, answers, confidence) {
    const api = window.InsecureStudy;
    if (!api) return;
    instruments.background.forEach((item) => {
      if (background[item.id] == null) return;
      api.record("assessment_submit", {
        item_id: item.id,
        response: background[item.id],
        metadata: { phase, item_kind: "background" },
      });
    });
    instrument.items.forEach((item) => {
      if (answers[item.id] == null) return;
      api.record("assessment_submit", {
        item_id: item.id,
        response: String(answers[item.id]),
        confidence: confidence[item.id],
        metadata: { phase: phase === "survey" ? "post" : phase, item_kind: phase === "survey" ? "survey" : "item" },
      });
    });
    void api.flush();
  }

  function addHeading(text, description) {
    const heading = document.createElement("h2");
    heading.textContent = text;
    byId("assessment-form").appendChild(heading);
    if (description) {
      const paragraph = document.createElement("p");
      paragraph.textContent = description;
      byId("assessment-form").appendChild(paragraph);
    }
  }

  function showNext(completed = false) {
    byId("assessment-next").hidden = false;
    if (completed && phase === "post") {
      byId("assessment-next-link").href = "study-assessment.html?phase=survey";
      byId("assessment-next-link").textContent = "続けて事後アンケートへ";
    } else if (completed && phase === "survey") {
      byId("assessment-next-link").href = WORKSHOP_FEEDBACK_URL;
      byId("assessment-next-link").target = "_blank";
      byId("assessment-next-link").rel = "noopener";
      byId("assessment-next-link").textContent = "最後に、匿名の勉強会アンケートへ";
    }
  }

  function renderSurveyItem(item, previousSection) {
    if (item.section && item.section !== previousSection) {
      addHeading(item.section, "実際に行った範囲について回答してください。完了していなくても問題ありません。");
    }
    const fieldset = document.createElement("fieldset");
    fieldset.className = "study-question";
    const legend = document.createElement("legend");
    legend.textContent = item.prompt;
    fieldset.appendChild(legend);
    const choices = item.options
      ? item.options.map((option) => [option, option])
      : Array.from({ length: item.scale[1] - item.scale[0] + 1 }, (_, i) => {
        const value = item.scale[0] + i;
        return [String(value), `${value}${value === 1 ? "：全くそう思わない" : value === 5 ? "：とてもそう思う" : ""}`];
      });
    choices.push(["", "該当しない・回答しない"]);
    choices.forEach(([value, text]) => {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "radio";
      input.name = item.id;
      input.value = value;
      label.append(input, ` ${text}`);
      fieldset.appendChild(label);
    });
    return fieldset;
  }

  function renderBackgroundItem(item, previous) {
    const fieldset = document.createElement("fieldset");
    fieldset.className = "study-question";
    const legend = document.createElement("legend");
    legend.textContent = item.prompt;
    fieldset.appendChild(legend);
    item.options.forEach((option) => {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "radio";
      input.name = item.id;
      input.value = option;
      input.required = true;
      input.checked = previous && previous.background && previous.background[item.id] === option;
      label.append(input, ` ${option}`);
      fieldset.appendChild(label);
    });
    return fieldset;
  }

  function renderItem(item, index, previous) {
    const fieldset = document.createElement("fieldset");
    fieldset.className = "study-question";
    const legend = document.createElement("legend");
    legend.textContent = `${index + 1}. ${item.prompt}`;
    fieldset.appendChild(legend);

    item.options.forEach((option, optionIndex) => {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "radio";
      input.name = item.id;
      input.value = String(optionIndex);
      input.required = true;
      input.checked = previous && previous.answers && previous.answers[item.id] === optionIndex;
      label.append(input, ` ${option}`);
      fieldset.appendChild(label);
    });

    const confidenceLabel = document.createElement("label");
    confidenceLabel.textContent = "この回答への確信度（0: 全く確信がない〜10: 非常に確信がある） ";
    const confidence = document.createElement("select");
    confidence.name = `${item.id}-confidence`;
    confidence.required = true;
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "選択してください";
    confidence.appendChild(placeholder);
    for (let value = 0; value <= 10; value += 1) {
      const option = document.createElement("option");
      option.value = String(value);
      option.textContent = String(value);
      option.selected = previous && previous.confidence && previous.confidence[item.id] === value;
      confidence.appendChild(option);
    }
    confidenceLabel.appendChild(confidence);
    fieldset.appendChild(confidenceLabel);
    return fieldset;
  }

  async function start() {
    const message = byId("assessment-message");
    if (!allowedPhases.has(phase)) {
      message.hidden = false;
      message.textContent = "テストを開けませんでした。研究参加ページから選び直してください。";
      return;
    }

    try {
      await window.InsecureStudy.ready;
      if (window.InsecureStudy.status().consent !== "granted") {
        message.hidden = false;
        message.textContent = "回答には研究参加への同意が必要です。研究参加ページで説明を確認してください。事前回答済みの方は同じ端末・ブラウザで開いてください。";
        showNext();
        return;
      }
      const response = await fetch("content/instruments.json", { cache: "no-cache" });
      if (!response.ok) throw new Error("load failed");
      const instruments = await response.json();
      const instrument = phase === "survey" ? {
        title: "勉強会後のアンケート",
        description: "目安3〜5分。全章を終えている必要はありません。実際に扱った範囲、参加方法、行った活動、途中で止まった理由を回答してください。全項目任意です。",
        items: instruments.post_survey,
      } : instruments.phases[phase];
      byId("assessment-title").textContent = instrument.title;
      byId("assessment-description").textContent = instrument.description;
      const previous = loadSaved()[storagePhase];
      if (previous) {
        message.hidden = false;
        message.innerHTML = "<strong>この回答はこのブラウザに保存済みです。</strong><br>" +
          "研究の回答なので、確定したあとの書き換えはできません。" +
          "研究参加ページに戻り、次のステップへ進んでください。";
        showNext(true);
        return;
      }
      if (phase === "pre") {
        addHeading("最初に、あなたについて", "ここには正解がありません。現在の経験にいちばん近いものを選んでください。");
        instruments.background.forEach((item) => {
          byId("assessment-form").appendChild(renderBackgroundItem(item, previous));
        });
        addHeading("5つの短い場面", "知らない言葉を問う問題ではありません。いちばん近いと思う答えと、その確信度を選んでください。");
      }
      let surveySection = null;
      instrument.items.forEach((item, index) => {
        if (phase === "survey") {
          byId("assessment-form").appendChild(renderSurveyItem(item, surveySection));
          if (item.section) surveySection = item.section;
        } else {
          byId("assessment-form").appendChild(renderItem(item, index, previous));
        }
      });
      byId("assessment-actions").hidden = false;

      byId("assessment-form").addEventListener("submit", (event) => {
        event.preventDefault();
        if (submitting) return;
        submitting = true;
        byId("assessment-submit").disabled = true;
        const formData = new FormData(event.currentTarget);
        const answers = {};
        const confidence = {};
        const background = {};
        if (phase === "pre") {
          instruments.background.forEach((item) => { background[item.id] = formData.get(item.id); });
        }
        instrument.items.forEach((item) => {
          const value = formData.get(item.id);
          answers[item.id] = phase === "survey" ? (value || null) : Number(value);
          if (phase !== "survey") confidence[item.id] = Number(formData.get(`${item.id}-confidence`));
        });
        try {
          const state = window.InsecureStudy.status();
          if (state.consent !== "granted") throw new Error("研究参加が停止されています。研究参加ページへ戻ってください。");
          saveAnswers({ submitted_at: new Date().toISOString(), participant_id: state.participant_id,
            content_version: instruments.content_version, background, answers, confidence });
        } catch (error) {
          submitting = false;
          byId("assessment-submit").disabled = false;
          message.hidden = false;
          message.textContent = `回答を確定できませんでした。${error.message}`;
          return;
        }
        let queued = true;
        try { sendAnswers(instruments, instrument, background, answers, confidence); }
        catch (_) { queued = false; }
        event.currentTarget.hidden = true;
        byId("assessment-actions").hidden = true;
        message.hidden = false;
        message.textContent = queued
          ? "回答をこのブラウザに保存しました。自動送信が有効な場合は送信処理を行います。送信待ちの件数は研究参加ページで確認できます。"
          : "回答はこのブラウザに保存しましたが、送信処理に失敗しました。このブラウザを保持し、主催者へ連絡してください。";
        showNext(true);
      });
    } catch (_) {
      message.hidden = false;
      message.textContent = "テストを読み込めませんでした。ページを再読み込みしてください。";
    }
  }

  document.addEventListener("DOMContentLoaded", () => { void start(); });
})();
