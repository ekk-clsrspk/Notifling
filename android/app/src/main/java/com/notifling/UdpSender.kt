package com.notifling

import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.UUID

/**
 * UDP discovery + notify sender. See docs/protocol.md.
 * Caches the PC address for 10 min to avoid broadcasting on every notification.
 */
object UdpSender {
    const val PORT = 51234
    private const val TAG = "Notifling"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    @Volatile private var cachedIp: InetAddress? = null
    @Volatile private var cachedAt: Long = 0
    @Volatile var lastPcName: String = ""

    fun keyHash(authKey: String): String {
        val raw: ByteArray = try {
            Base64.decode(authKey.trim(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            authKey.trim().toByteArray()
        }
        val sum = MessageDigest.getInstance("SHA-256").digest(raw)
        return sum.joinToString("") { "%02x".format(it) }
    }

    private fun phoneId(): String = "${Build.MANUFACTURER}-${Build.MODEL}"

    /** Broadcast DISCOVER, wait for OFFER. Returns PC address or null. */
    suspend fun discover(authKey: String, port: Int = PORT): InetAddress? =
        withContext(Dispatchers.IO) {
            val hash = keyHash(authKey)
            val req = JSONObject()
                .put("v", 1).put("type", "DISCOVER")
                .put("key_hash", hash).put("phone_id", phoneId())
                .toString().toByteArray()
            try {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    sock.soTimeout = 1500
                    val dest = InetAddress.getByName("255.255.255.255")
                    repeat(2) {
                        sock.send(DatagramPacket(req, req.size, dest, port))
                        val t0 = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t0 < 1500) {
                            try {
                                val buf = ByteArray(2048)
                                val resp = DatagramPacket(buf, buf.size)
                                sock.receive(resp)
                                val obj = JSONObject(String(resp.data, 0, resp.length))
                                if (obj.optString("type") == "OFFER" &&
                                    obj.optString("key_hash") == hash
                                ) {
                                    lastPcName = obj.optString("pc_name", "")
                                    cachedIp = resp.address
                                    cachedAt = System.currentTimeMillis()
                                    Log.i(TAG, "discovered PC $lastPcName at $cachedIp")
                                    return@withContext resp.address
                                }
                            } catch (_: java.net.SocketTimeoutException) {
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "discover failed: $e")
            }
            null
        }

    /** Send one notification; discovers first if needed. Returns human-readable status. */
    suspend fun sendNotification(
        authKey: String,
        pkg: String,
        title: String,
        text: String,
        port: Int = PORT,
    ): String = withContext(Dispatchers.IO) {
        var target = cachedIp
        if (target == null || System.currentTimeMillis() - cachedAt > CACHE_TTL_MS) {
            target = discover(authKey, port)
                ?: return@withContext "PC not found (discover failed)"
        }
        val pkt = JSONObject()
            .put("v", 1).put("type", "NOTIFY")
            .put("key_hash", keyHash(authKey))
            .put("id", UUID.randomUUID().toString())
            .put("pkg", pkg)
            .put("title", title.take(256))
            .put("text", text.take(512))
            .put("ts", System.currentTimeMillis() / 1000)
            .toString().toByteArray()
        try {
            DatagramSocket().use { sock ->
                sock.send(DatagramPacket(pkt, pkt.size, target, port))
            }
            "sent to ${target.hostAddress} ($lastPcName)"
        } catch (e: Exception) {
            cachedIp = null // invalidate; next send re-discovers
            "send failed: ${e.message}"
        }
    }
}
