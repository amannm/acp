package com.amannmalik.acp.cli;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.*;

public final class Entrypoint {
    public Entrypoint() {
    }

    public static void main(String[] args) {
        var mainSpec = CommandSpec.create().name("acp");
        var commandLine = new CommandLine(mainSpec);
        // TODO: subcommands
        try {
            var parseResult = commandLine.parseArgs(args);
            var helpExitCode = CommandLine.executeHelpRequest(parseResult);
            if (helpExitCode != null) {
                System.exit(helpExitCode);
            }
            System.exit(execute(parseResult));
        } catch (ParameterException e) {
            var cmd = e.getCommandLine();
            cmd.getErr().println(e.getMessage());
            if (!UnmatchedArgumentException.printSuggestions(e, cmd.getErr())) {
                cmd.usage(cmd.getErr());
            }
            System.exit(cmd.getCommandSpec().exitCodeOnInvalidInput());
        } catch (Exception e) {
            commandLine.getErr().println("ERROR: " + e.getMessage());
            System.exit(commandLine.getCommandSpec().exitCodeOnExecutionException());
        }
    }

    private static int execute(ParseResult parseResult) {
//        if (parseResult.hasSubcommand()) {
//            var subResult = parseResult.subcommand();
//            var name = subResult.commandSpec().name();
//            // TODO:
//        }
        CommandLine.usage(parseResult.commandSpec(), System.out);
        return 0;
    }
}
