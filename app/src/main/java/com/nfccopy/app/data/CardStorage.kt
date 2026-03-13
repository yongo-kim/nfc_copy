package com.nfccopy.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nfccopy.app.model.NfcCardData

class CardStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nfc_cards", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_CARDS = "saved_cards"
        private const val KEY_ACTIVE_CARD = "active_card_id"
    }

    fun saveCard(card: NfcCardData) {
        val cards = getAllCards().toMutableList()
        val existingIndex = cards.indexOfFirst { it.id == card.id }
        if (existingIndex >= 0) {
            cards[existingIndex] = card
        } else {
            cards.add(card)
        }
        val json = gson.toJson(cards)
        prefs.edit().putString(KEY_CARDS, json).apply()
    }

    fun getAllCards(): List<NfcCardData> {
        val json = prefs.getString(KEY_CARDS, null) ?: return emptyList()
        val type = object : TypeToken<List<NfcCardData>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getCard(id: String): NfcCardData? {
        return getAllCards().find { it.id == id }
    }

    fun deleteCard(id: String) {
        val cards = getAllCards().filter { it.id != id }
        val json = gson.toJson(cards)
        prefs.edit().putString(KEY_CARDS, json).apply()
        if (getActiveCardId() == id) {
            clearActiveCard()
        }
    }

    fun setActiveCard(id: String) {
        prefs.edit().putString(KEY_ACTIVE_CARD, id).apply()
    }

    fun getActiveCardId(): String? {
        return prefs.getString(KEY_ACTIVE_CARD, null)
    }

    fun clearActiveCard() {
        prefs.edit().remove(KEY_ACTIVE_CARD).apply()
    }
}
