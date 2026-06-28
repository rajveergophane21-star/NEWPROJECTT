package com.anchor.app

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
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

        b.pickTitle.typeface = Ui.serif(this)
        b.done.typeface = Ui.serif(this); b.done.setTextColor(Ui.INK)
        b.list.layoutManager = GridLayoutManager(this, 4)
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
                filter(b.search.text?.toString() ?: "")   // honour a query typed while loading
            }
        }.start()
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val slot: FrameLayout = v.findViewById(R.id.slot)
            val icon: ImageView = v.findViewById(R.id.icon)
            val name: TextView = v.findViewById(R.id.name)
            val check: ImageView = v.findViewById(R.id.check)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))
        override fun getItemCount() = shown.size

        private fun paint(h: VH, on: Boolean) {
            h.slot.background = ContextCompat.getDrawable(h.itemView.context, if (on) R.drawable.slot_sel else R.drawable.slot)
            h.check.visibility = if (on) View.VISIBLE else View.GONE
            h.name.setTextColor(if (on) Ui.ACC_TEXT else Ui.TEXT)
        }
        override fun onBindViewHolder(h: VH, position: Int) {
            val item = shown[position]
            h.icon.setImageDrawable(item.icon)
            h.name.text = item.label
            paint(h, selected.contains(item.pkg))
            h.itemView.setOnClickListener {
                // Re-resolve the item from the holder's current position — a captured
                // reference can point at the wrong row after a fast scroll/recycle.
                val pos = h.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val it2 = shown[pos]
                val nowOn = !selected.contains(it2.pkg)
                if (nowOn) selected.add(it2.pkg) else selected.remove(it2.pkg)
                Ui.haptic(it); paint(h, nowOn)
            }
        }
    }

    companion object { const val EXTRA_SELECTED = "selected" }
}
