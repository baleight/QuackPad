package org.qosp.notes.ui.calendar

import org.qosp.notes.data.model.Note
import java.time.LocalDate

sealed class AgendaItem {
    data class Header(val date: LocalDate) : AgendaItem()
    data class NoteItem(val note: Note) : AgendaItem()
}
