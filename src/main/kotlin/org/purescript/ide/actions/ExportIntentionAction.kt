package org.purescript.ide.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.purescript.module.Module
import org.purescript.module.declaration.classes.ClassDecl
import org.purescript.module.declaration.data.DataConstructor
import org.purescript.module.declaration.data.DataDeclaration
import org.purescript.module.declaration.newtype.NewtypeDecl
import org.purescript.module.declaration.type.TypeDecl
import org.purescript.module.declaration.value.ValueDecl
import org.purescript.module.declaration.value.ValueDeclarationGroup
import org.purescript.module.exports.ExportedData
import org.purescript.psi.PSPsiFactory

class ExportIntentionAction : IntentionAction {
    private var actionText: String = "Export"

    private fun findAncestor(element: PsiElement, clazz: Class<*>): PsiElement? {
        var current: PsiElement? = element.parent
        while (current != null) {
            if (clazz.isInstance(current)) return current
            current = current.parent
        }
        return null
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        val element = file.findElementAt(editor?.caretModel?.offset ?: return false) ?: return false
        val declaration = getDeclaration(element) ?: return false
        val module = findAncestor(declaration, Module::class.java) as? Module ?: return false
        return checkAndSetActionText(module, declaration)
    }

    private fun checkAndSetActionText(module: Module, declaration: Any): Boolean {
        val result = when (declaration) {
            is ValueDecl -> {
                if (isValueExported(module, declaration)) null else "Export Value"
            }
            is ValueDeclarationGroup -> {
                if (isValueGroupExported(module, declaration)) null else "Export Value"
            }
            is DataDeclaration -> {
                if (isDataExported(module, declaration)) {
                    if (hasUnexportedConstructors(module, declaration)) {
                        "Export Type and Constructors"
                    } else null
                } else "Export Type"
            }
            is NewtypeDecl -> {
                if (isNewtypeExported(module, declaration)) {
                    if (hasUnexportedConstructor(module, declaration)) {
                        "Export Type and Constructor"
                    } else null
                } else "Export Type"
            }
            is TypeDecl -> {
                if (isTypeOperatorExported(module, declaration)) null else "Export Type Operator"
            }
            is ClassDecl -> {
                if (isClassExported(module, declaration)) null else "Export Class"
            }
            is DataConstructor -> {
                val dataDecl = declaration.parentOfType<DataDeclaration>() ?: return false
                if (isDataExportedWithAllConstructors(module, dataDecl)) null else "Export Constructor"
            }
            else -> null
        }
        
        if (result == null) return false
        actionText = result
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val element = file.findElementAt(editor?.caretModel?.offset ?: return) ?: return
        val declaration = getDeclaration(element) ?: return
        val module = findAncestor(declaration, Module::class.java) as? Module ?: return

        runWriteAction {
            when (declaration) {
                is ValueDecl -> {
                    val name = declaration.name ?: return@runWriteAction
                    addExport(module, name)
                }
                is ValueDeclarationGroup -> {
                    val name = declaration.name ?: return@runWriteAction
                    addExport(module, name)
                }
                is DataDeclaration -> {
                    val name = declaration.name ?: return@runWriteAction
                    if (actionText.contains("and Constructors")) {
                        val ctorNames = declaration.dataConstructors.mapNotNull { it.name }
                        addDataExportWithConstructors(module, name, ctorNames)
                    } else {
                        addExport(module, name)
                    }
                }
                is NewtypeDecl -> {
                    val name = declaration.name ?: return@runWriteAction
                    if (actionText.contains("and Constructor")) {
                        addExport(module, "$name(..)")
                    } else {
                        addExport(module, name)
                    }
                }
                is TypeDecl -> {
                    val name = declaration.name ?: return@runWriteAction
                    addExport(module, name)
                }
                is ClassDecl -> {
                    val name = declaration.name ?: return@runWriteAction
                    addExport(module, name)
                }
                is DataConstructor -> {
                    val name = declaration.name ?: return@runWriteAction
                    val dataDecl = declaration.parentOfType<DataDeclaration>() ?: return@runWriteAction
                    val typeName = dataDecl.name ?: return@runWriteAction
                    addDataExportWithSpecificConstructors(module, typeName, listOf(name))
                }
            }
        }
    }

