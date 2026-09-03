@file:Suppress("unused")

package localchecks

import com.company.gauge.typed.completion.BooleanCompletionProvider
import com.company.gauge.typed.completion.EnumCompletionProvider
import com.company.gauge.typed.completion.GaugeQualifiedValuePrefixMatcher
import com.company.gauge.typed.completion.GaugeValuePrefixMatcher
import com.company.gauge.typed.enums.EnumClassCatalog
import com.company.gauge.typed.enums.EnumClassLookup
import com.company.gauge.typed.enums.EnumClassResolver
import com.company.gauge.typed.enums.ProjectEnumBrowser
import com.company.gauge.typed.enums.ProjectEnumCandidates
import com.company.gauge.typed.enums.ProjectEnumStage
import com.company.gauge.typed.gauge.GaugeStepAdapter
import com.company.gauge.typed.java.JavaStepParameterResolver
import com.company.gauge.typed.model.GaugeParameterKind
import com.company.gauge.typed.model.GaugeValueValidator
import com.company.gauge.typed.model.GaugeValueValidator.Violation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

/**
 * Executable checks for every part of the plugin that does NOT need a live IntelliJ instance.
 * Compiled and run against the stub API tree, so it exercises the plugin's own logic only.
 */

private var passed = 0
private var failed = 0

private fun check(name: String, condition: Boolean, detail: String = "") {
    if (condition) {
        passed++
        println("  PASS  $name")
    } else {
        failed++
        println("  FAIL  $name ${if (detail.isEmpty()) "" else "-> $detail"}")
    }
}

private fun <T> eq(name: String, expected: T, actual: T) =
    check(name, expected == actual, "expected=$expected actual=$actual")

// ---------------------------------------------------------------- fake Java PSI

private abstract class FakePsi : PsiElement {
    override fun getNode(): ASTNode? = null
    override fun getText(): String = ""
    override fun getTextRange(): TextRange = TextRange(0, 0)
    override fun getFirstChild(): PsiElement? = null
    override fun getNextSibling(): PsiElement? = null
    override fun getParent(): PsiElement? = null
    override fun getContainingFile(): PsiFile? = null
    override fun getProject(): Project = error("not needed in local checks")
    override fun isValid(): Boolean = true
    override fun getLanguage(): Language = error("not needed in local checks")
    override fun getReference(): PsiReference? = null
}

private class FakeEnumConstant(private val n: String) : FakePsi(), PsiEnumConstant {
    override fun getName(): String = n
}

private class FakeClass(
    private val simpleName: String,
    private val fqn: String,
    private val enum: Boolean,
    private val constants: List<String> = emptyList(),
) : FakePsi(), PsiClass {
    override fun getName(): String = simpleName
    override fun getQualifiedName(): String = fqn
    override fun isEnum(): Boolean = enum
    override fun getFields(): Array<PsiField> =
        constants.map { FakeEnumConstant(it) as PsiField }.toTypedArray()
}

private class FakeClassType(private val target: PsiClass?) : PsiClassType() {
    override fun resolve(): PsiClass? = target
    override fun getCanonicalText(): String = target?.qualifiedName ?: "?"
}

private class FakePrimitive(private val text: String) : PsiPrimitiveType() {
    override fun getCanonicalText(): String = text
}

// ---------------------------------------------------------------- checks

private fun annotationTemplates() {
    println("\n[annotation -> canonical template]")
    val single = GaugeStepAdapter.templateFromAnnotationValue("<element> elementine tiklanir")
    eq("single placeholder text", "{} elementine tiklanir", single.text)
    eq("single placeholder count", 1, single.placeholderCount)

    val three = GaugeStepAdapter.templateFromAnnotationValue(
        "<browser> ile <retryCount> kere <element> elementine tiklanir",
    )
    eq("three placeholder text", "{} ile {} kere {} elementine tiklanir", three.text)
    eq("three placeholder count", 3, three.placeholderCount)

    eq(
        "whitespace is collapsed",
        "{} ile {}",
        GaugeStepAdapter.templateFromAnnotationValue("<a>   ile\t<b>").text,
    )
    eq(
        "no placeholders",
        "hicbir parametre yok",
        GaugeStepAdapter.templateFromAnnotationValue("  hicbir parametre yok  ").text,
    )
    eq(
        "table placeholder is just another parameter",
        "{} ile {}",
        GaugeStepAdapter.templateFromAnnotationValue("<a> ile <table>").text,
    )
}

