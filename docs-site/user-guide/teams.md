# Teams

Multi-tenancy model, team assignment, and active team selection.

## Overview

The orchestrator supports multi-team environments through the `X-Team-Id` header. Each job belongs to a specific team, and users can be members of multiple teams.

## Team Memberships

List your team memberships via `/api/teams/my-teams`. Set an active team with `/api/teams/active/{teamId}`. The active team determines which jobs are visible in the default list view.

## X-Team-Id Header

API requests include `X-Team-Id` to scope operations to a specific team. Jobs, runs, and credentials are isolated per team.
