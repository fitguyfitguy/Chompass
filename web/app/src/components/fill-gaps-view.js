// @ts-check
/**
 * AI fill-missing gaps view (WP2 batch 4): the gapped favorites/diary rows of
 * the current scan as a queue — per-row run (AI fill, only-missing merge),
 * per-row / apply-all commit, one undo toast after the final apply. Web twin
 * of Android ui/home/FillGapsSheet.kt; session state lives in the element
 * instance and dies with the sheet. Also mounts the Home banner chip
 * (install-banner DOM precedent) and opens the sheet.
 */

import { foodEntries, prefs } from "../lib/db.js";
import { listFavorites, updateFavorite } from "../lib/saved-meals.js";
import { scanGaps, bannerVisible, markBannerShown, dismissBanner, FILL_GAPS_ENABLED } from "../lib/fill-gaps.js";
import { fillMissingFrom } from "../lib/chompass-core/entry-gaps.js";
import { buildCorrectDiff } from "../lib/ai/correct-diff.js";
import { isAbortError } from "../lib/ai/analysis-phase.js";
import { todayIso } from "../lib/date.js";
import { escapeHtml, escapeAttr } from "../lib/ui/html.js";
import { t } from "../lib/i18n/index.js";
import { showUndoToast } from "../lib/ui/toast.js";
import { openSheet } from "../lib/ui/sheet.js";

/**
 * @typedef {"pending"|"running"|"filled"|"failed"|"applied"} FillStatus
 * @typedef {{
 *   favorite: boolean,
 *   key: string,
 *   original: import('../lib/chompass-core/models.js').FoodEntry,
 *   merged: import('../lib/chompass-core/models.js').FoodEntry | null,
 *   status: FillStatus,
 *   error: string,
 * }} FillRow
 */

/** diary-view sits behind the sheet; re-render it so Home totals follow. */
function refreshHostView() {
  const host = /** @type {any} */ (document.querySelector("diary-view"));
  if (host && typeof host.render === "function") void host.render();
}

export class FillGapsView extends HTMLElement {
  connectedCallback() {
    /** @type {FillRow[]} */
    this.rows = [];
    /** Pre-apply originals for undo, keyed like the scan (kind:id). */
    this._written = new Map();
    this._running = false;
    /** @type {AbortController|null} */
    this._abort = null;
    this.load();
  }

  disconnectedCallback() {
    // Cancel a run whose sheet is gone; nothing is persisted until Apply.
    this._abort?.abort();
    this._abort = null;
  }

  async load() {
    const [favs, all] = await Promise.all([listFavorites(), foodEntries.all()]);
    const monthPrefix = todayIso().slice(0, 7);
    const monthEntries = all.filter((e) => typeof e?.date === "string" && e.date.startsWith(monthPrefix));
    const scan = scanGaps({ favorites: favs, monthEntries });
    this.rows = scan.items.map(({ favorite, entry }) => ({
      favorite,
      key: `${favorite ? "f" : "d"}:${entry.id}`,
      original: entry,
      merged: null,
      status: /** @type {FillStatus} */ ("pending"),
      error: "",
    }));
    this.render();
  }

  render() {
    const anyFilled = this.rows.some((r) => r.status === "filled");
    this.innerHTML = `
      <div class="fill-gaps">
        ${anyFilled ? `<button type="button" class="btn btn--primary" data-gaps-apply-all>${escapeHtml(t("gaps.apply_all"))}</button>` : ""}
        <div class="copy-select">
          ${this.rows.map((row, i) => this.rowHtml(row, i)).join("")}
        </div>
      </div>
    `;
    this.querySelector("[data-gaps-apply-all]")?.addEventListener("click", () => {
      void this.applyAll();
    });
    for (const el of this.querySelectorAll("[data-gaps-run]")) {
      el.addEventListener("click", () => {
        void this.runRow(Number(el.getAttribute("data-gaps-run")));
      });
    }
    for (const el of this.querySelectorAll("[data-gaps-apply]")) {
      el.addEventListener("click", () => {
        void this.applyRow(Number(el.getAttribute("data-gaps-apply")));
      });
    }
  }

