package com.nfccopy.app.ui

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.nfccopy.app.R
import com.nfccopy.app.data.CardStorage
import com.nfccopy.app.databinding.ActivityMainBinding
import com.nfccopy.app.model.NfcCardData
import com.nfccopy.app.service.NfcReader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cardStorage: CardStorage
    private lateinit var adapter: CardListAdapter
    private var nfcAdapter: NfcAdapter? = null
    private var isReadMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cardStorage = CardStorage(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupRecyclerView()
        setupButtons()
        checkNfcAvailability()
        handleIntent(intent)
    }

    private fun setupRecyclerView() {
        adapter = CardListAdapter(
            onCardClick = { card -> openCardDetail(card) },
            onEmulateClick = { card -> startEmulation(card) },
            onDeleteClick = { card -> confirmDelete(card) }
        )
        binding.recyclerCards.layoutManager = LinearLayoutManager(this)
        binding.recyclerCards.adapter = adapter
    }

    private fun setupButtons() {
        binding.fabRead.setOnClickListener {
            if (nfcAdapter == null || !nfcAdapter!!.isEnabled) {
                Toast.makeText(this, getString(R.string.nfc_not_available), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            isReadMode = true
            binding.readOverlay.visibility = View.VISIBLE
            Snackbar.make(binding.root, getString(R.string.hold_card), Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(R.string.cancel)) {
                    isReadMode = false
                    binding.readOverlay.visibility = View.GONE
                }
                .show()
        }

        binding.readOverlay.setOnClickListener {
            isReadMode = false
            binding.readOverlay.visibility = View.GONE
        }
    }

    private fun checkNfcAvailability() {
        when {
            nfcAdapter == null -> {
                binding.statusText.text = getString(R.string.nfc_not_supported)
                binding.statusIndicator.setBackgroundResource(R.drawable.status_error)
            }
            !nfcAdapter!!.isEnabled -> {
                binding.statusText.text = getString(R.string.nfc_disabled)
                binding.statusIndicator.setBackgroundResource(R.drawable.status_warning)
            }
            else -> {
                binding.statusText.text = getString(R.string.nfc_ready)
                binding.statusIndicator.setBackgroundResource(R.drawable.status_ok)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
        refreshCardList()
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun enableForegroundDispatch() {
        val nfc = nfcAdapter ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        )
        val techLists = arrayOf(
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name),
            arrayOf(IsoDep::class.java.name),
            arrayOf(MifareClassic::class.java.name),
            arrayOf(MifareUltralight::class.java.name),
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name)
        )
        nfc.enableForegroundDispatch(this, pendingIntent, filters, techLists)
    }

    private fun disableForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) return

        val tag: Tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return

        val cardData = NfcReader.readTag(tag)
        onCardRead(cardData)
    }

    private fun onCardRead(card: NfcCardData) {
        isReadMode = false
        binding.readOverlay.visibility = View.GONE

        // Show name dialog
        val editText = android.widget.EditText(this).apply {
            hint = getString(R.string.card_name_hint)
            setText(getString(R.string.default_card_name, card.uid.takeLast(4)))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_card_title))
            .setMessage(getString(R.string.card_read_success, card.uid, card.techList.joinToString(", ")))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val namedCard = card.copy(name = editText.text.toString().ifBlank {
                    getString(R.string.default_card_name, card.uid.takeLast(4))
                })
                cardStorage.saveCard(namedCard)
                refreshCardList()
                Toast.makeText(this, getString(R.string.card_saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun startEmulation(card: NfcCardData) {
        val activeId = cardStorage.getActiveCardId()
        if (activeId == card.id) {
            cardStorage.clearActiveCard()
            Toast.makeText(this, getString(R.string.emulation_stopped), Toast.LENGTH_SHORT).show()
        } else {
            cardStorage.setActiveCard(card.id)
            Toast.makeText(this, getString(R.string.emulation_started, card.name), Toast.LENGTH_SHORT).show()
        }
        refreshCardList()
    }

    private fun confirmDelete(card: NfcCardData) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title))
            .setMessage(getString(R.string.delete_confirm, card.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                cardStorage.deleteCard(card.id)
                refreshCardList()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openCardDetail(card: NfcCardData) {
        val intent = Intent(this, CardDetailActivity::class.java)
        intent.putExtra("card_id", card.id)
        startActivity(intent)
    }

    private fun refreshCardList() {
        val cards = cardStorage.getAllCards()
        val activeId = cardStorage.getActiveCardId()
        adapter.submitList(cards, activeId)
        binding.emptyView.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerCards.visibility = if (cards.isEmpty()) View.GONE else View.VISIBLE
    }
}
