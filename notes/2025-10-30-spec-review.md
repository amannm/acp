# ACP Spec Review — 30 Oct 2025

## Reviewed Materials
- specification/2025-09-29/rfcs/rfc.agentic_checkout.md
- specification/2025-09-29/spec/openapi/openapi.agentic_checkout.yaml
- specification/2025-09-29/spec/openapi/openapi.agentic_checkout_webhook.yaml
- specification/2025-09-29/rfcs/rfc.delegate_payment.md
- specification/2025-09-29/spec/openapi/openapi.delegate_payment.yaml

## Implementation Coverage Snapshot
- **Domain & Serialization:** Records (`CheckoutSession`, `DelegatePaymentRequest`, etc.) and Parsson codecs mirror schema fields, enforce non-null invariants, and serialize enum values to the lower-case forms spelled out in the OpenAPI definitions.
- **Lifecycle Services:** `InMemoryCheckoutSessionService` and `InMemoryDelegatePaymentService` now implement idempotency, status transitions, allowance validation, webhook publication, and deterministic pricing/tax (minor-unit integers) per RFC §§2–6.
- **HTTP Surface:** `CheckoutSessionServlet` and `DelegatePaymentServlet` enforce `Authorization` + `API-Version`, echo correlation headers, map domain exceptions to the required flat error shape, and require JSON payloads where mandated.
- **Security Hooks:** `ConfigurableRequestAuthenticator` validates bearer tokens, canonicalizes JSON for signature verification, enforces HMAC key selection, and bounds timestamp skew.
- **Observability:** `ServeCommand` wires correlation headers through to responses, and webhook publisher signs outbound payloads with HMAC + timestamp.

## RFC §10 Conformance Checklist Status
| Requirement | Status | Notes |
| --- | --- | --- |
| Enforce HTTPS + TLS 1.2+ | 🚧 | Server listens over plain HTTP only; CLI lacks TLS configuration. |
| Authoritative cart state on every response | ✅ | `CheckoutSessionServlet` reserializes full session after each mutation. |
| Integer minor-unit amounts | ✅ | `MinorUnitAmount` record guards range and all totals derive from integers. |
| Create/Update/Retrieve/Complete/Cancel endpoints | ✅ | All verbs implemented with appropriate status codes (201/200/404/409/405). |
| Idempotency semantics for create/complete | ✅ | Dedicated stores replay identical requests and raise conflicts on mutation. |
| Flat error objects (`type/code/message/param?`) | ✅ | Codec emits expected shape; servlet mapping aligned with RFC error taxonomy. |
| Auth + signature verification | ✅ | HMAC verification + timestamp window enforced when secrets configured. |
| Order webhook emission | ⚠️ | `OrderWebhookPublisher` publishes `order_create` events; `order_update` flows still TODO.

## Key Gaps Identified
1. **Transport Security:** Reference server violates HTTPS/TLS requirement, blocking certification and contradicting RFC §7. Adding configurable TLS is the top priority.
2. **Webhook Completeness:** Only `order_create` webhooks ship; RFC expects additional lifecycle events (`order_update`, refunds). Not part of today’s scope but should remain on the radar.
3. **Operational Hardening:** Logging/metrics, persistence, and PSP integration remain future work; today’s in-memory defaults are suitable only for demos/tests.

## Immediate Focus
- Introduce mandatory (or explicitly opt-in) TLS support in the embedded Jetty server and surface configuration via `acp serve`.
- Backfill automated verification to cover TLS startup and request servicing so regressions are caught early.
- Track webhook and productionization follow-ups separately once transport security is compliant.

## Progress on 30 Oct 2025
- Embedded Jetty server now accepts a `TlsConfiguration`, enabling HTTPS-only deployments by default while gating plain HTTP behind `--allow-insecure-http`.
- `acp serve` exposes `--tls-keystore`, `--tls-keystore-password`, `--tls-key-password`, and updated `--port` semantics for HTTPS; running without TLS now requires an explicit opt-in flag.
- Servlet integration tests execute against self-signed TLS endpoints using an ephemeral PKCS#12 keystore to exercise the secure listener end-to-end.
