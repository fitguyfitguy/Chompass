# Design: Local OpenAI-compatible endpoints (cleartext + user-CA trust) (ARCHIVED)

Status: **ARCHIVED 2026-09-08**: shipped (D1/D3/D4 in 3.9.0, D2 Option B +
Ollama trust + error hints in 3.21.0); kept as history only.

What shipped (issue #8): user-installed root-CA trust for custom
OpenAI-compatible endpoints and Ollama, release cleartext opt-in behind an
app-level gate, URL scheme normalization, and actionable connection errors
on the `AiError.Network` surface. The cleartext capability side effects are
documented in `android/app/src/main/res/xml/network_security_config.xml`.

- Full doc: [`docs/archive/LOCAL_ENDPOINT_TRUST_DESIGN.md`](archive/LOCAL_ENDPOINT_TRUST_DESIGN.md)
- Shipped record: `docs/CHANGELOG.md` (3.9.0, 3.21.0)
