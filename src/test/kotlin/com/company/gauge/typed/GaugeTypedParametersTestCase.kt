package com.company.gauge.typed

import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * The stock light-project descriptors take their SDK from `IdeaTestUtil.getMockJdk*()`, which
 * locates the mock JDK relative to an intellij-community checkout. A plugin build has no such
 * checkout, so the SDK ends up with no roots and `java.lang.*` does not resolve at all: a boxed
 * `Boolean` parameter classifies as UnsupportedKind while a primitive `boolean` works fine,
 * because primitives need no class resolution.
 *
 * Pointing the fixture at the JDK that is running the tests fixes it. Declared at file scope so
 * that every test shares one descriptor instance and the light project is reused between tests.
 */
private val REAL_JDK_DESCRIPTOR: LightProjectDescriptor = object : DefaultLightProjectDescriptor() {
    private val jdk: Sdk by lazy {
        JavaSdk.getInstance().createJdk(
            "gauge-typed-parameters-test-jdk",
            System.getProperty("java.home"),
            false,
        )
    }

    override fun getSdk(): Sdk = jdk
}

/**
 * Common fixture: a fake `com.thoughtworks.gauge.Step` annotation (the real one comes from the
 * gauge-java runtime jar, which a test project does not have) plus helpers to declare enums and
 * step implementations.
 */
abstract class GaugeTypedParametersTestCase : LightJavaCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = REAL_JDK_DESCRIPTOR

    override fun setUp() {
        super.setUp()
        myFixture.addClass(
            """
            package com.thoughtworks.gauge;

            public @interface Step {
                String[] value();
            }
            """.trimIndent(),
        )
    }

    protected fun addElementEnum() {
        myFixture.addClass(
            "package com.example; public enum Element { LOGIN_BUTTON, LOGOUT_BUTTON, SETTINGS_BUTTON }",
        )
    }

    protected fun addBrowserEnum() {
        myFixture.addClass("package com.example; public enum Browser { CHROME, FIREFOX }")
    }

    /**
     * Declares a class with the given method bodies, importing the Gauge Step annotation.
     *
     * The class name is derived from the running test so that two tests can declare the same
     * `@Step` text without a leftover class from a previous test making the step resolve
     * ambiguously (which would silently disable completion).
     *
     * @return the fully qualified name of the generated class
     */
    protected fun addStepImplementation(body: String): String {
        val className = "StepImpl_" + getName().replace(Regex("[^A-Za-z0-9]"), "")
        myFixture.addClass(
            """
            package com.example;

            import com.thoughtworks.gauge.Step;

            public class $className {
            $body
            }
            """.trimIndent(),
        )
        return "com.example.$className"
    }

    protected fun spec(vararg steps: String): String =
        buildString {
            appendLine("# Typed parameters")
            appendLine()
            appendLine("## Scenario")
            appendLine()
            steps.forEach { appendLine(it) }
        }

    /** Runs basic completion and returns the offered lookup strings (never null). */
    protected fun completionStrings(): List<String> {
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }
}
