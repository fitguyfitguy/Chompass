// @ts-check
import { weights, foodEntries, profile as profileStore, bodyFat, prefs } from "../lib/db.js";
import { dailyTargets } from "../lib/chompass-core/formulas.js";
import { computeWeightForecast, suggestAdaptiveCalories } from "../lib/chompass-core/forecast.js";
import { lineChartSvg, barChartSvg } from "../lib/charts.js";
import { openInput, openConfirm } from "../lib/ui/dialog.js";
import { t } from "../lib/i18n/index.js";

const RANGES = [
  { id: "1W", labelKey: "progress.range_1w", days: 7 },
  { id: "1M", labelKey: "progress.range_1m", days: 30 },
  { id: "3M", labelKey: "progress.range_3m", days: 90 },
  { id: "6M", labelKey: "progress.range_6m", days: 180 },
  { id: "1Y", labelKey: "progress.range_1y", days: 365 },
  { id: "All", labelKey: "progress.range_all", days: 3650 },
];

export class ProgressView extends HTMLElement {
  constructor() {
    super();
    this.rangeId = "1W";
    this.showWeightHistory = false;
    this.showBodyFatHistory = false;
    /** @type {HTMLElement | null} */
    this._tipEl = null;
  }

  connectedCallback() {
    this.render();
  }

  async render() {
    const [allWeights, allEntries, allBf, prof, appPrefs] = await Promise.all([
      weights.all(),
      foodEntries.all(),
      bodyFat.all(),
      profileStore.load(),
      prefs.load(),
    ]);
    if (appPrefs.progressRangeId && RANGES.some((r) => r.id === appPrefs.progressRangeId)) {
      this.rangeId = appPrefs.progressRangeId;
    }
    const activeRange = RANGES.find((r) => r.id === this.rangeId) ?? RANGES[0];
    const startIso = shiftDate(todayIso(), -(activeRange.days - 1));
    const weightUnit = appPrefs.weightUnit === "lb" ? "lb" : "kg";
    const toDisplay = (kg) => (weightUnit === "lb" ? kg * 2.20462 : kg);
    const fromDisplay = (v) => (weightUnit === "lb" ? v / 2.20462 : v);

    const weightPoints = allWeights
      .filter((w) => w.date.slice(0, 10) >= startIso)
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((w) => ({ label: shortDate(w.date), value: toDisplay(w.weightKg), id: w.id, raw: w }));

    const bfPoints = allBf
      .filter((w) => w.date.slice(0, 10) >= startIso)
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((w) => ({
        label: shortDate(w.date),
        value: w.bodyFatPercent > 1 ? w.bodyFatPercent : w.bodyFatPercent * 100,
        id: w.id,
        raw: w,
      }));

    const totalsByDate = new Map();
    for (const e of allEntries) {
      if (e.date < startIso) continue;
      const acc = totalsByDate.get(e.date) ?? {
        calories: 0,
        proteinG: 0,
        carbsG: 0,
        fatG: 0,
        fiberG: 0,
      };
      acc.calories += e.calories;
      acc.proteinG += e.proteinG;
      acc.carbsG += e.carbsG;
      acc.fatG += e.fatG;
      acc.fiberG += e.fiberG ?? 0;
      totalsByDate.set(e.date, acc);
    }
    // Match Android: one bar per logged non-zero day (no calendar zero-padding).
    const calorieBars = [...totalsByDate.entries()]
      .filter(([, t]) => t.calories !== 0)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([d, t]) => ({ label: shortDate(d), value: t.calories }));
    const targets = prof ? dailyTargets(prof) : null;
    const goalWeight = prof?.goalWeightKg != null ? toDisplay(prof.goalWeightKg) : null;

    const currentW = weightPoints.length ? weightPoints[weightPoints.length - 1].value : null;
    const firstW = weightPoints.length ? weightPoints[0].value : null;
    const avgW =
      weightPoints.length > 0 ? weightPoints.reduce((s, p) => s + p.value, 0) / weightPoints.length : null;
    const netChange = currentW != null && firstW != null ? currentW - firstW : null;

