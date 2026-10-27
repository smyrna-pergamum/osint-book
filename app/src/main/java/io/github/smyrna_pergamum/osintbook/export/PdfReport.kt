package io.github.smyrna_pergamum.osintbook.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** A block of report text; the PDF writer only knows these, so the content is testable apart from drawing. */
sealed interface Block {
    data class Title(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Para(val text: String, val muted: Boolean = false) : Block
    data class Item(val title: String, val lines: List<String>) : Block
    data class Image(val bitmap: android.graphics.Bitmap, val caption: String) : Block
}

/**
 * FR-12 / AT-08: on-device PDF. Long words and URLs wrap inside the margin and long sections
 * continue on the next page line by line, so nothing runs off the page.
 */
class PdfWriter(private val footer: (page: Int) -> String) {
    private val pageW = 595
    private val pageH = 842
    private val margin = 48f
    private val width = (pageW - 2 * margin).toInt()

    private val title = paint(18f, bold = true)
    private val heading = paint(12.5f, bold = true)
    private val body = paint(9.5f)
    private val bold = paint(9.5f, bold = true)
    private val muted = paint(8.5f).apply { color = Color.rgb(90, 96, 100) }
    private val rule = Paint().apply { color = Color.rgb(200, 205, 208); strokeWidth = 0.6f }

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNo = 0

    fun write(blocks: List<Block>, out: OutputStream) {
        doc = PdfDocument()
        try {
            newPage()
            for (b in blocks) when (b) {
                is Block.Title -> { text(b.text, title); y += 6 }
                is Block.Heading -> { ensure(40f); y += 10; text(b.text, heading); canvas!!.drawLine(margin, y, pageW - margin, y, rule); y += 6 }
                is Block.Para -> { text(b.text, if (b.muted) muted else body); y += 4 }
                is Block.Image -> {
                    val w = width.toFloat()
                    val h = w * b.bitmap.height / b.bitmap.width
                    ensure(h + 24f)
                    canvas!!.drawBitmap(b.bitmap, null, android.graphics.RectF(margin, y, margin + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                    y += h + 4
                    text(b.caption, muted)
                    y += 6
                }
                is Block.Item -> {
                    ensure(30f)
                    text(b.title, bold)
                    b.lines.forEach { text(it, muted, indent = 10f) }
                    y += 6
                }
            }
            finishPage()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun paint(size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = Color.rgb(20, 24, 26)
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun newPage() {
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        canvas = page!!.canvas
        y = margin
    }

    private fun finishPage() {
        val c = canvas ?: return
        c.drawText(footer(pageNo), margin, pageH - margin / 2, muted)
        doc.finishPage(page)
        page = null; canvas = null
    }

    private fun bottom() = pageH - margin - 12

    private fun ensure(space: Float) {
        if (y + space > bottom()) { finishPage(); newPage() }
    }

    private fun text(s: String, p: TextPaint, indent: Float = 0f) {
        val w = (width - indent).toInt()
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .build()
        var line = 0
        while (line < layout.lineCount) {
            if (y + (layout.getLineBottom(line) - layout.getLineTop(line)) > bottom()) { finishPage(); newPage() }
            // Draw as many whole lines as fit on this page.
            var last = line
            while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - layout.getLineTop(line)) <= bottom()) last++
            val top = layout.getLineTop(line).toFloat()
            val h = layout.getLineBottom(last) - top
            val c = canvas!!
            c.save()
            c.translate(margin + indent, y - top)
            c.clipRect(0f, top, w.toFloat(), top + h)
            layout.draw(c)
            c.restore()
            y += h
            line = last + 1
        }
    }
}
