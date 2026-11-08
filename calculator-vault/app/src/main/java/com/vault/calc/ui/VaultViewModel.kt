package com.vault.calc.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vault.calc.data.VaultItem
import com.vault.calc.data.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = VaultRepository.get(app)

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _items.value = repo.loadItems() }
    }

    fun import(uris: List<Uri>, onDone: (success: Int, failed: Int) -> Unit) {
        viewModelScope.launch {
            var ok = 0
            var fail = 0
            for (uri in uris) {
                runCatching { repo.importUri(uri) }
                    .onSuccess { ok++ }
                    .onFailure { fail++ }
            }
            _items.value = repo.loadItems()
            onDone(ok, fail)
        }
    }

    fun delete(item: VaultItem, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.delete(item)
            _items.value = repo.loadItems()
            onDone()
        }
    }

    fun export(item: VaultItem, target: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(target)?.use { out ->
                    repo.exportTo(item, out)
                } ?: error("no stream")
            }.isSuccess
            onResult(ok)
        }
    }
}
