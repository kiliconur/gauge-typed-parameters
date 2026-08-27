package com.company.gauge.typed.gauge

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.thoughtworks.gauge.language.psi.SpecStep
import com.thoughtworks.gauge.util.StepUtil

/**
 * Resolves a Gauge step invocation to the Java `@Step`-annotated method that implements it.
 *
 * Two strategies, in order:
 *
 *  1. Gauge's own [com.thoughtworks.gauge.reference.StepReference]. In a configured Gauge
 *     project this is authoritative - it goes through the Gauge daemon's step parser and
 *     Gauge's reference cache, exactly like "Go to step implementation" does.
 *  2. A PSI-only fallback that matches the canonical template of the invocation against the
 *     canonical template of every `@Step` annotation value in scope. This keeps the feature
 *     working when the Gauge CLI is not available (fresh checkout, tests, indexing just
 *     finished, non-Gauge module layout) without duplicating Gauge's spec parser - the
 *     invocation is read straight from Gauge's own PSI tree.
 *
 * Everything is index-dependent, so all entry points return `null` in dumb mode.
 */
@Service(Service.Level.PROJECT)
class GaugeStepResolver(private val project: Project) {

    /**
     * @return the implementing method, or `null` when it cannot be resolved unambiguously.
     */
    fun resolveImplementation(
        step: SpecStep,
        template: GaugeStepAdapter.StepTemplate,
        useGaugeReference: Boolean = true,
    ): PsiMethod? {
        if (project.isDisposed || DumbService.isDumb(project)) return null

        if (useGaugeReference) {
            gaugeReference(step)?.let { return it }
        }
        val candidates = templateIndex(step)[template.text] ?: return null
        return candidates.singleOrNull()
    }

    /** Delegates to the Gauge plugin's own step -> implementation resolution. */
    private fun gaugeReference(step: SpecStep): PsiMethod? = try {
        val physical = originalOf(step)
        physical?.reference?.resolve() as? PsiMethod
    } catch (e: IndexNotReadyException) {
        LOG.debug(e)
        null
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Throwable) {
        // Gauge's resolution reaches out to the Gauge daemon; never let that break completion.
        LOG.debug("Gauge step reference resolution failed", e)
        null
    }

    /**
     * During completion the PSI we are handed belongs to a non-physical copy of the file that
     * has the dummy identifier injected. Gauge's reference only works on the real file.
     */
    private fun originalOf(step: SpecStep): SpecStep? {
        val file = step.containingFile ?: return null
        val original = file.originalFile
        if (original === file) return step
        val element = original.findElementAt(step.textRange.startOffset) ?: return null
        return GaugeStepAdapter.findStep(element)
    }

    /** template text -> the distinct methods whose `@Step` value produces that template. */
    private fun templateIndex(context: PsiElement): Map<String, List<PsiMethod>> {
        val module = moduleOf(context)
        val holder: UserDataHolder = module ?: project
        return CachedValuesManager.getManager(project).getCachedValue(holder, INDEX_KEY, {
            CachedValueProvider.Result.create(
                buildIndex(module),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }, false)
    }

    private fun moduleOf(context: PsiElement): Module? = try {
        val file = context.containingFile?.originalFile
        // A disposed module is not a usable UserDataHolder for the cache, and not a usable
        // search scope either - fall back to the project in that case.
        ModuleUtilCore.findModuleForPsiElement(file ?: context)?.takeIf { !it.isDisposed }
    } catch (e: Throwable) {
        LOG.debug(e)
        null
    }

    private fun buildIndex(module: Module?): Map<String, List<PsiMethod>> {
        val facade = JavaPsiFacade.getInstance(project)
        val annotationClass = facade.findClass(
            GaugeStepAdapter.STEP_ANNOTATION_FQN,
            GlobalSearchScope.allScope(project),
        ) ?: return emptyMap()

        val scope = when {
            module != null && !module.isDisposed ->
                GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
            else -> GlobalSearchScope.projectScope(project)
        }

        val result = LinkedHashMap<String, MutableList<PsiMethod>>()
        val methods = try {
            AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope).findAll()
        } catch (e: IndexNotReadyException) {
            LOG.debug(e)
            return emptyMap()
        }

        for (method in methods) {
            // Reuses Gauge's own annotation reader (handles aliases and constant folding).
            val values = try {
                StepUtil.getGaugeStepAnnotationValues(method)
            } catch (e: Throwable) {
                LOG.debug(e)
                continue
            }
            for (value in values) {
                val template = GaugeStepAdapter.templateFromAnnotationValue(value)
                if (template.text.isEmpty()) continue
                val bucket = result.getOrPut(template.text) { ArrayList(1) }
                if (bucket.none { it == method }) bucket.add(method)
            }
        }
        return result
    }

    companion object {
        private val LOG = Logger.getInstance(GaugeStepResolver::class.java)
        private val INDEX_KEY =
            Key.create<com.intellij.psi.util.CachedValue<Map<String, List<PsiMethod>>>>(
                "gauge.typed.parameters.stepTemplateIndex",
            )

        @JvmStatic
        fun getInstance(project: Project): GaugeStepResolver = project.service()
    }
}
