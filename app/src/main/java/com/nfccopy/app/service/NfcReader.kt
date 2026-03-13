package com.nfccopy.app.service

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import com.nfccopy.app.model.NdefData
import com.nfccopy.app.model.NdefRecord
import com.nfccopy.app.model.NfcCardData

object NfcReader {

    fun readTag(tag: Tag): NfcCardData {
        val uid = tag.id?.toHexString() ?: ""
        val techList = tag.techList.map { it.substringAfterLast('.') }

        var atqa = ""
        var sak = ""
        var ndefData: NdefData? = null
        val rawDataBuilder = StringBuilder()

        // Read NfcA info
        if (tag.techList.contains("android.nfc.tech.NfcA")) {
            try {
                val nfcA = NfcA.get(tag)
                atqa = nfcA.atqa?.toHexString() ?: ""
                sak = String.format("%02X", nfcA.sak.toInt())
            } catch (_: Exception) {
            }
        }

        // Read NfcB info
        if (tag.techList.contains("android.nfc.tech.NfcB")) {
            try {
                val nfcB = NfcB.get(tag)
                rawDataBuilder.appendLine("NfcB App Data: ${nfcB.applicationData?.toHexString()}")
                rawDataBuilder.appendLine("NfcB Protocol: ${nfcB.protocolInfo?.toHexString()}")
            } catch (_: Exception) {
            }
        }

        // Read NDEF data
        if (tag.techList.contains("android.nfc.tech.Ndef")) {
            ndefData = readNdef(tag)
        }

        // Read MifareClassic
        if (tag.techList.contains("android.nfc.tech.MifareClassic")) {
            val mifareData = readMifareClassic(tag)
            if (mifareData.isNotEmpty()) {
                rawDataBuilder.appendLine("=== MifareClassic ===")
                rawDataBuilder.append(mifareData)
            }
        }

        // Read MifareUltralight
        if (tag.techList.contains("android.nfc.tech.MifareUltralight")) {
            val ultralightData = readMifareUltralight(tag)
            if (ultralightData.isNotEmpty()) {
                rawDataBuilder.appendLine("=== MifareUltralight ===")
                rawDataBuilder.append(ultralightData)
            }
        }

        // Read IsoDep
        if (tag.techList.contains("android.nfc.tech.IsoDep")) {
            val isoData = readIsoDep(tag)
            if (isoData.isNotEmpty()) {
                rawDataBuilder.appendLine("=== IsoDep ===")
                rawDataBuilder.append(isoData)
            }
        }

        return NfcCardData(
            uid = uid,
            techList = techList,
            atqa = atqa,
            sak = sak,
            ndef = ndefData,
            rawData = rawDataBuilder.toString()
        )
    }

    private fun readNdef(tag: Tag): NdefData? {
        return try {
            val ndef = Ndef.get(tag) ?: return null
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            val records = ndefMessage?.records?.map { record ->
                NdefRecord(
                    tnf = record.tnf.toInt(),
                    type = record.type?.toHexString() ?: "",
                    payload = record.payload?.toHexString() ?: "",
                    payloadText = parseNdefPayload(record)
                )
            } ?: emptyList()
            val data = NdefData(
                records = records,
                maxSize = ndef.maxSize,
                isWritable = ndef.isWritable
            )
            ndef.close()
            data
        } catch (_: Exception) {
            null
        }
    }

    private fun readMifareClassic(tag: Tag): String {
        return try {
            val mifare = MifareClassic.get(tag) ?: return ""
            mifare.connect()
            val sb = StringBuilder()
            sb.appendLine("Type: ${getMifareType(mifare.type)}")
            sb.appendLine("Size: ${mifare.size} bytes")
            sb.appendLine("Sectors: ${mifare.sectorCount}")
            sb.appendLine("Blocks: ${mifare.blockCount}")

            for (sector in 0 until mifare.sectorCount) {
                val authenticated = mifare.authenticateSectorWithKeyA(sector, MifareClassic.KEY_DEFAULT)
                        || mifare.authenticateSectorWithKeyB(sector, MifareClassic.KEY_DEFAULT)
                if (authenticated) {
                    val blockStart = mifare.sectorToBlock(sector)
                    val blockCount = mifare.getBlockCountInSector(sector)
                    for (block in blockStart until blockStart + blockCount) {
                        try {
                            val data = mifare.readBlock(block)
                            sb.appendLine("Block $block: ${data.toHexString()}")
                        } catch (_: Exception) {
                            sb.appendLine("Block $block: [read error]")
                        }
                    }
                } else {
                    sb.appendLine("Sector $sector: [auth failed]")
                }
            }
            mifare.close()
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun readMifareUltralight(tag: Tag): String {
        return try {
            val ultralight = MifareUltralight.get(tag) ?: return ""
            ultralight.connect()
            val sb = StringBuilder()
            sb.appendLine("Type: ${getUltralightType(ultralight.type)}")

            val maxPages = when (ultralight.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> 16
                MifareUltralight.TYPE_ULTRALIGHT_C -> 48
                else -> 16
            }

            var page = 0
            while (page < maxPages) {
                try {
                    val data = ultralight.readPages(page)
                    sb.appendLine("Page $page-${page + 3}: ${data.toHexString()}")
                    page += 4
                } catch (_: Exception) {
                    break
                }
            }
            ultralight.close()
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun readIsoDep(tag: Tag): String {
        return try {
            val isoDep = IsoDep.get(tag) ?: return ""
            isoDep.connect()
            val sb = StringBuilder()
            sb.appendLine("Max Transceive Length: ${isoDep.maxTransceiveLength}")
            sb.appendLine("Timeout: ${isoDep.timeout} ms")
            isoDep.historicalBytes?.let {
                sb.appendLine("Historical Bytes: ${it.toHexString()}")
            }
            isoDep.hiLayerResponse?.let {
                sb.appendLine("HiLayer Response: ${it.toHexString()}")
            }
            isoDep.close()
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseNdefPayload(record: android.nfc.NdefRecord): String {
        return try {
            when (record.tnf.toInt()) {
                android.nfc.NdefRecord.TNF_WELL_KNOWN -> {
                    if (record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {
                        val payload = record.payload
                        val langCodeLen = payload[0].toInt() and 0x3F
                        String(payload, langCodeLen + 1, payload.size - langCodeLen - 1, Charsets.UTF_8)
                    } else if (record.type.contentEquals(android.nfc.NdefRecord.RTD_URI)) {
                        record.toUri()?.toString() ?: ""
                    } else {
                        String(record.payload, Charsets.UTF_8)
                    }
                }
                android.nfc.NdefRecord.TNF_ABSOLUTE_URI -> {
                    String(record.payload, Charsets.UTF_8)
                }
                else -> record.payload.toHexString()
            }
        } catch (_: Exception) {
            record.payload?.toHexString() ?: ""
        }
    }

    private fun getMifareType(type: Int): String = when (type) {
        MifareClassic.TYPE_CLASSIC -> "Classic"
        MifareClassic.TYPE_PLUS -> "Plus"
        MifareClassic.TYPE_PRO -> "Pro"
        else -> "Unknown"
    }

    private fun getUltralightType(type: Int): String = when (type) {
        MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
        MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
        else -> "Unknown"
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }
}
