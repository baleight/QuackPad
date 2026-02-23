package org.qosp.notes.components

import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Watches a root notes directory for filesystem changes and triggers
 * [FolderSyncManager.syncFromFilesystem] whenever content changes.
 *
 * Use [start] / [stop] to control the observation lifecycle.
 */
class FileWatcher(
    private val folderSyncManager: FolderSyncManager,
) {
    private val tag = FileWatcher::class.java.simpleName
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: FileObserver? = null
    private var currentPath: String? = null

    /**
     * Start watching [rootPath]. If already watching a different path, the old watcher is stopped first.
     */
    fun start(rootPath: String) {
        if (currentPath == rootPath && observer != null) return
        stop()
        currentPath = rootPath

        observer = createObserver(rootPath).also { it.startWatching() }
        Log.i(tag, "FileWatcher started on '$rootPath'")
    }

    /** Stop watching the current directory. */
    fun stop() {
        observer?.stopWatching()
        observer = null
        Log.d(tag, "FileWatcher stopped")
    }

    // API level 29+ multi-event constructor is preferred; fall back to legacy for API < 29.
    @Suppress("DEPRECATION")
    private fun createObserver(rootPath: String): FileObserver {
        val mask = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            object : FileObserver(File(rootPath), mask) {
                override fun onEvent(event: Int, path: String?) = onAnyEvent()
            }
        } else {
            object : FileObserver(rootPath, mask) {
                override fun onEvent(event: Int, path: String?) = onAnyEvent()
            }
        }
    }

    private fun onAnyEvent() {
        val path = currentPath ?: return
        scope.launch {
            Log.d(tag, "FileWatcher: change detected in '$path', triggering sync")
            folderSyncManager.syncFromFilesystem(path)
        }
    }
}
