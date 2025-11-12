package com.amannmalik.acp.cli.test;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import com.amannmalik.acp.cli.Entrypoint;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EntrypointTest {
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

    private static CommandLine configureTestCommandLine() {
        var commandLine = Entrypoint.commandLine();
        var err = new PrintWriter(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        commandLine.setErr(err);
        return commandLine;
    }
}
