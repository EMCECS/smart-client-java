# Migration Summary: Java 25 + Gradle 9.2.1 + Jersey 3.1.11

## Overview
Successfully migrated the `smart-client-java` project from **Java 8 / Gradle 6.9.2 / Jersey 1.19.4** to **Java 25 / Gradle 9.2.1 / Jersey 3.1.11** in two phases:
- **Phase A** (PR #38, commit `a4ea62c`): Jersey 1.19.4 → 2.47 (`javax.ws.rs`), JAXB `javax.xml.bind:jaxb-api:2.3.1`, `jaxb-runtime:2.3.9`
- **Phase B** (commit `225ba92`): Jersey 2.47 → 3.1.11 (`jakarta.ws.rs`), JAXB `jakarta.xml.bind:jakarta.xml.bind-api:4.0.0`, `jaxb-runtime:4.0.2`

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
- Replaced Jersey 1.x dependencies in two steps:
  - Phase A: `jersey-client:1.19.4` → `jersey-client:2.47` (`javax.ws.rs`)
  - Phase B: `jersey-client:2.47` → `jersey-client:3.1.11` (`jakarta.ws.rs`)
- Similarly: `jersey-apache-client4:1.19.4` → `jersey-apache-connector:2.47` → `3.1.11`
- `jersey-json:1.19.4` → removed (replaced by Jackson + JAXB providers)
- Added `jersey-hk2:3.1.11` (DI framework)
- Added `jersey-media-jaxb:3.1.11` (JAXB XML support)
- Updated Jackson to `2.17.2`
- JAXB dependencies: Phase A added `javax.xml.bind:jaxb-api:2.3.1` + `jaxb-runtime:2.3.9`, Phase B migrated to `jakarta.xml.bind-api:4.0.0` + `jaxb-runtime:4.0.2`
- Migrated all imports from `javax.ws.rs` → `jakarta.ws.rs`
- JUnit 5 + `junit-platform-launcher` + `logback-classic`

#### smart-client-ecs
- Replaced `jersey-client:1.19.4` → `2.47` → `3.1.11`
- Updated `commons-codec` to `1.17.1`
- JAXB dependencies: Phase A added `javax.xml.bind:jaxb-api:2.3.1` + `jaxb-runtime:2.3.9`, Phase B migrated to `jakarta.xml.bind-api:4.0.0` + `jaxb-runtime:4.0.2`
- Migrated all imports from `javax.xml.bind` → `jakarta.xml.bind`
- JUnit 5 + `junit-platform-launcher` + `logback-classic`
- Added test dependencies: `jersey-apache-connector:3.1.11`, `jersey-hk2:3.1.11`, `httpclient:4.5.14`

## Source Code Migration

### SmartFilter.java (smart-client-jersey)
- **Before:** Extended Jersey 1 `ClientFilter` with `handle(ClientRequest)` pattern
- **After:** Implements Jersey 3 `Connector` interface wrapping a delegate connector
- Preserves around-advice semantics for load balancing (host selection, URI rewriting, error tracking)

### SmartClientFactory.java (smart-client-jersey)
- **Before:** Used Jersey 1 `ApacheHttpClient4.create()`, `DefaultClientConfig`, `ClientHandler`
- **After:** Uses Jersey 3 `ClientBuilder`, `ClientConfig`, `ApacheConnectorProvider`
- Uses `PoolingHttpClientConnectionManager` (replaces deprecated `PoolingClientConnectionManager`)
- Registers `SmartFilter` as a connector wrapper via custom `ConnectorProvider`
- Manages `PollingDaemon` lifecycle and connection manager via scheduled executor

### SizeOverrideWriter.java (smart-client-jersey)
- Replaced Jersey 1 internal provider classes (`ByteArrayProvider`, `FileProvider`, `InputStreamProvider`) with standalone `MessageBodyWriter` implementations using Jersey 3's `ReaderWriter`

### SizedInputStreamWriter.java (smart-client-jersey)
- Updated `ReaderWriter` import from `com.sun.jersey.core.util` to `org.glassfish.jersey.message.internal`

### OctetStreamXmlProvider.java (smart-client-jersey)
- Replaced Jersey 1 `XMLRootElementProvider.App` with direct JAXB marshalling/unmarshalling using `jakarta.xml.bind.JAXBContext`

### EcsHostListProvider.java (smart-client-ecs)
- `com.sun.jersey.api.client.Client` → `jakarta.ws.rs.client.Client`
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

### Jersey 3 API Changes in Tests
- `client.resource(uri)` → `client.target(uri).request()`
- `ClientResponse` → `jakarta.ws.rs.core.Response`
- `response.getEntity(Class)` → `response.readEntity(Class)`
- `ClientHandlerException` → `jakarta.ws.rs.ProcessingException`
- `ApacheHttpClient4Config.PROPERTY_HTTP_PARAMS` → `ClientProperties.CONNECT_TIMEOUT`

## Dependency Version Summary

| Dependency | Old Version | Phase A (PR #38) | Phase B (Current) |
|------------|------------|------------------|-------------------|
| Java | 8 | 25 | 25 |
| Gradle | 6.9.2 | 9.2.1 | 9.2.1 |
| Jersey | 1.19.4 | 2.47 | 3.1.11 |
| Namespace | `javax.ws.rs` | `javax.ws.rs` | `jakarta.ws.rs` |
| JUnit | 4.x | 5.11.0 | 5.11.0 |
| SLF4J | 1.x | 2.0.16 | 2.0.16 |
| Jackson | 1.x (via Jersey) | 2.17.2 | 2.17.2 |
| Apache HttpClient | 4.x (managed by Jersey) | 4.5.14 | 4.5.14 |
| Commons Codec | 1.x | 1.17.1 | 1.17.1 |
| JAXB API | `javax.xml.bind` (in JDK) | `javax.xml.bind:jaxb-api:2.3.1` | `jakarta.xml.bind:jakarta.xml.bind-api:4.0.0` |
| JAXB Runtime | (in JDK) | `jaxb-runtime:2.3.9` | `jaxb-runtime:4.0.2` |
| Logback | — | 1.5.12 (test) | 1.5.12 (test) |

## Build Verification
- ✅ `compileJava` — all main sources compile
- ✅ `compileTestJava` — all test sources compile
- ✅ `test` — BUILD SUCCESSFUL (all tests pass)
- ✅ `testReport` — aggregated HTML report generated across all subprojects

## Aggregated Test Report

A `testReport` task was added to the root `build.gradle` to generate a single HTML report merging results from all three submodules:

```groovy
tasks.register('testReport', TestReport) {
    destinationDirectory = file("${layout.buildDirectory.get().asFile}/reports/allTests")
    testResults.from(subprojects*.test)
}
tasks.testReport.dependsOn subprojects.test
```

Run with:
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean test testReport
```

Report location: `build/reports/allTests/index.html`

### Test Results (37 tests, 0 failures, 0 ignored)

| Module | Test Class | Tests | Type |
|--------|-----------|-------|------|
| smart-client-core | HostTest | 2 | Unit |
| smart-client-core | LoadBalancerTest | 2 | Unit |
| smart-client-core | TestHealthCheck | 2 | Unit |
| smart-client-jersey | RewriteURITest | 1 | Unit |
| smart-client-jersey | SmartFilterTest | 19 | Unit |
| smart-client-jersey | SmartClientTest | 3 | Integration/E2E |
| smart-client-ecs | ListDataNodeTest | 1 | Unit |
| smart-client-ecs | EcsHostListProviderTest | 7 | Integration/E2E |
| **Total** | | **37** | |

## Notes
- All `javax.ws.rs` packages migrated to `jakarta.ws.rs` (required by Jersey 3.x)
- All `javax.xml.bind` packages migrated to `jakarta.xml.bind` (required by JAXB 4.x)
- Zero remaining `javax.ws.rs` or `javax.xml.bind` references in the codebase
- Remaining IDE lint warnings are style suggestions from the original code (e.g., `instanceof` pattern, `size() > 0` → `!isEmpty()`) — not introduced by the migration
