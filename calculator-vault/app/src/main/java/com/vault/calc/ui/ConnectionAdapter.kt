package com.vault.calc.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vault.calc.databinding.ItemConnectionBinding
import com.vault.calc.network.NetworkConnection

class ConnectionAdapter(
    private val onClick: (NetworkConnection) -> Unit,
    private val onLongClick: (NetworkConnection) -> Unit
) : RecyclerView.Adapter<ConnectionAdapter.VH>() {

    private val items = mutableListOf<NetworkConnection>()

    fun submit(list: List<NetworkConnection>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemConnectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.binding.name.text = c.name
        holder.binding.subtitle.text = when (c.type.name) {
            "SMB" -> "SMB · ${c.host}/${c.share}"
            else -> "WebDAV · ${c.host}"
        }
        holder.binding.root.setOnClickListener { onClick(c) }
        holder.binding.root.setOnLongClickListener { onLongClick(c); true }
    }

    override fun getItemCount(): Int = items.size
}
