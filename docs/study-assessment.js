(() => {
  "use strict";

  const STORE_KEY = "insecureapp.assessments.v1";
  const phase = new URLSearchParams(location.search).get("phase");
  const allowedPhases = new Set(["pre", "post", "delayed"]);
  const byId = (id) => document.getElementById(id);

  function loadSaved() {
    try { return JSON.parse(localStorage.getItem(STORE_KEY) || "{}"); }
    catch (_) { return {}; }
  }

  function saveAnswers(value) {
    const saved = loadSaved();
    saved[phase] = value;
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
      api.record("assessment_submit", {
        item_id: item.id,
        response: String(answers[item.id]),
        confidence: confidence[item.id],
        metadata: { phase, item_kind: "item" },
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
      const response = await fetch("content/instruments.json", { cache: "no-cache" });
      if (!response.ok) throw new Error("load failed");
      const instruments = await response.json();
      const instrument = instruments.phases[phase];
      const previous = loadSaved()[phase];
      byId("assessment-title").textContent = instrument.title;
      byId("assessment-description").textContent = instrument.description;
      if (phase === "pre") {
        addHeading("最初に、あなたについて", "ここには正解がありません。現在の経験にいちばん近いものを選んでください。");
        instruments.background.forEach((item) => {
          byId("assessment-form").appendChild(renderBackgroundItem(item, previous));
        });
        addHeading("5つの短い場面", "知らない言葉を問う問題ではありません。いちばん近いと思う答えと、その確信度を選んでください。");
      }
      instrument.items.forEach((item, index) => {
        byId("assessment-form").appendChild(renderItem(item, index, previous));
      });
      byId("assessment-actions").hidden = false;

      byId("assessment-form").addEventListener("submit", (event) => {
        event.preventDefault();
        const formData = new FormData(event.currentTarget);
        const answers = {};
        const confidence = {};
        const background = {};
        if (phase === "pre") {
          instruments.background.forEach((item) => { background[item.id] = formData.get(item.id); });
        }
        instrument.items.forEach((item) => {
          answers[item.id] = Number(formData.get(item.id));
          confidence[item.id] = Number(formData.get(`${item.id}-confidence`));
        });
        saveAnswers({ submitted_at: new Date().toISOString(), background, answers, confidence });
        sendAnswers(instruments, instrument, background, answers, confidence);
        event.currentTarget.hidden = true;
        byId("assessment-actions").hidden = true;
        message.hidden = false;
        message.innerHTML = "<strong>回答を受け付けました。</strong><br>研究参加ページに戻り、次のステップへ進んでください。";
      });
    } catch (_) {
      message.hidden = false;
      message.textContent = "テストを読み込めませんでした。ページを再読み込みしてください。";
    }
  }

  document.addEventListener("DOMContentLoaded", () => { void start(); });
})();
