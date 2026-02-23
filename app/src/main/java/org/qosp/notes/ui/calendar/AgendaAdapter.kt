package org.qosp.notes.ui.calendar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NotePriority
import org.qosp.notes.databinding.ItemAgendaHeaderBinding
import org.qosp.notes.databinding.ItemAgendaNoteBinding
import java.time.format.DateTimeFormatter
import java.util.Locale

class AgendaAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onNotePriorityChange: (Note, NotePriority) -> Unit
) : ListAdapter<AgendaItem, RecyclerView.ViewHolder>(DiffCallback) {

    private val headerFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy", Locale.getDefault())
    private val itemDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

    enum class ViewType {
        HEADER, NOTE
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AgendaItem.Header -> ViewType.HEADER.ordinal
            is AgendaItem.NoteItem -> ViewType.NOTE.ordinal
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ViewType.HEADER.ordinal -> {
                val binding = ItemAgendaHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                HeaderViewHolder(binding)
            }
            ViewType.NOTE.ordinal -> {
                val binding = ItemAgendaNoteBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                NoteViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as AgendaItem.Header)
            is NoteViewHolder -> holder.bind((item as AgendaItem.NoteItem).note)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemAgendaHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: AgendaItem.Header) {
            binding.headerText.text = header.date.format(headerFormatter)
        }
    }

    inner class NoteViewHolder(private val binding: ItemAgendaNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position) as? AgendaItem.NoteItem
                    item?.let { onNoteClick(it.note) }
                }
            }
            
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position) as? AgendaItem.NoteItem
                    item?.let { showPriorityMenu(it.note) }
                }
                true
            }
        }

        private fun showPriorityMenu(note: Note) {
            val popup = PopupMenu(binding.root.context, binding.root)
            popup.menu.add(0, NotePriority.HIGH.ordinal, 0, "🔴 High")
            popup.menu.add(0, NotePriority.MEDIUM.ordinal, 1, "🟡 Medium")
            popup.menu.add(0, NotePriority.LOW.ordinal, 2, "🟢 Low")
            popup.menu.add(0, NotePriority.NONE.ordinal, 3, "⚪ None")
            
            popup.setOnMenuItemClickListener { menuItem ->
                val priority = NotePriority.values()[menuItem.itemId]
                onNotePriorityChange(note, priority)
                true
            }
            popup.show()
        }

        fun bind(note: Note) {
            binding.noteTitle.text = note.title.ifBlank { "Untitled Note" }
            binding.notePreview.text = note.content.take(150).replace("\n", " ").trim()
            if (note.eventDate != null) {
                val date = java.time.LocalDate.ofEpochDay(note.eventDate)
                binding.eventDate.text = date.format(itemDateFormatter)
            } else {
                binding.eventDate.text = ""
            }

            val (colorStr, emoji) = when (note.priority) {
                NotePriority.HIGH -> "#F44336" to "🔴"
                NotePriority.MEDIUM -> "#FFC107" to "🟡"
                NotePriority.LOW -> "#4CAF50" to "🟢"
                NotePriority.NONE -> "#9E9E9E" to null
            }

            binding.priorityBorder.setBackgroundColor(Color.parseColor(colorStr))
            if (emoji != null) {
                binding.priorityBadge.text = emoji
                binding.priorityBadge.visibility = View.VISIBLE
            } else {
                binding.priorityBadge.visibility = View.GONE
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<AgendaItem>() {
        override fun areItemsTheSame(oldItem: AgendaItem, newItem: AgendaItem): Boolean {
            return when {
                oldItem is AgendaItem.Header && newItem is AgendaItem.Header -> oldItem.date == newItem.date
                oldItem is AgendaItem.NoteItem && newItem is AgendaItem.NoteItem -> oldItem.note.id == newItem.note.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: AgendaItem, newItem: AgendaItem): Boolean {
            return oldItem == newItem
        }
    }
}
