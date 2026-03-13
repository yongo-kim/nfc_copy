package com.nfccopy.app.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.nfccopy.app.R
import com.nfccopy.app.data.CardStorage
import com.nfccopy.app.databinding.ActivityCardDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CardDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCardDetailBinding
    private lateinit var cardStorage: CardStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        cardStorage = CardStorage(this)
        val cardId = intent.getStringExtra("card_id") ?: run {
            finish()
            return
        }

        val card = cardStorage.getCard(cardId) ?: run {
            finish()
            return
        }

        supportActionBar?.title = card.name

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val sb = StringBuilder()
        sb.appendLine("${getString(R.string.detail_name)}: ${card.name}")
        sb.appendLine("${getString(R.string.detail_uid)}: ${card.uid}")
        sb.appendLine("${getString(R.string.detail_tech)}: ${card.techList.joinToString(", ")}")
        if (card.atqa.isNotEmpty()) sb.appendLine("ATQA: ${card.atqa}")
        if (card.sak.isNotEmpty()) sb.appendLine("SAK: ${card.sak}")
        sb.appendLine("${getString(R.string.detail_time)}: ${dateFormat.format(Date(card.timestamp))}")

        card.ndef?.let { ndef ->
            sb.appendLine()
            sb.appendLine("=== NDEF ===")
            sb.appendLine("Max Size: ${ndef.maxSize} bytes")
            sb.appendLine("Writable: ${ndef.isWritable}")
            ndef.records.forEachIndexed { i, record ->
                sb.appendLine("Record $i:")
                sb.appendLine("  TNF: ${record.tnf}")
                sb.appendLine("  Type: ${record.type}")
                if (record.payloadText.isNotEmpty()) {
                    sb.appendLine("  Content: ${record.payloadText}")
                }
                sb.appendLine("  Raw: ${record.payload}")
            }
        }

        if (card.rawData.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== ${getString(R.string.detail_raw)} ===")
            sb.append(card.rawData)
        }

        binding.detailText.text = sb.toString()

        binding.btnEmulate.setOnClickListener {
            val activeId = cardStorage.getActiveCardId()
            if (activeId == card.id) {
                cardStorage.clearActiveCard()
                binding.btnEmulate.text = getString(R.string.start_emulation)
            } else {
                cardStorage.setActiveCard(card.id)
                binding.btnEmulate.text = getString(R.string.stop_emulation)
            }
        }

        val isActive = cardStorage.getActiveCardId() == card.id
        binding.btnEmulate.text = if (isActive) getString(R.string.stop_emulation) else getString(R.string.start_emulation)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
