# Security policy

## Supported version

Security updates are applied to the latest commit on `main`.

## Reporting

Do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting for this repository and include the affected route, reproduction steps, impact, and any proposed mitigation.

## Deployment notes

- Replace the default database and administrator passwords.
- Terminate TLS at a trusted reverse proxy.
- Restrict PostgreSQL to the application network.
- Keep Actuator metrics behind authentication or an internal network.
- Review authentication requirements before using the bundled public read routes outside a portfolio environment.
