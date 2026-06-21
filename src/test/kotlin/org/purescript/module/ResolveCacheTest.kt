package org.purescript.module

import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.purescript.*
import org.purescript.module.declaration.value.ValueDeclarationGroup

class ResolveCacheTest : BasePlatformTestCase() {

    fun `test resolve cache returns same result as direct resolve`() {
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            bar = 1
            baz = bar
        """.trimIndent())
        val expressionIdentifiers = file.getExpressionIdentifiers()
        assertEquals(1, expressionIdentifiers.size)
        val identifier = expressionIdentifiers.first()
        val resolved = identifier.reference.resolve()
        assertTrue(resolved is ValueDeclarationGroup)
        assertEquals("bar", resolved?.name)
    }

    fun `test resolve cache for imported value`() {
        myFixture.configureByText("Bar.purs", """
            module Bar (qux) where
            qux = 1
        """.trimIndent())
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            import Bar
            baz = qux
        """.trimIndent())
        val identifier = file.getExpressionIdentifiers().last()
        val resolved = identifier.reference.resolve()
        assertEquals("qux", resolved?.name)
    }

    fun `test resolve cache for type constructor`() {
        myFixture.configureByText("Bar.purs", """
            module Bar where
            data Qux = Qux
        """.trimIndent())
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            import Bar
            a :: Qux
        """.trimIndent())
        val typeConstructor = file.getTypeConstructor()
        val resolved = typeConstructor.reference.resolve()
        assertEquals("Qux", resolved?.name)
    }

    fun `test resolve cache for imported data reference`() {
        myFixture.configureByText("Bar.purs", """
            module Bar (Qux(..)) where
            data Qux = Qux
        """.trimIndent())
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            import Bar (Qux(..))
        """.trimIndent())
        val importedData = file.getImportedData()
        val resolved = importedData.reference.resolve()
        assertEquals("Qux", (resolved as? PsiNamedElement)?.name)
    }

    fun `test resolve cache for imported value reference`() {
        myFixture.configureByText("Bar.purs", """
            module Bar (qux) where
            qux = 1
        """.trimIndent())
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            import Bar (qux)
        """.trimIndent())
        val importedValue = file.getImportedValue()
        val resolved = importedValue.reference.resolve()
        assertEquals("qux", (resolved as? PsiNamedElement)?.name)
    }

    fun `test resolve cache returns null for unresolvable reference`() {
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            baz = nonexistent
        """.trimIndent())
        val identifier = file.getExpressionIdentifiers().last()
        val resolved = identifier.reference.resolve()
        assertNull(resolved)
    }
}
