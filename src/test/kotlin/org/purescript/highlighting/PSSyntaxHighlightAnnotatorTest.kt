package org.purescript.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.purescript.*
import org.purescript.module.declaration.value.ValueDeclarationGroup
import org.purescript.module.declaration.value.binder.VarBinder

class PSSyntaxHighlightAnnotatorTest : BasePlatformTestCase() {

    fun `test highlights top-level value as global variable`() {
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            bar = 1
            baz = bar
        """.trimIndent())
        val identifier = file.getExpressionIdentifiers().last()
        val resolved = identifier.reference.resolve()
        assertEquals(file.getValueDeclarationGroupByName("bar"), resolved)
    }

    fun `test highlights let-bound value as local variable`() {
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            bar = let baz = 1 in baz
        """.trimIndent())
        val identifier = file.getExpressionIdentifiers().last()
        val resolved = identifier.reference.resolve()
        assertNotNull(resolved)
        assertEquals("baz", resolved?.name)
        assertTrue(resolved is ValueDeclarationGroup)
    }

    fun `test highlights parameter as parameter`() {
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            bar x = x
        """.trimIndent())
        val identifier = file.getExpressionIdentifiers().last()
        val resolved = identifier.reference.resolve()
        assertTrue(resolved is VarBinder)
    }

    fun `test highlights imported value`() {
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

    fun `test highlights local scope shadows imported`() {
        myFixture.configureByText("Bar.purs", """
            module Bar (x) where
            x = 1
        """.trimIndent())
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            import Bar
            y = let x = 2 in x
        """.trimIndent())
        val identifier = file.getExpressionIdentifiers().last()
        val resolved = identifier.reference.resolve()
        assertNotNull(resolved)
        assertEquals("x", resolved?.name)
        assertTrue(resolved is ValueDeclarationGroup)
    }

    fun `test highlights qualified reference`() {
        myFixture.configureByText("Bar.purs", """
            module Bar (qux) where
            qux = 1
        """.trimIndent())
        val file = myFixture.configureByText("Foo.purs", """
            module Foo where
            import Bar as B
            baz = B.qux
        """.trimIndent())
        val identifier = file.getExpressionIdentifiers().last()
        val resolved = identifier.reference.resolve()
        assertEquals("qux", resolved?.name)
    }
}
