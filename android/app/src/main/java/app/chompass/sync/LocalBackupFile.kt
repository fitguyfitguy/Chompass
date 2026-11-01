package app.chompass.sync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the backup document inside a user-picked folder (SAF tree).
 * The folder is granted once in Settings; the file name is fixed so an
 * external folder-sync tool sees one stable document.
 */
object LocalBackupFile {
    const val FILE_NAME = "Chompass-sync.json"
    private const val TEMP_FILE_NAME = "$FILE_NAME.tmp"
    private const val BACKUP_FILE_NAME = "$FILE_NAME.bak"
    private const val MIME_JSON = "application/json"

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

    /**
     * Writes [text] to a temp document, reads it back, and only then swaps it
     * in for [FILE_NAME]. The previous file becomes [BACKUP_FILE_NAME] for the
     * duration of the swap, so a failed or interrupted write leaves a backup.
     */
    suspend fun write(context: Context, treeUri: Uri, text: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        recoverStaleFiles(resolver, treeUri, parentId)
        val temp = DocumentsContract.createDocument(resolver, parentUri, MIME_JSON, TEMP_FILE_NAME)
            ?: error("Could not create the backup temp file")
        try {
            writeText(resolver, temp, text)
            if (readText(resolver, temp) != text) {
                error("Backup verification failed")
            }
            val previous = findChild(resolver, treeUri, parentId, FILE_NAME)
            val backup = previous?.let {
                DocumentsContract.renameDocument(resolver, it, BACKUP_FILE_NAME)
                    ?: error("Could not prepare the previous backup")
            }
            try {
                DocumentsContract.renameDocument(resolver, temp, FILE_NAME)
                    ?: error("Could not finalize the backup file")
            } catch (t: Throwable) {
                backup?.let {
                    runCatching { DocumentsContract.renameDocument(resolver, it, FILE_NAME) }
                }
                throw t
            }
            backup?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, temp) }
            throw t
        }
    }

    private fun recoverStaleFiles(
        resolver: ContentResolver,
        treeUri: Uri,
        parentId: String,
    ) {
        val final = findChild(resolver, treeUri, parentId, FILE_NAME)
        val staleBackup = findChild(resolver, treeUri, parentId, BACKUP_FILE_NAME)
        if (staleBackup != null) {
            if (final != null) {
                if (!DocumentsContract.deleteDocument(resolver, staleBackup)) {
                    error("Could not clear the previous backup copy")
                }
            } else {
                DocumentsContract.renameDocument(resolver, staleBackup, FILE_NAME)
                    ?: error("Could not restore the previous backup")
            }
        }
        findChild(resolver, treeUri, parentId, TEMP_FILE_NAME)?.let { stale ->
            if (!DocumentsContract.deleteDocument(resolver, stale)) {
                error("Could not clear the previous backup temp file")
            }
        }
    }

    private fun findChild(
        resolver: ContentResolver,
        treeUri: Uri,
        parentId: String,
        name: String,
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        return resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                }
            }
            null
        }
    }

    private fun writeText(resolver: ContentResolver, uri: Uri, text: String) {
        val stream = resolver.openOutputStream(uri, "wt")
            ?: error("Could not open the backup temp file for writing")
        stream.bufferedWriter().use { it.write(text) }
    }

    private fun readText(resolver: ContentResolver, uri: Uri): String? =
        resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
}
