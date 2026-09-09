package org.purescript.file

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.DefaultStubBuilder
import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.psi.tree.IStubFileElementType
import org.purescript.PSLanguage
import org.purescript.inference.TypeSpace
import org.purescript.module.Module
import org.purescript.module.exports.ExportedModule
import java.nio.file.Path
import java.util.Collections
import java.util.WeakHashMap
import kotlin.jvm.Volatile
import java.nio.file.Paths

class PSFile(viewProvider: FileViewProvider) :
    PsiFileBase(viewProvider, PSLanguage){
    class Stub(file: PSFile) : PsiFileStubImpl<PSFile>(file) {
        override fun getType() = Type
    }

    object Type : IStubFileElementType<Stub>("PSFile", PSLanguage) {
        override fun getStubVersion(): Int = 15
        override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile): Stub = Stub(file as PSFile)
        }
    }

    override fun getFileType(): FileType = PSFileType
    override fun toString(): String = "Purescript File"

    /**
     * @return the [Module] that this file contains,
     * or null if the module couldn't be parsed
     */
    val module: Module?
        get() = findChildByClass(Module::class.java)

    fun suggestModuleName(): String? {
        val fileName = name.removeSuffix(".purs")
        val path = parent?.virtualFile?.path ?: return null
        val directoryPath = Paths.get(path)
        val relativePath = try {
            project
                .basePath
                ?.let { Paths.get(it).toAbsolutePath() }
                ?.relativize(directoryPath.toAbsolutePath())
                ?: directoryPath
        } catch (ignore: IllegalArgumentException) {
            directoryPath
        }
        return relativePath
            .reversed<Path?>()
            .takeWhile<Path?> { "$it" != "src" && "$it" != "test" }
            .filter<Path?> { "$it".first().isUpperCase() }
            .reversed<Path?>()
            .joinToString<Path?>(".")
            .let<String, String> {
                "$it.$fileName"
            }
            .removePrefix(".")
    }

    val exportedNames: List<String>
        get() = module?.exportedItems
            ?.filter { it !is ExportedModule }
            ?.map { it.text.trim() }
            ?.toList()
            ?: emptyList()

    @Volatile var typeSpace = TypeSpace()
    private val resolveCache: MutableMap<PsiElement, PsiNamedElement?> = Collections.synchronizedMap(WeakHashMap())
    private val unusedGroupCache: MutableMap<PsiElement, Boolean> = Collections.synchronizedMap(WeakHashMap())
    private var lastContentStamp: Long = -1

    fun resolveCacheGet(element: PsiElement): PsiNamedElement? = resolveCache[element]
    fun resolveCachePut(element: PsiElement, value: PsiNamedElement?) { resolveCache[element] = value }

    fun unusedGroupCacheGet(element: PsiElement): Boolean? = unusedGroupCache[element]
    fun unusedGroupCachePut(element: PsiElement, unused: Boolean) { unusedGroupCache[element] = unused }

    override fun subtreeChanged() {
        val vFile = virtualFile
        if (vFile == null || vFile.modificationStamp != lastContentStamp) {
            lastContentStamp = vFile?.modificationStamp ?: 0
            resolveCache.clear()
            unusedGroupCache.clear()
            typeSpace = TypeSpace()
        }
        super.subtreeChanged()
    }
}
