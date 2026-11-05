package com.ezsocial.video

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().ifBlank {
                listOfNotNull(intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
                    intent.clipData?.let { clip -> (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(this).toString() } })
                    .joinToString("\n")
            }
            val url = Links.extract(text)
            startForegroundService(Intent(this, DownloadService::class.java).putExtra("url", url))
            val needsNotifications = Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            Toast.makeText(this, if (needsNotifications) "Downloading to gallery… Open EzSocial Video once to allow progress notifications."
                else "Downloading to gallery…", if (needsNotifications) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }
}
