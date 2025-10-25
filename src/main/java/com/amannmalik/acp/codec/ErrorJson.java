package com.amannmalik.acp.codec;

import com.amannmalik.acp.api.shared.ErrorResponse;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

final class ErrorJson {
    private ErrorJson() {}

    static void write(OutputStream outputStream, ErrorResponse error) {
        writeObject(build(error), outputStream);
    }

    static JsonObjectBuilder build(ErrorResponse error) {
        var builder = Json.createObjectBuilder()
                .add("type", error.type().name().toLowerCase())
                .add("code", error.code())
                .add("message", error.message());
        if (error.param() != null) {
            builder.add("param", error.param());
        }
        return builder;
    }

    static void writeObject(JsonObjectBuilder builder, OutputStream stream) {
        try (Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            Json.createWriter(writer).write(builder.build());
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
