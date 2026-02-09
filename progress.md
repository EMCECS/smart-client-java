# Progress Log

## 2026-02-09
- Updated Gradle wrapper to 9.1.0 and modernized root `build.gradle` for Gradle 9 / Java 25 toolchains.
- Updated module dependencies:
  - `smart-client-jersey`: Jersey 2 client + Apache connector + Jersey JSON/JAXB media modules; explicit `javax.ws.rs-api`.
  - `smart-client-ecs`: Jersey 2 client; JAXB API/runtime for Java 25; explicit `javax.ws.rs-api`.
- Refactored Jersey integration code:
  - `SmartFilter` migrated from Jersey 1 `ClientFilter` to JAX-RS 2 `ClientRequestFilter`/`ClientResponseFilter`.
  - `SmartClientFactory` migrated to Jersey 2 `ClientBuilder` + `ApacheConnectorProvider`, and registers features/providers.
  - Replaced Jersey 1 internal providers/utilities:
    - `SizedInputStreamWriter` now streams directly (no Jersey `ReaderWriter`).
    - `SizeOverrideWriter` now uses portable `MessageBodyWriter` delegates.
    - `OctetStreamXmlProvider` now uses JAXB unmarshalling.
- Refactored ECS integration:
  - `EcsHostListProvider` migrated from Jersey 1 client API to JAX-RS 2 (`Client.target(...)`, `Invocation.Builder`).
- Updated tests:
  - `SmartClientTest` migrated to JAX-RS 2 client API, using Jersey 2 timeout properties and `Response`/`Entity`.
  - `EcsHostListProviderTest` migrated to JAX-RS 2 client API; uses `SmartClientFactory` for client creation.
- Verification:
  - `./gradlew test` succeeded.
  - Re-ran `./gradlew test` after SmartFilter fix; build remains green.
