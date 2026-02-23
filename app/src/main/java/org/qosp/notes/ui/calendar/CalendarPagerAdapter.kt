package org.qosp.notes.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.data.model.Note
import org.qosp.notes.databinding.ItemCalendarMonthBinding
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarPagerAdapter(
    private val onDayClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<CalendarPagerAdapter.MonthViewHolder>() {

    private val months = mutableListOf<YearMonth>()
    private var notesByDay: Map<LocalDate, List<Note>> = emptyMap()
    private var selectedDay: LocalDate? = null

    init {
        val currentMonth = YearMonth.now()
        for (i in -120..120) {
            months.add(currentMonth.plusMonths(i.toLong()))
        }
    }

    val centerPosition: Int
        get() = months.indexOf(YearMonth.now())
        
    fun getMonthAtPosition(position: Int): YearMonth = months[position]

    fun updateNotesAndSelection(notes: Map<LocalDate, List<Note>>, selected: LocalDate?) {
        notesByDay = notes
        selectedDay = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val binding = ItemCalendarMonthBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MonthViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        holder.bind(months[position])
    }

    override fun getItemCount(): Int = months.size

    inner class MonthViewHolder(private val binding: ItemCalendarMonthBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dayAdapter = CalendarDayAdapter(onDayClick)

        init {
            binding.monthGrid.layoutManager = GridLayoutManager(binding.root.context, 7)
            binding.monthGrid.adapter = dayAdapter
            binding.monthGrid.itemAnimator = null
        }

        fun bind(yearMonth: YearMonth) {
            binding.monthTitle.text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            
            val days = mutableListOf<CalendarDayItem>()
            val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value
            
            for (i in 1 until firstDayOfWeek) {
                days.add(CalendarDayItem.Empty)
            }
            
            for (i in 1..yearMonth.lengthOfMonth()) {
                val date = yearMonth.atDay(i)
                val dayNotes = notesByDay[date] ?: emptyList()
                val isSelected = selectedDay == date
                days.add(CalendarDayItem.Day(date, dayNotes, isSelected))
            }
            
            dayAdapter.submitList(days)
        }
    }
}
