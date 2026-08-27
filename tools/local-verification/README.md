# Local verification (no IntelliJ download)

This directory exists because the plugin's real test suite needs an IntelliJ Platform
distribution (~1.5 GB) that is not always available. It lets the *decision logic* of the plugin
be compiled and executed against a hand-written stub of the IntelliJ and Gauge APIs.

```
./run.sh            # needs a JDK and kotlinc on PATH; nothing else
```

## What it covers

* `@Step("...")` value -> canonical `{}` template, and placeholder counting
* completion dummy-identifier stripping and whitespace normalisation
* `PsiType` -> enum / boolean / numeric / string / unsupported classification
  (including all primitives, box types, `BigInteger`, `BigDecimal`)
* completion candidate generation per kind, and that enum and boolean providers stay disjoint
* the prefix matcher: filtering, case insensitivity, cross-enum isolation, and - critically -
  the replacement range that keeps `"LOG|"` -> `"LOGIN_BUTTON"` from duplicating text or quotes
* value validation and quick-fix ranking (Levenshtein)

## What it does NOT cover

Anything that needs a live PSI tree, a parsed `.spec` file, indexing, or the completion
pipeline. Those live in `src/test/kotlin` as IntelliJ fixture tests and need
`./gradlew test`.

## Stub fidelity

Every signature in `stubs/` was copied from the real sources of the `262` branches of
`JetBrains/intellij-community` and `JetBrains/intellij-plugins` (IntelliJ IDEA 2026.2.x).
`CamelHumpMatcher` is the one deliberate approximation - a case-insensitive prefix match,
which agrees with the real matcher for every input used here.
