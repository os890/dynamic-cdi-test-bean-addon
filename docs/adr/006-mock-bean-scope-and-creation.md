# ADR-006: Mock Bean Scope and Creation Strategy

## Status

Accepted

## Context

The addon auto-registers Mockito mocks for unsatisfied injection points.
Two decisions are needed: what CDI scope to use and how to create the
mocks.

### Scope options

- `@RequestScoped` — fresh mock per request context (matches typical
  app-server scoping, requires request context activation per test)
- `@Singleton` — one mock instance shared across the entire test class
  (simpler lifecycle, no request context needed for mock resolution)
- `@Dependent` — new mock per injection point (no sharing, no proxy)

### Creation options (ArC)

- `BeanCreator<T>` — ArC's SPI for synthetic bean creation, receives a
  `SyntheticCreationalContext` with typed parameters
- Inline lambda — simpler but not serializable for ArC's bytecode
  generation

## Decision

### CDI SE module

Mock beans use `@RequestScoped` scope with a custom `Bean<T>`
implementation (`MockBean`) that calls `Mockito.mock(rawType)` in its
`create()` method. This matches typical application-server scoping and
ensures a fresh mock per request context. `PassivationCapable` is
implemented to satisfy Weld's serialization requirements for
normal-scoped beans.

### Quarkus module

Mock beans use `@Singleton` scope with `MockBeanCreator` (a
`BeanCreator<Object>` implementation). The class to mock is passed via
the `implementationClass` parameter. `@Singleton` is used instead of
`@RequestScoped` because ArC's request context handling during the build
phase is simpler with singleton beans, and test isolation is provided at
the container level (each test class boots a fresh ArC container).

Inline producer fields use `InlineFieldBeanCreator` which reads the
static field value via reflection at runtime.

## Consequences

- CDI SE: mocks are scoped per request — multiple requests in one test
  get separate mock instances.
- Quarkus: mocks are singletons — shared across the test class, which
  is acceptable since each test class boots its own container.
- Both approaches register mocks with `defaultBean()` semantics so they
  yield to any real bean.
