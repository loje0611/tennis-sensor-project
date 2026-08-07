package io.github.loje0611.tennisdoc.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvFileExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SwingHistoryRepository,
) {
    suspend fun exportDataToCsv(
        sessionId: String? = null,
        startTimeMillis: Long? = null,
        endTimeMillis: Long? = null,
    ): Uri {
        val csv = repository.generateCsvString(sessionId, startTimeMillis, endTimeMillis)
        val file = File(context.cacheDir, SwingHistoryRepository.EXPORT_FILE_NAME)
        file.writeText(csv, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
