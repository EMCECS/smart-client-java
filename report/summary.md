# Migration Summary: Java 25 + Gradle 9.2.1 + Jersey 2.47

## Overview
Successfully migrated the `smart-client-java` project from **Java 8 / Gradle 6.9.2 / Jersey 1.19.4** to **Java 25 / Gradle 9.2.1 / Jersey 2.47**.

## Build System Changes

### Gradle Wrapper
- Updated `gradle-wrapper.properties` distribution URL from `gradle-6.9.2-bin.zip` to `gradle-9.2.1-bin.zip`

### Root `build.gradle`
- Replaced `cobertura` plugin with `jacoco`
- Updated `nebula.release` plugin to `21.0.0`
- Updated `org.ajoberstar.git-publish` plugin to `5.1.2`
- Replaced deprecated `maven` plugin with `maven-publish`
- Updated `sourceCompatibility` from Java 8 to Java 25
- Replaced deprecated `testCompile`/`compile` configurations with `testImplementation`/`implementation`

### Subproject `build.gradle` Files

#### smart-client-core
- `slf4j-api` → `2.0.16`
- `junit` → `junit-jupiter:5.11.0` (JUnit 5)
- Added `junit-platform-launcher` (required by Gradle 9.x)
- Added `logback-classic:1.5.12` for test logging
- Added `useJUnitPlatform()`

#### smart-client-jersey
- Replaced Jersey 1.x dependencies with Jersey 2.47:
  - `jersey-client:1.19.4` → `jersey-client:2.47`
  - `jersey-apache-client4:1.19.4` → `jersey-apache-connector:2.47`
  - `jersey-json:1.19.4` → removed (replaced by Jackson + JAXB providers)
- Added `jersey-hk2:2.47` (DI framework)
- Added `jersey-media-jaxb:2.47` (JAXB XML support)
- Updated Jackson to `2.17.2`
- Added explicit JAXB dependencies (`jaxb-api:2.3.1`, `jaxb-runtime:2.3.9`)
- JUnit 5 + `junit-platform-launcher` + `logback-classic`

#### smart-client-ecs
- Replaced `jersey-client:1.19.4` → `jersey-client:2.47`
- Updated `commons-codec` to `1.17.1`
- Added explicit JAXB dependencies
- JUnit 5 + `junit-platform-launcher` + `logback-classic`
- Added test dependencies: `jersey-apache-connector:2.47`, `jersey-hk2:2.47`, `httpclient:4.5.14`

## Source Code Migration

### SmartFilter.java (smart-client-jersey)
- **Before:** Extended Jersey 1 `ClientFilter` with `handle(ClientRequest)` pattern
- **After:** Implements Jersey 2 `Connector` interface wrapping a delegate connector
- Preserves around-advice semantics for load balancing (host selection, URI rewriting, error tracking)

### SmartClientFactory.java (smart-client-jersey)
- **Before:** Used Jersey 1 `ApacheHttpClient4.create()`, `DefaultClientConfig`, `ClientHandler`
- **After:** Uses Jersey 2 `ClientBuilder`, `ClientConfig`, `ApacheConnectorProvider`
- Uses `PoolingHttpClientConnectionManager` (replaces deprecated `PoolingClientConnectionManager`)
- Registers `SmartFilter` as a connector wrapper via custom `ConnectorProvider`
- Manages `PollingDaemon` lifecycle and connection manager via scheduled executor

### SizeOverrideWriter.java (smart-client-jersey)
- Replaced Jersey 1 internal provider classes (`ByteArrayProvider`, `FileProvider`, `InputStreamProvider`) with standalone `MessageBodyWriter` implementations using Jersey 2's `ReaderWriter`

### SizedInputStreamWriter.java (smart-client-jersey)
- Updated `ReaderWriter` import from `com.sun.jersey.core.util` to `org.glassfish.jersey.message.internal`

### OctetStreamXmlProvider.java (smart-client-jersey)
- Replaced Jersey 1 `XMLRootElementProvider.App` with direct JAXB marshalling/unmarshalling using `JAXBContext`

### EcsHostListProvider.java (smart-client-ecs)
- `com.sun.jersey.api.client.Client` → `javax.ws.rs.client.Client`
- `client.resource(uri)` → `client.target(uri).request()`
- `WebResource.Builder` → `Invocation.Builder`
- `client.destroy()` → `client.close()`

## Test Migration

All test files migrated from JUnit 4 to JUnit 5:

| Change | JUnit 4 | JUnit 5 |
|--------|---------|---------|
| Import | `org.junit.Test` | `org.junit.jupiter.api.Test` |
| Assertions | `Assert.assertEquals(msg, exp, act)` | `Assertions.assertEquals(exp, act, msg)` |
| Assumptions | `Assume.assumeTrue(msg, cond)` | `Assumptions.assumeTrue(cond, msg)` |
| Lifecycle | `@Before` / `@After` | `@BeforeEach` / `@AfterEach` |
| Logging | `org.apache.log4j.Logger` | `org.slf4j.Logger` + `LoggerFactory` |

### Files Migrated
- `smart-client-core`: `HostTest`, `LoadBalancerTest`, `TestHealthCheck`
- `smart-client-jersey`: `SmartClientTest`, `RewriteURITest`, `TestConfig`
- `smart-client-ecs`: `EcsHostListProviderTest`, `ListDataNodeTest`, `TestConfig`

### Jersey 2 API Changes in Tests
- `client.resource(uri)` → `client.target(uri).request()`
- `ClientResponse` → `javax.ws.rs.core.Response`
- `response.getEntity(Class)` → `response.readEntity(Class)`
- `ClientHandlerException` → `ProcessingException`
- `ApacheHttpClient4Config.PROPERTY_HTTP_PARAMS` → `ClientProperties.CONNECT_TIMEOUT`

## Dependency Version Summary

| Dependency | Old Version | New Version |
|------------|------------|-------------|
| Java | 8 | 25 |
| Gradle | 6.9.2 | 9.2.1 |
| Jersey | 1.19.4 | 2.47 |
| JUnit | 4.x | 5.11.0 |
| SLF4J | 1.x | 2.0.16 |
| Jackson | 1.x (via Jersey) | 2.17.2 |
| Apache HttpClient | 4.x (managed by Jersey) | 4.5.14 |
| Commons Codec | 1.x | 1.17.1 |
| JAXB API | (in JDK) | 2.3.1 (explicit) |
| JAXB Runtime | (in JDK) | 2.3.9 (explicit) |
| Logback | — | 1.5.12 (test) |

## Build Verification
- ✅ `compileJava` — all main sources compile
- ✅ `compileTestJava` — all test sources compile
- ✅ `test` — BUILD SUCCESSFUL (all tests pass)

## Notes
- `javax` packages were **not** migrated to `jakarta` (per requirements)
- Remaining IDE lint warnings are style suggestions from the original code (e.g., `instanceof` pattern, `size() > 0` → `!isEmpty()`) — not introduced by the migration
