# ADR-005: Split Package Across API and CDI Modules

## Status

Superseded — the split package was eliminated by moving `@EnableTestBeans`
to the API module (see ADR-002).

## Context

The original single-module addon published all public classes in
`org.os890.cdi.addon.dynamictestbean` — `EnableTestBeans`, `TestBean`,
`TestBeans`.

After the initial modularization, `TestBean` and `TestBeans` moved to
the API module while `EnableTestBeans` stayed in the CDI module. Both
modules contributed classes to the same Java package — a split package.

## Original Decision

Accept the split package to preserve backward compatibility.

## Superseded By

ADR-002 was revised to move `@EnableTestBeans` into the API module with
SPI delegation via `TestBeanContainerManager`. This eliminated the split
package entirely:

- **API module** owns the full `org.os890.cdi.addon.dynamictestbean`
  package: `EnableTestBeans`, `TestBean`, `TestBeans`
- **CDI module** only contributes to
  `org.os890.cdi.addon.dynamictestbean.internal`
- No split package remains

The `internal` package is still split across API (delegating extension)
and CDI (portable extension), but internal packages are not part of the
public API and do not cause JPMS issues for consumers.
