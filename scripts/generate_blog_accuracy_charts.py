#!/usr/bin/env python3
"""Generate dark-theme accuracy charts for the Chompass blog post.

Numbers are baked from docs/FOOD_ACCURACY_BENCHMARK_STATUS.md /
docs/ACCURACY.md (snapshot late July 2026). Results CSVs are gitignored;
do not read them here.

Regen:
  uv run --with matplotlib python scripts/generate_blog_accuracy_charts.py

Outputs land in website/static/img/blog/accuracy/.
"""

from __future__ import annotations

from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "website" / "static" / "img" / "blog" / "accuracy"

# Site palette (matches website/assets/css/main.css + generate_og_image.py)
BG = "#121116"
SURFACE = "#24232a"
TEAL = "#5cc48f"
TEAL_DEEP = "#006b5e"
TEAL_SOFT = "#4db6ac"
TEXT = "#e6e1e5"
MUTED = "#a9a4ad"
LINE = "#3a3840"
ACCENT_WARM = "#e8a87c"  # underestimate / warning accent (not purple)
ACCENT_COOL = "#7eb8e8"  # busy-tray accent

DPI = 160


def apply_theme(fig: plt.Figure, axes) -> None:
    fig.patch.set_facecolor(BG)
    ax_list = axes if hasattr(axes, "__iter__") else [axes]
    for ax in ax_list:
        ax.set_facecolor(BG)
        ax.tick_params(colors=MUTED, labelsize=10)
        for spine in ax.spines.values():
            spine.set_color(LINE)
        ax.title.set_color(TEXT)
        ax.xaxis.label.set_color(MUTED)
        ax.yaxis.label.set_color(MUTED)
        ax.xaxis.label.set_fontsize(11)
        ax.yaxis.label.set_fontsize(11)


def save(fig: plt.Figure, name: str) -> Path:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / name
    fig.savefig(
        path,
        dpi=DPI,
        facecolor=fig.get_facecolor(),
        edgecolor="none",
        bbox_inches="tight",
        pad_inches=0.35,
    )
    plt.close(fig)
    print(f"Wrote {path}")
    return path


def chart_text_vs_photo() -> Path:
    """Two-panel: WMAPE (lower better) and ±20% kcal (higher better)."""
    labels = ["Typed / text\n(FNDDS 42)", "Photo · best paid\n(JFB 50)", "Photo · free\n(JFB 50)"]
    wmape = [5.7, 32.3, 39.8]
    within20 = [90.0, 50.0, 32.0]
    colors = [TEAL, TEAL_SOFT, TEAL_DEEP]

    fig, (ax_l, ax_r) = plt.subplots(1, 2, figsize=(12.5, 5.2))
    apply_theme(fig, (ax_l, ax_r))

    y = range(len(labels))
    bars_l = ax_l.barh(y, wmape, color=colors, height=0.55, zorder=3)
    ax_l.set_yticks(list(y))
    ax_l.set_yticklabels(labels, color=TEXT, fontsize=11)
    ax_l.set_xlabel("WMAPE %  (lower is better)")
    ax_l.set_xlim(0, 50)
    ax_l.set_title("Macro error (WMAPE)", color=TEXT, fontsize=14, pad=12)
    ax_l.grid(axis="x", color=LINE, linewidth=0.8, zorder=0)
    ax_l.invert_yaxis()
    for bar, val in zip(bars_l, wmape):
        ax_l.text(
            val + 0.8,
            bar.get_y() + bar.get_height() / 2,
            f"{val}%",
            va="center",
            color=TEXT,
            fontsize=11,
            fontweight="bold",
        )

    bars_r = ax_r.barh(y, within20, color=colors, height=0.55, zorder=3)
    ax_r.set_yticks(list(y))
    ax_r.set_yticklabels(labels, color=TEXT, fontsize=11)
    ax_r.set_xlabel("Within ±20% of true calories  (higher is better)")
    ax_r.set_xlim(0, 100)
    ax_r.set_title("Calorie hit rate (±20%)", color=TEXT, fontsize=14, pad=12)
    ax_r.grid(axis="x", color=LINE, linewidth=0.8, zorder=0)
    ax_r.invert_yaxis()
    for bar, val in zip(bars_r, within20):
        ax_r.text(
            val + 1.5,
            bar.get_y() + bar.get_height() / 2,
            f"{val:.0f}%",
            va="center",
            color=TEXT,
            fontsize=11,
            fontweight="bold",
        )

    fig.suptitle(
        "Typed entry vs plate photos",
        color=TEXT,
        fontsize=16,
        fontweight="bold",
        y=1.02,
    )
    fig.text(
        0.5,
        -0.02,
        "Offline harness · food-analysis prompts equivalent to the app · late July 2026",
        ha="center",
        color=MUTED,
        fontsize=9,
    )
    return save(fig, "text-vs-photo.png")


