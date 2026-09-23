package com.goldrs.vida

import java.util.Calendar

/** Acao controlada que a IA propoe; a UI pede confirmacao antes de executar. */
data class AiAction(val summary: String, val sensitive: Boolean, val run: () -> Unit)

/** Resultado de uma conversa: resposta em texto + acao opcional. */
data class AiResult(val reply: String, val action: AiAction? = null)

private val WEEKDAYS = mapOf(
    "domingo" to 1, "segunda" to 2, "terca" to 3, "quarta" to 4,
    "quinta" to 5, "sexta" to 6, "sabado" to 7
)

private val TIME_REGEX = Regex("as\\s*([0-9]{1,2})(:|h)([0-9]{2})?")

/** Início do dia atual em ms. */
fun vidaTodayStart(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** Interpreta datas relativas em pt-BR: "hoje", "amanha", "proxima sexta", "15/09". 0 = sem data. */
fun vidaParseWhen(raw: String): Long {
    val input = LocalStore.stripAccents(raw.trim().lowercase())
    val today = vidaTodayStart()
    val dm = Regex("(\\d{1,2})/(\\d{1,2})").find(input)
    if (dm != null) {
        val d = dm.groupValues[1].toInt(); val mo = dm.groupValues[2].toInt()
        if (mo in 1..12 && d in 1..31) {
            val c = Calendar.getInstance()
            c.set(Calendar.MONTH, mo - 1); c.set(Calendar.DAY_OF_MONTH, d)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            var ts = c.time
            if (ts.time < today) {
                c.add(Calendar.YEAR, 1)
                ts = c.time
            }
            return ts.time
        }
    }
    if (input.contains("depois de amanha")) return today + 2L * LocalStore.DAY_MS
    if (input.contains("amanha")) return today + LocalStore.DAY_MS
    if (input.contains("semana que vem")) return today + 7L * LocalStore.DAY_MS
    val wd = WEEKDAYS.entries.firstOrNull { input.contains(it.key) }
    if (wd != null) {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        var delta = ((wd.value - dow + 7) % 7).toLong()
        if (delta == 0L) delta = 7L
        if (input.contains("proxim")) delta += 7L
        return today + delta * LocalStore.DAY_MS
    }
    if (input.contains("hoje")) return today
    return 0L
}

/** Rótulo amigável para um timestamp de dia: "hoje", "amanhã", "sexta 15/09". */
fun vidaDateLabel(ms: Long): String {
    if (ms <= 0) return "sem data"
    val today = vidaTodayStart()
    if (ms == today) return "hoje"
    if (ms == today + LocalStore.DAY_MS) return "amanhã"
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val name = WEEKDAYS.entries.firstOrNull { it.value == c.get(Calendar.DAY_OF_WEEK) }?.key ?: ""
    val dt = java.text.SimpleDateFormat("dd/MM", java.util.Locale("pt", "BR")).format(c.time)
    return if (name.isNotBlank()) "$name $dt" else dt
}

/**
 * Motor offline do assistente: entende comandos em portugues e traduz para
 * operacoes no LocalStore. Sem chave de IA ele mantem o app funcional;
 * com chave, o LLM assume as conversas mais abertas.
 */
class AiEngine(private val store: LocalStore) {

    fun handle(rawInput: String): AiResult {
        val input = LocalStore.stripAccents(rawInput.trim().lowercase())

        // ── Consultas ─────────────────────────────────────────────
        if (input.startsWith("quanto") && (input.contains("gasto") || input.contains("gast"))) {
            val cat = extractCategory(input)
            if (cat != null) {
                val (sum, n) = store.categorySpendThisMonth(cat)
                return if (n == 0) AiResult("Você não registrou gastos de \"$cat\" este mês. ${hintExpense()}")
                else AiResult("Você gastou ${money(sum)} com $cat este mês, em $n lançamento(s).")
            }
            return AiResult(spendingReport())
        }
        val isCreateish = input.startsWith("criar") || input.startsWith("crie") || input.startsWith("registrar") ||
            input.startsWith("registre") || input.startsWith("adicione") || input.startsWith("coloque") ||
            input.startsWith("anote") || input.startsWith("lembre")
        if (!isCreateish && (input.contains("gasto") || input.startsWith("analise") || input.startsWith("econom"))) {
            return AiResult(spendingReport())
        }
        if ((input.startsWith("quais") || input.startsWith("contas") || input.startsWith("conta")) &&
            (input.contains("vencem") || input.contains("proxim"))) {
            val days = Regex("([0-9]+)\\s*dias").find(input)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
            val bills = store.upcomingBills(vidaTodayStart(), vidaTodayStart() + days.toLong() * LocalStore.DAY_MS)
            return if (bills.isEmpty()) AiResult("Nenhuma conta vence nos próximos $days dias. 🎉")
            else AiResult("Vencem nos próximos $days dias: " + listBills(bills))
        }
        if (input.contains("atrasad") && (input.contains("tarefa") || input.startsWith("quais"))) {
            val late = store.overdueTasks(System.currentTimeMillis())
            return if (late.isEmpty()) AiResult("Nenhuma tarefa atrasada. Tudo em dia!")
            else AiResult("Você tem ${late.size} tarefa(s) atrasada(s): " + late.joinToString("; ") { it.title } +
                ". Para adiar, diga: \"adiar X para amanhã\".")
        }
        if (input.startsWith("planej") || input.startsWith("organize") || input.contains("prioridade")) {
            return AiResult(planWeek())
        }
        if (input.contains("livre") && (input.contains("o que") || input.contains("fazer"))) {
            return AiResult(suggestFreeTime(input))
        }

        // ── Alterações: concluir / adiar ──────────────────────────
        if (input.startsWith("marcar") || input.startsWith("concluir") || input.startsWith("finalizar")) {
            val open = store.byKind("task").filter { !it.done }
            val stop = setOf("marcar", "marque", "concluir", "conclua", "finalizar", "finalize", "como",
                "a", "o", "tarefa", "tarefas", "minha", "minhas", "de", "da", "do")
            val target = stripWords(input, stop)
            val hit = open.firstOrNull { t ->
                val n = LocalStore.stripAccents(t.title).lowercase()
                input.contains(n) || target.contains(n)
            } ?: open.firstOrNull()
            if (hit == null) return AiResult("Nenhuma tarefa pendente para concluir. Crie uma e depois peça: \"concluir X\".")
            val t = hit
            return AiResult("Posso marcar \"${t.title}\" como concluída?",
                AiAction("Concluir \"${t.title}\"", false) {
                    store.toggle(t.id); store.logAiAction("concluir: ${t.title}")
                })
        }
        if (input.startsWith("adiar")) {
            val whenMs = vidaParseWhen(input)
            if (whenMs == 0L) return AiResult("Para qual dia você quer adiar? Diga, por exemplo: \"adiar ${sampleTask()} para amanhã\".")
            val open = store.byKind("task").filter { !it.done }
            val stop = setOf("adiar", "para", "ate", "o", "a", "tarefa", "minha", "de", "da", "do")
            val target = stripWords(input, stop)
            val hit = open.firstOrNull { t ->
                val n = LocalStore.stripAccents(t.title).lowercase()
                input.contains(n) || target.contains(n)
            }
            if (hit == null) return AiResult("Não encontrei essa tarefa entre as pendentes. Você tem: " + taskList())
            val t = hit
            val dayLabel = vidaDateLabel(whenMs)
            return AiResult("Posso adiar \"${t.title}\" para $dayLabel?",
                AiAction("Adiar \"${t.title}\" para $dayLabel", false) {
                    store.moveDue(t.id, whenMs); store.logAiAction("adiar: ${t.title} -> $dayLabel")
                })
        }
        // ── Criação ─────────────────────────────────────────────────
        val isEvent = input.contains("reuniao") || input.contains("compromisso") || input.contains("evento") ||
            input.contains("consulta") || input.contains("entrevista") ||
            (input.startsWith("criar") || input.startsWith("crie"))
        val isExpense = input.contains("despesa") || input.contains("gasto") || input.contains("paguei") || input.contains("pagar")
        val isIncome = input.contains("receita") || input.contains("ganhei")
        val isShopping = input.contains("compras") || input.contains("compra") || input.contains("mercearia") || input.contains("farmacia")
        val isNote = input.contains("nota") || input.contains("anota")
        val isGoal = input.contains("meta") || input.contains("metas") || input.contains("objetivo")
        val isTask = input.contains("tarefa") || input.startsWith("lembre") || input.contains("lembrete") || input.contains("lembra")
        val isCreate = input.startsWith("crie") || input.startsWith("criar") || input.startsWith("registrar") || input.startsWith("adicione") || input.startsWith("coloque") || input.startsWith("anote") || input.startsWith("lembre")

        if (isCreate) {
            val time = extractTime(input)
            val whenMs = if (isExpense || isIncome) 0L else vidaParseWhen(input)
            val cleaned = cleanTitle(input)

            when {
                isExpense -> {
                    val amount = parseAmount(input)
                    if (amount <= 0.0) return AiResult("Qual o valor? Diga, por exemplo: \"registrar despesa de R$ 45,90 com mercado\".")
                    val title = cleaned.ifBlank { "Despesa" }
                    val cat = extractCategory(input) ?: ""
                    return AiResult("Posso registrar a despesa \"$title\" de ${money(amount)}${if (cat.isNotBlank()) " em $cat" else ""}?",
                        AiAction("Registrar despesa \"$title\" de ${money(amount)}", true) {
                            store.add(title, "expense", amount, category = cat); store.logAiAction("despesa: $title ${money(amount)}")
                        })
                }
                isIncome -> {
                    val amount = parseAmount(input)
                    if (amount <= 0.0) return AiResult("Qual o valor? Diga, por exemplo: \"registrar receita de R$ 500 com freelance\".")
                    val title = cleaned.ifBlank { "Receita" }
                    val cat = extractCategory(input) ?: ""
                    return AiResult("Posso registrar a receita \"$title\" de ${money(amount)}${if (cat.isNotBlank()) " em $cat" else ""}?",
                        AiAction("Registrar receita \"$title\" de ${money(amount)}", true) {
                            store.add(title, "income", amount, category = cat); store.logAiAction("receita: $title ${money(amount)}")
                        })
                }
                isShopping -> {
                    val title = cleaned.ifBlank { "Item" }
                    return AiResult("Posso colocar \"$title\" na lista de compras?",
                        AiAction("Adicionar \"$title\" às compras", false) {
                            store.add(title, "shopping"); store.logAiAction("compra: $title")
                        })
                }
                isNote -> {
                    val title = cleaned.ifBlank { "Nota" }
                    return AiResult("Posso criar a nota \"$title\"?",
                        AiAction("Criar nota \"$title\"", false) {
                            store.add(title, "note"); store.logAiAction("nota: $title")
                        })
                }
                isGoal -> {
                    val title = cleaned.ifBlank { "Meta" }
                    return AiResult("Posso criar a meta \"$title\"?",
                        AiAction("Criar meta \"$title\"", false) {
                            store.add(title, "goal"); store.logAiAction("meta: $title")
                        })
                }
                isEvent -> {
                    val whenLabel = vidaDateLabel(if (whenMs > 0) whenMs else vidaTodayStart())
                    val timeLabel = if (time > 0) timeLabel(time) else ""
                    val title = cleaned.ifBlank { "Compromisso" }
                    val eventAt = if (whenMs > 0) whenMs else vidaTodayStart()
                    return AiResult("Deseja criar o compromisso \"$title\" para $whenLabel${if (timeLabel.isNotBlank()) ", às $timeLabel" else ""}?",
                        AiAction("Criar evento \"$title\" em $whenLabel${if (timeLabel.isNotBlank()) " às $timeLabel" else ""}", true) {
                            store.add(title, "event", dueDate = eventAt, time = time); store.logAiAction("evento: $title $whenLabel")
                        })
                }
                else -> {
                    val whenLabel = if (whenMs > 0) vidaDateLabel(whenMs) else ""
                    val title = cleaned.ifBlank { "Tarefa" }
                    val prio = if (input.contains("urgente") || input.contains("importante") || input.contains("alta")) 2 else 0
                    return AiResult("Posso criar a tarefa \"$title\"${if (whenLabel.isNotBlank()) " com prazo de $whenLabel" else ""}?",
                        AiAction("Criar tarefa \"$title\"${if (whenLabel.isNotBlank()) " (prazo: $whenLabel)" else ""}", true) {
                            store.add(title, "task", dueDate = whenMs, priority = prio); store.logAiAction("tarefa: $title")
                        })
                }
            }
        }

        // ── Fallback ────────────────────────────────────────────────
        return AiResult(fallback())
    }

    // ── Relatórios ──────────────────────────────────────────────────

    private fun spendingReport(): String {
        val total = store.totalThisMonth("expense")
        if (total <= 0.0) return "Ainda não há despesas este mês. ${hintExpense()}"
        val cats = store.byKind("expense").filter { it.createdAt >= monthStart() }
            .groupBy { if (it.category.isNotBlank()) it.category else "geral" }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        val top = cats.entries.sortedByDescending { it.value }
            .take(3)
            .joinToString(" · ") { "${it.key}: ${money(it.value)} (${(it.value / total * 100).toInt()}%)" }
        val budgetLine = if (store.budget() > 0) {
            val b = store.budget()
            if (total > b) " ⚠ Você estourou o orçamento em ${money(total - b)}."
            else " Faltam ${money(b - total)} do seu orçamento."
        } else ""
        return "Gastos deste mês: ${money(total)}. Principais: $top.$budgetLine Se quiser economizar, crie uma meta comigo: \"criar meta de economizar com X\"."
    }

    private fun listBills(bills: List<Item>): String =
        bills.map { v ->
            val label = if (v.kind == "expense") "− " else "+ "
            "${v.title} ${label}${money(v.amount)}${if (v.dueDate > 0) " (${vidaDateLabel(v.dueDate)})" else ""}"
        }.joinToString("; ")

    private fun planWeek(): String {
        val now = System.currentTimeMillis()
        val overdue = store.overdueTasks(now)
        val today = store.tasksDueOn(vidaTodayStart())
        val soon = (1..7).flatMap { store.tasksDueOn(vidaTodayStart() + it * LocalStore.DAY_MS) }.distinctBy { it.id }
        val noDate = store.byKind("task").filter { !it.done && it.dueDate == 0L }
        val open = store.byKind("task").filter { !it.done }
        if (open.isEmpty()) return "Nenhuma tarefa pendente — sua semana está livre! Quer criar compromissos ou metas? É só pedir."
        val sb = StringBuilder("Sugestão de plano:\n")
        if (overdue.isNotEmpty()) sb.append("🔴 Primeiro, as atrasadas: ").appendLine(overdue.joinToString("; ") { it.title }).appendLine()
        if (today.isNotEmpty()) sb.append("📅 Hoje: ").appendLine(today.joinToString("; ") { it.title }).appendLine()
        val upcoming = soon.filterNot { today.contains(it) }
        if (upcoming.isNotEmpty()) sb.append("⏭ Próximos dias: ").appendLine(upcoming.take(5).joinToString("; ") { it.title }).appendLine()
        if (noDate.isNotEmpty()) sb.append("🕓 Sem prazo (${noDate.size}): ").append(noDate.take(5).joinToString("; ") { it.title }).append(" — diga \"adiar X para segunda\" para eu definir prazos.")
        return sb.toString().trim()
    }

    private fun suggestFreeTime(input: String): String {
        val hours = Regex("([0-9]+)\\s*(?:h|horas)").find(input)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2
        val cands = store.byKind("task").filter { !it.done }
            .sortedWith(compareByDescending<Item> { it.priority }.thenBy { if (it.dueDate > 0) it.dueDate else Long.MAX_VALUE })
            .take(2)
        if (cands.isEmpty()) return "Você está livre e sem tarefas — bom momento para planejar algo novo! Peça \"criar meta...\" ou \"planejar minha semana\"."
        val min = hours * 60
        val first = cands.first()
        val verb = if (min <= 45) "dá para concluir" else "vale a pena começar"
        val rest = if (cands.size > 1) " — depois, ${cands[1].title}" else ""
        return "Com ${hours}h livres: ${verb} \"${first.title}\"$rest. Quer que eu conclua ou adie alguma?"
    }

    // ── Helpers de parsing ──────────────────────────────────────────

    private fun sampleTask(): String =
        store.byKind("task").firstOrNull { !it.done }?.title ?: "a tarefa"

    private fun taskList(): String {
        val open = store.byKind("task").filter { !it.done }.take(5).map { it.title }
        return if (open.isEmpty()) "nenhuma pendente" else open.joinToString("; ")
    }

    private fun extractCategory(input: String): String? =
        Regex("\\bcom ([a-z0-9 ]+)").find(input)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("\\bde ([a-z0-9 ]+)$").find(input)?.groupValues?.getOrNull(1)?.trim()

    private fun parseAmount(input: String): Double {
        val m = Regex("r\\$\\s*([0-9]+[.,]?[0-9]*)").find(input)
            ?: Regex("\\bpaguei\\s+([0-9]+[.,]?[0-9]*)").find(input)
            ?: Regex("\\b([0-9]+[.,][0-9]{1,2})\\b").find(input)
        return m?.groupValues?.getOrNull(1)?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
    }

    private fun extractTime(input: String): Long {
        val m = TIME_REGEX.find(input) ?: return 0L
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[3].toIntOrNull() ?: 0
        return h * 3_600_000L + min * 60_000L
    }

    private fun timeLabel(msOfDay: Long): String {
        val h = (msOfDay / 3_600_000L).toInt()
        val min = ((msOfDay % 3_600_000L) / 60_000L).toInt()
        return if (min == 0) "%dh".format(h) else "%d:%02d".format(h, min)
    }

    private fun stripWords(text: String, stop: Set<String>): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        var i = 0
        while (i < words.size && stop.contains(words[i])) i++
        while (i < words.size && setOf("de", "do", "da", "o", "a", "os", "as").contains(words[i])) i++
        return words.drop(i).joinToString(" ")
    }

    private fun cleanTitle(input: String): String {
        var text = input
        TIME_REGEX.find(text)?.let { text = text.replace(it.value, " ") }
        text = text.replace(Regex("proximos?\\s+[0-9]+\\s*dias"), " ")
            .replace(Regex("(proximo[a]?)?\\s*(domingo|segunda|terca|quarta|quinta|sexta|sabado)(\\s+([0-9]{2}/[0-9]{2}))?"), " ")
            .replace(Regex("semana que vem"), " ")
            .replace(Regex("depois de amanha"), " ")
            .replace(Regex("amanha"), " ")
            .replace(Regex("\\bhoje\\b"), " ")
        val stop = setOf("crie", "criar", "registrar", "registre", "adicione", "adicione", "anote", "lembre-me",
            "lembre", "lembrete", "coloque", "por favor", "me", "te", "reuniao", "reunioes", "compromisso",
            "compromissos", "evento", "eventos", "consulta", "entrevista", "tarefa", "tarefas", "despesa",
            "despesas", "receita", "gasto", "gastos", "paguei", "nota", "notas", "anotacao", "anotacoes",
            "compras", "compra", "lista", "produto", "produtos", "item", "meta", "metas", "objetivo",
            "objetivos", "em", "a", "o", "as", "os")
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        var i = 0
        while (i < words.size && stop.contains(words[i])) i++
        while (i < words.size && setOf("de", "do", "da", "o", "a", "os", "as").contains(words[i])) i++
        return words.drop(i).joinToString(" ")
    }

    private fun fallback(): String =
        "Posso criar tarefas, eventos, notas, metas e itens de compra; registrar receitas e despesas; adiar ou concluir tarefas; e responder \"quanto gastei com X\", \"quais contas vencem\" e \"planejar minha semana\". " +
            hintExpense() +
            " Para conversar de forma mais livre, configure sua chave em Mais → Configurações → Assistente de IA."

    private fun hintExpense(): String =
        "Para eu analisar gastos, registre despesas com categoria: \"registrar despesa de R$ 30 com mercado\"."

    private fun monthStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun money(v: Double) = "R$ %.2f".format(java.util.Locale("pt", "BR"), v)
}
