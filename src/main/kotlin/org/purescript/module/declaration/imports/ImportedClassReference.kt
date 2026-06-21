package org.purescript.module.declaration.imports

import com.intellij.psi.PsiReferenceBase
import org.purescript.file.PSFile
import org.purescript.module.declaration.classes.ClassDecl

class ImportedClassReference(importedClass: PSImportedClass) : PsiReferenceBase<PSImportedClass>(
    importedClass,
    importedClass.properName.textRangeInParent,
    false
) {

    override fun getVariants(): Array<Any> =
        candidates.toTypedArray()

    override fun resolve(): ClassDecl? {
        val file = myElement.containingFile as? PSFile
        file?.resolveCache?.get(myElement)?.let { return it as? ClassDecl }
        val result = candidates.firstOrNull { it.name == myElement.name }
        file?.resolveCache?.put(myElement, result)
        return result
    }

    private val candidates: List<ClassDecl>
        get() = myElement.importDeclaration.importedModule?.exportedClassDeclarations
            ?: emptyList()
}