    const forecast = prof ? computeWeightForecast({ weights: allWeights, foods: allEntries, profile: prof }) : null;
    const adaptive =
      prof && appPrefs.adaptiveGoals
        ? suggestAdaptiveCalories({ profile: prof, weights: allWeights, foods: allEntries })
        : null;

    const macroDays = [...totalsByDate.values()];
    const macroAvg = (key) =>
      macroDays.length ? macroDays.reduce((s, d) => s + d[key], 0) / macroDays.length : 0;

    // Chart + history only when logged BF entries exist (Android never draws an
    // empty BF plot). Profile body-fat alone is not enough to show this card.
    const hasBodyFatLogs = allBf.length > 0;
    if (!hasBodyFatLogs) this.showBodyFatHistory = false;

    const profileBfPct =
      prof?.bodyFatPercentage != null
        ? prof.bodyFatPercentage > 1
          ? prof.bodyFatPercentage
          : prof.bodyFatPercentage * 100
        : null;
    const currentBf = bfPoints.length
      ? bfPoints[bfPoints.length - 1].value
      : profileBfPct;
    const firstBf = bfPoints.length ? bfPoints[0].value : null;
    const avgBf =
      bfPoints.length > 0 ? bfPoints.reduce((s, p) => s + p.value, 0) / bfPoints.length : null;
    const netBf = currentBf != null && firstBf != null ? currentBf - firstBf : null;