private fun dummyStripping() {
    println("\n[completion dummy identifier handling]")
    eq("dummy with trailing space", "LOG", GaugeStepAdapter.stripDummy("LOGIntellijIdeaRulezzz "))
    eq("dummy trimmed", "LOG", GaugeStepAdapter.stripDummy("LOGIntellijIdeaRulezzz"))
    eq("untouched text", "LOGIN_BUTTON", GaugeStepAdapter.stripDummy("LOGIN_BUTTON"))
    eq("normalize", "a b c", GaugeStepAdapter.normalize("  a \t b\n c  "))
}

private fun typeClassification() {
    println("\n[PsiType -> GaugeParameterKind]")
    val elementEnum = FakeClass(
        "Element", "com.example.Element", true,
        listOf("LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON"),
    )

    val enumKind = JavaStepParameterResolver.kindOf(FakeClassType(elementEnum))
    check("enum is recognised", enumKind is GaugeParameterKind.SpecificEnumKind)
    eq("enum type name", "Element", (enumKind as GaugeParameterKind.SpecificEnumKind).typeName)
    eq(
        "enum constants",
        listOf("LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON"),
        enumKind.constantNames,
    )

    check("boolean primitive", JavaStepParameterResolver.kindOf(FakePrimitive("boolean")) === GaugeParameterKind.BooleanKind)
    check(
        "Boolean box",
        JavaStepParameterResolver.kindOf(FakeClassType(FakeClass("Boolean", "java.lang.Boolean", false)))
            === GaugeParameterKind.BooleanKind,
    )
    check("String opens the enum browser", JavaStepParameterResolver.kindOf(FakeClassType(FakeClass("String", "java.lang.String", false))) === GaugeParameterKind.StringEnumBrowserKind)
    check("CharSequence too", JavaStepParameterResolver.kindOf(FakeClassType(FakeClass("CharSequence", "java.lang.CharSequence", false))) === GaugeParameterKind.StringEnumBrowserKind)
    check("java.lang.Enum itself is no longer special", JavaStepParameterResolver.kindOf(FakeClassType(FakeClass("Enum", "java.lang.Enum", false))) === GaugeParameterKind.UnsupportedKind)
    check("unknown class is unsupported", JavaStepParameterResolver.kindOf(FakeClassType(FakeClass("Foo", "com.example.Foo", false))) === GaugeParameterKind.UnsupportedKind)
    check("unresolvable class is unsupported", JavaStepParameterResolver.kindOf(FakeClassType(null)) === GaugeParameterKind.UnsupportedKind)

    for (p in listOf("int", "long", "short", "byte")) {
        val k = JavaStepParameterResolver.kindOf(FakePrimitive(p))
        check("$p is integral numeric", k is GaugeParameterKind.NumericKind && k.integral)
    }
    for (p in listOf("double", "float")) {
        val k = JavaStepParameterResolver.kindOf(FakePrimitive(p))
        check("$p is fractional numeric", k is GaugeParameterKind.NumericKind && !k.integral)
    }
    for ((fqn, integral) in listOf(
        "java.lang.Integer" to true, "java.lang.Long" to true, "java.lang.Short" to true,
        "java.lang.Byte" to true, "java.lang.Double" to false, "java.lang.Float" to false,
        "java.math.BigInteger" to true, "java.math.BigDecimal" to false,
    )) {
        val k = JavaStepParameterResolver.kindOf(FakeClassType(FakeClass(fqn.substringAfterLast('.'), fqn, false)))
        check("$fqn numeric(integral=$integral)", k is GaugeParameterKind.NumericKind && k.integral == integral)
    }
    check("char is unsupported", JavaStepParameterResolver.kindOf(FakePrimitive("char")) === GaugeParameterKind.UnsupportedKind)
}

