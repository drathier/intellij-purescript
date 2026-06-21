package org.purescript.module.exports

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.purescript.file.PSFile
import org.purescript.psi.PSPsiFactory
import org.purescript.module.declaration.imports.Import

class ExportedModuleReference(exportedModule: ExportedModule) : PsiReferenceBase<ExportedModule>(
    exportedModule,
    exportedModule.moduleName.textRangeInParent,
    false
) {
    override fun getVariants(): Array<String> {
        return candidates.map { it.name }.toTypedArray()
    }

    override fun resolve(): PsiElement? {
        val file = myElement.containingFile as? PSFile
        file?.resolveCache?.get(myElement)?.let { return it }
        val result = if (element.name == myElement.module.name) {
            myElement.module
        } else {
            candidates.firstOrNull { it.name == myElement.name }
                ?.run { importAlias ?: importedModule }
        }
        file?.resolveCache?.put(myElement, result)
        return result
    }

    override fun handleElementRename(name: String): PsiElement? {
        val newProperName = PSPsiFactory(element.project).createModuleName(name)
            ?: return null
        element.moduleName.replace(newProperName)
        return element
    }

    private val candidates: Array<Import>
        get() =
            myElement.module.cache.imports
}
