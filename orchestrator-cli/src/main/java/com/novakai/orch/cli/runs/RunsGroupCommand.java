package com.novakai.orch.cli.runs;

import picocli.CommandLine;

@CommandLine.Command(
        name = "runs",
        description = {"Manage job runs."},
        subcommands = {
                RunsListCommand.class,
                RunsTailCommand.class
        }
)
public class RunsGroupCommand {
}
