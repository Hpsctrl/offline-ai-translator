package com.example.offlineaitranslator.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundled, 100% on-device OCR using ML Kit Text Recognition (Latin + Devanagari).
 * No network is used. Sample documents are available only when the user explicitly taps a sample.
 */
class OfflineOcrEngine {

    data class OcrSample(
        val id: String,
        val title: String,
        val text: String,
    )

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val devanagariRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    /**
     * Runs real on-device OCR on a bitmap; returns "" when no readable text is found.
     * When [language] is a Devanagari script, only the bundled Devanagari recognizer runs (the Latin
     * recognizer ignores it and vice versa) — matching the language mode the user selected.
     */
    suspend fun recognize(bitmap: Bitmap, language: SupportedLanguage = SupportedLanguage.ENGLISH): String =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val devanagari = isDevanagari(language)
            val text = runCatching {
                Tasks.await((if (devanagari) devanagariRecognizer else latinRecognizer).process(image)).text
            }.getOrDefault("")
            if (devanagari) listOf(text).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            else listOf(text).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }

    /** Devanagari scripts supported by the bundled ML Kit Devanagari recognizer. */
    private fun isDevanagari(language: SupportedLanguage): Boolean = language.code.startsWith("devanagari") ||
        language.code in listOf("as", "mni", "sa", "brx", "doi", "kok", "mai", "sat", "sd")

    /** Decodes a gallery Uri (downsampled) and runs real bundled OCR on it. */
    suspend fun processOcrImage(uri: Uri, context: Context, language: SupportedLanguage = SupportedLanguage.ENGLISH): String =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ""
            val bitmap = stream.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext ""
            recognize(bitmap, language)
        }

    companion object {
        val SAMPLES: List<OcrSample> = listOf(
            OcrSample(
                "ocr_medical_prescription", "Medical Prescription",
                "CITY HEALTH CLINIC\nPrescription: Paracetamol 500 mg\nDosage: One tablet three times a day after food\nDuration: 5 days\nDoctor: Dr. A. Sharma, MBBS",
            ),
            OcrSample(
                "ocr_pilgrim_railway_board", "Pilgrim/Railway Board",
                "NORTH RAILWAY - PILGRIM SPECIAL\nPlatform 3: Train 12903 to Haridwar\nDeparture 06:40 AM\nWaiting Room and Free Water Point available\nPlease carry your identity proof",
            ),
            OcrSample(
                "ocr_disaster_evacuation_notice", "Disaster Evacuation Notice",
                "DISTRICT DISASTER CONTROL ROOM\nEVACUATION NOTICE\nHeavy rainfall warning: leave low-lying areas immediately\nAssembly point: District Sports Ground\nEmergency helpline: 108",
            ),
            OcrSample(
                "ocr_market_trade_invoice", "Market Trade Invoice",
                "WHOLESALE GRAIN MARKET - INVOICE\nItem: Wheat, 20 kg bags - Quantity: 40\nRate: 2,150 INR per bag\nTotal: 86,000 INR\nPayment due within 7 days",
            ),
            OcrSample(
                "ocr_safety_warning", "Safety Warning",
                "SAFETY WARNING\nDanger: High voltage area\nDo not touch the machinery\nWear helmet and gloves at all times\nReport any accident to the supervisor",
            ),
        )

        /** Sample selection is only used by the explicit sample chips, never for gallery images. */
        fun sampleFor(bitmap: Bitmap): OcrSample {
            val key = bitmap.width.toLong() * 31L + bitmap.height.toLong()
            return SAMPLES[java.lang.Math.floorMod(key, SAMPLES.size.toLong()).toInt()]
        }
    }

    val samples: List<OcrSample> get() = SAMPLES
}

typealias OcrSample = OfflineOcrEngine.OcrSample
