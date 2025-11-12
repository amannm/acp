package com.amannmalik.acp.cli.test;

import com.amannmalik.acp.cli.Entrypoint;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EntrypointTest {
    private static CommandLine configureTestCommandLine() {
        var commandLine = Entrypoint.commandLine();
        var err = new PrintWriter(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        commandLine.setErr(err);
        return commandLine;
    }

    @Test
    void helpOptionIsAvailable() {
        var cli = configureTestCommandLine();
        var stdout = new ByteArrayOutputStream();
        cli.setOut(new PrintWriter(stdout, true));
        var exitCode = cli.execute("--help");
        assertEquals(0, exitCode);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Usage: acp"));
    }

    @Test
    void versionOptionUsesManifestVersionOrFallback() {
        var cli = configureTestCommandLine();
        var stdout = new ByteArrayOutputStream();
        cli.setOut(new PrintWriter(stdout, true));
        var exitCode = cli.execute("--version");
        assertEquals(0, exitCode);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).toLowerCase().contains("acp"));
    }
}