    override fun getText(): String = actionText

    override fun getFamilyName(): String = "Export"

    override fun startInWriteAction(): Boolean = true

    private fun getDeclaration(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            when (current) {
                is ValueDecl -> return current
                is ValueDeclarationGroup -> return current
                is DataDeclaration -> return current
                is NewtypeDecl -> return current
                is TypeDecl -> return current
                is ClassDecl -> return current
                is DataConstructor -> return current
            }
            current = current.parent
        }
        return null
    }

    private fun isValueExported(module: Module, decl: ValueDecl): Boolean {
        val name = decl.name ?: return false
        return module.exportedValueDeclarationGroups.any { it.name == name }
    }

    private fun isValueGroupExported(module: Module, group: ValueDeclarationGroup): Boolean {
        val name = group.name ?: return false
        return module.exportedValueDeclarationGroups.any { it.name == name }
    }

    private fun isDataExported(module: Module, decl: DataDeclaration): Boolean {
        val name = decl.name ?: return false
        return module.exportedDataDeclarations.any { it.name == name }
    }

    private fun hasUnexportedConstructors(module: Module, decl: DataDeclaration): Boolean {
        val name = decl.name ?: return false
        val exported = module.exportedDataDeclarations.find { it.name == name } as? ExportedData.Psi
        if (exported == null) return true
        val exportedCtorNames = exported.dataMembersNames.toSet()
        return decl.dataConstructors.any { (it as? DataConstructor)?.name !in exportedCtorNames }
    }

    private fun isDataExportedWithAllConstructors(module: Module, decl: DataDeclaration): Boolean {
        val name = decl.name ?: return false
        val exported = module.exportedDataDeclarations.find { it.name == name } as? ExportedData.Psi
        if (exported == null) return false
        if (exported.exportsAll) return true
        val exportedCtorNames = exported.dataMembersNames.toSet()
        return decl.dataConstructors.all { (it as? DataConstructor)?.name in exportedCtorNames }
    }

    private fun isNewtypeExported(module: Module, decl: NewtypeDecl): Boolean {
        val name = decl.name ?: return false
        return module.exportedDataDeclarations.any { it.name == name }
    }

    private fun hasUnexportedConstructor(module: Module, decl: NewtypeDecl): Boolean {
        val name = decl.name ?: return false
        val exported = module.exportedDataDeclarations.find { it.name == name } as? ExportedData.Psi
        if (exported == null) return true
        val ctorName = decl.newTypeConstructor?.name ?: return false
        return ctorName !in exported.dataMembersNames
    }

    private fun isTypeOperatorExported(module: Module, decl: TypeDecl): Boolean {
        val name = decl.name ?: return false
        return module.exportedTypeFixityDeclarations.any { it.name == name }
    }

    private fun isClassExported(module: Module, decl: ClassDecl): Boolean {
        val name = decl.name ?: return false
        return module.exportedClassDeclarations.any { it.name == name }
    }

    private fun addExport(module: Module, name: String) {
        val exportList = module.exports
        val factory = PSPsiFactory(module.project)

        if (exportList == null) {
            val newExportList = factory.createExportList(name)
            val whereKeyword = module.whereKeyword ?: return
            module.addAfter(newExportList, whereKeyword)
        } else {
            val existingNames = exportList.exportedItems.map { it.name }
            val allNames = (existingNames + name).distinct().sorted()
            val newExportList = factory.createExportList(*allNames.toTypedArray())
            exportList.replace(newExportList)
        }
    }

    private fun addDataExportWithConstructors(module: Module, typeName: String, ctorNames: List<String>) {
        addExport(module, "$typeName(..)")
    }

    private fun addDataExportWithSpecificConstructors(module: Module, typeName: String, ctorNames: List<String>) {
        addExport(module, "$typeName(${ctorNames.joinToString(", ")})")
    }
}
