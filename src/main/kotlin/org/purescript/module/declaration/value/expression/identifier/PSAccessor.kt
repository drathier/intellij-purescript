package org.purescript.module.declaration.value.expression.identifier

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import org.purescript.inference.InferType
import org.purescript.module.declaration.Importable
import org.purescript.module.declaration.ImportableTypeIndex
import org.purescript.module.declaration.data.DataConstructor
import org.purescript.module.declaration.data.DataDeclaration
import org.purescript.module.declaration.newtype.NewtypeCtor
import org.purescript.module.declaration.newtype.NewtypeDecl
import org.purescript.module.declaration.type.Labeled
import org.purescript.module.declaration.type.LabeledIndex
import org.purescript.module.declaration.type.TypeDecl
import org.purescript.module.declaration.type.type.PSType
import org.purescript.module.declaration.type.type.PSTypeConstructor
import org.purescript.module.declaration.type.type.TypeRecord
import org.purescript.module.declaration.value.Similar
import org.purescript.module.declaration.value.binder.ConstructorBinder
import org.purescript.module.declaration.value.binder.VarBinder
import org.purescript.module.declaration.value.expression.Expression
import org.purescript.module.declaration.value.expression.RecordAccess
import org.purescript.name.PSIdentifier
import org.purescript.psi.PSPsiElement
import org.purescript.psi.PSPsiFactory

class PSAccessor(node: ASTNode) : PSPsiElement(node), Similar {
    private val identifier get() = findNotNullChildByClass(PSIdentifier::class.java)
    override fun getName(): String = identifier.name
    override fun getReference(): PsiReference = AccessorReference(this)

