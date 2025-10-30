package com.amannmalik.acp.api.checkout;

import com.amannmalik.acp.api.checkout.model.Address;
import com.amannmalik.acp.api.checkout.model.Buyer;
import com.amannmalik.acp.api.checkout.model.CheckoutSession;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCompleteRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCreateRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionId;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionStatus;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionUpdateRequest;
import com.amannmalik.acp.api.checkout.model.FulfillmentOption;
import com.amannmalik.acp.api.checkout.model.FulfillmentOptionId;
import com.amannmalik.acp.api.checkout.model.Item;
import com.amannmalik.acp.api.checkout.model.LineItem;
import com.amannmalik.acp.api.checkout.model.Link;
import com.amannmalik.acp.api.checkout.model.Message;
import com.amannmalik.acp.api.checkout.model.Order;
import com.amannmalik.acp.api.checkout.model.PaymentProvider;
import com.amannmalik.acp.api.checkout.model.Total;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.api.shared.MinorUnitAmount;
import com.amannmalik.acp.server.webhook.OrderWebhookEvent;
import com.amannmalik.acp.server.webhook.OrderWebhookPublisher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

public final class InMemoryCheckoutSessionService implements CheckoutSessionService {
    private static final PaymentProvider PAYMENT_PROVIDER = new PaymentProvider(
            PaymentProvider.Provider.STRIPE,
            List.of(PaymentProvider.PaymentMethod.CARD));
    private static final List<Link> DEFAULT_LINKS = List.of(
            new Link(Link.LinkType.TERMS_OF_USE, URI.create("https://merchant.example.com/legal/terms-of-use")),
            new Link(Link.LinkType.PRIVACY_POLICY, URI.create("https://merchant.example.com/legal/privacy")));
    private static final BigDecimal TAX_RATE = new BigDecimal("0.0825");

    private final ConcurrentMap<String, CheckoutSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StoredCreateRequest> createIdempotency = new ConcurrentHashMap<>();
    private final ConcurrentMap<CompleteIdempotencyKey, StoredCompleteRequest> completeIdempotency =
            new ConcurrentHashMap<>();
    private final AtomicLong sessionSequence = new AtomicLong(1);
    private final AtomicLong lineItemSequence = new AtomicLong(1);
    private final AtomicLong orderSequence = new AtomicLong(1);
    private final Map<String, Long> priceBook;
    private final Clock clock;
    private final CurrencyCode currency;
    private final OrderWebhookPublisher webhookPublisher;

    public InMemoryCheckoutSessionService(Map<String, Long> priceBook, Clock clock, CurrencyCode currency) {
        this(priceBook, clock, currency, OrderWebhookPublisher.NOOP);
    }

    public InMemoryCheckoutSessionService(
            Map<String, Long> priceBook, Clock clock, CurrencyCode currency, OrderWebhookPublisher webhookPublisher) {
        this.priceBook = Map.copyOf(priceBook);
        this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
        this.currency = currency == null ? new CurrencyCode("usd") : currency;
        this.webhookPublisher = webhookPublisher == null ? OrderWebhookPublisher.NOOP : webhookPublisher;
    }

    public InMemoryCheckoutSessionService() {
        this(defaultPriceBook(), Clock.systemUTC(), new CurrencyCode("usd"));
    }

    public InMemoryCheckoutSessionService(OrderWebhookPublisher webhookPublisher) {
        this(defaultPriceBook(), Clock.systemUTC(), new CurrencyCode("usd"), webhookPublisher);
    }

