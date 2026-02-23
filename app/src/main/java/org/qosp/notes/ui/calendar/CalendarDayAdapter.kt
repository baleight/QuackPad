package org.qosp.notes.ui.calendar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NotePriority
import org.qosp.notes.databinding.ItemCalendarDayBinding
import java.time.LocalDate

sealed class CalendarDayItem {
    object Empty : CalendarDayItem()
    data class Day(val date: LocalDate, val notes: List<Note>, val isSelected: Boolean) : CalendarDayItem()
}

class CalendarDayAdapter(
    private val onDayClick: (LocalDate) -> Unit
) : ListAdapter<CalendarDayItem, RecyclerView.ViewHolder>(DiffCallback) {

    enum class ViewType {
        EMPTY, DAY
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is CalendarDayItem.Empty -> ViewType.EMPTY.ordinal
            is CalendarDayItem.Day -> ViewType.DAY.ordinal
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return if (viewType == ViewType.DAY.ordinal) {
            DayViewHolder(binding)
        } else {
            binding.root.visibility = View.INVISIBLE
            EmptyViewHolder(binding.root)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is DayViewHolder) {
            holder.bind(getItem(position) as CalendarDayItem.Day)
        }
    }

    class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class DayViewHolder(private val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val item = getItem(bindingAdapterPosition) as? CalendarDayItem.Day
                item?.let { onDayClick(it.date) }
            }
        }

        fun bind(item: CalendarDayItem.Day) {
            binding.root.visibility = View.VISIBLE
            binding.dayText.text = item.date.dayOfMonth.toString()

            val isToday = item.date == LocalDate.now()
            
            val context = binding.root.context
            binding.dayText.background = null
            
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            var textColor = typedValue.data

            if (item.isSelected) {
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL
                
                context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
                drawable.setColor(typedValue.data)
                
                binding.dayText.background = drawable
                binding.dayText.setTextColor(Color.WHITE)
            } else if (isToday) {
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL
                
                context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
                drawable.setStroke(3, typedValue.data)
                
                binding.dayText.background = drawable
                binding.dayText.setTextColor(textColor)
            } else {
                binding.dayText.setBackgroundResource(0)
                binding.dayText.setTextColor(textColor)
            }

            binding.dotsContainer.removeAllViews()
            val maxDots = 3
            val notesToShow = item.notes.take(maxDots)
            
            notesToShow.forEach { note ->
                val dot = View(context)
                val dotParams = LinearLayout.LayoutParams(10, 10)
                dotParams.setMargins(2, 0, 2, 0)
                dot.layoutParams = dotParams

                val colorStr = when(note.priority) {
                    NotePriority.HIGH -> "#F44336"
                    NotePriority.MEDIUM -> "#FFC107"
                    NotePriority.LOW -> "#4CAF50"
                    NotePriority.NONE -> "#9E9E9E"
                }

                val dotDrawable = GradientDrawable()
                dotDrawable.shape = GradientDrawable.OVAL
                dotDrawable.setColor(Color.parseColor(colorStr))
                dot.background = dotDrawable

                binding.dotsContainer.addView(dot)
            }
            
            if (item.notes.size > maxDots) {
                val plusDot = android.widget.TextView(context)
                plusDot.text = "+"
                plusDot.textSize = 8f
                plusDot.setTextColor(textColor)
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(2, -4, 2, 0)
                plusDot.layoutParams = params
                binding.dotsContainer.addView(plusDot)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<CalendarDayItem>() {
        override fun areItemsTheSame(oldItem: CalendarDayItem, newItem: CalendarDayItem): Boolean {
            if (oldItem is CalendarDayItem.Empty && newItem is CalendarDayItem.Empty) return true
            if (oldItem is CalendarDayItem.Day && newItem is CalendarDayItem.Day) return oldItem.date == newItem.date
            return false
        }

        override fun areContentsTheSame(oldItem: CalendarDayItem, newItem: CalendarDayItem): Boolean {
            return oldItem == newItem
        }
    }
}
