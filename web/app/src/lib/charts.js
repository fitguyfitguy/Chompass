// @ts-check
// Minimal hand-rolled SVG charts — no canvas library.

/**
 * @param {{label: string, value: number}[]} points chronological, ascending
 * @param {{width?: number, height?: number, color?: string, unit?: string, goal?: number|null}} [opts]
 * @returns {string} inline SVG markup
 */
export function lineChartSvg(points, opts = {}) {
  const width = opts.width ?? 320;
  const height = opts.height ?? 120;
  const padding = 24;
  const color = opts.color ?? "var(--teal)";
  const unit = opts.unit ?? "";
  const goal = opts.goal;

  if (points.length === 0) {
    return `<svg viewBox="0 0 ${width} ${height}" class="chart-svg"><text x="${width / 2}" y="${height / 2}" text-anchor="middle" class="chart-empty">No data yet</text></svg>`;
  }

  const values = points.map((p) => p.value);
  if (goal != null) values.push(goal);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const stepX = points.length > 1 ? (width - padding * 2) / (points.length - 1) : 0;

  const yFor = (v) => height - padding - ((v - min) / range) * (height - padding * 2);

  const coords = points.map((p, i) => {
    const x = padding + i * stepX;
    const y = yFor(p.value);
    return [x, y];
  });

  const path = coords.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  const [lastX, lastY] = coords[coords.length - 1];
  const goalY = goal != null ? yFor(goal) : null;

  return `
    <svg viewBox="0 0 ${width} ${height}" class="chart-svg" preserveAspectRatio="none">
      ${
        goalY != null
          ? `<line x1="${padding}" y1="${goalY.toFixed(1)}" x2="${width - padding}" y2="${goalY.toFixed(1)}" stroke="var(--teal)" stroke-width="1.5" stroke-dasharray="4 4" opacity="0.7" />`
          : ""
      }
      <path d="${path}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
      <circle cx="${lastX.toFixed(1)}" cy="${lastY.toFixed(1)}" r="3" fill="${color}" />
      <text x="${padding}" y="12" class="chart-label">${formatNum(max)}${unit}</text>
      <text x="${padding}" y="${height - 6}" class="chart-label">${formatNum(min)}${unit}</text>
      <text x="${width - padding}" y="12" text-anchor="end" class="chart-label">${escapeHtml(points[0].label)} → ${escapeHtml(points[points.length - 1].label)}</text>
    </svg>`;
}

/**
 * @param {{label: string, value: number}[]} points
 * @param {{width?: number, height?: number, target?: number|null}} [opts]
 */
export function barChartSvg(points, opts = {}) {
  const width = opts.width ?? 320;
  const height = opts.height ?? 120;
  const padding = 24;
  const target = opts.target;

  if (points.length === 0) {
    return `<svg viewBox="0 0 ${width} ${height}" class="chart-svg"><text x="${width / 2}" y="${height / 2}" text-anchor="middle" class="chart-empty">No data yet</text></svg>`;
  }

  const values = points.map((p) => p.value);
  if (target != null) values.push(target);
  const max = Math.max(...values, 1);
  const barW = Math.max(2, (width - padding * 2) / points.length - 2);
  const targetY = target != null ? height - padding - (target / max) * (height - padding * 2) : null;

  const bars = points
    .map((p, i) => {
      const h = (p.value / max) * (height - padding * 2);
      const x = padding + i * ((width - padding * 2) / points.length);
      const y = height - padding - h;
      const over = target != null && p.value > target;
      const color = over ? "var(--over)" : "var(--teal)";
      return `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barW.toFixed(1)}" height="${Math.max(0, h).toFixed(1)}" fill="${color}" rx="2" opacity="${p.value ? 0.9 : 0.25}" />`;
    })
    .join("");

  return `
    <svg viewBox="0 0 ${width} ${height}" class="chart-svg" preserveAspectRatio="none">
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
