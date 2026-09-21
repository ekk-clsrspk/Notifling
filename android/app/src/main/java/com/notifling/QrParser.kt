package com.notifling

/** Parses the QR content shown by the Windows exe: `NOTIFLING:1:<base64url-key>`. */
object QrParser {
    private const val PREFIX = "NOTIFLING:1:"

    fun parse(raw: String?): String? {
        val s = raw?.trim() ?: return null
        if (!s.startsWith(PREFIX)) return null
        val key = s.removePrefix(PREFIX).trim()
        return key.ifEmpty { null }
    }
}