    @Override
    public CheckoutSession create(CheckoutSessionCreateRequest request, String idempotencyKey) {
        var normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey == null) {
            return createNewSession(request);
        }
        var createdSession = new AtomicReference<CheckoutSession>();
        var stored = createIdempotency.compute(normalizedKey, (key, existing) -> {
            if (existing == null) {
                var session = createNewSession(request);
                createdSession.set(session);
                return new StoredCreateRequest(request, session.id().value());
            }
            if (!existing.request().equals(request)) {
                throw new CheckoutSessionIdempotencyConflictException(
                        "Same Idempotency-Key used with different parameters");
            }
            return existing;
        });
        if (createdSession.get() != null) {
            return createdSession.get();
        }
        return retrieve(new CheckoutSessionId(stored.sessionId()));
    }

    @Override
    public CheckoutSession update(CheckoutSessionId id, CheckoutSessionUpdateRequest request) {
        return sessions.compute(id.value(), (key, current) -> {
            if (current == null) {
                throw new CheckoutSessionNotFoundException(id);
            }
            ensureMutable(current);
            var buyer = request.buyer() != null ? request.buyer() : current.buyer();
            var fulfillmentAddress = request.fulfillmentAddress() != null
                    ? request.fulfillmentAddress()
                    : current.fulfillmentAddress();
            var items = request.items() != null ? request.items() : extractItems(current);
            var fulfillmentOptionId = request.fulfillmentOptionId() != null
                    ? request.fulfillmentOptionId()
                    : current.fulfillmentOptionId();
            return assemble(id, buyer, fulfillmentAddress, fulfillmentOptionId, items, current.status(), current.order());
        });
    }

    @Override
    public CheckoutSession retrieve(CheckoutSessionId id) {
        var session = sessions.get(id.value());
        if (session == null) {
            throw new CheckoutSessionNotFoundException(id);
        }
        return session;
    }

    @Override
    public CheckoutSession complete(
            CheckoutSessionId id, CheckoutSessionCompleteRequest request, String idempotencyKey) {
        var normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey == null) {
            return completeInternal(id, request);
        }
        var completedSession = new AtomicReference<CheckoutSession>();
        var key = new CompleteIdempotencyKey(id.value(), normalizedKey);
        completeIdempotency.compute(key, (k, existing) -> {
            if (existing == null) {
                completedSession.set(completeInternal(id, request));
                return new StoredCompleteRequest(request);
            }
            if (!existing.request().equals(request)) {
                throw new CheckoutSessionIdempotencyConflictException(
                        "Same Idempotency-Key used with different parameters");
            }
            return existing;
        });
        var session = completedSession.get();
        if (session != null) {
            return session;
        }
        return retrieve(id);
    }

    @Override
    public CheckoutSession cancel(CheckoutSessionId id) {
        return sessions.compute(id.value(), (key, current) -> {
            if (current == null) {
                throw new CheckoutSessionNotFoundException(id);
            }
            if (current.status() == CheckoutSessionStatus.COMPLETED) {
                throw new CheckoutSessionMethodNotAllowedException("Cannot cancel a completed session");
            }
            if (current.status() == CheckoutSessionStatus.CANCELED) {
                throw new CheckoutSessionMethodNotAllowedException("Checkout session already canceled");
            }
            return assemble(
                    id,
                    current.buyer(),
                    current.fulfillmentAddress(),
                    current.fulfillmentOptionId(),
                    extractItems(current),
                    CheckoutSessionStatus.CANCELED,
                    null);
        });
    }

    private CheckoutSession assemble(
            CheckoutSessionId id,
            Buyer buyer,
            Address fulfillmentAddress,
            FulfillmentOptionId requestedFulfillmentOptionId,
            List<Item> items,
            CheckoutSessionStatus status,
            Order order) {
        var normalizedItems = List.copyOf(items);
        var lineItems = priceItems(normalizedItems);
        var fulfillmentOptions = buildFulfillmentOptions();
        var fulfillmentOptionId = resolveFulfillmentOptionId(fulfillmentOptions, requestedFulfillmentOptionId);
        var totals = computeTotals(lineItems, fulfillmentOptions, fulfillmentOptionId);
        var messages = messagesForStatus(status);
        return new CheckoutSession(
                id,
                buyer,
                PAYMENT_PROVIDER,
                status,
                currency,
                lineItems,
                fulfillmentAddress,
                fulfillmentOptions,
                fulfillmentOptionId,
                totals,
                messages,
                DEFAULT_LINKS,
                order);
    }

    private static List<Item> extractItems(CheckoutSession session) {
        return session.lineItems().stream().map(LineItem::item).collect(Collectors.toUnmodifiableList());
    }

    private List<LineItem> priceItems(List<Item> items) {
        var result = new ArrayList<LineItem>(items.size());
        for (var item : items) {
            var unitPrice = priceForItem(item.id());
            var baseAmount = unitPrice * item.quantity();
            var discount = 0L;
            var subtotal = baseAmount - discount;
            var tax = calculateTax(subtotal);
            var total = subtotal + tax;
            result.add(new LineItem(
                    "line_%06d".formatted(lineItemSequence.getAndIncrement()),
                    item,
                    new MinorUnitAmount(baseAmount),
                    new MinorUnitAmount(discount),
                    new MinorUnitAmount(subtotal),
                    new MinorUnitAmount(tax),
                    new MinorUnitAmount(total)));
        }
        return List.copyOf(result);
    }

    private long calculateTax(long subtotal) {
        var amount = TAX_RATE.multiply(BigDecimal.valueOf(subtotal));
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private List<FulfillmentOption> buildFulfillmentOptions() {
        var now = Instant.now(clock);
        return List.of(
                new FulfillmentOption.Shipping(
                        "fulfillment_option_standard",
                        "Standard",
                        "Arrives in 4-5 days",
                        "USPS",
                        now.plus(Duration.ofDays(4)),
                        now.plus(Duration.ofDays(5)),
                        new MinorUnitAmount(500),
                        MinorUnitAmount.zero(),
                        new MinorUnitAmount(500)),
                new FulfillmentOption.Shipping(
                        "fulfillment_option_express",
                        "Express",
                        "Arrives in 1-2 days",
                        "UPS",
                        now.plus(Duration.ofDays(2)),
                        now.plus(Duration.ofDays(3)),
                        new MinorUnitAmount(1500),
                        MinorUnitAmount.zero(),
                        new MinorUnitAmount(1500)));
    }

    private FulfillmentOptionId resolveFulfillmentOptionId(
            List<FulfillmentOption> options,
            FulfillmentOptionId requested) {
        if (requested != null) {
            var exists = options.stream().anyMatch(option -> option.id().equals(requested.value()));
            if (!exists) {
                throw new CheckoutSessionConflictException("Unknown fulfillment_option_id: " + requested.value());
            }
            return requested;
        }
        if (options.isEmpty()) {
            return null;
        }
        return new FulfillmentOptionId(options.get(0).id());
    }

    private List<Total> computeTotals(
            List<LineItem> lineItems,
            List<FulfillmentOption> options,
            FulfillmentOptionId fulfillmentOptionId) {
        var baseAmount = sum(lineItems, line -> line.baseAmount().value());
        var discount = sum(lineItems, line -> line.discount().value());
        var subtotal = sum(lineItems, line -> line.subtotal().value());
        var tax = sum(lineItems, line -> line.tax().value());
        var fulfillment = fulfillmentTotal(options, fulfillmentOptionId);
        var total = subtotal + tax + fulfillment;
        return List.of(
                new Total(Total.TotalType.ITEMS_BASE_AMOUNT, "Item(s) total", new MinorUnitAmount(baseAmount)),
                new Total(Total.TotalType.ITEMS_DISCOUNT, "Item discount", new MinorUnitAmount(discount)),
                new Total(Total.TotalType.SUBTOTAL, "Subtotal", new MinorUnitAmount(subtotal)),
                new Total(Total.TotalType.TAX, "Tax", new MinorUnitAmount(tax)),
                new Total(Total.TotalType.FULFILLMENT, "Fulfillment", new MinorUnitAmount(fulfillment)),
                new Total(Total.TotalType.TOTAL, "Total", new MinorUnitAmount(total)));
    }

    private static long sum(List<LineItem> lineItems, ToLongFunction<LineItem> mapper) {
        return lineItems.stream().mapToLong(mapper).sum();
    }

    private long fulfillmentTotal(List<FulfillmentOption> options, FulfillmentOptionId fulfillmentOptionId) {
        if (fulfillmentOptionId == null) {
            return 0L;
        }
        var target = fulfillmentOptionId.value();
        return options.stream()
                .filter(option -> option.id().equals(target))
                .findFirst()
                .map(option -> option.total().value())
                .orElse(0L);
    }

    private List<Message> messagesForStatus(CheckoutSessionStatus status) {
        if (status == CheckoutSessionStatus.CANCELED) {
            return List.of(new Message.Info(null, Message.ContentType.PLAIN, "Checkout session has been canceled."));
        }
        return List.of();
    }

    private void ensureMutable(CheckoutSession session) {
        if (session.status() == CheckoutSessionStatus.COMPLETED || session.status() == CheckoutSessionStatus.CANCELED) {
            throw new CheckoutSessionConflictException("Checkout session is immutable in status " + session.status());
        }
    }

    private String nextSessionId() {
        return "csn_%06d".formatted(sessionSequence.getAndIncrement());
    }

    private String nextOrderId() {
        return "ord_%06d".formatted(orderSequence.getAndIncrement());
    }

    private long priceForItem(String itemId) {
        return priceBook.getOrDefault(itemId, 1000L);
    }

    private static Map<String, Long> defaultPriceBook() {
        return Map.of(
                "item_123", 1500L,
                "item_456", 3000L,
                "item_789", 5000L);
    }

    private CheckoutSession createNewSession(CheckoutSessionCreateRequest request) {
        var id = new CheckoutSessionId(nextSessionId());
        var session = assemble(
                id,
                request.buyer(),
                request.fulfillmentAddress(),
                null,
                request.items(),
                CheckoutSessionStatus.READY_FOR_PAYMENT,
                null);
        sessions.put(id.value(), session);
        return session;
    }

    private CheckoutSession completeInternal(CheckoutSessionId id, CheckoutSessionCompleteRequest request) {
        return sessions.compute(id.value(), (key, current) -> {
            if (current == null) {
                throw new CheckoutSessionNotFoundException(id);
            }
            if (current.status() == CheckoutSessionStatus.CANCELED) {
                throw new CheckoutSessionConflictException("Cannot complete a canceled session");
            }
            if (current.status() == CheckoutSessionStatus.COMPLETED) {
                return current;
            }
            if (request.paymentData().provider() != PAYMENT_PROVIDER.provider()) {
                throw new CheckoutSessionConflictException("Unsupported payment provider: " + request.paymentData().provider());
            }
            var buyer = request.buyer() != null ? request.buyer() : current.buyer();
            var orderId = nextOrderId();
            var order = new Order(orderId, id, URI.create("https://merchant.example.com/orders/" + orderId));
            var updated = assemble(
                    id,
                    buyer,
                    current.fulfillmentAddress(),
                    current.fulfillmentOptionId(),
                    extractItems(current),
                    CheckoutSessionStatus.COMPLETED,
                    order);
            publishOrderCreated(updated);
            return updated;
        });
    }

    private void publishOrderCreated(CheckoutSession session) {
        var order = session.order();
        if (order == null) {
            return;
        }
        var event = new OrderWebhookEvent(
                OrderWebhookEvent.Type.ORDER_CREATE,
                session.id().value(),
                session.status().name().toLowerCase(Locale.ROOT),
                order.permalinkUrl());
        webhookPublisher.publish(event);
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null) {
            return null;
        }
        var trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record StoredCreateRequest(CheckoutSessionCreateRequest request, String sessionId) {}

    private record StoredCompleteRequest(CheckoutSessionCompleteRequest request) {}

    private record CompleteIdempotencyKey(String sessionId, String idempotencyKey) {}
}
