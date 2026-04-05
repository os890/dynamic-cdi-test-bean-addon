# ADR-002: @EnableTestBeans in API Module with SPI Delegation

## Status

Accepted (supersedes previous decision to keep @EnableTestBeans per impl)

## Context

`@EnableTestBeans` is the user-facing entry point annotation. It carries
`@ExtendWith` to trigger the JUnit 5 extension that boots the CDI
container. The CDI SE and Quarkus implementations have fundamentally
different container management — CDI uses `SeContainerInitializer`, ArC
uses `BeanProcessor` + `Arc.initialize()`.

Options considered:

1. **Duplicate `@EnableTestBeans` in each impl module** — simple but
   different import paths per runtime; users must know which module
   they use at the import level.
2. **Single `@EnableTestBeans` in API with SPI delegation** — one
   import for all users; a thin JUnit extension in the API discovers
   the actual implementation via `ServiceLoader`.

## Decision

Place `@EnableTestBeans` in the API module with a
`DelegatingJUnitExtension` that loads a `TestBeanContainerManager`
implementation via `ServiceLoader`.

### API module provides

- `@EnableTestBeans` annotation with `@ExtendWith(DelegatingJUnitExtension.class)`
- `TestBeanContainerManager` SPI interface (5 lifecycle methods)
- `DelegatingJUnitExtension` — thin JUnit extension that resolves
  exactly one `TestBeanContainerManager` from the classpath and
  forwards all callbacks

### Each impl module provides

- A `TestBeanContainerManager` implementation
- A `META-INF/services/...TestBeanContainerManager` registration file

### Attribute handling

All attributes (`addTestClass`, `limitToTestBeans`, `manageContainer`)
are defined on the shared annotation. The `manageContainer` attribute
is CDI SE specific — the Quarkus implementation ignores it.

## Consequences

- **One import** for all users: `org.os890.cdi.addon.dynamictestbean.EnableTestBeans`
- Switching from CDI SE to Quarkus requires changing the Maven
  dependency only — no import changes in test classes.
- Clear error message if no implementation is on the classpath or
  if multiple implementations conflict.
- The API module now depends on `junit-jupiter-api` (provided scope)
  for the delegating extension.
- No split-package issue for `@EnableTestBeans` — it lives in the
  API module alongside `@TestBean` and `@TestBeans`.
