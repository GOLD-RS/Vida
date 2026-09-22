package com.goldrs.vida

import android.content.Context
import java.util.Calendar
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
            result += Item(
                o.getLong("id"),
                o.getString("title"),
                o.getString("kind"),
                o.optBoolean("done"),
                o.optDouble("amount", 0.0),
                o.optLong("createdAt", 0L)
            )
        }
        return result.sortedByDescending { it.createdAt }.toMutableList()
    }

    fun add(title: String, kind: String, amount: Double = 0.0) {
        val id = nextId()
        save(items().plus(Item(id, title.trim(), kind, false, amount, System.currentTimeMillis())))
    }

    fun toggle(id: Long) { save(items().map { if (it.id == id) it.copy(done = !it.done) else it }) }

    fun delete(id: Long) { save(items().filterNot { it.id == id }) }

    fun total(kind: String): Double = items().filter { it.kind == kind }.sumOf { it.amount }

    /** Soma apenas os lançamentos criados no mês atual. */
    fun totalThisMonth(kind: String): Double {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        return items().filter { it.kind == kind && it.createdAt >= start }.sumOf { it.amount }
    }

    /** Devolve os itens salvos como JSON, pronto para exportar/compartilhar. */
    fun rawJson(): String = prefs.getString("items", "[]") ?: "[]"

    fun budget(): Double = prefs.getLong("budgetCents", 0L) / 100.0
    fun setBudget(value: Double) { prefs.edit().putLong("budgetCents", Math.round(value * 100.0)).apply() }

    /** Gera um id monotônico que nunca colide com itens existentes. */
    private fun nextId(): Long {
        val now = System.currentTimeMillis()
        val maxExisting = items().maxOfOrNull { it.id } ?: 0L
        return if (now > maxExisting) now else maxExisting + 1
    }

    private fun save(list: List<Item>) {
        val array = JSONArray()
        list.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("kind", item.kind)
                put("done", item.done)
                put("amount", item.amount)
                put("createdAt", item.createdAt)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
