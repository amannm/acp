package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

public sealed interface Message permits Message.Info, Message.Error {
    ContentType contentType();

    String content();

    enum ContentType {
        PLAIN,
        MARKDOWN
    }

    enum ErrorCode {
        MISSING,
        INVALID,
        OUT_OF_STOCK,
        PAYMENT_DECLINED,
        REQUIRES_SIGN_IN,
        REQUIRES_3DS
    }

    record Info(String param, ContentType contentType, String content) implements Message {
        public Info {
            contentType = Ensure.notNull("message.content_type", contentType);
            content = Ensure.nonBlank("message.content", content);
            if (param != null && param.isBlank()) {
                throw new IllegalArgumentException("message.param MUST be non-blank when provided");
            }
        }
    }

    record Error(
            ErrorCode code,
            String param,
            ContentType contentType,
            String content) implements Message {
        public Error {
            code = Ensure.notNull("message.code", code);
            contentType = Ensure.notNull("message.content_type", contentType);
            content = Ensure.nonBlank("message.content", content);
            if (param != null && param.isBlank()) {
                throw new IllegalArgumentException("message.param MUST be non-blank when provided");
            }
        }
    }
}