  /**
   * @param {FillRow} row
   * @param {number} index
   */
  rowHtml(row, index) {
    const statusLabel =
      row.status === "pending" || row.status === "running" || row.status === "failed"
        ? `<span class="field-hint">${escapeHtml(t(`gaps.state_${row.status}`))}</span>`
        : "";
    const noValuesHint =
      row.status === "filled" && !diff.length
        ? `<span class="field-hint">${escapeHtml(t("gaps.no_values"))}</span>`
        : "";
    const diffHtml = diff.length
      ? `<div class="fill-gaps-row__diff">${diff
          .map((d) => `${escapeHtml(d.label)}: ${escapeHtml(d.before)} → ${escapeHtml(d.after)}`)
          .map((line) => `<div>${line}</div>`)
          .join("")}</div>`
      : "";
    const errorHtml = row.error
      ? `<div class="entry-correct-error">${escapeHtml(row.error)}</div>`
      : "";
    const runLabel = row.status === "failed" ? t("gaps.retry") : t("gaps.run");
    const runBtn =
      row.status === "pending" || row.status === "failed"
        ? `<button type="button" class="btn btn--ghost btn--sm" data-gaps-run="${index}">${escapeHtml(runLabel)}</button>`
        : "";
    const applyBtn =
      row.status === "filled"
        ? `<button type="button" class="btn btn--primary btn--sm" data-gaps-apply="${index}">${escapeHtml(t("gaps.apply"))}</button>`
        : "";
    return `
      <div class="fill-gaps-row">
        <div class="fill-gaps-row__info">
          <strong>${escapeHtml(row.original.name)}</strong>
          ${statusLabel}
          ${noValuesHint}
          ${diffHtml}
          ${errorHtml}
        </div>
        <div class="fill-gaps-row__actions">
          ${runBtn}
          ${applyBtn}
        </div>
      </div>
    `;
  }

  /**
   * One AI fill for a row: the estimate merges ONLY missing values into the
   * stored entry; an unchanged merge marks the row filled-no-op (empty diff).
   * @param {number} index
   */
  async runRow(index) {
    const row = this.rows[index];
    if (!row || this._running) return;
    if (row.status !== "pending" && row.status !== "failed") return;

    const resolved = await resolveProvider();
    if (!resolved) {
      row.status = "failed";
      row.error = t("entry.error_key_missing");
      this.render();
      return;
    }

    this._running = true;
    row.status = "running";
    row.error = "";
    this.render();
    const ac = new AbortController();
    this._abort = ac;

    try {
      const { fillFoodEntry } = await import("../lib/ai/food-analyze.js");
      const estimate = await fillFoodEntry({
        providerId: resolved.providerId,
        config: resolved.config,
        entry: row.original,
        signal: ac.signal,
      });
      if (ac.signal.aborted) return;
      const merged = fillMissingFrom(row.original, estimate);
      row.merged = merged;
      row.status = "filled";
    } catch (err) {
      if (!isAbortError(err) && !ac.signal.aborted) {
        row.status = "failed";
        row.error = err instanceof Error ? err.message : String(err);
      }
    } finally {
      this._running = false;
      this._abort = null;
      this.render();
    }
  }

  /**
   * @param {FillRow} row
   * @returns {Promise<boolean>} true when a value was written
   */
  async writeRow(row) {
    if (!row.merged) return false; // filled-no-op: nothing to persist
    this._written.set(row.key, { favorite: row.favorite, entry: row.original });
    if (row.favorite) {
      await updateFavorite(row.merged);
    } else {
      await foodEntries.put(row.merged);
    }
    return true;
  }

  /** @param {number} index */
  async applyRow(index) {
    const row = this.rows[index];
    if (!row || row.status !== "filled") return;
    await this.writeRow(row);
    row.status = "applied";
    refreshHostView();
    this.render();
    // Undo toast after the final apply (single-level toast: an Apply-all
    // toast follows its own write batch).
    if (this.rows.every((r) => r.status === "applied")) this.showUndo();
  }

