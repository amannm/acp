# RFC: Agentic Checkout — Merchant REST API

**Status:** Draft  
**Version:** 2025-09-29  
**Scope:** Checkout session lifecycle and webhook integration

This RFC defines the **Agentic Checkout Specification (ACS)**, a standardized REST API contract that merchants SHOULD implement to support experiences across agent platforms.

The merchant remains the **system of record** for all orders, payments, taxes, and compliance.  
This specification ensures that:

- Orders are processed entirely on the merchant’s existing commerce stack.
- Payment authorization and settlement continue to occur via the merchant’s PSP.

---

## 1. Scope & Goals

- Provide a **stable, versioned** API surface (`API-Version: 2025-09-29`) that ChatGPT calls to create, update, retrieve, complete, and cancel checkout sessions.
- Ensure ChatGPT renders an **authoritative cart state** on every response.
- Keep **payments on merchant rails**; optional delegated payments are covered separately.
- Support **safe retries** via idempotency and **strong security** via authentication and request signing.

**Out of scope:** PSP authorization/capture semantics, returns/exchanges workflows, tax configuration, fraud modeling details.

### 1.1 Normative Language

The key words **MUST**, **MUST NOT**, **SHOULD**, **MAY** follow RFC 2119/8174.

---

## 2. Protocol Phases

### 2.1 Initialization

- **Versioning:** Client (ChatGPT) **MUST** send `API-Version`. Server **MUST** validate support (e.g., `2025-09-29`).
- **Identity/Signing:** Server **SHOULD** publish acceptable signature algorithms out‑of‑band; client **SHOULD** sign requests (`Signature`) over canonical JSON with an accompanying `Timestamp` (RFC 3339).
- **Capabilities:** Merchant **SHOULD** document accepted payment methods (e.g., `card`) and fulfillment types (`shipping`, `digital`).

### 2.2 Session Lifecycle

1. **Create** — `POST /checkout_sessions` initializes a session from items and optional buyer/address.
2. **Update** — `POST /checkout_sessions/{id}` applies changes (items, address, fulfillment option).
3. **Retrieve** — `GET /checkout_sessions/{id}` returns latest authoritative state.
4. **Complete** — `POST /checkout_sessions/{id}/complete` finalizes with a payment method and **MUST** create an order.
5. **Cancel** — `POST /checkout_sessions/{id}/cancel` cancels if the session is not yet completed/canceled.

### 2.3 Webhooks (Separate Spec)

Order lifecycle updates (`order_create`, `order_update`) **MUST** be emitted to the application’s webhook receiver (see **Agentic Checkout Webhooks API** RFC/OAS).

---

## 3. HTTP Interface

### 3.1 Common Requirements

All endpoints **MUST** use HTTPS and return JSON. Amounts **MUST** be integers in minor units.

**Request Headers (sent by ChatGPT unless noted):**

- `Authorization: Bearer <token>` (**REQUIRED**)
- `Content-Type: application/json` (**REQUIRED** on requests with body)
- `Accept-Language: <locale>` (e.g., `en-US`) (**RECOMMENDED**)
- `User-Agent: <string>` (**RECOMMENDED**)
- `Idempotency-Key: <string>` (**RECOMMENDED**; required for create/complete in certification)
- `Request-Id: <string>` (**RECOMMENDED**)
- `Signature: <base64url>` (**RECOMMENDED**)
- `Timestamp: <RFC3339>` (**RECOMMENDED**)
- `API-Version: 2025-09-29` (**REQUIRED**)

**Response Headers:**

- `Idempotency-Key` — echo if provided
- `Request-Id` — echo if provided

**Error Shape (flat):**

```json
{
  "type": "invalid_request",
  "code": "invalid",
  "message": "Description",
  "param": "$.line_items[0].quantity"
}
```

Where `type` ∈ `invalid_request | request_not_idempotent | processing_error | service_unavailable`. `param` is an RFC 9535 JSONPath (optional).

---

## 4. Endpoints

### 4.1 Create Session

`POST /checkout_sessions` → **201 Created**

**Request body (subset):**

```json
{
  "items": [{ "id": "item_123", "quantity": 1 }],
  "buyer": {
    "first_name": "John",
    "last_name": "Smith",
    "email": "john@example.com"
  },
  "fulfillment_address": {
    "name": "John Smith",
    "line_one": "1234 Chat Road",
    "line_two": "",
    "city": "San Francisco",
    "state": "CA",
    "country": "US",
    "postal_code": "94102"
  }
}
```

**Response body (authoritative cart):**

