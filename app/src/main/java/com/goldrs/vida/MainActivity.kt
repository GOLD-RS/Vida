package com.goldrs.vida

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var store: LocalStore
    private lateinit var content: LinearLayout
    private val ink = Color.rgb(27, 29, 41)
    private val muted = Color.rgb(103, 107, 124)
    private val primary = Color.rgb(82, 103, 217)
    private val surface = Color.rgb(247, 248, 252)

    override fun onCreate(state: Bundle?) { super.onCreate(state); store = LocalStore(this); showHome() }

    private fun frame(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(surface) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 12) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomBar(), LinearLayout.LayoutParams(-1, 68))
        return root
    }

    private fun bottomBar(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE)
        listOf("⌂\nInício", "▣\nAgenda", "✓\nTarefas", "R$\nFinanças", "•••\nMais").forEach { label ->
            addView(TextView(this@MainActivity).apply {
                text = label; textSize = 11f; gravity = Gravity.CENTER; setTextColor(muted); setPadding(4, 0, 4, 0)
                setOnClickListener { when (label.substringAfter("\n")) { "Início" -> showHome(); "Agenda" -> showList("Agenda", "event"); "Tarefas" -> showList("Tarefas", "task"); "Finanças" -> showFinance(); "Mais" -> showMore() } }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun header(title: String, subtitle: String? = null) {
        content.removeAllViews()
        content.addView(TextView(this).apply { this.text = title; textSize = 28f; setTextColor(ink); setTypeface(null, 1) })
        subtitle?.let { value -> content.addView(TextView(this).apply { this.text = value; textSize = 14f; setTextColor(muted); setPadding(0, 5, 0, 18) }) }
    }

    private fun section(label: String) { content.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(muted); setTypeface(null, 1); setPadding(0, 10, 0, 8) }) }

    private fun card(label: String, value: String, accent: Int = primary, action: (() -> Unit)? = null) {
        val card = MaterialCardView(this).apply { radius = 20f; cardElevation = 0f; setCardBackgroundColor(Color.WHITE); action?.let { setOnClickListener { it() } } }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(17, 15, 17, 15) }
        box.addView(TextView(this).apply { text = label; textSize = 13f; setTextColor(muted) })
        box.addView(TextView(this).apply { text = value; textSize = 19f; setTextColor(ink); setPadding(0, 7, 0, 0) })
        card.addView(box); content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 10) })
    }

    private fun actionButton(text: String, action: () -> Unit) { content.addView(MaterialButton(this).apply { this.text = text; setOnClickListener { action() } }) }

    private fun showHome() {
        setContentView(frame())
        val date = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(Date()).replaceFirstChar { it.uppercase() }
        header("Olá, GOLD RS 👋", date)
        val pending = store.items().count { it.kind == "task" && !it.done }
        val events = store.items().count { it.kind == "event" && !it.done }
        section("VISÃO GERAL")
        card("Foco de hoje", if (pending == 0) "Tudo em dia 🎉" else "$pending tarefas para concluir") { showList("Tarefas", "task") }
        card("Agenda", if (events == 0) "Nenhum compromisso cadastrado" else "$events compromisso(s) próximo(s)") { showList("Agenda", "event") }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(metric("Metas", "0%"), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, 6, 0) })
        row.addView(metric("Hábitos", "0/0"), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(6, 0, 0, 0) })
        content.addView(row)
        section("ACESSO RÁPIDO")
        actionButton("＋  Nova tarefa") { inputDialog("Nova tarefa", "task") }
        actionButton("＋  Novo compromisso") { inputDialog("Novo compromisso", "event") }
        actionButton("＋  Registrar despesa") { inputDialog("Nova despesa", "expense") }
    }

    private fun metric(label: String, value: String): View = MaterialCardView(this).apply {
        radius = 18f; cardElevation = 0f; setCardBackgroundColor(Color.WHITE)
        addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 13, 14, 13); addView(TextView(context).apply { text = label; textSize = 12f; setTextColor(muted) }); addView(TextView(context).apply { text = value; textSize = 20f; setTextColor(primary); setPadding(0, 5, 0, 0) }) })
    }

    private fun showList(name: String, kind: String) {
        setContentView(frame()); header(name, "Organizado, local e disponível sem internet")
        val items = store.items().filter { it.kind == kind }
        section(if (items.isEmpty()) "NADA POR AQUI" else "SEUS ITENS")
        if (items.isEmpty()) content.addView(TextView(this).apply { text = "Ainda não há itens. Adicione o primeiro usando o botão abaixo."; textSize = 16f; setTextColor(muted); setPadding(0, 0, 0, 12) })
        items.forEach { item -> content.addView(CheckBox(this).apply { text = item.title; isChecked = item.done; textSize = 16f; setPadding(2, 9, 2, 9); setOnClickListener { store.toggle(item.id); showList(name, kind) } }) }
        actionButton("＋  Adicionar") { inputDialog(if (kind == "event") "Novo compromisso" else "Nova tarefa", kind) }
    }

    private fun showFinance() {
        setContentView(frame()); header("Finanças", "Uma visão simples do seu dinheiro")
        section("ESTE MÊS"); card("Saldo disponível", "R$ 0,00"); card("Despesas registradas", "Nenhuma ainda"); card("Orçamento", "Configure seu limite mensal")
        section("AÇÕES"); actionButton("＋  Registrar despesa") { inputDialog("Nova despesa", "expense") }; actionButton("＋  Registrar receita") { inputDialog("Nova receita", "income") }
    }

    private fun showMore() {
        setContentView(frame()); header("Organizar", "Tudo que você precisa, em um só lugar")
        section("MÓDULOS")
        module("📝", "Notas", "Ideias, registros e informações importantes") { showList("Notas", "note") }
        module("🛒", "Lista de compras", "Produtos, quantidades e categorias") { showList("Compras", "shopping") }
        module("🎯", "Metas", "Acompanhe o que importa para você") { showList("Metas", "goal") }
        module("🔁", "Hábitos", "Construa uma rotina sustentável") { showList("Hábitos", "habit") }
        module("⚙", "Configurações", "Segurança, tema e permissões da IA") { settings() }
        section("ASSISTENTE"); actionButton("✦  Conversar com a IA") { showAssistant() }
    }

    private fun module(icon: String, name: String, description: String, action: () -> Unit) {
        val c = MaterialCardView(this).apply { radius = 18f; cardElevation = 0f; setCardBackgroundColor(Color.WHITE); setOnClickListener { action() } }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(15, 13, 15, 13) }
        row.addView(TextView(this).apply { text = icon; textSize = 24f; setPadding(0, 0, 14, 0) })
        row.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(TextView(this@MainActivity).apply { text = name; textSize = 17f; setTextColor(ink) }); addView(TextView(this@MainActivity).apply { text = description; textSize = 12f; setTextColor(muted); setPadding(0, 4, 0, 0) }) })
        c.addView(row); content.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 9) })
    }

    private fun settings() { AlertDialog.Builder(this).setTitle("Configurações").setItems(arrayOf("Bloqueio e privacidade", "Permissões da IA", "Exportar meus dados", "Tema escuro")) { _, _ -> }.show() }

    private fun showAssistant() {
        setContentView(frame()); header("Assistente", "Ajuda prática, com confirmação antes de agir")
        content.addView(TextView(this).apply { text = "Olá! Posso organizar suas tarefas e compromissos. A integração com um provedor de IA será adicionada sem comprometer o modo offline."; textSize = 16f; setTextColor(ink); setPadding(0, 0, 0, 18) })
        listOf("Organizar minhas tarefas de hoje", "Planejar minha semana", "Analisar meus gastos").forEach { suggestion -> content.addView(MaterialButton(this).apply { text = suggestion; setOnClickListener { assistantUnavailable() } }) }
        val input = EditText(this).apply { hint = "Escreva uma mensagem…" }; content.addView(input)
        actionButton("Enviar") { assistantUnavailable() }
    }

    private fun assistantUnavailable() { AlertDialog.Builder(this).setTitle("IA ainda não configurada").setMessage("O app continua funcionando normalmente offline. Nenhuma ação foi executada e seus dados permanecem no aparelho.").setPositiveButton("Entendi", null).show() }

    private fun quickAdd() { inputDialog("Nova tarefa", "task") }
    private fun inputDialog(label: String, kind: String) {
        val input = EditText(this).apply { hint = label; setSingleLine() }
        AlertDialog.Builder(this).setTitle(label).setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
            if (input.text.isNotBlank()) { store.add(input.text.toString(), kind); when (kind) { "task" -> showList("Tarefas", kind); "event" -> showList("Agenda", kind); "expense", "income" -> showFinance(); "note", "shopping", "goal", "habit" -> showMore() } }
        }.show()
    }
}
