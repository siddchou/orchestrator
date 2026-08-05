package com.novakai.orch.cli.notifications;

import picocli.CommandLine;

@CommandLine.Command(
        name = "notifications",
        description = {"Manage notification subscriptions (ADMIN only)."},
        subcommands = {
                NotificationsListCommand.class
        }
)
public class NotificationsGroupCommand {
}
