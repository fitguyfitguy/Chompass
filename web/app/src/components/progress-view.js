// @ts-check
import { weights, foodEntries, profile as profileStore, bodyFat, prefs } from "../lib/db.js";
import { dailyTargets } from "../lib/chompass-core/formulas.js";
import { computeWeightForecast, suggestAdaptiveCalories } from "../lib/chompass-core/forecast.js";
import {
  computeWeightTrend,
  resolveProgressRangeId,
} from "../lib/chompass-core/weight-trend.js";
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
const RANGE_IDS = RANGES.map((r) => r.id);

const ICONS = {
  addCircle: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z"/></svg>`,
  listAlt: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M19 5v14H5V5h14m1.1-2H3.9c-.5 0-.9.4-.9.9v16.2c0 .4.4.9.9.9h16.2c.4 0 .9-.5.9-.9V3.9c0-.5-.5-.9-.9-.9zM11 7h6v2h-6V7zm0 4h6v2h-6v-2zm0 4h6v2h-6v-2zM7 7h2v2H7V7zm0 4h2v2H7v-2zm0 4h2v2H7v-2z"/></svg>`,
  chevron: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>`,
};

/** Android MacroProgressRow: colored label + "63g / 75g" + 8dp progress bar. */
function macroRow(label, currentG, goalG, accent) {
  const pct = goalG > 0 ? Math.min(100, (currentG / goalG) * 100) : 0;
  return `
    <div class="macro-progress">
      <div class="macro-progress__head">
        <span style="color:${accent}">${label}</span>
        <span>${t("progress.macro_progress_format", { current: Math.round(currentG), goal: Math.round(goalG) })}</span>
      </div>
      <div class="macro-progress__track"><div class="macro-progress__fill" style="width:${Math.max(2, pct)}%;background:${accent}"></div></div>
    </div>`;
}

export class ProgressView extends HTMLElement {
  constructor() {
    super();
    /** @type {string | null} */
    this.rangeId = null;
    /** @type {"weight" | "body_fat"} */
    this.bodyMetric = "weight";
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
    this.rangeId = resolveProgressRangeId(
      this.rangeId ?? appPrefs.progressRangeId,
      appPrefs.progressDefaultRangeId,
      RANGE_IDS,
    );
    const activeRange = RANGES.find((r) => r.id === this.rangeId) ?? RANGES[0];
    const startIso = shiftDate(todayIso(), -(activeRange.days - 1));
    const weightUnit = appPrefs.weightUnit === "lb" ? "lb" : "kg";
    const toDisplay = (kg) => (weightUnit === "lb" ? kg * 2.20462 : kg);
    const fromDisplay = (v) => (weightUnit === "lb" ? v / 2.20462 : v);

    const filteredWeights = allWeights
      .filter((w) => w.date.slice(0, 10) >= startIso)
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date));
    const weightPoints = filteredWeights.map((w) => ({
      label: shortDate(w.date),
      value: toDisplay(w.weightKg),
      id: w.id,
      raw: w,
      day: w.date.slice(0, 10),
    }));

    const trendKg = computeWeightTrend(
      filteredWeights.map((w) => ({ date: w.date, weightKg: w.weightKg })),
    );
    const trendPoints = trendKg.map((p) => ({
      label: shortDate(`${p.date}T12:00:00.000Z`),
      value: toDisplay(p.valueKg),
      day: p.date,
    }));
    const hasTrend = trendPoints.length > 0;

    const bfPoints = allBf
      .filter((w) => w.date.slice(0, 10) >= startIso)
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((w) => ({
        label: shortDate(w.date),
        value: w.bodyFatPercent > 1 ? w.bodyFatPercent : w.bodyFatPercent * 100,
        id: w.id,
        raw: w,
        day: w.date.slice(0, 10),
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

    const goalBf =
      prof?.goalBodyFatPercentage != null
        ? prof.goalBodyFatPercentage > 1
          ? prof.goalBodyFatPercentage
          : prof.goalBodyFatPercentage * 100
        : null;
    const avgCalories = calorieBars.length
      ? Math.round(calorieBars.reduce((s, b) => s + b.value, 0) / calorieBars.length)
      : null;

    const weightSection = `
      <div class="progress-head">
        <h2 class="progress-title">${t("progress.weight")}</h2>
        <button type="button" class="progress-log-btn" data-log-weight>${ICONS.addCircle}${t("progress.log_weight")}</button>
      </div>
      ${
        weightPoints.length === 0
          ? `<p class="progress-empty">${t("progress.log_first_weight")}</p>`
          : `
      <div class="stat-badges">
        <div class="stat-badge"><strong>${fmt(currentW)} ${weightUnit}</strong>${t("progress.stat_current")}</div>
        <div class="stat-badge"><strong>${goalWeight != null ? `${fmt(goalWeight)} ${weightUnit}` : "—"}</strong>${t("progress.stat_goal")}</div>
        <div class="stat-badge"><strong>${fmt(netChange, true)} ${weightUnit}</strong>${t("progress.stat_net_change")}</div>
        <div class="stat-badge"><strong>${fmt(avgW)} ${weightUnit}</strong>${t("progress.stat_average")}</div>
      </div>
      <div class="chart-legend">
        <span class="legend-swatch"><span class="swatch-dot"></span>${t("progress.weight_raw_legend")}</span>
        ${hasTrend ? `<span class="legend-swatch"><span class="swatch-line"></span>${t("progress.weight_trend_legend")}</span>` : ""}
      </div>
      ${!hasTrend ? `<p class="legend-hint">${t("progress.weight_trend_need_more")}</p>` : ""}
      ${lineChartSvg(
        weightPoints.map(({ label, value, day }) => ({ label, value, day })),
        {
          color: "var(--teal)",
          unit: weightUnit,
          goal: goalWeight,
          trend: hasTrend ? trendPoints.map(({ label, value, day }) => ({ label, value, day })) : null,
          trendColor: "var(--protein)",
          rangeLabel: yearSpanLabel(weightPoints),
          grid: true,
        }
      )}
      ${
        this.showWeightHistory
          ? `<div class="history-list">
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
      }`
      }
    `;

    const bodyFatSection = hasBodyFatLogs
      ? `
      <div class="progress-head">
        <h2 class="progress-title">${t("progress.metric_body_fat")}</h2>
        <button type="button" class="progress-log-btn" data-log-bf>${ICONS.addCircle}${t("progress.log_body_fat")}</button>
      </div>
      ${
        bfPoints.length === 0
          ? `<p class="progress-empty">${t("progress.log_first_body_fat")}</p>`
          : `
      <div class="stat-badges">
        <div class="stat-badge"><strong>${fmt(currentBf)}%</strong>${t("progress.stat_current")}</div>
        <div class="stat-badge"><strong>${goalBf != null ? `${fmt(goalBf)}%` : "—"}</strong>${t("progress.stat_goal")}</div>
        <div class="stat-badge"><strong>${fmt(netBf, true)}%</strong>${t("progress.stat_net_change")}</div>
        <div class="stat-badge"><strong>${fmt(avgBf)}%</strong>${t("progress.stat_average")}</div>
      </div>
      ${lineChartSvg(
        bfPoints.map(({ label, value }) => ({ label, value })),
        { color: "var(--fat)", unit: "%", rangeLabel: yearSpanLabel(bfPoints) }
      )}
      ${
        this.showBodyFatHistory
          ? `<div class="history-list">
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
      }`
      }
    `
      : `
      <div class="btn-row">
        <button type="button" class="btn btn--ghost" data-log-bf>${t("progress.log_body_fat")}</button>
      </div>`;

    this.innerHTML = `
      <div class="range-chips" role="tablist" aria-label="${t("progress.title")}">
        ${RANGES.map(
          (r) =>
            `<button type="button" class="chip${r.id === this.rangeId ? " is-active" : ""}" data-range="${r.id}" role="tab" aria-selected="${r.id === this.rangeId}">${t(r.labelKey)}</button>`
        ).join("")}
      </div>
      <div class="chart-tip" hidden data-chart-tip></div>

      ${
        hasBodyFatLogs
          ? `<div class="metric-toggle" role="tablist" aria-label="${t("progress.body_fat")}">
        <button type="button" class="metric-toggle__segment${this.bodyMetric === "weight" ? " is-active" : ""}" data-metric="weight" role="tab" aria-selected="${this.bodyMetric === "weight"}">${t("progress.weight")}</button>
        <button type="button" class="metric-toggle__segment${this.bodyMetric === "body_fat" ? " is-active" : ""}" data-metric="body_fat" role="tab" aria-selected="${this.bodyMetric === "body_fat"}">${t("progress.metric_body_fat")}</button>
      </div>`
          : ""
      }

      <div class="card card--glass">
        ${this.bodyMetric === "body_fat" ? bodyFatSection : weightSection}
      </div>

      ${allWeights.length ? `
      <button type="button" class="history-link" data-toggle-wh>
        <span class="history-link__icon">${ICONS.listAlt}</span>
        <span class="history-link__text">
          <strong>${t("progress.weight_history")}</strong>
          <span>${t("progress.history_count_format", { count: allWeights.length })}</span>
        </span>
        <span class="history-link__chevron">${ICONS.chevron}</span>
      </button>` : ""}

      ${hasBodyFatLogs && allBf.length ? `
      <button type="button" class="history-link" data-toggle-bfh>
        <span class="history-link__icon">${ICONS.listAlt}</span>
        <span class="history-link__text">
          <strong>${t("progress.body_fat_history")}</strong>
          <span>${t("progress.history_count_format", { count: allBf.length })}</span>
        </span>
        <span class="history-link__chevron">${ICONS.chevron}</span>
      </button>` : ""}

      <div class="card card--glass">
        <div class="progress-head">
          <h2 class="progress-title">${t("diary.calories")}</h2>
          ${avgCalories != null ? `<span class="progress-avg">${t("progress.avg_format", { avg: avgCalories })}</span>` : ""}
        </div>
        ${
          calorieBars.length === 0
            ? `<p class="progress-empty">${t("progress.no_food")}</p>`
            : barChartSvg(calorieBars, { target: targets?.calories ?? null })
        }
      </div>

      ${
        targets
          ? `<div class="card card--glass">
        <h2 class="progress-title">${t("progress.macro_averages")}</h2>
        ${macroRow(t("onboarding.plan.protein"), macroAvg("proteinG"), targets.proteinG, "var(--protein)")}
        ${macroRow(t("onboarding.plan.carbs"), macroAvg("carbsG"), targets.carbsG, "var(--carbs)")}
        ${macroRow(t("onboarding.plan.fat"), macroAvg("fatG"), targets.fatG, "var(--fat)")}
      </div>`
          : ""
      }

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
    this.querySelectorAll("[data-metric]").forEach((btn) => {
      btn.addEventListener("click", () => {
        this.bodyMetric = btn.getAttribute("data-metric") === "body_fat" ? "body_fat" : "weight";
        this.render();
      });
    });
    this.bindChartTips();
    this.querySelector("[data-log-weight]")?.addEventListener("click", async () => {
      const raw = await openInput({
        title: t("progress.log_weight"),
        label: t("progress.weight"),
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
        title: t("progress.log_body_fat"),
        label: t("progress.body_fat"),
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

/** Local calendar YYYY-MM-DD (avoid UTC shift from toISOString). */
function localIsoDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function todayIso() {
  return localIsoDate(new Date());
}

function shiftDate(iso, days) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return localIsoDate(d);
}

function shortDate(iso) {
  const d = iso.includes("T") ? new Date(iso) : new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

/**
 * Year-inclusive range label when the plotted span crosses calendar years
 * (e.g. the All range with 2y of history); otherwise undefined so charts keep
 * their compact "Aug 6 → Aug 6" style.
 * @param {{label: string, day?: string}[]} points
 */
function yearSpanLabel(points) {
  const day = (p) => p.day ?? p.label;
  if (points.length < 2) return undefined;
  const first = day(points[0]);
  const last = day(points[points.length - 1]);
  if (first.slice(0, 4) === last.slice(0, 4)) return undefined;
  const fmt = (iso) => {
    const d = iso.includes("T") ? new Date(iso) : new Date(`${iso}T00:00:00`);
    return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
  };
  return `${fmt(first)} → ${fmt(last)}`;
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
