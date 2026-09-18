package de.szalkowski.activitylauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.szalkowski.activitylauncher.agent.data.CaregiverMessageEntity
import de.szalkowski.activitylauncher.agent.data.CaregiverRepository
import de.szalkowski.activitylauncher.databinding.ItemChatAssistantBinding
import de.szalkowski.activitylauncher.databinding.ItemChatUserBinding

class ChatMessageAdapter : ListAdapter<CaregiverMessageEntity, RecyclerView.ViewHolder>(DIFF) {
    override fun getItemViewType(position: Int): Int =
        if (getItem(position).role == CaregiverRepository.ROLE_USER) TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(ItemChatUserBinding.inflate(inflater, parent, false))
        } else {
            AssistantHolder(ItemChatAssistantBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is UserHolder -> holder.binding.message.text = item.content
            is AssistantHolder -> holder.binding.message.text = item.content
        }
    }

    class UserHolder(val binding: ItemChatUserBinding) : RecyclerView.ViewHolder(binding.root)
    class AssistantHolder(val binding: ItemChatAssistantBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2
        private val DIFF = object : DiffUtil.ItemCallback<CaregiverMessageEntity>() {
            override fun areItemsTheSame(old: CaregiverMessageEntity, new: CaregiverMessageEntity) = old.id == new.id
            override fun areContentsTheSame(old: CaregiverMessageEntity, new: CaregiverMessageEntity) = old == new
        }
    }
}
