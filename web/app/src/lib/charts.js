// @ts-check
// Minimal hand-rolled SVG charts — no canvas library.

/**
 * Downsample chronological points to at most maxPoints (keep ends).
 * @template {{label: string, value: number, day?: string}} T
 * @param {T[]} points
 * @param {number} [maxPoints]
 * @returns {T[]}
 */
export function downsamplePoints(points, maxPoints = 60) {
  if (points.length <= maxPoints) return points;
  /** @type {T[]} */
  const out = [];
  const last = points.length - 1;
  for (let i = 0; i < maxPoints; i++) {
    const idx = Math.round((i / (maxPoints - 1)) * last);
    out.push(points[idx]);
  }
  return out;
}

/**
 * Calendar epoch day (UTC) for an ISO date (yyyy-MM-dd or full timestamp).
 * Returns null for unparseable input so callers can fall back to index space.
 * @param {string | undefined} iso
 * @returns {number | null}
 */
export function epochDay(iso) {
  if (!iso) return null;
  const day = String(iso).slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return null;
  const [y, m, d] = day.split("-").map(Number);
  return Math.floor(Date.UTC(y, m - 1, d) / 86_400_000);
}

/**
 * @param {{label: string, value: number, day?: string}[]} points chronological, ascending
 * @param {{width?: number, height?: number, color?: string, unit?: string, goal?: number|null, interactive?: boolean, trend?: {label: string, value: number, day?: string}[]|null, trendColor?: string, rangeLabel?: string|null, grid?: boolean}} [opts]
 * @returns {string} inline SVG markup
 */
