# Design: Local OpenAI-compatible endpoints (cleartext + user-CA trust) (issue #8)

Status: **D2 follow-up implemented (issue #8 follow-ups, ARR8 2026-08-18/19).** The
original change shipped in 3.9.0 (2026-08-11): D1 (user-CA trust for custom
endpoints), D3 (scheme normalization) and D4 (actionable connection errors)
landed; D2 was deferred by decision. The follow-up change lands **D2 Option B**
(release cleartext opt-in with an app-level gate), extends D1 to **Ollama**
(user-entered endpoint, same trust model), and wires D4's hints into the
`AiError.Network` surface (they were defined but never shown).
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

- `CUSTOM_OPENAI` and `OLLAMA` are the providers with user-entered URLs → the
  only ones that get user-CA trust (follow-up: Ollama's URL is user-overridable
  via `prefs.customBaseUrl(OLLAMA)`, same trust model as custom). Cloud
  providers, WebDAV, STT all keep the hardened platform default.
- `AndroidCAStore` contains both system and user CAs, so valid public certs
  keep working and self-signed ones now verify against the phone's own trust
  store; that matches the reporter's mental model ("the app should read the
  Android cert store").

### D2 (implemented follow-up): opt-in LAN cleartext in release

Platform constraint 1 makes a per-host runtime cleartext toggle impossible.
The #8 follow-ups (ARR8: even the custom endpoint fails for LAN cleartext;
darkxylese: same) are that demand, so **Option B is now implemented**:

| Option | What | Verdict |
|--------|------|---------|
| **A: https-only in release** (v1) | Keep release NSC as-is; self-signed https works via D1; cleartext stays a debug-build/loopback feature. | Shipped 3.9.0. |
| **B: opt-in LAN cleartext** (implemented) | Release base-config `cleartextTrafficPermitted="true"` **plus** an app-level gate: `AiHttp.assertCleartextAllowed(url, allowInsecureHttp)` rejects http:// URLs for non-loopback hosts unless the user enabled the "Allow insecure HTTP" toggle (Settings → AI & Speech, default OFF; pref `allowInsecureHttp`). Gate is called at the base-URL resolution sites (`ChatService` + `FoodAnalysisService.dispatch`, which also covers fallback). | Chosen. Default posture unchanged (off); loopback stays exempt for the default Ollama URL. |

**Known widening (accepted):** the NSC applies app-wide, so WebDAV and custom
STT endpoints also gain cleartext capability in release builds. Both only ever
use http when the user configured an http:// URL themselves; replicating the
gate in those clients is a possible follow-up if it becomes a concern.

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

**Follow-up wiring:** `connectionFailureHint(cause)` is now the single mapper,
and `AiError.Network` uses it: hint causes show the English hint verbatim
(`messageRes = 0`), everything else keeps the localized generic network
message. The cleartext hint now points at the "Allow insecure HTTP" toggle
instead of the debug build. `AiError.InsecureHttpBlocked` is thrown by the D2
gate before any network attempt.

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
2. Same endpoint over `http://` → clean gate error pointing at the toggle
   while "Allow insecure HTTP" is off; succeeds once it is on.
3. Cloud provider (e.g. Gemini) still connects; a *newly* user-installed CA
   cannot intercept it (no app-wide trust change).
4. OLLAMA loopback unaffected (gate exempts localhost/127.0.0.1).
5. Remote Ollama over https + user CA connects (D1 extension).
6. `devenv tasks run release:check-parity` unaffected (no PWA/formula change).

## Out of scope / follow-ups

- WebDAV sync and custom STT endpoints have the identical trust gap (same
  `CertPathValidatorException`); fix them with the same `LocalEndpointTrust`
  helper in a later change: noting each has its own client
  (`WebDavClient.kt`, `RemoteSttClients.kt`).
- The D2 gate is enforced on the AI path only; WebDAV/STT cleartext now works
  in release whenever the user configured http:// URLs (accepted widening,
  see D2). Replicate the gate there if it becomes a concern.
