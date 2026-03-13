package com.nfccopy.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nfccopy.app.R
import com.nfccopy.app.databinding.ItemCardBinding
import com.nfccopy.app.model.NfcCardData

class CardListAdapter(
    private val onCardClick: (NfcCardData) -> Unit,
    private val onEmulateClick: (NfcCardData) -> Unit,
    private val onDeleteClick: (NfcCardData) -> Unit
) : RecyclerView.Adapter<CardListAdapter.ViewHolder>() {

    private var cards: List<NfcCardData> = emptyList()
    private var activeCardId: String? = null

    fun submitList(newCards: List<NfcCardData>, activeId: String?) {
        cards = newCards
        activeCardId = activeId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(cards[position])
    }

    override fun getItemCount() = cards.size

    inner class ViewHolder(private val binding: ItemCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: NfcCardData) {
            val context = binding.root.context
            binding.cardName.text = card.name.ifBlank { "Card ${card.uid.takeLast(4)}" }
            binding.cardUid.text = "UID: ${card.uid}"
            binding.cardTech.text = card.techList.joinToString(", ")

            val isActive = card.id == activeCardId
            if (isActive) {
                binding.btnEmulate.text = context.getString(R.string.stop_emulation)
                binding.btnEmulate.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.emulation_active)
                )
                binding.activeIndicator.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.emulation_active)
                )
            } else {
                binding.btnEmulate.text = context.getString(R.string.start_emulation)
                binding.btnEmulate.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.primary)
                )
                binding.activeIndicator.setBackgroundColor(
                    ContextCompat.getColor(context, android.R.color.transparent)
                )
            }

            binding.root.setOnClickListener { onCardClick(card) }
            binding.btnEmulate.setOnClickListener { onEmulateClick(card) }
            binding.btnDelete.setOnClickListener { onDeleteClick(card) }
        }
    }
}
