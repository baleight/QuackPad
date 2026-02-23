package org.qosp.notes.ui.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.R
import org.qosp.notes.data.model.FolderEntity
import org.qosp.notes.data.model.Note

/**
 * Adapter that shows folders first, then notes in a single flat list.
 * Use [submitContent] to update both lists atomically.
 */
class FolderContentAdapter(
    private val onFolderClick: (FolderEntity) -> Unit,
    private val onFolderLongClick: (FolderEntity) -> Unit,
    private val onNoteClick: (Note) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()  // holds FolderEntity or Note

    companion object {
        const val TYPE_FOLDER = 0
        const val TYPE_NOTE = 1
    }

    fun submitContent(folders: List<FolderEntity>, notes: List<Note>) {
        items.clear()
        items.addAll(folders.sortedBy { it.name })
        items.addAll(notes.sortedBy { it.title })
        notifyDataSetChanged()
    }

    fun isFolderAt(position: Int): Boolean = items.getOrNull(position) is FolderEntity

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) =
        if (items[position] is FolderEntity) TYPE_FOLDER else TYPE_NOTE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOLDER -> FolderViewHolder(
                inflater.inflate(R.layout.item_folder, parent, false)
            )
            else -> NoteViewHolder(
                inflater.inflate(R.layout.item_note, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FolderViewHolder -> {
                val folder = items[position] as FolderEntity
                holder.bind(folder)
                holder.itemView.setOnClickListener { onFolderClick(folder) }
                holder.itemView.setOnLongClickListener { onFolderLongClick(folder); true }
            }
            is NoteViewHolder -> {
                val note = items[position] as Note
                holder.bind(note)
                holder.itemView.setOnClickListener { onNoteClick(note) }
            }
        }
    }

    // --------------------------------------------------------------------------
    // ViewHolders
    // --------------------------------------------------------------------------

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_folder_name)
        private val icon: ImageView = itemView.findViewById(R.id.icon_folder)

        fun bind(folder: FolderEntity) {
            nameText.text = folder.name
        }
    }

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.text_note_title)
        private val contentText: TextView? = itemView.findViewById(R.id.text_note_content)

        fun bind(note: Note) {
            titleText.text = note.title.ifBlank { itemView.context.getString(R.string.hint_note_title) }
            contentText?.text = note.content.take(120)
        }
    }
}
