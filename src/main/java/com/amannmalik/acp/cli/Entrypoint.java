package com.amannmalik.acp.cli;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public final class Entrypoint {
    public Entrypoint() {
    }

    static void main(String[] args) {
        var mainSpec = CommandSpec.create().name("acp");
        mainSpec.usageMessage().description("Agentic Commerce Protocol CLI");
        var commandLine = new CommandLine(mainSpec);
        commandLine.addSubcommand("serve", new ServeCommand());
        System.exit(commandLine.execute(args));
    }
}
