# ADR-004: Shared Test Use-Cases via Maven test-jar

## Status

Accepted

## Context

Both the CDI and Quarkus modules need the same 38 test use-case classes
(beans, interfaces, qualifiers, entities) to run their test suites. These
classes use only `jakarta.*` annotations — no addon-specific imports.

Options considered:

1. **Separate test-support module** — a fourth Maven module dedicated to
   shared test classes. Clean but adds module overhead for ~35 trivial
   files.
2. **Maven test-jar** — the API module produces a test-jar artifact from
   its `src/test/java`. Implementation modules depend on it with
   `<type>test-jar</type><scope>test</scope>`.
3. **Duplicate the classes** — copy them into each module. No shared
   dependency but maintenance burden.

## Decision

Use Maven's `maven-jar-plugin` with the `test-jar` goal in the API
module. Both CDI and Quarkus modules depend on the API test-jar.

A `beans.xml` with `bean-discovery-mode="annotated"` is included in
`api/src/test/resources/META-INF/` so that Weld discovers the annotated
beans from the test-jar archive.

## Consequences

- Use-case classes are maintained in one place (`api/src/test/java`).
- Both implementation modules get them via standard Maven dependency
  resolution.
- Weld requires `beans.xml` in the test-jar for annotated discovery —
  this was a fix discovered during Weld profile testing.
- The Quarkus module's class discovery had to be extended to scan
  classpath JARs (not just the filesystem `target/test-classes/`
  directory) since the use-case classes now live in a JAR.
