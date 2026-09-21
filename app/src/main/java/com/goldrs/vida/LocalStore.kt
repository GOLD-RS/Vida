package com.goldrs.vida

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Item(
    val id: Long,
    val title: String,
    val kind: String,
    val done: Boolean = false,
    val amount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Armazenamento offline versionado. A UI depende desta API, facilitando migrar para Room depois. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("vida_local", Context.MODE_PRIVATE)

    fun items(): MutableList<Item> {
        val result = mutableListOf<Item>()
        val array = JSONArray(prefs.getString("items", "[]") ?: "[]")
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            result += Item(o.getLong("id"), o.getString("title"), o.getString("kind"), o.optBoolean("done"), o.optDouble("amount", 0.0), o.optLong("createdAt", 0L))
        }
        return result.sortedByDescending { it.createdAt }.toMutableList()
    }

    fun add(title: String, kind: String, amount: Double = 0.0) {
        save(items().plus(Item(System.currentTimeMillis(), title.trim(), kind, false, amount, System.currentTimeMillis())))
    }

    fun toggle(id: Long) { save(items().map { if (it.id == id) it.copy(done = !it.done) else it }) }

    fun total(kind: String): Double = items().filter { it.kind == kind }.sumOf { it.amount }

    private fun save(list: List<Item>) {
        val array = JSONArray()
        list.forEach { item -> array.put(JSONObject().apply { put("id", item.id); put("title", item.title); put("kind", item.kind); put("done", item.done); put("amount", item.amount); put("createdAt", item.createdAt) }) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
