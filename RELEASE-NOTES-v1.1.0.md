# Gauge Typed Parameters 1.1.0

Two features, no changes to how the existing typed completion behaves.

## Concept file (`.cpt`) support

Typed parameter completion and the inspection now work inside Gauge concept files, not only in
`.spec` files.

This is the same pipeline, not a second engine. Gauge models concepts with a structurally
identical PSI hierarchy (`ConceptStep` / `ConceptArg` / `ConceptStaticArg`, generated from the
same grammar shape as the spec one) and resolves concept steps by wrapping their AST node in a
`SpecStepImpl` and calling the same `StepUtil.findStepImpl`. A new `GaugeDialect` captures that
correspondence, so both languages produce one `GaugeParameterContext` and everything downstream —
step resolution, parameter mapping, type classification, completion, inspection — is shared.

## Project enum browser for `java.lang.Enum` parameters

A step parameter declared as exactly `java.lang.Enum` is an intentional signal: any project enum
constant may go there, and the implementation resolves which enum it belongs to at run time.

```java
@Step("<item> menusune git")
public void goToMenu(Enum item) { }
```

* **Stage 1** — `* "Pag|" menusune git` offers enum **class** names (`PageItems`, `PageItems2`,
  `PageHeaderItems`), each shown with `enum` and its package. Only the bare class name is
  inserted.
* **Stage 2** — after typing a dot, `* "PageItems2.LO|" menusune git` offers only that class's
  constants.
* Selecting a constant replaces the whole temporary value: the result is
  `* "LOGIN_BUTTON" menusune git`, never `"PageItems2.LOGIN_BUTTON"`.

Behaviour that was designed in deliberately:

* A parameter declared as a **concrete** enum keeps the previous, direct behaviour. Only exactly
  `java.lang.Enum` switches to browsing mode.
* Discovery is limited to the current module, its dependencies and project sources — no JDK,
  platform, Gauge or library enums.
* Stage 2 resolves the typed class name directly through the Java short-name index and reads only
  that class's constants; it never rebuilds the project enum catalogue.
* Two enums with the same short name are listed separately with their packages. If such a name is
  typed by hand and stays ambiguous, no constants are offered rather than the wrong enum's. The
  class picked in stage 1 is remembered per editor and breaks the tie; a fully qualified name
  works too.
* `java.lang.Enum` parameters are never flagged by the inspection.

## Unchanged

Specific enum completion, boolean completion, prefix matching and insertion, wrong-value
replacement, quote handling, the inspection and its quick fixes, dumb-mode safety and step
resolution all behave exactly as in 1.0.0, and are covered by the same tests as before.

## Install

Download `gauge-typed-parameters-1.1.0.zip` below, then **Settings → Plugins → gear →
Install Plugin from Disk…** and restart. Requires IntelliJ IDEA 2026.2.x with the official Gauge
plugin installed.