def chart_plate_model_ladder() -> Path:
    """Horizontal WMAPE ladder for plate photos; typed reference line."""
    # Ordered best → worst (top = best after invert)
    rows = [
        ("Gemini 3.6 Flash", 32.3),
        ("GPT-4o mini", 34.5),
        ("Gemini 3.5 Flash-Lite", 35.9),
        ("Qwen3.5 Flash", 37.1),
        ("Claude 3 Haiku", 37.9),
        ("Gemma 26B free", 39.8),
        ("nofud/free (cold)", 41.1),
        ("GPT-5 Nano", 43.8),
    ]
    labels = [r[0] for r in rows]
    vals = [r[1] for r in rows]
    text_ref = 5.7

    fig, ax = plt.subplots(figsize=(11.5, 6.2))
    apply_theme(fig, ax)

    y = range(len(labels))
    # Gradient: better (lower WMAPE) = brighter teal
    n = len(vals)
    bar_colors = []
    for i in range(n):
        t = i / max(n - 1, 1)
        # interpolate TEAL → TEAL_DEEP-ish via RGB
        r = int(0x5C + t * (0x00 - 0x5C))
        g = int(0xC4 + t * (0x6B - 0xC4))
        b = int(0x8F + t * (0x5E - 0x8F))
        bar_colors.append(f"#{r:02x}{g:02x}{b:02x}")

    bars = ax.barh(y, vals, color=bar_colors, height=0.62, zorder=3)
    ax.set_yticks(list(y))
    ax.set_yticklabels(labels, color=TEXT, fontsize=11)
    ax.set_xlabel("Plate photo WMAPE %  (lower is better) · JFB 50 · compact")
    ax.set_xlim(0, 55)
    ax.invert_yaxis()
    ax.grid(axis="x", color=LINE, linewidth=0.8, zorder=0)

    ax.axvline(text_ref, color=TEAL, linestyle="--", linewidth=1.6, zorder=4)
    ax.text(
        text_ref + 0.6,
        -0.55,
        f"typed entry  {text_ref}%",
        color=TEAL,
        fontsize=10,
        fontweight="bold",
        va="bottom",
    )

    for bar, val in zip(bars, vals):
        ax.text(
            val + 0.7,
            bar.get_y() + bar.get_height() / 2,
            f"{val}%",
            va="center",
            color=TEXT,
            fontsize=10,
            fontweight="bold",
        )

    ax.set_title(
        "Plate photo accuracy by model",
        color=TEXT,
        fontsize=15,
        fontweight="bold",
        pad=14,
    )
    fig.text(
        0.5,
        0.01,
        "Per-model harness results — not a Chompass accuracy score. BYOK means your model choice matters.",
        ha="center",
        color=MUTED,
        fontsize=9,
    )
    return save(fig, "plate-model-ladder.png")


