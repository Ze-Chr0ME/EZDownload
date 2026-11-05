package com.ezsocial.video

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.*

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var link: EditText
    private lateinit var status: TextView
    private lateinit var history: TextView
    private lateinit var prefs: SharedPreferences
    private val mint = Color.rgb(76, 219, 171)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("downloads", MODE_PRIVATE)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        scroll.addView(body)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        fun label(text: String, size: Float = 16f, color: Int = Color.WHITE): TextView {
            return TextView(this).apply { this.text = text; textSize = size; setTextColor(color); setPadding(0, 12, 0, 16); body.addView(this) }
        }
        fun button(text: String, action: () -> Unit) {
            body.addView(Button(this).apply { this.text = text; setOnClickListener { action() } })
        }
        label("E Z S O C I A L   /   V I D E O", 14f, mint)
        label("Keep the good ones.", 32f)
        label("Share a video to EzSocial, or paste a link below.\nSaved videos appear in your gallery under Movies / EzSocial.")
        link = EditText(this).apply {
            hint = "https://…"; setSingleLine(); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        }
        body.addView(link)
        button("Paste link") {
            val clipboard = getSystemService(ClipboardManager::class.java)
            link.setText(clipboard.primaryClip?.getItemAt(0)?.coerceToText(this) ?: "")
        }
        val quality = Spinner(this)
        quality.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("1080p", "720p", "Best"))
        quality.setSelection(prefs.getInt("quality", 0).coerceIn(0, 2))
        quality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putInt("quality", position).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        body.addView(quality)
        button("Download video ↓") {
            prefs.edit().putInt("quality", quality.selectedItemPosition).apply()
            begin(link.text.toString())
        }
        status = label("", 16f, mint)
        button("Cancel current download") { startService(Intent(this, DownloadService::class.java).setAction("CANCEL")) }
        label("Native downloader · engine updates are included in app updates.", 13f)
        label("RECENT ACTIVITY", 14f, mint)
        history = label("", 14f)
        setContentView(scroll)
        refresh()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun begin(text: String) {
        try {
            val url = Links.extract(text)
            link.setText(url)
            startForegroundService(Intent(this, DownloadService::class.java).putExtra("url", url))
        } catch (e: Exception) { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
    }
    private fun refresh() {
        status.text = prefs.getString("status", "Ready when you are.")
        history.text = prefs.getString("history", "Your completed downloads will appear here.")
    }
    override fun onResume() { super.onResume(); prefs.registerOnSharedPreferenceChangeListener(this); refresh() }
    override fun onPause() { prefs.unregisterOnSharedPreferenceChangeListener(this); super.onPause() }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) { runOnUiThread { refresh() } }
}
