package com.goldrs.vida

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.card.MaterialCardView
import java.util.Calendar
import java.util.Locale
import java.util.Date

/**
 * Tela principal do Vida — painel pessoal construído programaticamente sobre a
 * camada de design [Vida]: hero em gradiente, anel de progresso, gráfico de
 * barras, grade responsiva, animações escalonadas, press-scale e haptics.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var store: LocalStore
    private lateinit var engine: AiEngine
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var bottomBar: LinearLayout
    private var pal: Vida.Palette = Vida.paletteOf(this)
    private var activeTab = 0

    private val chatHistory = mutableListOf<Pair<String, String>>()
    private var chatLog: LinearLayout? = null
    private var assistantInput: EditText? = null
    private var cloudBusy = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        pal = Vida.paletteOf(this)
        store = LocalStore(this)
        engine = AiEngine(store)
        showHome()
    }

    // ── Estrutura / navegação ─────────────────────────────────────
    private fun frame(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.surface)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(30))
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        bottomBar = buildBottomBar()
        root.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)))
        return root
    }

    private data class Tab(val icon: String, val title: String, val index: Int, val open: () -> Unit)

    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(12))
            background = roundedBackground(30, pal.card, 1, pal.cardStroke)
            elevation = dp(6).toFloat()
        }
        val tabs = listOf(
            Tab("⌂", "Início", 0, { showHome() }),
            Tab("▣", "Agenda", 1, { showList("Agenda", "event") }),
            Tab("✓", "Tarefas", 2, { showList("Tarefas", "task") }),
            Tab("R$", "Finanças", 3, { showFinance() }),
            Tab("✦", "Assistente", 4, { showAssistant() })
        )
        for (t in tabs) {
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val icon = TextView(this@MainActivity).apply {
                text = t.icon; textSize = 20f; gravity = Gravity.CENTER; setTextColor(pal.muted)
            }
            val label = TextView(this@MainActivity).apply {
                text = t.title; textSize = 10.5f; gravity = Gravity.CENTER; setTextColor(pal.muted)
                setPadding(dp(2), 0, dp(2), 0)
            }
            col.addView(icon)
            col.addView(label)
            bar.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
            col.setOnClickListener {
                col.haptic()
                t.open()
            }
            col.pressScale()
        }
        return bar
    }

    private fun openTab(i: Int) {
        when (i) { 0 -> showHome(); 1 -> showList("Agenda", "event"); 2 -> showList("Tarefas", "task"); 3 -> showFinance(); else -> showAssistant() }
    }

    private fun setActiveTab(i: Int) {
        activeTab = i
        for (j in 0 until bottomBar.childCount) {
            val col = bottomBar.getChildAt(j) as? LinearLayout ?: continue
            val icon = col.getChildAt(0) as? TextView ?: continue
            val label = col.getChildAt(1) as? TextView ?: continue
            val on = j == i
            icon.setTextColor(if (on) pal.primary else pal.muted); icon.alpha = if (on) 1f else 0.5f
            label.setTextColor(if (on) pal.primary else pal.muted); label.alpha = if (on) 1f else 0.6f
        }
    }

    // ── Blocos reutilizáveis ──────────────────────────────────────
    private fun header(title: String, subtitle: String? = null) {
        content.removeAllViews()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(2))
        }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 0) }
        titleCol.addView(TextView(this@MainActivity).apply {
            text = "VIDA  •  SEU PAINEL PESSOAL"; textSize = 11f; setTextColor(pal.primary); setTypeface(null, 1)
        })
        titleCol.addView(TextView(this@MainActivity).apply { this.text = title; textSize = 28f; setTextColor(pal.ink); setTypeface(null, 1); setPadding(0, dp(2), 0, 0) })
        top.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val gear = TextView(this).apply {
            text = "⚙"; textSize = 22f; gravity = Gravity.CENTER; setPadding(dp(6), 0, dp(6), 0)
        }
        gear.setOnClickListener { showMore() }
        gear.pressScale()
        top.addView(gear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(top)
        subtitle?.let {
            content.addView(TextView(this).apply { this.text = it; textSize = 14f; setTextColor(pal.muted); setPadding(0, dp(6), 0, dp(16)) })
        }
    }

    private fun section(label: String) {
        content.addView(TextView(this).apply {
            text = label; textSize = 13f; setTextColor(pal.muted); setTypeface(null, 1); setPadding(0, dp(14), 0, dp(8))
        })
    }

    private fun hero(title: String, subtitle: String): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientBackground(26, pal.heroTop, pal.heroBottom)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            elevation = dp(8).toFloat()
        }
        h.addView(TextView(this).apply { text = title; textSize = 23f; setTextColor(Color.WHITE); setTypeface(null, 1) })
        h.addView(TextView(this).apply {
            text = subtitle; textSize = 13f; setTextColor(Color.argb(215, 255, 255, 255)); setPadding(0, dp(4), 0, 0)
        })
        content.addView(h, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(14) })
        return h
    }

    private fun statCard(label: String, value: String, accent: Int = pal.primary, sub: String = "", onClick: (() -> Unit)? = null): MaterialCardView {
        val c = MaterialCardView(this).apply {
            radius = dp(18).toFloat(); cardElevation = 1f
            setCardBackgroundColor(pal.card); strokeWidth = 1; strokeColor = pal.cardStroke
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        col.addView(TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(pal.muted) })
        col.addView(TextView(this@MainActivity).apply {
            text = value; textSize = 22f; setTextColor(accent); setTypeface(null, 1); setPadding(0, dp(5), 0, 0)
        })
        if (sub.isNotEmpty()) col.addView(TextView(this@MainActivity).apply { text = sub; textSize = 11f; setTextColor(pal.muted); setPadding(0, dp(4), 0, 0) })
        c.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        onClick?.let { c.setOnClickListener { it() }; c.pressScale() }
        return c
    }

    /** Card colocado direto na rolagem principal. */
    private fun addStatCard(label: String, value: String, accent: Int = pal.primary, sub: String = "") {
        val c = statCard(label, value, accent, sub)
        content.addView(c, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(10) })
    }

    private fun actionButton(text: String, action: () -> Unit): TextView {
        val b = TextView(this).apply {
            this.text = text; textSize = 15f; setTypeface(null, 1); gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = gradientBackground(16, pal.primary, pal.violet)
            setPadding(dp(18), dp(13), dp(18), dp(13))
            elevation = dp(3).toFloat()
        }
        b.setOnClickListener { b.haptic(); action() }
        b.pressScale()
        content.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10) })
        return b
    }

    private fun outlineButton(text: String, action: () -> Unit): TextView {
        val b = TextView(this).apply {
            this.text = text; textSize = 15f; setTypeface(null, 1); gravity = Gravity.CENTER
            setTextColor(pal.primary)
            background = roundedBackground(16, pal.surfaceTint, 1, pal.primary)
            setPadding(dp(18), dp(13), dp(18), dp(13))
        }
        b.setOnClickListener { b.haptic(); action() }
        b.pressScale()
        content.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10) })
        return b
    }

    // ── Início ────────────────────────────────────────────────────
    private fun showHome() {
        setContentView(frame()); setActiveTab(0)
        val date = java.text.SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(Date()).replaceFirstChar { it.uppercase() }
        header("Olá 👋", date)
        hero("Bem-vindo ao seu dia", "Um resumo rápido para você se organizar em segundos.")

        section("VISÃO GERAL")
        val today = startOfDay(System.currentTimeMillis())
        val dayTasks = store.tasksDueOn(today)
        val pending = store.byKind("task").count { !it.done }
        val eventsToday = store.eventsOn(today).size
        val balance = store.totalThisMonth("income") - store.totalThisMonth("expense")

        val cards = mutableListOf<View>(
            statCard("Tarefas pendentes", pending.toString(), if (pending > 0) pal.amber else pal.green, "Toque para ver", { showList("Tarefas", "task") }),
            statCard("Compromissos hoje", eventsToday.toString(), pal.teal, "Agenda do dia", { showList("Agenda", "event") }),
            statCard("Saldo do mês", moneyLabel(balance), if (balance >= 0) pal.green else pal.red, "Receitas − despesas", { showFinance() })
        )
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(grid)
        responsiveGrid(grid, this, cards, minDp = 260, gapDp = 10)

        val late = store.overdueTasks(System.currentTimeMillis())
        if (late.isNotEmpty()) {
            addStatCard("Atrasadas", "${late.size} tarefa(s)", pal.red, "Precisam da sua atenção")
        }

        if (eventsToday > 0) {
            section("AGENDA DE HOJE")
            store.eventsOn(today).forEach { content.addView(eventRow(it)) }
        }

        section("AÇÕES RÁPIDAS")
        val quick = listOf(
            "＋  Nova tarefa" to { inputDialog("Nova tarefa", "task") },
            "＋  Novo compromisso" to { inputDialog("Novo compromisso", "event") },
            "＋  Registrar despesa" to { financialDialog("Nova despesa", "expense") },
            "✦  Falar com a IA" to { showAssistant() }
        )
        val qgrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(qgrid)
        val qviews = quick.map { (label, fn) -> quickBtn(label, fn) }
        responsiveGrid(qgrid, this, qviews, minDp = 260, gapDp = 10)
        staggerIn(content)
    }

    private fun quickBtn(label: String, fn: () -> Unit): View {
        val b = TextView(this).apply {
            this.text = label; textSize = 14f; setTypeface(null, 1); gravity = Gravity.CENTER
            setTextColor(pal.primary); background = roundedBackground(14, pal.surfaceTint, 1, pal.cardStroke)
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        b.setOnClickListener { b.haptic(); fn() }
        b.pressScale()
        return b
    }

    // ── Listas (tarefas / agenda) ─────────────────────────────────
    private fun showList(name: String, kind: String) {
        setContentView(frame()); setActiveTab(if (kind == "event") 1 else 2)
        header(name, if (kind == "event") "Seus compromissos do dia" else "Organize o que importa")
        val items = store.byKind(kind)
        section(if (items.isEmpty()) "NADA POR AQUI" else "SEUS ITENS")
        if (items.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "Ainda não há itens. Adicione o primeiro abaixo."; textSize = 15f; setTextColor(pal.muted); setPadding(0, 0, 0, dp(12))
            })
        } else {
            content.addView(TextView(this).apply {
                text = "Toque para concluir  •  segure para excluir"; textSize = 12f; setTextColor(pal.muted); setPadding(0, 0, 0, dp(8))
            })
            val sorted = if (kind == "event") items.sortedBy { if (it.dueDate > 0) it.dueDate else Long.MAX_VALUE } else items
            sorted.forEach { content.addView(itemCard(it, kind)) }
        }
        actionButton(if (kind == "event") "＋  Novo compromisso" else "＋  Nova tarefa") {
            inputDialog(if (kind == "event") "Novo compromisso" else "Nova tarefa", kind)
        }
        staggerIn(content)
    }

    private fun itemCard(item: Item, kind: String): MaterialCardView {
        val c = MaterialCardView(this).apply {
            radius = dp(16).toFloat(); cardElevation = 0.5f
            setCardBackgroundColor(pal.card); strokeWidth = 1; strokeColor = pal.cardStroke
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }

        val dot = View(this).apply {
            val size = dp(20)
            layoutParams = ViewGroup.LayoutParams(size, size)
            background = if (item.done) circle(pal.green) else circleStroke(pal.primary, 2)
        }
        row.addView(dot)
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = item.title; textSize = 15f; setTextColor(if (item.done) pal.muted else pal.ink)
                paint.isStrikeThruText = item.done; maxLines = 2
            })
            addView(subRow(item, kind))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val pri = when (item.priority) {
            2 -> chip(this, "Alta", pal.red, Color.WHITE)
            1 -> chip(this, "Média", pal.amber, Color.rgb(60, 45, 10))
            else -> null
        }
        pri?.let { p -> row.addView(p, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { leftMargin = dp(8) }) }

        c.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        c.setOnClickListener {
            c.haptic()
            store.toggle(item.id)
            if (kind == "event") showList("Agenda", "event") else showList("Tarefas", "task")
        }
        c.setOnLongClickListener { confirmDelete(item.id, kind); true }
        c.pressScale()
        return c
    }

    private fun subRow(it: Item, kind: String): TextView {
        val parts = mutableListOf<String>()
        val due = dueLabel(it)
        if (due.isNotEmpty()) parts.add(if (kind == "event") due else "prazo: $due")
        if (kind == "event" && it.time > 0) parts.add(vidaTimeLabel(it.time))
        if (it.category.isNotEmpty()) parts.add(it.category)
        if (it.body.isNotEmpty()) parts.add(it.body)
        return TextView(this).apply {
            text = parts.joinToString("  •  "); textSize = 11.5f; setTextColor(pal.muted); setPadding(0, dp(3), 0, 0); maxLines = 1
        }
    }

    private fun eventRow(item: Item): View {
        val time = if (item.time > 0) vidaTimeLabel(item.time) else ""
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat(); cardElevation = 0.5f
            setCardBackgroundColor(pal.card); strokeWidth = 1; strokeColor = pal.cardStroke
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(11), dp(14), dp(11)) }
        val timeBox = TextView(this).apply {
            text = time.ifEmpty { "—" }; textSize = 13f; setTypeface(null, 1); gravity = Gravity.CENTER; setTextColor(pal.primary)
            setPadding(dp(10), dp(4), dp(10), dp(4)); background = roundedBackground(10, pal.chipBg)
        }
        row.addView(timeBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(12) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this@MainActivity).apply { text = item.title; textSize = 15f; setTextColor(if (item.done) pal.muted else pal.ink); paint.isStrikeThruText = item.done })
        if (item.body.isNotEmpty()) col.addView(TextView(this@MainActivity).apply { text = item.body; textSize = 11.5f; setTextColor(pal.muted) })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.setOnClickListener { card.haptic(); store.toggle(item.id); showHome() }
        card.setOnLongClickListener { confirmDelete(item.id, "event"); true }
        card.pressScale()
        return card
    }

    private fun dueLabel(it: Item): String {
        if (it.dueDate == 0L) return ""
        val today = startOfDay(System.currentTimeMillis())
        val day = startOfDay(it.dueDate)
        val diff = day - today
        return when (diff) {
            -LocalStore.DAY_MS -> "Atrasada"
            0L -> "Hoje"
            LocalStore.DAY_MS -> "Amanhã"
            else -> java.text.SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(Date(it.dueDate))
        }
    }

    // ── Finanças ──────────────────────────────────────────────────
    private fun showFinance() {
        setContentView(frame()); setActiveTab(3)
        val income = store.totalThisMonth("income")
        val expense = store.totalThisMonth("expense")
        val balance = income - expense
        val budget = store.budget()

        header("Finanças", "Uma visão clara do seu dinheiro")
        hero("Saldo do mês", moneyLabel(balance) + "  (receitas ${moneyLabel(income)} − despesas ${moneyLabel(expense)})")

        // Orçamento + anel
        val ring = RingView(this, pal)
        ring.layoutParams = ViewGroup.LayoutParams(dp(104), dp(104))
        if (budget > 0) {
            val pct = (expense / budget).coerceAtMost(1f)
            ring.set(if (expense > budget) 1f else pct, moneyLabel(expense), "de ${moneyLabel(budget)}")
        } else ring.set(0f, "sem limite", "orçamento")

        val ringText = TextView(this).apply {
            text = if (budget > 0) (if (expense > budget) "Você passou do limite em ${moneyLabel(expense - budget)}." else "Falta ${moneyLabel(budget - expense)} do limite.")
            else "Defina um limite mensal para eu acompanhar seus gastos."
            textSize = 13f; setTextColor(pal.muted); setLineSpacing(dpF(4f), 1f); gravity = Gravity.CENTER_VERTICAL
        }
        val ringRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        ringRow.addView(ring, ViewGroup.LayoutParams(dp(104), dp(104)))
        ringRow.addView(ringText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(16) })

        val ringCard = MaterialCardView(this).apply {
            radius = dp(20).toFloat(); cardElevation = 1f
            setCardBackgroundColor(pal.card); strokeWidth = 1; strokeColor = pal.cardStroke
        }
        ringCard.addView(ringRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ringCard.setOnClickListener { budgetDialog() }
        ringCard.pressScale()
        content.addView(ringCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
        // Gráfico de barras — últimos 14 dias de gasto
        section("ÚLTIMOS 14 DIAS")
        val chart = BarChartView(this, pal).apply { setData(last14DaysExpense()) }
        content.addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)))

        // Top categorias
        val cats = topCategories(3)
        if (cats.isNotEmpty()) {
            section("MAIORES CATEGORIAS DO MÊS")
            cats.forEach { (name, total, count) -> categoryBar(name, total, count, cats.first().second) }
        }

        section("AÇÕES")
        val agrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(agrid)
        responsiveGrid(agrid, this, listOf(
            quickBtn("Definir orçamento", { budgetDialog() }),
            quickBtn("＋ Despesa", { financialDialog("Nova despesa", "expense") }),
            quickBtn("＋ Receita", { financialDialog("Nova receita", "income") })
        ), minDp = 260, gapDp = 10)

        val recent = store.byKind("expense") + store.byKind("income")
        if (recent.isNotEmpty()) {
            section("MOVIMENTAÇÕES RECENTES")
            recent.take(6).forEach {
                val c = statCard(if (it.kind == "expense") it.title else "Receita: ${it.title}",
                    (if (it.kind == "expense") "− " else "+ ") + moneyLabel(it.amount),
                    if (it.kind == "expense") pal.red else pal.green,
                    vidaDateLabel(it.createdAt))
                c.setOnLongClickListener { confirmDelete(it.id, it.kind); true }
                content.addView(c, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(10) })
            }
        }
        staggerIn(content)
    }

    private fun last14DaysExpense(): List<Double> {
        val today = startOfDay(System.currentTimeMillis())
        val out = ArrayList<Double>(14)
        for (i in 13 downTo 0) {
            val dayStart = today - i * LocalStore.DAY_MS
            out.add(store.byKind("expense").filter { it.createdAt in dayStart until dayStart + LocalStore.DAY_MS }.sumOf { it.amount })
        }
        return out
    }

    private fun topCategories(n: Int): List<Pair<String, Pair<Double, Int>>> {
        val today = startOfDay(System.currentTimeMillis())
        val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val map = LinkedHashMap<String, Pair<Double, Int>>()
        store.byKind("expense").filter { it.createdAt >= start }.forEach {
            val key = it.category.ifBlank { it.title }
            val cur = map[key]
            map[key] = if (cur == null) it.amount to 1 else (cur.first + it.amount) to (cur.second + 1)
        }
        return map.map { it.key to it.value }.sortedByDescending { it.second.first }.take(n)
    }

    private fun categoryBar(name: String, total: Double, count: Int, max: Double) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(5), 0, dp(5)) }
        row.addView(TextView(this).apply {
            text = name; textSize = 13f; setTextColor(pal.ink); setPadding(0, 0, dp(10), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = roundedBackground(8, pal.chipBg); setPadding(dp(4), 0, dp(4), 0) }
        bar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(8), ((total / max).coerceIn(0.06, 1.0)).toFloat())
            background = gradientBackground(6, pal.primary, pal.violet)
        })
        row.addView(bar, LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        row.addView(TextView(this).apply {
            text = moneyLabel(total); textSize = 12f; setTextColor(pal.muted); setPadding(dp(10), 0, 0, 0)
        })
        content.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    // ── Módulos (notas, compras, metas, hábitos) ─────────────────
    private fun showMore() {
        setContentView(frame()); setActiveTab(0)
        header("Organizar", "Tudo que você precisa, em um só lugar")
        section("MÓDULOS")
        val mgrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(mgrid)
        val items = listOf(
            module("📝", "Notas", "Ideias e registros"), { showModule("Notas", "note", "＋  Nova nota") },
            module("🛒", "Lista de compras", "Produtos e categorias"), { showModule("Compras", "shopping", "＋  Adicionar produto") },
            module("🎯", "Metas", "Acompanhe o progresso"), { showModule("Metas", "goal", "＋  Nova meta") },
            module("🔁", "Hábitos", "Rotina sustentável"), { showModule("Hábitos", "habit", "＋  Novo hábito") },
            module("⚙", "Configurações", "Tema, PIN e exportar"), { settings() },
            module("✦", "Assistente", "IA com confirmação"), { showAssistant() }
        )
        responsiveGrid(mgrid, this, items, minDp = 280, gapDp = 10)
        staggerIn(mgrid)
    }

    private fun module(icon: String, name: String, desc: String, action: () -> Unit): View {
        val c = MaterialCardView(this).apply {
            radius = dp(18).toFloat(); cardElevation = 0.5f
            setCardBackgroundColor(pal.card); strokeWidth = 1; strokeColor = pal.cardStroke
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(15), dp(14), dp(15), dp(14)) }
        val badge = TextView(this).apply {
            text = icon; textSize = 22f; gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(10), 0)
        }
        row.addView(badge)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this@MainActivity).apply { text = name; textSize = 16f; setTextColor(pal.ink); setTypeface(null, 1) })
        col.addView(TextView(this@MainActivity).apply { text = desc; textSize = 12f; setTextColor(pal.muted); setPadding(0, dp(3), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        c.setOnClickListener { c.haptic(); action() }
        c.pressScale()
        return c
    }

    private fun showModule(name: String, kind: String, addLabel: String) {
        setContentView(frame()); setActiveTab(0)
        header(name, "Seus dados ficam salvos localmente")
        val items = store.byKind(kind)
        if (kind == "goal" && items.isNotEmpty()) {
            section("PROGRESSO")
            val ring = RingView(this, pal)
            ring.layoutParams = LinearLayout.LayoutParams(dp(150), dp(150))
            val done = items.count { it.done }
            ring.set(done.toFloat() / items.size, "$done/${items.size}", "metas concluídas")
            content.addView(ring)
        }
        section(if (items.isEmpty()) "COMECE AGORA" else "SEUS REGISTROS")
        if (items.isEmpty()) {
            content.addView(TextView(this).apply { text = "Você ainda não adicionou nada aqui."; textSize = 15f; setTextColor(pal.muted); setPadding(0, 0, 0, dp(12)) })
        } else {
            items.forEach {
                content.addView(itemCard(it, kind))
            }
        }
        actionButton(addLabel) { inputDialog(name, kind) }
        staggerIn(content)
    }

    // ── Assistente (IA offline + nuvem opcional) ──────────────────
    private fun showAssistant() {
        setContentView(frame()); setActiveTab(4)
        val mode = if (store.hasAiKey()) "IA na nuvem (sua chave) + fallback offline" else "Modo offline — nada sai do aparelho"
        header("Assistente", mode)

        section("SUGESTÕES")
        val sgrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sgrid)
        val sugs = listOf("Planejar minha semana", "Analisar meus gastos", "Quais contas vencem?", "Quais tarefas estão atrasadas?", "Criar nota: ideias do ano")
        responsiveGrid(sgrid, this, sugs.map { s -> quickBtn("✦  $s", { assistantInput?.setText(s); sendAssistant() }) }, minDp = 260, gapDp = 10)

        section("CONVERSA")
        chatLog = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        content.addView(chatLog)
        if (chatHistory.isEmpty()) addBubble("assistant", "Olá! Posso organizar suas tarefas, compromissos e finanças. Me diga o que fazer — ou toque em uma sugestão. Qualquer ação só acontece depois que você confirma.")

        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, 0) }
        assistantInput = EditText(this).apply {
            hint = "Escreva uma mensagem…"; textSize = 14f; setSingleLine()
            setTextColor(pal.ink); setHintTextColor(pal.muted)
            background = roundedBackground(16, pal.card, 1, pal.cardStroke); setPadding(dp(14), dp(11), dp(14), dp(11))
        }
        inputRow.addView(assistantInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        val send = TextView(this).apply {
            text = "Enviar"; textSize = 14f; setTypeface(null, 1); gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = gradientBackground(16, pal.primary, pal.violet); setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        send.setOnClickListener { sendAssistant() }
        inputRow.addView(send)
        content.addView(inputRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        staggerIn(content)
    }

    private fun sendAssistant() {
        val input = assistantInput ?: return
        val msg = input.text.toString().trim()
        if (msg.isEmpty()) return
        input.setText("")
        handleAssistant(msg)
    }

    private fun handleAssistant(msg: String) {
        addBubble("user", msg)
        chatHistory.add("user" to msg)
        if (chatHistory.size > 16) chatHistory.removeAt(0)
        if (store.hasAiKey() && !cloudBusy) askCloud(msg) else answerOffline(msg)
    }

    private fun answerOffline(msg: String) {
        val r = engine.handle(msg)
        finishAssistant(r)
    }

    private fun askCloud(msg: String) {
        addBubble("sys", "Consultando a IA na nuvem…")
        cloudBusy = true
        Thread {
            val cloud = AiCloud(store)
            val messages = mutableListOf(AiCloud.ChatMsg("system", ASSISTANT_SYSTEM))
            chatHistory.takeLast(8).forEach { messages.add(AiCloud.ChatMsg(it.first, it.second)) }
            val r = cloud.chat(messages) { _ -> } ?: engine.handle(msg)
            runOnUiThread { cloudBusy = false; finishAssistant(r) }
        }.start()
    }

    private fun finishAssistant(r: AiResult) {
        addBubble("assistant", r.reply)
        chatHistory.add("assistant" to r.reply)
        r.action?.let { confirmAction(it) }
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun confirmAction(a: AiAction) {
        val note = if (a.sensitive) "  (ação sensível — confira antes de confirmar)" else ""
        AlertDialog.Builder(this)
            .setTitle("Confirmar ação")
            .setMessage(a.summary + note)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ ->
                a.run()
                store.logAiAction(a.summary)
                addBubble("sys", "✓ ${a.summary} — concluído.")
            }.show()
    }

    private fun addBubble(role: String, text: String) {
        val log = chatLog ?: return
        val bg = when (role) {
            "user" -> gradientBackground(18, pal.primary, pal.violet)
            "assistant" -> roundedBackground(18, pal.card, 1, pal.cardStroke)
            else -> roundedBackground(14, pal.chipBg)
        }
        val tv = TextView(this).apply {
            this.text = text; textSize = 14f
            setLineSpacing(dpF(4f), 1f)
            setTextColor(when (role) { "user" -> Color.WHITE; "sys" -> pal.muted; else -> pal.ink })
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = bg
            maxWidth = dp(420)
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (role == "user") Gravity.END else Gravity.START
        }
        wrap.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        wrap.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        log.addView(wrap)
    }

    // ── Diálogos ──────────────────────────────────────────────────
    private fun confirmDelete(id: Long, kind: String) {
        AlertDialog.Builder(this)
            .setTitle("Excluir item")
            .setMessage("Tem certeza? Essa ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                store.delete(id)
                when (kind) {
                    "task" -> showList("Tarefas", kind)
                    "event" -> showList("Agenda", kind)
                    "expense", "income" -> showFinance()
                    else -> showMore()
                }
            }.show()
    }

    private fun inputDialog(label: String, kind: String) {
        if (kind == "expense" || kind == "income") { financialDialog(label, kind); return }
        val body = EditText(this).apply { hint = "Descrição (opcional)"; setSingleLine() }
        val input = EditText(this).apply { hint = label; setSingleLine() }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        box.addView(input); box.addView(body)
        AlertDialog.Builder(this).setTitle(label).setView(box)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                if (input.text.isNotBlank()) {
                    store.add(input.text.toString(), kind, body = body.text.toString())
                    when (kind) {
                        "task" -> showList("Tarefas", kind)
                        "event" -> showList("Agenda", kind)
                        else -> showModule(label, kind, "＋  Adicionar")
                    }
                }
            }.show()
    }

    private fun financialDialog(label: String, kind: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        val description = EditText(this).apply { hint = "Descrição"; setSingleLine() }
        val cat = EditText(this).apply { hint = "Categoria (ex.: alimentação)"; setSingleLine() }
        val amount = EditText(this).apply {
            hint = "Valor (ex.: 25,90)"; inputType = 2 or 4096; setSingleLine()
        }
        box.addView(description); box.addView(cat); box.addView(amount)
        AlertDialog.Builder(this).setTitle(label).setView(box)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val value = amount.text.toString().replace(",", ".").toDoubleOrNull()
                if (!description.text.isNullOrBlank() && value != null && value > 0) {
                    store.add(description.text.toString(), kind, value, category = cat.text.toString().trim())
                    showFinance()
                }
            }.show()
    }

    private fun budgetDialog() {
        val amount = EditText(this).apply {
            hint = "Limite mensal (ex.: 2500,00)"; inputType = 2 or 4096; setSingleLine()
            if (store.budget() > 0) setText(store.budget().toString().replace(".", ","))
        }
        AlertDialog.Builder(this).setTitle("Orçamento mensal").setView(amount)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val value = amount.text.toString().replace(",", ".").toDoubleOrNull()
                if (value != null && value >= 0) { store.setBudget(value); showFinance() }
            }.show()
    }

    // ── Configurações / PIN / exportar ────────────────────────────
    private fun settings() {
        val choices = mutableListOf<String>("Tema claro/escuro", "Exportar meus dados")
        val acts = mutableListOf { toggleTheme() }
        acts.add { exportData() }
        if (store.hasPin()) { choices.add("Remover PIN"); acts.add { store.clearAllData(); Toast.makeText(this, "Dados e PIN removidos.", Toast.LENGTH_SHORT).show(); showHome() } }
        else { choices.add("Definir PIN de bloqueio"); acts.add { pinDialog() } }
        choices.add("Sobre o Vida"); acts.add { about() }
        AlertDialog.Builder(this).setTitle("Configurações")
            .setItems(choices.toTypedArray()) { _, which -> acts[which]() }.show()
    }

    private fun pinDialog() {
        val pin = EditText(this).apply { hint = "Escolha um PIN numérico"; inputType = 2; setSingleLine(); setPadding(dp(16), dp(8), dp(16), 0) }
        AlertDialog.Builder(this).setTitle("Definir PIN")
            .setMessage("Você digitará esse PIN ao abrir o app.")
            .setView(pin).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                if (pin.text.length >= 4) { store.setPin(pin.text.toString()); Toast.makeText(this, "PIN definido.", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Use pelo menos 4 dígitos.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun toggleTheme() {
        AppCompatDelegate.setDefaultNightMode(
            if (isNight()) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
        recreate()
    }

    private fun isNight(): Boolean = Vida.isDark(this)

    private fun exportData() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TEXT, store.rawJson())
            .putExtra(Intent.EXTRA_SUBJECT, "Vida - exportação de dados")
        startActivity(Intent.createChooser(intent, "Exportar dados do Vida"))
    }

    private fun about() {
        AlertDialog.Builder(this)
            .setTitle("Sobre o Vida")
            .setMessage("Vida 0.1.0 — organização pessoal 100% offline. Seus dados ficam só neste aparelho.")
            .setPositiveButton("Ok", null).show()
    }

    // ── Utilidades ────────────────────────────────────────────────
    private fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    companion object {
        private const val ASSISTANT_SYSTEM =
            "Você é o assistente do Vida, um app de organização pessoal em pt-BR (tarefas, agenda, finanças, notas, metas, hábitos, compras). Responda de forma curta e útil. Para criar/concluir/adiar/excluir ou registrar algo, use uma tool_call e o app pedirá confirmação antes de executar."
    }
}
