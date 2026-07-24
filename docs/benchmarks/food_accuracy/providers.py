#!/usr/bin/env python3
"""OpenAI-compatible, OpenRouter, NoFUD free-router, and stub providers."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import random
import time
import urllib.error
import urllib.request
from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path

from env_local import openrouter_api_key
from openrouter_models import (
    NOFUD_FREE_ROUTER_ID,
    eligible_chat_free_models,
)


@dataclass
class ProviderResponse:
    text: str
    latency_ms: float
    model: str
    usage: dict | None = None
    routed_model: str | None = None


def normalize_usage(usage: dict | None) -> dict[str, int | float | None]:
    """Flatten OpenAI/OpenRouter ``usage`` into stable per-sample fields.

    OpenRouter always returns token counts (native tokenizer). Cached / reasoning
    / cost fields are present when the upstream provider reports them::

        usage.prompt_tokens
        usage.completion_tokens
        usage.total_tokens
        usage.prompt_tokens_details.cached_tokens
        usage.prompt_tokens_details.cache_write_tokens
        usage.completion_tokens_details.reasoning_tokens
        usage.cost
    """
    empty = {
        "prompt_tokens": None,
        "completion_tokens": None,
        "total_tokens": None,
        "cached_tokens": None,
        "cache_write_tokens": None,
        "reasoning_tokens": None,
        "cost": None,
    }
    if not isinstance(usage, dict):
        return empty

    prompt_details = usage.get("prompt_tokens_details")
    if not isinstance(prompt_details, dict):
        prompt_details = {}
    completion_details = usage.get("completion_tokens_details")
    if not isinstance(completion_details, dict):
        completion_details = {}

    def _num(value: object) -> int | float | None:
        if isinstance(value, bool) or value is None:
            return None
        if isinstance(value, (int, float)):
            return value
        return None

    return {
        "prompt_tokens": _num(usage.get("prompt_tokens")),
        "completion_tokens": _num(usage.get("completion_tokens")),
        "total_tokens": _num(usage.get("total_tokens")),
        "cached_tokens": _num(prompt_details.get("cached_tokens")),
        "cache_write_tokens": _num(prompt_details.get("cache_write_tokens")),
        "reasoning_tokens": _num(completion_details.get("reasoning_tokens")),
        "cost": _num(usage.get("cost")),
    }


def aggregate_usage(records: list[dict]) -> dict[str, int | float | None]:
    """Sum / mean token + cost fields across sample records that reported usage."""
    keys = (
        "prompt_tokens",
        "completion_tokens",
        "total_tokens",
        "cached_tokens",
        "cache_write_tokens",
        "reasoning_tokens",
        "cost",
    )
    buckets: dict[str, list[float]] = {k: [] for k in keys}
    for record in records:
        for key in keys:
            value = record.get(key)
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                buckets[key].append(float(value))

    out: dict[str, int | float | None] = {}
    for key, values in buckets.items():
        if not values:
            out[f"sum_{key}"] = None
            out[f"mean_{key}"] = None
            continue
        total = sum(values)
        # Keep token sums as ints when every value was integral-looking.
        if key != "cost" and all(v.is_integer() for v in values):
            out[f"sum_{key}"] = int(total)
        else:
            out[f"sum_{key}"] = total
        out[f"mean_{key}"] = total / len(values)

    prompt_sum = out.get("sum_prompt_tokens")
    cached_sum = out.get("sum_cached_tokens")
    if isinstance(prompt_sum, (int, float)) and prompt_sum > 0 and isinstance(cached_sum, (int, float)):
        out["cache_hit_rate"] = float(cached_sum) / float(prompt_sum)
    else:
        out["cache_hit_rate"] = None
    out["usage_n"] = max((len(buckets[k]) for k in keys), default=0)
    return out


class Provider(ABC):
    @abstractmethod
    def complete(self, *, prompt: str, image_path: Path | None = None) -> ProviderResponse:
        raise NotImplementedError


class StubProvider(Provider):
    """Deterministic fake macros for pipeline testing without API keys."""

    def complete(self, *, prompt: str, image_path: Path | None = None) -> ProviderResponse:
        seed = hashlib.sha256((prompt + str(image_path)).encode()).hexdigest()
        calories = 200 + (int(seed[:4], 16) % 600)
        protein = 10 + (int(seed[4:8], 16) % 40)
        carbs = 15 + (int(seed[8:12], 16) % 60)
        fat = 5 + (int(seed[12:16], 16) % 30)
        payload = {
            "name": "stub food",
            "calories": calories,
            "protein": float(protein),
            "carbs": float(carbs),
            "fat": float(fat),
            "serving_size_grams": 250.0,
            "unit_options": [],
        }
        if "clarify_request" in prompt:
            # Two-stage clarify eval: cover both ask and no-ask branches offline.
            payload["clarify_request"] = ["portion", "added_fat", "none"][int(seed[16:18], 16) % 3]
        return ProviderResponse(
            text=json.dumps(payload),
            latency_ms=1.0,
            model="stub",
        )


class OpenAICompatibleProvider(Provider):
    def __init__(
        self,
        *,
        model: str,
        api_key: str | None = None,
        base_url: str | None = None,
        timeout_s: float = 120.0,
        extra_headers: dict[str, str] | None = None,
        extra_body: dict | None = None,
    ) -> None:
        self.model = model
        self.api_key = api_key or os.environ.get("OPENAI_API_KEY", "")
        self.base_url = (base_url or os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1")).rstrip("/")
        self.timeout_s = timeout_s
        self.extra_headers = extra_headers or {}
        self.extra_body = extra_body or {}

    def complete(self, *, prompt: str, image_path: Path | None = None) -> ProviderResponse:
        if not self.api_key:
            raise RuntimeError("API key is required for this provider")

        content: list[dict] = [{"type": "text", "text": prompt}]
        if image_path is not None:
            if not image_path.exists():
                raise FileNotFoundError(f"Image not found: {image_path}")
            encoded = base64.b64encode(image_path.read_bytes()).decode("ascii")
            mime = "image/jpeg"
            if image_path.suffix.lower() == ".png":
                mime = "image/png"
            elif image_path.suffix.lower() == ".webp":
                mime = "image/webp"
            content.append(
                {
                    "type": "image_url",
                    "image_url": {"url": f"data:{mime};base64,{encoded}"},
                }
            )

        body = {
            "model": self.model,
            "messages": [{"role": "user", "content": content}],
            "temperature": 0.2,
            **self.extra_body,
        }
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
            **self.extra_headers,
        }
        req = urllib.request.Request(
            f"{self.base_url}/chat/completions",
            data=json.dumps(body).encode("utf-8"),
            headers=headers,
            method="POST",
        )

        started = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=self.timeout_s) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"API HTTP {exc.code}: {detail}") from exc

        latency_ms = (time.perf_counter() - started) * 1000.0
        try:
            text = payload["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise RuntimeError(f"Unexpected API response: {payload}") from exc

        usage = payload.get("usage")
        routed = payload.get("model")
        return ProviderResponse(
            text=text or "",
            latency_ms=latency_ms,
            model=self.model,
            usage=usage,
            routed_model=routed if routed and routed != self.model else routed,
        )

    def complete_with_tools(
        self,
        *,
        system: str,
        user: str,
        tools: list[dict],
        execute_tool,
        max_rounds: int = 4,
        image_path: Path | None = None,
    ) -> tuple[ProviderResponse, dict | None, dict]:
        """OpenAI-compatible tool loop. ``execute_tool(name, args) -> str``.

        Returns (aggregate_response, finalize_args_or_None, stats).
        """
        if not self.api_key:
            raise RuntimeError("API key is required for this provider")

        content: list[dict] = [{"type": "text", "text": user}]
        if image_path is not None:
            if not image_path.exists():
                raise FileNotFoundError(f"Image not found: {image_path}")
            encoded = base64.b64encode(image_path.read_bytes()).decode("ascii")
            mime = "image/jpeg"
            if image_path.suffix.lower() == ".png":
                mime = "image/png"
            elif image_path.suffix.lower() == ".webp":
                mime = "image/webp"
            content.append(
                {
                    "type": "image_url",
                    "image_url": {"url": f"data:{mime};base64,{encoded}"},
                }
            )

        messages: list[dict] = [
            {"role": "system", "content": system},
            {"role": "user", "content": content},
        ]
        tool_defs = [
            {
                "type": "function",
                "function": {
                    "name": t["name"],
                    "description": t.get("description", ""),
                    "parameters": t.get("parameters", {"type": "object", "properties": {}}),
                },
            }
            for t in tools
        ]

        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
            **self.extra_headers,
        }
        started = time.perf_counter()
        usages: list[dict] = []
        rounds = 0
        search_count = 0
        finalize_args: dict | None = None
        last_text = ""
        routed_model = None

        for round_i in range(max_rounds):
            rounds = round_i + 1
            # Force finalize on the last round so the model cannot leave without a pick.
            if round_i == max_rounds - 1 and finalize_args is None:
                tool_choice: str | dict = {
                    "type": "function",
                    "function": {"name": "finalize_grounding"},
                }
            else:
                tool_choice = "auto"
            body = {
                "model": self.model,
                "messages": messages,
                "tools": tool_defs,
                "tool_choice": tool_choice,
                "temperature": 0.2,
                **self.extra_body,
            }
            req = urllib.request.Request(
                f"{self.base_url}/chat/completions",
                data=json.dumps(body).encode("utf-8"),
                headers=headers,
                method="POST",
            )
            try:
                with urllib.request.urlopen(req, timeout=self.timeout_s) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
            except urllib.error.HTTPError as exc:
                detail = exc.read().decode("utf-8", errors="replace")
                raise RuntimeError(f"API HTTP {exc.code}: {detail}") from exc

            if payload.get("usage"):
                usages.append(payload["usage"])
            routed_model = payload.get("model") or routed_model
            try:
                message = payload["choices"][0]["message"]
            except (KeyError, IndexError, TypeError) as exc:
                raise RuntimeError(f"Unexpected API response: {payload}") from exc

            tool_calls = message.get("tool_calls") or []
            if tool_calls:
                messages.append(message)
                for call in tool_calls:
                    fn = call.get("function") or {}
                    name = fn.get("name") or ""
                    raw_args = fn.get("arguments") or "{}"
                    try:
                        args = json.loads(raw_args) if isinstance(raw_args, str) else (raw_args or {})
                    except json.JSONDecodeError:
                        args = {}
                    if name == "search_usda":
                        search_count += 1
                    result = execute_tool(name, args if isinstance(args, dict) else {})
                    if name == "finalize_grounding" and isinstance(args, dict):
                        finalize_args = args
                    messages.append(
                        {
                            "role": "tool",
                            "tool_call_id": call.get("id") or name,
                            "content": result if isinstance(result, str) else json.dumps(result),
                        }
                    )
                if finalize_args is not None:
                    break
                continue

            last_text = message.get("content") or ""
            if finalize_args is None and round_i < max_rounds - 1:
                messages.append(message)
                messages.append(
                    {
                        "role": "user",
                        "content": "Call finalize_grounding now with your best source picks.",
                    }
                )
                continue
            break

        latency_ms = (time.perf_counter() - started) * 1000.0
        merged_usage = None
        if usages:
            merged_usage = {}
            for u in usages:
                for k, v in u.items():
                    if isinstance(v, (int, float)):
                        merged_usage[k] = merged_usage.get(k, 0) + v
        resp = ProviderResponse(
            text=last_text or json.dumps(finalize_args or {}),
            latency_ms=latency_ms,
            model=self.model,
            usage=merged_usage,
            routed_model=routed_model if routed_model and routed_model != self.model else routed_model,
        )
        stats = {"rounds": rounds, "search_usda_count": search_count}
        return resp, finalize_args, stats


class OpenRouterProvider(OpenAICompatibleProvider):
    """OpenRouter chat/completions with NoFUD-aligned headers and reasoning disabled."""

    def __init__(
        self,
        *,
        model: str,
        api_key: str | None = None,
        timeout_s: float = 180.0,
    ) -> None:
        key = api_key or openrouter_api_key()
        super().__init__(
            model=model,
            api_key=key,
            base_url="https://openrouter.ai/api/v1",
            timeout_s=timeout_s,
            extra_headers={
                "HTTP-Referer": "https://codeberg.org/fitguy/NoFUD",
                "X-Title": "NoFUD Food Accuracy Benchmark",
            },
            extra_body={"reasoning": {"exclude": True}},
        )


class NofudFreeRouterProvider(Provider):
    """Pick randomly among live OpenRouter :free chat models, excluding content-safety.

    - Text prompts → any eligible free chat model
    - Image prompts → vision-capable free models only
    - On 429/502, try another model from the pool (up to ``failover`` attempts)
    """

    def __init__(
        self,
        *,
        api_key: str | None = None,
        timeout_s: float = 180.0,
        failover: int = 3,
        seed: int | None = None,
    ) -> None:
        self.api_key = api_key or openrouter_api_key()
        if not self.api_key:
            raise RuntimeError("OPENROUTER_TOKEN is required for nofud/free")
        self.timeout_s = timeout_s
        self.failover = max(1, failover)
        self._rng = random.Random(seed)
        self.model = NOFUD_FREE_ROUTER_ID
        self._text_pool: list[str] | None = None
        self._vision_pool: list[str] | None = None

    def refresh_pools(self) -> None:
        text = eligible_chat_free_models(api_key=self.api_key, require_vision=False)
        vision = eligible_chat_free_models(api_key=self.api_key, require_vision=True)
        self._text_pool = [m.id for m in text]
        self._vision_pool = [m.id for m in vision]
        if not self._text_pool:
            raise RuntimeError("No eligible free chat models found on OpenRouter")
        if not self._vision_pool:
            # Fall back to text-only pool listing for diagnostics; image calls will fail clearly.
            self._vision_pool = []

    def _pool_for(self, *, need_vision: bool) -> list[str]:
        if self._text_pool is None or self._vision_pool is None:
            self.refresh_pools()
        assert self._text_pool is not None and self._vision_pool is not None
        if need_vision:
            if not self._vision_pool:
                raise RuntimeError("No vision-capable free models available for nofud/free")
            return list(self._vision_pool)
        return list(self._text_pool)

    def complete(self, *, prompt: str, image_path: Path | None = None) -> ProviderResponse:
        need_vision = image_path is not None
        pool = self._pool_for(need_vision=need_vision)
        self._rng.shuffle(pool)
        candidates = pool[: self.failover]
        if not candidates:
            raise RuntimeError("Empty free-model pool")

        errors: list[str] = []
        for model_id in candidates:
            backend = OpenRouterProvider(
                model=model_id,
                api_key=self.api_key,
                timeout_s=self.timeout_s,
            )
            try:
                response = backend.complete(prompt=prompt, image_path=image_path)
            except Exception as exc:  # noqa: BLE001
                msg = str(exc)
                errors.append(f"{model_id}: {msg}")
                retryable = "429" in msg or "rate-limited" in msg or "502" in msg or "ResourceExhausted" in msg
                if retryable:
                    continue
                # Non-retryable: still try another free model once or twice.
                continue

            return ProviderResponse(
                text=response.text,
                latency_ms=response.latency_ms,
                model=NOFUD_FREE_ROUTER_ID,
                usage=response.usage,
                routed_model=response.routed_model or model_id,
            )

        raise RuntimeError(
            "nofud/free exhausted failover pool: " + " | ".join(errors[:5])
        )


def build_provider(name: str, *, model: str) -> Provider:
    if name == "stub":
        return StubProvider()
    if name == "openrouter":
        if model in {NOFUD_FREE_ROUTER_ID, "nofud/free-router", "free"}:
            return NofudFreeRouterProvider()
        return OpenRouterProvider(model=model)
    if name in {"openai", "ollama"}:
        return OpenAICompatibleProvider(model=model)
    raise ValueError(f"Unknown provider {name!r}. Use stub, openai, ollama, or openrouter.")
