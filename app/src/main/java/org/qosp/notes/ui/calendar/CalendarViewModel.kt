package org.qosp.notes.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NotePriority
import org.qosp.notes.data.repo.NoteRepository
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _currentYearMonth = MutableStateFlow(YearMonth.now())
    val currentYearMonth: StateFlow<YearMonth> = _currentYearMonth

    private val _selectedDay = MutableStateFlow<LocalDate?>(LocalDate.now())
    val selectedDay: StateFlow<LocalDate?> = _selectedDay

    private val allDatedNotes = noteRepository.getNotesWithDate()

    val notesByDayInMonth: StateFlow<Map<LocalDate, List<Note>>> = allDatedNotes
        .combine(_currentYearMonth) { notes, currentMonth ->
            val startOfMonthDay = currentMonth.atDay(1).toEpochDay()
            val endOfMonthDay = currentMonth.atEndOfMonth().toEpochDay()
            
            notes
                .filter { it.eventDate != null && it.eventDate in startOfMonthDay..endOfMonthDay }
                .groupBy { LocalDate.ofEpochDay(it.eventDate!!) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val agendaNotes: StateFlow<List<AgendaItem>> = allDatedNotes
        .map { notes ->
            val items = mutableListOf<AgendaItem>()
            val grouped = notes.groupBy { LocalDate.ofEpochDay(it.eventDate!!) }
            
            grouped.entries.sortedByDescending { it.key }.forEach { (date, dayNotes) ->
                items.add(AgendaItem.Header(date))
                dayNotes.forEach { note ->
                    items.add(AgendaItem.NoteItem(note))
                }
            }
            items
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDay(date: LocalDate) {
        _selectedDay.value = date
    }

    fun nextMonth() {
        _currentYearMonth.value = _currentYearMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _currentYearMonth.value = _currentYearMonth.value.minusMonths(1)
    }
    
    fun setMonth(yearMonth: YearMonth) {
        _currentYearMonth.value = yearMonth
    }

    fun updatePriority(note: Note, priority: NotePriority) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedNote = note.copy(priority = priority)
            noteRepository.updateNotes(updatedNote)
        }
    }
}
