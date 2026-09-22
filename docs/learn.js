/* 学習ループ。予測 → 確認 → 説明 → 検証 を1つの画面で回す。
   通常の記録はブラウザ内 (localStorage) に保存し、JSONに書き出せる。
   研究参加へ明示同意した場合だけ、study.jsが自由記述を除く構造化イベントを別に送る。 */
(() => {
  "use strict";

  const KEY = "insecureapp.learn.v1";
  const empty = () => ({ predictions: {}, quiz: [], explain: {}, oracle: {}, notes: {}, surface: {} });

  const store = {
    load() {
      try { return Object.assign(empty(), JSON.parse(localStorage.getItem(KEY) || "{}")); }
      catch (e) { return empty(); }
    },
    save(s) {
      try { localStorage.setItem(KEY, JSON.stringify(s)); }
      catch (e) { warnStorage(); }
    },
  };

  function warnStorage() {
    if (document.getElementById("storage-warning")) return;
    const d = document.createElement("div");
    d.id = "storage-warning";
    d.className = "callout vuln";
    d.innerHTML = "<strong>記録が保存できない</strong><br>" +
      "このブラウザが、このページのデータ保存を止めています。" +
      "プライベートウィンドウを使っていないか、" +
      "サイトデータ（Cookie とサイトデータ）の保存をブロックしていないか確かめてください。" +
      "設定を変えずに進めることもできますが、予測と説明は残りません。";
    document.querySelector("main").prepend(d);
  }

  const now = () => new Date().toISOString().slice(0, 16).replace("T", " ");
  const studyEvent = (type, fields = {}) => {
    if (window.InsecureStudy) window.InsecureStudy.record(type, fields);
  };
  const VERDICT = { ok: "納得した", suspect: "まだ怪しい", checked: "実験で確かめた" };
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const nl2br = (s) => esc(s).replace(/\n/g, "<br>");

  const cache = {};
  async function content(name) {
    if (!cache[name]) {
      // 設問を差し替えたとき、参加者のブラウザが古い JSON を出し続けないようにする
      const res = await fetch(`content/${name}.json`, { cache: "no-cache" });
      if (!res.ok) throw new Error(`content/${name}.json が読めない`);
      cache[name] = await res.json();
    }
    return cache[name];
  }

  /* ---------------------------------------------------- 脅威ブリーフ */
  async function renderThreat(el) {
    const id = el.dataset.threat;
    const bank = await content("threats");
    const t = bank[id];
    if (!t) { el.innerHTML = `<p>未定義の脅威ブリーフ: ${esc(id)}</p>`; return; }
    const rows = [
      ["資産", t.asset], ["守る性質", t.property], ["攻撃者の能力", t.attacker],
      ["成立の前提", t.preconditions], ["この実験で除外", t.excluded],
      ["検証する主張", t.claim], ["攻撃成功", t.success],
      ["観測点", t.observe], ["対策後にも残ること", t.residual],
    ];
    el.className = "threat-brief";
    el.innerHTML = `
      <div class="threat-head"><span class="learn-tag">今回の実験</span>${esc(t.title)}</div>
      <dl class="threat-short">
        <div><dt>守るもの</dt><dd>${esc(t.protect)}</dd></div>
        <div><dt>相手</dt><dd>${esc(t.opponent)}</dd></div>
        <div><dt>確かめること</dt><dd>${esc(t.goal)}</dd></div>
      </dl>
      <details class="threat-more">
        <summary>条件を詳しく見る（脅威モデル）</summary>
        <dl>${rows.map(([k, v]) => `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join("")}</dl>
      </details>`;
  }

  /* ---------------------------------------------------- 予測 → 確認 */
  async function renderStep(el) {
    const id = el.dataset.step;
    const steps = await content("steps");
    const step = steps[id];
    if (!step) { el.innerHTML = `<p>未定義の手順: ${esc(id)}</p>`; return; }

    const state = store.load();
    const rec = state.predictions[id];

    if (!rec) return viewPredict(el, id, step);
    if (rec.observed == null) return viewObserve(el, id, step, rec);
    return viewResult(el, id, step, rec);
  }

  function viewPredict(el, id, step) {
    el.className = "learn-step predict";
    el.innerHTML = `
      <div class="learn-head"><span class="learn-tag">予測</span>${esc(step.title)}</div>
      <p class="learn-setup">${nl2br(step.setup)}</p>
      <div class="learn-action"><b>予測を記録したあとに行う操作</b><br>${nl2br(step.action)}</div>
      <p class="learn-q">${esc(step.question)}</p>
      <div class="learn-choice">
        <label><input type="radio" name="g-${id}" value="ok"> 成功する / 通る / 読める</label>
        <label><input type="radio" name="g-${id}" value="ng"> 失敗する / 止められる / 読めない</label>
      </div>
      <label class="learn-reason">理由（書きたい場合だけでよい）
        <input type="text" id="r-${id}" placeholder="例: UID が違うので読めないはず（任意）">
      </label>
      <button class="learn-btn" id="b-${id}">予測を記録する</button>
      <p class="learn-note">記録するまで解説は出ません。</p>`;
    el.querySelector(`#b-${id}`).addEventListener("click", () => {
      const g = el.querySelector(`input[name="g-${id}"]:checked`);
      const reason = el.querySelector(`#r-${id}`).value.trim();
      if (!g) { alert("成功するか失敗するかを選ぶこと。"); return; }
      const s = store.load();
      s.predictions[id] = { guess: g.value, reason, predicted_at: now(), observed: null };
      store.save(s);
      studyEvent("prediction_submit", {
        chapter_id: step.chapter,
        item_id: id,
        attempt: 1,
        response: g.value,
      });
      renderStep(el);
    });
  }

  function viewObserve(el, id, step, rec) {
    el.className = "learn-step observe";
    el.innerHTML = `
      <div class="learn-head"><span class="learn-tag">確認</span>${esc(step.title)}</div>
      <p class="learn-mine">あなたの予測: <b>${rec.guess}</b>${rec.reason ? `（${esc(rec.reason)}）` : ""}</p>
      <div class="learn-action"><b>いま行う操作</b><br>${nl2br(step.action)}</div>
      <p class="learn-q">上の操作を行い、実際の結果を選ぶ。</p>
      <div class="learn-actions">
        <button class="learn-btn" data-a="ok">成功した</button>
        <button class="learn-btn" data-a="ng">失敗した / 止められた</button>
      </div>`;
    el.querySelectorAll("[data-a]").forEach((b) =>
      b.addEventListener("click", () => {
        const s = store.load();
        const r = s.predictions[id];
        r.observed = b.dataset.a;
        r.observed_at = now();
        r.matched = (r.observed === r.guess);
        store.save(s);
        studyEvent("observation_submit", {
          chapter_id: step.chapter,
          item_id: id,
          attempt: 1,
          response: r.observed,
          metadata: { matched: r.matched, expected: step.answer },
        });
        renderStep(el);
      }));
  }

  function viewResult(el, id, step, rec) {
    const matched = rec.matched;
    const hit = rec.observed === step.answer;
    el.className = "learn-step result " + (matched ? "matched" : "missed");
    el.innerHTML = `
      <div class="learn-head">
        <span class="learn-tag">${matched ? "予測どおり" : "予測を外した"}</span>${esc(step.title)}
      </div>
      <table class="learn-table">
        <tr><th>あなたの予測</th><td>${rec.guess}${rec.reason ? `　（${esc(rec.reason)}）` : ""}</td></tr>
        <tr><th>実際の結果</th><td>${rec.observed}</td></tr>
        <tr><th>正解</th><td>${step.answer}</td></tr>
      </table>
      ${hit ? "" : `<div class="callout vuln"><strong>実測が想定と違う</strong><br>
        環境側の問題かもしれない。<a href="setup.html">よくある詰まり</a>を確認する。
        それでも違うなら、その差自体が観察対象になる。</div>`}
      <p class="learn-lead">${matched
        ? "では、なぜそうなるのかを確認する。"
        : "ここがこの手順で一番学べる場所。"}</p>
      <div class="learn-why">${nl2br(step.why)}</div>
      ${step.misconception ? `<div class="learn-misc"><b>ありがちな誤解</b><br>
        ${nl2br(step.misconception)}</div>` : ""}
      ${(!matched && step.if_wrong) ? `<div class="learn-misc"><b>予測を外した人へ</b><br>
        ${nl2br(step.if_wrong)}</div>` : ""}
      <button class="learn-link" id="again-${id}">やり直す</button>`;
    el.querySelector(`#again-${id}`).addEventListener("click", () => {
      const s = store.load();
      delete s.predictions[id];
      store.save(s);
      renderStep(el);
    });
  }

  /* ---------------------------------------------------- 概念テスト */
  async function renderQuiz(el) {
    const ch = el.dataset.chapter || "all";
    const bank = await content("quiz");
    const items = bank.filter((q) => ch === "all" || q.chapter === ch);
    const labels = "abcd".split("");
    let i = 0;
    const answers = [];

    const phase = () => el.querySelector("#quiz-phase")?.value || "pre";

    function start() {
      el.innerHTML = `
        <div class="learn-head"><span class="learn-tag">概念テスト</span>${esc(ch)}（${items.length}問）</div>
        <p>選択肢には、実際によくある誤解が仕込んである。
        わからなければ「わからない」を選んでよい。当てずっぽうより価値がある。</p>
        <label>記録の種類
          <select id="quiz-phase">
            <option value="pre">事前（学習前）</option>
            <option value="post">事後（学習後）</option>
          </select>
        </label>
        <button class="learn-btn" id="quiz-start">開始する</button>`;
      el.querySelector("#quiz-start").addEventListener("click", () => {
        studyEvent("practice_quiz_start", { item_id: ch, metadata: { practice_phase: phase() } });
        i = 0;
        question();
      });
    }

    function question() {
      if (i >= items.length) return finish();
      const q = items[i];
      el.innerHTML = `
        <div class="learn-head"><span class="learn-tag">Q${i + 1} / ${items.length}</span>${esc(q.chapter)}</div>
        <p class="learn-q">${nl2br(q.q.trim())}</p>
        <div class="learn-choice">
          ${q.options.map((o, n) =>
            `<label><input type="radio" name="q" value="${n}"> ${esc(o.text)}</label>`).join("")}
          <label><input type="radio" name="q" value="x"> わからない</label>
        </div>
        <button class="learn-btn" id="q-ok">答える</button>`;
      el.querySelector("#q-ok").addEventListener("click", () => {
        const sel = el.querySelector('input[name="q"]:checked');
        if (!sel) { alert("選ぶこと。わからなければ「わからない」でよい。"); return; }
        feedback(q, sel.value);
      });
    }

    function feedback(q, v) {
      const unknown = v === "x";
      const idx = unknown ? -1 : Number(v);
      const correct = idx === q.answer;
      answers.push({
        id: q.id, choice: unknown ? "x" : labels[idx], correct,
        misconception: unknown ? "unknown" : q.options[idx].tag,
      });
      studyEvent("practice_quiz_answer", {
        chapter_id: q.chapter,
        item_id: q.id,
        response: unknown ? "unknown" : labels[idx],
        metadata: { matched: correct, practice_phase: phase() },
      });
      el.innerHTML = `
        <div class="learn-head">
          <span class="learn-tag">${correct ? "正解" : "不正解"}</span>${esc(q.id)}
        </div>
        <p class="learn-q">${nl2br(q.q.trim())}</p>
        ${unknown ? "" : `<div class="learn-misc"><b>選んだ選択肢</b><br>
          ${esc(q.options[idx].text)}<br><br>${nl2br(q.options[idx].why)}</div>`}
        ${correct ? "" : `<div class="learn-why"><b>正解: ${esc(q.options[q.answer].text)}</b><br><br>
          ${nl2br(q.options[q.answer].why)}</div>`}
        <button class="learn-btn" id="q-next">次へ</button>`;
      el.querySelector("#q-next").addEventListener("click", () => { i += 1; question(); });
    }

    function finish() {
      const score = answers.filter((a) => a.correct).length;
      const tags = [...new Set(answers.filter((a) => !a.correct)
        .map((a) => a.misconception).filter((t) => t !== "correct"))];
      const s = store.load();
      s.quiz.push({ chapter: ch, phase: phase(), at: now(), score, total: items.length, results: answers });
      store.save(s);
      studyEvent("practice_quiz_complete", {
        item_id: ch,
        metadata: { practice_phase: phase(), score, total: items.length },
      });
      el.innerHTML = `
        <div class="learn-head"><span class="learn-tag">結果</span>${score} / ${items.length}</div>
        ${tags.length ? `<div class="learn-misc"><b>残っている誤解</b><ul>
          ${tags.map((t) => `<li><code>${esc(t)}</code></li>`).join("")}</ul>
          該当する章の Standard 層をもう一度やると、この誤解は実験で潰せる。
          読み直すより手を動かすほうが早い。</div>`
        : "<p>誤解の残りはない。</p>"}
        <p><a href="progress.html">記録を見る</a></p>
        <button class="learn-btn" id="q-restart">もう一度</button>`;
      el.querySelector("#q-restart").addEventListener("click", start);
    }

    if (!items.length) { el.innerHTML = "<p>該当する設問がない。</p>"; return; }
    start();
  }

  /* ---------------------------------------------------- オラクル演習 */
  async function renderOracle(el) {
    const ch = el.dataset.chapter;
    const bank = await content("oracle");
    const items = bank.filter((o) => o.chapter === ch);
    let i = 0;
    const results = [];

    function intro() {
      el.innerHTML = `
        <div class="learn-head"><span class="learn-tag">オラクル演習</span>${esc(ch)}（${items.length}件）</div>
        <p>AI が出した診断結果と修正案が並んでいる。正しいものも、
        もっともらしいが誤っているものも混ざっている。</p>
        <p><b>あなたの仕事は「見つける」ことではない。</b>
        AI の主張が正しいかどうかを、実験で確かめて判定すること。
        根拠は「そう習ったから」ではなく「こう試したらこうなったから」で書く。</p>
        <button class="learn-btn" id="o-start">開始する</button>`;
      el.querySelector("#o-start").addEventListener("click", () => { i = 0; item(); });
    }

    function item() {
      if (i >= items.length) return finish();
      const o = items[i];
      el.innerHTML = `
        <div class="learn-head"><span class="learn-tag">AI の出力 ${i + 1} / ${items.length}</span></div>
        <div class="learn-claim">${nl2br(o.claim)}</div>
        ${o.patch ? `<pre><code>${esc(o.patch)}</code></pre>` : ""}
        <div class="learn-choice">
          <label><input type="radio" name="v" value="yes"> この主張は正しい</label>
          <label><input type="radio" name="v" value="no"> この主張は誤っている</label>
        </div>
        <label class="learn-reason">そう判断した根拠（実際に何をどう試したか）
          <input type="text" id="o-ev" placeholder="例: frida で読めたので「安全」は誤り">
        </label>
        <button class="learn-btn" id="o-ok">判定する</button>`;
      el.querySelector("#o-ok").addEventListener("click", () => {
        const sel = el.querySelector('input[name="v"]:checked');
        const ev = el.querySelector("#o-ev").value.trim();
        if (!sel) { alert("正しいか誤っているかを選ぶこと。"); return; }
        if (!ev) { alert("根拠を書くこと。ここが演習の本体。"); return; }
        verdict(o, sel.value, ev);
      });
    }

    function verdict(o, v, ev) {
      const correct = v === o.verdict;
      results.push({ id: o.id, verdict: v, correct, evidence: ev });
      el.innerHTML = `
        <div class="learn-head">
          <span class="learn-tag">${correct ? "判定は合っている" : "判定が違う"}</span>${esc(o.id)}
        </div>
        <div class="learn-claim">${nl2br(o.claim)}</div>
        <div class="learn-why">${nl2br(o.explain)}</div>
        ${o.how_to_check ? `<div class="learn-misc"><b>確かめ方</b><br>
          ${nl2br(o.how_to_check)}</div>` : ""}
        <p class="learn-mine">あなたの根拠: ${esc(ev)}</p>
        <button class="learn-btn" id="o-next">次へ</button>`;
      el.querySelector("#o-next").addEventListener("click", () => { i += 1; item(); });
    }

    function finish() {
      const score = results.filter((r) => r.correct).length;
      const s = store.load();
      s.oracle[ch] = { at: now(), results };
      store.save(s);
      el.innerHTML = `
        <div class="learn-head"><span class="learn-tag">結果</span>${score} / ${items.length}</div>
        <p>AI が普及するほど、出力を検証できることの価値は上がる。
        ここで問われたのは脆弱性を見つける力ではなく、主張を実験で棄却できる力のほう。</p>
        <p><a href="progress.html">記録を見る</a></p>`;
    }

    if (!items.length) { el.innerHTML = "<p>該当する課題がない。</p>"; return; }
    intro();
  }

  /* ---------------------------------------------------- 説明課題 */
  async function renderExplain(el) {
    const ch = el.dataset.chapter;
    const tasks = await content("explain");
    const task = tasks[ch];
    if (!task) { el.innerHTML = "<p>該当する章がない。</p>"; return; }
    const saved = store.load().explain[ch] || { answers: {}, rubric: {} };

    el.innerHTML = `
      <div class="learn-head"><span class="learn-tag">説明課題</span>${esc(task.title)}</div>
      <p>${esc(task.intro)}</p>
      <p class="learn-note">採点はコマンドの成否ではなく説明の質。
      教材の言い回しをなぞらず、自分の言葉で書くこと。</p>
      ${task.questions.map((q, n) => `
        <div class="learn-qa">
          <p class="learn-q">Q${n + 1}. ${esc(q.q)}</p>
          ${q.hint ? `<p class="learn-note">観点: ${esc(q.hint)}</p>` : ""}
          <textarea id="a-${q.id}" rows="3">${esc(saved.answers[q.id] || "")}</textarea>
        </div>`).join("")}
      <h3>自己採点</h3>
      <p>書いた説明に次の要素が入っているかを、自分で確認する。</p>
      ${task.rubric.map((r) => `
        <label class="learn-rubric">
          <input type="checkbox" id="c-${r.id}" ${saved.rubric[r.id] === "yes" ? "checked" : ""}>
          ${esc(r.point)}
        </label>`).join("")}
      <button class="learn-btn" id="e-save">保存して確認する</button>
      <div id="e-result"></div>`;

    el.querySelector("#e-save").addEventListener("click", () => {
      const answers = {}, rubric = {};
      task.questions.forEach((q) => { answers[q.id] = el.querySelector(`#a-${q.id}`).value.trim(); });
      task.rubric.forEach((r) => { rubric[r.id] = el.querySelector(`#c-${r.id}`).checked ? "yes" : "no"; });
      const s = store.load();
      s.explain[ch] = { at: now(), answers, rubric };
      store.save(s);
      const missing = task.rubric.filter((r) => rubric[r.id] === "no");
      el.querySelector("#e-result").innerHTML = missing.length
        ? `<div class="learn-misc"><b>足りない観点</b><ul>${missing.map((r) =>
            `<li>${esc(r.point)}<br><span class="learn-note">${esc(r.where)}</span></li>`).join("")}</ul></div>`
        : `<div class="learn-why">必要な観点は揃っている。保存した。</div>`;
    });
  }

  /* ---------------------------------------------------- 問い → 説明 → AI → 検証 */
  /* 段の順序は意図的。自分で説明を書く前に答えと AI を開かない。
     ただし禁止はせず、逃げ道は常に置いておく。 */

  function copyToClipboard(text, btn) {
    const label = btn.textContent;
    const done = () => {
      btn.textContent = "コピーしました";
      setTimeout(() => { btn.textContent = label; }, 1600);
    };
    const manual = () => {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.top = "-1000px";
      document.body.appendChild(ta);
      ta.select();
      let ok = false;
      try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
      document.body.removeChild(ta);
      if (ok) done();
      else window.prompt("コピーできなかった。下の内容を選択してコピーすること。", text);
    };
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(done, manual);
    } else {
      manual();
    }
  }

  const askPrompt = (item, teach) =>
