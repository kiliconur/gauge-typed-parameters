package com.company.gauge.typed.enums

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiShortNamesCache

/**
 * Stage 2 of the project enum browser: resolve exactly the enum class the user named.
 *
 * This is the performance-critical path. Once the text has the shape `EnumClassName.<prefix>`
 * nothing here enumerates project enums or project classes: a short name goes through the Java
 * short-name index ([PsiShortNamesCache]), a dotted name straight through [JavaPsiFacade]. Only
 * the resolved class's own fields are then read.
 */
@Service(Service.Level.PROJECT)
class DirectEnumClassResolver(private val project: Project) : EnumClassResolver {

    override fun resolve(context: PsiElement, name: String): EnumClassLookup {
        if (project.isDisposed || DumbService.isDumb(project)) return EnumClassLookup.NotFound
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return EnumClassLookup.NotFound

        val scope = GaugeEnumSearchScopes.sources(project, GaugeEnumSearchScopes.moduleOf(context))

        val candidates: List<PsiClass> = try {
            if (trimmed.contains('.')) {
                listOfNotNull(JavaPsiFacade.getInstance(project).findClass(trimmed, scope))
            } else {
                PsiShortNamesCache.getInstance(project).getClassesByName(trimmed, scope).toList()
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            LOG.debug(e)
            return EnumClassLookup.NotFound
        } catch (e: Throwable) {
            LOG.warn("direct enum class resolution failed for '$trimmed'", e)
            return EnumClassLookup.NotFound
        }

        val enums = candidates
            .filter { it.isEnum }
            .distinctBy { it.qualifiedName ?: it.name }

        return when {
            enums.isEmpty() -> EnumClassLookup.NotFound
            enums.size == 1 -> EnumClassLookup.Found(enums[0])
            // Same short name in different packages. Merging is only safe when every candidate
            // declares exactly the same constants - then no choice can be the wrong one.
            sameConstants(enums) -> EnumClassLookup.Found(enums[0])
            else -> EnumClassLookup.Ambiguous(enums)
        }
    }

    private fun sameConstants(classes: List<PsiClass>): Boolean {
        val first = EnumConstants.of(classes[0])
        if (first.isEmpty()) return false
        return classes.drop(1).all { EnumConstants.of(it) == first }
    }

    companion object {
        private val LOG = Logger.getInstance(DirectEnumClassResolver::class.java)

        @JvmStatic
        fun getInstance(project: Project): DirectEnumClassResolver = project.service()
    }
}
