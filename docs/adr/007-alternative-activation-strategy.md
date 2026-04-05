# ADR-007: @TestBean Alternative Activation Strategy

## Status

Accepted

## Context

`@TestBean(bean = X.class)` activates an `@Alternative` bean class for a
specific test. The mechanism differs between CDI SE and ArC.

### CDI SE options

- `AfterTypeDiscovery.getAlternatives().add(X)` — the standard CDI
  portable extension hook for enabling alternatives programmatically
- `beans.xml` `<alternatives>` — static, not per-test-class

### ArC options

- `quarkus.arc.selected-alternatives` config property — requires
  config manipulation at build time
- `AnnotationTransformation` — add `@Priority` to the alternative class
  at build time so ArC activates it automatically
- `AlternativePriorities` callback — ArC's SPI for custom alternative
  priority resolution

## Decision

### CDI SE module

Use `AfterTypeDiscovery.getAlternatives().add(X)` in the CDI portable
extension. The active test class is communicated from the JUnit extension
to the CDI extension via `DynamicTestBeanContext` (a static volatile
bridge).

Additionally, unselected `@Alternative` beans whose bean types clash with
selected ones are vetoed via `ProcessAnnotatedType.veto()` to prevent
ambiguous resolution.

### Quarkus module

Use `AnnotationTransformation` to add `@Priority(Integer.MAX_VALUE)` to
`@TestBean`-selected alternative classes. This makes ArC treat them as
active alternatives without any config property manipulation.

Unselected clashing alternatives are excluded via
`BeanProcessor.addExcludeType()`.

Scope-less alternatives (no CDI scope annotation) also get `@Singleton`
added via the annotation transformation so they are discoverable in
annotated bean discovery mode.

## Consequences

- Both approaches achieve the same result: per-test-class alternative
  selection.
- The CDI SE approach uses the standard portable extension lifecycle.
- The ArC approach is build-time — alternatives are resolved before
  the container starts.
- Meta-annotation support (recursive `@TestBean` discovery) works
  identically in both modules since it is pure annotation reflection.
