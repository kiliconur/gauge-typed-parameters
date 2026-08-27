First release of **Gauge Typed Parameters** — semantic, type-driven completion for Gauge specs in IntelliJ IDEA.

This is a **separate plugin**. The official Gauge plugin is a declared dependency and is never forked, patched or bundled, so you can upgrade it freely.

### Highlights

- **IntelliJ IDEA 2026.2 support** — built against 2026.2.1 (build 262).
- **Semantic enum completion** — the constants of the resolved Java enum parameter, and nothing else. Two enum parameters in the same step never leak candidates into each other.
- **Prefix completion** — `* "LO<caret>" elementine tiklanir` offers `LOGIN_BUTTON` and `LOGOUT_BUTTON`; matching is case-insensitive, insertion is exact, and only the typed prefix is replaced — no duplicated quotes or text.
- **Boolean completion** — `true` / `false` for `boolean` and `Boolean`.
- **Enum inspection and quick fixes** — invalid values are flagged (`Unknown Element value 'LOGNI_BUTTON'`) with ranked *Replace with …* fixes. Numeric and boolean values are validated too.
- **Fails silent** — nothing is suggested or reported while indexing, or when the step is unresolved, ambiguous, or the placeholder count does not match the Java parameter count.

Specs stay plain Gauge — no custom syntax — so they still run unchanged on the standard Gauge runtime.

### Requirements

- IntelliJ IDEA 2026.2.x (build 262)
- The official Gauge IntelliJ plugin
- Java step implementations

### Install

Download `gauge-typed-parameters-1.0.0.zip` below, then **Settings → Plugins → gear → Install Plugin from Disk…** and restart.

### Known limitations

- Java step implementations only; Kotlin is not implemented yet.
- Concept (`.cpt`) files are not supported.
- Auto-quoting outside existing quotes is best-effort and currently inert, because Gauge's own step completion calls `stopHere()` for step-text positions.