export function lineChartSvg(points, opts = {}) {
  const width = opts.width ?? 320;
  const height = opts.height ?? 120;
  const padding = 24;
  const color = opts.color ?? "var(--teal)";
  const trendColor = opts.trendColor ?? "var(--protein)";
  const unit = opts.unit ?? "";
  const goal = opts.goal;
  const interactive = opts.interactive !== false;
  const series = downsamplePoints(points);
  const gridLines = opts.grid
    ? [0.25, 0.5, 0.75]
        .map(
          (f) =>
            `<line x1="${padding}" y1="${(padding + f * (height - padding * 2)).toFixed(1)}" x2="${width - padding}" y2="${(padding + f * (height - padding * 2)).toFixed(1)}" class="chart-grid" />`,
        )
        .join("")
    : "";
  /** @type {{label: string, value: number, day?: string}[]} */
  // Downsample the trend series too: callers pass per-day trend points (2y
  // history = 700+). x positions are date-based below, so this only bounds
  // the path length.
  const trendSeries = opts.trend && opts.trend.length ? downsamplePoints(opts.trend) : [];

  if (series.length === 0) {
    return `<svg viewBox="0 0 ${width} ${height}" class="chart-svg"><text x="${width / 2}" y="${height / 2}" text-anchor="middle" class="chart-empty">No data yet</text></svg>`;
  }

  const values = series.map((p) => p.value);
  if (goal != null) values.push(goal);
  for (const p of trendSeries) values.push(p.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const stepX = series.length > 1 ? (width - padding * 2) / (series.length - 1) : 0;

  const yFor = (v) => height - padding - ((v - min) / range) * (height - padding * 2);

  // Place points on the real calendar axis when the series carries day
  // stamps (Android maps x from timestamps: (t - tStart) / tRange). Index
  // space is only a fallback for series without days (e.g. body-fat).
  const dayOf = (p) => epochDay(p.day);
  const firstDay = series.length ? dayOf(series[0]) : null;
  const lastDay = series.length ? dayOf(series[series.length - 1]) : null;
  const dateBased = firstDay != null && lastDay != null && lastDay > firstDay;
  const spanDays = dateBased ? lastDay - firstDay : 0;
  const xForDay = (day) =>
    dateBased ? padding + ((day - firstDay) / spanDays) * (width - padding * 2) : NaN;

  const coords = series.map((p, i) => {
    const d = dayOf(p);
    const x = d != null && dateBased ? xForDay(d) : padding + i * stepX;
    return [x, yFor(p.value)];
  });

  // Trend overlay shares the same date axis — never label/index matching:
  // year-less labels collide across years (a 2026 "Aug 7" trend point matched
  // the 2024 raw point, and the dashed line's last segment shot back to x=24).
  const trendCoords = [];
  for (let j = 0; j < trendSeries.length; j++) {
    const p = trendSeries[j];
    const d = dayOf(p);
    let x;
    if (d != null && dateBased) {
      x = xForDay(d);
    } else if (series.length > 0) {
      // No usable days: nearest raw point by day, else even index spacing.
      let idx = -1;
      for (let i = 0; i < series.length; i++) {
        if (p.day && series[i].day === p.day) { idx = i; break; }
      }
      if (idx < 0) {
        idx = trendSeries.length > 1
          ? Math.round((j / Math.max(1, trendSeries.length - 1)) * (series.length - 1))
          : 0;
      }
      x = padding + idx * stepX;
    } else {
      continue;
    }
    trendCoords.push([x, yFor(p.value)]);
  }

  const path = coords.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  const trendPath =
    trendCoords.length >= 2
      ? trendCoords.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ")
      : "";
  const [lastX, lastY] = coords[coords.length - 1];
  const goalY = goal != null ? yFor(goal) : null;

  const hits = interactive
    ? coords
        .map(([x, y], i) => {
          const p = series[i];
          const tip = `${p.label}: ${formatNum(p.value)}${unit}`;
          return `<circle class="chart-hit" data-tip="${escapeAttr(tip)}" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="10" fill="transparent"><title>${escapeHtml(tip)}</title></circle>`;
        })
        .join("")
    : "";

  return `
    <svg viewBox="0 0 ${width} ${height}" class="chart-svg chart-svg--interactive" preserveAspectRatio="none">
      ${gridLines}
      ${
        goalY != null
          ? `<line x1="${padding}" y1="${goalY.toFixed(1)}" x2="${width - padding}" y2="${goalY.toFixed(1)}" stroke="var(--teal)" stroke-width="1.5" stroke-dasharray="4 4" opacity="0.7" />`
          : ""
      }
      ${
        trendPath
          ? `<path d="${trendPath}" fill="none" stroke="${trendColor}" stroke-width="2" stroke-dasharray="6 5" stroke-linejoin="round" stroke-linecap="round" opacity="0.95" />`
          : ""
      }
      <path d="${path}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
      <circle cx="${lastX.toFixed(1)}" cy="${lastY.toFixed(1)}" r="3" fill="${color}" />
      ${hits}
      <text x="${padding}" y="12" class="chart-label">${formatNum(max)}${unit}</text>
      <text x="${padding}" y="${height - 6}" class="chart-label">${formatNum(min)}${unit}</text>
      <text x="${width - padding}" y="12" text-anchor="end" class="chart-label">${escapeHtml(
        opts.rangeLabel ||
          (series.length >= 2
            ? `${series[0].label} → ${series[series.length - 1].label}`
            : ""),
      )}</text>
    </svg>`;
}

/**
 * @param {{label: string, value: number}[]} points
 * @param {{width?: number, height?: number, target?: number|null, interactive?: boolean}} [opts]
 */
export function barChartSvg(points, opts = {}) {
  const width = opts.width ?? 320;
  const height = opts.height ?? 120;
  const padding = 24;
  const target = opts.target;
  const interactive = opts.interactive !== false;
  // No downsample — match Android (all logged days; bars get thinner).
  const series = points;

  if (series.length === 0) {
    return `<svg viewBox="0 0 ${width} ${height}" class="chart-svg"><text x="${width / 2}" y="${height / 2}" text-anchor="middle" class="chart-empty">No data yet</text></svg>`;
  }

  const values = series.map((p) => p.value);
  if (target != null) values.push(target);
  const max = Math.max(...values, 1);
  const barW = Math.max(2, (width - padding * 2) / series.length - 2);
  const targetY = target != null ? height - padding - (target / max) * (height - padding * 2) : null;

  const bars = series
    .map((p, i) => {
      const h = (p.value / max) * (height - padding * 2);
      const x = padding + i * ((width - padding * 2) / series.length);
      const y = height - padding - h;
      const over = target != null && p.value > target;
      const color = over ? "var(--over)" : "var(--teal)";
      const tip = `${p.label}: ${Math.round(p.value)} kcal`;
      return `<rect class="chart-hit" data-tip="${escapeAttr(tip)}" x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barW.toFixed(1)}" height="${Math.max(0, h).toFixed(1)}" fill="${color}" rx="2" opacity="${p.value ? 0.9 : 0.25}">${interactive ? `<title>${escapeHtml(tip)}</title>` : ""}</rect>`;
    })
    .join("");

  return `
    <svg viewBox="0 0 ${width} ${height}" class="chart-svg chart-svg--interactive" preserveAspectRatio="none">
      ${
        targetY != null
          ? `<line x1="${padding}" y1="${targetY.toFixed(1)}" x2="${width - padding}" y2="${targetY.toFixed(1)}" stroke="var(--muted)" stroke-width="1" stroke-dasharray="3 3" />`
          : ""
      }
      ${bars}
      <text x="${padding}" y="12" class="chart-label">${formatNum(max)}</text>
    </svg>`;
}

function formatNum(n) {
  return Math.abs(n - Math.round(n)) < 0.05 ? String(Math.round(n)) : n.toFixed(1);
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}

function escapeAttr(s) {
  return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}
