# Unpacked distribution

This folder holds the **exact, unmodified contents** of
[`../release/gauge-typed-parameters-1.1.0.zip`](../release/gauge-typed-parameters-1.1.0.zip),
extracted with the directory structure preserved:

```
release-unpacked/
└── gauge-typed-parameters/
    └── lib/
        └── gauge-typed-parameters-1.1.0.jar
```

The jar is byte-for-byte the one produced by `gradlew buildPlugin`:

```
sha256  f9202c9a78dc999877e285f8b04a81757b087c53d875b30d1599a77bb2744cf6
size    82904 bytes
```

Nothing here was recompiled, repacked or otherwise touched.

## Recreating the ZIP on Windows

1. Open the `release-unpacked` folder.
2. Select **all contents inside the folder** — that is the `gauge-typed-parameters`
   folder itself. Do **not** select or compress the outer `release-unpacked` folder.
3. Right-click → **Send to** → **Compressed (zipped) folder**.
4. Rename the result to:

   ```
   gauge-typed-parameters-1.1.0.zip
   ```

### Why step 2 matters

An IntelliJ plugin ZIP must have the plugin directory at its **root**. The archive has to
look exactly like this:

```
gauge-typed-parameters-1.1.0.zip
└── gauge-typed-parameters/
    └── lib/
        └── gauge-typed-parameters-1.1.0.jar
```

If you compress the outer folder instead, you get an extra `release-unpacked/` level at the
root and IntelliJ IDEA will reject the plugin.

### Verifying the result

Open the new ZIP and confirm the first entry is the `gauge-typed-parameters` folder — not
`release-unpacked`. On PowerShell you can list it without extracting:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::OpenRead("gauge-typed-parameters-1.1.0.zip").Entries |
    Select-Object FullName, Length
```

A rebuilt ZIP will not be byte-identical to the original — archive timestamps, entry order
and compression settings differ between tools — but it is functionally identical, because the
jar inside is unchanged. That jar is what IntelliJ IDEA actually loads.

## Installing without repacking

You do not have to build a ZIP at all. This folder is already in the exact layout the IDE
uses, so you can copy the `gauge-typed-parameters` folder straight into IntelliJ IDEA's
plugins directory and restart:

```
%APPDATA%\JetBrains\IntelliJIdea2026.2\plugins\gauge-typed-parameters\lib\gauge-typed-parameters-1.1.0.jar
```

The supported route remains **Settings → Plugins → gear icon → Install Plugin from Disk…**
with the ZIP from [`../release/`](../release).

Either way the official Gauge plugin must be installed as well — it is a required dependency.
