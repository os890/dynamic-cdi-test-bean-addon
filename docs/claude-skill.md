---
name: cdi-test
description: Write CDI SE tests using the Dynamic CDI Test Bean Addon. Use when creating tests that need @EnableTestBeans, @TestBean, @Inject, or auto-mocked CDI beans.
argument-hint: "[test-class-name] [bean-to-test]"
---

# Dynamic CDI Test Bean Addon — Test Writing Guide

## Maven dependency

```xml
<dependency>
    <groupId>org.os890.cdi.addon</groupId>
    <artifactId>dynamic-cdi-test-bean-addon</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

Also add a CDI SE implementation (Weld SE or OpenWebBeans SE) to the test classpath.

## Quick Start

Every CDI test needs `@EnableTestBeans`. The extension handles container start, request scope, `@Inject` fields, and shutdown.

```java
@EnableTestBeans
class MyTest {

    @Inject
    MyService service;

    @Test
    void test() {
        assertNotNull(service);
    }
}
```

Unsatisfied injection points are auto-mocked with Mockito. No `@BeforeAll`, no `SeContainerInitializer`.

## Replace a mock with a real alternative

Declare the replacement as `@Alternative` (no `@Priority`), reference it via `@TestBean`:

```java
@Alternative
@ApplicationScoped
public class CustomDao extends BaseDao<Order> { ... }

@EnableTestBeans
@TestBean(bean = CustomDao.class)
class MyTest { ... }
```

## Inline Mockito mock as CDI bean

```java
@EnableTestBeans
class MyTest {

    @Produces @TestBean
    private static MyService service = Mockito.mock(MyService.class);

    @Inject
    MyConsumer consumer;

    @Test
    void test() {
        Mockito.when(service.getValue()).thenReturn("stubbed");
        assertEquals("stubbed", consumer.callService());
    }
}
```

## Producer bean replacement (multiple types)

```java
@Alternative
@ApplicationScoped
public class TestProducers {
    @Produces AuditService audit() { return action -> { }; }
    @Produces MetricsService metrics() { return Mockito.mock(MetricsService.class); }
}

@EnableTestBeans
@TestBean(beanProducer = TestProducers.class)
class MyTest { ... }
```

## Composable meta-annotations

```java
@TestBean(bean = CustomDao.class)
@TestBean(bean = CustomAuth.class)
@Retention(RUNTIME) @Target(TYPE)
public @interface StandardMocks {}

@EnableTestBeans
@StandardMocks
class MyTest { ... }
```

Duplicates across meta-annotation levels are deduplicated automatically.

## Whitelist mode

Veto all beans except those declared via `@TestBean`:

```java
@EnableTestBeans(limitToTestBeans = true)
@TestBean(bean = CustomDao.class)
class MyTest { ... }
```

## Manual container management

```java
@EnableTestBeans(manageContainer = false)
class MyTest {
    static SeContainer container;

    @BeforeAll static void boot() {
        container = SeContainerInitializer.newInstance().initialize();
    }

    @AfterAll static void shutdown() { container.close(); }
}
```

## @EnableTestBeans attributes

| Attribute | Default | Effect |
|-----------|---------|--------|
| `manageContainer` | `true` | Auto start/stop CDI container + request scope |
| `addTestClass` | `true` | Register test class as `@Singleton`, inject `@Inject` fields |
| `limitToTestBeans` | `false` | Veto all beans except `@TestBean` declarations |

## Rules

- `@Alternative` beans for `@TestBean(bean=...)` need no `@Priority` — the extension enables them
- `@Alternative @Priority` beans work normally unless their types clash with a `@TestBean`
- `@Typed` is respected on both sides when checking for type clashes
- Inline `@Produces @TestBean` fields must be `static`
- Each test class is isolated — different `@TestBean` sets don't conflict

## Package structure

- **Public API**: `org.os890.cdi.addon.dynamictestbean` — `@EnableTestBeans`, `@TestBean`, `@TestBeans`
- **Internal**: `org.os890.cdi.addon.dynamictestbean.internal` — do not depend on directly
