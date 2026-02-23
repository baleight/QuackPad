package org.qosp.notes.ui.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.data.model.FolderEntity
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.PreferenceRepository
import org.koin.android.ext.android.inject
import org.qosp.notes.ui.utils.navigateSafely

/**
 * File-explorer-style folder browser.
 *
 * Each call with a different [folderId] argument pushes a new instance onto the back-stack,
 * giving natural Android back-button behaviour. A breadcrumb bar at the top lets users jump
 * directly to any ancestor.
 */
class FolderFragment : Fragment(R.layout.fragment_folder) {

    private val model: FolderViewModel by viewModel()
    private val preferenceRepository: PreferenceRepository by inject()

    private lateinit var breadcrumbRecycler: RecyclerView
    private lateinit var contentRecycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var fab: FloatingActionButton

    private lateinit var breadcrumbAdapter: BreadcrumbAdapter
    private lateinit var contentAdapter: FolderContentAdapter

    /** -1 = root (as stored in nav graph default value), null = root in ViewModel. */
    private val args: FolderFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        breadcrumbRecycler = view.findViewById(R.id.recycler_breadcrumb)
        contentRecycler = view.findViewById(R.id.recycler_folder_content)
        emptyState = view.findViewById(R.id.layout_empty_folders)
        fab = view.findViewById(R.id.fab_folder)

        // Translate nav arg (-1 = root) into ViewModel navigation
        val initialFolderId = args.folderId.takeIf { it >= 0L }
        if (initialFolderId != null) model.navigateTo(initialFolderId)

        setupBreadcrumb()
        setupContent()
        setupFab()
    }

    // --------------------------------------------------------------------------
    // Breadcrumb
    // --------------------------------------------------------------------------

    private fun setupBreadcrumb() {
        breadcrumbAdapter = BreadcrumbAdapter { folder ->
            // Navigate to the tapped ancestor by popping back to that level, then re-navigating
            findNavController().navigate(
                R.id.fragment_folder,
                androidx.core.os.bundleOf("folderId" to folder.id)
            )
        }
        breadcrumbRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        breadcrumbRecycler.adapter = breadcrumbAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            model.breadcrumbPath.collectLatest { crumbs ->
                breadcrumbAdapter.submitList(crumbs)
                breadcrumbRecycler.isVisible = crumbs.isNotEmpty()
            }
        }
    }

    // --------------------------------------------------------------------------
    // Folder + Note content list
    // --------------------------------------------------------------------------

    private fun setupContent() {
        contentAdapter = FolderContentAdapter(
            onFolderClick = { folder ->
                // Push new FolderFragment for the subfolder
                findNavController().navigateSafely(
                    R.id.fragment_folder,
                    androidx.core.os.bundleOf("folderId" to folder.id)
                )
            },
            onFolderLongClick = { folder -> showFolderOptions(folder) },
            onNoteClick = { note ->
                findNavController().navigateSafely(
                    R.id.action_folder_to_editor,
                    Bundle().apply {
                        putString("transitionName", note.id.toString())
                        putLong("noteId", note.id)
                    }
                )
            }
        )

        contentRecycler.layoutManager = GridLayoutManager(requireContext(), 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    // Folders take full width, notes take half
                    return if (contentAdapter.isFolderAt(position)) 2 else 1
                }
            }
        }
        contentRecycler.adapter = contentAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            model.folderContent.collectLatest { content ->
                contentAdapter.submitContent(content.subfolders, content.notes)
                emptyState.isVisible = !content.isLoading &&
                    content.subfolders.isEmpty() && content.notes.isEmpty()
            }
        }
    }

    // --------------------------------------------------------------------------
    // FAB — New Folder / New Note bottom sheet
    // --------------------------------------------------------------------------

    private fun setupFab() {
        fab.setOnClickListener { showAddBottomSheet() }
    }

    private fun showAddBottomSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_folder_add, null)
        sheetView.findViewById<LinearLayout>(R.id.action_new_folder).setOnClickListener {
            sheet.dismiss()
            showNewFolderDialog()
        }
        sheetView.findViewById<LinearLayout>(R.id.action_new_note).setOnClickListener {
            sheet.dismiss()
            val currentFolderId = model.currentFolderId.value ?: -1L
            findNavController().navigateSafely(
                R.id.action_folder_to_editor,
                Bundle().apply {
                    putString("transitionName", "new_note")
                    putLong("newNoteFolderId", currentFolderId)
                }
            )
        }
        sheet.setContentView(sheetView)
        sheet.show()
    }

    private fun showNewFolderDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.hint_folder_name)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Create Folder")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, which ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val rootPath = resolveRootPath()
                    model.createFolder(name, rootPath)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFolderOptions(folder: FolderEntity) {
        val items = arrayOf(
            getString(R.string.action_rename),
            getString(R.string.action_delete)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(folder.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showRenameFolderDialog(folder)
                    1 -> model.deleteFolder(folder)
                }
            }
            .show()
    }

    private fun showRenameFolderDialog(folder: FolderEntity) {
        val input = EditText(requireContext()).apply { setText(folder.name) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, which ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != folder.name) {
                    model.renameFolder(folder, newName)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resolveRootPath(): String {
        // For subfolders, use the current folder path; fall back to external files dir
        val currentId = model.currentFolderId.value
        val current = model.folderContent.value.subfolders.firstOrNull()
        return current?.absolutePath?.let { java.io.File(it).parent } ?:
            requireContext().getExternalFilesDir(null)?.absolutePath + "/QuackPad/Notes"
    }
}
