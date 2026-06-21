package org.purescript.module.exports

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReferenceBase
import org.purescript.file.PSFile
import org.purescript.psi.PSPsiFactory

class ExportedValueReference(exportedValue: ExportedValue.Psi) : PsiReferenceBase<ExportedValue.Psi>(
    exportedValue,
    exportedValue.identifier.textRangeInParent,
    false
) {

    override fun getVariants(): Array<PsiNamedElement> =
        candidates.distinctBy { it.name }.toTypedArray()

    override fun resolve(): PsiElement? {
        val file = myElement.containingFile as? PSFile
        file?.resolveCache?.get(myElement)?.let { return it }
        val result = candidates.firstOrNull { it.name == myElement.name }
        file?.resolveCache?.put(myElement, result)
        return result
    }

    private val candidates: List<PsiNamedElement>
        get() = myElement?.module?.run { listOf(*valueGroups, *foreignValues, *classMembers.toTypedArray()) }
            ?: emptyList()

    override fun handleElementRename(name: String): PsiElement? {
        val newName = PSPsiFactory(element.project).createIdentifier(name)
            ?: return null
        element.identifier.replace(newName)
        return element
    }
}
