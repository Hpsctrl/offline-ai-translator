package com.example.offlineaitranslator.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed interface ExtractionState {
    object Idle : ExtractionState
    data class Extracting(
        val currentFile: String,
        val filesExtracted: Int,
        val totalFiles: Int,
        val bytesExtracted: Long,
        val totalBytes: Long
    ) : ExtractionState {
        val progress: Float
            get() = if (totalBytes > 0) (bytesExtracted.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    data class Completed(val targetDir: File, val totalFiles: Int, val totalBytes: Long) : ExtractionState
    data class Error(val message: String, val throwable: Throwable? = null) : ExtractionState
}

class AssetExtractor(private val context: Context) {

    companion object {
        private const val TAG = "AssetExtractor"
        private const val ASSET_MODELS_ROOT = "models"
        private const val COMPLETION_MARKER = ".extraction_complete_v3"
    }

    private val _state = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val state: StateFlow<ExtractionState> = _state.asStateFlow()

    val modelsDir: File
        get() = File(context.filesDir, "models")

    fun isExtractionComplete(): Boolean {
        val target = modelsDir
        val marker = File(target, COMPLETION_MARKER)
        return marker.exists() && target.exists() && (target.listFiles()?.isNotEmpty() == true)
    }

    suspend fun extractIfNeeded(): Result<File> = withContext(Dispatchers.IO) {
        val target = modelsDir
        val marker = File(target, COMPLETION_MARKER)

        if (marker.exists()) {
            Log.i(TAG, "Models already extracted at: ${target.absolutePath}")
            val completed = ExtractionState.Completed(target, 0, 0)
            _state.value = completed
            return@withContext Result.success(target)
        }

        try {
            if (!target.exists()) {
                target.mkdirs()
            }

            // Discover all asset files
            val assetList = mutableListOf<String>()
            discoverAssets(ASSET_MODELS_ROOT, assetList)
            val totalFiles = assetList.size

            if (totalFiles == 0) {
                val err = "No model assets found under 'models/' directory in APK"
                _state.value = ExtractionState.Error(err)
                return@withContext Result.failure(IOException(err))
            }

            // Calculate approximate total bytes
            var totalBytes = 0L
            val fileSizes = mutableMapOf<String, Long>()
            for (path in assetList) {
                val size = try {
                    context.assets.openFd(path).use { it.length }
                } catch (e: Exception) {
                    try {
                        context.assets.open(path).use { it.available().toLong() }
                    } catch (ex: Exception) {
                        0L
                    }
                }
                fileSizes[path] = size
                totalBytes += size
            }

            var bytesExtractedSoFar = 0L
            var filesExtractedSoFar = 0

            for (assetPath in assetList) {
                val relativePath = assetPath.removePrefix("$ASSET_MODELS_ROOT/")
                val destFile = File(target, relativePath)

                _state.value = ExtractionState.Extracting(
                    currentFile = relativePath,
                    filesExtracted = filesExtractedSoFar,
                    totalFiles = totalFiles,
                    bytesExtracted = bytesExtractedSoFar,
                    totalBytes = totalBytes
                )

                destFile.parentFile?.mkdirs()

                // Check if already extracted with matching size (safe resume)
                val expectedSize = fileSizes[assetPath] ?: 0L
                if (destFile.exists() && destFile.length() > 0 && (expectedSize == 0L || destFile.length() == expectedSize)) {
                    bytesExtractedSoFar += destFile.length()
                    filesExtractedSoFar++
                    continue
                }

                // Copy asset to destination
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesExtractedSoFar += read
                            _state.value = ExtractionState.Extracting(
                                currentFile = relativePath,
                                filesExtracted = filesExtractedSoFar,
                                totalFiles = totalFiles,
                                bytesExtracted = bytesExtractedSoFar,
                                totalBytes = totalBytes
                            )
                        }
                    }
                }
                filesExtractedSoFar++
            }

            // Write completion marker
            marker.writeText("version=1.0\ntotalFiles=$totalFiles\ntotalBytes=$bytesExtractedSoFar\n")
            Log.i(TAG, "Successfully extracted $totalFiles model files ($bytesExtractedSoFar bytes) to ${target.absolutePath}")

            val completed = ExtractionState.Completed(target, totalFiles, bytesExtractedSoFar)
            _state.value = completed
            Result.success(target)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to extract models: ${t.message}", t)
            _state.value = ExtractionState.Error("Extraction failed: ${t.message}", t)
            Result.failure(t)
        }
    }

    private fun discoverAssets(path: String, outList: MutableList<String>) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            // It is a file
            outList.add(path)
        } else {
            // It is a directory
            for (child in children) {
                discoverAssets("$path/$child", outList)
            }
        }
    }
}
