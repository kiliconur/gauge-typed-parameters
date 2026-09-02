package com.company.gauge.typed.enums

import com.company.gauge.typed.GtpLog
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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.Processor

/** The declared constants of an enum class, in declaration order. */
internal object EnumConstants {
    fun of(psiClass: PsiClass): List<String> = try {
        psiClass.fields.filterIsInstance<PsiEnumConstant>().mapNotNull { it.name }
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: IndexNotReadyException) {
        emptyList()
    }
}

/** The search scope the enum browser works in: the user's own project sources, never the SDK. */
internal object GaugeEnumSearchScopes {

    fun sources(project: Project, module: Module?): GlobalSearchScope {
        val projectSources = GlobalSearchScope.projectScope(project)
        if (module == null || module.isDisposed) return projectSources
        // Module + the modules it depends on, then intersected with project content so that
        // library and JDK enums (java.lang.Thread.State, platform enums, ...) stay out.
        return GlobalSearchScope.moduleWithDependenciesScope(module).intersectWith(projectSources)
    }

    fun moduleOf(context: PsiElement): Module? = try {
        val file = context.containingFile?.originalFile
        ModuleUtilCore.findModuleForPsiElement(file ?: context)?.takeIf { !it.isDisposed }
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Throwable) {
        null
    }
}

/**
 * Stage 1 of the `java.lang.Enum` browser: every enum class declared in the user's project.
 *
 * Backed by the platform's own class index ([AllClassesSearch]) and cached per module through
 * [CachedValuesManager] with [PsiModificationTracker.MODIFICATION_COUNT], so no manually
 * maintained map can go stale and no Ctrl+Space walks the file system.
 */
@Service(Service.Level.PROJECT)
class ProjectEnumClassProvider(private val project: Project) : EnumClassCatalog {

    override fun enumClasses(context: PsiElement): List<PsiClass> {
        if (project.isDisposed || DumbService.isDumb(project)) return emptyList()
        val module = GaugeEnumSearchScopes.moduleOf(context)
        val holder: UserDataHolder = module ?: project
        return try {
            CachedValuesManager.getManager(project).getCachedValue(holder, CACHE_KEY, {
                CachedValueProvider.Result.create(
                    collect(module),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            }, false)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            emptyList()
        }
    }

    private fun collect(module: Module?): List<PsiClass> {
        val scope = GaugeEnumSearchScopes.sources(project, module)
        val found = ArrayList<PsiClass>()
        var visited = 0
        try {
            AllClassesSearch.search(scope, project).forEach(
                Processor { psiClass ->
                    visited++
                    if (psiClass.isEnum && psiClass.name != null) found.add(psiClass)
                    found.size < LIMIT
                },
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            LOG.debug(e)
            return emptyList()
        } catch (e: Throwable) {
            LOG.warn("project enum discovery failed", e)
            return emptyList()
        }

        if (visited == 0) {
            // The class search saw nothing at all in this scope, which means it could not run
            // here rather than "this project declares no classes". Fall back to the short-name
            // index once. `visited > 0 && found.isEmpty()` is a genuine "no enums" answer and
            // deliberately does NOT trigger this.
            found.addAll(byShortNames(scope))
        }

        val sorted = found.sortedWith(compareBy({ it.name }, { it.qualifiedName }))
        GtpLog.info(
            "Stage1 project enum catalogue built for module=${module?.name ?: "<project scope>"}:" +
                " ${sorted.size} enum class(es)" + if (sorted.size >= LIMIT) " (capped at $LIMIT)" else "",
        )
        return sorted
    }

    /** Fallback discovery through the Java short-name index. Only used by [collect]. */
    private fun byShortNames(scope: GlobalSearchScope): List<PsiClass> = try {
        val cache = PsiShortNamesCache.getInstance(project)
        val result = ArrayList<PsiClass>()
        for (name in cache.allClassNames) {
            for (psiClass in cache.getClassesByName(name, scope)) {
                if (psiClass.isEnum && psiClass.name != null) result.add(psiClass)
            }
            if (result.size >= LIMIT) break
        }
        GtpLog.info("Stage1 fell back to the short-name index: ${result.size} enum class(es)")
        result
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: IndexNotReadyException) {
        LOG.debug(e)
        emptyList()
    } catch (e: Throwable) {
        LOG.warn("short-name enum discovery failed", e)
        emptyList()
    }

    companion object {
        /** A popup is a browsing aid, not a report: never build an unbounded list. */
        private const val LIMIT = 500

        private val LOG = Logger.getInstance(ProjectEnumClassProvider::class.java)

        private val CACHE_KEY = Key.create<CachedValue<List<PsiClass>>>(
            "gauge.typed.parameters.projectEnumClasses",
        )

        @JvmStatic
        fun getInstance(project: Project): ProjectEnumClassProvider = project.service()
    }
}
