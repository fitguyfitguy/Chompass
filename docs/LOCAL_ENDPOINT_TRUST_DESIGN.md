# Design: Local OpenAI-compatible endpoints (cleartext + user-CA trust) (issue #8)

Status: **Shipped in 3.9.0 (2026-08-11).** D1 (user-CA trust for custom
endpoints), D3 (scheme normalization) and D4 (actionable connection errors)
landed; D2 (LAN cleartext in release by default) deferred by decision; see the
D2 section for the rationale.
Related: [Codeberg #8](https://codeberg.org/fitguy/Chompass/issues/8)

## Problem statement

The Custom (OpenAI-compatible) provider, and any other user-entered endpoint, cannot talk to a self-hosted server (reporter: OmniRoute on a home server) in
either mode:

1. **HTTP**: `Cleartext is not allowed` (reporter's words).
2. **HTTPS with a self-signed cert**: `CertPathValidatorException: Trust anchor
   for certification path not found`, even though the user installed the root
   CA on the phone and other apps (browser, Kai 9000) connect fine.

Both failures are code-confirmed, and they are **two independent blockers** in
the same file:

| Blocker | Cause (release build) |
|---------|------------------------|
| Cleartext | `app/src/main/res/xml/network_security_config.xml`: base-config `cleartextTrafficPermitted="false"`; domain-config exceptions only for `localhost` / `127.0.0.1` / `10.0.2.2` (loopback). A LAN IP (`http://192.168.x.y:port`) is blocked by the platform. |
| Self-signed HTTPS | The same NSC declares **no `<trust-anchors>`**: since API 24 (minSdk 26, targetSdk 36) the Android default is **system CAs only**; user-installed CAs are ignored. No custom `X509TrustManager` exists anywhere: `FoodAnalysisService.defaultClient` is a plain `OkHttpClient`, and `AiHttp.clientForProvider` only adjusts timeouts. |

The app advertises custom base URLs (`AIProvider.CUSTOM_OPENAI`, Settings →
AI → Custom base URL, placeholder `https://your-endpoint.com/v1`), so this
blocks the whole "local AI" use case.

## Goals / non-goals

**Goals**
- Custom (OpenAI-compatible) endpoints over **HTTPS with user-installed CA
  certs** work in the release build: no downgrade of any other connection.
- Zero behavior change for cloud providers, OLLAMA (loopback), WebDAV, STT.
- Fix survives rotation/restarts; no new permissions; no network calls.

**Non-goals (v1)**
- **No app-wide trust change**: do **not** add `<trust-anchors><certificates
  src="user"/></trust-anchors>` to the NSC; that would let any user-installed
  CA MITM *cloud* AI traffic in a privacy-focused app. Trust relaxation is
  scoped to the user-configured endpoint only.
- **No LAN cleartext in release by default** (see Decision D2: platform
  constraint, opt-in follow-up only).
- No PWA changes, no formula/export changes (**no parity impact**: this is an
  Android-only networking path).

## Platform constraints (why the obvious fixes don't work)

1. **NSC is compiled into the APK.** `NetworkSecurityPolicy` has no setters;
   cleartext permission and trust anchors are **not runtime-configurable**.
   OkHttp enforces cleartext via `Platform.isCleartextTrafficPermitted(host)`
   with no per-client bypass. A runtime "allow cleartext for this host" toggle
   is therefore **impossible** without shipping a permissive base-config.
2. **HTTPS trust *is* runtime-configurable.** OkHttp accepts a custom
   `SSLSocketFactory` + `X509TrustManager` on the client. A trust manager built
   from `KeyStore.getInstance("AndroidCAStore")` (system **and** user-installed
   CAs; public API since API 14, no permission) makes the app honor
   user-added CAs (exactly what the reporter expects) **per client**, leaving
   every other connection on the platform default.

## Design

### D1 (core fix): user-CA trust for custom endpoints only

New file `android/app/src/main/java/app/chompass/services/ai/LocalEndpointTrust.kt`:

```kotlin
internal object LocalEndpointTrust {
    /** OkHttp client that also trusts user-installed CA certs (AndroidCAStore). */
    fun withUserCaTrust(base: OkHttpClient): OkHttpClient {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(store)
        val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
        return base.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, tm)
            .build()
    }
}
```

Wiring: single extension point already exists, `AiHttp.clientForProvider`
(used by `FoodAnalysisService.dispatch` **and** `ChatService`, i.e. food
analysis + coach):

```kotlin
fun clientForProvider(base: OkHttpClient, provider: AIProvider, localTimeoutSeconds: Int): OkHttpClient {
    var client = if (provider.usesConfigurableRequestTimeout) {
        clientWithReadTimeout(base, localTimeoutSeconds)
    } else {
        base
    }
    if (provider == AIProvider.CUSTOM_OPENAI) client = LocalEndpointTrust.withUserCaTrust(client)
    return client
}
```

- `CUSTOM_OPENAI` is the only provider with a user-entered URL → the only one
  that gets user-CA trust. Cloud providers, OLLAMA (`http://localhost:11434`,
  loopback: allowed today and unaffected), WebDAV, STT all keep the hardened
  platform default.
- `AndroidCAStore` contains both system and user CAs, so valid public certs
  keep working and self-signed ones now verify against the phone's own trust
  store; that matches the reporter's mental model ("the app should read the
  Android cert store").

### D2 (decision): LAN cleartext in release (deferred, documented)

Platform constraint 1 makes a per-host runtime cleartext toggle impossible.
Options:

| Option | What | Verdict |
|--------|------|---------|
| **A: https-only in release** (recommended) | Keep release NSC as-is. With D1, self-signed https works; reply to the reporter: switch OmniRoute (or the endpoint) to https, or use the debug build for LAN http. Settings hint text says "HTTPS required in the release build". | Zero security regression, minimal diff. Fixes the reporter's actual use case (they already run a root CA on the phone and prefer https). |
| **B: opt-in LAN cleartext** (follow-up candidate) | Release base-config `cleartextTrafficPermitted="true"` **plus** an app-level gate: new per-provider "Allow insecure HTTP" toggle checked in `AiHttp`/dispatch that rejects cleartext URLs unless enabled. | Platform then permits cleartext for *all* sockets (WebDAV, STT, OFF, WebView): the app-level gate must be replicated in every client or it silently widens. More surface, weaker default posture. Only if LAN-http demand materializes. |

**Decision for v1: Option A.** D1 already unblocks self-hosted servers over
https; cleartext stays a debug-build/loopback feature. Revisit B if #8 gets
follow-ups asking for plain-http LAN support.

### D3 (small hardening): scheme normalization for custom base URLs

Mirror `WebDavUrl.normalizeWebDavUrl` for the AI base URL: a pasted
`192.168.1.5:8000/v1` (no scheme) currently produces a confusing failure;
default a missing scheme to `https://` (and strip stacked schemes). With D1,
an https-normalized URL against a self-signed host now succeeds. Land as a
small pure function next to `AiHttp` (or in `PreferencesStoreAi` where the URL
is written), with unit tests.

### D4 (small hardening): actionable connection errors

When a custom-endpoint call fails with `UnknownServiceException` (cleartext)
or `CertPathValidatorException` (untrusted cert), surface a hint in the
existing `AiError` message: "Release builds require https; user-installed CA
certificates are trusted for custom endpoints." Cheap, and it converts the
reporter's two cryptic errors into guidance.

## Strings / localization

- New hint string for the Base URL field (e.g. `settings_custom_url_hint`:
  "HTTPS required in the release build. Certificates you install on this phone
  are trusted for this endpoint.").
- **Parity cost**: per `docs/LOCALIZATION.md` + `testdata/parity/locales.json`,
  new user-facing strings need entries in **all 14 locale variants**
  (`values-*`) or the locale fixture drifts. Either do the full-locale pass in
  the same change, or reuse an existing string: decide at implementation time.

## Tests

- Unit: `LocalEndpointTrust`: client for `CUSTOM_OPENAI` carries the
  AndroidCAStore-backed trust manager; other providers return the base client
  unchanged (assert same instance).
- Unit (if D3): scheme normalization (missing scheme → https, stacked schemes,
  explicit http preserved).
- Device pass (Windows adb): OmniRoute http on debug
  (should still work: debug NSC allows cleartext), https + self-signed on
  release (now works), cloud providers unaffected.

## Verification checklist (release build)

1. Custom endpoint `https://<lan-ip>:<port>/v1` with self-signed cert + root
   CA installed on phone → food analysis and coach chat succeed.
2. Same endpoint over `http://` → clean error with the D4 hint (not a raw
   platform exception).
3. Cloud provider (e.g. Gemini) still connects; a *newly* user-installed CA
   cannot intercept it (no app-wide trust change).
4. OLLAMA loopback unaffected.
5. `devenv tasks run release:check-parity` unaffected (no PWA/formula change).

## Out of scope / follow-ups

- WebDAV sync and custom STT endpoints have the identical trust gap (same
  `CertPathValidatorException`); fix them with the same `LocalEndpointTrust`
  helper in a later change: noting each has its own client
  (`WebDavClient.kt`, `RemoteSttClients.kt`).
- Option B (opt-in LAN cleartext) if demand appears.
