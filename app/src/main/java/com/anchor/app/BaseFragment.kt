package com.anchor.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

/** A scrollable fragment that rebuilds its content on each refresh. */
abstract class BaseFragment : Fragment(), Refreshable {
    protected lateinit var col: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        val (sv, c) = Ui.scroll(requireContext()); col = c; return sv
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s); refresh()
    }

    override fun refresh() {
        if (::col.isInitialized) { col.removeAllViews(); render() }
    }

    abstract fun render()
}
