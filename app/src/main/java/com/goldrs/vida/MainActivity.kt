package com.goldrs.vida

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.content.Context
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var store: LocalStore
    private lateinit var content: LinearLayout
    private val blue = Color.rgb(82, 103, 217)
    override fun onCreate(state: Bundle?) { super.onCreate(state); store = LocalStore(this); showHome() }
    private fun base(): LinearLayout { val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(247,248,252)) }; content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,18,20,12) }; root.addView(content, LinearLayout.LayoutParams(-1,0,1f)); root.addView(nav(), LinearLayout.LayoutParams(-1,64)); return root }
    private fun nav(): LinearLayout { val bar = LinearLayout(this).apply { gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE) }; listOf("Início","Agenda","Tarefas","Finanças","IA").forEach { label -> val b = TextView(this).apply { text = label; textSize=12f; gravity=17; setTextColor(Color.DKGRAY); setPadding(8,0,8,0); setOnClickListener { when(label) { "Início" -> showHome(); "Tarefas" -> showList("Tarefas","task"); "Agenda" -> showList("Agenda","event"); "Finanças" -> showFinance(); "IA" -> showAssistant() } } }; bar.addView(b, LinearLayout.LayoutParams(0,-1,1f)) }; return bar }
    private fun title(text: String, sub: String? = null) { content.removeAllViews(); content.addView(TextView(this).apply { this.text=text; textSize=28f; setTextColor(Color.rgb(27,29,41)); setTypeface(null,1) }); sub?.let { subtitle -> content.addView(TextView(this).apply { text=subtitle; textSize=14f; setTextColor(Color.GRAY); setPadding(0,4,0,16) }) } }
    private fun card(label:String, value:String, action:()->Unit = {}) { val c=MaterialCardView(this).apply { radius=22f; setCardBackgroundColor(Color.WHITE); setOnClickListener { action() }; cardElevation=0f }; val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,16,18,16) }; box.addView(TextView(this).apply { text=label; textSize=13f; setTextColor(Color.GRAY) }); box.addView(TextView(this).apply { text=value; textSize=19f; setTextColor(Color.rgb(27,29,41)); setPadding(0,8,0,0) }); c.addView(box); content.addView(c, LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,0,12) }) }
    private fun showHome() { setContentView(base()); title("Olá, GOLD RS 👋", SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt","BR")).format(Date()).replaceFirstChar { it.uppercase() }); card("Hoje", "Seu painel pessoal", { showList() }); card("Tarefas pendentes", "${store.items().count { it.kind=="task" && !it.done }} para concluir", { showList("Tarefas","task") }); card("Próximos compromissos", "${store.items().count { it.kind=="event" && !it.done }} na agenda", { showList("Agenda","event") }); val quick=MaterialButton(this).apply { text="＋  Adicionar rápido"; setOnClickListener { quickAdd() } }; content.addView(quick) }
    private fun showList(name:String="Tarefas", kind:String="task") { setContentView(base()); title(name,"Funciona offline e salva tudo neste aparelho"); store.items().filter { it.kind==kind }.forEach { item -> val row=CheckBox(this).apply { text=item.title; isChecked=item.done; textSize=16f; setPadding(4,10,4,10); setOnClickListener { store.toggle(item.id); showList(name,kind) } }; content.addView(row) }; val b=MaterialButton(this).apply { text="＋  Adicionar"; setOnClickListener { inputDialog(if(kind=="event") "Novo compromisso" else "Nova tarefa",kind) } }; content.addView(b) }
    private fun showFinance() { setContentView(base()); title("Finanças","Resumo local — nenhum dado sai do aparelho"); card("Saldo do mês","R$ 0,00"); card("Despesas registradas","Nenhuma ainda"); val b=MaterialButton(this).apply { text="＋  Registrar despesa"; setOnClickListener { inputDialog("Nova despesa","expense") } }; content.addView(b) }
    private fun showAssistant() { setContentView(base()); title("Assistente","Ações importantes sempre pedem confirmação"); val info=TextView(this).apply { text="A IA poderá organizar tarefas, eventos, notas e finanças quando um provedor for configurado. Nesta primeira versão, o modo offline continua disponível."; textSize=16f; setTextColor(Color.DKGRAY); setPadding(0,0,0,18) }; content.addView(info); val input=EditText(this).apply { hint="Diga o que você precisa…" }; content.addView(input); content.addView(MaterialButton(this).apply { text="Enviar"; setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Ação não executada").setMessage("O assistente offline recebeu sua mensagem, mas ainda não há um provedor de IA configurado. Seus dados continuam protegidos.").setPositiveButton("Entendi",null).show() } }) }
    private fun quickAdd() { AlertDialog.Builder(this).setTitle("Adicionar rápido").setItems(arrayOf("Tarefa","Compromisso","Despesa")) { _,which -> inputDialog(arrayOf("Nova tarefa","Novo compromisso","Nova despesa")[which],arrayOf("task","event","expense")[which]) }.show() }
    private fun inputDialog(label:String,kind:String) { val input=EditText(this).apply { hint=label; setSingleLine() }; AlertDialog.Builder(this).setTitle(label).setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Salvar") { _,_ -> if(input.text.isNotBlank()) { store.add(input.text.toString(),kind); if(kind=="task") showList("Tarefas","task") else if(kind=="event") showList("Agenda","event") else showFinance() } }.show() }
}
