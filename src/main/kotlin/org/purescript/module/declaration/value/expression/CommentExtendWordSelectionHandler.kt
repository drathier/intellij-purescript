package org.purescript.module.declaration.value.expression

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

class CommentExtendWordSelectionHandler : ExtendWordSelectionHandler {
    override fun canSelect(e: PsiElement) = e is PsiComment

    override fun select(
        e: PsiElement,
        editorText: CharSequence,
        cursorOffset: Int,
        editor: Editor
    ): MutableList<TextRange> {
        val comment = e as? PsiComment ?: return mutableListOf()
        val textRange = comment.textRange
        val commentText = editorText.subSequence(textRange.startOffset, textRange.endOffset)
        
        val ranges = mutableListOf<TextRange>()
        
        val commentStart = textRange.startOffset
        var inWord = false
        var wordStart = 0
        
        for (i in commentText.indices) {
            val c = commentText[i]
            val isWordChar = c.isLetterOrDigit() || c == '_'
            
            if (isWordChar && !inWord) {
                wordStart = i
                inWord = true
            } else if (!isWordChar && inWord) {
                if (i > wordStart) {
                    ranges.add(TextRange(commentStart + wordStart, commentStart + i))
                }
                inWord = false
            }
        }
        
        if (inWord && wordStart < commentText.length) {
            ranges.add(TextRange(commentStart + wordStart, commentStart + commentText.length))
        }
        
        ranges.add(textRange)
        
        return ranges
    }
}
