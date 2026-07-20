# Offline synchronization contract

## Client responsibilities

Each installation keeps a durable device UUID and a monotonically advancing `lastPulledCursor`. Every local write receives a mutation UUID before it is committed to the device database. The UUID must remain unchanged across retries.

A device sends up to 100 mutations in one batch. It must not mark a mutation complete until the API returns `APPLIED`, `CONFLICT`, or `REJECTED` for that UUID.

## Server transaction

For each mutation, the API:

1. Looks up the pair of device UUID and mutation UUID.
2. Returns the stored result when the pair already exists.
3. Applies the mutation-specific resolution rule when it is new.
4. Writes the domain change and change-log entry in the same transaction.
5. Stores the mutation result for future retries.
6. Returns ordered changes after the supplied cursor.

## Cursor behavior

The `change_log.cursor` column is a PostgreSQL sequence. Cursors are ordered within the server database and are never reused. A response can contain at most 200 changes. When `hasMore` is true, the client immediately requests another page using `nextCursor`.

The client applies a page and its new cursor in one Room transaction. If the app stops between pages, it safely requests the same page again.

## Conflict behavior

Product records use optimistic versions. A client update is accepted only when its `baseVersion` equals the current server version. A mismatch creates an immutable `sync_conflicts` record containing both payloads. The current server product is returned through the change feed.

Stock adjustments do not replace an absolute quantity. They contribute a signed delta. The mutation UUID prevents the same delta from being applied twice.

## Response statuses

| Status | Client action |
|---|---|
| `APPLIED` | Remove the queued mutation after applying returned changes |
| `CONFLICT` | Apply the server change and show the local edit as needing review |
| `REJECTED` | Keep the reason for the user and do not retry without a correction |

`replayed: true` means the server returned a result recorded by an earlier attempt.
