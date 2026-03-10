# Migration Plan: smart-client-java

## Target Versions
- **JDK**: 25 (toolchain), targeting 17 (sourceCompatibility/targetCompatibility)
- **Gradle**: 9.2.1
- **Jersey**: 2.47
- **JUnit**: 5 (JUnit Jupiter)

## Phase 1: Build Infrastructure
1. Update Gradle wrapper from 6.9.2 to 9.2.1
2. Modernize root `build.gradle`:
   - Replace `maven` plugin with `maven-publish`
   - Replace `net.saliman.cobertura` with `jacoco`
   - Update `com.github.jk1.dependency-license-report` to compatible version
   - Update `org.ajoberstar.git-publish` to compatible version
   - Update `nebula.release` to compatible version
   - Add Java toolchain configuration (JDK 25 toolchain, target 17)
   - Remove deprecated `uploadJars`/`pom()` APIs, replace with `maven-publish` publishing
   - Fix `configurations.runtime` → `configurations.runtimeClasspath`
   - Remove deprecated `archives` configuration usage

## Phase 2: Dependency Updates (subproject build.gradle files)
3. **smart-client-core/build.gradle**:
   - JUnit 4 → JUnit 5
   - log4j 1.x → slf4j-simple (test only)
   - `useJUnit()` → `useJUnitPlatform()`
4. **smart-client-jersey/build.gradle**:
   - Jersey 1.19.4 (`com.sun.jersey`) → Jersey 2.47 (`org.glassfish.jersey`)
   - Remove `jersey-apache-client4` → add `jersey-apache-connector`
   - Update Jackson to latest 2.x compatible with Jersey 2
   - Remove Jettison constraint (not needed with Jersey 2)
   - JUnit 4 → JUnit 5
   - log4j 1.x → slf4j-simple
   - Add `jersey-hk2` for DI
   - `useJUnit()` → `useJUnitPlatform()`
5. **smart-client-ecs/build.gradle**:
   - Jersey 1.19.4 → Jersey 2.47
   - Update `commons-codec` to latest
   - Keep `javax.xml.bind:jaxb-api` + add JAXB runtime impl
   - JUnit 4 → JUnit 5
   - log4j 1.x → slf4j-simple
   - `useJUnit()` → `useJUnitPlatform()`

## Phase 3: Source Code Migration - smart-client-jersey module
6. **SmartClientFactory.java**: Rewrite for Jersey 2.x Client API
   - `com.sun.jersey.api.client.Client` → `javax.ws.rs.client.Client` + `org.glassfish.jersey.client.*`
   - `ClientHandler` → `Connector`/`ConnectorProvider`
   - `ClientConfig`/`DefaultClientConfig` → `org.glassfish.jersey.client.ClientConfig`
   - `ApacheHttpClient4` → `org.glassfish.jersey.apache.connector.ApacheConnectorProvider`
   - `ClientFilter` → `ClientRequestFilter`/`ClientResponseFilter`
   - Connection pool: `PoolingClientConnectionManager` → `PoolingHttpClientConnectionManager`
7. **SmartFilter.java**: Rewrite as Jersey 2 `ClientRequestFilter` + `ClientResponseFilter`
8. **SizeOverrideWriter.java**: Update provider imports from `com.sun.jersey.core` → Jersey 2 equivalents
9. **SizedInputStreamWriter.java**: Update `ReaderWriter` import
10. **OctetStreamXmlProvider.java**: Rewrite for Jersey 2 (no `Injectable`, no `XMLRootElementProvider`)

## Phase 4: Source Code Migration - smart-client-ecs module
11. **EcsHostListProvider.java**: Update Jersey Client API usage
    - `Client.resource()` → `Client.target()`
    - `WebResource.Builder` → `Invocation.Builder`

## Phase 5: Test Migration
12. All tests: JUnit 4 → JUnit 5
    - `org.junit.Test` → `org.junit.jupiter.api.Test`
    - `org.junit.Assert` → `org.junit.jupiter.api.Assertions`
    - `org.junit.Before/After` → `org.junit.jupiter.api.BeforeEach/AfterEach`
    - `org.junit.Assume` → `org.junit.jupiter.api.Assumptions`
    - `org.apache.log4j.Logger` → `org.slf4j.Logger`
13. Update Jersey client API calls in tests

## Phase 6: Verification
14. Compile all modules
15. Run all tests
16. Verify no CVEs introduced
17. Generate summary report

## Constraints
- **NO javax → jakarta migration** (keep javax.ws.rs, javax.xml.bind)
- Do not call JDK >17 APIs
- Keep jersey at exactly 2.47
- Keep Gradle at exactly 9.2.1
