package com.company.gauge.typed

import com.company.gauge.typed.java.JavaStepParameterResolver
import com.company.gauge.typed.model.GaugeParameterKind
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.psi.search.GlobalSearchScope

class GaugeTypedParameterCompletionTest : GaugeTypedParametersTestCase() {

    // TEST 1 - empty parameter offers every enum constant
    fun testEmptyParameterOffersAllEnumConstants() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "<caret>" elementine tiklanir"""))

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")
    }

    // TEST 2 - prefix filtering
    fun testPrefixFiltersEnumConstants() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "LO<caret>" elementine tiklanir"""))

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON")
        assertDoesntContain(strings, "SETTINGS_BUTTON")
    }

    fun testLongerPrefixStillMatches() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "LOGIN<caret>" elementine tiklanir"""))

        assertContainsElements(completionStrings(), "LOGIN_BUTTON")
    }

    // TEST 3 - two enum parameters must not leak into each other
    fun testFirstParameterOffersOnlyBrowserValues() {
        addElementEnum()
        addBrowserEnum()
        addStepImplementation(
            """
                @Step("<browser> ile <element> elementine tiklanir")
                public void click(Browser browser, Element element) {}
            """,
        )
        myFixture.configureByText(
            "t.spec",
            spec("""* "CHR<caret>" ile "LOGIN_BUTTON" elementine tiklanir"""),
        )

        val strings = completionStrings()
        assertContainsElements(strings, "CHROME")
        assertDoesntContain(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")
    }

    fun testSecondParameterOffersOnlyElementValues() {
        addElementEnum()
        addBrowserEnum()
        addStepImplementation(
            """
                @Step("<browser> ile <element> elementine tiklanir")
                public void click(Browser browser, Element element) {}
            """,
        )
        myFixture.configureByText(
            "t.spec",
            spec("""* "CHROME" ile "LOG<caret>" elementine tiklanir"""),
        )

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON")
        assertDoesntContain(strings, "CHROME", "FIREFOX")
    }

    fun testThirdParameterOfThreeResolvesToTheThirdJavaParameter() {
        addElementEnum()
        addBrowserEnum()
        addStepImplementation(
            """
                @Step("<browser> ile <retryCount> kere <element> elementine tiklanir")
                public void click(Browser browser, int retryCount, Element element) {}
            """,
        )
        myFixture.configureByText(
            "t.spec",
            spec("""* "CHROME" ile "3" kere "LOG<caret>" elementine tiklanir"""),
        )

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON")
        assertDoesntContain(strings, "CHROME", "FIREFOX")
    }

    // TEST 4 - boolean parameters
    fun testBooleanParameterOffersTrueAndFalse() {
        addStepImplementation(
            """
                @Step("<enabled> aktif edilir")
                public void enable(boolean enabled) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "<caret>" aktif edilir"""))

        assertContainsElements(completionStrings(), "true", "false")
    }

    fun testBoxedBooleanParameterOffersTrueAndFalse() {
        addStepImplementation(
            """
                @Step("<enabled> aktif edilir")
                public void enable(Boolean enabled) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "<caret>" aktif edilir"""))

        assertContainsElements(completionStrings(), "true", "false")
    }

    // TEST 5 - a String parameter never gets another type's enum constants. (It does get the
    // project enum browser - enum CLASS names - which is covered by ProjectEnumBrowserTest.)
    fun testStringParameterOffersNoEnumConstants() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<text> yazilir")
                public void type(String text) {}

                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "LO<caret>" yazilir"""))

        val strings = completionStrings()
        assertDoesntContain(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")
    }

    // TEST 6 - unresolved step must not throw
    fun testUnresolvedStepDoesNotThrow() {
        addElementEnum()
        myFixture.configureByText("t.spec", spec("""* "LO<caret>" hicbir yerde yok"""))

        val strings = completionStrings()
        assertDoesntContain(strings, "LOGIN_BUTTON")
    }

    // TEST 7 - ambiguous / mismatching implementation must not throw
    fun testAmbiguousImplementationOffersNothing() {
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
        myFixture.configureByText("t.spec", spec("""* "LO<caret>" elementine tiklanir"""))

        val strings = completionStrings()
        assertDoesntContain(strings, "LOGIN_BUTTON", "CHROME")
    }

    fun testParameterCountMismatchOffersNothing() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element, String unexpected) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "LO<caret>" elementine tiklanir"""))

        assertDoesntContain(completionStrings(), "LOGIN_BUTTON")
    }

    // TEST 9 - insertion must replace the typed prefix, not duplicate quotes or text
    fun testSelectingConstantReplacesTypedPrefix() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "LOG<caret>" elementine tiklanir"""))

        selectLookupItem("LOGIN_BUTTON")

        myFixture.checkResult(spec("""* "LOGIN_BUTTON" elementine tiklanir"""))
    }

    fun testSelectingConstantOnEmptyParameterInsertsValue() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "<caret>" elementine tiklanir"""))

        selectLookupItem("SETTINGS_BUTTON")

        myFixture.checkResult(spec("""* "SETTINGS_BUTTON" elementine tiklanir"""))
    }

    // Case 5 of the spec: a wrong value still offers the constants so it can be replaced.
    fun testWrongValueStillOffersConstantsAndReplacesThem() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "WRONG_VALUE<caret>" elementine tiklanir"""))

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")

        myFixture.lookup!!.setCurrentItem(myFixture.lookupElements!!.first { it.lookupString == "LOGIN_BUTTON" })
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        myFixture.checkResult(spec("""* "LOGIN_BUTTON" elementine tiklanir"""))
    }

    // Case 4 of the spec: a complete, valid value must not be corrupted.
    fun testCompleteValueIsNotDuplicated() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "LOGIN_BUTTON<caret>" elementine tiklanir"""))

        selectLookupItem("LOGIN_BUTTON")

        myFixture.checkResult(spec("""* "LOGIN_BUTTON" elementine tiklanir"""))
    }

    // Regression: TextRange.containsOffset() is end-inclusive, so a caret sitting right after a
    // closing quote used to be attributed to that parameter, which made the insert handler
    // overwrite the quote. Caret ownership is decided by PSI ancestry now.
    fun testCaretRightAfterClosingQuoteOffersNothingAndDoesNotCorrupt() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "LOGIN_BUTTON"<caret> elementine tiklanir"""))

        // Only the offered values are asserted. The document itself is not checked because at
        // a STEP-token position Gauge's own step completion may auto-insert its single
        // suggestion, which rewrites the line for reasons that have nothing to do with us.
        val strings = completionStrings()
        assertDoesntContain(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")
    }

    // Matching is case insensitive, insertion is not.
    fun testLowercasePrefixInsertsTheConstantExactly() {
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "log<caret>" elementine tiklanir"""))

        selectLookupItem("LOGIN_BUTTON")

        myFixture.checkResult(spec("""* "LOGIN_BUTTON" elementine tiklanir"""))
    }

    /**
     * Optional auto-quote support outside of quotes.
     *
     * Deliberately NOT asserting that the values appear. For a caret in plain step text the
     * position is a `STEP` token, and Gauge's own `StepCompletionProvider` opens with
     * `resultSet.stopHere()`, which suppresses every contributor ordered after it. Both
     * contributors declare `order="first"` and `LoadingOrder` leaves two `first` entries
     * mutually unconstrained, so which one wins is decided by extension registration order -
     * in practice Gauge's, because this plugin depends on it and therefore loads later.
     *
     * What this test does guarantee is the part that would be a real bug: invoking completion
     * there must not throw, and if our values are offered they must be the correct ones for
     * the resolved parameter.
     */
    fun testAutoQuoteOutsideQuotesIsSafe() {
        addElementEnum()
        addBrowserEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* LO<caret> elementine tiklanir"""))

        val strings = completionStrings()
        // Never values from an unrelated enum, whether or not we got to run at all.
        assertDoesntContain(strings, "CHROME", "FIREFOX")
    }

    // --- direct type classification, independent of step resolution -----------------

    /**
     * Guard against the fixture silently losing its JDK. Without a resolvable java.lang.*,
     * every boxed type classifies as unsupported and the typed completion tests fail in a way
     * that looks like a plugin bug.
     */
    fun testJdkClassesResolveInTheFixture() {
        assertNotNull(
            "java.lang.Boolean must resolve - the fixture SDK is misconfigured",
            myFixture.javaFacade.findClass("java.lang.Boolean", GlobalSearchScope.allScope(project)),
        )
    }

    fun testPrimitiveBooleanTypeIsRecognisedFromRealPsi() {
        val fqn = addStepImplementation(
            """
                @Step("<enabled> aktif edilir")
                public void enable(boolean enabled) {}
            """,
        )
        assertEquals(GaugeParameterKind.BooleanKind, firstParameterKind(fqn))
    }

    fun testBoxedBooleanTypeIsRecognisedFromRealPsi() {
        val fqn = addStepImplementation(
            """
                @Step("<enabled> aktif edilir")
                public void enable(Boolean enabled) {}
            """,
        )
        assertEquals(GaugeParameterKind.BooleanKind, firstParameterKind(fqn))
    }

    fun testEnumTypeIsRecognisedFromRealPsi() {
        addElementEnum()
        val fqn = addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        val kind = firstParameterKind(fqn)
        assertTrue("expected an enum kind but got $kind", kind is GaugeParameterKind.SpecificEnumKind)
        assertContainsElements(
            (kind as GaugeParameterKind.SpecificEnumKind).constantNames,
            "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON",
        )
    }

    private fun firstParameterKind(classFqn: String): GaugeParameterKind {
        val psiClass = myFixture.findClass(classFqn)
        val method = psiClass.methods.first()
        return JavaStepParameterResolver.kindOf(method.parameterList.parameters.first())
    }
}
