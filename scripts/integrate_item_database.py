from pathlib import Path
import re

p = Path("app/src/main/java/com/crimson/editor/MainActivity.kt")
s = p.read_text()

s = s.replace('import java.io.File\n', 'import java.io.File\nimport java.net.URL\nimport java.net.HttpURLConnection\n')

s = s.replace('    private var allItems = JSONArray()\n', '''    private var allItems = JSONArray()
    private val itemNames = mutableMapOf<String, String>()
    private val itemCategories = mutableMapOf<String, String>()
    private var namesLoaded = false
''')

s = s.replace('''        setContentView(root)
        searchInput.addTextChangedListener''', '''        setContentView(root)
        loadCommunityDatabase()
        searchInput.addTextChangedListener''')

s = s.replace('''            query.isEmpty() || item.optLong("itemNo").toString().contains(query) || item.optLong("itemKey").toString().contains(query)
''', '''            val key = item.opt("itemKey")?.toString() ?: ""
            val mappedName = itemNames[key] ?: ""
            query.isEmpty() || item.optLong("itemNo").toString().contains(query, true) ||
                key.contains(query, true) || mappedName.contains(query, true) ||
                (itemCategories[key] ?: "").contains(query, true)
''')

s = s.replace('''            val count = item.optLong("stackCount", 1L)
            val icon = if (count > 1) "📚" else "⚔️"
            val card = LinearLayout(this).apply {''', '''            val count = item.optLong("stackCount", 1L)
            val key = item.opt("itemKey")?.toString() ?: ""
            val mappedName = itemNames[key]
            val category = itemCategories[key] ?: "Nicht zugeordnet"
            val icon = categoryIcon(category)
            val card = LinearLayout(this).apply {''')

s = s.replace('''            card.addView(TextView(this).apply {
                text = "$icon  Item-Nr. ${item.optLong("itemNo")}     ×$count"
                textSize = 17f; setTextColor(gold)
            })
            card.addView(TextView(this).apply {
                text = "🔑 Schlüssel: ${item.optLong("itemKey")}\\n🎒 Inventar: ${item.optInt("inventoryKey")}   •   Container: ${item.optInt("container")}\\n📍 Slot: ${item.optInt("slotNo")}"
                textSize = 12f; setTextColor(white); setPadding(0, 6, 0, 0)
            })''', '''            card.addView(TextView(this).apply {
                text = "$icon  ${mappedName ?: "Unbekannter Gegenstand"}"
                textSize = 17f; setTextColor(gold)
            })
            card.addView(TextView(this).apply {
                text = "Item-Nr. ${item.optLong("itemNo")}   •   ×$count\\nKategorie: $category   •   Schlüssel: $key\\nInventar: ${item.optInt("inventoryKey")}   •   Container: ${item.optInt("container")}   •   Slot: ${item.optInt("slotNo")}"
                textSize = 12f; setTextColor(white); setPadding(0, 6, 0, 0)
            })''')

# Add mapping helpers before exportCleanedCopy
marker = '    private fun exportCleanedCopy(destination: Uri) {'
helpers = '''    private fun loadCommunityDatabase() {
        Thread {
            try {
                val connection = (URL("https://raw.githubusercontent.com/NattKh/CrimsonDesertCommunityItemMapping/main/item_names.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    requestMethod = "GET"
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val root = org.json.JSONTokener(text).nextValue()
                collectItemNames(root, null)
                namesLoaded = true
                runOnUiThread {
                    if (::status.isInitialized) {
                        status.text = "Community-Datenbank geladen: ${itemNames.size} Namen zugeordnet."
                        renderItems()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (::status.isInitialized) status.text = "Offline: Item-Namen konnten nicht geladen werden. IDs bleiben sichtbar."
                }
            }
        }.start()
    }

    private fun collectItemNames(value: Any?, parentKey: String?) {
        when (value) {
            is JSONObject -> {
                val name = value.optString("name", "").takeIf { it.isNotBlank() }
                val keyValue = value.opt("itemKey")?.toString()?.takeIf { it.isNotBlank() } ?: parentKey
                if (name != null && keyValue != null) {
                    itemNames[keyValue] = name
                    itemCategories[keyValue] = value.optString("category", "Sonstiges").ifBlank { "Sonstiges" }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    collectItemNames(value.opt(key), key)
                }
            }
            is JSONArray -> for (i in 0 until value.length()) collectItemNames(value.opt(i), null)
        }
    }

    private fun categoryIcon(category: String): String {
        val c = category.lowercase()
        return when {
            "weapon" in c || "sword" in c || "weapon" in c -> "⚔️"
            "armor" in c || "equipment" in c -> "🛡️"
            "quest" in c || "document" in c || "letter" in c -> "📜"
            "material" in c || "resource" in c -> "🪵"
            "food" in c || "consumable" in c -> "🍖"
            "potion" in c -> "🧪"
            "tool" in c -> "🔧"
            "accessory" in c -> "💍"
            else -> "📦"
        }
    }

'''
s = s.replace(marker, helpers + marker)

p.write_text(s)

manifest = Path("app/src/main/AndroidManifest.xml")
m = manifest.read_text()
if "android.permission.INTERNET" not in m:
    m = m.replace("<application", '<uses-permission android:name="android.permission.INTERNET" />\n    <application', 1)
manifest.write_text(m)
