// @ts-check
import { runCoachTurn, applyProposal } from "../lib/ai/coach.js";
import { listConfiguredProviders, loadProviderKey } from "../lib/ai/key-storage.js";
import { fileToJpegBase64 } from "../lib/ai/image.js";
import { chat, prefs } from "../lib/db.js";
import { openConfirm } from "../lib/ui/dialog.js";
import { createSpeechCapture } from "../lib/speech.js";
import { resolveProviderModel } from "../lib/ai/providers.js";

const CAMERA_ICON = `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM4 5h3.2l1.4-1.8c.2-.3.5-.4.8-.4h5.2c.3 0 .6.1.8.4L16.8 5H20c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V7c0-1.1.9-2 2-2zm8 13c2.8 0 5-2.2 5-5s-2.2-5-5-5-5 2.2-5 5 2.2 5 5 5z"/></svg>`;
const MIC_ICON = `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5-3c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/></svg>`;

export class CoachView extends HTMLElement {
  async connectedCallback() {
    this.history = await chat.load();
    this.pendingProposals = [];
    this.providers = await listConfiguredProviders();
    const appPrefs = await prefs.load();
    if (appPrefs.primaryAiProvider && this.providers.includes(/** @type {any} */ (appPrefs.primaryAiProvider))) {
      this.activeProvider = /** @type {any} */ (appPrefs.primaryAiProvider);
    } else {
      this.activeProvider = this.providers[0] ?? null;
    }
    this.render();
  }

  async persist() {
    const slim = this.history
      .filter((m) => m.role === "user" || m.role === "assistant")
      .map((m) => ({
        role: m.role,
        text: m.text || "",
        toolCalls: m.toolCalls,
        toolResults: m.toolResults,
      }))
      .slice(-40);
    await chat.save(slim);
  }

  render() {
    if (!this.activeProvider) {
      this.innerHTML = `
        <div class="card">
          <h1 class="screen-title">AI Coach</h1>
          <p style="color:var(--muted);font-size:0.9rem;">
            No AI provider is configured yet. Add a bring-your-own API key in Settings to start chatting.
            Your key stays on this device and is only ever sent directly to the provider you choose.
          </p>
          <a class="btn btn--primary" href="#/settings?section=ai">Go to settings</a>
        </div>`;
      return;
    }

    this.innerHTML = `
      <div style="display:flex;justify-content:space-between;align-items:center;gap:0.5rem;margin-bottom:0.5rem;">
        <h1 class="screen-title" style="margin:0;">AI Coach</h1>
        <button type="button" class="chip" data-clear-chat>Clear chat</button>
      </div>
      <div class="coach-log" id="coach-log">
        ${
          this.history.filter((m) => (m.role === "assistant" || m.role === "user") && m.text).length === 0
            ? `<p class="empty-state">Ask about your day, or attach a food photo.</p>`
            : this.history
                .filter((m) => (m.role === "assistant" || m.role === "user") && m.text)
                .map(renderBubble)
                .join("")
        }
        ${this.pendingProposals.map((p, i) => renderProposalCard(p, i)).join("")}
      </div>
      <form class="coach-input" id="coach-form">
        <label class="btn btn--ghost coach-photo-btn" title="Attach a photo" aria-label="Attach a photo">
          ${CAMERA_ICON}<input type="file" accept="image/*" id="coach-photo" style="display:none;" />
        </label>
        ${
          createSpeechCapture().supported
            ? `<button type="button" class="btn btn--ghost coach-photo-btn" data-voice title="Voice" aria-label="Voice input">${MIC_ICON}</button>`
            : ""
        }
        <input type="text" id="coach-text" placeholder="Ask the coach…" autocomplete="off" />
        <button type="submit" class="btn btn--primary">Send</button>
      </form>
      <p id="coach-status" style="color:var(--muted);font-size:0.8rem;margin-top:0.4rem;"></p>
    `;

    this.querySelector("#coach-form").addEventListener("submit", (ev) => this.onSend(ev));
    this.querySelector("[data-voice]")?.addEventListener("click", () => {
      const textInput = /** @type {HTMLInputElement} */ (this.querySelector("#coach-text"));
      const status = this.querySelector("#coach-status");
      if (status) status.textContent = "Listening…";
      createSpeechCapture().start(
        (text) => {
          textInput.value = textInput.value ? `${textInput.value} ${text}` : text;
          if (status) status.textContent = "";
        },
        (err) => {
          if (status) status.textContent = `Voice: ${err}`;
        }
      );
    });
    this.querySelector("[data-clear-chat]")?.addEventListener("click", async () => {
      const ok = await openConfirm({
        title: "Clear chat",
        message: "Delete the conversation history on this device?",
        confirmLabel: "Clear",
        danger: true,
      });
      if (!ok) return;
      this.history = [];
      this.pendingProposals = [];
      await chat.clear();
      this.render();
    });
    this.querySelectorAll("[data-confirm]").forEach((btn) =>
      btn.addEventListener("click", () => this.onConfirm(Number(btn.getAttribute("data-confirm"))))
    );
    this.querySelectorAll("[data-discard]").forEach((btn) =>
      btn.addEventListener("click", () => this.onDiscard(Number(btn.getAttribute("data-discard"))))
    );
    const log = this.querySelector("#coach-log");
    if (log) log.scrollTop = log.scrollHeight;
  }

