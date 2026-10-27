package com.immerlex.app

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a user-picked text file; returns null when the provider refuses or the stream fails. */
suspend fun Context.readPlainText(uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
    } catch (e: IOException) {
        null
    }
}
