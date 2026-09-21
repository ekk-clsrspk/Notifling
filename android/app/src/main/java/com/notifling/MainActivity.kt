package com.notifling

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Simple UI: key input (persistent), QR scan pairing, status, test send. */
class MainActivity : AppCompatActivity() {

    private lateinit var store: KeyStore
    private lateinit var keyInput: EditText
    private lateinit var statusText: TextView
    private val ui = CoroutineScope(Dispatchers.Main)

    private val qrLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val contents = IntentIntegrator.parseActivityResult(res.resultCode, res.data)?.contents
            val key = QrParser.parse(contents)
            if (key != null) {
                store.saveKey(key)
                keyInput.setText(key)
                toast("Key saved from QR")
                refreshStatus()
            } else if (contents != null) {
                toast("Not a Notifling QR code")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = KeyStore(this)

        keyInput = findViewById(R.id.keyInput)
        statusText = findViewById(R.id.statusText)
        keyInput.setText(store.getKey())

        findViewById<Button>(R.id.saveBtn).setOnClickListener {
            store.saveKey(keyInput.text.toString())
            toast("Key saved")
            refreshStatus()
        }
        findViewById<Button>(R.id.scanBtn).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
                return@setOnClickListener
            }
            qrLauncher.launch(IntentIntegrator(this).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("Scan the QR shown by Notifling on your PC")
            }.createScanIntent())
        }
        findViewById<Button>(R.id.enableBtn).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.testBtn).setOnClickListener {
            val key = store.getKey()
            if (key.isEmpty()) {
                toast("Enter or scan the key first")
                return@setOnClickListener
            }
            statusText.text = "sending test…"
            ui.launch {
                val status = withContext(Dispatchers.IO) {
                    UdpSender.sendNotification(key, packageName, "Notifling", "Test from phone ✓")
                }
                store.setLastStatus(status)
                refreshStatus()
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val listener = isListenerEnabled()
        statusText.text = "Listener: ${if (listener) "ON" else "OFF (tap Enable)"}\n" +
            "Key: ${if (store.hasKey()) "set ✓" else "NOT SET"}\n" +
            "Last: ${store.getLastStatus()}"
    }

    private fun isListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == packageName
        } && !TextUtils.isEmpty(flat)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
