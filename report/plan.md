# Migration Plan: Java 8 → Java 25, Gradle 6.9.2 → 9.2.1, Jersey 1.19.4 → 2.47

## Current State Analysis

### Project Structure
- **Root Project**: `smart-client` (multi-module Gradle project)
- **Subprojects**:
  - `smart-client-core`: Core smart client logic (minimal dependencies)
  - `smart-client-jersey`: Jersey 1.x client integration
  - `smart-client-ecs`: ECS-specific host list provider

### Current Technology Stack
- **Java Version**: 1.8 (sourceCompatibility = 1.8)
- **Gradle Version**: 6.9.2
- **Jersey Client**: 1.19.4 (com.sun.jersey)
- **Testing Framework**: JUnit 4.13.2
- **Build Tools**: 
  - net.saliman.cobertura:4.0.0
  - com.github.jk1.dependency-license-report:1.17
  - org.ajoberstar.git-publish:3.0.1
  - nebula.release:15.3.1

### Key Dependencies to Update
1. Jersey 1.19.4 → 2.47 (major API changes)
2. Apache HttpClient connection manager (deprecated classes)
3. JUnit 4.13.2 → JUnit 5
4. Jackson 2.12.7 → 2.18.x
5. SLF4J 1.7.36 → 2.0.x
6. Log4j 1.2.17 → Log4j2 or Logback

## Jersey 1.x → 2.x Migration Impact Analysis

### Package Changes
- `com.sun.jersey.*` → `org.glassfish.jersey.*`
- Client API completely redesigned

### Major API Changes Identified

#### 1. Client Creation (SmartClientFactory.java)
**Jersey 1.x:**
```java
Client client = new Client(clientHandler, clientConfig);
```

**Jersey 2.x:**
```java
Client client = ClientBuilder.newClient(clientConfig);
```

#### 2. ClientFilter → ClientRequestFilter/ClientResponseFilter
**Jersey 1.x:**
```java
class SmartFilter extends ClientFilter {
    public ClientResponse handle(ClientRequest request) { ... }
}
```

**Jersey 2.x:**
```java
class SmartFilter implements ClientRequestFilter, ClientResponseFilter {
    public void filter(ClientRequestContext requestContext) { ... }
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) { ... }
}
```

#### 3. Apache HttpClient Integration
**Jersey 1.x:**
- `jersey-apache-client4`
- `ApacheHttpClient4Handler`
- Direct HttpClient manipulation

**Jersey 2.x:**
- `jersey-apache-connector`
- `ApacheConnectorProvider`
- Configuration-based setup

#### 4. Provider Registration
**Jersey 1.x:**
```java
clientConfig.getClasses().add(Provider.class);
clientConfig.getSingletons().add(instance);
```

**Jersey 2.x:**
```java
clientConfig.register(Provider.class);
clientConfig.register(instance);
```

#### 5. WebResource → WebTarget
**Jersey 1.x:**
```java
WebResource resource = client.resource(uri);
WebResource.Builder builder = resource.getRequestBuilder();
```

**Jersey 2.x:**
```java
WebTarget target = client.target(uri);
Invocation.Builder builder = target.request();
```

### Files Requiring Code Changes

#### smart-client-jersey Module
1. **SmartClientFactory.java** - Major refactoring needed
   - Client creation with Apache connector
   - Configuration API changes
   - Connection manager setup
   - Provider registration

2. **SmartFilter.java** - Complete rewrite
   - ClientFilter → ClientRequestFilter + ClientResponseFilter
   - Request/Response handling API changes

3. **SizeOverrideWriter.java** - Provider updates
   - Remove references to Jersey 1.x internal providers
   - Implement custom providers for Jersey 2.x

4. **OctetStreamXmlProvider.java** - Provider updates
   - Remove Jersey 1.x XMLRootElementProvider
   - Use Jersey 2.x equivalents

5. **SizedInputStreamWriter.java** - Review for compatibility

#### smart-client-ecs Module
1. **EcsHostListProvider.java**
   - WebResource → WebTarget
   - Builder API changes

#### Test Files (All modules)
- **JUnit 4 → JUnit 5 migration**
  - `@Test` annotations (no changes needed for basic tests)
  - `org.junit.Assert` → `org.junit.jupiter.api.Assertions`
  - `@Before/@After` → `@BeforeEach/@AfterEach`
  - `@BeforeClass/@AfterClass` → `@BeforeAll/@AfterAll`
  - Test runner configuration

