package com.anchor.app

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

/** A scrollable fragment that rebuilds its content on each refresh. */
abstract class BaseFragment : Fragment(), Refreshable {
    protected lateinit var col: LinearLayout
    private var firstRender = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        val (sv, c) = Ui.scroll(requireContext()); col = c
        col.layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
        return sv
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s); refresh()
    }

    /** Rebuilds content. The first build per visit rises in with a stagger. */
    override fun refresh() {
        if (!::col.isInitialized) return
        col.removeAllViews(); render()
        if (firstRender) { Ui.stagger(col); firstRender = false }
    }

    abstract fun render()
}