    class AccessorReference(val accessor: PSAccessor) : PsiReferenceBase<PSAccessor>(
        accessor,
        accessor.identifier.textRangeInParent,
        false
    ) {
        override fun resolve(): PsiElement? {
            val recordAccess = accessor.parentOfType<RecordAccess>() ?: return fallback()
            val recordExpr = recordAccess.record
            val fieldName = accessor.name

            // Try to use type inference; if it fails, skip type-based strategies
            val type: InferType?
            try {
                recordAccess.inferType()
                type = recordExpr.inferType()
            } catch (e: Exception) {
                return fallback()
            }

            // Strategy 1: Type alias resolution
            resolveViaAlias(type, fieldName)?.let { return it }

            // Strategy 2: Constructor binder navigation (doesn't need type inference)
            resolveViaConstructorBinder(recordExpr, fieldName)?.let { return it }

            // Strategy 3: File-scoped TypeRecord match
            resolveViaFileScope(type, fieldName)?.let { return it }

            // Strategy 4: Chained accessor resolution (d.task.name)
            resolveViaChainedAccess(recordExpr, fieldName)?.let { return it }

            return fallback()
        }

        private fun fallback(): PsiElement? {
            val scope = GlobalSearchScope.allScope(accessor.project)
            return LabeledIndex.getLabeled(accessor.name, accessor.project, scope).singleOrNull()
        }

        private fun resolveViaAlias(type: InferType?, fieldName: String): PsiElement? {
            val aliasName = when (type) {
                is InferType.Alias -> type.name
                else -> return null
            }
            val typeDecls = StubIndex.getElements(
                ImportableTypeIndex.KEY,
                aliasName,
                accessor.project,
                GlobalSearchScope.allScope(accessor.project),
                Importable::class.java
            ).filterIsInstance<TypeDecl>()
            return typeDecls.firstNotNullOfOrNull { typeDecl ->
                val typeBody = typeDecl.type ?: return@firstNotNullOfOrNull null
                val typeRecord = typeBody as? TypeRecord
                    ?: typeBody.childrenOfType<TypeRecord>().firstOrNull()
                    ?: return@firstNotNullOfOrNull null
                typeRecord.childrenOfType<Labeled>().find { it.name == fieldName }
            }
        }

        private fun resolveViaConstructorBinder(
            recordExpr: Expression,
            fieldName: String
        ): PsiElement? {
            val identifier = recordExpr as? PSExpressionIdentifier ?: return null
            val binder = identifier.reference.resolve() as? VarBinder ?: return null
            val appBinder = binder.parentOfType<org.purescript.module.declaration.value.binder.AppBinder>()
                ?: return null
            val constructorBinder = appBinder.binders
                .filterIsInstance<ConstructorBinder>().firstOrNull() ?: return null
            val ctor = constructorBinder.reference.resolve()
            return when (ctor) {
                is DataConstructor -> ctor.childrenOfType<PSType>()
                    .firstNotNullOfOrNull { findLabeledInType(it, fieldName) }
                is NewtypeCtor -> findLabeledInType(ctor.typeAtom, fieldName)
                else -> null
            }
        }

        private fun findLabeledInType(type: PsiElement, fieldName: String): Labeled? {
            val typeRecord = type as? TypeRecord
                ?: type.childrenOfType<TypeRecord>().firstOrNull() ?: return null
            return typeRecord.childrenOfType<Labeled>().find { it.name == fieldName }
        }

        private fun resolveViaChainedAccess(recordExpr: Expression, fieldName: String): PsiElement? {
            val innerAccess = recordExpr as? RecordAccess ?: return null
            val innerLabeled = innerAccess.accessor.reference.resolve() as? Labeled ?: return null
            return resolveLabeledFromType(innerLabeled, fieldName)
        }

        private fun resolveLabeledFromType(labeled: Labeled, fieldName: String): PsiElement? {
            val fieldType = labeled.type ?: return null
            // Direct TypeRecord (inline record type)
            val typeRecord = fieldType as? TypeRecord
                ?: fieldType.childrenOfType<TypeRecord>().firstOrNull()
            if (typeRecord != null) {
                return typeRecord.childrenOfType<Labeled>().find { it.name == fieldName }
            }
            // Type constructor referencing a named type
            val typeCtor = fieldType as? PSTypeConstructor ?: return null
            val resolved = typeCtor.reference.resolve()
            return when (resolved) {
                is TypeDecl -> {
                    val body = resolved.type ?: return null
                    val tr = body as? TypeRecord
                        ?: body.childrenOfType<TypeRecord>().firstOrNull() ?: return null
                    tr.childrenOfType<Labeled>().find { it.name == fieldName }
                }
                is DataDeclaration -> {
                    resolved.dataConstructors.firstNotNullOfOrNull { ctor ->
                        ctor.childrenOfType<TypeRecord>().firstNotNullOfOrNull { tr ->
                            tr.childrenOfType<Labeled>().find { it.name == fieldName }
                        }
                    }
                }
                is NewtypeDecl -> {
                    val ntc = resolved.newTypeConstructor
                    val tr = ntc.typeAtom as? TypeRecord
                        ?: ntc.typeAtom.childrenOfType<TypeRecord>().firstOrNull() ?: return null
                    tr.childrenOfType<Labeled>().find { it.name == fieldName }
                }
                else -> null
            }
        }

        private fun resolveViaFileScope(type: InferType, fieldName: String): PsiElement? {
            val app = type as? InferType.App ?: return null
            if (app.f != InferType.Record) return null
            val labels = when (val row = app.on) {
                is InferType.RowList -> row.labels.map { it.first }.toSet()
                is InferType.RowMerge -> row.mergedLabels().map { it.first }.toSet()
                else -> return null
            }
            val typeRecords = accessor.containingFile.childrenOfType<TypeRecord>()
                .filter { typeRecord ->
                typeRecord.childrenOfType<Labeled>().map { it.name }.toSet() == labels
            }
            if (typeRecords.size == 1) {
                return typeRecords.single()
                    .childrenOfType<Labeled>()
                    .find { it.name == fieldName }
            }
            return null
        }

        override fun handleElementRename(name: String): PsiElement? {
            val oldName = accessor.identifier
            val newName = PSPsiFactory(accessor.project).createIdentifier(name)
                ?: return null
            oldName.replace(newName)
            return accessor
        }

        override fun isReferenceTo(element: PsiElement): Boolean {
            if (element !is Labeled) return false
            if (element.name != accessor.name) return false
            return accessor.manager.areElementsEquivalent(resolve(), element)
        }
    }
}
