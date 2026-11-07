package com.minimal.tvlauncher

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minimal.tvlauncher.databinding.ItemAppBinding

/**
 * Shared by the Favourites row and the All apps grid.
 * OK = open, long-press OK (or Menu / Info key) = app options.
 */
class AppAdapter(
    private val onOpen: (AppInfo) -> Unit,
    private val onMenu: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppAdapter.Holder>(Diff) {

    class Holder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = getItem(position)
        with(holder.binding) {
            appIcon.setImageDrawable(app.icon)
            appLabel.text = app.label

            root.setOnClickListener { onOpen(app) }
            root.setOnLongClickListener {
                onMenu(app)
                true
            }
            root.setOnKeyListener { _, keyCode, event ->
                val isMenuKey = keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO
                if (isMenuKey && event.action == KeyEvent.ACTION_UP) {
                    onMenu(app)
                    true
                } else {
                    false
                }
            }
            root.setOnFocusChangeListener { view, hasFocus ->
                val scale = if (hasFocus) 1.08f else 1f
                view.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
            }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.itemView.animate().cancel()
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
        super.onViewRecycled(holder)
    }

    private object Diff : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
            oldItem.packageName == newItem.packageName

        // Each reload creates fresh AppInfo objects (new icons), so always rebind.
        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
            oldItem === newItem
    }
}
