package org.purescript.module.exports

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReferenceBase
import org.purescript.file.PSFile
import org.purescript.module.declaration.data.DataDeclaration
import org.purescript.module.declaration.newtype.NewtypeDecl

class ExportedDataMemberReference(exportedDataMember: PSExportedDataMember) : PsiReferenceBase<PSExportedDataMember>(
    exportedDataMember,
    exportedDataMember.properName.textRangeInParent,
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

    private val candidates: Array<out PsiNamedElement>
        get() = when (val declaration = myElement.exportedData?.reference?.resolve()) {
            is DataDeclaration -> declaration.dataConstructorList?.dataConstructors ?: emptyArray()
            is NewtypeDecl -> arrayOf(declaration.newTypeConstructor)
            else -> emptyArray()
        }
}
