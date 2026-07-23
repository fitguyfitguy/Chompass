// @ts-check
import { weights, foodEntries, profile as profileStore } from "../lib/db.js";
import { dailyTargets } from "../lib/nofud-core/formulas.js";
import { lineChartSvg } from "../lib/charts.js";

const TREND_DAYS = 30;

export class ProgressView extends HTMLElement {
  connectedCallback() {
    this.render();
  }

  async render() {
    const [allWeights, allEntries, prof] = await Promise.all([weights.all(), foodEntries.all(), profileStore.load()]);

    const weightPoints = allWeights
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date))
      .slice(-TREND_DAYS)
      .map((w) => ({ label: shortDate(w.date), value: w.weightKg }));

    const days = lastNDays(TREND_DAYS);
    const totalsByDate = new Map();
    for (const e of allEntries) {
      const acc = totalsByDate.get(e.date) ?? { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 };
      acc.calories += e.calories;
      acc.proteinG += e.proteinG;
      acc.carbsG += e.carbsG;
      acc.fatG += e.fatG;
      totalsByDate.set(e.date, acc);
    }
    const seriesFor = (key) => days.map((d) => ({ label: shortDate(d), value: totalsByDate.get(d)?.[key] ?? 0 }));

    const targetCalories = prof ? dailyTargets(prof).calories : null;

    this.innerHTML = `
      <h1 style="font-family:var(--font-display);font-size:1.3rem;margin:0 0 1rem;">Progress</h1>
      <div class="card">
        <h2 class="chart-title">Weight (kg)</h2>
        ${lineChartSvg(weightPoints, { color: "var(--teal)", unit: "kg" })}
      </div>
      <div class="card">
        <h2 class="chart-title">Calories — last ${TREND_DAYS} days${targetCalories ? ` · target ${targetCalories}` : ""}</h2>
        ${lineChartSvg(seriesFor("calories"), { color: "var(--teal)" })}
      </div>
      <div class="card">
        <h2 class="chart-title">Protein g — last ${TREND_DAYS} days</h2>
        ${lineChartSvg(seriesFor("proteinG"), { color: "#e0a85c", unit: "g" })}
      </div>
      <div class="card">
        <h2 class="chart-title">Carbs g — last ${TREND_DAYS} days</h2>
        ${lineChartSvg(seriesFor("carbsG"), { color: "#5c9ee0", unit: "g" })}
      </div>
      <div class="card">
        <h2 class="chart-title">Fat g — last ${TREND_DAYS} days</h2>
        ${lineChartSvg(seriesFor("fatG"), { color: "#e05c8a", unit: "g" })}
      </div>
    `;
  }
}

function shortDate(iso) {
  const d = iso.includes("T") ? new Date(iso) : new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function lastNDays(n) {
  const out = [];
  const today = new Date();
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    out.push(d.toISOString().slice(0, 10));
  }
  return out;
}

customElements.define("progress-view", ProgressView);
