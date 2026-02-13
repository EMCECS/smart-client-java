# Migration Plan: Java 25, Gradle 9.2.1, Jersey 2.x

## Current State Analysis
- **Gradle Version**: 6.9.2 → Target: 9.2.1
- **Java Version**: 1.8 → Target: 25
- **Jersey Version**: 1.19.4 → Target: 2.x (latest compatible)
- **Test Framework**: JUnit 4.13.2 → Target: JUnit 5
- **Subprojects**: smart-client-core, smart-client-jersey, smart-client-ecs

## Key Dependencies Requiring Migration
### Jersey 1.x → Jersey 2.x
- `com.sun.jersey:jersey-client:1.19.4` → `org.glassfish.jersey.core:jersey-client:2.x`
- `com.sun.jersey.contribs:jersey-apache-client4:1.19.4` → `org.glassfish.jersey.connectors:jersey-apache-connector:2.x`
- `com.sun.jersey:jersey-json:1.19.4` → `org.glassfish.jersey.media:jersey-media-json-jackson:2.x`

### Namespace Migration
- `javax.ws.rs.*` → `jakarta.ws.rs.*`
- `javax.xml.bind.*` → `jakarta.xml.bind.*`

### Build Tool Updates
- Gradle wrapper: 6.9.2 → 9.2.1
- Plugin updates for Gradle 9.2.1 compatibility
- Replace deprecated `maven` plugin with `maven-publish`
- Update deprecated task syntax

## Migration Steps

### Phase 1: Build Configuration (Steps 1-3)
1. **Update Gradle Wrapper**
   - Update gradle-wrapper.properties to 9.2.1
   - Verify wrapper scripts

2. **Update Root build.gradle**
   - Update Java sourceCompatibility: 1.8 → 25
   - Update targetCompatibility to 25
   - Update plugin versions for Gradle 9.2.1 compatibility
   - Replace deprecated Maven plugin with maven-publish
   - Update deprecated task configurations
   - Fix deprecated syntax (configurations.runtime → configurations.runtimeClasspath)

3. **Update Subproject Dependencies**
   - smart-client-core: Update to JUnit 5, remove log4j 1.x
   - smart-client-jersey: Migrate to Jersey 2.x dependencies
   - smart-client-ecs: Migrate to Jersey 2.x, update JAXB dependencies

### Phase 2: Code Migration (Steps 4-6)
4. **Migrate Jersey API Imports**
   - Replace `com.sun.jersey.*` with `org.glassfish.jersey.*`
   - Update Client API usage patterns
   - Update filter implementations
   - Update provider implementations

5. **Migrate javax.* to jakarta.* Namespaces**
   - `javax.ws.rs.*` → `jakarta.ws.rs.*`
   - `javax.xml.bind.*` → `jakarta.xml.bind.*`
   - Update all affected Java files

6. **Update Test Code**
   - Migrate JUnit 4 → JUnit 5
   - Update Jersey test client code
   - Update annotations (@Test, @Before, @After, etc.)

### Phase 3: Verification (Steps 7-9)
7. **Compilation Verification**
   - Run `gradlew clean build`
   - Address any compilation errors
   - Verify all modules compile

8. **Test Execution**
   - Run `gradlew test`
   - Fix any test failures
   - Ensure all tests pass

9. **Final Validation**
   - Review all changes
   - Verify no deprecated APIs remain
   - Document any breaking changes
   - Generate summary report

## Risk Areas
- Jersey 1.x → 2.x has significant API changes
- Jackson version compatibility with Jersey 2.x
- Test code may require significant refactoring
- Custom providers and filters need careful migration

## Success Criteria
✓ All files compile without errors  
✓ All tests pass  
✓ No javax.* imports remain (replaced with jakarta.*)  
✓ No Jersey 1.x dependencies remain  
✓ No JUnit 4 dependencies remain  
✓ Gradle 9.2.1 wrapper configured  
✓ Java 25 compatibility verified  
