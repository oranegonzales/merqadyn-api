# Contributing

## Local checks

Use Java 21 and run:

```shell
./gradlew clean test bootJar --no-build-cache
docker compose config --quiet
```

## Change guidelines

- Keep every write retry-safe.
- Add a Flyway migration instead of modifying an applied migration.
- Preserve backward compatibility in the sync response.
- Add an integration test for every new mutation type or conflict rule.
- Do not commit credentials, local environment files, database volumes, or build output.

Open changes from a focused branch and describe the user impact and verification in the pull request.
