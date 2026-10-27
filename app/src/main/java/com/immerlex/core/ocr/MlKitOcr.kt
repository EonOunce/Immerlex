package com.immerlex.core.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Recognised lines, one per detected text line; null when recognition itself failed. */
interface OcrEngine {
    suspend fun lines(context: Context, uri: Uri): List<String>?
}

/**
 * ML Kit bundled recogniser (Chinese + Latin), so screenshots of word lists with
 * Chinese glosses survive; no Play Services download needed at runtime.
 */
class MlKitOcr : OcrEngine {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun lines(context: Context, uri: Uri): List<String>? =
        withContext(Dispatchers.IO) {
            val text = runCatching {
                recognizer.process(InputImage.fromFilePath(context, uri)).await().text
            }.getOrNull() ?: return@withContext null
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
}
