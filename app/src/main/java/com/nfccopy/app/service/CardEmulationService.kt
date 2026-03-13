package com.nfccopy.app.service

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.nfccopy.app.data.CardStorage

class CardEmulationService : HostApduService() {

    private lateinit var cardStorage: CardStorage

    companion object {
        private const val SELECT_APDU_HEADER = "00A40400"
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_UNKNOWN = byteArrayOf(0x6F.toByte(), 0x00)
    }

    override fun onCreate() {
        super.onCreate()
        cardStorage = CardStorage(this)
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val hexCommand = commandApdu.toHexString()

        // Handle SELECT command
        if (hexCommand.uppercase().startsWith(SELECT_APDU_HEADER)) {
            val activeCardId = cardStorage.getActiveCardId() ?: return SW_NOT_FOUND
            val card = cardStorage.getCard(activeCardId) ?: return SW_NOT_FOUND

            // Return UID as response data + SW_OK
            val uidBytes = card.uid.hexStringToByteArray()
            return uidBytes + SW_OK
        }

        // For GET DATA or other commands, return card raw data
        val activeCardId = cardStorage.getActiveCardId()
        if (activeCardId != null) {
            val card = cardStorage.getCard(activeCardId)
            if (card != null) {
                val responseData = card.uid.hexStringToByteArray()
                return responseData + SW_OK
            }
        }

        return SW_UNKNOWN
    }

    override fun onDeactivated(reason: Int) {
        // No-op
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }

    private fun String.hexStringToByteArray(): ByteArray {
        val hex = this.replace(" ", "")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
