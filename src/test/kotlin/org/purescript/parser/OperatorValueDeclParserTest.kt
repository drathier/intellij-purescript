package org.purescript.parser

class OperatorValueDeclParserTest : PSLanguageParserTestBase("operatorvaluedecl") {
    fun testOperatorDecl() = doTest(true, true)
    fun testBenchmarkFile() {
        val text = loadFile("BenchmarkFile.purs")
        val file = createPsiFile("BenchmarkFile", text)
        assertNotNull("file should not be null", file)
        assertNotNull("file should have children", file.firstChild)
    }
}