- `id` (string)
- `payment_provider` (e.g., `stripe`, `supported_payment_methods: ["card"]`)
- `status`: `not_ready_for_payment | ready_for_payment | completed | canceled | in_progress`
- `currency` (ISO 4217, e.g., `usd`)
- `line_items[]` with `base_amount`, `discount`, `subtotal`, `tax`, `total` (all **integers**)
- `fulfillment_address` (if known)
- `fulfillment_options[]` (shipping/digital) with integer costs and optional delivery windows
- `fulfillment_option_id` (selected option)
- `totals[]` each with integer `amount`
- `messages[]` (`info` / `error` entries)
- `links[]` policy URLs

### 4.2 Update Session

`POST /checkout_sessions/{checkout_session_id}` → **200 OK**  
Body may include `items`, `fulfillment_address`, or `fulfillment_option_id`. Response returns full authoritative state as in **Create**.

### 4.3 Retrieve Session

`GET /checkout_sessions/{checkout_session_id}` → **200 OK** (or **404** if not found)  
Returns the full authoritative session state.

### 4.4 Complete Session

`POST /checkout_sessions/{checkout_session_id}/complete` → **200 OK** on success  
Body includes `payment_data` (e.g., delegated token + optional billing address) and optional `buyer`.  
Response **MUST** include `status: completed` and an `order` with `id`, `checkout_session_id`, and `permalink_url`.

### 4.5 Cancel Session

`POST /checkout_sessions/{checkout_session_id}/cancel` → **200 OK** when canceled, **405** if already `completed` or `canceled`.

---

## 5. Data Model (authoritative extract)

- **Item**: `id` (string), `quantity` (int ≥ 1)
- **LineItem**: `id`, `item`, `base_amount`, `discount`, `subtotal`, `tax`, `total` (**int**)
- **Total**: `type` (`items_base_amount | items_discount | subtotal | discount | fulfillment | tax | fee | total`), `display_text`, `amount` (**int**)
- **FulfillmentOption (shipping)**: `id`, `title`, `subtitle?`, `carrier?`, `earliest_delivery_time?`, `latest_delivery_time?`, `subtotal`, `tax`, `total` (**int**)
- **FulfillmentOption (digital)**: `id`, `title`, `subtitle?`, `subtotal`, `tax`, `total` (**int**)
- **PaymentProvider**: `provider` (`stripe`), `supported_payment_methods` (`["card"]`)
- **PaymentData**: `token`, `provider` (`stripe`), `billing_address?`
- **Order**: `id`, `checkout_session_id`, `permalink_url`
- **Message (info)**: `type: "info"`, `param?`, `content_type: "plain"|"markdown"`, `content`
- **Message (error)**: `type: "error"`, `code` (`missing|invalid|out_of_stock|payment_declined|requires_sign_in|requires_3ds`), `param?`, `content_type`, `content`

All money fields are **integers (minor units)**.

---

## 6. Idempotency, Retries & Concurrency

- Clients **SHOULD** set `Idempotency-Key` on **create** and **complete** requests.
- Replays with the **same** key and **identical** parameters **MUST** return the original result.
- Replays with the **same** key and **different** parameters **MUST** return **409** with:
  ```json
  {
    "type": "invalid_request",
    "code": "idempotency_conflict",
    "message": "Same Idempotency-Key used with different parameters"
  }
  ```
- Servers **SHOULD** implement at‑least‑once processing and be resilient to network timeouts.

---

## 7. Security Considerations

- **Authentication:** `Authorization: Bearer <token>` **REQUIRED**.
- **Integrity & Freshness:** `Signature` over canonical JSON and `Timestamp` **SHOULD** be verified with a bounded clock‑skew window.
- **PCI/PII:** Do not log full PAN/CVC; redact addresses as required by policy. TLS 1.2+ **MUST** be used.
- **Webhooks:** Verify HMAC (`Merchant-Signature`) on webhook calls (see separate Webhooks RFC/OAS).

---

## 8. Validation Rules (non‑exhaustive)

- `currency` is ISO‑4217 (lowercase recommended, e.g., `usd`).
- All monetary amounts are **integers** (minor units).
- `status` ∈ `not_ready_for_payment | ready_for_payment | completed | canceled | in_progress`.
- At least one `Total` with `type: "total"` **SHOULD** be present when calculable.
- `fulfillment_option_id` **MUST** match an element of `fulfillment_options` when set.
- `messages[].param` **SHOULD** be an RFC 9535 JSONPath when applicable.

---

## 9. Examples (minor units)

### 9.1 Create — Request

```json
{
  "items": [{ "id": "item_456", "quantity": 1 }],
  "fulfillment_address": {
    "name": "test",
    "line_one": "1234 Chat Road",
    "line_two": "",
    "city": "San Francisco",
    "state": "CA",
    "country": "US",
    "postal_code": "94131"
  }
}
```

