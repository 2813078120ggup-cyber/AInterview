# Backend

## Start dependencies

From the repository root, start MySQL and Redis with `docker compose up -d`. On an empty MySQL volume, Docker runs `docs/database/docker-init/01-ai-interview-init.sql`; when the backend starts, Flyway validates the schema and applies any unapplied runtime migrations from `src/main/resources/db/migration`.

## Start the service

Set `JWT_SECRET` to a value of at least 32 characters, then run:

```powershell
$env:JWT_SECRET = 'replace-with-a-secret-at-least-32-characters'
cd backend
mvn spring-boot:run
```

The API base URL is `http://localhost:8080/api/v1`. Registering an account assigns the `CANDIDATE` role. Assign `HR` or `INTERVIEWER` roles directly in `user_role` for initial local testing.
