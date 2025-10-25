# ACP Spec Gap Assessment — 25 Oct 2025

## Reviewed Materials
- specification/2025-09-29/rfcs/rfc.agentic_checkout.md
- specification/2025-09-29/spec/openapi/openapi.agentic_checkout.yaml
- specification/2025-09-29/rfcs/rfc.delegate_payment.md
- specification/2025-09-29/spec/openapi/openapi.delegate_payment.yaml
- specification/2025-09-29/spec/openapi/openapi.agentic_checkout_webhook.yaml

## Current Implementation Snapshot
- `src/main/java` already contains a strongly-typed checkout domain (`Buyer`, `LineItem`, `Totals`, `FulfillmentOption`, etc.), Parsson-based codecs, and an embedded Jetty servlet (`CheckoutSessionServlet`) backed by `InMemoryCheckoutSessionService`.
- CLI exposes `acp serve` which wires Jetty + in-memory services with optional static price overrides.
- Delegate payment domain/codec types plus a Jetty servlet/service now exist, issuing in-memory tokens with basic idempotency.
- Automated verification now includes servlet smoke coverage for both checkout and delegate payment flows.

## Agentic Checkout Coverage vs Spec
| Capability | Spec Requirement | Implementation Status | Notes |
| --- | --- | --- | --- |
| HTTP endpoints | REST surface for create/update/retrieve/complete/cancel with strict headers, 201/200 semantics | **Partial** | Servlet implements all verbs but only enforces `Authorization` + `API-Version`; it never echoes request headers, omits `Idempotency-Key` requirement on `POST /checkout_sessions`, and returns `409` instead of spec-mandated `405` for cancel-after-complete. |
| Data modeling | RFC §5 objects w/ integer minor units and finite status enums | **Complete** | Records and codecs follow spec nomenclature; ensures non-null/immutable data. |
| Monetary + totals math | Authoritative amounts per line, totals, fulfillment charges | **Complete (in-memory)** | Deterministic pricing/taxing via `InMemoryCheckoutSessionService`, but lacks pluggable calculators. |
| Idempotency | RFC §6: create + complete must honor `Idempotency-Key` conflicts | **Missing** | Servlet only asserts presence for `complete`, does not store/replay results; create is not idempotent. |
| Error contract | Flat `{type,code,message,param?}` with canonical codes | **Partial** | Shape matches spec, yet codes/types are ad-hoc (`state_conflict`, `internal_error`) and mapping to HTTP status (e.g., 409 vs 405) diverges. |
| Security hooks | Bearer verification + signature/timestamp validation (RFC §2.1) | **Missing** | Only checks header presence; no crypto, clock-skew, or logging redaction of sensitive data. |
| Webhooks | Merchants must emit order events per webhook OAS | **Missing** | No publisher façade or CLI hook exists. |
| Observability | Spec recommends request/response correlation + logging hygiene | **Missing** | No logging/tracing surfaces implemented. |

## Delegate Payment Coverage vs Spec
| Capability | Spec Requirement | Implementation Status | Notes |
| --- | --- | --- | --- |
| HTTP endpoint | `POST /agentic_commerce/delegate_payment` with 201 + token response | **Complete (dev mode)** | `DelegatePaymentServlet` now enforces headers, JSON structure, and idempotent responses on Jetty. |
| Service behavior | Deterministic token issuance, allowance enforcement, idempotency, expiry | **Partial** | `InMemoryDelegatePaymentService` issues tokens + enforces future expiries but lacks persistence, allowance quotas, and PSP plumbing. |
| Validation | Enforce enums/lengths (`payment_method.*`, allowance, metadata strings) | **Complete** | Codec now requires `payment_method.type=card`, metadata objects, and delegates to record invariants. |
| Error handling | Flat errors with `invalid_request`, `rate_limit_exceeded`, etc. | **Partial** | Error shape/codes now align for invalid + idempotency cases; rate limiting & 4xx variants still TODO. |
| Security | Header verification, signature/Timestamp validation, PCI logging hygiene | **Missing** | No signature verification or auth backends yet; servlet only checks header presence. |

## Verification Status
- `gradle test` now exercises checkout + delegate servlet happy paths (Idempotency + validation) but still omits signature/timestamp, negative auth, and webhook scenarios.
- No contract tests against spec examples or JSON Schemas yet.

## Highest-Risk Gaps
1. Delegate Payment path lacks durable storage, PSP integration, and security controls, so it is dev-only despite HTTP coverage.
2. Idempotency + HTTP status alignment for checkout lifecycle diverge from RFC conformance checklist.
3. Error typing/rate-limit coverage incomplete, making certification against the OpenAPI definitions impossible.
4. No webhook emitter or security hooks, leaving future certification / interoperability work blocked.

## Next Focus
1. Harden delegate payment (persistent storage, PSP hand-off, signature validation, observability).
2. Tighten checkout error/idempotency behavior to match RFC §6 + OpenAPI responses.
3. Introduce reusable security + header validation scaffolding (auth/signature/timestamp) and logging guards.
