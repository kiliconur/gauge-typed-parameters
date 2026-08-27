package com.company.gauge.typed

import com.company.gauge.typed.inspection.GaugeTypedParameterInspection

class GaugeTypedParameterInspectionTest : GaugeTypedParametersTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(GaugeTypedParameterInspection())
    }

    // TEST 8 - valid enum value produces nothing
    fun testValidEnumValueIsNotHighlighted() {
        addElementEnum()
        addClickStep()
        myFixture.configureByText("t.spec", spec("""* "LOGIN_BUTTON" elementine tiklanir"""))

        assertEmpty(pluginProblems())
    }

    // TEST 8 - invalid enum value is highlighted
    fun testInvalidEnumValueIsHighlighted() {
        addElementEnum()
        addClickStep()
        myFixture.configureByText("t.spec", spec("""* "LOGNI_BUTTON" elementine tiklanir"""))

        val problems = pluginProblems()
        assertSize(1, problems)
        assertTrue(problems.first(), problems.first().contains("Unknown Element value 'LOGNI_BUTTON'"))
    }

    fun testInvalidEnumValueQuickFixReplacesIt() {
        addElementEnum()
        addClickStep()
        myFixture.configureByText("t.spec", spec("""* "LOGNI_BUTTON" elementine tiklanir"""))

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.contains("LOGIN_BUTTON") }
        assertNotNull("No 'Replace with LOGIN_BUTTON' quick fix offered", fix)
        myFixture.launchAction(fix!!)
        myFixture.checkResult(spec("""* "LOGIN_BUTTON" elementine tiklanir"""))
    }

    fun testInvalidBooleanValueIsHighlighted() {
        addStepImplementation(
            """
                @Step("<enabled> aktif edilir")
                public void enable(boolean enabled) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "tru" aktif edilir"""))

        val problems = pluginProblems()
        assertSize(1, problems)
        assertTrue(problems.first(), problems.first().contains("Invalid boolean value 'tru'"))
    }

    fun testValidBooleanValueIsNotHighlighted() {
        addStepImplementation(
            """
                @Step("<enabled> aktif edilir")
                public void enable(boolean enabled) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "true" aktif edilir"""))

        assertEmpty(pluginProblems())
    }

    fun testInvalidNumericValueIsHighlighted() {
        addStepImplementation(
            """
                @Step("<count> kere denenir")
                public void retry(int count) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "abc" kere denenir"""))

        val problems = pluginProblems()
        assertSize(1, problems)
        assertTrue(problems.first(), problems.first().contains("Invalid int value 'abc'"))
    }

    fun testValidNumericValueIsNotHighlighted() {
        addStepImplementation(
            """
                @Step("<count> kere denenir")
                public void retry(int count) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "3" kere denenir"""))

        assertEmpty(pluginProblems())
    }

    fun testStringParameterIsNeverHighlighted() {
        addStepImplementation(
            """
                @Step("<text> yazilir")
                public void type(String text) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "anything at all" yazilir"""))

        assertEmpty(pluginProblems())
    }

    // TEST 6 / 7 - no implementation, ambiguous implementation: no highlighting, no exception
    fun testUnresolvedStepIsNotHighlighted() {
        addElementEnum()
        myFixture.configureByText("t.spec", spec("""* "LOGNI_BUTTON" hicbir yerde yok"""))

        assertEmpty(pluginProblems())
    }

    fun testAmbiguousImplementationIsNotHighlighted() {
        addElementEnum()
        addBrowserEnum()
        myFixture.addClass(
            """
            package com.example;
            import com.thoughtworks.gauge.Step;
            public class FirstImpl {
                @Step("<element> elementine tiklanir")
                public void a(Element element) {}
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.example;
            import com.thoughtworks.gauge.Step;
            public class SecondImpl {
                @Step("<browser> elementine tiklanir")
                public void b(Browser browser) {}
            }
            """.trimIndent(),
        )
        myFixture.configureByText("t.spec", spec("""* "NOPE" elementine tiklanir"""))

        assertEmpty(pluginProblems())
    }

    private fun addClickStep() {
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
    }

    /** Descriptions of the highlights produced by this plugin's inspection. */
    private fun pluginProblems(): List<String> =
        myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Unknown ") || it.startsWith("Invalid ") }
}
