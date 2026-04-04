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

Add `dynamic-cdi-test-bean-addon` as a test dependency. The extension observes
all injection points during bootstrap and registers `@RequestScoped` Mockito
mocks for every type that has no matching bean.

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

### Manual container management

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
`@TestBean` to activate `@Alternative` beans per test class:

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
    @Produces @Named("primaryAudit")
    AuditService audit() { return action -> { /* no-op */ }; }
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
    @Produces @TestBean
    private static Greeting greeting = Mockito.mock(Greeting.class);

    @Inject GreetingConsumer consumer;
}
```

### Composable meta-annotations

Bundle `@TestBean` declarations into reusable annotations:

```java
@TestBean(bean = CustomGreeting.class)
@TestBean(bean = CustomDao.class)
@Retention(RUNTIME) @Target(TYPE)
public @interface StandardMocks {}

@StandardMocks
@TestBean(beanProducer = AuditProducers.class)
@Retention(RUNTIME) @Target(TYPE)
public @interface FullTestSetup {}

@EnableTestBeans
@FullTestSetup
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
| `manageContainer` | `true` | Start/stop CDI container and request scope automatically |
| `addTestClass` | `true` | Register test class as `@Singleton` bean, inject `@Inject` fields |
| `limitToTestBeans` | `false` | Veto all beans except those declared via `@TestBean` |

## Package structure

| Package | Contents |
|---------|----------|
| `org.os890.cdi.addon.dynamictestbean` | **Public API** — `@EnableTestBeans`, `@TestBean`, `@TestBeans` |
| `org.os890.cdi.addon.dynamictestbean.internal` | Extension internals — do not depend on directly |

## Third-party CDI extension compatibility

The addon is built exclusively on the standard CDI API — it has **no
dependency** on any third-party CDI extension or specific CDI implementation.
However, it detects types managed by other extensions and avoids mocking them.

Currently recognized:

- **Apache DeltaSpike** — types annotated with a `@PartialBeanBinding`
  binding annotation are skipped.

## CDI implementation compatibility

The extension uses only standard CDI APIs. Tests run against both major
CDI implementations via Maven profiles:

| Implementation                        | Profile   | Command                 |
|---------------------------------------|-----------|-------------------------|
| **OpenWebBeans** 4.0.3 (default)      | `owb`     | `mvn test` or `mvn test -Powb` |
| **Weld** 5.1.4.Final                  | `weld`    | `mvn test -Pweld`       |

## Requirements

| Dependency       | Version             |
|------------------|---------------------|
| Jakarta CDI API  | 4.1.0 (provided)    |
| Mockito          | 5.x (provided)      |
| JUnit            | 6.0.3 (provided)    |
| Java             | 25+                 |

## Code quality

| Plugin                          | Phase      | Purpose                                                        |
|---------------------------------|------------|----------------------------------------------------------------|
| **maven-compiler-plugin**       | `compile`  | `-Xlint:all` with `failOnWarning` — all compiler warnings are errors |
| **maven-enforcer-plugin**       | `validate` | Java 25+, Maven 3.6.3+, dependency convergence, banned `javax.*` dependencies, no duplicate classes |
| **maven-checkstyle-plugin**     | `validate` | Code style: no star imports, braces on all blocks, modifier order, whitespace rules |
| **apache-rat-plugin**           | `validate` | Apache License 2.0 header present in all source files |
| **maven-javadoc-plugin**        | `package`  | Generates and attaches Javadoc JAR |

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
mvn clean install           # OpenWebBeans (default)
mvn clean test -Pweld       # Weld
mvn javadoc:javadoc         # Generate Javadoc
```

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
