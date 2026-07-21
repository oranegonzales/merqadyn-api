# Security policy

Security fixes target the latest `main`. Report vulnerabilities through GitHub Private Vulnerability Reporting; do not open a public issue containing credentials, merchant data, or exploit instructions.

## Implemented controls

- Stateless authorization uses a strong administrator credential for operator actions and random device-scoped tokens for phones.
- Enrollment codes are hashed, expire after 10 minutes, redeem once, and rotate any previous device token.
- Device tokens are hashed with SHA-256; comparisons are constant-time.
- Private routes require the device to belong to the merchant path, and a device may submit only its own sync ID.
- Request bodies, sync batches, pages, and write rates are bounded. Redis keeps quotas consistent across instances.
- Responses include restrictive CSP, frame denial, no-referrer, Permissions Policy, and HSTS headers. Private API responses are not cacheable.
- PostgreSQL is not exposed on a host port. The application container is non-root, read-only, capability-free, PID/memory-limited, and uses a private temporary filesystem.
- Detailed errors and health details are hidden from clients. Prometheus is authenticated by Spring Security.

## Production requirements

Terminate TLS at a maintained ingress, store credentials in a secret manager, restrict Redis/PostgreSQL to private networks, use multiple application instances behind a health-aware load balancer, centralize audit and authentication logs, alert on elevated 401/429/5xx rates, and rotate/revoke device credentials when phones are lost or reassigned.
