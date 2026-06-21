package org.purescript.module.declaration.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReferenceBase
import org.purescript.file.PSFile

class ImportedDataReference(element: PSImportedData) : PsiReferenceBase<PSImportedData>(
    element,
    element.properName.textRangeInParent,
    false
) {

    override fun getVariants(): Array<Any> =
        candidates.toTypedArray()

    override fun resolve(): PsiElement? {
        val file = element.containingFile as? PSFile
        file?.resolveCache?.get(element)?.let { return it }
        val result = candidates.firstOrNull { it.name == element.name }
        file?.resolveCache?.put(element, result)
        return result
    }

    private val candidates: List<PsiNamedElement>
        get() =
            element.importDeclaration.importedModule
                ?.run { exportedNewTypeDeclarations + exportedDataDeclarations + exportedTypeSynonymDeclarations}
                ?: emptyList()

}
