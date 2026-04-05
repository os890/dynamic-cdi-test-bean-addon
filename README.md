# Dynamic CDI Test Bean Addon

Auto-mock unsatisfied CDI injection points at container startup.
Add it as a **test dependency** and the CDI container will start even if some
injection points have no real implementation on the classpath.

Works with **CDI SE** (Weld, OpenWebBeans) and **Quarkus/ArC**.

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

Add the addon as a test dependency. The extension observes all injection points
during bootstrap and registers `@RequestScoped` Mockito mocks for every type
that has no matching bean.

### Supported injection point types

| Pattern                     | Example                                      |
|-----------------------------|----------------------------------------------|
| Interface                   | `@Inject AuditService`                       |
| Concrete class              | `@Inject NotificationSender`                 |
| Parameterized generic       | `@Inject BaseDao<Order>`                     |
| Constructor injection       | `@Inject MyBean(LogService log)`             |
| Initializer method          | `@Inject void init(ValidationService vs)`    |
| `@Named` qualifier          | `@Inject @Named("primary") AuditService`     |
| Custom qualifier            | `@Inject @Premium PaymentService`            |
| Multiple qualifiers         | `@Inject @Premium @Reliable ShippingService` |
| Qualifier with member       | `@Inject @ServiceType("express") CacheService` |
| `@Nonbinding` members       | `@Inject @Traced(description="...") TracingService` |
| Producer method parameter   | `@Produces String format(FormatService fs)`  |
| Observer method parameter   | `void on(@Observes Event e, LogService log)` |

## Module Structure

```
dynamic-cdi-test-bean-addon/        (parent POM)
  api/                              shared annotations + SPI
  cdi-module/                       CDI SE implementation (Weld / OpenWebBeans)
  quarkus-module/                   Quarkus/ArC implementation
```

| Directory | Artifact ID | Description |
|-----------|-------------|-------------|
| `api/` | `dynamic-cdi-test-bean-addon-api` | `@EnableTestBeans`, `@TestBean`, `@TestBeans`, `TestBeanContainerManager` SPI |
| `cdi-module/` | `dynamic-cdi-test-bean-addon-cdi` | CDI SE implementation (Weld / OpenWebBeans) |
| `quarkus-module/` | `dynamic-cdi-test-bean-addon-quarkus` | Quarkus/ArC implementation |

## Usage

### Maven dependency — pick your runtime

```xml
<!-- CDI SE (Weld / OpenWebBeans) -->
<dependency>
    <groupId>org.os890.cdi.addon</groupId>
    <artifactId>dynamic-cdi-test-bean-addon-cdi</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>

<!-- Quarkus / ArC -->
<dependency>
    <groupId>org.os890.cdi.addon</groupId>
    <artifactId>dynamic-cdi-test-bean-addon-quarkus</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

The API module (`@TestBean`, `@TestBeans`) is pulled in transitively.

### Simplest test — zero boilerplate

`@EnableTestBeans` manages the CDI container lifecycle, request scope, and
`@Inject` field injection automatically:

```java
@EnableTestBeans
class MyServiceTest {

    @Inject
    MyService service;

    @Test
    void serviceStartsWithMockedDependencies() {
        assertNotNull(service);
        assertNull(service.getDao().findById(1L)); // Mockito default
    }
}
```

No `@BeforeAll`, no `@AfterAll`, no `SeContainerInitializer`. The extension
handles container start, request scope activation per test method, `@Inject`
field injection, and shutdown.

### Manual container management (CDI SE only)

Use `manageContainer = false` if you need control over the container lifecycle:

```java
@EnableTestBeans(manageContainer = false)
class MyTest {

    static SeContainer container;

    @BeforeAll
    static void boot() {
        container = SeContainerInitializer.newInstance().initialize();
    }

    @AfterAll
    static void shutdown() {
        container.close();
    }
}
```

## Custom replacement beans with @TestBean

If you need a hand-crafted implementation instead of a Mockito mock, use
`@TestBean` to activate `@Alternative` beans per test class. The
annotation is repeatable — use multiple `@TestBean` annotations when you
need two or three replacements. For larger setups, see
[composable meta-annotations](#composable-meta-annotations) below.

### Direct replacement

```java
@Alternative
@ApplicationScoped
public class CustomGreeting implements Greeting {
    public String greet(String name) { return "Hello, " + name + "!"; }
}

@EnableTestBeans
@TestBean(bean = CustomGreeting.class)
class MyTest {
    @Inject GreetingConsumer consumer;

    @Test void test() {
        assertEquals("Hello, world!", consumer.getGreeting().greet("world"));
    }
}
```

### Producer-based replacement

```java
@Alternative
@ApplicationScoped
public class TestProducers {

    @Produces
    @Named("primaryAudit")
    public AuditService audit() {
        return action -> { /* no-op */ };
    }
}

@EnableTestBeans
@TestBean(beanProducer = TestProducers.class)
class MyTest { ... }
```

### Inline producer fields

The simplest pattern for one-off mocks with stubbing:

```java
@EnableTestBeans
class MyTest {

    @TestBean
    private static Greeting greeting = Mockito.mock(Greeting.class);

    @Inject
    GreetingConsumer consumer;
}
```

### Composable meta-annotations

Bundle `@TestBean` declarations into reusable annotations:

```java
@TestBean(bean = CustomGreeting.class)
@Retention(RUNTIME)
@Target({TYPE, ANNOTATION_TYPE})
public @interface GreetingMocks {}

@GreetingMocks
@Retention(RUNTIME)
@Target({TYPE, ANNOTATION_TYPE})
public @interface StandardMocks {}

