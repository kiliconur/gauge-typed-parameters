import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    // 2.4.x is the first Kotlin line that officially supports Gradle 9 (7.6.3 - 9.5.0).
    // Kotlin 2.1.x tops out at Gradle 8.10 and cannot be used with the Gradle 9 that
    // IntelliJ Platform Gradle Plugin 2.18.1 requires.
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Source/target bytecode level. Driven by gradle.properties so that the compile-against
// platform and the bytecode level stay in sync (IntelliJ Platform 2026.2 -> 25).
val javaVersion: String = providers.gradleProperty("javaVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Since 2025.3 (build 253) IntelliJ IDEA ships as ONE unified distribution and the
        // separate Community artifact (type "IC") is no longer published - resolving it fails
        // with "Couldn't resolve IntellijIdeaCommunity download URL". intellijIdea() maps to
        // IntelliJPlatformType.IntellijIdea (com.jetbrains.intellij.idea:idea), which is the
        // correct dependency for 2026.2.1.
        //
        // useInstaller = false takes the IDE archive from the IntelliJ Maven repository rather
        // than the OS installer. Both parse correctly; the archive is the classic
        // plugin-development distribution and is smaller to fetch. Set -PuseInstaller=true to
        // switch back.
        intellijIdea(providers.gradleProperty("platformVersion")) {
            useInstaller = providers.gradleProperty("useInstaller").map(String::toBoolean)
        }

        // Java PSI (PsiMethod / PsiParameter / PsiEnumConstant) lives in the Java plugin.
        bundledPlugin("com.intellij.java")

        // The existing Gauge plugin. Declared as a normal marketplace dependency,
        // never bundled into our own artifact.
        plugin(
            providers.gradleProperty("gaugePluginId").get(),
            providers.gradleProperty("gaugePluginVersion").get(),
        )

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    // kotlin-stdlib is provided by the IntelliJ Platform (kotlin.stdlib.default.dependency=false).
    testImplementation("junit:junit:4.13.2")
}

// No Java toolchain is declared on purpose: the build compiles with whatever JDK runs Gradle.
// That keeps the Java 17 build machine working without provisioning or requiring a second JDK.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion)
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.company.gauge.typed.parameters"
        name = "Gauge Typed Parameters"
        version = project.version.toString()

        description =
            """
            Adds semantic parameter completion and validation to Gauge specifications based on
            Gauge Java step implementation parameter types.
            <br/>
            When the caret sits inside a Gauge step parameter, the plugin resolves the step
            implementation, maps the placeholder under the caret to the matching Java
            <code>PsiParameter</code>, and offers completion derived from its type:
            <ul>
              <li>Java enums &rarr; the enum constants</li>
              <li><code>boolean</code>/<code>Boolean</code> &rarr; <code>true</code> / <code>false</code></li>
            </ul>
            An inspection flags parameter values that cannot possibly be valid for the resolved
            Java type, with a "Replace with &hellip;" quick fix.
            <br/>
            Standard Gauge syntax is untouched - specs stay runnable by the plain Gauge runtime.
            """.trimIndent()

        changeNotes = "Initial release."

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }

        vendor {
            name = "Gauge Typed Parameters"
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

/**
 * Diagnostic only - prints the layout of every IntelliJ IDEA distribution Gradle has extracted.
 *
 * Kept because it is handy when the platform dependency will not parse: it resolves nothing,
 * so it runs even when the IDE cannot be read. Prints `product-info.json`, `lib/` and
 * `modules/` so the actual layout can be compared against what the parser expects.
 * Run it on demand with `gradlew diagnosePlatform`; it is not part of the normal build.
 */
tasks.register("diagnosePlatform") {
    notCompatibleWithConfigurationCache("diagnostic task, reads the Gradle cache directly")
    doLast {
        val caches = File(System.getProperty("user.home"), ".gradle/caches")
        println("== scanning $caches for extracted IDEs ==")
        if (!caches.isDirectory) {
            println("   cache directory not found")
            return@doLast
        }

        val ides = caches.walkTopDown()
            .maxDepth(6)
            .onEnter { it.name != "modules-2" && it.name != "journal-1" }
            .filter { it.isDirectory && it.name.startsWith("idea-") && File(it, "lib").isDirectory }
            .toList()

        if (ides.isEmpty()) {
            println("   no extracted IDE found")
            return@doLast
        }

        ides.forEach { ide ->
            println()
            println("IDE: ${ide.absolutePath}")
            println("  top level: " + (ide.list()?.sorted()?.joinToString(", ") ?: "?"))

            val productInfo = File(ide, "product-info.json")
            println("  product-info.json present: ${productInfo.exists()} (${productInfo.length()} bytes)")
            if (productInfo.exists()) {
                println("  --- product-info.json head ---")
                productInfo.useLines { lines ->
                    lines.take(25).forEach { println("  | " + it.take(200)) }
                }
                println("  --- keys ---")
                val text = productInfo.readText()
                listOf("productCode", "version", "buildNumber", "layout", "modules", "bundledPlugins", "launch")
                    .forEach { key -> println("  | \"$key\" present: ${text.contains("\"$key\"")}") }
            }

            val lib = File(ide, "lib")
            val libJars = lib.list()?.sorted().orEmpty()
            println("  lib/ entries: ${libJars.size}")
            println("  lib/ notable: " + libJars.filter {
                it.contains("app") || it.contains("product") || it.contains("core") ||
                    it.contains("platform") || it.contains("customization")
            }.joinToString(", ").ifEmpty { "(none matched)" })

            val modules = File(ide, "modules")
            if (modules.isDirectory) {
                val moduleJars = modules.list()?.sorted().orEmpty()
                println("  modules/ entries: ${moduleJars.size}")
                println("  modules/ first 15: " + moduleJars.take(15).joinToString(", "))
            } else {
                println("  modules/ absent")
            }

            val javaPlugin = File(ide, "plugins/java")
            println("  plugins/java present: ${javaPlugin.isDirectory}")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks {
    test {
        useJUnit()
        systemProperty("java.awt.headless", "true")
        // Test workers are forked JVMs and do not inherit org.gradle.jvmargs, so the
        // invariant locale has to be set here too - see the note in gradle.properties.
        jvmArgs("-Duser.language=en", "-Duser.country=US")
        defaultCharacterEncoding = "UTF-8"
        // IntelliJ fixture tests are not parallel-safe.
        maxParallelForks = 1
    }

    // The step-resolution index and the sample project are not part of the artifact.
    buildPlugin {
        archiveBaseName.set("gauge-typed-parameters")
    }
}
