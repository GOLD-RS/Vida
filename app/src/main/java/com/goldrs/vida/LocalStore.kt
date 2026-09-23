package com.goldrs.vida

import android.content.Context
import java.text.Normalizer
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

/**
 * Modelo central do Vida. Campos novos são opcionais para manter
 * compatibilidade com dados antigos (camada offline versionada, pronta p/ Room).
 */
data class Item(
    val id: Long,
    val title: String,
    val kind: String,
    val done: Boolean = false,
    val amount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long = 0L,   // prazo (tarefa) ou data (evento); 0 = sem
    val time: Long = 0L,       // horário do evento (ms no dia); 0 = sem
    val priority: Int = 0,     // 0 sem, 1 normal, 2 alta
    val body: String = "",     // descrição / local
    val category: String = ""  // categoria (ex.: alimentação, contas)
)

/** Armazenamento offline versionado. A UI depende desta API, facilitando migrar para Room depois. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("vida_local", Context.MODE_PRIVATE)

    fun items(): MutableList<Item> {
        val result = mutableListOf<Item>()
        val array = try { JSONArray(prefs.getString("items", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            try {
                val o = array.getJSONObject(i)
                result += Item(
                    o.optLong("id", System.currentTimeMillis() + i),
                    o.optString("title", "Sem título"),
                    o.optString("kind", "note"),
                    o.optBoolean("done"),
                    o.optDouble("amount", 0.0),
                    o.optLong("createdAt", System.currentTimeMillis()),
                    o.optLong("dueDate", 0L),
                    o.optLong("time", 0L),
                    o.optInt("priority", 0),
                    o.optString("body"),
                    o.optString("category")
                )
            } catch (_: Exception) { /* ignora somente o registro corrompido */ }
        }
        return result.sortedByDescending { it.createdAt }.toMutableList()
    }

    fun add(
        title: String,
        kind: String,
        amount: Double = 0.0,
        dueDate: Long = 0L,
        time: Long = 0L,
        priority: Int = 0,
        body: String = "",
        category: String = ""
    ) {
        val id = nextId()
        save(items().plus(Item(id, title.trim(), kind, false, amount, System.currentTimeMillis(), dueDate, time, priority, body, category)))
    }

    fun toggle(id: Long) { save(items().map { if (it.id == id) it.copy(done = !it.done) else it }) }

    fun delete(id: Long) { save(items().filterNot { it.id == id }) }

    /** Adia/define o prazo de uma tarefa (ex.: "adiar para amanhã"). */
    fun moveDue(id: Long, newDue: Long) { save(items().map { if (it.id == id) it.copy(dueDate = newDue) else it }) }

    fun byKind(kind: String): List<Item> = items().filter { it.kind == kind }

    fun total(kind: String): Double = items().filter { it.kind == kind }.sumOf { it.amount }

    /** Soma apenas os lançamentos criados no mês atual. */
    fun totalThisMonth(kind: String): Double {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        return items().filter { it.kind == kind && it.createdAt >= start }.sumOf { it.amount }
    }

    /** Gastos de uma categoria no mês atual: soma + quantos lançamentos. */
    fun categorySpendThisMonth(category: String): Pair<Double, Int> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val norm = normalize(category)
        val hits = items().filter {
            it.kind == "expense" && it.createdAt >= start &&
            (normalize(it.category) == norm || it.title.lowercase().contains(norm))
        }
        return hits.sumOf { it.amount } to hits.size
    }

    /** Lançamentos (contas/receitas) com vencimento na janela dada. */
    fun upcomingBills(fromMs: Long, toMs: Long): List<Item> =
        items().filter { (it.kind == "expense" || it.kind == "income") && !it.done && it.dueDate in fromMs..toMs }

    /** Tarefas atrasadas. */
    fun overdueTasks(now: Long): List<Item> =
        items().filter { it.kind == "task" && !it.done && it.dueDate > 0 && it.dueDate < now }

    /** Tarefas com prazo dentro de um dia (dayMs = início do dia em ms). */
    fun tasksDueOn(dayMs: Long): List<Item> =
        items().filter { it.kind == "task" && !it.done && it.dueDate in dayMs..(dayMs + DAY_MS) }

    /** Eventos de um dia, ordenados por horário. */
    fun eventsOn(dayMs: Long): List<Item> =
        items().filter { it.kind == "event" && it.dueDate in dayMs..(dayMs + DAY_MS) }
            .sortedBy { if (it.time > 0) it.time else Long.MAX_VALUE }

    fun rawJson(): String = prefs.getString("items", "[]") ?: "[]"

    fun budget(): Double = prefs.getLong("budgetCents", 0L) / 100.0
    fun setBudget(value: Double) { prefs.edit().putLong("budgetCents", Math.round(value * 100.0)).apply() }

    // ── Configurações da IA e segurança ─────────────────────────────
    /** Chave/base/modelo da IA (chave do próprio usuário; opcional). */
    fun cfg(key: String): String = prefs.getString("ai_$key", "") ?: ""
    fun setCfg(key: String, value: String) { prefs.edit().putString("ai_$key", value).apply() }
    fun hasAiKey(): Boolean = cfg("key").isNotBlank()

    /** Módulos que a IA pode acessar (lista de kind, vazio = todos). */
    fun aiAllowedModules(): List<String> = cfg("allowed").split(",").filter { it.isNotBlank() }

    /** PIN local (hash SHA-256 com salt) para bloquear o app. */
    fun hasPin(): Boolean = prefs.contains("pinHash")

    fun setPin(pin: String) {
        val existing = prefs.getString("pinSalt", "")
        val salt = if (existing != null && existing.isNotBlank()) existing else randomSalt()
        prefs.edit().putString("pinSalt", salt).putString("pinHash", sha256(salt + pin)).commit()
    }

    fun verifyPin(pin: String): Boolean {
        val hash = prefs.getString("pinHash", "") ?: ""
        val salt = prefs.getString("pinSalt", "") ?: ""
        return hash.isNotEmpty() && sha256(salt + pin) == hash
    }

    /** Apaga TUDO (itens, config, histórico da IA). Usado em "resetar dados". */
    fun clearAllData() { prefs.edit().clear().apply() }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom.getInstanceStrong().nextBytes(bytes)
        return bytes.joinToString("") { b -> String.format(java.util.Locale.ROOT, "%02x", b) }
    }

    private fun sha256(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Histórico das ações executadas pela IA (transparência; mantém 50). */
    fun logAiAction(what: String) {
        val key = "ai_log"
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        arr.put(JSONObject().put("at", System.currentTimeMillis()).put("what", what))
        if (arr.length() > 50) arr.remove(0)
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun aiLog(): List<String> {
        val arr = JSONArray(prefs.getString("ai_log", "[]") ?: "[]")
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) out += arr.optJSONObject(i)?.optString("what") ?: ""
        return out
    }

    /** Gera um id monotônico que nunca colide com itens existentes. */
    private fun nextId(): Long {
        val now = System.currentTimeMillis()
        val maxExisting = items().maxOfOrNull { it.id } ?: 0L
        return if (now > maxExisting) now else maxExisting + 1
    }

    private fun normalize(s: String): String = stripAccents(s.lowercase())

    private fun save(list: List<Item>) {
        val array = JSONArray()
        list.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("title", item.title); put("kind", item.kind)
                put("done", item.done); put("amount", item.amount); put("createdAt", item.createdAt)
                put("dueDate", item.dueDate); put("time", item.time); put("priority", item.priority)
                put("body", item.body); put("category", item.category)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    companion object {
        const val DAY_MS = 86_400_000L

        /** Remove acentos (pt-BR) para casamento de palavras. */
        fun stripAccents(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace("\\p{M}".toRegex(), "")
                .replace("ç", "c")
    }
}
