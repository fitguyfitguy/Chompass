// @ts-check
import { runCoachTurn, applyProposal } from "../lib/ai/coach.js";
import { listConfiguredProviders, loadProviderKey } from "../lib/ai/key-storage.js";
import { fileToJpegBase64 } from "../lib/ai/image.js";

export class CoachView extends HTMLElement {
  async connectedCallback() {
    this.history = [];
    this.pendingProposals = [];
    this.providers = await listConfiguredProviders();
    this.activeProvider = this.providers[0] ?? null;
    this.render();
  }

  render() {
    if (!this.activeProvider) {
      this.innerHTML = `
        <div class="card">
          <h1 style="font-family:var(--font-display);font-size:1.3rem;margin:0 0 0.5rem;">AI Coach</h1>
          <p style="color:var(--muted);font-size:0.9rem;">
            No AI provider is configured yet. Add a bring-your-own API key in Settings to start chatting —
            your key stays on this device and is only ever sent directly to the provider you choose.
          </p>
          <a class="btn btn--primary" href="#/settings">Go to settings</a>
        </div>`;
      return;
    }

    this.innerHTML = `
      <div class="coach-log" id="coach-log">
        ${
          this.history.length === 0
            ? `<p class="empty-state">Ask about your day, or attach a food photo.</p>`
            : this.history.filter((m) => m.role !== "user" || m.text).map(renderBubble).join("")
        }
        ${this.pendingProposals.map((p, i) => renderProposalCard(p, i)).join("")}
      </div>
      <form class="coach-input" id="coach-form">
        <label class="btn btn--ghost coach-photo-btn" title="Attach a photo">
          📷<input type="file" accept="image/*" id="coach-photo" style="display:none;" />
        </label>
        <input type="text" id="coach-text" placeholder="Ask the coach…" autocomplete="off" />
        <button type="submit" class="btn btn--primary">Send</button>
      </form>
      <p id="coach-status" style="color:var(--muted);font-size:0.8rem;margin-top:0.4rem;"></p>
    `;

    this.querySelector("#coach-form").addEventListener("submit", (ev) => this.onSend(ev));
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
      if (!config) throw new Error("Provider key missing — re-add it in Settings.");
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
      propose_log_food: `Log "${tc.input.name}" — ${tc.input.calories} kcal (${tc.input.mealType})`,
      propose_log_weight: `Log weight — ${tc.input.weightKg} kg`,
      propose_log_water: `Log water — ${tc.input.amountMl} ml`,
    }[tc.name] ?? tc.name;
  return `
    <div class="card proposal-card">
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
