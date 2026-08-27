# Manual test project

A minimal Gauge/Java project for trying the **Gauge Typed Parameters** plugin by hand.

1. Open this folder as a Maven project in IntelliJ IDEA (it has `manifest.json`, so the
   Gauge plugin recognises it as a Gauge project).
2. Make sure both the official **Gauge** plugin and **Gauge Typed Parameters** are installed.
3. Open `specs/sample.spec`.
4. Put the caret inside a quoted parameter, e.g. `* "LO<caret>" elementine tiklanir`,
   and press <kbd>Ctrl</kbd>+<kbd>Space</kbd>.

Expected: `LOGIN_BUTTON` and `LOGOUT_BUTTON` are offered; picking one produces exactly
`* "LOGIN_BUTTON" elementine tiklanir`.

The last scenario in `sample.spec` contains deliberately wrong values which the inspection
highlights with a "Replace with ..." quick fix.
