package com.goldrs.vida

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Item(val id: Long, val title: String, val kind: String, val done: Boolean = false)

/** Pequeno armazenamento offline. A camada pode ser substituída por Room sem alterar a UI. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("vida_local", Context.MODE_PRIVATE)
    fun items(): MutableList<Item> {
        val result = mutableListOf<Item>(); val raw = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        for (i in 0 until array.length()) { val o = array.getJSONObject(i); result += Item(o.getLong("id"), o.getString("title"), o.getString("kind"), o.optBoolean("done")) }
        return result
    }
    fun add(title: String, kind: String) { val a = JSONArray(); items().plus(Item(System.currentTimeMillis(), title, kind)).forEach { i -> a.put(JSONObject().apply { put("id", i.id); put("title", i.title); put("kind", i.kind); put("done", i.done) }) }; prefs.edit().putString("items", a.toString()).apply() }
    fun toggle(id: Long) { val a = JSONArray(); items().map { if (it.id == id) it.copy(done = !it.done) else it }.forEach { i -> a.put(JSONObject().apply { put("id", i.id); put("title", i.title); put("kind", i.kind); put("done", i.done) }) }; prefs.edit().putString("items", a.toString()).apply() }
}