    this.innerHTML = `
      <h1 class="screen-title">${t("progress.title")}</h1>
      <div class="range-pills range-pills--equal" role="tablist" aria-label="${t("progress.title")}">
        ${RANGES.map(
          (r) =>
            `<button type="button" class="chip${r.id === this.rangeId ? " is-active" : ""}" data-range="${r.id}">${t(r.labelKey)}</button>`
        ).join("")}
      </div>
      <div class="chart-tip" hidden data-chart-tip></div>

      <div class="card card--glass">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:0.5rem;margin-bottom:0.5rem;">
          <h2 class="chart-title" style="margin:0;">${t("progress.weight")} (${weightUnit})</h2>
          <button type="button" class="chip progress-log-btn" data-log-weight>${t("progress.weight")}</button>
        </div>
        <div class="stat-badges">
          <div class="stat-badge"><strong>${fmt(currentW)}</strong>Current</div>
          <div class="stat-badge"><strong>${fmt(goalWeight)}</strong>Goal</div>
          <div class="stat-badge"><strong>${fmt(netChange, true)}</strong>Net</div>
          <div class="stat-badge"><strong>${fmt(avgW)}</strong>Average</div>
        </div>
        ${lineChartSvg(
          weightPoints.map(({ label, value }) => ({ label, value })),
          { color: "var(--teal)", unit: weightUnit, goal: goalWeight }
        )}
        ${
          weightPoints.length
            ? `<button type="button" class="btn btn--ghost" style="margin-top:0.6rem;" data-toggle-wh>${this.showWeightHistory ? "Hide" : "Show"} history (${allWeights.length})</button>`
            : ""
        }
        ${
          this.showWeightHistory
            ? `<div class="history-list" style="margin-top:0.6rem;">
                ${allWeights
                  .slice()
                  .sort((a, b) => b.date.localeCompare(a.date))
                  .map(
                    (w) => `
                  <div class="history-item">
                    <span>${shortDate(w.date)} · <strong>${fmt(toDisplay(w.weightKg))} ${weightUnit}</strong></span>
                    <button type="button" data-del-weight="${w.id}" aria-label="Delete weight">Delete</button>
                  </div>`
                  )
                  .join("")}
              </div>`
            : ""
        }
      </div>

      ${
        hasBodyFatLogs
          ? `<div class="card card--glass">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:0.5rem;margin-bottom:0.5rem;">
          <h2 class="chart-title" style="margin:0;">Body fat %</h2>
          <button type="button" class="chip progress-log-btn" data-log-bf>Log body fat</button>
        </div>
        ${
          bfPoints.length === 0
            ? `<p style="margin:0.6rem 0 0;text-align:center;color:var(--muted);font-size:0.9rem;">No body fat readings in this range</p>`
            : `<div class="stat-badges">
          <div class="stat-badge"><strong>${fmt(currentBf)}</strong>Current</div>
          <div class="stat-badge"><strong>${fmt(netBf, true)}</strong>Net</div>
          <div class="stat-badge"><strong>${fmt(avgBf)}</strong>Average</div>
        </div>
        ${lineChartSvg(
          bfPoints.map(({ label, value }) => ({ label, value })),
          { color: "var(--fat)", unit: "%" }
        )}`
        }
        <button type="button" class="btn btn--ghost" style="margin-top:0.6rem;" data-toggle-bfh>${this.showBodyFatHistory ? "Hide" : "Show"} history (${allBf.length})</button>
        ${
          this.showBodyFatHistory
            ? `<div class="history-list" style="margin-top:0.6rem;">
                ${allBf
                  .slice()
                  .sort((a, b) => b.date.localeCompare(a.date))
                  .map((w) => {
                    const pct = w.bodyFatPercent > 1 ? w.bodyFatPercent : w.bodyFatPercent * 100;
                    return `
                  <div class="history-item">
                    <span>${shortDate(w.date)} · <strong>${pct.toFixed(1)}%</strong></span>
                    <button type="button" data-del-bf="${w.id}" aria-label="Delete body fat">Delete</button>
                  </div>`;
                  })
                  .join("")}
              </div>`
            : ""
        }
      </div>`
          : `<div class="btn-row" style="margin:0.25rem 0 0.5rem;">
        <button type="button" class="btn btn--ghost" data-log-bf>Log body fat</button>
      </div>`
      }

      <div class="card card--glass">
        <h2 class="chart-title">Calories${targets ? ` · target ${targets.calories}` : ""}${
          calorieBars.length
            ? ` · avg ${Math.round(calorieBars.reduce((s, b) => s + b.value, 0) / calorieBars.length)}`
            : ""
        }</h2>
        ${barChartSvg(calorieBars, { target: targets?.calories ?? null })}
      </div>

      <div class="card card--glass">
        <h2 class="chart-title">Macro averages (range)</h2>
        <div class="stat-badges">
          <div class="stat-badge" style="color:var(--protein)"><strong>${macroAvg("proteinG").toFixed(0)} g</strong>Protein</div>
          <div class="stat-badge" style="color:var(--carbs)"><strong>${macroAvg("carbsG").toFixed(0)} g</strong>Carbs</div>
          <div class="stat-badge" style="color:var(--fat)"><strong>${macroAvg("fatG").toFixed(0)} g</strong>Fat</div>
          <div class="stat-badge" style="color:var(--teal)"><strong>${macroAvg("fiberG").toFixed(0)} g</strong>Fiber</div>
        </div>
      </div>

      ${
        forecast
          ? `<div class="card card--glass">
              <h2 class="chart-title">Weight forecast</h2>
              <p style="margin:0 0 0.4rem;font-size:0.9rem;">
                Predicted ${forecast.predictedWeeklyChangeKg >= 0 ? "+" : ""}${forecast.predictedWeeklyChangeKg.toFixed(2)} kg/wk
                ${forecast.observedWeeklyChangeKg != null ? ` · observed ${forecast.observedWeeklyChangeKg >= 0 ? "+" : ""}${forecast.observedWeeklyChangeKg.toFixed(2)} kg/wk` : ""}
              </p>
              <p style="margin:0;color:var(--muted);font-size:0.82rem;">
                30d ≈ ${toDisplay(forecast.predictedWeight30dKg).toFixed(1)} ${weightUnit}
                ${forecast.daysToGoal != null ? ` · ~${forecast.daysToGoal} days to goal` : ""}
                ${forecast.trendsDisagree ? " · trends disagree" : ""}
              </p>
              ${
                adaptive
                  ? `<p style="margin:0.6rem 0 0;font-size:0.85rem;">${escapeHtml(adaptive.message)}</p>
                     ${
                       adaptive.changed && adaptive.updatedCalories != null
                         ? `<button type="button" class="btn btn--primary" style="margin-top:0.5rem;" data-apply-adapt="${adaptive.updatedCalories}">Apply ${adaptive.updatedCalories} kcal</button>`
                         : ""
                     }`
                  : ""
              }
            </div>`
          : ""
      }

      <a class="settings-link" href="#/measurements">Body measurements <span>Tape / US Navy</span></a>
    `;

