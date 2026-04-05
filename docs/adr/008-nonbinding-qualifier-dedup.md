# ADR-008: @Nonbinding Qualifier Member Deduplication

## Status

Accepted

## Context

CDI qualifiers can have members annotated `@Nonbinding` — these values
are ignored during bean resolution. If two injection points request the
same type with the same qualifier but different `@Nonbinding` member
values, they must resolve to the same bean.

Example:

```java
@Inject @Traced(description = "order-processing") TracingService order;
@Inject @Traced(description = "payment-processing") TracingService pay;
```

If `description` is `@Nonbinding`, both injection points need one shared
mock — not two separate beans (which would cause an ambiguous resolution
error).

## Decision

### CDI SE module

The `InjectionPointKey` value object implements custom `equals()` and
`hashCode()` that invoke qualifier annotation methods reflectively,
skipping any method annotated with `@Nonbinding`. Two keys that differ
only in `@Nonbinding` member values are considered equal.

### Quarkus module

The `BeanRegistrar` queries `BeanDeployment.getQualifiers()` and
`getQualifierNonbindingMembers()` to build a map of qualifier types to
their `@Nonbinding` member names. When registering mock beans:

1. A deduplication key is built from the type and qualifier annotations,
   excluding `@Nonbinding` member values.
2. Qualifier annotations are rebuilt without `@Nonbinding` members before
   being passed to `BeanConfigurator.qualifiers()`.

This ensures ArC registers exactly one mock bean per unique
(type + binding qualifier values) combination.

## Consequences

- Both modules correctly handle `@Nonbinding` semantics.
- The CDI SE approach is reflection-based and works at runtime.
- The ArC approach uses ArC's own qualifier metadata at build time.
- Tests with `@Traced(description = "...")` injection points verify this
  behavior in both modules.
