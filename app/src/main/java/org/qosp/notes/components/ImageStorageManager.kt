package org.qosp.notes.components

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Manages inline images embedded in note Markdown content as `![name](file:///absolute/path)` tags.
 *
 * All file I/O is performed on [Dispatchers.IO].
 */
class ImageStorageManager(private val context: Context) {

    /** Lazy directory reference; created on first access. */
    val imagesDir: File
        get() = File(context.filesDir, IMAGES_FOLDER).also { it.mkdirs() }

    /**
     * Copies the image at [sourceUri] into the internal images folder.
     *
     * @param sourceUri  A content:// or file:// URI pointing to the source image.
     * @param suggestedName  Optional filename hint (without extension).
     * @return The absolute path of the saved file, or `null` on failure.
     */
    suspend fun copyImageToStorage(sourceUri: Uri, suggestedName: String? = null): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val extension = extensionFromUri(context, sourceUri) ?: "jpg"
                val baseName = suggestedName?.takeIf { it.isNotBlank() } ?: "img_${UUID.randomUUID()}"
                val destFile = uniqueFile(imagesDir, "$baseName.$extension")

                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                destFile.absolutePath
            }.getOrNull()
        }

    /**
     * Copies the image at [sourceUri] into the note's assets subfolder if [noteDirectory] is provided,
     * otherwise falls back to internal storage.
     *
     * @return The relative path `assets/filename.jpg` or absolute `file://...` URI on failure.
     */
    suspend fun copyImageForNote(sourceUri: Uri, noteDirectory: File?, suggestedName: String? = null): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val extension = extensionFromUri(context, sourceUri) ?: "jpg"
                val baseName = suggestedName?.takeIf { it.isNotBlank() } ?: "img_${UUID.randomUUID()}"

                if (noteDirectory != null) {
                    val assetsDir = File(noteDirectory, "assets")
                    assetsDir.mkdirs()
                    val destFile = uniqueFile(assetsDir, "$baseName.$extension")

                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    "assets/${destFile.name}"
                } else {
                    val destFile = uniqueFile(imagesDir, "$baseName.$extension")
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    "file://${destFile.absolutePath}"
                }
            }.getOrNull()
        }

    /**
     * Extracts all absolute file paths embedded in Markdown image tags of the form
     * `![...](file:///absolute/path)` within [content].
     */
    fun parseImagePaths(content: String): List<String> =
        IMAGE_REGEX.findAll(content).map { it.groupValues[1] }.toList()

    /**
     * Extracts all relative file paths embedded in Markdown image tags of the form
     * `![...](assets/path)` within [content].
     */
    fun parseRelativeImagePaths(content: String): List<String> =
        RELATIVE_IMAGE_REGEX.findAll(content).map { it.groupValues[1] }.toList()

    /**
     * Deletes every image file referenced by `file://` Markdown tags found in [content].
     * If [noteDirectory] is provided, it also deletes relative `assets/` images.
     */
    suspend fun deleteImagesReferencedIn(content: String, noteDirectory: File? = null) = withContext(Dispatchers.IO) {
        parseImagePaths(content).forEach { path ->
            runCatching { File(path).delete() }
        }
        
        if (noteDirectory != null) {
            parseRelativeImagePaths(content).forEach { relPath ->
                runCatching { File(noteDirectory, relPath).delete() }
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun extensionFromUri(context: Context, uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri) ?: return null
        return when {
            mimeType.endsWith("png")  -> "png"
            mimeType.endsWith("gif")  -> "gif"
            mimeType.endsWith("webp") -> "webp"
            mimeType.startsWith("image") -> "jpg"
            else -> null
        }
    }

    /** Returns a [File] inside [dir] whose name does not yet exist, appending a counter if needed. */
    private fun uniqueFile(dir: File, desiredName: String): File {
        val base = desiredName.substringBeforeLast(".")
        val ext  = desiredName.substringAfterLast(".", "")
        var candidate = File(dir, desiredName)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "${base}_$counter" else "${base}_$counter.$ext")
            counter++
        }
        return candidate
    }

    companion object {
        const val IMAGES_FOLDER = "images"

        /**
         * Matches `![alt](file:///absolute/path)` and captures the absolute path.
         * Group 1 = `/absolute/path`
         */
        val IMAGE_REGEX = Regex("""!\[.*?]\(file://(/[^)]*)\)""")

        /**
         * Matches `![alt](assets/path)` and captures the relative path.
         * Group 1 = `assets/path`
         */
        val RELATIVE_IMAGE_REGEX = Regex("""!\[.*?]\((assets/[^)]*)\)""")
    }
}
