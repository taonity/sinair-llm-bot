# Database Migrations

This project uses Flyway for database schema management.

## Location

SQL migrations live in `templates/docker/flyway/sql/tables/`.

## Naming Convention

```
V{version}__{description}.sql
```

- Version numbers start at `100000` and increment
- Use double underscore `__` between version and description
- Description uses snake_case

Examples:
```
V100000__create_user_table.sql
V100001__add_profile_columns.sql
V100002__create_orders_table.sql
```

## Local Development (H2)

When running with the `h2` profile, Flyway reads migrations from `filesystem:templates/docker/flyway/sql/tables/`. The database is automatically cleaned and rebuilt on each start (via `flyway-clean-migrate` profile).

Run from the project root:
```bash
mvn -pl backend spring-boot:run '-Dspring-boot.run.jvmArguments="-Dspring.profiles.active=h2,stub-google,local"'
```

## Production (PostgreSQL)

In production, Flyway runs as a separate Docker container before the app starts (see `templates/docker/flyway/docker-compose.yml`). Migrations are never rolled back — write forward-only migrations.

## Writing Migrations

### Create a table
```sql
-- V100001__create_orders_table.sql
CREATE TABLE orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     VARCHAR NOT NULL REFERENCES app_user(google_id),
    total       DECIMAL(10,2) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
```

### Add a column
```sql
-- V100002__add_order_status.sql
ALTER TABLE orders ADD COLUMN status VARCHAR NOT NULL DEFAULT 'pending';
```

### H2 Compatibility

H2 is mostly PostgreSQL-compatible. If you need H2-specific SQL, create a `flyway/sql/conflicts/h2/` directory and add the H2 variant there. Configure in `application-h2.yaml`:

```yaml
spring:
  flyway:
    locations:
      - filesystem:templates/docker/flyway/sql/tables
      - filesystem:templates/docker/flyway/sql/conflicts/h2
```

## Tips

- Keep migrations small and focused
- Never modify an existing migration that's been applied in production
- Test migrations locally with H2 before deploying
- Use `flyway.conf` for Flyway container configuration
