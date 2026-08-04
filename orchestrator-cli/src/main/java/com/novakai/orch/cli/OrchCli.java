package com.novakai.orch.cli;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "orch",
        mixinStandardHelpOptions = true,
        version = {"orch 0.0.1-SNAPSHOT"},
        description = "CLI tool for Novakai Orchestrator",
        subcommands = {
                CommandLine.HelpCommand.class
        }
)
public class OrchCli implements Callable<Integer> {

    @Override
    public Integer call() {
        new CommandLine(this).usage(new PrintWriter(System.out));
        return 0;
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new OrchCli());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}
