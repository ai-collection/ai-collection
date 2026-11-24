package com.vault.calc.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.vault.calc.R
import com.vault.calc.data.VaultItem
import com.vault.calc.data.VaultRepository
import com.vault.calc.databinding.FragmentVaultListBinding
import kotlinx.coroutines.launch

class VaultListFragment : Fragment() {

    private var _binding: FragmentVaultListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VaultViewModel by activityViewModels()
    private lateinit var category: VaultItem.Category

    private var fileAdapter: FileAdapter? = null
    private var photoAdapter: PhotoAdapter? = null

    private var pendingExport: VaultItem? = null

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            viewModel.import(uris) { ok, failed ->
                val msg = if (failed == 0) getString(R.string.imported)
                else "${getString(R.string.import_failed)} ($failed)"
                toast(msg)
            }
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            val item = pendingExport
            pendingExport = null
            if (uri != null && item != null) {
                viewModel.export(item, uri) { ok ->
                    toast(getString(if (ok) R.string.exported else R.string.export_failed))
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = VaultItem.Category.valueOf(
            arguments?.getString(ARG_CATEGORY) ?: VaultItem.Category.FILE.name
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVaultListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (category == VaultItem.Category.PHOTO) {
            binding.recycler.layoutManager = GridLayoutManager(requireContext(), 3)
            val repo = VaultRepository.get(requireContext())
            photoAdapter = PhotoAdapter(
                scope = viewLifecycleOwner.lifecycleScope,
                decrypt = { repo.decryptBytes(it) },
                onClick = { onPhotoClick(it) },
                onLongClick = { confirmDelete(it) }
            )
            binding.recycler.adapter = photoAdapter
            binding.empty.text = getString(R.string.empty_photos)
        } else {
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
            fileAdapter = FileAdapter { onFileClick(it) }
            binding.recycler.adapter = fileAdapter
            binding.empty.text = getString(R.string.empty_files)
        }

        binding.fab.setOnClickListener { launchImport() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { all ->
                    val filtered = all.filter { it.category == category }
                    bind(filtered)
                }
            }
        }
        viewModel.refresh()
    }

    private fun bind(items: List<VaultItem>) {
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (category == VaultItem.Category.PHOTO) photoAdapter?.submit(items)
        else fileAdapter?.submit(items)
    }

    private fun launchImport() {
        val mimes = if (category == VaultItem.Category.PHOTO)
            arrayOf("image/*", "video/*") else arrayOf("*/*")
        importLauncher.launch(mimes)
    }

    private fun onPhotoClick(item: VaultItem) {
        if (item.isImage) {
            startActivity(
                Intent(requireContext(), ImageViewerActivity::class.java)
                    .putExtra(ImageViewerActivity.EXTRA_ID, item.id)
            )
        } else {
            showItemActions(item)
        }
    }

    private fun onFileClick(item: VaultItem) = showItemActions(item)

    private fun showItemActions(item: VaultItem) {
        val options = arrayOf(getString(R.string.export), getString(R.string.delete))
        AlertDialog.Builder(requireContext())
            .setTitle(item.displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { pendingExport = item; exportLauncher.launch(item.displayName) }
                    1 -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun confirmDelete(item: VaultItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_message, item.displayName))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.delete(item) { toast(getString(R.string.deleted)) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        fileAdapter = null
        photoAdapter = null
    }

    companion object {
        private const val ARG_CATEGORY = "category"

        fun newInstance(category: VaultItem.Category): VaultListFragment =
            VaultListFragment().apply {
                arguments = Bundle().apply { putString(ARG_CATEGORY, category.name) }
            }
    }
}