private fun completionValues() {
    println("\n[completion candidates per kind]")
    val elementEnum = FakeClass("Element", "com.example.Element", true, listOf("LOGIN_BUTTON", "LOGOUT_BUTTON"))
    val enumKind = GaugeParameterKind.SpecificEnumKind(elementEnum)

    val enumProvider = EnumCompletionProvider()
    check("enum provider supports enums", enumProvider.supports(enumKind))
    check("enum provider ignores booleans", !enumProvider.supports(GaugeParameterKind.BooleanKind))
    eq("enum values", listOf("LOGIN_BUTTON", "LOGOUT_BUTTON"), enumProvider.values(enumKind).map { it.value })
    eq("enum type text", listOf("Element", "Element"), enumProvider.values(enumKind).map { it.typeText })

    val boolProvider = BooleanCompletionProvider()
    check("boolean provider supports booleans", boolProvider.supports(GaugeParameterKind.BooleanKind))
    check("boolean provider ignores enums", !boolProvider.supports(enumKind))
    eq("boolean values", listOf("true", "false"), boolProvider.values(GaugeParameterKind.BooleanKind).map { it.value })

    eq("String kind offers no context-free values", emptyList(), GaugeParameterKind.completionValues(GaugeParameterKind.StringEnumBrowserKind))
    eq("numeric kind offers nothing", emptyList(), GaugeParameterKind.completionValues(GaugeParameterKind.NumericKind("int", true)))
}

private fun prefixMatching() {
    println("\n[prefix matcher: filtering + replacement range]")
    val all = listOf("LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")

    val empty = GaugeValuePrefixMatcher.forCandidates("", all)
    eq("empty prefix keeps replacement range empty", "", empty.prefix)
    eq("empty prefix matches everything", all, all.filter { empty.prefixMatches(it) })

    val lo = GaugeValuePrefixMatcher.forCandidates("LO", all)
    eq("LO replacement range", "LO", lo.prefix)
    eq("LO filters", listOf("LOGIN_BUTTON", "LOGOUT_BUTTON"), all.filter { lo.prefixMatches(it) })

    val login = GaugeValuePrefixMatcher.forCandidates("LOGIN", all)
    eq("LOGIN filters", listOf("LOGIN_BUTTON"), all.filter { login.prefixMatches(it) })

    val lower = GaugeValuePrefixMatcher.forCandidates("log", all)
    eq("lowercase matches case-insensitively", listOf("LOGIN_BUTTON", "LOGOUT_BUTTON"), all.filter { lower.prefixMatches(it) })

    val exact = GaugeValuePrefixMatcher.forCandidates("LOGIN_BUTTON", all)
    eq("complete value still offered", listOf("LOGIN_BUTTON"), all.filter { exact.prefixMatches(it) })
    eq("complete value replacement range covers it", "LOGIN_BUTTON", exact.prefix)

    val wrong = GaugeValuePrefixMatcher.forCandidates("WRONG_VALUE", all)
    eq("wrong value falls back to showing everything", all, all.filter { wrong.prefixMatches(it) })
    eq("wrong value replacement range covers the whole value", "WRONG_VALUE", wrong.prefix)

    val cloned = wrong.cloneWithPrefix("W")
    eq("clone keeps prefix", "W", cloned.prefix)

    // Cross-enum isolation: Browser candidates never see Element values and vice versa.
    val browser = listOf("CHROME", "FIREFOX")
    val chr = GaugeValuePrefixMatcher.forCandidates("CHR", browser)
    eq("CHR matches CHROME only", listOf("CHROME"), browser.filter { chr.prefixMatches(it) })
    eq("CHR never matches Element values", emptyList(), all.filter { chr.prefixMatches(it) })
}

