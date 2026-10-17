package et.bizassistant.app.platform

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/** Exports documents as shareable text or as a PDF rendered on the device (no network involved). */
object Exporter {
    private const val PAGE_WIDTH = 595 // A4 at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48

    fun shareText(context: Context, title: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sharePdf(context: Context, title: String, body: String) {
        val file = writePdf(context, title, body)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun writePdf(context: Context, title: String, body: String): File {
        val paint = TextPaint().apply { textSize = 11f; isAntiAlias = true }
        val width = PAGE_WIDTH - 2 * MARGIN
        val layout = StaticLayout.Builder.obtain(body, 0, body.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .build()
        val pdf = PdfDocument()
        val usable = PAGE_HEIGHT - 2 * MARGIN
        var line = 0
        var pageNo = 1
        while (line < layout.lineCount) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNo).create())
            val top = layout.getLineTop(line)
            var last = line
            while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= usable) last++
            val canvas = page.canvas
            canvas.save()
            canvas.translate(MARGIN.toFloat(), (MARGIN - top).toFloat())
            canvas.clipRect(0, top, width, layout.getLineBottom(last))
            layout.draw(canvas)
            canvas.restore()
            pdf.finishPage(page)
            line = last + 1
            pageNo++
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // don't keep old exports around
        val safe = title.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(60).ifBlank { "document" }
        val file = File(dir, "$safe.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }
}
