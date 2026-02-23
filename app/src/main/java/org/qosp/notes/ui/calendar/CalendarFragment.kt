package org.qosp.notes.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import org.qosp.notes.ui.utils.navigateSafely
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.databinding.FragmentCalendarBinding
import java.time.LocalDate

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private val viewModel: CalendarViewModel by viewModel()
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var calendarPagerAdapter: CalendarPagerAdapter
    private lateinit var agendaAdapter: AgendaAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        calendarPagerAdapter = CalendarPagerAdapter { date ->
            viewModel.selectDay(date)
            scrollToDate(date)
        }

        binding.calendarPager.adapter = calendarPagerAdapter
        binding.calendarPager.setCurrentItem(calendarPagerAdapter.centerPosition, false)

        binding.calendarPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val yearMonth = calendarPagerAdapter.getMonthAtPosition(position)
                viewModel.setMonth(yearMonth)
                val firstOfMonth = yearMonth.atDay(1)
                scrollToDate(firstOfMonth)
            }
        })

        agendaAdapter = AgendaAdapter(
            onNoteClick = { note ->
                findNavController().navigateSafely(
                    CalendarFragmentDirections.actionCalendarToEditor("").setNoteId(note.id)
                )
            },
            onNotePriorityChange = { note, priority ->
                viewModel.updatePriority(note, priority)
            }
        )

        binding.agendaList.layoutManager = LinearLayoutManager(requireContext())
        binding.agendaList.adapter = agendaAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.notesByDayInMonth.collectLatest { notes ->
                        calendarPagerAdapter.updateNotesAndSelection(notes, viewModel.selectedDay.value)
                    }
                }
                launch {
                    viewModel.selectedDay.collectLatest { day ->
                        calendarPagerAdapter.updateNotesAndSelection(viewModel.notesByDayInMonth.value, day)
                    }
                }
                launch {
                    viewModel.agendaNotes.collectLatest { items ->
                        agendaAdapter.submitList(items)
                    }
                }
            }
        }
    }

    private fun scrollToDate(date: LocalDate) {
        val items = agendaAdapter.currentList
        val index = items.indexOfFirst {
            it is AgendaItem.Header && (it.date.isEqual(date) || it.date.isBefore(date))
        }
        if (index != -1) {
            (binding.agendaList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
