package app.chompass.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LocalBackupFileTest {
    private val authority = "app.chompass.test.backup"
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var provider: FakeDocumentProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = Files.createTempDirectory("chompass-backup").toFile()
        provider = FakeDocumentProvider(root, authority)
        val info = ProviderInfo()
        info.authority = authority
        provider.attachInfo(context, info)
        ShadowContentResolver.registerProviderInternal(authority, provider)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(authority, FakeDocumentProvider.ROOT_ID)

    @Test
    fun writesAndReplacesTheBackup() = runBlocking {
        val first = "{\"version\":1}"
        val second = "{\"version\":2}"

        LocalBackupFile.write(context, treeUri(), first)
        assertEquals(first, File(root, LocalBackupFile.FILE_NAME).readText())

        LocalBackupFile.write(context, treeUri(), second)
        assertEquals(second, File(root, LocalBackupFile.FILE_NAME).readText())
        assertFalse(File(root, "${LocalBackupFile.FILE_NAME}.tmp").exists())
        assertFalse(File(root, "${LocalBackupFile.FILE_NAME}.bak").exists())
    }

    @Test
    fun keepsThePreviousBackupWhenTheTempCopyCannotBeReadBack() = runBlocking {
        val previous = "{\"version\":1}"
        File(root, LocalBackupFile.FILE_NAME).writeText(previous)
        provider.corruptReads = true

        val result = runCatching {
            LocalBackupFile.write(context, treeUri(), "{\"version\":2}")
        }

        assertEquals(true, result.isFailure)
        assertEquals(previous, File(root, LocalBackupFile.FILE_NAME).readText())
        assertFalse(File(root, "${LocalBackupFile.FILE_NAME}.tmp").exists())
        assertFalse(File(root, "${LocalBackupFile.FILE_NAME}.bak").exists())
    }

    private class FakeDocumentProvider(
        private val root: File,
        private val authority: String,
    ) : ContentProvider() {
        var corruptReads = false

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = projection ?: DEFAULT_DOCUMENT_PROJECTION
            val cursor = MatrixCursor(columns)
            val segments = uri.pathSegments
            if (segments.lastOrNull() == "children") {
                root.listFiles()?.forEach { addFile(cursor, it, columns) }
            } else {
                addFile(cursor, file(segments.last()), columns)
            }
            return cursor
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val source = if (corruptReads && !mode.contains('w')) {
                File(root, "corrupt").apply { writeText("corrupt") }
            } else {
                file(uri.pathSegments.last())
            }
            return ParcelFileDescriptor.open(source, ParcelFileDescriptor.parseMode(mode))
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            val uri = extras?.getParcelable("uri", Uri::class.java) ?: return null
            return when (method) {
                "android:createDocument" -> {
                    val name = extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: return null
                    val file = File(root, name)
                    if (!file.createNewFile()) error("document already exists")
                    Bundle().apply {
                        putParcelable(
                            "uri",
                            DocumentsContract.buildDocumentUri(authority, file.name),
                        )
                    }
                }
                "android:deleteDocument" -> {
                    if (!file(uri.pathSegments.last()).delete()) error("could not delete document")
                    Bundle()
                }
                "android:renameDocument" -> {
                    val name = extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: return null
                    val target = File(root, name)
                    if (!file(uri.pathSegments.last()).renameTo(target)) error("could not rename document")
                    Bundle().apply {
                        putParcelable(
                            "uri",
                            DocumentsContract.buildDocumentUri(authority, target.name),
                        )
                    }
                }
                else -> null
            }
        }

        override fun getType(uri: Uri): String? = "application/json"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        private fun file(documentId: String): File =
            if (documentId == ROOT_ID) root else File(root, documentId)

        private fun addFile(cursor: MatrixCursor, file: File, columns: Array<out String>) {
            val row = cursor.newRow()
            for (column in columns) {
                when (column) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> row.add(file.name)
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> row.add(file.name)
                    DocumentsContract.Document.COLUMN_MIME_TYPE ->
                        row.add(if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/json")
                    DocumentsContract.Document.COLUMN_SIZE -> row.add(file.length())
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> row.add(file.lastModified())
                    DocumentsContract.Document.COLUMN_FLAGS -> row.add(0)
                    else -> row.add(null)
                }
            }
        }

        companion object {
            const val ROOT_ID = "root"
        }
    }

    private companion object {
        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}