def chart_portion_clarify() -> Path:
    """Before/after portion-oracle clarification."""
    metrics = ["WMAPE %\n(lower better)", "Within ±20% kcal\n(higher better)"]
    before = [35.9, 40.0]
    after = [22.8, 50.0]
    deltas = ["−15 pp", "+12 pp"]

    fig, ax = plt.subplots(figsize=(10.5, 5.4))
    apply_theme(fig, ax)

    x = range(len(metrics))
    width = 0.34
    b1 = ax.bar(
        [i - width / 2 for i in x],
        before,
        width,
        label="Photo only (compact)",
        color=TEAL_DEEP,
        zorder=3,
    )
    b2 = ax.bar(
        [i + width / 2 for i in x],
        after,
        width,
        label="Photo + portion answer (simulated)",
        color=TEAL,
        zorder=3,
    )

    ax.set_xticks(list(x))
    ax.set_xticklabels(metrics, color=TEXT, fontsize=11)
    ax.set_ylabel("Percent")
    ax.set_ylim(0, 65)
    ax.grid(axis="y", color=LINE, linewidth=0.8, zorder=0)
    ax.legend(
        facecolor=SURFACE,
        edgecolor=LINE,
        labelcolor=TEXT,
        fontsize=10,
        loc="upper right",
    )

    for bar, val in zip(b1, before):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            val + 1.2,
            f"{val}%",
            ha="center",
            color=MUTED,
            fontsize=10,
        )
    for bar, val, delta in zip(b2, after, deltas):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            val + 1.2,
            f"{val}%",
            ha="center",
            color=TEXT,
            fontsize=11,
            fontweight="bold",
        )
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            val + 5.5,
            delta,
            ha="center",
            color=TEAL,
            fontsize=12,
            fontweight="bold",
        )

    ax.set_title(
        "Portion clarification ceiling (simulated)",
        color=TEXT,
        fontsize=15,
        fontweight="bold",
        pad=14,
    )
    fig.text(
        0.5,
        0.01,
        "JFB 50 · Gemini 3.5 Flash-Lite · oracle portion injected into the prompt (stand-in for a one-tap chip)",
        ha="center",
        color=MUTED,
        fontsize=9,
    )
    return save(fig, "portion-clarify.png")


def chart_failure_modes() -> Path:
    """Conceptual three-panel failure-mode diagram."""
    modes = [
        {
            "title": "Restaurant overestimate",
            "subtitle": "Dominant failure",
            "callout": "+100–200% kcal",
            "detail": "Invent diner-scale plates\nand sides not in the log",
            "color": TEAL,
        },
        {
            "title": "Hidden-calorie miss",
            "subtitle": "Oil · sauce · denseness",
            "callout": "−65–80% kcal",
            "detail": "Camera understates fat;\nslice vs whole dish",
            "color": ACCENT_WARM,
        },
        {
            "title": "Busy multi-item tray",
            "subtitle": "ID roughly right",
            "callout": "grams wrong",
            "detail": "Recognition OK;\nportion grounding fails",
            "color": ACCENT_COOL,
        },
    ]

    fig, axes = plt.subplots(1, 3, figsize=(12.8, 4.8))
    apply_theme(fig, axes)

    for ax, mode in zip(axes, modes):
        ax.set_xlim(0, 10)
        ax.set_ylim(0, 10)
        ax.axis("off")
        ax.set_facecolor(BG)

        box = FancyBboxPatch(
            (0.4, 0.6),
            9.2,
            8.6,
            boxstyle="round,pad=0.15,rounding_size=0.6",
            linewidth=1.5,
            edgecolor=mode["color"],
            facecolor=SURFACE,
            mutation_aspect=0.8,
        )
        ax.add_patch(box)

        # Accent bar at top of card
        accent = FancyBboxPatch(
            (0.4, 8.4),
            9.2,
            0.8,
            boxstyle="round,pad=0.02,rounding_size=0.3",
            linewidth=0,
            facecolor=mode["color"],
        )
        ax.add_patch(accent)

        ax.text(
            5,
            7.2,
            mode["title"],
            ha="center",
            va="center",
            color=TEXT,
            fontsize=12,
            fontweight="bold",
        )
        ax.text(
            5,
            6.2,
            mode["subtitle"],
            ha="center",
            va="center",
            color=MUTED,
            fontsize=9,
        )
        ax.text(
            5,
            4.4,
            mode["callout"],
            ha="center",
            va="center",
            color=mode["color"],
            fontsize=18,
            fontweight="bold",
        )
        ax.text(
            5,
            2.4,
            mode["detail"],
            ha="center",
            va="center",
            color=MUTED,
            fontsize=10,
            linespacing=1.45,
        )

    fig.suptitle(
        "How plate photos go wrong",
        color=TEXT,
        fontsize=15,
        fontweight="bold",
        y=1.02,
    )
    fig.text(
        0.5,
        -0.04,
        "Consensus patterns across five vision models on JFB 50 · hard ≠ big meals (similar mean GT kcal)",
        ha="center",
        color=MUTED,
        fontsize=9,
    )
    return save(fig, "failure-modes.png")


def main() -> None:
    chart_text_vs_photo()
    chart_plate_model_ladder()
    chart_portion_clarify()
    chart_failure_modes()
    print(f"All charts in {OUT_DIR}")


if __name__ == "__main__":
    main()
