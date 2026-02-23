package org.qosp.notes.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.msoul.datastore.defaultOf
import org.qosp.notes.components.FolderSyncManager
import org.qosp.notes.data.model.FolderEntity
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.FolderRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortMethod

data class FolderContent(
    val subfolders: List<FolderEntity> = emptyList(),
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
)

class FolderViewModel(
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
    private val folderSyncManager: FolderSyncManager,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    /** Current folder id being browsed. Null means root. */
    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    /**
     * The breadcrumb chain from root down to the current folder.
     * E.g. [Work, Projects] means the user is inside Projects which is inside Work.
     */
    val breadcrumbPath: StateFlow<List<FolderEntity>> = _currentFolderId
        .flatMapLatest { folderId -> buildBreadcrumb(folderId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Combined content (subfolders + notes) for the folder currently being browsed. */
    val folderContent: StateFlow<FolderContent> = _currentFolderId
        .flatMapLatest { folderId ->
            val subfolderFlow = folderRepository.getFoldersByParent(folderId)
            val noteFlow: kotlinx.coroutines.flow.Flow<List<Note>> = if (folderId == null) {
                noteRepository.getNotesAtRoot(defaultOf<SortMethod>())
            } else {
                noteRepository.getByFolder(folderId, defaultOf<SortMethod>())
            }
            combine(subfolderFlow, noteFlow) { subfolders, notes ->
                FolderContent(subfolders = subfolders, notes = notes, isLoading = false)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FolderContent(isLoading = true)
        )

    // --------------------------------------------------------------------------
    // Navigation
    // --------------------------------------------------------------------------

    fun navigateInto(folderId: Long) {
        _currentFolderId.value = folderId
    }

    fun navigateUp() {
        viewModelScope.launch {
            val parent = breadcrumbPath.value.dropLast(1).lastOrNull()
            _currentFolderId.value = parent?.id
        }
    }

    /** Jump directly to a specific folder (used via breadcrumb tap). */
    fun navigateTo(folderId: Long?) {
        _currentFolderId.value = folderId
    }

    // --------------------------------------------------------------------------
    // CRUD
    // --------------------------------------------------------------------------

    fun createFolder(name: String, parentAbsolutePath: String) {
        val parentId = _currentFolderId.value
        viewModelScope.launch(Dispatchers.IO) {
            folderSyncManager.createFolder(name, parentId, parentAbsolutePath)
        }
    }

    fun renameFolder(folder: FolderEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            folderSyncManager.renameFolder(folder, newName)
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            folderSyncManager.deleteFolder(folder)
        }
    }

    fun syncCurrentFolder(rootPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            folderSyncManager.syncFromFilesystem(rootPath)
        }
    }

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    /** Walks up the folder hierarchy to produce the breadcrumb list (root first). */
    private fun buildBreadcrumb(targetId: Long?) = flow<List<FolderEntity>> {
        if (targetId == null) {
            emit(emptyList())
            return@flow
        }
        val chain = mutableListOf<FolderEntity>()
        var current: FolderEntity? = folderRepository.getFolderById(targetId).first()
        while (current != null) {
            chain.add(0, current)
            val parentId = current.parentId ?: break
            current = folderRepository.getFolderById(parentId).first()
        }
        emit(chain)
    }
}