### 9.2 Create — Response (201)

```json
{
  "id": "checkout_session_123",
  "payment_provider": {
    "provider": "stripe",
    "supported_payment_methods": ["card"]
  },
  "status": "ready_for_payment",
  "currency": "usd",
  "line_items": [
    {
      "id": "line_item_456",
      "item": { "id": "item_456", "quantity": 1 },
      "base_amount": 300,
      "discount": 0,
      "subtotal": 300,
      "tax": 30,
      "total": 330
    }
  ],
  "fulfillment_address": {
    "name": "test",
    "line_one": "1234 Chat Road",
    "line_two": "",
    "city": "San Francisco",
    "state": "CA",
    "country": "US",
    "postal_code": "94131"
  },
  "fulfillment_option_id": "fulfillment_option_123",
  "totals": [
    {
      "type": "items_base_amount",
      "display_text": "Item(s) total",
      "amount": 300
    },
    { "type": "subtotal", "display_text": "Subtotal", "amount": 300 },
    { "type": "tax", "display_text": "Tax", "amount": 30 },
    { "type": "fulfillment", "display_text": "Fulfillment", "amount": 100 },
    { "type": "total", "display_text": "Total", "amount": 430 }
  ],
  "fulfillment_options": [
    {
      "type": "shipping",
      "id": "fulfillment_option_123",
      "title": "Standard",
      "subtitle": "Arrives in 4-5 days",
      "carrier": "USPS",
      "earliest_delivery_time": "2025-10-12T07:20:50.52Z",
      "latest_delivery_time": "2025-10-13T07:20:50.52Z",
      "subtotal": 100,
      "tax": 0,
      "total": 100
    },
    {
      "type": "shipping",
      "id": "fulfillment_option_456",
      "title": "Express",
      "subtitle": "Arrives in 1-2 days",
      "carrier": "USPS",
      "earliest_delivery_time": "2025-10-09T07:20:50.52Z",
      "latest_delivery_time": "2025-10-10T07:20:50.52Z",
      "subtotal": 500,
      "tax": 0,
      "total": 500
    }
  ],
  "messages": [],
  "links": [
    {
      "type": "terms_of_use",
      "url": "https://www.testshop.com/legal/terms-of-use"
    }
  ]
}
```

### 9.3 Update — Request

```json
{ "fulfillment_option_id": "fulfillment_option_456" }
```

### 9.4 Update — Response (200)

```json
{
  "id": "checkout_session_123",
  "status": "ready_for_payment",
  "currency": "usd",
  "line_items": [
    {
      "id": "line_item_456",
      "item": { "id": "item_456", "quantity": 1 },
      "base_amount": 300,
      "discount": 0,
      "subtotal": 300,
      "tax": 30,
      "total": 330
    }
  ],
  "fulfillment_address": {
    "name": "test",
    "line_one": "1234 Chat Road",
    "line_two": "",
    "city": "San Francisco",
    "state": "CA",
    "country": "US",
    "postal_code": "94131"
  },
  "fulfillment_option_id": "fulfillment_option_456",
  "totals": [
    {
      "type": "items_base_amount",
      "display_text": "Item(s) total",
      "amount": 300
    },
    { "type": "subtotal", "display_text": "Subtotal", "amount": 300 },
    { "type": "tax", "display_text": "Tax", "amount": 30 },
    { "type": "fulfillment", "display_text": "Fulfillment", "amount": 500 },
    { "type": "total", "display_text": "Total", "amount": 830 }
  ],
  "fulfillment_options": [
    {
      "type": "shipping",
      "id": "fulfillment_option_123",
      "title": "Standard",
      "subtitle": "Arrives in 4-5 days",
      "carrier": "USPS",
      "earliest_delivery_time": "2025-10-12T07:20:50.52Z",
      "latest_delivery_time": "2025-10-13T07:20:50.52Z",
      "subtotal": 100,
      "tax": 0,
      "total": 100
    },
    {
      "type": "shipping",
      "id": "fulfillment_option_456",
      "title": "Express",
      "subtitle": "Arrives in 1-2 days",
      "carrier": "USPS",
      "earliest_delivery_time": "2025-10-09T07:20:50.52Z",
      "latest_delivery_time": "2025-10-10T07:20:50.52Z",
      "subtotal": 500,
      "tax": 0,
      "total": 500
    }
  ],
  "messages": [],
  "links": [
    {
      "type": "terms_of_use",
      "url": "https://www.testshop.com/legal/terms-of-use"
    }
  ]
}
```

### 9.5 Complete — Request