private fun validation() {
    println("\n[value validation]")
    val elementEnum = GaugeParameterKind.SpecificEnumKind(
        FakeClass("Element", "com.example.Element", true, listOf("LOGIN_BUTTON", "LOGOUT_BUTTON")),
    )
    check("valid enum constant", GaugeValueValidator.validate(elementEnum, "LOGIN_BUTTON") == null)

    val typo = GaugeValueValidator.validate(elementEnum, "LOGNI_BUTTON")
    check("typo is reported", typo is Violation.UnknownEnumConstant)
    eq("typo type name", "Element", (typo as Violation.UnknownEnumConstant).typeName)
    eq("typo best suggestion is ranked first", "LOGIN_BUTTON", typo.suggestions.firstOrNull())
    check("typo suggestions are capped at 3", typo.suggestions.size <= 3)
    check("typo suggestions exclude the far candidate", "SETTINGS_BUTTON" !in typo.suggestions)

    val nonsense = GaugeValueValidator.validate(elementEnum, "COMPLETELY_DIFFERENT")
    check("nonsense is reported", nonsense is Violation.UnknownEnumConstant)
    eq("nonsense gets no suggestion", emptyList(), nonsense!!.suggestions)

    val emptyEnum = GaugeParameterKind.SpecificEnumKind(FakeClass("E", "com.example.E", true, emptyList()))
    check("enum without readable constants stays silent", GaugeValueValidator.validate(emptyEnum, "X") == null)

    check("true is valid", GaugeValueValidator.validate(GaugeParameterKind.BooleanKind, "true") == null)
    check("TRUE is valid", GaugeValueValidator.validate(GaugeParameterKind.BooleanKind, "TRUE") == null)
    val badBool = GaugeValueValidator.validate(GaugeParameterKind.BooleanKind, "tru")
    check("tru is reported", badBool is Violation.InvalidBoolean)
    eq("tru suggests true", listOf("true"), badBool!!.suggestions)

    val int = GaugeParameterKind.NumericKind("int", true)
    check("3 is a valid int", GaugeValueValidator.validate(int, "3") == null)
    check("-42 is a valid int", GaugeValueValidator.validate(int, "-42") == null)
    check("abc is not an int", GaugeValueValidator.validate(int, "abc") is Violation.InvalidNumber)
    check("3.5 is not an int", GaugeValueValidator.validate(int, "3.5") is Violation.InvalidNumber)

    val dbl = GaugeParameterKind.NumericKind("double", false)
    check("3.5 is a valid double", GaugeValueValidator.validate(dbl, "3.5") == null)
    check("abc is not a double", GaugeValueValidator.validate(dbl, "abc") is Violation.InvalidNumber)

    check("String is never reported", GaugeValueValidator.validate(GaugeParameterKind.StringEnumBrowserKind, "whatever") == null)
    check("String stays free text: custom value", GaugeValueValidator.validate(GaugeParameterKind.StringEnumBrowserKind, "custom value") == null)
    check("String stays free text: abc123", GaugeValueValidator.validate(GaugeParameterKind.StringEnumBrowserKind, "abc123") == null)
    check("String stays free text: browsing leftovers", GaugeValueValidator.validate(GaugeParameterKind.StringEnumBrowserKind, "PageItems2.") == null)
    check("Unsupported is never reported", GaugeValueValidator.validate(GaugeParameterKind.UnsupportedKind, "whatever") == null)

    eq("levenshtein equal", 0, GaugeValueValidator.editDistance("ABC", "ABC"))
    eq("levenshtein one substitution", 1, GaugeValueValidator.editDistance("ABC", "ABD"))
    eq("levenshtein transposed pair", 2, GaugeValueValidator.editDistance("LOGNI", "LOGIN"))
    eq("levenshtein against empty", 3, GaugeValueValidator.editDistance("", "ABC"))
}


