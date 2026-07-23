// @ts-check
// Minimal hand-rolled SVG line chart — no canvas library, no chart.js. Good
// enough for eyeballing a 30-day trend, which is all Progress needs.

/**
 * @param {{label: string, value: number}[]} points chronological, ascending
 * @param {{width?: number, height?: number, color?: string, unit?: string}} [opts]
 * @returns {string} inline SVG markup
 */
export function lineChartSvg(points, opts = {}) {
  const width = opts.width ?? 320;
  const height = opts.height ?? 120;
  const padding = 24;
  const color = opts.color ?? "var(--teal)";
  const unit = opts.unit ?? "";

  if (points.length === 0) {
    return `<svg viewBox="0 0 ${width} ${height}" class="chart-svg"><text x="${width / 2}" y="${height / 2}" text-anchor="middle" class="chart-empty">No data yet</text></svg>`;
  }

  const values = points.map((p) => p.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const stepX = points.length > 1 ? (width - padding * 2) / (points.length - 1) : 0;

  const coords = points.map((p, i) => {
    const x = padding + i * stepX;
    const y = height - padding - ((p.value - min) / range) * (height - padding * 2);
    return [x, y];
  });

  const path = coords.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  const [lastX, lastY] = coords[coords.length - 1];

  return `
    <svg viewBox="0 0 ${width} ${height}" class="chart-svg" preserveAspectRatio="none">
      <path d="${path}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
      <circle cx="${lastX.toFixed(1)}" cy="${lastY.toFixed(1)}" r="3" fill="${color}" />
      <text x="${padding}" y="12" class="chart-label">${formatNum(max)}${unit}</text>
      <text x="${padding}" y="${height - 6}" class="chart-label">${formatNum(min)}${unit}</text>
      <text x="${width - padding}" y="12" text-anchor="end" class="chart-label">${escapeHtml(points[0].label)} → ${escapeHtml(points[points.length - 1].label)}</text>
    </svg>`;
}

function formatNum(n) {
  return Math.abs(n - Math.round(n)) < 0.05 ? String(Math.round(n)) : n.toFixed(1);
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}
