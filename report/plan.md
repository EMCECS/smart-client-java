# Migration Plan: Java 25, Gradle 9.2.1, Jersey 3.1.11

## Phase 1: Build System
1. Update Gradle wrapper from 6.9.2 → 9.2.1
2. Update root `build.gradle`:
   - Java `sourceCompatibility` 1.8 → 25
   - Replace `maven` plugin with `maven-publish`
   - Replace `net.saliman.cobertura` with `jacoco`
   - Update `com.github.jk1.dependency-license-report` 1.17 → 3.0.1
   - Update `org.ajoberstar.git-publish` 3.0.1 → 5.1.2
   - Update `nebula.release` 15.3.1 → 21.0.0
   - Replace `uploadJars`/`mavenDeployer` with `maven-publish` publishing
   - Fix deprecated Gradle APIs (`configurations.runtime`, `pom()`, etc.)
3. Update subproject `build.gradle` files:
   - Jersey 1.19.4 → Jersey 3.1.11 (`org.glassfish.jersey.*`)
   - JUnit 4.13.2 → JUnit 5.11.0
   - Log4j 1.2.17 → Logback 1.5.12 (test logging)
   - SLF4J 1.7.36 → 2.0.16
   - Jackson 2.12.7 → 2.17.2
   - Apache HttpClient 4.5.13 → 4.5.14
   - Add Jakarta JAXB runtime (`jakarta.xml.bind-api:4.0.0`, `jaxb-runtime:4.0.2`)
   - Add `jersey-hk2` for DI
   - Migrate `javax.ws.rs` → `jakarta.ws.rs`, `javax.xml.bind` → `jakarta.xml.bind`

## Phase 2: Source Code Migration
4. Migrate `smart-client-jersey` main sources:
   - `SmartFilter`: Rewrite from `ClientFilter` to Jersey 3 `Connector` wrapper
   - `SmartClientFactory`: Rewrite for Jersey 3 client creation API
   - `SizeOverrideWriter`: Remove Jersey 1 internal provider deps, use standalone impls
   - `SizedInputStreamWriter`: Update `ReaderWriter` import
   - `OctetStreamXmlProvider`: Remove Jersey 1 deps, use JAXB directly
5. Migrate `smart-client-ecs` main sources:
   - `EcsHostListProvider`: Update Jersey client API (`Client`, `WebResource` → `WebTarget`)

## Phase 3: Test Migration
6. Migrate all tests to JUnit 5:
   - `org.junit.Test` → `org.junit.jupiter.api.Test`
   - `org.junit.Assert` → `org.junit.jupiter.api.Assertions` (note: arg order changes)
   - `org.junit.Assume` → `org.junit.jupiter.api.Assumptions`
   - `@Before/@After` → `@BeforeEach/@AfterEach`
7. Migrate test Jersey API usage to Jersey 3 (`jakarta.ws.rs`)
8. Replace log4j 1.x usage in tests with SLF4J + Logback

## Phase 4: Verification
9. Build the project: `./gradlew build`
10. Fix any compilation errors
11. Run tests: `./gradlew test`
12. Generate summary report

## Dependency Mapping
| Old (Jersey 1.x) | Phase A (Jersey 2.x) | Phase B / Current (Jersey 3.x) |
|-----|-----|-----|
| `com.sun.jersey:jersey-client:1.19.4` | `org.glassfish.jersey.core:jersey-client:2.47` | `jersey-client:3.1.11` |
| `com.sun.jersey.contribs:jersey-apache-client4:1.19.4` | `jersey-apache-connector:2.47` | `jersey-apache-connector:3.1.11` |
| `com.sun.jersey:jersey-json:1.19.4` | Removed (Jackson handles JSON) | — |
| `javax.xml.bind:jaxb-api` (in JDK) | `javax.xml.bind:jaxb-api:2.3.1` | `jakarta.xml.bind:jakarta.xml.bind-api:4.0.0` |
| `jaxb-runtime` (in JDK) | `jaxb-runtime:2.3.9` | `jaxb-runtime:4.0.2` |
| `junit:junit:4.13.2` | `org.junit.jupiter:junit-jupiter:5.11.0` | — |
| `log4j:log4j:1.2.17` | `ch.qos.logback:logback-classic:1.5.12` | — |
| `org.slf4j:slf4j-api:1.7.36` | `org.slf4j:slf4j-api:2.0.16` | — |
| `com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.12.7` | `jackson-jaxrs-json-provider:2.17.2` | — |
