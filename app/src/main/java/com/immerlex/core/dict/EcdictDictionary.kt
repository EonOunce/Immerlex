package com.immerlex.core.dict

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import android.database.sqlite.SQLiteDatabase

class EcdictDictionary private constructor(private val db: SQLiteDatabase) : DictionarySource {

    override fun lookup(word: String): DictEntry? {
        db.rawQuery(
            "SELECT phonetic, translation, definition FROM dict WHERE word = ?",
            arrayOf(word.lowercase()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            return DictEntry(
                word = word.lowercase(),
                phonetic = c.getString(0).orEmpty().asIpa(),
                translation = c.getString(1).orEmpty().oneLine(),
                definition = c.getString(2).orEmpty().oneLine(),
            )
        }
    }

    fun close() = db.close()

    companion object {
        const val ASSET_NAME = "ecdict-lite.db"

        fun openFromAssets(context: Context): EcdictDictionary {
            val target = File(context.getDatabaseFileDir(), ASSET_NAME)
            if (!target.exists() || target.length() == 0L) {
                target.parentFile?.mkdirs()
                context.assets.open(ASSET_NAME).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
            val db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
            return EcdictDictionary(db)
        }

        private fun Context.getDatabaseFileDir(): File = File(filesDir, "dict")
    }
}

/** ECDICT ships glosses with escaped newlines, but the reader embeds them inline — fold them. */
internal fun String.oneLine(): String = replace("\\n", "\n")
    .replace("\n", "；")
    .replace(Regex("；{2,}"), "；")
    .trim('；', ' ')

internal fun String.asIpa(): String = trim().let {
    if (it.isEmpty() || it.startsWith("/")) it else "/$it/"
}
