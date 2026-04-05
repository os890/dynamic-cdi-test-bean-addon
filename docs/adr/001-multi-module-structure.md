# ADR-001: Multi-Module Project Structure

## Status

Accepted

## Context

The addon started as a single-module CDI SE project. After porting to
Quarkus/ArC, we had two implementations that duplicated the `@TestBean`
and `@TestBeans` annotations plus 38 test use-case classes. The Quarkus
module also lacked the build quality plugins (RAT, Checkstyle, Enforcer)
that the CDI module enforced.

We needed a structure that shares common code, enforces build quality
uniformly, and lets consumers pick the right implementation for their
runtime.

## Decision

Split the project into three Maven modules under a parent POM:

- **api** — shared annotations (`@TestBean`, `@TestBeans`) and a test-jar
  with 38 use-case classes reused by both implementations
- **cdi** — CDI SE implementation (Weld / OpenWebBeans)
- **quarkus** — Quarkus/ArC implementation

The parent POM defines all build plugins in `<pluginManagement>` and
activates them for all child modules. Dependency versions are managed
centrally via `<dependencyManagement>`.

## Consequences

- Consumers add a single dependency (`-cdi` or `-quarkus`); the API
  module is pulled in transitively.
- Build quality (RAT, Checkstyle, Enforcer) is enforced uniformly across
  all modules.
- Test use-case classes are maintained once in the API module and shared
  via Maven's test-jar mechanism.
- Adding a third implementation (e.g., for a different CDI engine) only
  requires a new child module.
