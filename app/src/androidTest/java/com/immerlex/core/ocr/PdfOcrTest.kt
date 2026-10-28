package com.immerlex.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfOcrTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun writePdf(pages: Int): File {
        val doc = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 72f
            isAntiAlias = true
        }
        repeat(pages) { index ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            page.canvas.drawText("LANTERN $index", 80f, 200f, paint)
            doc.finishPage(page)
        }
        val file = File(context.cacheDir, "ocr-test.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    @Test
    fun rendersEveryPageAndRecognisesItsText() = runBlocking {
        val file = writePdf(pages = 2)
        val progress = mutableListOf<Pair<Int, Int>>()
        val pdf = PdfOcr(MlKitOcr())

        val scan = pdf.scan(context, Uri.fromFile(file)) { done, total -> progress += done to total }

        val ok = scan as PdfScan.Ok
        assertEquals(2, ok.pages)
        assertEquals(2, ok.totalPages)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertTrue("没认出 PDF 上的字：${ok.lines}", ok.lines.any { it.contains("LANTERN", ignoreCase = true) })
        file.delete()
        Unit
    }

    @Test
    fun missingFileIsCannotOpen() = runBlocking {
        val scan = PdfOcr(MlKitOcr()).scan(context, Uri.fromFile(File(context.cacheDir, "nope.pdf")))

        assertEquals(PdfScan.CannotOpen, scan)
    }

    @Test
    fun bundledRecognizerReadsBitmapText() = runBlocking {
        val bitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText(
                "LANTERN",
                40f,
                180f,
                Paint().apply {
                    color = Color.BLACK
                    textSize = 96f
                    isAntiAlias = true
                },
            )
        }

        val lines = MlKitOcr().lines(bitmap)

        assertTrue("识别器返回空：$lines", lines?.any { it.contains("LANTERN", ignoreCase = true) } == true)
    }

    @Test
    fun pdfPageRendersVisibleInk() = runBlocking {
        val file = writePdf(pages = 1)

        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val bitmap = descriptor.use {
            PdfRenderer(it).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
        val ink = (0 until bitmap.width step 4).sumOf { x ->
            (0 until bitmap.height step 4).count { y -> bitmap.getPixel(x, y) != Color.WHITE }
        }
        val lines = MlKitOcr().lines(bitmap)

        assertTrue("渲染出来是白纸：$ink 个非白像素", ink > 0)
        assertTrue("画了字却没认出来：$lines", lines?.any { it.contains("LANTERN", ignoreCase = true) } == true)
    }
}
