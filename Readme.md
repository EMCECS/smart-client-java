Load-balancing "Smart" Java REST Client
===

Based on the JAX-RS (JSR 311) reference implementation ("Jersey")

Supports a pluggable host list provider to provide an updated list of active hosts (i.e from an orchestration component)

## Prerequisites

- Java 17+
- Gradle 9.2.1 (included via wrapper)

## Build

```bash
./gradlew compileJava compileTestJava
```

## Test Configuration

Copy `test.properties.template` to `~/test.properties` and fill in your credentials. See the template for required properties.

Tests that require missing properties are automatically skipped via JUnit 5 Assumptions.

## Running Tests

Run all tests:
```bash
./gradlew clean test
```

Run tests for a specific module:
```bash
./gradlew :smart-client-core:test
./gradlew :smart-client-jersey:test
./gradlew :smart-client-ecs:test
```

Run a specific test class:
```bash
./gradlew :smart-client-jersey:test --tests 'com.emc.rest.smart.SmartFilterTest'
```

## Modules

| Module | Description |
|--------|-------------|
| `smart-client-core` | Core load balancing logic with minimal dependencies |
| `smart-client-jersey` | Jersey 3.x SmartClient with SmartClientFactory |
| `smart-client-ecs` | ECS-specific HostListProvider implementation |