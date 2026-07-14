#!/usr/bin/env python3
"""Summarize FudAIPerf entry-addition timing logs.

Parses `key=value` lines emitted by PerfLog / PerfEventListener (tag FudAIPerf)
and prints per-(op, phase) latency stats plus a network-phase breakdown.

Usage:
    python3 scripts/summarize_entry_perf.py android/build/perf-entry/<stamp>/entry_perf.log
    adb logcat -s FudAIPerf:V | python3 scripts/summarize_entry_perf.py   # stdin

Stdlib only (no deps), so it runs under plain python3 or `uv run python`.
"""
import re
import sys
from collections import defaultdict

KV = re.compile(r"(\w+)=(-?\d+\.?\d*|[^\s]+)")


def parse(line):
    """Return a dict of key=value tokens on a FudAIPerf line, or None."""
    if "op=" not in line or "phase=" not in line:
        return None
    return {k: v for k, v in KV.findall(line)}


def num(d, key):
    try:
        return float(d[key])
    except (KeyError, ValueError):
        return None


def pct(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    i = min(len(sorted_vals) - 1, int(round((p / 100.0) * (len(sorted_vals) - 1))))
    return sorted_vals[i]


def stats(vals):
    s = sorted(vals)
    n = len(s)
    return dict(
        n=n,
        min=s[0],
        p50=pct(s, 50),
        p90=pct(s, 90),
        max=s[-1],
        mean=sum(s) / n,
    )


def main():
    src = open(sys.argv[1]) if len(sys.argv) > 1 else sys.stdin

    phase_ms = defaultdict(list)          # (op, phase) -> [ms]
    net = defaultdict(list)               # metric -> [value]  (net calls only)
    net_status = defaultdict(int)         # status code -> count
    net_calls = 0

    for line in src:
        d = parse(line)
        if not d:
            continue
        op, phase = d.get("op"), d.get("phase")
        if op == "net" and phase == "call":
            net_calls += 1
            net_status[d.get("status", "?")] += 1
            for m in ("dnsMs", "connectMs", "tlsMs", "ttfbMs", "totalMs", "reqBytes", "respBytes"):
                v = num(d, m)
                if v is not None and v >= 0:
                    net[m].append(v)
            continue
        ms = num(d, "ms")
        if ms is not None and op and phase:
            phase_ms[(op, phase)].append(ms)

    if not phase_ms and not net_calls:
        print("No FudAIPerf records found. Is this a DEBUG build, and did an entry-add run?")
        return 1

    if phase_ms:
        print("== Per-phase timing (ms) ==")
        print(f"{'op':<16}{'phase':<14}{'n':>4}{'min':>8}{'p50':>8}{'p90':>8}{'max':>9}{'mean':>9}")
        for (op, phase) in sorted(phase_ms):
            st = stats(phase_ms[(op, phase)])
            print(f"{op:<16}{phase:<14}{st['n']:>4}{st['min']:>8.0f}{st['p50']:>8.0f}"
                  f"{st['p90']:>8.0f}{st['max']:>9.0f}{st['mean']:>9.1f}")
        print()

    if net_calls:
        print(f"== Network (op=net), {net_calls} call(s) ==")
        print(f"{'metric':<12}{'n':>4}{'min':>8}{'p50':>8}{'p90':>8}{'max':>9}{'mean':>9}")
        for m in ("dnsMs", "connectMs", "tlsMs", "ttfbMs", "totalMs", "reqBytes", "respBytes"):
            if net[m]:
                st = stats(net[m])
                print(f"{m:<12}{st['n']:>4}{st['min']:>8.0f}{st['p50']:>8.0f}"
                      f"{st['p90']:>8.0f}{st['max']:>9.0f}{st['mean']:>9.1f}")
        statuses = ", ".join(f"{k}:{v}" for k, v in sorted(net_status.items()))
        print(f"status codes: {statuses}")
        print()

        # A single photo analysis fires two network calls (analysis + serving-unit
        # inference); flag when calls outnumber analyze parses so it's not mistaken
        # for one round-trip per entry.
        analyze_parses = sum(
            len(v) for (op, ph), v in phase_ms.items() if ph == "parse" and op != "inferServing"
        )
        if analyze_parses and net_calls > analyze_parses:
            print(f"note: {net_calls} network calls for {analyze_parses} analyze parse(s) — "
                  f"photo analyses issue a 2nd call for serving-unit inference.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
