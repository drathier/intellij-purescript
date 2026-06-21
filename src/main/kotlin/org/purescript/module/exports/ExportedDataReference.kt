package org.purescript.module.exports

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReferenceBase
import org.purescript.file.PSFile

class ExportedDataReference(exportedData: ExportedData.Psi) : PsiReferenceBase<ExportedData.Psi>(
    exportedData,
    exportedData.properName.textRangeInParent,
    false
) {
    override fun getVariants(): Array<PsiNamedElement> =
        candidates.distinctBy { it.name }
            .toTypedArray()

    override fun resolve(): PsiElement? {
        val file = myElement.containingFile as? PSFile
        file?.resolveCache?.get(myElement)?.let { return it }
        val result = candidates.firstOrNull { it.name == myElement.name }
        file?.resolveCache?.put(myElement, result)
        return result
    }

    private val candidates: Array<PsiNamedElement>
        get() = try {
            myElement.module.run { arrayOf(*cache.dataDeclarations, *cache.newTypeDeclarations) }
        } catch (_: IllegalStateException) {
            emptyArray<PsiNamedElement>()
        }
}
