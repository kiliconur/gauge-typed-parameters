# Gauge Typed Parameters 1.2.0

Two features on top of 1.0.0: Gauge concept file support, and a project enum browser offered on
`String` step parameters.

## Concept file (`.cpt`) support

Typed parameter completion and the inspection now work inside Gauge concept files, not only in
`.spec` files.

This is the same pipeline, not a second engine. Gauge models concepts with a structurally
identical PSI hierarchy (`ConceptStep` / `ConceptArg` / `ConceptStaticArg`, generated from the
same grammar shape as the spec one) and resolves concept steps by wrapping their AST node in a
`SpecStepImpl` and calling the same `StepUtil.findStepImpl`. A new `GaugeDialect` captures that
correspondence, so both languages produce one `GaugeParameterContext` and everything downstream —
step resolution, parameter mapping, type classification, completion, inspection — is shared.

## Project enum browser on `String` parameters

Many Gauge string parameters carry an enum constant name that the step implementation converts
itself. Completion now helps with exactly that, in two stages:

```java
@Step("<item> menusune git")
public void goToMenu(String item) { }
```

* **Stage 1** — `* "Pag|" menusune git` offers enum **class** names (`PageItems`, `PageItems2`,
  `PageHeaderItems`), each shown with `enum` and its package. Only the bare class name is
  inserted.
* **Stage 2** — after typing a dot, `* "PageItems2.LO|" menusune git` offers only that class's
  constants.
* Selecting a constant replaces the whole temporary value: the result is
  `* "LOGIN_BUTTON" menusune git`, never `"PageItems2.LOGIN_BUTTON"`.

**The parameter stays completely unrestricted.** `"anything"`, `"custom value"`, `"abc123"` remain
legal and are never flagged — not by the inspection, not by completion, and not even against the
enum class the user just browsed. Text that matches no enum class name simply offers nothing, so
free text is never interrupted by a popup of the whole catalogue. No annotations, no extra syntax.

Behaviour that was designed in deliberately:

* A parameter declared as a **concrete** enum keeps the previous, direct behaviour: its own
  constants, immediately, with validation and quick fixes as before.
* Discovery is limited to the current module, its dependencies and project sources — no JDK,
  platform, Gauge or library enums.
* Stage 2 resolves the typed class name directly through the Java short-name index and reads only
  that class's constants; it never rebuilds the project enum catalogue.
* Two enums with the same short name are listed separately with their packages. If such a name is
  typed by hand and stays ambiguous, no constants are offered rather than the wrong enum's. The
  class picked in stage 1 is remembered per editor and breaks the tie; a fully qualified name
  works too.

### Note on the unreleased 1.1.0

1.1.0 used a parameter declared as exactly `java.lang.Enum` as the signal for this browser. That
does not survive Gauge's own runtime parameter conversion, so the signal is now `String` and
`java.lang.Enum` carries no special meaning at all (it classifies as unsupported, like any other
type the plugin has nothing useful to say about). 1.1.0 was never published; 1.2.0 replaces it.

## Unchanged

Specific enum completion, boolean completion, numeric recognition, prefix matching and insertion,
wrong-value replacement, quote handling, the inspection and its quick fixes, dumb-mode safety and
step resolution all behave exactly as in 1.0.0.

## Install

Download `gauge-typed-parameters-1.2.0.zip` below, then **Settings → Plugins → gear →
Install Plugin from Disk…** and restart. Requires IntelliJ IDEA 2026.2.x with the official Gauge
plugin installed.
