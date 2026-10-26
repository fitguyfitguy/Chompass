#!/usr/bin/env python3
"""Generate the bundled provider directory for Custom (OpenAI-compatible) endpoints.

Codeberg #143. OpenAI-compatible APIs have no capability-negotiation endpoint,
so "accepts images" ships as reviewed metadata, not runtime discovery. This
script distills https://models.dev/api.json into
android/app/src/main/assets/provider_directory.json; the Custom base-URL sheet
offers the entries and surfaces each entry's vision-flagged lineup in the model
and vision-model pickers. No runtime registry fetch: the trust root is the
signed APK.

Usage (from the repo root, uv only — never bare python/pip):
    uv run --with jsonschema python scripts/generate_provider_directory.py
        Fetch models.dev, filter, write the asset, validate it against
        contracts/provider-directory-1.0.schema.json.
    uv run --with jsonschema python scripts/generate_provider_directory.py --check
        Validate the committed asset against the schema only (no network).

Filters, in order:
  1. Provider has a non-empty `api` starting with https:// (cleartext hosts are
     unreachable by default — the AI layer rejects http:// unless the user
     opts in).
  2. Provider has a non-empty `doc` (the sheet links it as "Documentation").
  3. The `api` host is not a curated card host (the AIProvider.baseUrl values
     are already first-class provider cards; OLLAMA's localhost is excluded by
     the https filter, with an explicit localhost/127.0.0.1/IP-literal guard).
  4. Provider id not in EXCLUDE_PROVIDER_IDS below.
  5. visionModels = model ids where attachment is true or modalities.input
     contains "image", and status != "deprecated"; deduped case-insensitively;
     models.dev order kept; capped at 25 (long-tail models stay reachable via
     the runtime /models merge and the custom-model text field).
  6. Entries with an empty vision list are dropped; duplicate exact `api`
     values keep the lowest id only.
  7. Entries sorted by id.

Review workflow (automated shortlist, not automated trust): every release
sweep that runs this script reviews the diff. A host that turns out bad
(spam docs, dead endpoint, wrong vision claims) gets its models.dev id added
to EXCLUDE_PROVIDER_IDS and the script reruns.

If models.dev changes shape (fields renamed), validation fails loudly — fix
this script, never widen the asset silently.
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
SOURCE_URL = "https://models.dev/api.json"
SCHEMA_PATH = ROOT / "contracts/provider-directory-1.0.schema.json"
ASSET_PATH = ROOT / "android/app/src/main/assets/provider_directory.json"

DIRECTORY_VERSION = 1
MAX_VISION_MODELS = 25

# Curated AIProvider.baseUrl hosts (app/chompass/models/AIProvider.kt). Keep in
# sync when a card is added; the Custom sheet must not duplicate them.
CURATED_HOSTS = frozenset(
    {
        "generativelanguage.googleapis.com",
        "api.openai.com",
        "api.anthropic.com",
        "api.x.ai",
        "openrouter.ai",
        "api.together.xyz",
        "api.groq.com",
        "router.huggingface.co",
        "api.fireworks.ai",
        "api.deepinfra.com",
        "api.mistral.ai",
        "ollama.com",
    }
)

LOCAL_HOSTS = frozenset({"localhost", "127.0.0.1", "::1", "0.0.0.0"})

# models.dev provider ids found bad during release-diff review. Empty until a
# review needs it; see the module docstring.
# 2026-10-26 (first review): per-account URL templates — models.dev cannot
# express the account/product id these endpoints require, so a prefilled URL
# would be junk (and one breaks strict URI parsing).
EXCLUDE_PROVIDER_IDS: frozenset[str] = frozenset(
    {
        "cloudflare-workers-ai",
        "databricks",
        "infomaniak",
        "snowflake-cortex",
    }
)


def fetch_source() -> dict:
    request = urllib.request.Request(
        SOURCE_URL,
        headers={"User-Agent": "chompass-provider-directory-generator/1.0"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def host_of(url: str) -> str:
    return (urlsplit(url).hostname or "").lower()


def is_ip_literal(host: str) -> bool:
    if not host:
        return True
    stripped = host.strip("[]")
    parts = stripped.split(".")
    if len(parts) == 4 and all(p.isdigit() and 0 <= int(p) <= 255 for p in parts):
        return True
    return ":" in stripped


def vision_models(provider: dict) -> list[str]:
    """Vision-flagged model ids in models.dev order, deduped case-insensitively."""
    seen: set[str] = set()
    out: list[str] = []
    for model_id, model in (provider.get("models") or {}).items():
        if not isinstance(model, dict):
            continue
        if model.get("status") == "deprecated":
            continue
        modalities = model.get("modalities") or {}
        accepts_images = model.get("attachment") is True or "image" in (
            modalities.get("input") or []
        )
        if not accepts_images:
            continue
        key = model_id.lower()
        if key in seen:
            continue
        seen.add(key)
        out.append(model_id)
        if len(out) >= MAX_VISION_MODELS:
            break
    return out


def build_entries(registry: dict) -> list[dict]:
    candidates: dict[str, dict] = {}
    for provider_id, provider in (registry.get("providers") or registry).items():
        if not isinstance(provider, dict):
            continue
        if provider_id in EXCLUDE_PROVIDER_IDS:
            continue
        base_url = provider.get("api") or ""
        docs_url = provider.get("doc") or ""
        if not base_url.startswith("https://"):
            continue
        if not docs_url.startswith("https://"):
            continue
        host = host_of(base_url)
        if host in LOCAL_HOSTS or is_ip_literal(host):
            continue
        if host in CURATED_HOSTS:
            continue
        models = vision_models(provider)
        if not models:
            continue
        candidates[provider_id] = {
            "id": provider_id,
            "name": provider.get("name") or provider_id,
            "baseUrl": base_url,
            "docsUrl": docs_url,
            "visionModels": models,
        }
    # Duplicate exact api values keep the lowest id only.
    entries: list[dict] = []
    seen_urls: set[str] = set()
    for provider_id in sorted(candidates):
        entry = candidates[provider_id]
        if entry["baseUrl"] in seen_urls:
            continue
        seen_urls.add(entry["baseUrl"])
        entries.append(entry)
    entries.sort(key=lambda e: e["id"])
    return entries


def build_document(entries: list[dict]) -> dict:
    return {
        "version": DIRECTORY_VERSION,
        "source": SOURCE_URL,
        "fetchedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
        "entries": entries,
    }


def validate(document: dict, label: str) -> None:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    errors = sorted(
        Draft202012Validator(schema).iter_errors(document),
        key=lambda e: list(e.path),
    )
    if errors:
        for err in errors[:20]:
            path = "/".join(str(p) for p in err.path) or "(root)"
            print(f"FAIL {label}: {path}: {err.message}", file=sys.stderr)
        raise SystemExit(1)
    ids = [e["id"] for e in document["entries"]]
    urls = [e["baseUrl"].rstrip("/") for e in document["entries"]]
    if len(set(ids)) != len(ids):
        raise SystemExit(f"FAIL {label}: duplicate entry ids")
    if len(set(urls)) != len(urls):
        raise SystemExit(f"FAIL {label}: duplicate entry baseUrls")
    templated = [e["id"] for e in document["entries"] if any(c in e["baseUrl"] for c in "${}")]
    if templated:
        raise SystemExit(
            f"FAIL {label}: templated baseUrl, unusable for prefill — "
            f"add to EXCLUDE_PROVIDER_IDS: {templated}"
        )
    hosts = [host_of(e["baseUrl"]) for e in document["entries"]]
    overlap = sorted(set(hosts) & (CURATED_HOSTS | LOCAL_HOSTS))
    if overlap:
        raise SystemExit(f"FAIL {label}: curated/local host overlap: {overlap}")
    if any(is_ip_literal(h) for h in hosts):
        raise SystemExit(f"FAIL {label}: IP-literal baseUrl host")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate the committed asset only (no network)",
    )
    args = parser.parse_args()

    if args.check:
        document = json.loads(ASSET_PATH.read_text(encoding="utf-8"))
        validate(document, ASSET_PATH.name)
        print(f"OK   {ASSET_PATH.relative_to(ROOT)} ({len(document['entries'])} entries)")
        return 0

    document = build_document(build_entries(fetch_source()))
    validate(document, "candidate")
    payload = json.dumps(document, ensure_ascii=False, indent=1) + "\n"
    ASSET_PATH.parent.mkdir(parents=True, exist_ok=True)
    ASSET_PATH.write_text(payload, encoding="utf-8")
    print(
        f"Wrote {ASSET_PATH.relative_to(ROOT)}: {len(document['entries'])} entries, "
        f"{len(payload.encode('utf-8'))} bytes, fetchedAt {document['fetchedAt']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
