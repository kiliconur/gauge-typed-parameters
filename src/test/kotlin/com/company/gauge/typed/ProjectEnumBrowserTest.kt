package com.company.gauge.typed

import com.company.gauge.typed.enums.DirectEnumClassResolver
import com.company.gauge.typed.enums.EnumClassCatalog
import com.company.gauge.typed.enums.EnumClassLookup
import com.company.gauge.typed.enums.ProjectEnumBrowser
import com.company.gauge.typed.enums.ProjectEnumCandidates
import com.company.gauge.typed.java.JavaStepParameterResolver
import com.company.gauge.typed.model.GaugeParameterKind
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

/**
 * The project enum browser offered on `String` parameters: enum CLASS names first, that class's
 * constants after the dot, and the class name never survives in the Gauge file.
 *
 * The parameter itself stays free text - see the "unrestricted" tests at the end and
 * [GaugeTypedParameterInspectionTest], which asserts nothing is ever flagged.
 */
class ProjectEnumBrowserTest : GaugeTypedParametersTestCase() {

    private fun addStringStep(): String = addStepImplementation(
        """
            @Step("<item> menusune git")
            public void goToMenu(String item) {}
        """,
    )

    // TEST 2 - stage 1 offers enum class names, never constants
    fun testStringParameterOffersProjectEnumClassNames() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "Pa<caret>" menusune git"""))

        val strings = completionStrings()
        assertContainsElements(strings, "PageItems", "PageItems2")
        assertDoesntContain(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON", "HOME_BUTTON")
    }

    fun testEmptyStringValueOffersEveryProjectEnum() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "<caret>" menusune git"""))

        assertContainsElements(completionStrings(), "PageItems", "PageItems2", "HeaderItems")
    }

    fun testSelectingAnEnumClassInsertsOnlyItsSimpleName() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "PageItems2<caret>" menusune git"""))

        selectLookupItem("PageItems2")

        // No package, no quotes added, nothing duplicated.
        myFixture.checkResult(spec("""* "PageItems2" menusune git"""))
    }

    // TEST 3 - two enum classes with the same short name stay distinguishable in the popup
    fun testSameShortNameClassesKeepTheirPackageInThePresentation() {
        addAmbiguousEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "Scr<caret>" menusune git"""))

        val elements = myFixture.completeBasic()
        assertNotNull("expected a lookup for the enum class names", elements)
        val tails = elements!!
            .filter { it.lookupString == "Screens" }
            .map { LookupElementPresentation.renderElement(it).tailText.orEmpty() }

        assertEquals("both Screens enums must be offered, got $tails", 2, tails.size)
        assertTrue("web package missing from $tails", tails.any { it.contains("com.foo.web") })
        assertTrue("mobile package missing from $tails", tails.any { it.contains("com.foo.mobile") })

        val types = elements
            .filter { it.lookupString == "Screens" }
            .map { LookupElementPresentation.renderElement(it).typeText }
        assertTrue("the popup must say these are enums, got $types", types.all { it == "enum" })
    }

    // TEST 4 - stage 2 offers ONLY the named class's constants
    fun testDotSwitchesToTheConstantsOfThatEnumOnly() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "PageItems2.LO<caret>" menusune git"""))

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON")
        assertDoesntContain(strings, "SETTINGS_BUTTON", "HOME_BUTTON", "PROFILE_BUTTON", "LOGO", "SEARCH_BOX")
        assertDoesntContain(strings, "PageItems", "PageItems2", "HeaderItems")
    }

    // TEST 5 - the class name is only a browsing namespace and must not survive
    fun testSelectingAConstantReplacesTheWholeQualifiedValue() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "PageItems2.LO<caret>" menusune git"""))

        selectLookupItem("LOGIN_BUTTON")

        myFixture.checkResult(spec("""* "LOGIN_BUTTON" menusune git"""))
    }

    // TEST 6 - empty suffix
    fun testSelectingAConstantAfterABareDotReplacesTheClassName() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "PageItems2.<caret>" menusune git"""))

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")

        selectLookupItem("SETTINGS_BUTTON")
        myFixture.checkResult(spec("""* "SETTINGS_BUTTON" menusune git"""))
    }

    // TEST 7 - a wrong suffix still offers everything, and replacing it is clean
    fun testWrongSuffixStillOffersTheConstantsAndReplacesThem() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "PageItems2.WRONG<caret>" menusune git"""))

        val strings = completionStrings()
        assertContainsElements(strings, "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")

        selectLookupItem("LOGOUT_BUTTON")
        myFixture.checkResult(spec("""* "LOGOUT_BUTTON" menusune git"""))
    }

    fun testStage2MatchingIsCaseInsensitiveButInsertsTheExactConstant() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "PageItems2.log<caret>" menusune git"""))

        selectLookupItem("LOGIN_BUTTON")
        myFixture.checkResult(spec("""* "LOGIN_BUTTON" menusune git"""))
    }

    // TEST 8 - after the dot nothing may rebuild the project enum catalogue
    fun testStage2NeverConsultsTheProjectCatalogue() {
        addProjectEnums()
        val anchor: PsiElement = myFixture.findClass("com.example.pages.PageItems2")

        val catalog = object : EnumClassCatalog {
            var calls = 0
            override fun enumClasses(context: PsiElement): List<PsiClass> {
                calls++
                return emptyList()
            }
        }
        val browser = ProjectEnumBrowser(catalog, DirectEnumClassResolver.getInstance(project))

        val candidates = browser.candidatesFor(anchor, "PageItems2.LO")

        assertTrue("expected constants, got $candidates", candidates is ProjectEnumCandidates.Constants)
        assertContainsElements(
            (candidates as ProjectEnumCandidates.Constants).names,
            "LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON",
        )
        assertEquals("stage 2 must not enumerate the project's enums", 0, catalog.calls)
    }

    fun testStage1DoesConsultTheProjectCatalogue() {
        addProjectEnums()
        val anchor: PsiElement = myFixture.findClass("com.example.pages.PageItems2")
        val browser = ProjectEnumBrowser(
            com.company.gauge.typed.enums.ProjectEnumClassProvider.getInstance(project),
            DirectEnumClassResolver.getInstance(project),
        )

        val candidates = browser.candidatesFor(anchor, "Pa")

        assertTrue("expected class names, got $candidates", candidates is ProjectEnumCandidates.Classes)
        val names = (candidates as ProjectEnumCandidates.Classes).classes.mapNotNull { it.name }
        assertContainsElements(names, "PageItems", "PageItems2", "HeaderItems")
    }

    // The direct resolver is index based and must not be fooled by non-enum classes.
    fun testDirectResolverFindsOnlyEnums() {
        addProjectEnums()
        val resolver = DirectEnumClassResolver.getInstance(project)
        val anchor: PsiElement = myFixture.findClass("com.example.pages.PageItems2")

        val found = resolver.resolve(anchor, "PageItems2")
        assertTrue("PageItems2 must resolve, got $found", found is EnumClassLookup.Found)
        assertEquals(
            "com.example.pages.PageItems2",
            (found as EnumClassLookup.Found).psiClass.qualifiedName,
        )

        assertEquals(EnumClassLookup.NotFound, resolver.resolve(anchor, "NoSuchEnumAnywhere"))
    }

    // TEST 14 - the same short name in two packages is never guessed
    fun testAmbiguousShortNameOffersNoConstants() {
        addAmbiguousEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "Screens.<caret>" menusune git"""))

        val strings = completionStrings()
        assertDoesntContain(strings, "WEB_ONLY", "MOBILE_ONLY", "WEB_HOME", "MOBILE_HOME")
    }

    fun testAmbiguousShortNameIsReportedByTheResolver() {
        addAmbiguousEnums()
        val resolver = DirectEnumClassResolver.getInstance(project)
        val anchor: PsiElement = myFixture.findClass("com.foo.web.Screens")

        val lookup = resolver.resolve(anchor, "Screens")
        assertTrue("expected an ambiguity, got $lookup", lookup is EnumClassLookup.Ambiguous)
        assertEquals(2, (lookup as EnumClassLookup.Ambiguous).classes.size)
    }

    fun testFullyQualifiedNameResolvesTheAmbiguity() {
        addAmbiguousEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "com.foo.mobile.Screens.MOB<caret>" menusune git"""))

        val strings = completionStrings()
        assertContainsElements(strings, "MOBILE_HOME", "MOBILE_ONLY")
        assertDoesntContain(strings, "WEB_HOME", "WEB_ONLY")

        selectLookupItem("MOBILE_ONLY")
        myFixture.checkResult(spec("""* "MOBILE_ONLY" menusune git"""))
    }

    // TEST 1 (regression) - a concrete enum parameter keeps the old, direct behaviour
    fun testSpecificEnumParameterStillCompletesConstantsDirectly() {
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
        assertDoesntContain(strings, "SETTINGS_BUTTON", "Element")

        selectLookupItem("LOGIN_BUTTON")
        myFixture.checkResult(spec("""* "LOGIN_BUTTON" elementine tiklanir"""))
    }

    // TEST 9 - a concrete enum parameter must NOT open the class browser
    fun testSpecificEnumParameterDoesNotOpenTheClassBrowser() {
        addProjectEnums()
        addElementEnum()
        addStepImplementation(
            """
                @Step("<element> elementine tiklanir")
                public void click(Element element) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "Pa<caret>" elementine tiklanir"""))

        // Its own constants only - never enum class names from elsewhere in the project.
        assertDoesntContain(completionStrings(), "PageItems", "PageItems2", "HeaderItems")
    }

    // The parameter is free text: text that matches no enum class name offers nothing at all,
    // instead of popping up the whole project enum catalogue.
    fun testFreeTextThatMatchesNoEnumClassOffersNothing() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "custom valu<caret>" menusune git"""))

        assertDoesntContain(
            completionStrings(),
            "PageItems", "PageItems2", "HeaderItems", "LOGIN_BUTTON", "HOME_BUTTON",
        )
    }

    fun testFreeTextIsNeverRewrittenByTheBrowser() {
        addProjectEnums()
        addStringStep()
        myFixture.configureByText("t.spec", spec("""* "abc123<caret>" menusune git"""))

        completionStrings()

        // Nothing was inserted or replaced: the value the user typed is still exactly there.
        myFixture.checkResult(spec("""* "abc123" menusune git"""))
    }

    // TEST 10 - boolean parameters are untouched
    fun testBooleanParameterStillOffersTrueAndFalse() {
        addProjectEnums()
        addStepImplementation(
            """
                @Step("<enabled> aktif edilir")
                public void enable(boolean enabled) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "<caret>" aktif edilir"""))

        val strings = completionStrings()
        assertContainsElements(strings, "true", "false")
        assertDoesntContain(strings, "PageItems", "PageItems2")
    }

    // TEST 15 - nothing resolvable must never throw
    fun testUnresolvedStringStepIsSilent() {
        addProjectEnums()
        myFixture.configureByText("t.spec", spec("""* "PageItems2.LO<caret>" hicbir yerde yok"""))

        val strings = completionStrings()
        assertDoesntContain(strings, "LOGIN_BUTTON", "PageItems2")
    }

    fun testParameterCountMismatchOffersNoEnumClasses() {
        addProjectEnums()
        addStepImplementation(
            """
                @Step("<item> menusune git")
                public void goToMenu(String item, String unexpected) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "Pa<caret>" menusune git"""))

        assertDoesntContain(completionStrings(), "PageItems", "PageItems2")
    }

    // Type classification, independent of completion
    fun testStringParameterIsTheBrowserKind() {
        val fqn = addStringStep()
        val method = myFixture.findClass(fqn).methods.first()
        assertEquals(
            GaugeParameterKind.StringEnumBrowserKind,
            JavaStepParameterResolver.kindOf(method.parameterList.parameters.first()),
        )
    }

    // java.lang.Enum carries no special meaning any more - Gauge's own runtime conversion
    // cannot handle such a parameter, so it is just another unsupported type.
    fun testRawEnumParameterIsUnsupported() {
        val fqn = addStepImplementation(
            """
                @Step("<item> menusune gider")
                public void goToMenuRaw(Enum item) {}
            """,
        )
        val method = myFixture.findClass(fqn).methods.first()
        assertEquals(
            GaugeParameterKind.UnsupportedKind,
            JavaStepParameterResolver.kindOf(method.parameterList.parameters.first()),
        )
    }

    fun testRawEnumParameterOffersNothing() {
        addProjectEnums()
        addStepImplementation(
            """
                @Step("<item> menusune gider")
                public void goToMenuRaw(Enum item) {}
            """,
        )
        myFixture.configureByText("t.spec", spec("""* "Pa<caret>" menusune gider"""))

        assertDoesntContain(completionStrings(), "PageItems", "PageItems2", "HeaderItems")
    }

    fun testConcreteEnumParameterIsSpecificEnumKind() {
        addProjectEnums()
        val fqn = addStepImplementation(
            """
                @Step("<item> menusune git")
                public void goToMenu(com.example.pages.PageItems2 item) {}
            """,
        )
        val method = myFixture.findClass(fqn).methods.first()
        val kind = JavaStepParameterResolver.kindOf(method.parameterList.parameters.first())
        assertTrue("expected a specific enum kind, got $kind", kind is GaugeParameterKind.SpecificEnumKind)
        assertEquals(
            "com.example.pages.PageItems2",
            (kind as GaugeParameterKind.SpecificEnumKind).psiClass.qualifiedName,
        )
    }
}
