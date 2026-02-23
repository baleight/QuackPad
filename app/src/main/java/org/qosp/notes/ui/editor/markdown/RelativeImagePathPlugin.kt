package org.qosp.notes.ui.editor.markdown

import io.noties.markwon.AbstractMarkwonPlugin
import java.io.File

class RelativeImagePathPlugin(
    private val noteDirectoryProvider: () -> File?
) : AbstractMarkwonPlugin() {

    override fun processMarkdown(markdown: String): String {
        val noteDirectory = noteDirectoryProvider()
        if (noteDirectory == null) return markdown

        // Regex matches ![alt](path) where path does NOT start with
        // http://, https://, file://, or content://
        val relativeImageRegex = Regex(
            """!\[([^\]]*)\]\((?!https?://|file://|content://)([^)]+)\)"""
        )
        return relativeImageRegex.replace(markdown) { match ->
            val alt = match.groupValues[1]
            val relativePath = match.groupValues[2].trim()
            val absoluteFile = File(noteDirectory, relativePath)
            if (absoluteFile.exists()) {
                "![$alt](file://${absoluteFile.absolutePath})"
            } else {
                match.value // leave unchanged if file not found
            }
        }
    }
}