`Android のセキュリティを学んでいます。次の問いに答えてください。

【問い】
${item.q}

【私がいま書いた説明】
${teach || "(まだ書いていません)"}

【条件】
- 断定する箇所には、それを Android エミュレータの手元で確かめる方法
  （打つコマンド、または見るべき出力）を必ず添えてください。
- 私の説明に誤りがあれば、どこがどう違うのかをはっきり書いてください。
- 一般論で終わらせず、パス名・API 名・エラー名などの具体的な名前を出してください。`;

  const teachPrompt = (item, teach) =>
`あなたは Android を知らない後輩です。私がこれから説明します。

分かったふりをしないでください。曖昧な言葉、根拠のない断定、話の飛躍があれば、
その都度かならず質問し返してください。
私が答えられなかった点は、最後にまとめて教えてください。

【テーマ】
${item.q}

【私の説明】
${teach}`;

  async function renderInquiry(el) {
    const id = el.dataset.inquiry;
    const items = await content("inquiry");
    const item = items[id];
    if (!item) { el.innerHTML = `<p>未定義の問い: ${esc(id)}</p>`; return; }

    const rec = store.load().notes[id] || {};
    // 自分の説明を書いたか、逃げ道を押したら、以降の段が開く
    const opened = !!(rec.teach && rec.teach.trim()) || rec.opened === true || el.dataset.opened === "1";
    const escape = "先に座学に戻る";

    let verifyStep = null;
    if (item.verify) {
      try { verifyStep = (await content("steps"))[item.verify] || null; } catch (e) { verifyStep = null; }
    }

    el.className = "learn-inquiry" + (opened ? " opened" : "");
    el.innerHTML = `
      <div class="learn-head"><span class="learn-tag">問い</span>${esc(item.section)}</div>
      <p class="learn-q">${esc(item.q)}</p>

      <div class="inq-stage">
        <p class="inq-lead"><span class="inq-num">1</span>まず自分で答える</p>
        <label class="inq-label" for="t-${id}">${esc(item.answer_format || "結論と根拠を分けて答える")}</label>
        <textarea id="t-${id}" rows="4" placeholder="うまく書けなくて構いません。書けなかったところが、そのまま次に聞くことになります。">${esc(rec.teach || "")}</textarea>
        <p class="learn-note">観点: ${esc(item.teach_hint)}</p>
        <div class="learn-actions">
          <button class="learn-btn" data-act="wrote">書いた</button>
          <button class="learn-link" data-act="escape">${escape}</button>
        </div>
      </div>

      ${opened ? `
      <div class="inq-stage">
        <p class="inq-lead"><span class="inq-num">2</span>座学に戻る</p>
        <p><a href="#${esc(item.anchor)}">${esc(item.anchor_label)}</a> を読み直してから、上の説明を書き足してください。</p>
      </div>

      <div class="inq-stage">
        <p class="inq-lead"><span class="inq-num">3</span>AI に聞く</p>
        <p class="learn-note">自分の説明ごと渡します。答えだけでなく、確かめ方も一緒に出させます。</p>
        <div class="learn-actions">
          <button class="learn-btn" data-act="copy-ask">質問をコピー</button>
        </div>
        <label class="inq-label" for="n-${id}">AI の答えで、自分の説明のどこが変わりましたか</label>
        <textarea id="n-${id}" rows="3">${esc(rec.ai_note || "")}</textarea>
      </div>

      <div class="inq-stage">
        <p class="inq-lead"><span class="inq-num">4</span>AI に教える</p>
        <p class="learn-note">読んで分かった気になったところは、説明しようとすると必ず詰まります。ここは実際に貼って会話してください。</p>
        <div class="learn-actions">
          <button class="learn-btn" data-act="copy-teach">後輩役のプロンプトをコピー</button>
        </div>
        <label class="inq-label" for="b-${id}">説明していて詰まったところ</label>
        <textarea id="b-${id}" rows="3">${esc(rec.teach_back || "")}</textarea>
      </div>

      <div class="inq-stage">
        <p class="inq-lead"><span class="inq-num">5</span>自分の手で確かめる</p>
        <p class="learn-note">AI が持っている Android の知識は古いことがあります。手元のエミュレータのほうが正しいと考えてください。</p>
        ${verifyStep ? `<div class="learn-actions">
          <button class="learn-btn" data-act="goto">${esc(verifyStep.title)} へ</button>
        </div>` : ""}
        <div class="inq-verdict">
          ${[["ok", "納得した"], ["suspect", "まだ怪しい"], ["checked", "実験で確かめた"]].map(([v, t]) =>
            `<label><input type="radio" name="v-${id}" value="${v}"${rec.verdict === v ? " checked" : ""}> ${t}</label>`).join("")}
        </div>
      </div>

      <div class="inq-stage">
        <p class="inq-lead"><span class="inq-num">6</span>ここで自分が思いついた問い</p>
        <p class="learn-note">答えは要りません。書いておくと記録ページに集まります。</p>
        <input type="text" id="q-${id}" value="${esc(rec.own_q || "")}" placeholder="例: exported=false のプロバイダに root から触ったらどうなるのか">
      </div>

      <p class="learn-note inq-saved">入力は自動で保存されます。書き出しは <a href="progress.html">記録</a> から。</p>
      ` : `<p class="learn-note">説明を書くか、上のリンクを押すと、AI に聞く手順まで開きます。</p>`}`;

    const field = (p) => el.querySelector(`#${p}-${id}`);

    const persist = () => {
      const s = store.load();
      const prev = s.notes[id] || {};
      const checked = el.querySelector(`input[name="v-${id}"]:checked`);
      s.notes[id] = {
        section: item.section,
        teach: field("t") ? field("t").value.trim() : (prev.teach || ""),
        ai_note: field("n") ? field("n").value.trim() : (prev.ai_note || ""),
        teach_back: field("b") ? field("b").value.trim() : (prev.teach_back || ""),
        own_q: field("q") ? field("q").value.trim() : (prev.own_q || ""),
        verdict: checked ? checked.value : (prev.verdict || null),
        opened: prev.opened === true || el.dataset.opened === "1",
        at: now(),
      };
      store.save(s);
      return s.notes[id];
    };

    if (el.dataset.bound !== "1") {
      el.dataset.bound = "1";
      el.addEventListener("input", persist);
      el.addEventListener("change", persist);
    }

    el.querySelectorAll("[data-act]").forEach((b) => b.addEventListener("click", () => {
      const teach = field("t").value.trim();
      switch (b.dataset.act) {
        case "wrote":
          if (!teach) { alert("一行でよいので書くこと。書けなかった部分が、次に AI へ聞く材料になる。"); return; }
          persist();
          renderInquiry(el);
          break;
        case "escape":
          el.dataset.opened = "1";
          persist();
          renderInquiry(el);
          break;
        case "copy-ask":
          persist();
          studyEvent("ai_prompt_copy", { item_id: id, response: "ask" });
          copyToClipboard(askPrompt(item, teach), b);
          break;
        case "copy-teach":
          if (!teach) { alert("先に自分の説明を書くこと。教える中身がないと、この段は意味がない。"); return; }
          persist();
          studyEvent("ai_prompt_copy", { item_id: id, response: "teach" });
          copyToClipboard(teachPrompt(item, teach), b);
          break;
        case "goto": {
          const target = document.querySelector(`[data-step="${item.verify}"]`);
          if (!target) { alert("この手順はこのページにない。第1章を開くこと。"); break; }
          // smooth は環境によって黙って無視されるので既定の即時スクロールにする
          target.scrollIntoView({ block: "center" });
          target.classList.add("inq-target");
          setTimeout(() => target.classList.remove("inq-target"), 1800);
          break;
        }
      }
    }));
  }

  /* ---------------------------------------------------- 攻撃面マップ */
  /* 資産 × 立場。届く立場を「全部選ぶ」形にしてある。
     防御側は経路を1本ずつ塞ぐが、攻撃側は資産から経路を数えるので、入力も数え上げにした。
     表は Basic / Standard / Deep の3枚に割れる。割り方は「その立場を実際に使う章の層」に合わせてあり、
     層が上がると列が増える。増えた列で結論がひっくり返る行があるのが、この分け方の狙い。 */
  const SF_RANK = { basic: 1, standard: 2, deep: 3 };
  const SF_LABEL = { basic: "Basic", standard: "Standard", deep: "Deep" };
  /* 判定は3値。yes = 直接届く、cond = 前提をもう一段足せば届く、no = 届かない。
     cond を no に丸めると、root からプロセス内実行へ進める経路が表から消えてしまう。
     入力は「届くかどうか」の二択のままにして、答え合わせでは cond も「届く」に数える。 */
  const sfReached = (r) => r === "yes" || r === "cond";
  const SF_MARK = { yes: "●", cond: "◐", no: "○" };
  const SF_WORD = { yes: "届く", cond: "条件付きで届く", no: "届かない" };

  async function renderSurface(el) {
    const view = el.dataset.surface || "deep";
    const rank = SF_RANK[view] || 3;
    const data = await content("surface");
    const cols = data.positions.filter((p) => SF_RANK[p.layer || "basic"] <= rank);
    const assets = data.assets.filter((a) => SF_RANK[a.layer || "basic"] <= rank);
    const answerOf = (asset) => cols.filter((p) => sfReached(asset.reach[p.id])).map((p) => p.id);
    const evidenceOf = (asset, pid) => (asset.evidence || {})[pid];
    const pointOf = (asset) => (asset.point_by_layer && asset.point_by_layer[view]) || asset.point;

    /* 記録は層ごとに分ける。列が増えたら同じ行をもう一度考えてほしいので、答えは共有しない */
    function bucket() {
      const s = store.load();
      const sf = s.surface || {};
      // 層で分ける前の形（資産 id が直接ぶら下がっている）が残っていたら Deep に寄せる
      if (Object.values(sf).some((v) => v && v.picked)) {
        const moved = {};
        Object.entries(sf).forEach(([k, v]) => { if (v && v.picked) moved[k] = v; });
        s.surface = { deep: moved };
        store.save(s);
        return s.surface;
      }
      return sf;
    }
    const live = () => bucket()[view] || {};

    el.className = "sf-panel";
    el.innerHTML = '<div class="sf-rows"></div><div class="sf-map"></div>';
    const rowsEl = el.querySelector(".sf-rows");
    const mapEl = el.querySelector(".sf-map");

    assets.forEach((asset) => {
      const box = document.createElement("section");
      rowsEl.appendChild(box);
      drawAsset(box, asset);
    });
    drawMap();

    function save(assetId, rec) {
      const s = store.load();
      s.surface = s.surface || {};
      s.surface[view] = s.surface[view] || {};
      if (rec) s.surface[view][assetId] = rec;
      else delete s.surface[view][assetId];
      store.save(s);
    }

    function drawAsset(box, asset) {
      const rec = live()[asset.id];
      if (rec) viewAnswer(box, asset, rec);
      else viewPick(box, asset);
    }

    function viewPick(box, asset) {
      box.className = "sf-row pick";
      box.innerHTML = `
        <div class="learn-head"><span class="learn-tag">${esc(asset.chapter)}</span>${esc(asset.name)}</div>
        <p class="learn-note sf-where">${esc(asset.where)}</p>
        <p class="learn-q">この資産に届く立場を、全部選んでください。
        一段進めば届く場合も「届く」に数えます。</p>
        <div class="sf-choice">
          ${cols.map((p) => asset.reach[p.id] === "na"
            ? `<label class="off"><input type="checkbox" disabled> ${esc(p.short)}<span class="sf-na">対象外</span></label>`
            : `<label><input type="checkbox" value="${p.id}"> ${esc(p.short)}</label>`).join("")}
        </div>
        <label class="learn-reason">いちばん危ないと思った立場と、その理由（書きたい場合だけでよい）
          <input type="text" placeholder="例: adb shell。run-as で中に入れるので（任意）">
        </label>
        <button class="learn-btn">この行を確定する</button>
        <p class="learn-note">確定するまで答えは出ません。</p>`;
      box.querySelector("button").addEventListener("click", () => {
        const picked = [...box.querySelectorAll('input[type="checkbox"]:checked')].map((c) => c.value);
        const reason = box.querySelector('input[type="text"]').value.trim();
        if (!picked.length &&
            !confirm("1つも選ばずに確定する。「どの立場からも届かない」という答えでよいか。")) return;
        save(asset.id, { picked, reason, at: now() });
        drawAsset(box, asset);
        drawMap();
      });
    }

    function viewAnswer(box, asset, rec) {
      const ans = answerOf(asset);
      const picked = rec.picked || [];
      const off = cols.filter((p) => asset.reach[p.id] !== "na" &&
        ans.includes(p.id) !== picked.includes(p.id));
      box.className = "sf-row result " + (off.length ? "missed" : "matched");
      box.innerHTML = `
        <div class="learn-head">
          <span class="learn-tag">${off.length ? `${off.length} マスずれた` : "全部合っている"}</span>${esc(asset.name)}
        </div>
        <table class="matrix sf-cells">
          <tr><th>立場</th><th>あなた</th><th>実際</th><th>根拠</th></tr>
          ${cols.map((p) => {
            const r = asset.reach[p.id];
            const na = r === "na";
            const mine = na ? "—" : picked.includes(p.id) ? "届く" : "届かない";
            const same = na || (picked.includes(p.id) === sfReached(r));
            const untested = evidenceOf(asset, p.id) === "untested"
              ? '<span class="sf-untested">未検証</span>' : "";
            return `<tr class="${same ? "" : "sf-diff"}">
              <td>${esc(p.short)}</td>
              <td>${mine}</td>
              <td class="${na ? "" : r === "no" ? "ok" : "ng"}">${na ? "—" : SF_WORD[r]}${untested}</td>
              <td class="sf-why">${esc(asset.cells[p.id] || "")}</td></tr>`;
          }).join("")}
        </table>
        ${rec.reason ? `<p class="learn-mine">あなたの見立て: ${esc(rec.reason)}</p>` : ""}
        <div class="learn-why">${nl2br(pointOf(asset))}</div>
        <button class="learn-link">この行をやり直す</button>`;
      box.querySelector("button").addEventListener("click", () => {
        save(asset.id, null);
        drawAsset(box, asset);
        drawMap();
      });
    }

    function drawMap() {
      const recs = live();
      const done = assets.filter((a) => recs[a.id]).length;
      const all = done === assets.length;
      const reachCount = (p) => assets.filter((a) => sfReached(a.reach[p.id])).length;
      const scopeCount = (p) => assets.filter((a) => a.reach[p.id] !== "na").length;
      mapEl.innerHTML = `
        <h3 id="map-${view}">${SF_LABEL[view]} の攻撃面マップ</h3>
        <p class="learn-note">${done} / ${assets.length} 行が埋まっています。
        <b class="ng">●</b> が直接届く、<b class="ng">◐</b> が前提をもう一段足せば届く、
        <b class="ok">○</b> が届かない、— は対象外。
        <sup class="sf-bang">!</sup> は自分の答えとずれたマスです。</p>
        ${data.scope ? `<p class="learn-note sf-scope">${esc(data.scope)}</p>` : ""}
        <table class="matrix sf-map-table">
          <tr><th>資産</th>${cols.map((p) => `<th>${esc(p.short)}</th>`).join("")}</tr>
          ${assets.map((a) => {
            const rec = recs[a.id];
            return `<tr><td>${esc(a.name)}</td>${cols.map((p) => {
              const r = a.reach[p.id];
              if (!rec) return '<td class="blank">?</td>';
              if (r === "na") return '<td class="sf-off">—</td>';
              const diff = (rec.picked || []).includes(p.id) !== sfReached(r);
              return `<td class="${r === "no" ? "ok" : "ng"}">${SF_MARK[r]}` +
                `${diff ? '<sup class="sf-bang">!</sup>' : ""}</td>`;
            }).join("")}</tr>`;
          }).join("")}
          ${all ? `<tr class="sf-total"><td>届いた資産の数</td>
            ${cols.map((p) => `<td>${reachCount(p)} / ${scopeCount(p)}</td>`).join("")}</tr>` : ""}
        </table>
        ${all ? `<div class="learn-why">${nl2br(closing())}</div>` : ""}`;
    }

    function closing() {
      if (view === "basic") {
        return "最後の行を横に見てください。立場によって、届く資産の数がまるで違います。\n" +
          "ここまでで十分です。列はあと3本増えますが、それは Standard と Deep の話になります。";
      }
      if (view === "standard") {
        return "列が3本増えました。増えたぶんだけ埋まったマスが増えた行と、何も変わらなかった行があります。\n" +
          "変わらなかった行は、防御が効いている範囲がそこまで広いということです。\n" +
          "「取得済みファイル」と「root端末」を分けたのは、暗号化がどちらに効いてどちらに効かないかを、" +
          "1つの列として言い分けるためです。";
      }
      return "最後の行を横に見てください。立場によって届く資産の数がまるで違います。\n" +
        "この数がそのまま、その立場に立たれたときの被害の大きさです。\n" +
        "そして列は独立していません。◐ のマスは「この立場から、もう一段進めば届く」という意味で、" +
        "root からプロセス内実行へ進める継ぎ目がそこに出ています。";
    }
  }

  /* ---------------------------------------------------- 進捗 / 記録 */
  async function renderProgress(el) {
    const steps = await content("steps");
    const inq = await content("inquiry").catch(() => ({}));
    const sf = await content("surface").catch(() => null);
    const s = store.load();

    const byChapter = {};
    Object.entries(steps).forEach(([id, st]) => {
      // parked はどのページにも置いていない手順。並べると、到達できないのに
      // 永久に未着手のままの行が残る
      if (st.parked) return;
      (byChapter[st.chapter] = byChapter[st.chapter] || []).push([id, st]);
    });

    const mark = (rec) => !rec ? ["未着手", "todo"]
      : rec.observed == null ? ["予測済み・未確認", "pending"]
      : rec.matched ? ["的中", "hit"] : ["外した", "miss"];

    const done = Object.values(s.predictions).filter((r) => r.observed != null);
    const hit = done.filter((r) => r.matched).length;
    const missed = Object.entries(s.predictions).filter(([, r]) => r.observed != null && !r.matched);

    const pre = s.quiz.filter((q) => q.phase === "pre");
    const post = s.quiz.filter((q) => q.phase === "post");

    /* 攻撃面マップは層ごとに別の表なので、集計も層ごとに出す */
    const sfLayers = [];
    const sfOff = [];
    if (sf) {
      ["basic", "standard", "deep"].forEach((v) => {
        const rank = SF_RANK[v];
        const cols = sf.positions.filter((p) => SF_RANK[p.layer || "basic"] <= rank);
        const assets = sf.assets.filter((a) => SF_RANK[a.layer || "basic"] <= rank);
        const recs = (s.surface || {})[v] || {};
        const filled = assets.filter((a) => recs[a.id]);
        let off = 0;
        filled.forEach((a) => {
          const picked = recs[a.id].picked || [];
          cols.forEach((p) => {
            if (a.reach[p.id] === "na") return;
            const real = sfReached(a.reach[p.id]);
            if (picked.includes(p.id) !== real) {
              off += 1;
              sfOff.push({ layer: SF_LABEL[v], asset: a.name, pos: p.short,
                           real: real ? "届く" : "届かない" });
            }
          });
        });
        sfLayers.push({ v, label: SF_LABEL[v], filled: filled.length, total: assets.length, off });
      });
    }
    const sfDone = sfLayers.reduce((n, l) => n + l.filled, 0);

    const noteIds = Object.keys(s.notes);
    const ownQs = noteIds.map((id) => s.notes[id].own_q).filter(Boolean);

    el.innerHTML = `
      <h2>予測</h2>
      ${Object.keys(byChapter).sort().map((ch) => `
        <h3>${esc(ch)}</h3>
        <table class="matrix"><tr><th>状態</th><th>手順</th><th>内容</th></tr>
        ${byChapter[ch].map(([id, st]) => {
          const [label, cls] = mark(s.predictions[id]);
          return `<tr><td class="st-${cls}">${label}</td><td><code>${esc(id)}</code></td>
                  <td>${esc(st.title)}</td></tr>`;
        }).join("")}</table>`).join("")}
      ${done.length ? `<p><b>的中率 ${hit} / ${done.length}
        （${Math.round(hit * 100 / done.length)}%）</b></p>
        <p class="learn-note">的中率は高ければよいものではない。
        外したところを説明できるようになったかが本題。</p>` : "<p>まだ記録がない。</p>"}
      ${missed.length ? `<h3>外した手順（＝誤解が表に出た場所）</h3><ul>${missed.map(([id, r]) =>
        `<li><code>${esc(id)}</code> ${esc(steps[id].title)}<br>
         <span class="learn-note">予測 ${r.guess}${r.reason ? ` — ${esc(r.reason)}` : ""}</span></li>`).join("")}</ul>` : ""}

      <h2>概念テスト</h2>
      ${s.quiz.length ? `<table class="matrix"><tr><th>日時</th><th>範囲</th><th>種類</th><th>点</th></tr>
        ${s.quiz.map((q) => `<tr><td>${esc(q.at)}</td><td>${esc(q.chapter)}</td>
          <td>${q.phase === "pre" ? "事前" : "事後"}</td><td>${q.score}/${q.total}</td></tr>`).join("")}
        </table>` : "<p>未受験。</p>"}
      ${(pre.length && post.length) ? `<p><b>事前 ${Math.round(pre[0].score * 100 / pre[0].total)}%
        → 事後 ${Math.round(post[post.length - 1].score * 100 / post[post.length - 1].total)}%</b></p>` : ""}

      <h2>説明課題</h2>
      ${Object.keys(s.explain).length ? `<ul>${Object.entries(s.explain).map(([ch, e]) => {
        const ok = Object.values(e.rubric).filter((v) => v === "yes").length;
        return `<li>${esc(ch)} — 観点 ${ok}/${Object.keys(e.rubric).length}（${esc(e.at)}）</li>`;
      }).join("")}</ul>` : "<p>未提出。</p>"}

      <h2>オラクル演習</h2>
      ${Object.keys(s.oracle).length ? `<ul>${Object.entries(s.oracle).map(([ch, o]) => {
        const ok = o.results.filter((r) => r.correct).length;
        return `<li>${esc(ch)} — 判定 ${ok}/${o.results.length}（${esc(o.at)}）</li>`;
      }).join("")}</ul>` : "<p>未実施。</p>"}

      <h2>攻撃面マップ</h2>
      ${!sf ? "<p>読み込めなかった。</p>"
        : !sfDone ? '<p>未着手。<a href="surface-map.html">攻撃面マップ</a>から埋める。</p>'
        : `<table class="matrix"><tr><th>層</th><th>埋めた行</th><th>ずれたマス</th></tr>
          ${sfLayers.map((l) => `<tr><td>${esc(l.label)}</td>
            <td class="${l.filled === l.total ? "st-hit" : l.filled ? "st-pending" : "st-todo"}">
            ${l.filled} / ${l.total}</td><td>${l.filled ? l.off : "—"}</td></tr>`).join("")}</table>
        ${sfOff.length ? `<h3>ずれたマス</h3>
          <table class="matrix"><tr><th>層</th><th>資産</th><th>立場</th><th>実際</th></tr>
          ${sfOff.map((o) => `<tr><td>${esc(o.layer)}</td><td>${esc(o.asset)}</td>
            <td>${esc(o.pos)}</td><td>${esc(o.real)}</td></tr>`).join("")}</table>
          <p class="learn-note">ずれたマスは、自分が持っている攻撃者像の癖がそのまま出た場所。
          「どの立場を過大に見積もったか」と「どの立場を数え落としたか」は別の癖になる。</p>`
        : "<p>ずれはなかった。</p>"}`}

      <h2>問いと説明</h2>
      ${noteIds.length ? `
        <table class="matrix"><tr><th>状態</th><th>問い</th><th>判定</th></tr>
        ${noteIds.map((id) => {
          const n = s.notes[id];
          const wrote = n.teach_back ? ["説明した", "hit"]
            : n.teach ? ["書いた", "pending"] : ["未記入", "todo"];
          return `<tr><td class="st-${wrote[1]}">${wrote[0]}</td>
                  <td>${esc((inq[id] && inq[id].q) || id)}</td>
                  <td>${esc(VERDICT[n.verdict] || "—")}</td></tr>`;
        }).join("")}</table>
        <p class="learn-note">「説明した」は、AI に教えるところまで済んだ問い。
        書いただけで止まっているものは、まだ自分の言葉になっていない可能性がある。</p>
        ${ownQs.length ? `<h3>自分で思いついた問い</h3>
          <p class="learn-note">教材が用意した問いではなく、途中で自分が出した問い。
          書き出せば次に持っていける。</p>
          <ul>${ownQs.map((q) => `<li>${esc(q)}</li>`).join("")}</ul>` : ""}`
        : "<p>まだ記録がない。</p>"}

      <h2>記録の持ち出し</h2>
      <p>このページに表示する学習記録はブラウザ内だけにあります。研究参加に同意した場合の
      構造化イベントは別管理です。状態の確認・停止は<a href="study.html">研究参加</a>から行えます。</p>
      <div class="learn-actions">
        <button class="learn-btn" id="p-export">JSON を書き出す</button>
        <button class="learn-btn" id="p-import">JSON を読み込む</button>
        <button class="learn-link" id="p-reset">記録を全部消す</button>
      </div>
      <input type="file" id="p-file" accept="application/json" hidden>`;

    el.querySelector("#p-export").addEventListener("click", () => {
      const blob = new Blob([JSON.stringify(store.load(), null, 2)], { type: "application/json" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = `learn-progress-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(a.href);
    });
    el.querySelector("#p-import").addEventListener("click", () => el.querySelector("#p-file").click());
    el.querySelector("#p-file").addEventListener("change", async (ev) => {
      const f = ev.target.files[0];
      if (!f) return;
      try {
        store.save(Object.assign(empty(), JSON.parse(await f.text())));
        renderProgress(el);
      } catch (e) { alert("読み込めなかった: " + e.message); }
    });
    el.querySelector("#p-reset").addEventListener("click", () => {
      if (!confirm("記録を全部消す。元に戻せない。よいか。")) return;
      localStorage.removeItem(KEY);
      renderProgress(el);
    });
  }

  /* ---------------------------------------------------- 起動 */

  /* 描画に失敗したら、黙って空欄のまま残さずその場に理由を出す。
     content/*.json が取れなかったときにここへ来る。 */
  function failPanel(el, err) {
    el.className = "learn-panel";
    el.innerHTML = '<div class="learn-head"><span class="learn-tag">読み込めない</span></div>' +
      "<p>この欄の中身を読み込めませんでした。" +
      "ページを再読み込みすると直ることがあります。</p>" +
      `<p class="learn-note">${esc(String(err && err.message ? err.message : err))}</p>`;
  }

  const mount = (sel, render) =>
    document.querySelectorAll(sel).forEach((el) => {
      try { Promise.resolve(render(el)).catch((e) => failPanel(el, e)); }
      catch (e) { failPanel(el, e); }
    });

  document.addEventListener("DOMContentLoaded", () => {
    mount("[data-threat]", renderThreat);
    mount("[data-step]", renderStep);
    mount("[data-quiz]", renderQuiz);
    mount("[data-oracle]", renderOracle);
    mount("[data-explain]", renderExplain);
    mount("[data-inquiry]", renderInquiry);
    mount("[data-surface]", renderSurface);
    // ② に置いた「AI に聞く文」をコピーする。中身は HTML 側の <pre> をそのまま渡す
    document.querySelectorAll("[data-copy]").forEach((btn) => {
      const pre = (btn.closest("details, section, div") || document).querySelector("pre");
      if (!pre) return;
      btn.addEventListener("click", () => copyToClipboard(pre.textContent.trim(), btn));
    });

    mount("[data-progress]", renderProgress);
    // localStorage が使えない環境ならすぐ警告を出す
    try { localStorage.setItem(KEY + ".probe", "1"); localStorage.removeItem(KEY + ".probe"); }
    catch (e) { warnStorage(); }
  });
})();
