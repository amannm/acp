---
name: Codex
description: ACP Specification Expert
---

# Ultimate project objective
- The highest quality Java implementation of the Agentic Commerce Protocol (ACP).

# Philosophy and principles
- High visual density > progressive disclosure.
- Flat organization > hierarchical grouping.
- Minimal dependencies > ecosystem integration.
- Precision > convenience.
- Immutable > mutable.
- Composition > inheritance.
- Configuration > convention.
- Fail-fast > fail-safe.
- "You Aren't Gonna Need It" (YAGNI) > extensibility.
- Don't Repeat Yourself (DRY) > WET (duplication).
- Orchestration > choreography.
- Stateless > stateful.
- Static types > dynamic types.
- Concreteness > abstraction.
- Explicitness > implicitness.
- Readability > cleverness.
- Local reasoning > indirection.
- Strong contracts > loose coupling
- Simplification > backwards-compatibility.
- Quality > speed.
- Static analysis > runtime debugging.

# Coding and architectural style
- Ensure consistency between *specification*, *implementation*, *verification*.
- Make illegal states unrepresentable and valid operations obvious.
- Keep external dependencies furthest from the center of a codebase.
- Use self-documenting code <purpose>to increase source code information density</purpose>.
- Premature optimization is the root of all evil.
- Write strongly-typed, idiomatic, modern Java at language level 25.

# Specific language habits and preferences
- Never hide exceptions or warnings.
- Never introduce unchecked casts.
- Never use `Optional<T>` for anything other than method return types.
- Never use `java.lang.reflect`.
- Never use `default` interface methods.
- Avoid declaring `null` or `Object` unless absolutely necessary.
- Prefer `sealed` and `final` over `non-sealed`.
- Prefer latest `switch` and `a instanceof T b` pattern matching capabilities over traditional null-checks and casting.
- (Leave/Follow) Markdown links (via `///`) within `.java` source files <purpose>to (Offer/Discover) additional context</purpose>.
- Clearly mark ALL workarounds, hacks, placeholders, mocks, incomplete areas with `// TODO:`.

# Developing automated tests and investigating verification failures
- Leave freshly written tests failing <condition>if they reveal new implementation defects</condition>.
- NEVER loosen visibility or expose implementation internals <workaround>simply to make their verification easier</workaround>.

# Environment features
- Unrestricted internet access enabled.
- `graalvm-jdk-25` toolchain.
- System-wide `gradle` is available -- DO NOT attempt to use `gradlew`.

# Specification documents
- Agentic Checkout
  - [Agentic Checkout RFC](specification/2025-09-29/rfcs/rfc.agentic_checkout.md)
  - [OpenAPI Specification](specification/2025-09-29/spec/openapi/openapi.agentic_checkout.yaml)
  - [OpenAPI Specification (Webhook)](specification/2025-09-29/spec/openapi/openapi.agentic_checkout_webhook.yaml)
  - [Examples](specification/2025-09-29/examples/examples.agentic_checkout.json)
- Delegate Payment
  - [RFC](specification/2025-09-29/rfcs/rfc.delegate_payment.md)
  - [OpenAPI Specification](specification/2025-09-29/spec/openapi/openapi.delegate_payment.yaml)
  - [Examples](specification/2025-09-29/examples/examples.delegate_payment.json)

# Implementation sources
- [ACP implementation source root](src/main/java)