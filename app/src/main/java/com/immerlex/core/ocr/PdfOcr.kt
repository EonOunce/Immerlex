package com.immerlex.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** One PDF scan's outcome; [Empty] means the pages rendered but no text came back. */
sealed interface PdfScan {
    data class Ok(val lines: List<String>, val pages: Int, val totalPages: Int) : PdfScan {
        val truncated: Boolean get() = pages < totalPages
    }

    /** 加密、损坏，或选中的不是可渲染的 PDF——都归到这里，UI 有一句对应的话。 */
    data object CannotOpen : PdfScan

    data object Empty : PdfScan
}

/**
 * 词库的 PDF 导入：逐页渲染成位图，再走截图导入那条 ML Kit 通道，不另建一条识别链路。
 * PdfRenderer 要求可 seek 的文件描述符，且不保证线程安全，所以整个渲染过程钉在一条线程上。
 */
class PdfOcr(
    private val ocr: OcrEngine,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) {
    suspend fun scan(
        context: Context,
        uri: Uri,
        onPage: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): PdfScan {
        val descriptor = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }
            .getOrNull() ?: return PdfScan.CannotOpen
        val thread = Executors.newSingleThreadExecutor()
        try {
            return descriptor.use {
                withContext(thread.asCoroutineDispatcher()) {
                    val renderer = runCatching { PdfRenderer(descriptor) }.getOrNull()
                        ?: return@withContext PdfScan.CannotOpen
                    renderer.use rendererUse@ {
                        val total = renderer.pageCount
                        if (total <= 0) return@rendererUse PdfScan.Empty
                        val pages = minOf(total, maxPages)
                        val lines = ArrayList<String>()
                        for (index in 0 until pages) {
                            val page = runCatching { renderer.openPage(index) }.getOrNull() ?: continue
                            val pageLines = page.use pageUse@ { open ->
                                val bitmap = runCatching { render(open) }.getOrNull()
                                    ?: return@pageUse emptyList<String>()
                                try {
                                    ocr.lines(bitmap).orEmpty()
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                            lines += pageLines
                            onPage(index + 1, pages)
                        }
                        if (lines.isEmpty()) PdfScan.Empty
                        else PdfScan.Ok(lines, pages = pages, totalPages = total)
                    }
                }
            }
        } finally {
            thread.shutdown()
        }
    }

    private fun render(page: PdfRenderer.Page): Bitmap {
        val scale = (TARGET_WIDTH_PX.toFloat() / page.width).coerceIn(1f, MAX_SCALE)
        val bitmap = Bitmap.createBitmap(
            (page.width * scale).toInt().coerceAtLeast(1),
            (page.height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            // 无底色页在透明位图上仍是一层透明像素，ML Kit 会把黑字当黑底，必须先铺白纸。
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        } catch (e: RuntimeException) {
            bitmap.recycle()
            throw e
        }
        return bitmap
    }

    companion object {
        /** 词表 PDF 通常只有几页；再多就是整本书，OCR 会等到人放弃。 */
        const val DEFAULT_MAX_PAGES = 20
        private const val TARGET_WIDTH_PX = 1500
        private const val MAX_SCALE = 3f
    }
}
