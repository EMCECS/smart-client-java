Load-balancing "Smart" Java REST Client
===

Based on the JAX-RS (JSR 311) reference implementation ("Jersey")

Supports a pluggable host list provider to provide an updated list of active hosts (i.e from an orchestration component)

## Prerequisites

- Java 17+ (`JAVA_HOME` must point to JDK 17 or later)
- Gradle 9.2.1 (included via wrapper)

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew compileJava compileTestJava
```

## Test Configuration

Copy `test.properties.template` to `~/test.properties` and fill in your ECS cluster credentials:

- **S3 properties** (`s3.access_key`, `s3.secret_key`, `s3.endpoint`) — required by `smart-client-ecs` integration tests
- **Atmos properties** (`atmos.uid`, `atmos.secret`, `atmos.endpoints`) — required by `smart-client-jersey` integration tests

Tests that require missing properties are automatically skipped via JUnit 5 Assumptions.

## Running Tests

Run all tests:
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean test
```

Run tests for a specific module:
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :smart-client-core:test
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :smart-client-jersey:test
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :smart-client-ecs:test
```

Run a specific test class:
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :smart-client-jersey:test --tests 'com.emc.rest.smart.SmartFilterTest'
```

## Test Reports

### Aggregated report (all modules combined):
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean test testReport
```
Report: `build/reports/allTests/index.html`

### Per-module reports:
Generated automatically after `test` task:
- `smart-client-core/build/reports/tests/test/index.html`
- `smart-client-jersey/build/reports/tests/test/index.html`
- `smart-client-ecs/build/reports/tests/test/index.html`

### JaCoCo code coverage:
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean test jacocoTestReport
```
Coverage reports: `<module>/build/reports/jacoco/test/html/index.html`

## Modules

| Module | Description |
|--------|-------------|
| `smart-client-core` | Core load balancing logic with minimal dependencies |
| `smart-client-jersey` | Jersey 3.x SmartClient with SmartClientFactory |
| `smart-client-ecs` | ECS-specific HostListProvider implementation |