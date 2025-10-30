package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

import java.net.URI;

public record Link(LinkType type, URI url) {
    public Link {
        type = Ensure.notNull("link.type", type);
        url = Ensure.notNull("link.url", url);
    }

    public enum LinkType {
        TERMS_OF_USE,
        PRIVACY_POLICY,
        SELLER_SHOP_POLICIES
    }
}
