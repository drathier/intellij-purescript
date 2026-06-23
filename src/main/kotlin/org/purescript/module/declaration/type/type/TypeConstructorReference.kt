package org.purescript.module.declaration.type.type

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixProvider
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.purescript.PSLanguage
import org.purescript.file.PSFile
import org.purescript.module.declaration.Importable
import org.purescript.module.declaration.ImportableTypeIndex
import org.purescript.module.declaration.foreign.PSForeignDataDeclaration
import org.purescript.module.declaration.imports.ImportQuickFix

class TypeConstructorReference(typeConstructor: PSTypeConstructor) :
    LocalQuickFixProvider,
    PsiReferenceBase<PSTypeConstructor>(typeConstructor, typeConstructor.identifier.textRangeInParent, false) {

    override fun getVariants(): Array<PsiNamedElement> = candidates.toList().toTypedArray()
    override fun resolve(): PsiNamedElement? {
        val file = element.containingFile as? PSFile
        file?.resolveCache?.get(element)?.let { return it }
        val qualifier = element.moduleName?.name
        val result = if (qualifier != null) {
            candidatesFor(qualifier).firstOrNull { it.name == element.name }
        } else {
            resolveWithoutAlias() ?: fromPrim()
        }
        file?.resolveCache?.put(element, result)
        return result
    }

    private fun fromPrim(): PSForeignDataDeclaration? {
        val primModule = PSLanguage
            .getPrimModule(element.project)
        return primModule
            ?.cache?.foreignDataDeclarations
            ?.firstOrNull { it.name == element.name }
    }

    private fun resolveWithoutAlias(): PsiNamedElement? {
        val module = try { element.module } catch (_: IllegalStateException) { return null }
        module.cache.dataDeclarations.firstOrNull { it.name == element.name }?.let { return it }
        module.cache.newTypeDeclarations.firstOrNull { it.name == element.name }?.let { return it }
        module.cache.typeSynonymDeclarations.firstOrNull { it.name == element.name }?.let { return it }
        module.cache.foreignDataDeclarations.firstOrNull { it.name == element.name }?.let { return it }
        module.cache.classDeclarations.firstOrNull { it.name == element.name }?.let { return it }
        for (importDeclaration in module.cache.imports) {
            if (importDeclaration.importAlias != null) continue
            importDeclaration.importedTypeNames.firstOrNull { it.name == element.name }?.let { return it }
        }
        return null
    }

    /**
     * Type constructors can reference any data, new type, or synonym declaration
     * in the current module or any of the imported modules.
     */
    private val candidates: Sequence<PsiNamedElement>
        get() {
            val qualifier = element.moduleName?.name
            return if (qualifier != null) {
                candidatesFor(qualifier).asSequence()
            } else {
                allCandidatesWithoutAlias
            }
        }

    private fun candidatesFor(qualifier: String): List<PsiNamedElement> {
        val module = try { element.module } catch (_: IllegalStateException) { return emptyList() }
        val importDeclaration = module.cache.imports.filter { it.importAlias?.name == qualifier }
        return importDeclaration.flatMap { it.importedTypeNames }.toMutableList()
    }

    private val allCandidatesWithoutAlias: Sequence<PsiNamedElement>
        get() {
            val module = try { element.module } catch (_: IllegalStateException) { return emptySequence() }
            return sequence {
                yieldAll(module.cache.dataDeclarations.asSequence())
                yieldAll(module.cache.newTypeDeclarations.asSequence())
                yieldAll(module.cache.typeSynonymDeclarations.asSequence())
                yieldAll(module.cache.foreignDataDeclarations.asSequence())
                yieldAll(module.cache.classDeclarations.asSequence())
                for (importDeclaration in module.cache.imports) {
                    if (importDeclaration.importAlias != null) continue
                    yieldAll(importDeclaration.importedTypeNames)
                }
            }
        }

    override fun getQuickFixes(): Array<LocalQuickFix> {
        val scope = GlobalSearchScope.allScope(element.project)
        val imports = StubIndex.getElements(
            ImportableTypeIndex.key,
            element.name,
            element.project,
            scope,
            Importable::class.java
        ).mapNotNull { it.asImport()?.withAlias(element.qualifierName) }.toTypedArray()
        return if (imports.isNotEmpty()) {
            arrayOf(ImportQuickFix(*imports))
        } else {
            arrayOf()
        }
    }

}
