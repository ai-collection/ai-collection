package com.vault.calc.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vault.calc.R
import com.vault.calc.data.VaultRepository
import com.vault.calc.databinding.FragmentNetworkBinding
import com.vault.calc.network.ConnectionStore
import com.vault.calc.network.NetworkConnection
import com.vault.calc.network.RemoteEntry
import com.vault.calc.network.RemoteStorage
import com.vault.calc.network.RemoteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Network storage tab: manage saved SMB / WebDAV connections, browse them, and
 * pull files down into the encrypted vault.
 */
class NetworkFragment : Fragment() {

    private var _binding: FragmentNetworkBinding? = null
    private val binding get() = _binding!!

    private lateinit var store: ConnectionStore
    private lateinit var connAdapter: ConnectionAdapter
    private lateinit var remoteAdapter: RemoteAdapter

    private enum class Mode { CONNECTIONS, BROWSE }
    private var mode = Mode.CONNECTIONS

    private var storage: RemoteStorage? = null
    private var activeConnection: NetworkConnection? = null
    private val pathStack = mutableListOf<String>()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = goUp()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ConnectionStore.get(requireContext())

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        connAdapter = ConnectionAdapter(
            onClick = { openConnection(it) },
            onLongClick = { showConnectionMenu(it) }
        )
        remoteAdapter = RemoteAdapter { onRemoteClick(it) }

        binding.fab.setOnClickListener { showConnectionDialog(null) }
        binding.btnUp.setOnClickListener { goUp() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        showConnections()
    }

    // --- Connection list ---------------------------------------------------

    private fun showConnections() {
        mode = Mode.CONNECTIONS
        backCallback.isEnabled = false
        storage?.close()
        storage = null
        activeConnection = null
        pathStack.clear()

        binding.pathBar.visibility = View.GONE
        binding.fab.show()
        binding.recycler.adapter = connAdapter
        binding.empty.text = getString(R.string.empty_network)
        binding.progress.visibility = View.GONE

        val connections = store.all()
        connAdapter.submit(connections)
        binding.empty.visibility = if (connections.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showConnectionMenu(c: NetworkConnection) {
        val options = arrayOf(getString(R.string.edit_connection), getString(R.string.delete_connection))
        AlertDialog.Builder(requireContext())
            .setTitle(c.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showConnectionDialog(c)
                    1 -> { store.delete(c.id); showConnections() }
                }
            }
            .show()
    }

    // --- Browsing ----------------------------------------------------------

    private fun openConnection(c: NetworkConnection) {
        storage?.close()
        storage = runCatching { ConnectionStore.createClient(c) }.getOrNull()
        if (storage == null) {
            toast(getString(R.string.connect_failed, "client"))
            return
        }
        activeConnection = c
        mode = Mode.BROWSE
        backCallback.isEnabled = true
        pathStack.clear()
        pathStack.add("")
        binding.recycler.adapter = remoteAdapter
        binding.pathBar.visibility = View.VISIBLE
        binding.fab.hide()
        browse("")
    }

    private fun browse(path: String) {
        val s = storage ?: return
        binding.progress.visibility = View.VISIBLE
        binding.empty.visibility = View.GONE
        binding.pathText.text = displayPath(path)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { s.list(path) }
            if (!isAdded || _binding == null) return@launch
            binding.progress.visibility = View.GONE
            result.onSuccess { entries ->
                remoteAdapter.submit(entries)
                binding.empty.text = ""
                binding.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }.onFailure { e ->
                toast(getString(R.string.connect_failed, e.message ?: "error"))
                remoteAdapter.submit(emptyList())
            }
        }
    }

    private fun onRemoteClick(e: RemoteEntry) {
        if (e.isDirectory) {
            pathStack.add(e.path)
            browse(e.path)
        } else {
            confirmDownload(e)
        }
    }

    private fun confirmDownload(e: RemoteEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(e.name)
            .setMessage(R.string.download_prompt)
            .setPositiveButton(R.string.save) { _, _ -> download(e) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun download(e: RemoteEntry) {
        val s = storage ?: return
        binding.progress.visibility = View.VISIBLE
        toast(getString(R.string.downloading))
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    s.openInput(e.path).use { input ->
                        VaultRepository.get(requireContext())
                            .importStream(e.name, guessMime(e.name), input)
                    }
                }
            }.isSuccess
            if (!isAdded || _binding == null) return@launch
            binding.progress.visibility = View.GONE
            toast(getString(if (ok) R.string.download_done else R.string.download_failed))
        }
    }

    private fun goUp() {
        if (mode != Mode.BROWSE) return
        if (pathStack.size > 1) {
            pathStack.removeAt(pathStack.lastIndex)
            browse(pathStack.last())
        } else {
            showConnections()
        }
    }

    private fun displayPath(path: String): String {
        val root = activeConnection?.name ?: "/"
        return if (path.isEmpty()) root else "$root/$path"
    }

    private fun guessMime(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    // --- Add / edit connection --------------------------------------------

    private fun showConnectionDialog(existing: NetworkConnection?) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_connection, null)

        val typeGroup = view.findViewById<RadioGroup>(R.id.typeGroup)
        val editName = view.findViewById<EditText>(R.id.editName)
        val editHost = view.findViewById<EditText>(R.id.editHost)
        val editShare = view.findViewById<EditText>(R.id.editShare)
        val editUser = view.findViewById<EditText>(R.id.editUser)
        val editPassword = view.findViewById<EditText>(R.id.editPassword)
        val editDomain = view.findViewById<EditText>(R.id.editDomain)

        fun applyTypeHints() {
            val isSmb = typeGroup.checkedRadioButtonId == R.id.typeSmb
            editHost.hint = getString(if (isSmb) R.string.conn_host_smb else R.string.conn_host_webdav)
            editShare.visibility = if (isSmb) View.VISIBLE else View.GONE
            editDomain.visibility = if (isSmb) View.VISIBLE else View.GONE
        }
        typeGroup.setOnCheckedChangeListener { _, _ -> applyTypeHints() }

        existing?.let { c ->
            typeGroup.check(if (c.type == RemoteType.SMB) R.id.typeSmb else R.id.typeWebdav)
            editName.setText(c.name)
            editHost.setText(c.host)
            editShare.setText(c.share)
            editUser.setText(c.username)
            editPassword.setText(c.password)
            editDomain.setText(c.domain)
        }
        applyTypeHints()

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) R.string.new_connection else R.string.edit_connection)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val isSmb = typeGroup.checkedRadioButtonId == R.id.typeSmb
                val host = editHost.text.toString().trim()
                if (host.isEmpty()) { toast(getString(R.string.connect_failed, "host")); return@setPositiveButton }
                val name = editName.text.toString().trim().ifEmpty { host }
                val conn = NetworkConnection(
                    id = existing?.id ?: "",
                    name = name,
                    type = if (isSmb) RemoteType.SMB else RemoteType.WEBDAV,
                    host = host,
                    share = if (isSmb) editShare.text.toString().trim() else "",
                    username = editUser.text.toString().trim(),
                    password = editPassword.text.toString(),
                    domain = if (isSmb) editDomain.text.toString().trim() else ""
                )
                store.save(conn)
                showConnections()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        storage?.close()
        storage = null
        _binding = null
    }
}
