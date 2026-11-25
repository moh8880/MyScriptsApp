package com.my.scripts

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
        window.statusBarColor = Color.parseColor("#090D16")
        window.navigationBarColor = Color.parseColor("#111827")
        setContentView(R.layout.activity_main)

        loadScripts()

        val recycler = findViewById<RecyclerView>(R.id.scriptsRecycler)
        recycler.layoutManager = GridLayoutManager(this, 2)
        adapter = ScriptAdapter(
            items = scripts,
            onClick = { item, view ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
                runTermuxScript(item)
            },
            onLongClick = { index -> showEditOrDeleteDialog(index) }
        )
        recycler.adapter = adapter

        findViewById<View>(R.id.btnAdd).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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
    private val onClick: (ScriptItem, View) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.VH>() {

    private val accentColors = listOf(
        "#38BDF8", // Sky
        "#34D399", // Emerald
        "#A78BFA", // Violet
        "#FBBF24", // Amber
        "#FB7185", // Rose
        "#2DD4BF"  // Teal
    )

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val file: TextView = v.findViewById(R.id.tvFile)
        val mode: TextView = v.findViewById(R.id.tvMode)
        val iconContainer: FrameLayout = v.findViewById(R.id.iconContainer)
        val ivIcon: ImageView = v.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val accentHex = accentColors[position % accentColors.size]
        val accentColor = Color.parseColor(accentHex)

        holder.title.text = item.title
        holder.file.text = item.scriptFile

        // Colored icon badge
        val iconBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)))
        }
        holder.iconContainer.background = iconBg
        holder.ivIcon.setColorFilter(accentColor)

        // Status pill
        val modeColor = if (item.runInBackground) Color.parseColor("#34D399") else Color.parseColor("#38BDF8")
        holder.mode.text = if (item.runInBackground) "خلفي" else "تيرمكس"
        holder.mode.setTextColor(modeColor)
        val modeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(Color.argb(35, Color.red(modeColor), Color.green(modeColor), Color.blue(modeColor)))
        }
        holder.mode.background = modeBg

        holder.itemView.setOnClickListener { onClick(item, it) }
        holder.itemView.setOnLongClickListener {
            onLongClick(holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount() = items.size
}
