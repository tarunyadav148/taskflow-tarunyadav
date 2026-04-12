# TaskFlow

## Overview

TaskFlow is a backend for a task management app — register/login, create projects, manage tasks with statuses and assignees. Standard CRUD stuff but with proper auth, validation, and relational data.

I built this with Java 21 + Spring Boot 3.4, PostgreSQL 15, Flyway for migrations, JWT for auth (jjwt library, bcrypt cost 12). Logging is structured JSON via logstash-logback-encoder. Docker with multi-stage builds. Tests use Testcontainers.

## Architecture Decisions

I used Java/Spring instead of Go — the spec allows it and I'm significantly more productive here. Didn't want to spend half the time fighting a language I'm less comfortable with.

Flyway handles migrations and runs them on startup automatically. One catch: Flyway's free tier doesn't support down migrations natively. I've put rollback SQL in `db/rollback/` — not ideal but it works. If I were doing this again I'd probably just use dbmate.

I keep entities and DTOs separate. It's more files but I've been bitten before by accidentally serializing password hashes or triggering lazy loads during JSON serialization. `open-in-view` is off for the same reason — I'd rather write explicit `JOIN FETCH` queries than debug weird Hibernate behavior.

I added a `created_by` column on tasks that isn't in the original spec schema. Needed it because the delete rule says "project owner or task creator" — can't check the second condition without tracking it.

Status and priority are plain strings in Java, not enums. The DB has CHECK constraints so bad values can't sneak in, and the service layer validates too. Avoided Java enums because Hibernate's enum mapping is annoying and I didn't want to deal with it for a take-home.

UUIDs for all primary keys instead of auto-increment longs. The spec requires it, but I'd have picked UUIDs anyway — they're safe to generate client-side, don't leak row counts, and make life easier if you ever need to merge data across environments. The tradeoff is they're bigger (16 bytes vs 8) and slightly slower for index lookups, but for this scale it's irrelevant.

`ON DELETE CASCADE` on projects → tasks and users → projects. Means deleting a project wipes its tasks at the DB level — I don't have to manually delete tasks first in application code. Simpler, and it's impossible to end up with orphaned tasks. Downside is you can accidentally nuke a lot of data with one delete, but for this use case that's the intended behavior.

`ON DELETE SET NULL` on `assignee_id`. If a user gets deleted, their assigned tasks don't disappear — the assignee just becomes null. Felt more correct than cascading (you'd lose tasks that belong to a project someone else owns) or restricting (you'd have to unassign every task before deleting a user).

The `updated_at` trigger lives in Postgres, not just in JPA's `@PreUpdate`. I have both, but the DB trigger is the real safety net — it fires even if something updates a task through raw SQL or a migration. JPA's `@PreUpdate` only works when Hibernate is in the picture.

I also implemented the three bonus features:
- Pagination (`?page=&limit=`) on both list endpoints
- `GET /projects/:id/stats` for task counts by status/assignee
- 10 integration tests with Testcontainers against a real Postgres

## Running Locally

```bash
git clone https://github.com/tarunsinghyadav/taskflow-tarunyadav
cd taskflow-tarunyadav
cp .env.example .env
# put a real secret in JWT_SECRET — generate one with: openssl rand -base64 32
docker compose up --build
```

API comes up at `http://localhost:8080`. Postgres starts first, the API waits for it to be healthy, then Flyway runs migrations and seeds data. Nothing manual.

## Running Migrations

They run on startup. Flyway picks up files from `backend/src/main/resources/db/migration/`:

- `V1__create_schema.sql` — creates tables, indexes, the updated_at trigger
- `V2__seed_data.sql` — inserts the test user, a project, and 3 tasks

Rollback files are in `db/rollback/`. To undo:
```bash
docker exec -i taskflow-db psql -U postgres -d taskflow < backend/src/main/resources/db/rollback/V2__remove_seed_data.sql
docker exec -i taskflow-db psql -U postgres -d taskflow < backend/src/main/resources/db/rollback/V1__drop_schema.sql
```

## Test Credentials

```
Email:    test@example.com
Password: password123
```

That gives you 1 project ("Website Redesign") with 3 tasks — one todo, one in_progress, one done.

To run the integration tests (needs Docker for Testcontainers):
```bash
cd backend
./mvnw test
```

## API Reference

Base URL: `http://localhost:8080`

Swagger UI is at `http://localhost:8080/swagger-ui.html` — you can test all endpoints directly from the browser. Login first, then click "Authorize" and paste the JWT token.

Everything except `/auth/*` needs a Bearer token.

### Auth

**POST /auth/register**
```json
// request
{ "name": "Jane Doe", "email": "jane@example.com", "password": "secret123" }
// 201
{ "token": "eyJ...", "user": { "id": "uuid", "name": "Jane Doe", "email": "jane@example.com" } }
```

**POST /auth/login** — same request shape minus `name`, same response shape.

### Projects

**GET /projects?page=1&limit=20** — returns projects you own or are assigned tasks in.
```json
{ "data": [{ "id": "...", "name": "...", "ownerId": "...", "createdAt": "..." }], "page": 1, "limit": 20, "total": 1, "totalPages": 1 }
```

**POST /projects** — `{ "name": "...", "description": "..." }` → 201

**GET /projects/:id** — returns the project with its tasks embedded.

**PATCH /projects/:id** — owner only. Send whichever fields you want to change.

**DELETE /projects/:id** — owner only, cascades to tasks. → 204

**GET /projects/:id/stats** — bonus endpoint.
```json
{ "totalTasks": 3, "byStatus": { "todo": 1, "in_progress": 1, "done": 1 }, "byAssignee": { "Test Admin": 2, "Unassigned": 1 } }
```

### Tasks

**GET /projects/:id/tasks?status=todo&assignee=uuid&page=1&limit=20**

**POST /projects/:id/tasks** — `{ "title": "...", "priority": "high", "assigneeId": "uuid", "dueDate": "2026-04-15" }` → 201. Status defaults to `todo`.

**PATCH /tasks/:id** — send any subset of fields. → 200

**DELETE /tasks/:id** — project owner or task creator. → 204

### Users

**GET /users** — lists all users. Mainly exists so you can get UUIDs for the assignee field.

### Errors

All errors follow this shape:
```
400  { "error": "validation failed", "fields": { "email": "is required" } }
401  { "error": "unauthorized" }
403  { "error": "forbidden" }
404  { "error": "not found" }
```

## What I'd Do With More Time

The down migrations situation bugs me. Flyway CE just doesn't support it and I didn't want to bring in a second tool. dbmate would've been the right call from the start.

Beyond that:
- Rate limiting on auth endpoints. There's nothing stopping someone from hammering `/auth/login` right now.
- Refresh tokens. A single 24h access token works for a demo but I wouldn't ship it.
- My tests share state through static fields which makes them order-dependent. Not great. I'd switch to `@Sql` for setup/teardown.
- Some kind of audit log — even just a table that records who changed what.