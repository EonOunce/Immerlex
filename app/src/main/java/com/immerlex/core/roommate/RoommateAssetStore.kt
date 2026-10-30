package com.immerlex.core.roommate

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.immerlex.core.db.RoommateAssetDao
import com.immerlex.core.db.RoommateAssetEntity
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportSummary(
    val seen: Int = 0,
    val added: Int = 0,
    val bytes: Long = 0,
    val failed: Int = 0,
)

/**
 * 素材文件按内容哈希落盘：`filesDir/roommate/<sha256 前 32 位>.gif`。
 * 从别的文件夹、别的手机再导一次同一条 GIF，只会命中同一行，不会重复占空间；
 * 路线只记住这个哈希，不写任何机器上的绝对路径。
 */
class RoommateAssetStore(
    private val context: Context,
    private val assets: RoommateAssetDao,
) {

    fun directory(): File = File(context.filesDir, "roommate").apply { mkdirs() }

    fun fileOf(assetId: String): File = File(directory(), "$assetId.gif")

    fun missing(assets: List<RoommateAssetEntity>): List<String> =
        assets.filterNot { fileOf(it.assetId).exists() }.map { it.assetId }

    suspend fun import(uri: Uri, nowSec: Long): ImportSummary = withContext(Dispatchers.IO) {
        val docs = mutableListOf<Doc>()
        collect(uri, DocumentsContract.getTreeDocumentId(uri), depth = 0, into = docs)
        val gifs = docs.filter { RoommateAssets.isGif(it.name) }
        var added = 0
        var bytes = 0L
        var failed = 0
        val dir = directory()
        for (doc in gifs) {
            val entity = runCatching { copyIn(uri, doc, dir, nowSec) }.getOrNull()
            if (entity == null) {
                failed++
                continue
            }
            bytes += entity.bytes
            if (assets.insertIgnore(entity) != -1L) added++
        }
        ImportSummary(seen = gifs.size, added = added, bytes = bytes, failed = failed)
    }

    /** 返回待入库的一行；文件已存在时也返回该行（唯一主键保证不会重复占空间）。 */
    private fun copyIn(treeUri: Uri, doc: Doc, dir: File, nowSec: Long): RoommateAssetEntity? {
        val tmp = File.createTempFile("incoming", ".tmp", dir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = context.contentResolver.openInputStream(documentUri(treeUri, doc.id))?.use { input ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                    }
                }
                true
            } ?: false
            if (!copied) return null
            val assetId = digest.digest().joinToString("") { "%02x".format(it) }.take(32)
            val target = File(dir, "$assetId.gif")
            if (!target.exists() && !tmp.renameTo(target)) return null
            return RoommateAssetEntity(
                assetId = assetId,
                fileName = doc.name,
                tag = RoommateAssets.tagOf(doc.name),
                bytes = target.length(),
                importedAtSec = nowSec,
            )
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun collect(treeUri: Uri, documentId: String, depth: Int, into: MutableList<Doc>) {
        if (depth > 2) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1).orEmpty()
                val mime = cursor.getString(2).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collect(treeUri, id, depth + 1, into)
                } else {
                    into += Doc(id, name)
                }
            }
        }
    }

    private data class Doc(val id: String, val name: String)
}
