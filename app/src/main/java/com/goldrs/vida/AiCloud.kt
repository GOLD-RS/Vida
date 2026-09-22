package com.goldrs.vida

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat

/**
 * Camada opcional de LLM. Usa a chave do proprio usuario (qualquer API
 * compativel com OpenAI: OpenAI, Groq, OpenRouter, Ollama local...).
 * Sem chave/sem internet, o app usa o AiEngine offline e segue 100% funcional.
 */
class AiCloud(private val store: LocalStore) {

    data class ChatMsg(val role: String, val text: String)

    /** Chamada com fallback gracioso: null = usar o motor offline. */
    fun chat(messages: List<ChatMsg>, onError: (String) -> Unit): AiResult? {
        val key = store.cfg("key")
        if (key.isBlank()) { onError("Sem chave configurada — usando atalhos offline. Adicione a sua em Configurações → Assistente de IA."); return null }
        val base = store.cfg("base").ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val model = store.cfg("model").ifBlank { "gpt-4o-mini" }
        return try {
            val result = query(base, key, model, messages)
            result
        } catch (e: Exception) {
            onError("Não consegui falar com a IA (${e.message}). Sugerindo alternativa offline:")
            null
        }
    }

    private fun query(base: String, key: String, model: String, messages: List<ChatMsg>): AiResult {
        val body = buildBody(model, messages)
        val url = URL("$base/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val respCode = conn.responseCode
        if (respCode != 200) {
            val err = conn.errorStream?.bufferedReader()?.readText()?.take(400) ?: ""
            throw RuntimeException("HTTP $respCode — $err")
        }
        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val json = JSONObject(resp)
        val msg = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val reply = msg.optString("content", "").ifBlank { "Pronto." }
        val action = parseToolCall(msg, reply)
        return AiResult(reply, action)
    }

    /** Converte tool_calls da resposta em acao controlada com parametros validados. */
    private fun parseToolCall(msg: JSONObject, defaultReply: String): AiAction? {
        val arr = msg.optJSONArray("tool_calls") ?: return null
        if (arr.length() == 0) return null
        val c = arr.getJSONObject(0)
        val fn = c.optJSONObject("function") ?: c // formatos completo e simplificado
        val name = fn.optString("name", "")
        val argsRaw = fn.optString("arguments", "{}")
        val args = try { JSONObject(argsRaw) } catch (e: Exception) { JSONObject() }
        return buildAction(name, args, defaultReply)
    }

    /**
     * Constrói a ação confirmável. Cada operação valida dados e registra em
     * historico (logAiAction). Acoes sensiveis pedem confirmacao obrigatoria.
     */
    private fun buildAction(name: String, a: JSONObject, ctx: String): AiAction? {
        when (name) {
            "create_event" -> {
                val title = a.optString("title", "").trim()
                if (title.isBlank()) return null
                val whenMs = vidaParseWhen(a.optString("when"))
                val time = timeFromStr(a.optString("time"))
                val s = "Criar evento \"$title\"${if (whenMs > 0) " em ${vidaDateLabel(whenMs)}" else ""}${if (time > 0) " às ${vidaTimeLabel(time)}" else ""}"
                return AiAction(s, true) {
                    store.add(title, "event", dueDate = if (whenMs > 0) whenMs else vidaTodayStart(), time = time)
                    store.logAiAction("evento: $title")
                }
            }
            "create_task" -> {
                val title = a.optString("title", "").trim()
                if (title.isBlank()) return null
                val whenMs = vidaParseWhen(a.optString("when"))
                val prio = a.optInt("priority", 0).coerceIn(0, 2)
                val s = "Criar tarefa \"$title\"${if (whenMs > 0) " (prazo ${vidaDateLabel(whenMs)})" else ""}"
                return AiAction(s, true) {
                    store.add(title, "task", dueDate = whenMs, priority = prio)
                    store.logAiAction("tarefa: $title")
                }
            }
            "create_note" -> {
                val title = a.optString("title", "").trim()
                if (title.isBlank()) return null
                return AiAction("Criar nota \"$title\"", false) {
                    store.add(title, "note", body = a.optString("body", ""))
                    store.logAiAction("nota: $title")
                }
            }
            "add_shopping" -> {
                val title = a.optString("title", "").trim()
                if (title.isBlank()) return null
                return AiAction("Adicionar \"$title\" às compras", false) {
                    store.add(title, "shopping"); store.logAiAction("compra: $title")
                }
            }
            "create_goal" -> {
                val title = a.optString("title", "").trim()
                if (title.isBlank()) return null
                return AiAction("Criar meta \"$title\"", false) {
                    store.add(title, "goal"); store.logAiAction("meta: $title")
                }
            }
            "add_expense" -> {
                val amount = a.optDouble("amount", 0.0)
                val title = a.optString("title", "").trim().ifBlank { "Despesa" }
                if (amount <= 0.0) return null
                val s = "Registrar despesa \"$title\" de ${vidaMoney(amount)}"
                return AiAction(s, true) {
                    store.add(title, "expense", amount, category = a.optString("category", ""))
                    store.logAiAction("despesa: $title ${vidaMoney(amount)}")
                }
            }
            "add_income" -> {
                val amount = a.optDouble("amount", 0.0)
                val title = a.optString("title", "").trim().ifBlank { "Receita" }
                if (amount <= 0.0) return null
                return AiAction("Registrar receita \"$title\" de ${vidaMoney(amount)}", true) {
                    store.add(title, "income", amount, category = a.optString("category", ""))
                    store.logAiAction("receita: $title ${vidaMoney(amount)}")
                }
            }
            "complete_task" -> {
                val id = a.optLong("task_id", 0L)
                val t = store.items().find { it.id == id } ?: return null
                return AiAction("Concluir tarefa \"${t.title}\"", false) {
                    store.toggle(id); store.logAiAction("concluir: ${t.title}")
                }
            }
            "delete_item" -> {
                val id = a.optLong("item_id", 0L)
                val t = store.items().find { it.id == id } ?: return null
                return AiAction("Excluir \"${t.title}\" (a ação não pode ser desfeita)", true) {
                    store.delete(id); store.logAiAction("excluir: ${t.title}")
                }
            }
            "move_task_due" -> {
                val id = a.optLong("task_id", 0L)
                val whenMs = vidaParseWhen(a.optString("when"))
                if (whenMs <= 0) return null
                val t = store.items().find { it.id == id } ?: return null
                return AiAction("Mover prazo de \"${t.title}\" para ${vidaDateLabel(whenMs)}", false) {
                    store.moveDue(id, whenMs); store.logAiAction("adiar: ${t.title}")
                }
            }
            else -> null
        }
    }

    private fun buildBody(model: String, messages: List<ChatMsg>): String {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", systemPrompt()))
        messages.takeLast(12).forEach { m ->
            val o = JSONObject()
            o.put("role", m.role)
            o.put("content", m.text)
            arr.put(o)
        }
        val root = JSONObject()
        root.put("model", model)
        root.put("messages", arr)
        root.put("tools", toolSchemas())
        root.put("tool_choice", "auto")
        root.put("max_tokens", 400)
        return root.toString()
    }

    private fun toolSchemas(): JSONArray {
        fun p(name: String, type: String, required: List<String>): JSONObject {
            val props = JSONObject()
            props.put(name, JSONObject().put("type", type))
            val req = JSONArray()
            required.forEach { req.put(it) }
            return JSONObject().put("type", "object").put("properties", props).put("required", req)
        }
        fun pOpt(name: String, type: String, required: List<String>): JSONObject {
            val props = JSONObject()
            props.put(name, JSONObject().put("type", type))
            val req = JSONArray()
            required.forEach { req.put(it) }
            return JSONObject().put("type", "object").put("properties", props).put("required", req)
        }
        fun tool(name: String, desc: String, schema: JSONObject): JSONObject =
            JSONObject().put("type", "function")
                .put("function", JSONObject().put("name", name).put("description", desc).put("parameters", schema))

        val arr = JSONArray()
        arr.put(tool("create_event", "Criar compromisso/reuniao",
            JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("title", JSONObject().put("type", "string"))
                    .put("when", JSONObject().put("type", "string").put("description", "Relativa: 'amanha', 'sexta', 'proxima segunda', '15/09'"))
                    .put("time", JSONObject().put("type", "string").put("description", "Ex.: '14h', '14:30'")))
                .put("required", JSONArray().put("title"))))
        arr.put(tool("create_task", "Criar tarefa",
            JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("title", JSONObject().put("type", "string"))
                    .put("when", JSONObject().put("type", "string").put("description", "Prazo relativo, opcional")))
                .put("required", JSONArray().put("title"))))
        arr.put(tool("create_note", "Criar nota", p("title", "string", listOf("title"))))
        arr.put(tool("add_shopping", "Adicionar item a lista de compras", p("title", "string", listOf("title"))))
        arr.put(tool("create_goal", "Criar meta", p("title", "string", listOf("title"))))
        arr.put(tool("add_expense", "Registrar despesa",
            JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("amount", JSONObject().put("type", "number"))
                    .put("title", JSONObject().put("type", "string"))
                    .put("category", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("amount"))))
        arr.put(tool("add_income", "Registrar receita",
            JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("amount", JSONObject().put("type", "number"))
                    .put("title", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("amount"))))
        arr.put(tool("complete_task", "Marcar tarefa como concluida (use o id da lista)", p("task_id", "integer", listOf("task_id"))))
        arr.put(tool("delete_item", "Excluir item - somente se o usuario pedir explicitamente", p("item_id", "integer", listOf("item_id"))))
        arr.put(tool("move_task_due", "Mover prazo de tarefa para outra data",
            JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("task_id", JSONObject().put("type", "integer"))
                    .put("when", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("task_id"))))
        return arr
    }

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE dd MMMM yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date())
        val pending = store.byKind("task").count { !it.done }
        val bills = store.upcomingBills(vidaTodayStart(), vidaTodayStart() + 7L * LocalStore.DAY_MS)
            .joinToString("; ") { "${it.title} ${vidaMoney(it.amount)} (${vidaDateLabel(it.dueDate)})" }
        val sb = StringBuilder()
            .append("Você é o assistente do Vida, um app de organizacao pessoal 100% offline do usuario. ")
            .append("Fale sempre em portugues do Brasil, de forma curta e pratica (max ~80 palavras por resposta). ")
            .append("Hoje: $now. ")
            .append("Tarefas pendentes: $pending. ")
            .append(if (bills.isNotBlank()) "Contas proximas: $bills. " else "")
            .append("Regras firmes: NUNCA invente dados que nao estao acima; NUNCA diga que executou algo que o usuario nao confirmou; ")
            .append("para acoes importantes, use as ferramentas (create_event, create_task, add_expense...) e acoes sensiveis sempre com confirmacao do usuario. ")
            .append("Se faltar informacao (valor, data, detalhe), faca UMA pergunta curta. ")
            .append("Se o usuario perguntar sobre gastos/metas/rotina, use os numeros acima e sugira acoes simples.")
        return sb.toString()
    }
}

// ── Helpers de data/hora compartilhados ─────────────────────────────

fun vidaTimeLabel(msOfDay: Long): String {
    val h = (msOfDay / 3_600_000L).toInt()
    val min = ((msOfDay % 3_600_000L) / 60_000L).toInt()
    return if (min == 0) "%dh".format(h) else "%d:%02d".format(h, min)
}

fun vidaMoney(v: Double) = "R$ %.2f".format(java.util.Locale("pt", "BR"), v)

/** Converte "14:00", "14h30", "as 14h" em ms no dia. */
fun timeFromStr(s: String): Long {
    val m = s.trim().matchFirst(Regex("([0-9]{1,2})\\s*[:h]\\s*([0-9]{2})?")) ?: return 0L
    val h = m.groupValues[1].toInt().coerceIn(0, 23)
    val min = m.groupValues[2].toIntOrNull() ?: 0
    return h * 3_600_000L + min * 60_000L
}