// ------------------------------------------------- generic java.lang.Enum browser

private class CountingCatalog(private val classes: List<PsiClass>) : EnumClassCatalog {
    var calls = 0
    override fun enumClasses(context: PsiElement): List<PsiClass> {
        calls++
        return classes
    }
}

private class CountingResolver(private val byName: Map<String, EnumClassLookup>) : EnumClassResolver {
    var calls = 0
    val names = ArrayList<String>()
    override fun resolve(context: PsiElement, name: String): EnumClassLookup {
        calls++
        names.add(name)
        return byName[name.substringAfterLast('.')] ?: EnumClassLookup.NotFound
    }
}

private fun genericEnumStages() {
    println("\n[generic Enum: stage detection]")
    check("no dot is stage 1", ProjectEnumStage.parse("Pa") is ProjectEnumStage.ClassName)
    eq("stage 1 keeps the prefix", "Pa", (ProjectEnumStage.parse("Pa") as ProjectEnumStage.ClassName).prefix)
    check("empty text is stage 1", ProjectEnumStage.parse("") is ProjectEnumStage.ClassName)

    val afterDot = ProjectEnumStage.parse("PageItems2.")
    check("trailing dot is stage 2", afterDot is ProjectEnumStage.Constant)
    eq("stage 2 class name", "PageItems2", (afterDot as ProjectEnumStage.Constant).className)
    eq("stage 2 empty value prefix", "", afterDot.valuePrefix)

    val withPrefix = ProjectEnumStage.parse("PageItems2.LO") as ProjectEnumStage.Constant
    eq("stage 2 class name with prefix", "PageItems2", withPrefix.className)
    eq("stage 2 value prefix", "LO", withPrefix.valuePrefix)

    val qualified = ProjectEnumStage.parse("com.foo.PageItems.LO") as ProjectEnumStage.Constant
    eq("fully qualified class name", "com.foo.PageItems", qualified.className)
    eq("fully qualified value prefix", "LO", qualified.valuePrefix)
}

private fun genericEnumBrowsing() {
    println("\n[generic Enum: two-stage browsing]")
    val pageItems = FakeClass("PageItems", "com.foo.web.PageItems", true, listOf("LOGIN_BUTTON", "LOGOUT_BUTTON"))
    val pageItems2 = FakeClass(
        "PageItems2", "com.foo.web.PageItems2", true,
        listOf("LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON"),
    )
    val header = FakeClass("HeaderItems", "com.foo.common.HeaderItems", true, listOf("LOGO"))
    val anchor = FakeClass("Anchor", "com.foo.Anchor", false)

    val catalog = CountingCatalog(listOf(pageItems, pageItems2, header))
    val resolver = CountingResolver(
        mapOf(
            "PageItems" to EnumClassLookup.Found(pageItems),
            "PageItems2" to EnumClassLookup.Found(pageItems2),
        ),
    )
    val browser = ProjectEnumBrowser(catalog, resolver)

    val stage1 = browser.candidatesFor(anchor, "Pa")
    check("stage 1 offers classes", stage1 is ProjectEnumCandidates.Classes)
    eq("stage 1 lists every project enum", 3, (stage1 as ProjectEnumCandidates.Classes).classes.size)
    eq("stage 1 consulted the catalogue", 1, catalog.calls)
    eq("stage 1 did not resolve a class directly", 0, resolver.calls)

    val stage2 = browser.candidatesFor(anchor, "PageItems2.LO")
    check("stage 2 offers constants", stage2 is ProjectEnumCandidates.Constants)
    val constants = stage2 as ProjectEnumCandidates.Constants
    eq("stage 2 owner", "com.foo.web.PageItems2", constants.owner.qualifiedName)
    eq(
        "stage 2 lists only that enum's constants",
        listOf("LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON"),
        constants.names,
    )
    eq("stage 2 value prefix", "LO", constants.valuePrefix)
    // The mandatory optimisation: after the dot nothing may scan the project again.
    eq("stage 2 never rebuilt the enum catalogue", 1, catalog.calls)
    eq("stage 2 resolved exactly one class directly", 1, resolver.calls)
    eq("stage 2 asked for the typed name only", listOf("PageItems2"), resolver.names)

    val unknown = browser.candidatesFor(anchor, "Nope.LO")
    check("unknown class offers nothing", unknown === ProjectEnumCandidates.None)
    eq("unknown class did not rebuild the catalogue", 1, catalog.calls)
}