@EnableTestBeans
@StandardMocks
class MyTest { ... }
```

Meta-annotations can be nested to any depth. Duplicate bean class references
across annotations are deduplicated automatically.

### Whitelist mode

`limitToTestBeans = true` vetoes ALL application beans except those
explicitly declared via `@TestBean`. No auto-mocking occurs:

```java
@EnableTestBeans(limitToTestBeans = true)
@TestBean(bean = CustomGreeting.class)
class MyTest { ... }
```

### Test class isolation

Multiple test classes on the same classpath can activate **different
alternatives for the same type** without conflicts. The internal JUnit
extension scopes each test class's `@TestBean` declarations independently.
Unselected `@Alternative` beans are vetoed.

## @EnableTestBeans attributes

| Attribute | Default | Description |
|-----------|---------|-------------|
| `addTestClass` | `true` | Register test class as `@Singleton` bean, inject `@Inject` fields |
| `limitToTestBeans` | `false` | Veto all beans except those declared via `@TestBean` |
| `manageContainer` | `true` | Start/stop CDI container automatically (CDI SE only; Quarkus ignores this) |

### Quarkus: @QuarkusTest compatibility

If the test class is also annotated with `@QuarkusTest` or
`@QuarkusComponentTest`, the addon automatically detects this and skips
all container lifecycle management. The Quarkus test framework handles
the container — the addon only provides the `@TestBean` / auto-mocking
annotations in that scenario.

## Architecture

`@EnableTestBeans` lives in the **API module** and delegates to a
`TestBeanContainerManager` SPI implementation discovered via
`ServiceLoader`. Each implementation module registers its provider in
`META-INF/services/`. Users always import the same annotation — switching
runtimes requires only a Maven dependency change.

```
@EnableTestBeans ──@ExtendWith──→ DelegatingJUnitExtension
                                     │ ServiceLoader
                                     ▼
                          TestBeanContainerManager (SPI)
                          ┌───────────┴───────────┐
                    CDI SE impl              Quarkus/ArC impl
```

## Package structure

| Package | Module | Contents |
|---------|--------|----------|
| `org.os890.cdi.addon.dynamictestbean` | **api** | `@EnableTestBeans`, `@TestBean`, `@TestBeans` |
| `org.os890.cdi.addon.dynamictestbean.spi` | **api** | `TestBeanContainerManager` SPI interface |
| `org.os890.cdi.addon.dynamictestbean.internal` | **api** + **cdi-module** | Delegating extension (api) + CDI SE extension (cdi-module) |
| `org.os890.cdi.addon.dynamictestbean.quarkus.internal` | **quarkus-module** | ArC extension internals |

## CDI implementation compatibility

| Implementation                        | Module    | Profile   | Command                           |
|---------------------------------------|-----------|-----------|-----------------------------------|
| **OpenWebBeans** 4.0.3 (default)      | `cdi-module`     | `owb`     | `mvn test` or `mvn test -Powb`    |
| **Weld** 5.1.4.Final                  | `cdi-module`     | `weld`    | `mvn test -Pweld`                 |
| **Quarkus/ArC** (999-SNAPSHOT)        | `quarkus-module` | —         | `mvn test -pl quarkus-module`     |

## Requirements

| Dependency       | Version             |
|------------------|---------------------|
| Jakarta CDI API  | 4.1.0 (provided)    |
| Mockito          | 5.x (provided)      |
| JUnit            | 6.0.3 (provided)    |
| Java             | 25+                 |
| ArC (Quarkus)    | 999-SNAPSHOT        |

## Code quality

All modules are subject to the same build quality checks:

| Plugin                          | Phase      | Purpose                                                        |
|---------------------------------|------------|----------------------------------------------------------------|
| **maven-compiler-plugin**       | `compile`  | `-Xlint:all` with `failOnWarning` — all compiler warnings are errors |
| **maven-enforcer-plugin**       | `validate` | Java 25+, Maven 3.6.3+, dependency convergence, banned `javax.*` dependencies |
| **maven-checkstyle-plugin**     | `validate` | Code style: no star imports, braces on all blocks, modifier order, whitespace rules |
| **apache-rat-plugin**           | `validate` | Apache License 2.0 header present in all source files |
| **maven-javadoc-plugin**        | `package`  | Generates and attaches Javadoc JAR |

## Test counts

| Module    | Tests | Status |
|-----------|-------|--------|
| **api**   | 0 (test-jar only) | — |
| **cdi**   | 65    | all pass |
| **quarkus** | 64  | all pass |
| **demo** | 14  | all pass |
| **Total** | **143** | **all pass** |

## Claude Code skill

A [Claude Code](https://claude.ai/code) skill is included for AI-assisted
test writing. To use it, copy the skill into your project:

```bash
mkdir -p .claude/skills/cdi-test
cp docs/claude-skill.md .claude/skills/cdi-test/SKILL.md
```

Then use `/cdi-test MyServiceTest MyService` in Claude Code to generate
a CDI test with auto-mocking and `@TestBean` support.

## Building

```bash
mvn clean verify              # All modules (OWB default for CDI)
mvn clean test -Pweld         # CDI module with Weld
mvn clean test -pl quarkus-module    # Quarkus module only
mvn javadoc:javadoc           # Generate Javadoc
```

## Documentation

- [Auto-Mocking](docs/index.html) — getting started
- [@TestBean Basics](docs/testbean.html) — alternative replacement
- [Advanced Features](docs/testbean-advanced.html) — inline producers, whitelist mode, meta-annotations
- [Internals](docs/internals.html) — CDI lifecycle hooks, implementation details
- [Quarkus Port](docs/blog-quarkus.html) — porting the addon to Quarkus/ArC
- [Modularization](docs/blog-modular.html) — one API, two runtimes

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
