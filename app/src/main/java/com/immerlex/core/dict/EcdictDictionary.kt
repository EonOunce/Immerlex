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

    override fun search(needle: String, limit: Int): List<DictEntry> {
        if (limit <= 0) return emptyList()
        val query = DictionarySearch.of(needle) ?: return emptyList()
        val column = if (query.column == SearchColumn.WORD) "word" else "translation"
        // 预分配量不跟着调用方走：limit 可能是个很大的数，按它开数组会当场 OOM。
        val found = ArrayList<DictEntry>(limit.coerceAtMost(PREALLOC))
        db.rawQuery(
            "SELECT word, phonetic, translation, definition FROM dict " +
                // $column 出自枚举，不是用户输入；通配符已在 DictionarySearch 里转过义。
                "WHERE $column LIKE ? ESCAPE '\\' ORDER BY length(word), frq LIMIT ?",
            arrayOf(query.pattern, limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                found += DictEntry(
                    word = c.getString(0).orEmpty(),
                    phonetic = c.getString(1).orEmpty().asIpa(),
                    translation = c.getString(2).orEmpty().oneLine(),
                    definition = c.getString(3).orEmpty().oneLine(),
                )
            }
        }
        return found
    }

    fun close() = db.close()

    companion object {
        const val ASSET_NAME = "ecdict-lite.db"

        private const val PREALLOC = 64

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