  async applyAll() {
    for (const row of this.rows) {
      if (row.status !== "filled") continue;
      await this.writeRow(row);
      row.status = "applied";
    }
    refreshHostView();
    this.render();
    this.showUndo();
  }

  showUndo() {
    const n = this._written.size;
    if (!n) return;
    const originals = [...this._written.values()];
    showUndoToast(t("diary.fill_applied", { n }), async () => {
      for (const { favorite, entry } of originals) {
        if (favorite) {
          await updateFavorite(entry);
        } else {
          await foodEntries.put(entry);
        }
      }
      this._written.clear();
      refreshHostView();
      await this.load();
    });
  }
}

customElements.define("fill-gaps-view", FillGapsView);

/**
 * Resolve the primary AI provider for gap fills (same resolution as the
 * entry-form correct/fill flows). Null = no usable provider key.
 */
async function resolveProvider() {
  const { listConfiguredProviders, loadProviderKey } = await import(
    "../lib/ai/key-storage.js"
  );
  const providers = await listConfiguredProviders();
  if (!providers.length) return null;
  const appPrefs = await prefs.load();
  /** @type {import('../lib/ai/key-storage.js').ProviderId} */
  let providerId = /** @type {import('../lib/ai/key-storage.js').ProviderId} */ (providers[0]);
  if (
    appPrefs.primaryAiProvider &&
    providers.includes(/** @type {import('../lib/ai/key-storage.js').ProviderId} */ (appPrefs.primaryAiProvider))
  ) {
    providerId = /** @type {import('../lib/ai/key-storage.js').ProviderId} */ (appPrefs.primaryAiProvider);
  }
  const config = await loadProviderKey(providerId);
  if (!config) return null;
  return { providerId, config };
}

/** @returns {ReturnType<typeof openSheet>} */
export function openFillGapsSheet() {
  return openSheet({
    title: t("gaps.title"),
    body: `<fill-gaps-view></fill-gaps-view>`,
  });
}

/** Banner scan per home render (the contingency carve-out from a cached
 * scan): IndexedDB getAll + a gapReport per row is cheap next to the render
 * itself, and the minute-tick re-renders are the only automatic callers. A
 * live scan keeps the count honest right after fills / new logs; the gaps
 * sheet rescans on open regardless. */
export async function maybeShowFillGapsBanner(appPrefs) {
  if (!FILL_GAPS_ENABLED || appPrefs.aiFeaturesEnabled === false) {
    removeFillGapsBanner();
    return;
  }
  if (document.querySelector(".install-banner")) {
    // Never stack with the install banner.
    removeFillGapsBanner();
    return;
  }
  const [favs, all] = await Promise.all([listFavorites(), foodEntries.all()]);
  const monthPrefix = todayIso().slice(0, 7);
  const scan = scanGaps({
    favorites: favs,
    monthEntries: all.filter((e) => typeof e?.date === "string" && e.date.startsWith(monthPrefix)),
  });
  if (!bannerVisible(scan)) {
    removeFillGapsBanner();
    return;
  }
  let banner = /** @type {HTMLElement | null} */ (document.querySelector(".fill-gaps-banner"));
  if (!banner) {
    banner = document.createElement("div");
    banner.className = "fill-gaps-banner";
    banner.setAttribute("role", "status");
    banner.innerHTML = `
      <span class="fill-gaps-banner__text"></span>
      <div class="fill-gaps-banner__actions">
        <button type="button" class="fill-gaps-banner__dismiss" aria-label="${escapeAttr(t("install.dismiss"))}">✕</button>
      </div>
    `;
    banner.querySelector(".fill-gaps-banner__dismiss")?.addEventListener("click", (ev) => {
      ev.stopPropagation();
      dismissBanner(scan);
      banner?.remove();
    });
    banner.addEventListener("click", () => {
      removeFillGapsBanner();
      openFillGapsSheet();
    });
    document.body.appendChild(banner);
  }
  const text = banner.querySelector(".fill-gaps-banner__text");
  if (text) text.textContent = t("diary.fill_banner", { n: scan.count });
  markBannerShown();
}

export function removeFillGapsBanner() {
  document.querySelector(".fill-gaps-banner")?.remove();
}
