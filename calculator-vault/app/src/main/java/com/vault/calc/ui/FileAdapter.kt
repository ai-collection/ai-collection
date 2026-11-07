package com.vault.calc.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vault.calc.data.VaultItem
import com.vault.calc.databinding.ItemFileBinding
import com.vault.calc.util.BitmapUtils

class FileAdapter(
    private val onClick: (VaultItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private val items = mutableListOf<VaultItem>()

    fun submit(newItems: List<VaultItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.name.text = item.displayName
        val date = DateUtils.getRelativeTimeSpanString(item.addedAt)
        holder.binding.meta.text = "${BitmapUtils.humanSize(item.size)} · $date"
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
