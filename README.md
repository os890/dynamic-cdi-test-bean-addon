# Dynamic CDI Test Bean Addon

CDI portable extension that automatically registers Mockito-backed mock beans
for all unsatisfied injection points during container bootstrap.

Add it as a **test dependency** and the CDI container will start even if some
injection points have no real implementation on the classpath.

> **Note:** This is the first CDI extension from
> [os890](https://github.com/os890) created entirely in an agentic AI
> workflow (Claude, Anthropic) — extension, test suite, build configuration,
> and documentation.

## Problem

In multi-module projects a `common` module often defines abstract services that
inject DAOs or other beans whose implementations live in a different module
(`app`). Running CDI tests in `common` fails because the container cannot
satisfy the injection points:

```
WELD-001408: Unsatisfied dependencies for type BaseDao<Order>
```

## Solution

Add `dynamic-cdi-test-bean-addon` as a test dependency. The extension observes all
injection points during bootstrap and registers `@RequestScoped` Mockito mocks
for every type that has no matching bean.

### Supported injection point types

| Type                        | Example                          |
|-----------------------------|----------------------------------|
| Interface                   | `@Inject AuditService`           |
| Concrete class              | `@Inject NotificationSender`     |
| Parameterized generic       | `@Inject BaseDao<Order>`         |

## Usage

### Maven dependency

```xml
<dependency>
    <groupId>org.os890.cdi.addon</groupId>
    <artifactId>dynamic-cdi-test-bean-addon</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

The extension registers itself via
`META-INF/services/jakarta.enterprise.inject.spi.Extension` — no manual
configuration is needed.

### Test example (CDI SE, JUnit 6)

```java
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class MyServiceTest {

    static SeContainer container;
    static RequestContextController rc;

    @BeforeAll
    static void boot() {
        // Full classpath scanning — the extension is loaded via SPI
        container = SeContainerInitializer.newInstance().initialize();
        rc = container.select(RequestContextController.class).get();
        rc.activate();
    }

    @AfterAll
    static void shutdown() {
        rc.deactivate();
        container.close();
    }

    @Test
    void serviceStartsWithMockedDependencies() {
        MyService service = container.select(MyService.class).get();
        assertNotNull(service);
        // The injected DAO is a Mockito mock — returns null by default
        assertNull(service.getDao().findById(1L));
    }
}
```

Registered mocks are logged at `INFO` level during container bootstrap —
check the test output to see which types were mocked.

## How it works

1. **`ProcessInjectionPoint`** — collects every injection point's type and
   qualifiers, filtering out built-in CDI/JDK/vendor types.
2. **`ProcessBean`** — catches inherited generic injection points that some
   implementations (notably OpenWebBeans) do not report via
   `ProcessInjectionPoint`.
3. **`AfterBeanDiscovery`** — for each collected type with no matching bean,
   registers a synthetic `@RequestScoped` bean backed by `Mockito.mock()`.

The mock beans implement `PassivationCapable` with a unique ID to satisfy
CDI container requirements for normal-scoped beans.

## Third-party CDI extension compatibility

The addon is built exclusively on the standard CDI API — it has **no
dependency** on any third-party CDI extension or specific CDI implementation.
However, it detects types managed by other extensions and avoids mocking them.

Currently recognized:

- **Apache DeltaSpike** — types annotated with a `@PartialBeanBinding`
  binding annotation are skipped. DeltaSpike's partial bean module provides
  the real proxy; the addon only mocks the handler's own unsatisfied
  dependencies.

## CDI implementation compatibility

The extension uses only standard CDI APIs. Tests run against both major
CDI implementations via Maven profiles:

| Implementation                        | Profile   | Command                 |
|---------------------------------------|-----------|-------------------------|
| **OpenWebBeans** 4.0.3 (default)      | `owb`     | `mvn test` or `mvn test -Powb` |
| **Weld** 5.1.4.Final                  | `weld`    | `mvn test -Pweld`       |

### OpenWebBeans and Java 25

OWB 4.0.3 ships with an older xbean-asm that does not support Java 25 class
files (major version 69). The OWB profile overrides xbean-asm and adds
ASM 9.8 to resolve this.

## Requirements

| Dependency       | Version             |
|------------------|---------------------|
| Jakarta CDI API  | 4.1.0 (provided)    |
| Mockito          | 5.x (provided)      |
| JUnit            | 6.0.3 (test)        |
| Java             | 25+                 |

Both CDI API and Mockito are `provided` scope — the consumer supplies the
actual versions.

## Code quality

The build enforces code quality through several Maven plugins that run
automatically during `mvn clean install`:

| Plugin                          | Phase      | Purpose                                                        |
|---------------------------------|------------|----------------------------------------------------------------|
| **maven-compiler-plugin**       | `compile`  | `-Xlint:all` with `failOnWarning` — all compiler warnings are errors |
| **maven-enforcer-plugin**       | `validate` | Java 17+, Maven 3.6.3+, dependency convergence, banned `javax.*` dependencies, no duplicate classes |
| **maven-checkstyle-plugin**     | `validate` | Code style: no star imports, braces on all blocks, modifier order, whitespace rules, empty line separators |
| **apache-rat-plugin**           | `validate` | Apache License 2.0 header present in all source files (Java, XML, service files, POM) |
| **maven-javadoc-plugin**        | `package`  | Generates and attaches Javadoc JAR                             |

## Building

```bash
# Build and run tests with OpenWebBeans (default)
mvn clean install

# Run tests with Weld
mvn clean test -Pweld

# Generate Javadoc
mvn javadoc:javadoc
```

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