    this.querySelectorAll("[data-range]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        this.rangeId = btn.getAttribute("data-range") || "1W";
        await prefs.save({ progressRangeId: this.rangeId });
        this.render();
      });
    });
    this.bindChartTips();
    this.querySelector("[data-log-weight]")?.addEventListener("click", async () => {
      const raw = await openInput({
        title: "Log weight",
        label: "Weight",
        value: currentW != null ? String(Number(currentW.toFixed(1))) : "",
        unit: weightUnit,
        inputMode: "decimal",
        type: "number",
        confirmLabel: "Save",
      });
      if (raw == null) return;
      const v = Number(raw);
      if (!(v > 0)) return;
      await weights.put({ id: crypto.randomUUID(), date: new Date().toISOString(), weightKg: fromDisplay(v) });
      this.render();
    });
    this.querySelector("[data-log-bf]")?.addEventListener("click", async () => {
      const raw = await openInput({
        title: "Log body fat",
        label: "Body fat",
        value: "",
        unit: "%",
        inputMode: "decimal",
        type: "number",
        confirmLabel: "Save",
      });
      if (raw == null) return;
      const v = Number(raw);
      if (!(v > 0) || v > 100) return;
      await bodyFat.put({
        id: crypto.randomUUID(),
        date: new Date().toISOString(),
        bodyFatPercent: v / 100,
      });
      this.render();
    });
    this.querySelector("[data-toggle-wh]")?.addEventListener("click", () => {
      this.showWeightHistory = !this.showWeightHistory;
      this.render();
    });
    this.querySelector("[data-toggle-bfh]")?.addEventListener("click", () => {
      this.showBodyFatHistory = !this.showBodyFatHistory;
      this.render();
    });
    this.querySelectorAll("[data-del-weight]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const ok = await openConfirm({
          title: "Delete weight",
          message: "Remove this weight entry?",
          confirmLabel: "Delete",
          danger: true,
        });
        if (!ok) return;
        await weights.delete(btn.getAttribute("data-del-weight"));
        this.render();
      });
    });
    this.querySelectorAll("[data-del-bf]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const ok = await openConfirm({
          title: "Delete body fat",
          message: "Remove this body fat entry?",
          confirmLabel: "Delete",
          danger: true,
        });
        if (!ok) return;
        await bodyFat.delete(btn.getAttribute("data-del-bf"));
        this.render();
      });
    });
    this.querySelector("[data-apply-adapt]")?.addEventListener("click", async () => {
      if (!prof) return;
      const kcal = Number(this.querySelector("[data-apply-adapt]")?.getAttribute("data-apply-adapt"));
      await profileStore.save({ ...prof, customCalories: kcal });
      this.render();
    });
  }

  bindChartTips() {
    const tip = /** @type {HTMLElement | null} */ (this.querySelector("[data-chart-tip]"));
    if (!tip) return;
    this.querySelectorAll(".chart-hit").forEach((el) => {
      const show = (ev) => {
        const text = el.getAttribute("data-tip");
        if (!text) return;
        tip.hidden = false;
        tip.textContent = text;
        const pev = /** @type {PointerEvent} */ (ev);
        tip.style.left = `${Math.min(window.innerWidth - 160, Math.max(8, pev.clientX - 40))}px`;
        tip.style.top = `${Math.max(8, pev.clientY - 44)}px`;
      };
      el.addEventListener("pointerdown", show);
      el.addEventListener("click", show);
    });
    this.addEventListener(
      "pointerdown",
      (ev) => {
        if (!(/** @type {Element} */ (ev.target).closest(".chart-hit"))) tip.hidden = true;
      },
      true
    );
  }
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function shiftDate(iso, days) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

function shortDate(iso) {
  const d = iso.includes("T") ? new Date(iso) : new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function fmt(n, signed = false) {
  if (n == null || Number.isNaN(n)) return "—";
  const s = Math.abs(n - Math.round(n)) < 0.05 ? String(Math.round(n)) : n.toFixed(1);
  if (signed && n > 0) return `+${s}`;
  return s;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

customElements.define("progress-view", ProgressView);
