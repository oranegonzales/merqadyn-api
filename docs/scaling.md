# Scaling

Merqadyn application instances are stateless. Durable state lives in PostgreSQL and distributed request quotas live in Redis, so multiple instances can sit behind a load balancer without sticky sessions.

## Implemented limits and access paths

| Concern | Implementation |
| --- | --- |
| Large catalogs | Slice pagination capped at 200 rows |
| Large sync queues | Upload batches capped at 100; change pulls capped at 200 |
| Expensive public reads | SQL aggregates, bounded detail samples, 30-second shared-cache header |
| Database pressure | Configurable Hikari pool with short acquisition/validation timeouts |
| Request floods | Redis-backed atomic per-minute write and enrollment quotas |
| Payload memory | 512 KiB request-body limit and 16 KiB header limit |
| Query latency | Composite merchant/sort indexes for products, inventory, devices, mutations, and changes |
| Retry duplication | Durable unique `(device_id, mutation_id)` ledger |
| Instance overload | Container memory/PID limits, health probes, and virtual threads |

## Capacity planning

Set `DATABASE_POOL_MAX_SIZE` so the sum across all replicas stays below PostgreSQL's connection budget, leaving room for migrations and operations. Scale application replicas on request latency and CPU; scale PostgreSQL and Redis from their own saturation, latency, and failover signals.

Load tests should model many merchants, bursty reconnects, duplicate retries, hot inventory rows, and slow clients. Track p50/p95/p99 latency, pool wait time, transaction retries, Redis latency, sync queue age, 429/5xx rate, and change-log growth. Partition or archive the append-only change and processed-mutation tables when measured retention volume justifies it.
