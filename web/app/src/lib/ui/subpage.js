// @ts-check

/**
 * Shared subpage chrome: back chevron + sticky title.
 * @param {string} title
 * @param {{ backHref?: string }} [opts]
 */
export function subpageBar(title, opts = {}) {
  const backAttr = opts.backHref ? `data-back-href="${escapeAttr(opts.backHref)}"` : "data-back";
  return `
    <header class="subpage-bar">
      <button type="button" class="subpage-bar__back" ${backAttr} aria-label="Back">
        <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
          <path fill="currentColor" d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>
        </svg>
      </button>
      <h1 class="subpage-bar__title">${escapeHtml(title)}</h1>
    </header>`;
}

/**
 * Wire back buttons inside a host element.
 * @param {ParentNode} root
 * @param {string} [fallbackHash]
 */
export function bindSubpageBack(root, fallbackHash = "#/home") {
  root.querySelectorAll("[data-back], [data-back-href]").forEach((el) => {
    el.addEventListener("click", () => {
      const href = el.getAttribute("data-back-href");
      if (href) location.hash = href;
      else if (history.length > 1) history.back();
      else location.hash = fallbackHash;
    });
  });
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]
  );
}

function escapeAttr(s) {
  return String(s).replace(/"/g, "&quot;");
}
