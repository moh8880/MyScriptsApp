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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

data class ScriptItem(
    var title: String,
    var scriptFile: String,
    var runInBackground: Boolean
)

class MainActivity : Activity() {

    private val prefsName = "my_scripts_prefs"
    private val keyScripts = "scripts_json"
    private val keyCleanedV5 = "cleaned_kill_card_v5"
    private val shortcutsDir = "/data/data/com.termux/files/home/.shortcuts/"

    private val scripts = mutableListOf<ScriptItem>()
    private lateinit var adapter: ScriptAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A1A05")
        window.navigationBarColor = Color.parseColor("#000000")
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
            onMoreClick = { index -> showEditOrDeleteDialog(index) }
        )
        recycler.adapter = adapter

        // Enable Drag & Drop reordering on long press
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(dragFlags, 0) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                if (from < to) {
                    for (i in from until to) {
                        Collections.swap(scripts, i, i + 1)
                    }
                } else {
                    for (i in from downTo to + 1) {
                        Collections.swap(scripts, i, i - 1)
                    }
                }
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    viewHolder.itemView.animate().scaleX(1.06f).scaleY(1.06f).alpha(0.92f).setDuration(120).start()
                    viewHolder.itemView.elevation = 16f
                }
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
                viewHolder.itemView.elevation = 4f
                saveScripts()
            }
        })
        touchHelper.attachToRecyclerView(recycler)

        findViewById<View>(R.id.btnAdd).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAddEditDialog(editIndex = null)
        }

        findViewById<View>(R.id.btnKillAll).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            killBackgroundAppsAndTermux()
        }
    }

    private fun killBackgroundAppsAndTermux() {
        val shellCmd = "RISH=\"/data/data/com.termux/files/home/rish\"; " +
            "if [ -x \"\$RISH\" ]; then " +
            "\"\$RISH\" -c 'for pkg in \$(pm list packages -3 --user 0 | cut -d: -f2); do " +
            "case \"\$pkg\" in *shizuku*|*registar*|com.my.scripts|com.termux*) ;; " +
            "*) am force-stop --user 0 \"\$pkg\" ;; esac; done'; " +
            "fi; kill -9 -1"

        val intent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", shellCmd))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "جاري إغلاق تطبيقات الخلفية و Termux...", Toast.LENGTH_SHORT).show()
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
        if (index < 0 || index >= scripts.size) return
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
            prefs.edit().putBoolean(keyCleanedV5, true).apply()
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

        if (!prefs.getBoolean(keyCleanedV5, false)) {
            val removed = scripts.removeAll { it.scriptFile.equals("kill_termux.sh", ignoreCase = true) }
            if (removed) saveScripts()
            prefs.edit().putBoolean(keyCleanedV5, true).apply()
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
    private val onMoreClick: (Int) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cardRoot: LinearLayout = v.findViewById(R.id.cardRoot)
        val dotHalo: FrameLayout = v.findViewById(R.id.dotHalo)
        val dotCore: View = v.findViewById(R.id.dotCore)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val file: TextView = v.findViewById(R.id.tvFile)
        val mode: TextView = v.findViewById(R.id.tvMode)
        val btnMore: TextView = v.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.title.text = item.title
        holder.file.text = item.scriptFile
        holder.cardRoot.setBackgroundResource(R.drawable.card_bg_green)
        holder.title.setTextColor(Color.parseColor("#FFFFFF"))
        holder.btnMore.setTextColor(Color.parseColor("#D1D5DB"))

        val isLime = (position % 2 == 0)
        val dotColor = if (isLime) Color.parseColor("#B8F528") else Color.parseColor("#FACC15")
        holder.dotHalo.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(55, Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor)))
        }
        holder.dotCore.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }

        if (item.runInBackground) {
            holder.mode.text = "خلفي"
            holder.mode.setTextColor(Color.parseColor("#D1D5DB"))
            holder.mode.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setColor(Color.argb(60, 148, 163, 184))
            }
        } else {
            holder.mode.text = "تيرمكس"
            holder.mode.setTextColor(Color.parseColor("#D4FF3B"))
            holder.mode.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setColor(Color.argb(60, 163, 230, 53))
            }
        }

        holder.itemView.setOnClickListener { onClick(item, it) }
        holder.btnMore.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onMoreClick(pos)
            }
        }
    }

    override fun getItemCount() = items.size
}
