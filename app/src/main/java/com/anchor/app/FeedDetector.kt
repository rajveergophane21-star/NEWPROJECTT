package com.anchor.app

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Recognises when the user is actually inside a short-form video feed (Instagram Reels,
 * YouTube Shorts, TikTok For You, Snapchat Spotlight, Facebook Reels) rather than just
 * somewhere in the host app — so Tame can count and limit the feed without touching messaging.
 *
 * Detection is heuristic and offline: it scans the foreground window's view-id resource names
 * (and a few class-name hints) for the host's known feed markers. It reads only view ids —
 * never text content — and bails out fast (shallow, bounded traversal).
 */
object FeedDetector {

    /** Host apps that have a short-form feed Tame can target. */
    val feedPackages: Set<String> = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",      // TikTok (global)
        "com.ss.android.ugc.trill",      // TikTok (alt)
        "com.snapchat.android",
        "com.facebook.katana"
    )

    /** A human label for the feed of a host package. */
    fun feedLabel(pkg: String): String = when (pkg) {
        "com.instagram.android" -> "Reels"
        "com.google.android.youtube" -> "Shorts"
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "For You"
        "com.snapchat.android" -> "Spotlight"
        "com.facebook.katana" -> "Reels"
        else -> "feed"
    }

    fun isFeedHost(pkg: String?): Boolean = pkg != null && feedPackages.contains(pkg)

    // View-id markers that indicate the feed surface is on screen, per host.
    private val hints: Map<String, List<String>> = mapOf(
        "com.instagram.android" to listOf("clips_viewer", "reel_viewer", "clips_", "reels_tray", "clips_swipe"),
        "com.google.android.youtube" to listOf("reel_player", "reel_recycler", "shorts", "reel_watch"),
        "com.zhiliaoapp.musically" to listOf("feed_", "viewpager", "video_play", "vertical_view_pager"),
        "com.ss.android.ugc.trill" to listOf("feed_", "viewpager", "video_play", "vertical_view_pager"),
        "com.snapchat.android" to listOf("spotlight", "discover_feed", "vertical_paging"),
        "com.facebook.katana" to listOf("reels", "video_home", "reel_")
    )

    private const val MAX_NODES = 220

    /** Best-effort: is [pkg]'s short-form feed currently the visible surface in [root]? */
    fun isFeedOpen(root: AccessibilityNodeInfo?, pkg: String?): Boolean {
        if (root == null || pkg == null) return false
        val keys = hints[pkg] ?: return false
        var seen = 0
        // Bounded breadth-first scan of the live tree.
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && seen < MAX_NODES) {
            val n = queue.removeFirst()
            seen++
            val id = try { n.viewIdResourceName } catch (_: Exception) { null }
            if (id != null) {
                val low = id.lowercase()
                if (keys.any { low.contains(it) }) return true
            }
            val cn = try { n.className?.toString()?.lowercase() } catch (_: Exception) { null }
            if (cn != null && (cn.contains("reel") || cn.contains("shorts"))) return true
            val count = try { n.childCount } catch (_: Exception) { 0 }
            for (i in 0 until count) {
                val c = try { n.getChild(i) } catch (_: Exception) { null }
                if (c != null) queue.add(c)
            }
        }
        return false
    }
}
