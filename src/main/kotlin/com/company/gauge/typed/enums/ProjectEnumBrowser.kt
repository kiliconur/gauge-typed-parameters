package com.company.gauge.typed.enums

import com.company.gauge.typed.GtpLog
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

/**
 * What the user has typed inside the parameter decides which of the two browser stages
 * of the project enum browser applies.
 *
 * `"Pag"`             -> [ClassName]  (stage 1: offer enum class names)
 * `"PageItems2."`     -> [Constant]   (stage 2: offer only that class's constants)
 * `"PageItems2.LO"`   -> [Constant]
 */
sealed interface ProjectEnumStage {

    data class ClassName(val prefix: String) : ProjectEnumStage

    data class Constant(val className: String, val valuePrefix: String) : ProjectEnumStage

    companion object {
        /** Splits at the LAST dot, so a fully qualified `com.foo.PageItems.LO` also works. */
        fun parse(typedText: String): ProjectEnumStage {
            val dot = typedText.lastIndexOf('.')
            if (dot < 0) return ClassName(typedText)
            return Constant(
                className = typedText.substring(0, dot),
                valuePrefix = typedText.substring(dot + 1),
            )
        }
    }
}

/** Stage 1: every enum class of the user's own project, discovered through the IDE indices. */
interface EnumClassCatalog {
    fun enumClasses(context: PsiElement): List<PsiClass>
}

/** Stage 2: one enum class, resolved directly by name - never a full project scan. */
interface EnumClassResolver {
    fun resolve(context: PsiElement, name: String): EnumClassLookup
}

sealed interface EnumClassLookup {
    data class Found(val psiClass: PsiClass) : EnumClassLookup

    /** Several project enums share this short name and their constants differ. */
    data class Ambiguous(val classes: List<PsiClass>) : EnumClassLookup

    data object NotFound : EnumClassLookup
}

/** What the browser should offer for the text typed so far. */
sealed interface ProjectEnumCandidates {
    /** Stage 1 - enum class names. */
    data class Classes(val classes: List<PsiClass>, val prefix: String) : ProjectEnumCandidates

    /** Stage 2 - the constants of exactly one enum class. */
    data class Constants(
        val owner: PsiClass,
        val names: List<String>,
        val valuePrefix: String,
    ) : ProjectEnumCandidates

    /** Nothing safe to offer (unknown class name, ambiguous class, nothing indexed yet). */
    data object None : ProjectEnumCandidates
}

/**
 * The decision logic of the two-stage browser, deliberately free of completion API and of PSI
 * search: [catalog] and [resolver] are injected, which is what makes "stage 2 never triggers a
 * full project enum scan" a property a test can assert.
 */
class ProjectEnumBrowser(
    private val catalog: EnumClassCatalog,
    private val resolver: EnumClassResolver,
) {

    /**
     * @param anchor any PSI element of the file being edited; only used to derive the search
     *        scope (module first, project sources otherwise)
     * @param typedText the text already typed inside the Gauge parameter, up to the caret
     * @param preferred the enum class the user picked in stage 1 during this editing session,
     *        used to break short-name ambiguity without guessing
     */
    fun candidatesFor(
        anchor: PsiElement,
        typedText: String,
        preferred: PsiClass? = null,
    ): ProjectEnumCandidates = when (val stage = ProjectEnumStage.parse(typedText)) {
        is ProjectEnumStage.ClassName -> classCandidates(anchor, stage.prefix)
        is ProjectEnumStage.Constant -> constantCandidates(anchor, stage, preferred)
    }

    private fun classCandidates(anchor: PsiElement, prefix: String): ProjectEnumCandidates {
        val classes = catalog.enumClasses(anchor)
        GtpLog.info("Stage1 enum classes found=${classes.size}")
        if (classes.isEmpty()) return ProjectEnumCandidates.None
        return ProjectEnumCandidates.Classes(classes, prefix)
    }

    private fun constantCandidates(
        anchor: PsiElement,
        stage: ProjectEnumStage.Constant,
        preferred: PsiClass?,
    ): ProjectEnumCandidates {
        val shortName = stage.className.substringAfterLast('.')
        GtpLog.info("Stage2 resolving class shortName=$shortName")

        if (preferred != null && preferred.isValid && preferred.name == shortName) {
            GtpLog.info("Stage2 resolved class=${preferred.qualifiedName} (from the stage 1 selection)")
            return constantsOf(preferred, stage.valuePrefix)
        }

        return when (val lookup = resolver.resolve(anchor, stage.className)) {
            is EnumClassLookup.Found -> {
                GtpLog.info("Stage2 resolved class=${lookup.psiClass.qualifiedName}")
                constantsOf(lookup.psiClass, stage.valuePrefix)
            }

            is EnumClassLookup.Ambiguous -> {
                // Several enums share the short name and they do NOT agree on their constants.
                // Offering either one would silently pick the wrong enum, so offer nothing.
                GtpLog.info(
                    "Stage2 AMBIGUOUS shortName=$shortName candidates=" +
                        lookup.classes.mapNotNull { it.qualifiedName } +
                        " - no constants offered, pick the class from the list again",
                )
                ProjectEnumCandidates.None
            }

            EnumClassLookup.NotFound -> {
                GtpLog.info("Stage2 class NOT found: $shortName")
                ProjectEnumCandidates.None
            }
        }
    }

    private fun constantsOf(psiClass: PsiClass, valuePrefix: String): ProjectEnumCandidates {
        val names = EnumConstants.of(psiClass)
        GtpLog.info("Stage2 constants=$names")
        if (names.isEmpty()) return ProjectEnumCandidates.None
        return ProjectEnumCandidates.Constants(psiClass, names, valuePrefix)
    }
}
