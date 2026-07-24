// @ts-check
import { measurements, profile as profileStore } from "../lib/db.js";
import { usNavyBodyFatPercent, waistToHipRatio, waistToHeightRatio } from "../lib/chompass-core/formulas.js";

export class MeasurementsView extends HTMLElement {
  connectedCallback() {
    this.render();
  }

  async render() {
    const [all, prof] = await Promise.all([measurements.all(), profileStore.load()]);
    const sorted = all.slice().sort((a, b) => b.date.localeCompare(a.date));
    const latest = sorted[0];
    let navy = null;
    let whr = null;
    let wth = null;
    if (latest && prof && latest.waistCm != null && latest.neckCm != null) {
      navy = usNavyBodyFatPercent({
        sex: prof.sex,
        waistCm: latest.waistCm,
        neckCm: latest.neckCm,
        heightCm: prof.heightCm,
        hipsCm: latest.hipsCm ?? undefined,
      });
      if (latest.hipsCm) whr = waistToHipRatio(latest.waistCm, latest.hipsCm);
      wth = waistToHeightRatio(latest.waistCm, prof.heightCm);
    }

    this.innerHTML = `
      <h1 class="screen-title">Body measurements</h1>
      <p style="color:var(--muted);font-size:0.9rem;margin:0 0 1rem;">
        <a href="#/progress">← Progress</a>
      </p>
      <form class="entry-form card" id="m-form">
        <div class="field-row">
          <div class="field"><label for="neckCm">Neck cm</label><input id="neckCm" name="neckCm" type="number" step="0.1" min="0" /></div>
          <div class="field"><label for="waistCm">Waist cm</label><input id="waistCm" name="waistCm" type="number" step="0.1" min="0" required /></div>
          <div class="field"><label for="hipsCm">Hips cm</label><input id="hipsCm" name="hipsCm" type="number" step="0.1" min="0" /></div>
        </div>
        <div class="field-row">
          <div class="field"><label for="chestCm">Chest</label><input id="chestCm" name="chestCm" type="number" step="0.1" min="0" /></div>
          <div class="field"><label for="upperArmCm">Upper arm</label><input id="upperArmCm" name="upperArmCm" type="number" step="0.1" min="0" /></div>
          <div class="field"><label for="thighCm">Thigh</label><input id="thighCm" name="thighCm" type="number" step="0.1" min="0" /></div>
        </div>
        <div class="field-row field-row--2">
          <div class="field"><label for="calfCm">Calf cm</label><input id="calfCm" name="calfCm" type="number" step="0.1" min="0" /></div>
          <div class="field"><label for="wristCm">Wrist cm</label><input id="wristCm" name="wristCm" type="number" step="0.1" min="0" /></div>
        </div>
        <button type="submit" class="btn btn--primary">Save measurement</button>
      </form>
      ${
        navy != null || whr != null || wth != null
          ? `<div class="card">
              <h2 class="chart-title">From latest</h2>
              <div class="stat-badges">
                ${navy != null ? `<div class="stat-badge"><strong>${navy.toFixed(1)}%</strong>US Navy BF</div>` : ""}
                ${whr != null ? `<div class="stat-badge"><strong>${whr.toFixed(2)}</strong>WHR</div>` : ""}
                ${wth != null ? `<div class="stat-badge"><strong>${wth.toFixed(2)}</strong>WTH</div>` : ""}
              </div>
            </div>`
          : ""
      }
      <div class="card">
        <h2 class="chart-title">History</h2>
        ${
          sorted.length === 0
            ? `<p class="empty-state" style="padding:1rem 0;">No measurements yet.</p>`
            : `<div class="history-list">
                ${sorted
                  .map(
                    (m) => `
                  <div class="history-item">
                    <span>${shortDate(m.date)} · waist ${m.waistCm ?? "—"} · neck ${m.neckCm ?? "—"} · hips ${m.hipsCm ?? "—"}</span>
                    <button type="button" data-del="${m.id}">Delete</button>
                  </div>`
                  )
                  .join("")}
              </div>`
        }
      </div>
    `;

    this.querySelector("#m-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      const num = (k) => {
        const v = fd.get(k);
        return v ? Number(v) : null;
      };
      await measurements.put({
        id: crypto.randomUUID(),
        date: new Date().toISOString(),
        neckCm: num("neckCm"),
        waistCm: num("waistCm"),
        hipsCm: num("hipsCm"),
        chestCm: num("chestCm"),
        upperArmCm: num("upperArmCm"),
        thighCm: num("thighCm"),
        calfCm: num("calfCm"),
        wristCm: num("wristCm"),
      });
      this.render();
    });
    this.querySelectorAll("[data-del]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        await measurements.delete(btn.getAttribute("data-del"));
        this.render();
      });
    });
  }
}

function shortDate(iso) {
  const d = iso.includes("T") ? new Date(iso) : new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

customElements.define("measurements-view", MeasurementsView);
