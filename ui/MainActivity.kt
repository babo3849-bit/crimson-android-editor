package com.crimson.editor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var preview: LinearLayout
    private lateinit var itemNoInput: EditText
    private lateinit var exportButton: Button
    private lateinit var searchInput: EditText
    private lateinit var sortSpinner: Spinner
    private lateinit var summary: TextView
    private var inputFile: File? = null
    private var pendingOutputFile: File? = null
    private var allItems = JSONArray()
    private val bg = 0xFF101418.toInt()
    private val panel = 0xFF1B232B.toInt()
    private val gold = 0xFFFFC66D.toInt()
    private val white = 0xFFF1F4F7.toInt()
    private val muted = 0xFFADB8C2.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,12); setBackgroundColor(bg) }
        val title = TextView(this).apply { text = "⚔  CRIMSON DESERT\\nSAVE INVENTORY"; textSize = 23f; setTextColor(gold); gravity = Gravity.CENTER; setPadding(8,12,8,18) }
        val open = Button(this).apply { text = "📂  SAVE-DATEI ÖFFNEN"; setOnClickListener { pickSave() } }
        status = TextView(this).apply { text = "Noch keine Save-Datei geöffnet"; setTextColor(muted); textSize = 13f; setPadding(4,10,4,8) }
        summary = TextView(this).apply { text = "📦  INVENTARÜBERSICHT\\nÖffne zuerst deine Save-Datei."; setTextColor(white); textSize = 15f; setPadding(14,14,14,14); setBackgroundColor(panel) }
        searchInput = EditText(this).apply { hint = "🔎  Nach Item-Nr. oder Schlüssel suchen"; setSingleLine(true); setTextColor(white); setHintTextColor(muted); isEnabled = false }
        sortSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Sortieren: Stückzahl ↓","Sortieren: Stückzahl ↑","Sortieren: Item-Nr.","Sortieren: Inventar / Slot"))
            isEnabled = false
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { renderItems() }
            }
        }
        itemNoInput = EditText(this).apply { hint = "Item-Nr. zum Bereinigen"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(white); setHintTextColor(muted); isEnabled = false }
        exportButton = Button(this).apply { text = "🧹  DUPLIKATE ENTFERNEN & KOPIE SPEICHERN"; isEnabled = false; setOnClickListener { chooseOutput() } }
        preview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(title, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(open, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(status, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(summary, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })
        root.addView(searchInput, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(sortSpinner, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply { text = "INVENTAR-EINTRÄGE  ·  Antippen übernimmt die Item-Nr."; setTextColor(gold); textSize = 12f; setPadding(4,8,4,4) })
        root.addView(ScrollView(this).apply { addView(preview) }, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(itemNoInput, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(exportButton, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderItems() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun pickSave() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQUEST_OPEN)
    }
    private fun chooseOutput() {
        val key = itemNoInput.text.toString().trim().toLongOrNull()
        if (key == null || key <= 0) { status.text = "Bitte zuerst eine gültige Item-Nummer auswählen."; return }
        if (inputFile == null) return
        android.app.AlertDialog.Builder(this).setTitle("Duplikate wirklich entfernen?")
            .setMessage("Zusätzliche Einträge mit Item-Nr. $key werden innerhalb desselben Inventar-Containers entfernt. Ein Exemplar pro Container bleibt. Es wird nur eine neue Datei erstellt – dein Original bleibt unverändert. Prüfe vorher, ob es wirklich dein doppeltes Schwert ist.")
            .setNegativeButton("Abbrechen", null).setPositiveButton("Kopie erstellen") { _, _ -> createOutputDocument(key) }.show()
    }
    private fun createOutputDocument(key: Long) {
        pendingOutputFile = File(cacheDir, "crimson_cleaned_${System.currentTimeMillis()}.save")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"; putExtra(Intent.EXTRA_TITLE, "CrimsonDesert_cleaned.save") }
        getPreferences(MODE_PRIVATE).edit().putLong("pending_item_no", key).apply()
        startActivityForResult(intent, REQUEST_CREATE)
    }
    @Deprecated("Uses Android document picker for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) { REQUEST_OPEN -> loadSave(uri); REQUEST_CREATE -> exportCleanedCopy(uri) }
    }
    private fun loadSave(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Datei konnte nicht gelesen werden")
            val file = File(cacheDir, "selected_crimson_save.save"); file.writeBytes(bytes); inputFile = file
            if (!NativeBridge.initialize()) error("Parser-Bibliothek fehlt")
            val response = JSONObject(NativeBridge.inventoryJson(file.absolutePath))
            if (!response.optBoolean("ok")) error(response.optString("error", "Parserfehler"))
            allItems = response.optJSONArray("items") ?: JSONArray()
            val totalStacks = (0 until allItems.length()).sumOf { allItems.getJSONObject(it).optLong("stackCount",1L).coerceAtLeast(1L) }
            val uniqueItems = (0 until allItems.length()).map { allItems.getJSONObject(it).optLong("itemNo") }.distinct().size
            summary.text = "📦  INVENTARÜBERSICHT\\n${response.optInt("count")} Einträge   •   $uniqueItems verschiedene Item-Nr.\\n🔢  Gesamt-Stückzahl (Stackwerte): $totalStacks"
            searchInput.isEnabled = true; sortSpinner.isEnabled = true; itemNoInput.isEnabled = true; exportButton.isEnabled = true
            status.text = "Save erfolgreich gelesen. Du kannst suchen und sortieren."
            renderItems()
        } catch (e: Exception) { status.text = "Fehler: ${e.message}"; itemNoInput.isEnabled = false; exportButton.isEnabled = false }
    }
    private fun renderItems() {
        if (!::preview.isInitialized) return
        preview.removeAllViews()
        val query = if (::searchInput.isInitialized) searchInput.text.toString().trim() else ""
        val sort = if (::sortSpinner.isInitialized) sortSpinner.selectedItemPosition else 0
        val rows = (0 until allItems.length()).map { allItems.getJSONObject(it) }.filter {
            query.isEmpty() || it.optLong("itemNo").toString().contains(query) || it.optLong("itemKey").toString().contains(query)
        }.sortedWith(when (sort) {
            1 -> compareBy<JSONObject> { it.optLong("stackCount",1L) }
            2 -> compareBy<JSONObject> { it.optLong("itemNo") }
            3 -> compareBy<JSONObject> { it.optInt("inventoryKey") }.thenBy { it.optInt("container") }.thenBy { it.optInt("slotNo") }
            else -> compareByDescending<JSONObject> { it.optLong("stackCount",1L) }
        })
        if (rows.isEmpty()) { preview.addView(TextView(this@MainActivity).apply { text = "Keine passenden Einträge gefunden."; setTextColor(muted); setPadding(12,20,12,20) }); return }
        rows.forEach { item ->
            val count = item.optLong("stackCount",1L)
            val card = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(14,12,14,12); setBackgroundColor(panel); isClickable = true; isFocusable = true
                setOnClickListener { itemNoInput.setText(item.optLong("itemNo").toString()); status.text = "✓ Item-Nr. ${item.optLong("itemNo")} ausgewählt. Vor dem Export bitte prüfen." }
            }
            card.addView(TextView(this@MainActivity).apply { text = "${if (count > 1) "📚" else "⚔️"}  Item-Nr. ${item.optLong("itemNo")}     ×$count"; textSize = 17f; setTextColor(gold) })
            card.addView(TextView(this@MainActivity).apply { text = "🔑 Schlüssel: ${item.optLong("itemKey")}\\n🎒 Inventar: ${item.optInt("inventoryKey")}   •   Container: ${item.optInt("container")}\\n📍 Slot: ${item.optInt("slotNo")}"; textSize = 12f; setTextColor(white); setPadding(0,6,0,0) })
            preview.addView(card, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 7 })
        }
        status.text = "${rows.size} von ${allItems.length()} Einträgen angezeigt."
    }
    private fun exportCleanedCopy(destination: Uri) {
        val source = inputFile ?: return; val target = pendingOutputFile ?: return
        val key = getPreferences(MODE_PRIVATE).getLong("pending_item_no",-1L)
        try {
            status.text = "Duplikate werden entfernt …"
            val response = JSONObject(NativeBridge.removeDuplicates(source.absolutePath,target.absolutePath,key))
            if (!response.optBoolean("ok")) error(response.optString("error","Bearbeitung fehlgeschlagen"))
            contentResolver.openOutputStream(destination,"w")?.use { out -> target.inputStream().use { it.copyTo(out) } } ?: error("Zieldatei konnte nicht geschrieben werden")
            summary.text = "✅  KOPIE ERFOLGREICH ERSTELLT\\nEntfernt: ${response.optInt("removed")} Duplikate\\nBehalten: ${response.optInt("keptEntries")} Einträge\\nNachprüfung: ${response.optInt("verifiedInventoryCount")} Inventar-Einträge"
            status.text = "Fertig. Das Original wurde nicht verändert."
            preview.removeAllViews()
            preview.addView(TextView(this@MainActivity).apply { text = "Die bereinigte Datei wurde exportiert und erneut eingelesen.\\nItem-Nr.: ${response.optLong("itemNo")}\\nContainer mit Duplikaten: ${response.optInt("containersWithDuplicates")}"; setTextColor(white); setPadding(12,16,12,16) })
        } catch (e: Exception) { status.text = "Export abgebrochen: ${e.message}" }
    }
    companion object { private const val REQUEST_OPEN = 410; private const val REQUEST_CREATE = 411 }
}