private fun genericEnumAmbiguity() {
    println("\n[generic Enum: same short name in two packages]")
    val web = FakeClass("PageItems", "com.foo.web.PageItems", true, listOf("LOGIN_BUTTON", "WEB_ONLY"))
    val mobile = FakeClass("PageItems", "com.foo.mobile.PageItems", true, listOf("LOGIN_BUTTON", "MOBILE_ONLY"))
    val anchor = FakeClass("Anchor", "com.foo.Anchor", false)

    val ambiguous = ProjectEnumBrowser(
        CountingCatalog(listOf(web, mobile)),
        CountingResolver(mapOf("PageItems" to EnumClassLookup.Ambiguous(listOf(web, mobile)))),
    )

    val guessed = ambiguous.candidatesFor(anchor, "PageItems.LO")
    check("ambiguous short name offers nothing rather than the wrong enum", guessed === ProjectEnumCandidates.None)

    // ... unless the user picked one of them from the stage 1 list moments ago.
    val resolved = ambiguous.candidatesFor(anchor, "PageItems.LO", preferred = mobile)
    check("the stage 1 selection breaks the tie", resolved is ProjectEnumCandidates.Constants)
    eq(
        "and it is the class the user picked",
        "com.foo.mobile.PageItems",
        (resolved as ProjectEnumCandidates.Constants).owner.qualifiedName,
    )
}

private fun qualifiedPrefixMatching() {
    println("\n[generic Enum: stage 2 prefix matcher]")
    val constants = listOf("LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")

    val typed = GaugeQualifiedValuePrefixMatcher.forCandidates("PageItems2.LO", constants)
    eq("replacement range covers class name and prefix", "PageItems2.LO", typed.prefix)
    check("LO matches LOGIN_BUTTON", typed.prefixMatches("LOGIN_BUTTON"))
    check("LO matches LOGOUT_BUTTON", typed.prefixMatches("LOGOUT_BUTTON"))
    check("LO does not match SETTINGS_BUTTON", !typed.prefixMatches("SETTINGS_BUTTON"))

    val lower = GaugeQualifiedValuePrefixMatcher.forCandidates("PageItems2.lo", constants)
    check("matching is case insensitive", lower.prefixMatches("LOGIN_BUTTON"))

    val empty = GaugeQualifiedValuePrefixMatcher.forCandidates("PageItems2.", constants)
    eq("empty suffix still replaces the class name", "PageItems2.", empty.prefix)
    check("empty suffix matches everything", constants.all { empty.prefixMatches(it) })

    val wrong = GaugeQualifiedValuePrefixMatcher.forCandidates("PageItems2.WRONG", constants)
    check("wrong suffix falls back to showing everything", constants.all { wrong.prefixMatches(it) })
    eq("wrong suffix replacement range covers it all", "PageItems2.WRONG", wrong.prefix)
}

fun main() {
    println("Gauge Typed Parameters - local (no-IDE) checks")
    annotationTemplates()
    dummyStripping()
    typeClassification()
    completionValues()
    prefixMatching()
    validation()
    genericEnumStages()
    genericEnumBrowsing()
    genericEnumAmbiguity()
    qualifiedPrefixMatching()
    println("\n================================")
    println("passed=$passed failed=$failed")
    if (failed > 0) {
        throw AssertionError("$failed local check(s) failed")
    }
}
