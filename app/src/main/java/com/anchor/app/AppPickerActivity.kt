package com.anchor.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anchor.app.databinding.ActivityAppPickerBinding

class AppItem(val pkg: String, val label: String, val icon: Drawable)

/** Lists installed launchable apps for multi-select. Returns the chosen packages. */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var b: ActivityAppPickerBinding
    private val selected = linkedSetOf<String>()
    private val items = mutableListOf<AppItem>()        // all apps
    private val shown = mutableListOf<AppItem>()         // filtered view
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(b.root)

        intent.getStringArrayListExtra(EXTRA_SELECTED)?.let { selected.addAll(it) }

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.done.setOnClickListener {
            setResult(RESULT_OK, Intent().putStringArrayListExtra(EXTRA_SELECTED, ArrayList(selected)))
            finish()
        }
        b.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = filter(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        loadApps()
    }

    private fun filter(q: String) {
        val query = q.trim().lowercase()
        shown.clear()
        if (query.isEmpty()) shown.addAll(items)
        else shown.addAll(items.filter { it.label.lowercase().contains(query) })
        adapter.notifyDataSetChanged()
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(launch, 0)
            val seen = hashSetOf<String>()
            val loaded = mutableListOf<AppItem>()
            for (ri in resolved) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                val label = ri.loadLabel(pm).toString()
                val icon = ri.loadIcon(pm)
                loaded.add(AppItem(pkg, label, icon))
            }
            loaded.sortBy { it.label.lowercase() }
            Handler(Looper.getMainLooper()).post {
                items.clear(); items.addAll(loaded)
                shown.clear(); shown.addAll(loaded)
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val name: TextView = v.findViewById(R.id.name)
            val check: CheckBox = v.findViewById(R.id.check)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))
        override fun getItemCount() = shown.size
        override fun onBindViewHolder(h: VH, position: Int) {
            val item = shown[position]
            h.icon.setImageDrawable(item.icon)
            h.name.text = item.label
            h.check.isChecked = selected.contains(item.pkg)
            h.itemView.setOnClickListener {
                if (selected.contains(item.pkg)) selected.remove(item.pkg) else selected.add(item.pkg)
                h.check.isChecked = selected.contains(item.pkg)
            }
        }
    }

    companion object { const val EXTRA_SELECTED = "selected" }
}
