# Modernization Summary (Cap-20844)

## Status
- Tooling: Java 25 + Gradle 9.1.0 updated.
- Jersey: Migrated from Jersey 1.x client integration to Jersey 2 (JAX-RS 2).
- Build: `./gradlew test` passes.

## Key Changes
- Jersey client creation moved to `javax.ws.rs.client.ClientBuilder` with the Jersey Apache connector.
- Load-balancing filter migrated to JAX-RS 2 request/response filters.
- Removed Jersey 1 internal provider dependencies by replacing them with portable implementations.
- ECS host list provider migrated to the JAX-RS 2 client API.

## Follow-ups
- Gradle emits deprecation warnings (Gradle 10 incompatibility warnings). These are not currently failing the build but should be cleaned up before a Gradle 10 upgrade.
