package com.vault.calc.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vault.calc.data.VaultItem
import com.vault.calc.databinding.ItemPhotoBinding
import com.vault.calc.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoAdapter(
    private val scope: CoroutineScope,
    private val decrypt: suspend (VaultItem) -> ByteArray,
    private val onClick: (VaultItem) -> Unit,
    private val onLongClick: (VaultItem) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.VH>() {

    private val items = mutableListOf<VaultItem>()
    private val cache = HashMap<String, Bitmap>()

    fun submit(newItems: List<VaultItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: Job? = null
        var boundId: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.boundId = item.id
        holder.binding.playBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE
        holder.binding.thumb.setImageDrawable(null)
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.root.setOnLongClickListener { onLongClick(item); true }

        cache[item.id]?.let {
            holder.binding.thumb.setImageBitmap(it)
            return
        }

        // Videos: no thumbnail decode here; show the play badge over a dark cell.
        if (item.isVideo) return

        holder.job?.cancel()
        holder.job = scope.launch {
            val bmp = runCatching {
                val bytes = decrypt(item)
                withContext(Dispatchers.Default) { BitmapUtils.decodeSampled(bytes, 300, 300) }
            }.getOrNull()
            if (bmp != null && holder.boundId == item.id) {
                cache[item.id] = bmp
                holder.binding.thumb.setImageBitmap(bmp)
            }
        }
    }

    override fun onViewRecycled(holder: VH) {
        holder.job?.cancel()
        holder.job = null
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size
}
