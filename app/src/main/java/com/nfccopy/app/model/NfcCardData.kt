package com.nfccopy.app.model

data class NfcCardData(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "",
    val uid: String = "",
    val techList: List<String> = emptyList(),
    val atqa: String = "",
    val sak: String = "",
    val ndef: NdefData? = null,
    val rawData: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class NdefData(
    val records: List<NdefRecord> = emptyList(),
    val maxSize: Int = 0,
    val isWritable: Boolean = false
)

data class NdefRecord(
    val tnf: Int = 0,
    val type: String = "",
    val payload: String = "",
    val payloadText: String = ""
)