  async onSend(ev) {
    ev.preventDefault();
    const textInput = /** @type {HTMLInputElement} */ (this.querySelector("#coach-text"));
    const photoInput = /** @type {HTMLInputElement} */ (this.querySelector("#coach-photo"));
    const text = textInput.value.trim();
    const file = photoInput.files?.[0];
    if (!text && !file) return;

    const status = this.querySelector("#coach-status");
    status.textContent = "Thinking…";
    textInput.value = "";
    photoInput.value = "";
    this.history.push({ role: "user", text: text || "[photo attached]" });
    this.render();

    try {
      const config = await loadProviderKey(this.activeProvider);
      if (!config) throw new Error("Provider key missing. Re-add it in Settings.");
      config.model = resolveProviderModel(this.activeProvider, config.model, "primary");
      const image = file ? await fileToJpegBase64(file) : undefined;
      const result = await runCoachTurn({
        providerId: this.activeProvider,
        config,
        history: this.history.slice(0, -1),
        userText: text,
        image,
      });
      this.history = result.messages;
      this.pendingProposals = result.proposals;
      await this.persist();
      this.render();
    } catch (err) {
      this.render();
      this.querySelector("#coach-status").textContent = `Coach error: ${err.message}`;
    }
  }

  async onConfirm(index) {
    const tc = this.pendingProposals[index];
    if (!tc) return;
    if (tc.name === "propose_log_food") {
      const q = encodeURIComponent(JSON.stringify({ ...tc.input, source: "ai_estimated" }));
      location.hash = `#/entry/new?date=${encodeURIComponent(tc.input.date)}&prefill=${q}`;
      return;
    }
    await applyProposal(tc);
    this.pendingProposals = this.pendingProposals.filter((_, i) => i !== index);
    this.render();
  }

  onDiscard(index) {
    this.pendingProposals = this.pendingProposals.filter((_, i) => i !== index);
    this.render();
  }
}

function renderBubble(m) {
  const who = m.role === "assistant" ? "Coach" : "You";
  return `<div class="coach-bubble coach-bubble--${m.role}"><strong>${who}</strong><p>${escapeHtml(m.text || "")}</p></div>`;
}

function renderProposalCard(tc, index) {
  const label =
    {
      propose_log_food: `Log "${tc.input.name}": ${tc.input.calories} kcal (${tc.input.mealType})`,
      propose_log_weight: `Log weight: ${tc.input.weightKg} kg`,
      propose_log_water: `Log water: ${tc.input.amountMl} ml`,
    }[tc.name] ?? tc.name;
  return `
    <div class="card card--glass proposal-card">
      <p>${escapeHtml(label)}</p>
      <div class="btn-row">
        <button class="btn btn--primary" data-confirm="${index}">${tc.name === "propose_log_food" ? "Review & save" : "Confirm"}</button>
        <button class="btn btn--ghost" data-discard="${index}">Discard</button>
      </div>
    </div>`;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
}

customElements.define("coach-view", CoachView);
