# Gauge Typed Parameters

A standalone IntelliJ IDEA plugin that enhances Gauge specifications (`.spec`) and concept files
(`.cpt`) with **semantic typed parameter completion** based on the Java types of your Gauge step
implementations.

The official [Gauge plugin](https://plugins.jetbrains.com/plugin/7535-gauge) is **not forked, not
patched and not bundled**. This is a separate plugin that declares a normal dependency on it
(`com.thoughtworks.gauge`) and reuses its PSI, references and step resolution. Install both side
by side; upgrade Gauge whenever you like.

Your specs stay plain Gauge. No custom syntax, no markers, no annotations — a spec edited with
this plugin runs unchanged on the standard Gauge runtime.

## Example

Java step implementation:

```java
enum Element {
    LOGIN_BUTTON,
    LOGOUT_BUTTON,
    SETTINGS_BUTTON
}

@Step("<element> elementine tiklanir")
public void clickElement(Element element) {
}
```

Gauge spec, with the caret inside the parameter:

```
* "LO<caret>" elementine tiklanir
```

<kbd>Ctrl</kbd>+<kbd>Space</kbd> suggests:

```
LOGIN_BUTTON
LOGOUT_BUTTON
```

Selecting `LOGIN_BUTTON` produces exactly:

```
* "LOGIN_BUTTON" elementine tiklanir
```

## Features

- **Enum completion** — the constants of the resolved Java enum, and nothing else. Two enum
  parameters in one step never leak candidates into each other.
- **Concept file support** — the same completion and the same inspection inside `.cpt` files.
  Spec PSI and concept PSI feed one shared pipeline; there is no second engine.
- **Project enum browser for `java.lang.Enum` parameters** — declaring a parameter as the raw
  base class asks for enum *class* names first and that class's constants after a dot. See
  [below](#the-javalangenum-project-enum-browser).
- **Case-insensitive prefix matching** — typing `lo`, `LO` or `Lo` all match `LOGIN_BUTTON`.
  Insertion is *not* case-adjusted: you always get the constant exactly as declared.
- **Boolean completion** — `true` / `false` for `boolean` and `java.lang.Boolean`.
- **Semantic placeholder → `PsiParameter` resolution** — the caret's placeholder index is mapped
  to the matching Java parameter, so in
  `* "CHROME" ile "3" kere "LOG|" elementine tiklanir` the caret resolves to the *third*
  parameter, not the first.
- **Never duplicates quotes or text** — only the text you typed inside the quotes is replaced.
  `"LOG|"` becomes `"LOGIN_BUTTON"`, never `""LOGIN_BUTTON""` or `"LOGLOGIN_BUTTON"`.
- **Replaces wrong values in one keystroke** — on `"WRONG_VALUE|"`, Ctrl+Space still offers every
  constant and selecting one replaces the whole value.
- **Inspection with quick fixes** — flags values the resolved Java type cannot accept
  (`Unknown Element value 'LOGNI_BUTTON'`) and offers ranked *"Replace with …"* fixes.
- **Numeric type awareness** — `int`, `long`, `short`, `byte`, `double`, `float`, their box types,
  `BigInteger` and `BigDecimal` are recognised and validated. No values are suggested for them
  by design.
- **Fails silent, never noisy** — nothing is reported or suggested while the project is indexing,
  when the step cannot be resolved, when it resolves ambiguously, when the placeholder count does
  not match the Java parameter count, or for `String` and unsupported types. No false positives.

### Current limitations

- **Java step implementations only.** The resolver is structured so Kotlin can be added later,
  but it is not implemented.
- **Auto-quoting outside existing quotes is best-effort and currently inert on a stock install.**
  For a caret in plain step text the position is a `STEP` token, and Gauge's own
  `StepCompletionProvider` begins with `resultSet.stopHere()`, which suppresses every contributor
  ordered after it. Both contributors declare `order="first"` and nothing constrains their
  relative order, so Gauge wins in practice. Completion *inside* quotes — the primary feature — is
  unaffected by this.

### The `java.lang.Enum` project enum browser

Sometimes a step accepts a constant of *any* project enum and the implementation resolves the
enum itself at run time. Declaring the parameter as the raw base class is the signal for that:

```java
@Step("<item> menusune git")
public void goToMenu(Enum item) {
}
```

**Stage 1 — enum class names.** With the caret inside the quotes:

```
* "Pag<caret>" menusune git
```

<kbd>Ctrl</kbd>+<kbd>Space</kbd> lists the project's enum *classes*, with their packages shown in
the popup only:

```
PageItems          enum   com.company.pages
PageItems2         enum   com.company.pages
PageHeaderItems    enum   com.company.common
```

Selecting `PageItems2` inserts just the class name — never the package.

**Stage 2 — that class's constants.** Type a dot and press <kbd>Ctrl</kbd>+<kbd>Space</kbd> again:

```
* "PageItems2.LO<caret>" menusune git   →   LOGIN_BUTTON
                                            LOGOUT_BUTTON
```

Selecting `LOGIN_BUTTON` replaces the whole temporary value:

```
* "LOGIN_BUTTON" menusune git
```

The class name is a browsing namespace inside the IDE only; it never stays in the Gauge file.

Details worth knowing:

- A parameter typed as a **concrete** enum (`PageItems item`) keeps the direct behaviour —
  constants immediately, no class-browsing step. Only exactly `java.lang.Enum` switches modes.
- Only enums from the current module, its dependencies and the project's own sources are listed.
  JDK, IntelliJ platform, Gauge and third-party library enums are never offered.
- Stage 2 resolves the named class directly through the Java short-name index and reads only that
  class's constants — it never re-enumerates the project's enums, so the path stays cheap in large
  projects.
- Two enums sharing a short name (`com.foo.web.Screens`, `com.foo.mobile.Screens`) are listed
  separately with their packages. If such a name is typed by hand and stays ambiguous, **no**
  constants are offered rather than the wrong enum's; picking the class from the list first, or
  typing the fully qualified name (`com.foo.mobile.Screens.`), resolves it.
- A `java.lang.Enum` parameter is never flagged by the inspection: there is no single legal value
  set to validate against, and intermediate text such as `PageItems2.` must not light up red.

## Installation

### Option 1 — use the prebuilt ZIP

1. Download [`release/gauge-typed-parameters-1.1.0.zip`](release/gauge-typed-parameters-1.1.0.zip)
2. In IntelliJ IDEA: **Settings → Plugins → gear icon → Install Plugin from Disk…**
3. Select the ZIP
4. Restart IntelliJ IDEA

### Option 2 — use the unpacked distribution

[`release-unpacked/`](release-unpacked) contains the exact, unmodified contents of that ZIP with
the directory structure preserved. Use it if you would rather inspect the artifact, repack it
yourself, or drop the plugin folder straight into the IDE's `plugins` directory. See
[`release-unpacked/README.md`](release-unpacked/README.md) for both routes — including how to
recreate the ZIP correctly on Windows (the plugin folder must sit at the archive root).

### Requirements

- IntelliJ IDEA **2026.2.x** (build 262)
- The official **Gauge** IntelliJ plugin installed (a required dependency — the IDE will refuse to
  load this plugin without it)
- A Gauge project whose step implementations are written in Java

Try it against the included [`sample-project/`](sample-project), which has the enums, step
implementations and a `sample.spec` covering every supported case.

## Building from source

IntelliJ Platform 2026.2 **requires JDK 25** for plugin development — its artifacts are Java 25
bytecode, and JetBrains documents the requirement in
[build number ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html).
An older JDK cannot compile against it.

`build.cmd` uses a **project-local JDK 25 for the build only**. It sets `JAVA_HOME` and `PATH`
inside `setlocal`, so both are restored when it exits — your system Java installation, global
`JAVA_HOME` and default `java` are never modified. It looks for a JDK 25 in this order and accepts
the first whose `release` file reports `JAVA_VERSION="25…"`:

1. `<project>\jdk` — project-local, preferred (git-ignored)
2. `%JDK25_HOME%`
3. `%USERPROFILE%\.jdks\*`
4. `C:\Program Files\{Eclipse Adoptium,Java,Microsoft,Amazon Corretto,Zulu,JetBrains\jbr}\*`

If none is found it stops before invoking Gradle and prints exactly what to download and where to
put it. Extracting a JDK 25 **zip** (not the installer) into `<project>\jdk` is the least invasive
option.

```
build.cmd
```

or manually:

```
gradlew.bat clean test buildPlugin
```

Result:

```
build/distributions/gauge-typed-parameters-1.1.0.zip
```

### Toolchain

| Component                       | Version | Why                                                      |
|---------------------------------|---------|----------------------------------------------------------|
| Gradle                          | 9.5.0   | runs on JDK 25 (needs ≥ 9.1.0); Kotlin 2.4's max Gradle   |
| IntelliJ Platform Gradle Plugin | 2.18.1  | requires Gradle ≥ 9.0.0                                   |
| Kotlin Gradle plugin            | 2.4.10  | first line supporting Gradle 9; supports `jvmTarget = 25` |
| Build JDK                       | **25**  | required by IntelliJ Platform 2026.2                      |

Target platform and Gauge version live in `gradle.properties` and are all overridable with `-P`.

The build forces an invariant locale (`-Duser.language=en -Duser.country=US`). This is required,
not cosmetic: `intellij-plugin-structure` parses the IDE's `plugin.xml` with a locale-sensitive
`toUpperCase()`, and under a Turkish default locale `"application"` uppercases to `APPLİCATİON`
with a dotted capital İ, which breaks the IDE descriptor parse.

### Tests

```
gradlew.bat test          # IntelliJ fixture tests + plain JUnit
```

`tools/local-verification/run.sh` additionally exercises the plugin's pure decision logic
(templates, type classification, prefix matching, validation) against a stub API tree — useful in
environments where downloading the IntelliJ Platform is not an option.

## Architecture

```
caret inside a Gauge parameter (.spec or .cpt)
  → SpecStep / ConceptStep       the enclosing step invocation, via GaugeDialect
  → canonical template           * "CHROME" ile "3" kere "LOG|" …  →  {} ile {} kere {} …
  → placeholder index            which parameter the caret is in (here: 2)
  → Gauge step implementation    the matching @Step-annotated method
  → PsiMethod
  → PsiParameter                 the parameter at that index
  → PsiType
  → specific enum / java.lang.Enum / boolean / numeric / string / unsupported
```

`.spec` and `.cpt` differ only in the first two lines of that pipeline. Gauge models concepts with
a structurally identical PSI hierarchy (`ConceptStep` / `ConceptArg` / `ConceptStaticArg`,
generated from the same grammar shape) and resolves concept steps by wrapping their AST node in a
`SpecStepImpl` and calling the same `StepUtil.findStepImpl`. `GaugeDialect` captures exactly that
correspondence, so everything downstream is shared.

Step resolution tries Gauge's own `StepReference` first — the same path *Go to step
implementation* uses — and falls back to matching the canonical template against every
`@Step("…")` annotation value in scope, so it keeps working when the Gauge daemon is not running.
Caret ownership is decided by PSI ancestry rather than offset containment, because
`TextRange.containsOffset` is inclusive at both ends and would otherwise attribute a caret sitting
just after a closing quote to that parameter.

No Java source is parsed by hand, and this plugin contains no second Gauge parser.

### Gauge APIs reused

- `SpecStep`, `SpecArg`, `SpecStaticArg`, `SpecTable` — the spec PSI
- `ConceptStep`, `ConceptArg`, `ConceptStaticArg`, `ConceptTable` — the concept PSI
- `SpecTokenTypes`, `ConceptTokenTypes` — token/element types
- `Specification.INSTANCE`, `Concept.INSTANCE` — the two languages
- `StepReference`, `ConceptReference` — Gauge's own step → implementation resolution
- `StepUtil.getGaugeStepAnnotationValues` — reads `@Step` values, aliases and constant folding

All Gauge-specific integration is isolated in **`GaugeStepAdapter`** and **`GaugeStepResolver`**.
The completion contributor and the inspection never touch a Gauge class directly. If a future
Gauge release changes these APIs, those two files are the only ones that need to adapt.

### Layout

```
src/main/kotlin/com/company/gauge/typed/
    gauge/GaugeDialect.kt            Specification (.spec) and Concept (.cpt) PSI behind one API
    gauge/GaugeStepAdapter.kt        the ONLY place that touches Gauge PSI types
    gauge/GaugeParameterContext.kt   invocation PSI + placeholder index + prefix
    gauge/GaugeStepResolver.kt       invocation → PsiMethod
    java/JavaStepParameterResolver.kt placeholder index → PsiParameter, PsiType → kind
    model/GaugeParameterKind.kt      enum / boolean / numeric / string / unsupported
    model/GaugeValueValidator.kt     pure validation + quick-fix ranking, no IntelliJ API
    model/TypedParameterResolver.kt  ties it together, fails closed
    completion/…                     contributor, providers, prefix matchers, insert handlers
    enums/GenericEnumBrowser.kt      java.lang.Enum: stage decision logic, injectable
    enums/ProjectEnumClassProvider.kt stage 1: project enum classes, index backed + cached
    enums/DirectEnumClassResolver.kt stage 2: one class by name, no project scan
    inspection/…                     inspection + replace quick fix
src/test/kotlin/…                    fixture tests + plain JUnit tests
sample-project/                      manual test bed (Gauge + Java)
tools/local-verification/            run the logic with no IntelliJ download
release/                             the built, installable plugin ZIP
release-unpacked/                    the same artifact, extracted, structure preserved
```

## License

[MIT](LICENSE). See [NOTICE](NOTICE) for third-party provenance — in short, no substantial
third-party source is copied here; the Gradle wrapper and the offline API stubs are Apache-2.0 in
origin and are noted there.

Gauge is a trademark of ThoughtWorks, Inc. IntelliJ IDEA is a trademark of JetBrains s.r.o. This
project is not affiliated with or endorsed by either.
