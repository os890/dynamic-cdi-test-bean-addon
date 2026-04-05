# ADR-003: Use ArC SPIs Directly Instead of a Quarkus Deployment Extension

## Status

Accepted

## Context

Quarkus extensions typically follow a two-module structure
(deployment + runtime) with `@BuildStep` processors that produce build
items. This requires depending on `quarkus-core-deployment`,
`quarkus-arc-deployment`, and the full Quarkus build infrastructure.

An alternative is to use ArC's processor APIs directly — the same way
ArC's own `ArcTestContainer` and Quarkus's `QuarkusComponentTestExtension`
work. This only requires `arc-processor` (which transitively includes
`arc-runtime`).

## Decision

Use ArC's `BeanProcessor`, `BeanRegistrar`, and `AnnotationTransformation`
SPIs directly from within a JUnit 5 extension. No `@BuildStep` processors,
no deployment/runtime module split, no dependency on the full Quarkus
stack.

The JUnit extension performs a complete ArC build pass inside
`@BeforeAll`:

1. Index classes with Jandex
2. Configure `BeanProcessor` with registrars and transformations
3. Call `BeanProcessor.process()` to generate bytecode
4. Boot via `Arc.initialize()`

## Consequences

- Minimal dependency footprint: `arc-processor` + `arc-runtime` only.
- No `@QuarkusTest` required — the addon manages the container itself.
- Compatible with `@QuarkusTest` — if detected, container lifecycle
  management is skipped and the Quarkus test framework handles it.
- Works in any project that has ArC on the classpath, not just full
  Quarkus applications.
- Cannot use Quarkus-specific build items (`SyntheticBeanBuildItem`,
  `AdditionalBeanBuildItem`, etc.) — all logic goes through ArC's
  lower-level SPIs.
- A custom classloader is needed to make the generated
  `ComponentsProvider` visible to `Arc.initialize()`.
