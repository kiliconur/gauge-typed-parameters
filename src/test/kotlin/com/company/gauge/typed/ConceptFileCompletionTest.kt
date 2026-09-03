package com.company.gauge.typed

import com.company.gauge.typed.gauge.GaugeDialect
import com.company.gauge.typed.gauge.GaugeParameterContext
import com.company.gauge.typed.gauge.GaugeStepAdapter

/**
 * The same typed-parameter pipeline, driven from `.cpt` concept files.
 *
 * Nothing here is a second completion engine: the PSI comes from Gauge's Concept grammar
 * (`ConceptStep` / `ConceptArg` / `ConceptStaticArg`), [GaugeDialect] maps it onto the same
 * [GaugeParameterContext] the `.spec` path produces, and everything downstream is shared.
 */
class ConceptFileCompletionTest : GaugeTypedParametersTestCase() {

    // TEST 11 - a concrete enum parameter inside a concept file
    fun testConceptFileCompletesSpecificEnumConstants() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("login.cpt", concept("""* "LO<caret>" elementine tiklanir"""))

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON")
        assertDoesntContain(strings, "SETTINGS_BUTTON")
    }

    fun testConceptFileInsertionReplacesTheTypedPrefix() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("login.cpt", concept("""* "LOG<caret>" elementine tiklanir"""))

        selectLookupItem("LOGIN_BUTTON")

        myFixture.checkResult(concept("""* "LOGIN_BUTTON" elementine tiklanir"""))
    }

    fun testConceptFileKeepsParametersApart() {
        addElementEnum()
        addBrowserEnum()
        addStepImplementation(
            """
                @Step("<browser> ile <element> elementine tiklanir")
                public void click(Browser browser, Element element) {}
            """,
        )
        myFixture.configureByText(
            "login.cpt",
            concept("""* "CHROME" ile "LOG<caret>" elementine tiklanir"""),
        )

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON")
        assertDoesntContain(strings, "CHROME", "FIREFOX")
    }

    // TEST 12 - stage 1 of the project enum browser inside a concept file
    fun testConceptFileOffersProjectEnumClassNames() {
        addProjectEnums()
        addStepImplementation(
            """
                @Step("<item> menusune git")
                public void goToMenu(String item) {}
            """,
        )
        myFixture.configureByText("menu.cpt", concept("""* "Pa<caret>" menusune git"""))

        val strings = completionStrings()
        assertContainsElements(strings, "PageItems", "PageItems2")
        assertDoesntContain(strings, "LOGIN_BUTTON", "SETTINGS_BUTTON")
    }

    // TEST 13 - stage 2 inside a concept file, including the final replacement
    fun testConceptFileCompletesConstantsAfterTheDot() {
        addProjectEnums()
        addStepImplementation(
            """
                @Step("<item> menusune git")
                public void goToMenu(String item) {}
            """,
        )
        myFixture.configureByText("menu.cpt", concept("""* "PageItems2.LO<caret>" menusune git"""))

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON")
        assertDoesntContain(strings, "HOME_BUTTON", "PROFILE_BUTTON", "LOGO")

        selectLookupItem("LOGIN_BUTTON")
        myFixture.checkResult(concept("""* "LOGIN_BUTTON" menusune git"""))
    }

    fun testConceptFileBooleanParameter() {
        addStepImplementation(
            """
                @Step("<enabled> aktif edilir")
                public void enable(boolean enabled) {}
            """,
        )
        myFixture.configureByText("flags.cpt", concept("""* "<caret>" aktif edilir"""))

        assertContainsElements(completionStrings(), "true", "false")
    }

    // TEST 15 - unresolvable steps in a concept file stay silent instead of throwing
    fun testConceptFileWithUnresolvedStepIsSilent() {
        addElementEnum()
        myFixture.configureByText("login.cpt", concept("""* "LO<caret>" hicbir yerde yok"""))

        assertDoesntContain(completionStrings(), "LOGIN_BUTTON", "LOGOUT_BUTTON")
    }

    fun testConceptDynamicParameterIsNotCompleted() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText(
            "login.cpt",
            concept("""* <ele<caret>ment> elementine tiklanir"""),
        )

        // A <dynamic> concept parameter is a placeholder of the concept itself, not a value.
        assertDoesntContain(completionStrings(), "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")
    }

    // --- the shared pipeline, asserted directly on concept PSI ---------------------

    fun testConceptPsiProducesTheSameParameterContext() {
        addElementEnum()
        myFixture.configureByText("login.cpt", concept("""* "LOGIN_BUTTON" elementine tiklanir"""))

        val file = myFixture.file
        val offset = file.text.indexOf("LOGIN_BUTTON")
        val leaf = file.findElementAt(offset)
        assertNotNull("no PSI leaf at the parameter value", leaf)

        val dialect = GaugeDialect.of(leaf!!)
        assertSame("a .cpt file must map to the Concept dialect", GaugeDialect.Cpt, dialect)

        val step = GaugeStepAdapter.findStep(leaf)
        assertNotNull("the concept step was not found", step)

        val template = GaugeStepAdapter.buildTemplate(step!!)
        assertEquals("{} elementine tiklanir", template.text)
        assertEquals(1, template.placeholderCount)

        val context = GaugeParameterContext.atCaret(leaf, offset + 3, allowOutsideQuotes = false)
        assertNotNull("no parameter context built from concept PSI", context)
        assertEquals(0, context!!.placeholderIndex)
        assertEquals("LOG", context.prefix)
        assertTrue(context.insideQuotes)
        assertEquals(offset, context.valueStartOffset)
    }

    fun testSpecPsiStillMapsToTheSpecDialect() {
        addElementEnum()
        myFixture.configureByText("t.spec", spec("""* "LOGIN_BUTTON" elementine tiklanir"""))

        val leaf = myFixture.file.findElementAt(myFixture.file.text.indexOf("LOGIN_BUTTON"))
        assertNotNull(leaf)
        assertSame(GaugeDialect.Spec, GaugeDialect.of(leaf!!))
    }
}