## Migration Steps

### Phase 1: Build System Modernization
1. ✅ Update Gradle wrapper: 6.9.2 → 9.2.1
2. ✅ Update root build.gradle
   - Java 25 compatibility (toolchain or sourceCompatibility = 25)
   - Modern plugin versions
   - Update deprecated APIs (configurations.runtime → runtimeClasspath)
3. ✅ Update subproject build.gradle files
   - Dependency declarations (implementation/api)

### Phase 2: Dependency Updates
1. ✅ Jersey 1.19.4 → 2.47
   - `com.sun.jersey:jersey-client:1.19.4` → `org.glassfish.jersey.core:jersey-client:2.47`
   - `com.sun.jersey.contribs:jersey-apache-client4:1.19.4` → `org.glassfish.jersey.connectors:jersey-apache-connector:2.47`
   - `com.sun.jersey:jersey-json:1.19.4` → Remove (use Jackson directly)
   
2. ✅ JUnit 4.13.2 → JUnit 5
   - `junit:junit:4.13.2` → `org.junit.jupiter:junit-jupiter:5.11.4`
   
3. ✅ Other dependencies
   - Apache HttpClient 4.5.13 → 5.x (requires code changes)
   - Jackson 2.12.7 → 2.18.x
   - SLF4J 1.7.36 → 2.0.x
   - Log4j 1.2.17 → Logback or Log4j2
   - commons-codec 1.15 → 1.17

### Phase 3: Code Migration

#### smart-client-jersey Module

1. **SmartClientFactory.java**
   - Replace Client creation with ClientBuilder
   - Update Apache connector configuration
   - Replace PoolingClientConnectionManager with PoolingHttpClientConnectionManager
   - Update provider registration API
   - Replace internal Jersey providers with custom implementations

2. **SmartFilter.java**
   - Implement ClientRequestFilter for pre-request processing
   - Implement ClientResponseFilter for post-response processing
   - Update request/response API calls
   - Update property access methods

3. **SizeOverrideWriter.java**
   - Replace Jersey 1.x internal providers with custom implementations
   - Ensure MessageBodyWriter interface compatibility

4. **OctetStreamXmlProvider.java**
   - Replace XMLRootElementProvider.General with Jersey 2.x equivalent
   - Update constructor and context injection

#### smart-client-ecs Module

1. **EcsHostListProvider.java**
   - Replace WebResource with WebTarget
   - Update request builder API
   - Update HTTP method calls

#### Test Files Migration

1. Update imports: `org.junit.*` → `org.junit.jupiter.api.*`
2. Update assertion methods
3. Update lifecycle annotations
4. Update build.gradle test configuration: `useJUnit()` → `useJUnitPlatform()`

### Phase 4: Plugin & Configuration Updates

1. Update Cobertura plugin or replace with JaCoCo (Cobertura doesn't support Java 9+)
2. Update git-publish plugin
3. Update nebula.release plugin
4. Update signing and publishing configuration for Gradle 9.x
5. Fix deprecated API usage

### Phase 5: Compilation & Testing

1. Clean build and resolve compilation errors
2. Address any Java 25 compatibility issues
3. Run unit tests and fix failures
4. Run integration tests
5. Verify all functionality

### Phase 6: Documentation & Cleanup

1. Update README if needed
2. Remove old commented code
3. Update version numbers
4. Generate summary report

## Risk Assessment

### High Risk Areas
1. **Jersey API changes** - Extensive code modifications required
2. **Apache HttpClient** - Connection manager API changes
3. **Test compatibility** - All tests need verification

### Medium Risk Areas
1. **Jackson version** - Potential serialization issues
2. **Plugin compatibility** - Some plugins may not support Gradle 9.2.1/Java 25

### Low Risk Areas
1. **smart-client-core** - Minimal dependencies, low risk
2. **JUnit 5 migration** - Mostly backward compatible annotations

## Success Criteria Checklist

- [ ] Gradle wrapper updated to 9.2.1
- [ ] Java 25 configured and working
- [ ] All Jersey 1.x dependencies replaced with 2.47
- [ ] All code compiles without errors
- [ ] All tests updated to JUnit 5
- [ ] All tests pass
- [ ] No deprecated API warnings
- [ ] No CVEs in dependencies
- [ ] Maven publishing still works
- [ ] Distribution tasks work correctly
