# Import / Export

This page documents the job export and import formats, version management during import, and rollback procedures.

## Export Format

The JSON and YAML schemas used when exporting jobs, including metadata fields, step definitions, environment variables, and schedule configuration.

## Import Modes

Explanation of import modes (`CREATE` for new jobs, `UPDATE` for modifying existing ones), conflict resolution strategies, and validation rules applied during import.

## Version Snapshot on Import

How importing a job creates a version snapshot in the version history, enabling rollback to the pre-import state if needed.
