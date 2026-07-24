// @ts-check
// Minimal hand-rolled SVG charts — no canvas library.

/**
 * Downsample chronological points to at most maxPoints (keep ends).
 * @template {{label: string, value: number}} T
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
 * @param {{label: string, value: number}[]} points chronological, ascending
 * @param {{width?: number, height?: number, color?: string, unit?: string, goal?: number|null, interactive?: boolean}} [opts]
 * @returns {string} inline SVG markup
 */
export function lineChartSvg(points, opts = {}) {
  const width = opts.width ?? 320;
  const height = opts.height ?? 120;
  const padding = 24;
  const color = opts.color ?? "var(--teal)";
  const unit = opts.unit ?? "";
  const goal = opts.goal;
  const interactive = opts.interactive !== false;
  const series = downsamplePoints(points);

  if (series.length === 0) {
    return `<svg viewBox="0 0 ${width} ${height}" class="chart-svg"><text x="${width / 2}" y="${height / 2}" text-anchor="middle" class="chart-empty">No data yet</text></svg>`;
  }

  const values = series.map((p) => p.value);
  if (goal != null) values.push(goal);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const stepX = series.length > 1 ? (width - padding * 2) / (series.length - 1) : 0;

  const yFor = (v) => height - padding - ((v - min) / range) * (height - padding * 2);

  const coords = series.map((p, i) => {
    const x = padding + i * stepX;
    const y = yFor(p.value);
    return [x, y];
  });

  const path = coords.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
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
      ${
        goalY != null
          ? `<line x1="${padding}" y1="${goalY.toFixed(1)}" x2="${width - padding}" y2="${goalY.toFixed(1)}" stroke="var(--teal)" stroke-width="1.5" stroke-dasharray="4 4" opacity="0.7" />`
          : ""
      }
      <path d="${path}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
      <circle cx="${lastX.toFixed(1)}" cy="${lastY.toFixed(1)}" r="3" fill="${color}" />
      ${hits}
      <text x="${padding}" y="12" class="chart-label">${formatNum(max)}${unit}</text>
      <text x="${padding}" y="${height - 6}" class="chart-label">${formatNum(min)}${unit}</text>
      <text x="${width - padding}" y="12" text-anchor="end" class="chart-label">${escapeHtml(series[0].label)} → ${escapeHtml(series[series.length - 1].label)}</text>
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
