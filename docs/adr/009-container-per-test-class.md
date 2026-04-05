# ADR-009: One Container Per Test Class

## Status

Accepted

## Context

Each test class can declare a different set of `@TestBean` alternatives,
inline producer fields, and whitelist mode settings. These configurations
affect which beans are active, which are vetoed, and which are mocked.

Options:

1. **One container per test class** — boot a fresh CDI/ArC container in
   `@BeforeAll`, shut it down in `@AfterAll`. Complete isolation.
2. **Shared container with mock swapping** — boot once, swap mocks per
   test class (like Quarkus's `QuarkusMock`). Faster but limited to
   normal-scoped beans.
3. **Shared container with test profiles** — restart only when the
   configuration changes (like `@QuarkusTest` with `@TestProfile`).

## Decision

Boot a fresh container per test class.

### CDI SE module

`DynamicTestBeanJUnitExtension.beforeAll()` sets the active test class
in `DynamicTestBeanContext`, then starts a new `SeContainer` via
`SeContainerInitializer`. The CDI portable extension reads the test class
configuration during bean discovery. `afterAll()` shuts down the
container.

### Quarkus module

`DynamicTestBeanJUnitExtension.beforeAll()` calls `Arc.shutdown()` to
ensure no previous container is running, then builds a fresh Jandex
index, runs `BeanProcessor.process()`, and calls `Arc.initialize()`.
`afterAll()` calls `Arc.shutdown()`.

Request scope is activated per test method (`beforeEach`) and terminated
after (`afterEach`).

If the test class also carries `@QuarkusTest` or `@QuarkusComponentTest`,
all container lifecycle management is skipped — the Quarkus test framework
handles it. Detection is by annotation name (no compile-time dependency).

## Consequences

- Full isolation: different test classes can activate different
  alternatives for the same type without conflicts.
- Startup cost per test class — acceptable for test scenarios, not
  suitable for production.
- No shared state between test classes — each gets a clean container.
- The Quarkus module must swap the thread context classloader to make
  generated `ComponentsProvider` classes visible, and restore it after.
