package app.chompass.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the backup document inside a user-picked folder (SAF tree).
 * The folder is granted once in Settings; the file name is fixed so a
 * folder-sync tool such as Syncthing sees one stable document.
 */
object LocalBackupFile {
    const val FILE_NAME = "Chompass-sync.json"

    suspend fun folderName(context: Context, treeUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    /** Finds [FILE_NAME] in the tree, creating it when absent. Null when the tree no longer grants access. */
    suspend fun findOrCreate(context: Context, treeUri: Uri): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val parentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val foundId = resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                var id: String? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == FILE_NAME) {
                        id = cursor.getString(0)
                        break
                    }
                }
                id
            }
            if (foundId != null) {
                return@withContext DocumentsContract.buildDocumentUriUsingTree(treeUri, foundId)
            }
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
            DocumentsContract.createDocument(resolver, parentUri, "application/json", FILE_NAME)
        }.getOrNull()
    }

    suspend fun write(context: Context, fileUri: Uri, text: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val stream = runCatching { resolver.openOutputStream(fileUri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(fileUri)
            ?: error("Could not open the sync file for writing")
        stream.bufferedWriter().use { it.write(text) }
    }
}