```json
{
  "buyer": {
    "first_name": "John",
    "last_name": "Smith",
    "email": "johnsmith@mail.com",
    "phone_number": "15552003434"
  },
  "payment_data": {
    "token": "spt_123",
    "provider": "stripe",
    "billing_address": {
      "name": "test",
      "line_one": "1234 Chat Road",
      "line_two": "",
      "city": "San Francisco",
      "state": "CA",
      "country": "US",
      "postal_code": "94131"
    }
  }
}
```

### 9.6 Complete — Response (200)

```json
{
  "id": "checkout_session_123",
  "buyer": {
    "first_name": "John",
    "last_name": "Smith",
    "email": "johnsmith@mail.com",
    "phone_number": "15552003434"
  },
  "status": "completed",
  "currency": "usd",
  "line_items": [
    {
      "id": "line_item_456",
      "item": { "id": "item_456", "quantity": 1 },
      "base_amount": 300,
      "discount": 0,
      "subtotal": 300,
      "tax": 30,
      "total": 330
    }
  ],
  "fulfillment_address": {
    "name": "test",
    "line_one": "1234 Chat Road",
    "line_two": "",
    "city": "San Francisco",
    "state": "CA",
    "country": "US",
    "postal_code": "94131"
  },
  "fulfillment_option_id": "fulfillment_option_123",
  "totals": [
    {
      "type": "items_base_amount",
      "display_text": "Item(s) total",
      "amount": 300
    },
    { "type": "subtotal", "display_text": "Subtotal", "amount": 300 },
    { "type": "tax", "display_text": "Tax", "amount": 30 },
    { "type": "fulfillment", "display_text": "Fulfillment", "amount": 100 },
    { "type": "total", "display_text": "Total", "amount": 430 }
  ],
  "fulfillment_options": [
    {
      "type": "shipping",
      "id": "fulfillment_option_123",
      "title": "Standard",
      "subtitle": "Arrives in 4-5 days",
      "carrier": "USPS",
      "earliest_delivery_time": "2025-10-12T07:20:50.52Z",
      "latest_delivery_time": "2025-10-13T07:20:50.52Z",
      "subtotal": 100,
      "tax": 0,
      "total": 100
    },
    {
      "type": "shipping",
      "id": "fulfillment_option_456",
      "title": "Express",
      "subtitle": "Arrives in 1-2 days",
      "carrier": "USPS",
      "earliest_delivery_time": "2025-10-09T07:20:50.52Z",
      "latest_delivery_time": "2025-10-10T07:20:50.52Z",
      "subtotal": 500,
      "tax": 0,
      "total": 500
    }
  ],
  "messages": [],
  "links": [
    {
      "type": "terms_of_use",
      "url": "https://www.testshop.com/legal/terms-of-use"
    }
  ],
  "order": {
    "id": "ord_abc123",
    "checkout_session_id": "checkout_session_123",
    "permalink_url": "https://example.com/orders/ord_abc123"
  }
}
```

### 9.7 Cancel — Response (200)

```json
{
  "id": "checkout_session_123",
  "status": "canceled",
  "currency": "usd",
  "line_items": [
    {
      "id": "line_item_456",
      "item": { "id": "item_456", "quantity": 1 },
      "base_amount": 300,
      "discount": 0,
      "subtotal": 300,
      "tax": 30,
      "total": 330
    }
  ],
  "totals": [
    {
      "type": "items_base_amount",
      "display_text": "Item(s) total",
      "amount": 300
    },
    { "type": "subtotal", "display_text": "Subtotal", "amount": 300 },
    { "type": "tax", "display_text": "Tax", "amount": 30 },
    { "type": "fulfillment", "display_text": "Fulfillment", "amount": 100 },
    { "type": "total", "display_text": "Total", "amount": 430 }
  ],
  "messages": [
    {
      "type": "info",
      "content_type": "plain",
      "content": "Checkout session has been canceled."
    }
  ],
  "links": [
    {
      "type": "terms_of_use",
      "url": "https://www.testshop.com/legal/terms-of-use"
    }
  ]
}
```

---

## 10. Conformance Checklist

- [ ] Enforces HTTPS, JSON, and `API-Version: 2025-09-29`
- [ ] Returns **authoritative** cart state on every response
- [ ] Uses **integer** minor units for all monetary amounts
- [ ] Implements create, update (POST), retrieve (GET), complete, cancel
- [ ] Implements idempotency semantics and conflict detection
- [ ] Emits flat error objects with `type/code/message/param?`
- [ ] Verifies auth; signs/verifies requests where applicable
- [ ] Emits order webhooks per the Webhooks RFC (separate spec)

---

## 11. Change Log

- **2025-09-12**: Initial draft; clarified **integer amount** requirement; separated webhooks into dedicated spec.
