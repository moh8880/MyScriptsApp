package com.my.scripts

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

data class ScriptItem(
    var title: String,
    var scriptFile: String,
    var runInBackground: Boolean
)

class MainActivity : Activity() {

    private val prefsName = "my_scripts_prefs"
    private val keyScripts = "scripts_json"
    private val shortcutsDir = "/data/data/com.termux/files/home/.shortcuts/"

    private val scripts = mutableListOf<ScriptItem>()
    private lateinit var adapter: ScriptAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadScripts()

        val recycler = findViewById<RecyclerView>(R.id.scriptsRecycler)
        recycler.layoutManager = GridLayoutManager(this, 2)
        adapter = ScriptAdapter(
            items = scripts,
            onClick = { runTermuxScript(it) },
            onLongClick = { index -> showEditOrDeleteDialog(index) }
        )
        recycler.adapter = adapter

        findViewById<View>(R.id.btnAdd).setOnClickListener {
            showAddEditDialog(editIndex = null)
        }
    }

    private fun runTermuxScript(item: ScriptItem) {
        val fullPath = if (item.scriptFile.startsWith("/")) {
            item.scriptFile
        } else {
            shortcutsDir + item.scriptFile
        }

        val intent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", fullPath)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", item.runInBackground)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "جاري تشغيل: ${item.title}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "فعّل صلاحية Run commands in Termux من إعدادات التطبيق",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddEditDialog(editIndex: Int?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_script, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etScript = dialogView.findViewById<EditText>(R.id.etScriptName)
        val cbBg = dialogView.findViewById<CheckBox>(R.id.cbBackground)

        if (editIndex != null) {
            val current = scripts[editIndex]
            etTitle.setText(current.title)
            etScript.setText(current.scriptFile)
            cbBg.isChecked = current.runInBackground
        }

        AlertDialog.Builder(this)
            .setTitle(if (editIndex == null) "إضافة سكريبت جديد" else "تعديل السكريبت")
            .setView(dialogView)
            .setPositiveButton("حفظ") { _, _ ->
                val title = etTitle.text.toString().trim()
                val file = etScript.text.toString().trim()
                if (title.isEmpty() || file.isEmpty()) {
                    Toast.makeText(this, "اكتب اسم الزرار واسم السكريبت", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newItem = ScriptItem(title, file, cbBg.isChecked)
                if (editIndex == null) {
                    scripts.add(newItem)
                    adapter.notifyItemInserted(scripts.size - 1)
                } else {
                    scripts[editIndex] = newItem
                    adapter.notifyItemChanged(editIndex)
                }
                saveScripts()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showEditOrDeleteDialog(index: Int) {
        val options = arrayOf("تعديل", "حذف")
        AlertDialog.Builder(this)
            .setTitle(scripts[index].title)
            .setItems(options) { _, which ->
                if (which == 0) {
                    showAddEditDialog(index)
                } else {
                    scripts.removeAt(index)
                    adapter.notifyItemRemoved(index)
                    saveScripts()
                }
            }
            .show()
    }

    private fun loadScripts() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val raw = prefs.getString(keyScripts, null)
        if (raw == null) {
            scripts.addAll(
                listOf(
                    ScriptItem("Payslip 5", "payslip5.sh", false),
                    ScriptItem("Payslip 2", "payslip2.sh", false),
                    ScriptItem("Stop All", "STOP_ALL.sh", true)
                )
            )
            saveScripts()
            return
        }
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                scripts.add(
                    ScriptItem(
                        title = obj.getString("title"),
                        scriptFile = obj.getString("file"),
                        runInBackground = obj.optBoolean("bg", false)
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun saveScripts() {
        val arr = JSONArray()
        for (item in scripts) {
            val obj = JSONObject()
            obj.put("title", item.title)
            obj.put("file", item.scriptFile)
            obj.put("bg", item.runInBackground)
            arr.put(obj)
        }
        getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(keyScripts, arr.toString())
            .apply()
    }
}

class ScriptAdapter(
    private val items: List<ScriptItem>,
    private val onClick: (ScriptItem) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val file: TextView = v.findViewById(R.id.tvFile)
        val mode: TextView = v.findViewById(R.id.tvMode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.file.text = item.scriptFile
        holder.mode.text = if (item.runInBackground) "● تشغيل في الخلفية" else "● فتح Termux"

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount() = items.size
}
