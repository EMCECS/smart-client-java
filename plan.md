# Java Application Modernization Plan (Cap-20844)

## Objectives
- Migrate build/tooling to Java 25 and Gradle 9.1.0.
- Migrate Jersey 1.x client integration to Jersey 2 (JAX-RS 2) and remove `com.sun.jersey` dependencies/usages.
- Keep existing behavior (load-balancing filter, host polling, ECS host discovery, tests).
- Maintain a green build (`gradlew test`).

## Milestones
1. Tooling upgrade
   - Gradle wrapper -> 9.1.0
   - Java toolchain -> 25
   - Modernize Gradle publishing/config DSL for Gradle 9 compatibility
2. Jersey migration
   - Update dependencies to Jersey 2.x equivalents
   - Refactor Jersey 1 specific code to JAX-RS 2 APIs
   - Replace Jersey 1 internal providers with portable JAX-RS providers
3. Verification
   - Ensure all modules compile
   - Ensure all unit tests compile and pass

## Risks / Notes
- Windows file locking during `clean`/archive tasks (mitigated in build logic).
- Java 25 removes JAXB from the JDK; JAXB dependencies must be explicit.
- Jersey 2 uses `javax.ws.rs` APIs (not `jakarta.ws.rs` in 2.x).
