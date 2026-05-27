package com.nohimazin.monotune.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nohimazin.monotune.MainActivity
import com.nohimazin.monotune.R
import com.nohimazin.monotune.db.InternalDatabase
import com.nohimazin.monotune.db.MusicDatabase
import com.nohimazin.monotune.extensions.div
import com.nohimazin.monotune.extensions.zipInputStream
import com.nohimazin.monotune.extensions.zipOutputStream
import com.nohimazin.monotune.playback.MusicService
import com.nohimazin.monotune.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val TAG = BackupRestoreViewModel::class.simpleName.toString()

    fun backup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val backupOutputStream = context.applicationContext.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open backup output stream for uri: $uri")
                backupOutputStream.use {
                    it.buffered().zipOutputStream().use { outputStream ->
                        outputStream.setLevel(Deflater.BEST_COMPRESSION)
                        (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered().use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                            inputStream.copyTo(outputStream)
                        }
                        database.checkpoint()
                        FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                reportException(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        it.localizedMessage ?: context.getString(R.string.backup_create_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                var isCompatibleDatabase = true

                val restoreInputStream = context.applicationContext.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open backup input stream for uri: $uri")
                
                restoreInputStream.use {
                    it.zipInputStream().use { inputStream ->
                        var entry = inputStream.nextEntry
                        while (entry != null) {
                            when (entry.name) {
                                SETTINGS_FILENAME -> {
                                    (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream()
                                        .use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                }

                                InternalDatabase.DB_NAME -> {
                                    Log.i(TAG, "Starting database restore")
                                    database.checkpoint()

                                    Log.i(TAG, "Testing new database for compatibility...")
                                    val destFile = context.getDatabasePath(InternalDatabase.TEST_DB_NAME)
                                    destFile.parentFile?.apply {
                                        if (!exists()) mkdirs()
                                    }
                                    FileOutputStream(destFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }

                                    val status = try {
                                        val t = InternalDatabase.newTestInstance(context, InternalDatabase.TEST_DB_NAME)
                                        t.openHelper.writableDatabase.isDatabaseIntegrityOk
                                        t.close()
                                        true
                                    } catch (e: Exception) {
                                        Log.e(TAG, "DB validation failed", e)
                                        false
                                    }

                                    if (status) {
                                        Log.i(TAG, "Found valid database, proceeding with restore")
                                        database.close()
                                        destFile.inputStream().use { inputStream ->
                                            FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        }
                                    } else {
                                        isCompatibleDatabase = false
                                        Log.e(TAG, "Incompatible database, aborting restore")
                                    }
                                }
                            }
                            entry = inputStream.nextEntry
                        }
                    }
                }

                if (!isCompatibleDatabase) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.err_restore_incompatible_database),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val stopIntent = Intent(context, MusicService::class.java)
                context.stopService(stopIntent)
                withContext(Dispatchers.Main) {
                    val startIntent = Intent(context, MainActivity::class.java)
                    startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(startIntent)
                }
                exitProcess(0)
            }.onFailure {
                reportException(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}
