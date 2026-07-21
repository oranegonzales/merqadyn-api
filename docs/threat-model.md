# Threat model

| Risk | Control | Remaining concern |
| --- | --- | --- |
| Admin password extracted from mobile build | Phone uses one-time enrollment and device token | Operator workstation must remain trusted |
| Enrollment brute force | Non-ambiguous 10-character random code, 10-minute expiry, one-time use, Redis quota | Internet-facing deployments should add ingress/WAF abuse controls |
| Cross-merchant device access | Device ID/token are checked against merchant path | Lost phones require credential rotation or revocation |
| Replayed business write | Durable mutation ID ledger | Concurrent first attempts can transiently retry around a uniqueness race |
| Resource exhaustion | Request, batch, page, header, pool, PID, memory, and rate limits | Limits require load-test tuning per deployment |
| Browser data leakage | Public DTO removes internal IDs/timestamps; private data requires auth and `no-store` | The public demonstration inventory remains intentionally visible |
| Container escape or writable persistence | Non-root user, read-only root filesystem, dropped capabilities, private `/tmp` | Host/container runtime patching remains operational work |
| Database/Redis exposure | Compose keeps data services on the internal network | Production network policy and secret management are still required |
