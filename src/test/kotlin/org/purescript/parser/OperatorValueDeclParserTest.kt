package org.purescript.parser

import com.intellij.psi.PsiErrorElement

class OperatorValueDeclParserTest : PSLanguageParserTestBase("operatorvaluedecl") {
    fun testOperatorDecl() = doTest(true, true)

    fun testBenchmarkFile() {
        val text = java.nio.file.Files.readString(java.nio.file.Paths.get("test-data/operatorvaluedecl/BenchmarkFile.purs"))
        val file = createPsiFile("BenchmarkFile", text)
        val errors = mutableListOf<String>()
        file.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitErrorElement(element: PsiErrorElement) {
                errors.add("Offset ${element.textOffset}: '${element.text}': ${element.errorDescription}")
            }
        })
        if (errors.isNotEmpty()) {
            throw AssertionError("Errors:\n" + errors.joinToString("\n"))
        }
    }
}
