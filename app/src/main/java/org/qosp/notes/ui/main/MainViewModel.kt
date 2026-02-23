package org.qosp.notes.ui.main

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.ui.common.AbstractNotesViewModel

class MainViewModel(
    private val noteRepository: NoteRepository,
    preferenceRepository: PreferenceRepository,
    backendProvider: BackendProvider,
) : AbstractNotesViewModel(preferenceRepository, backendProvider) {

    override val provideNotes = { sortMethod: SortMethod ->
        noteRepository.getNonDeletedOrArchived(sortMethod)
    }
}
