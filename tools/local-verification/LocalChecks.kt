@file:Suppress("unused")

package localchecks

import com.company.gauge.typed.completion.BooleanCompletionProvider
import com.company.gauge.typed.completion.EnumCompletionProvider
import com.company.gauge.typed.completion.GaugeValuePrefixMatcher
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
    check("enum is recognised", enumKind is GaugeParameterKind.EnumKind)
    eq("enum type name", "Element", (enumKind as GaugeParameterKind.EnumKind).typeName)
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
    check("String", JavaStepParameterResolver.kindOf(FakeClassType(FakeClass("String", "java.lang.String", false))) === GaugeParameterKind.StringKind)
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
    val enumKind = GaugeParameterKind.EnumKind(elementEnum)

    val enumProvider = EnumCompletionProvider()
    check("enum provider supports enums", enumProvider.supports(enumKind))
    check("enum provider ignores booleans", !enumProvider.supports(GaugeParameterKind.BooleanKind))
    eq("enum values", listOf("LOGIN_BUTTON", "LOGOUT_BUTTON"), enumProvider.values(enumKind).map { it.value })
    eq("enum type text", listOf("Element", "Element"), enumProvider.values(enumKind).map { it.typeText })

    val boolProvider = BooleanCompletionProvider()
    check("boolean provider supports booleans", boolProvider.supports(GaugeParameterKind.BooleanKind))
    check("boolean provider ignores enums", !boolProvider.supports(enumKind))
    eq("boolean values", listOf("true", "false"), boolProvider.values(GaugeParameterKind.BooleanKind).map { it.value })

    eq("string kind offers nothing", emptyList(), GaugeParameterKind.completionValues(GaugeParameterKind.StringKind))
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
    val elementEnum = GaugeParameterKind.EnumKind(
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

    val emptyEnum = GaugeParameterKind.EnumKind(FakeClass("E", "com.example.E", true, emptyList()))
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

    check("String is never reported", GaugeValueValidator.validate(GaugeParameterKind.StringKind, "whatever") == null)
    check("Unsupported is never reported", GaugeValueValidator.validate(GaugeParameterKind.UnsupportedKind, "whatever") == null)

    eq("levenshtein equal", 0, GaugeValueValidator.editDistance("ABC", "ABC"))
    eq("levenshtein one substitution", 1, GaugeValueValidator.editDistance("ABC", "ABD"))
    eq("levenshtein transposed pair", 2, GaugeValueValidator.editDistance("LOGNI", "LOGIN"))
    eq("levenshtein against empty", 3, GaugeValueValidator.editDistance("", "ABC"))
}

fun main() {
    println("Gauge Typed Parameters - local (no-IDE) checks")
    annotationTemplates()
    dummyStripping()
    typeClassification()
    completionValues()
    prefixMatching()
    validation()
    println("\n================================")
    println("passed=$passed failed=$failed")
    if (failed > 0) {
        throw AssertionError("$failed local check(s) failed")
    }
}
