package com.vault.calc.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vault.calc.R
import com.vault.calc.databinding.ItemRemoteBinding
import com.vault.calc.network.RemoteEntry
import com.vault.calc.util.BitmapUtils

class RemoteAdapter(
    private val onClick: (RemoteEntry) -> Unit
) : RecyclerView.Adapter<RemoteAdapter.VH>() {

    private val items = mutableListOf<RemoteEntry>()

    fun submit(list: List<RemoteEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemRemoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemRemoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.binding.name.text = e.name
        holder.binding.icon.setImageResource(
            if (e.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
        )
        holder.binding.meta.text = if (e.isDirectory) "" else BitmapUtils.humanSize(e.size)
        holder.binding.root.setOnClickListener { onClick(e) }
    }

    override fun getItemCount(): Int = items.size
}
