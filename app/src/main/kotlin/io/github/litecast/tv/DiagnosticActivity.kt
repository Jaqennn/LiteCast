/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast.tv

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.jqssun.airplay.R
import io.github.litecast.diagnostics.Diagnostics

/** Platform widgets only: this page never binds the receiver or loads its native libraries. */
class DiagnosticActivity : Activity() {
    private lateinit var body: TextView
    private lateinit var counter: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private var pages = listOf("")
    private var page = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getInt("page") ?: 0
        val report = Diagnostics.reports.value
        report?.let(Diagnostics::acknowledge)
        val recovery = Diagnostics.needsRecovery()
        val summary = getString(when (report?.code) {
            "LC-NATIVE-LOAD" -> R.string.lc_diag_native
            "LC-START-INTERRUPTED" -> R.string.lc_diag_interrupted
            "LC-JAVA-CRASH" -> R.string.lc_diag_crash
            null -> R.string.lc_diag_empty
            else -> R.string.lc_diag_start
        })
        val fullText = report?.text() ?: Diagnostics.device(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(24), dp(36), dp(24))
            setBackgroundColor(Color.rgb(11, 13, 16))
        }
        counter = label(getString(R.string.lc_diag_title), 24f)
        root.addView(counter)
        root.addView(label(getString(R.string.lc_diag_photo), 16f).apply { setPadding(0, dp(8), 0, dp(12)) })
        body = label("", 18f).apply {
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(3).toFloat(), 1f)
            setBackgroundColor(Color.rgb(22, 26, 32))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { setPadding(0, dp(12), 0, 0) }
        previous = button(R.string.lc_diag_previous) { page--; render() }
        next = button(R.string.lc_diag_next) { page++; render() }
        val copy = button(R.string.lc_diag_copy) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("LiteCast diagnostic", fullText))
            Toast.makeText(this, R.string.lc_diag_copied, Toast.LENGTH_SHORT).show()
        }
        val back = button(R.string.lc_feedback_back) { finish() }
        val buttons = mutableListOf(previous, next, copy, back)
        if (recovery) buttons.add(button(R.string.lc_diag_retry) {
            Diagnostics.allowRetry()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        })
        buttons.forEach { actions.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) }) }
        root.addView(actions)
        setContentView(root)
        back.requestFocus()
        // First pages contain all device details and the error itself; full stack follows.
        val overview = if (report == null) "$summary\n\n$fullText" else
            "$summary\n\n${report.copy(detail = report.detail.lineSequence().take(2).joinToString("\n").take(400)).text()}"
        body.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                pages = paginate(overview) + if (report != null) paginate(report.detail) else emptyList()
                render()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun paginate(text: String): List<String> {
        val width = (body.width - body.paddingLeft - body.paddingRight).coerceAtLeast(1)
        val height = (body.height - body.paddingTop - body.paddingBottom).coerceAtLeast(1)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val rest = text.substring(start)
            val layout = StaticLayout(rest, body.paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, dp(3).toFloat(), false)
            var line = layout.getLineForVertical(height)
            while (line > 0 && layout.getLineBottom(line) > height) line--
            val end = layout.getLineEnd(line).coerceAtLeast(1)
            result.add(rest.substring(0, end))
            start += end
        }
        return result.ifEmpty { listOf("") }
    }

    private fun render() {
        page = page.coerceIn(0, pages.lastIndex)
        body.text = pages[page]
        counter.text = getString(R.string.lc_diag_page, page + 1, pages.size)
        previous.isEnabled = page > 0
        next.isEnabled = page < pages.lastIndex
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page)
        super.onSaveInstanceState(outState)
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(242, 244, 247))
        includeFontPadding = false
    }

    private fun button(text: Int, action: () -> Unit) = Button(this).apply {
        setText(text); textSize = 14f; isAllCaps = false
        setTextColor(Color.WHITE)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(0xFF176C54.toInt()))
            addState(intArrayOf(), shape(0xFF2A303A.toInt()))
        }
        setOnClickListener { action() }
    }

    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
