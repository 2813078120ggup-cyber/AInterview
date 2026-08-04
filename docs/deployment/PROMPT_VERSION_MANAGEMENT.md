# Prompt Version Management

The platform manages business prompts as immutable database versions. Java
code references stable prompt codes and supplies declared variables; it does
not contain the active prompt body.

## Managed Prompt Groups

| Group | Prompt codes |
| --- | --- |
| Simulated interview | `simulation.opening`, `simulation.follow_up` |
| Free interview | `free_interview.follow_up` |
| Resume analysis | `resume.analysis` |
| Reports and scoring | `report.answer_evaluation`, `report.simulation_summary`, `report.free_summary` |

Default v1 templates are packaged in
`backend/src/main/resources/prompts/defaults/`. On the first application start,
they are inserted only when a prompt code has no existing version. Subsequent
application releases never overwrite database-managed prompt versions.

## Administrator Workflow

Open **提示词版本** in the administrator sidebar.

1. Select a prompt and review its active version, available variables, full
   version list, and activation history.
2. Choose **新建版本**. The editor starts from the current active content.
3. Enter a change note and save. Enabling **创建后立即激活** makes new AI
   requests use that version immediately; no backend restart is required.
4. Use **激活** for a newer inactive version or **回滚到此版本** for an older
   version. Both operations write an activation log.

Version content is never updated or deleted through the API. A correction is a
new version. This preserves an auditable record and makes rollback predictable.

## Database Tables

- `ai_prompt_version`: immutable template content, version number, active flag,
  change note, creator, and activation timestamp.
- `ai_prompt_activation_log`: initial activation, later activation, and rollback
  records with operator and timestamp.

Only one active version is allowed by the service for each prompt code. Runtime
rendering fails closed if a prompt has zero or multiple active versions, so a
configuration defect cannot silently use an arbitrary prompt.

## API

All endpoints require the `ADMIN` role:

```text
GET  /api/v1/admin/prompt-templates
GET  /api/v1/admin/prompt-templates/{code}
POST /api/v1/admin/prompt-templates/{code}/versions
POST /api/v1/admin/prompt-templates/{code}/versions/{version}/activate
POST /api/v1/admin/prompt-templates/{code}/versions/{version}/rollback
```

Templates use `${variableName}` placeholders. The administrator UI lists the
allowed variables for each prompt, and the backend rejects undeclared
placeholders when a version is created.
