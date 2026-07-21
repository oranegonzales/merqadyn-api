# Operations runbook

## Readiness

Use `/actuator/health` for load-balancer health. A healthy result means Spring started and its configured health contributors are available. Keep `/actuator/prometheus` private and authenticate it.

## Common symptoms

| Symptom | Check | Action |
| --- | --- | --- |
| App restarts | `docker compose logs --tail 200 app` | Fix the first exception; confirm required passwords and Redis/PostgreSQL health |
| HTTP 429 | Redis health and request source | Honor `Retry-After`; investigate loops or abuse before raising quotas |
| Pool timeout | Hikari metrics and PostgreSQL active sessions | Find slow queries; do not multiply pool size across replicas beyond DB capacity |
| Sync conflicts rise | Conflict table and app version | Check stale clients or a hot record; preserve audit data |
| Redis unavailable | Redis logs/latency | Restore Redis; rate limiting fails closed by design |
| Database migration fails | Flyway history and PostgreSQL logs | Stop rollout, repair the migration safely, then resume one instance first |

## Rollout

Back up PostgreSQL, apply migrations with one release instance, verify health and a device sync, then increase the new replica count gradually. Roll back application code only when its schema remains backward compatible; never delete a migration already applied to a shared database.
