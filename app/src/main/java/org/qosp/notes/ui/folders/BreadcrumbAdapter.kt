package org.qosp.notes.ui.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.R
import org.qosp.notes.data.model.FolderEntity

/**
 * Horizontal breadcrumb adapter.
 * Each item shows a folder name followed by " ›".
 * The last item (current folder) is shown without the separator.
 */
class BreadcrumbAdapter(
    private val onBreadcrumbClick: (FolderEntity) -> Unit,
) : ListAdapter<FolderEntity, BreadcrumbAdapter.BreadcrumbViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FolderEntity>() {
            override fun areItemsTheSame(a: FolderEntity, b: FolderEntity) = a.id == b.id
            override fun areContentsTheSame(a: FolderEntity, b: FolderEntity) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreadcrumbViewHolder =
        BreadcrumbViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_breadcrumb, parent, false)
        )

    override fun onBindViewHolder(holder: BreadcrumbViewHolder, position: Int) {
        val folder = getItem(position)
        val isLast = position == itemCount - 1
        holder.bind(folder, isLast)
        if (!isLast) {
            holder.itemView.setOnClickListener { onBreadcrumbClick(folder) }
        }
    }

    class BreadcrumbViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.text_breadcrumb)
        private val separator: TextView = itemView.findViewById(R.id.text_breadcrumb_separator)

        fun bind(folder: FolderEntity, isLast: Boolean) {
            text.text = folder.name
            text.alpha = if (isLast) 1f else 0.6f
            separator.visibility = if (isLast) View.GONE else View.VISIBLE
        }
    }
}
