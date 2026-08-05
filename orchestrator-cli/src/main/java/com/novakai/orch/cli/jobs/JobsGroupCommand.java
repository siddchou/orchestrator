package com.novakai.orch.cli.jobs;

import picocli.CommandLine;

@CommandLine.Command(
        name = "jobs",
        description = {"Manage job definitions."},
        subcommands = {
                JobsListCommand.class,
                JobsRunCommand.class,
                JobsExportCommand.class,
                JobsImportCommand.class
        }
)
public class JobsGroupCommand {
}
