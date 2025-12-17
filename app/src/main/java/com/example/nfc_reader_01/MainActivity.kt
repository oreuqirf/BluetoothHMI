package com.example.nfc_reader_01

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var isShutdownArmed = false

    private lateinit var btnShutdown: Button
    private lateinit var tvStatus: TextView

    companion object {
        private const val CMD_SHUTDOWN: Byte = 0x10.toByte()
        private const val MIME_COMMAND_TYPE = "application/x-cmd"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnShutdown = findViewById(R.id.btn_shutdown)
        tvStatus = findViewById(R.id.tv_status)

        setupNfc()

        btnShutdown.setOnClickListener {
            if (nfcAdapter == null || !nfcAdapter!!.isEnabled) {
                Toast.makeText(this, "NFC no está disponible o activado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            isShutdownArmed = true
            updateStatus("LISTO PARA APAGAR.\nAcerca el dispositivo ahora.")
            btnShutdown.isEnabled = false // Disable to prevent double clicks until done or reset
            // Re-enable after some time or on completion?
            // Better: Allow re-arming if needed, but for now let's keep it simple.
            // Actually, let's keep it enabled but update text/logic.
            btnShutdown.isEnabled = true
        }
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            updateStatus("Error: Este dispositivo no soporta NFC.")
            btnShutdown.isEnabled = false
            return
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter != null) {
            val intentFilters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
            )
            val techList = arrayOf(
                arrayOf(Ndef::class.java.name),
                arrayOf(NdefFormatable::class.java.name),
                arrayOf(NfcV::class.java.name)
            )
            try {
                nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (!isShutdownArmed) {
            // If not armed, ignore the tag or maybe just show info?
            // "Active únicamente al presionar el botón" -> Ignore or tell user to press button.
            return
        }

        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            processTag(tag)
        }
    }

    private fun processTag(tag: Tag) {
        updateStatus("Procesando etiqueta...")

        Thread {
            val success = writeShutdownCommand(tag)
            runOnUiThread {
                if (success) {
                    updateStatus("COMANDO ENVIADO.\nEl dispositivo debería apagarse.")
                    Toast.makeText(this, "Apagado enviado correctamente", Toast.LENGTH_LONG).show()
                    isShutdownArmed = false // Reset arming
                } else {
                    // Check if fallback happened inside writeShutdownCommand
                    // The function returns false if total failure, but might have formatted via fallback.
                    // We need to differentiate "Error" from "Formatted, try again".
                    // Let's refine writeShutdownCommand return or status.
                    // For now, if false, we assume error or need retry.
                }
            }
        }.start()
    }

    private fun updateStatus(text: String) {
        runOnUiThread {
            tvStatus.text = text
        }
    }

    /**
     * Tries to write the shutdown command using Ndef, then NdefFormatable, then NfcV fallback.
     * Returns true if command was written.
     * Returns false if failed. If fallback format worked, it updates UI to ask for rescan.
     */
    private fun writeShutdownCommand(tag: Tag): Boolean {
        val payload = byteArrayOf(CMD_SHUTDOWN)
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, payload))

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                if (ndef.isWritable) {
                    ndef.writeNdefMessage(message)
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { ndef.close() } catch (_: Exception) {}
            }
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            try {
                formatable.connect()
                formatable.format(message)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { formatable.close() } catch (_: Exception) {}
            }
        }

        // Fallback: NfcV
        val nfcV = NfcV.get(tag)
        if (nfcV != null) {
            val formatted = executeNfcVForceFormatOnlyFallback(nfcV)
            if (formatted) {
                runOnUiThread {
                    updateStatus("Etiqueta formateada (Fallback).\nPOR FAVOR ESCANEE DE NUEVO para enviar el apagado.")
                    Toast.makeText(this, "Formateado. Escanee de nuevo.", Toast.LENGTH_LONG).show()
                }
                // We return false because the command was NOT sent, only formatted.
                // The user must scan again (where we will hit Ndef path).
                return false
            }
        }

        runOnUiThread {
            updateStatus("Error: No se pudo escribir en la etiqueta.\nIntente de nuevo.")
        }
        return false
    }

    private fun executeNfcVForceFormatOnlyFallback(nfcV: NfcV): Boolean {
        val ccBlock = byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x00, 0x40)
        val flags: Byte = 0x02
        val cmdWrite: Byte = 0x21.toByte()

        return try {
            if (!nfcV.isConnected) nfcV.connect()
            val cmd = byteArrayOf(flags, cmdWrite, 0x00) + ccBlock
            val response = nfcV.transceive(cmd)
            response.isEmpty() || (response.size == 1 && (response[0].toInt() and 0x01) == 0)
        } catch (e: Exception) {
            false
        } finally {
            try { nfcV.close() } catch (_: IOException) {}
        }
    }
}
