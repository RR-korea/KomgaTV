package com.komgatv.feature.reader.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.scale
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.komgatv.core.model.TranslationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 쪽 그림의 글자를 읽어(ML Kit 글자 인식) 한국어로 번역한다(ML Kit 번역). 모두 TV 안에서 한다.
 *
 * - 글자 인식 모델은 앱에 들어 있고(일본어 모델이 영어도 읽는다), 번역 모델은 처음 한 번 받는다(언어당 약 30MB).
 * - 결과는 쪽마다 [cacheDir]에 저장해 다시 볼 때는 인식·번역을 건너뛴다.
 * - TV 성능이 낮아 한 번에 한 쪽만 처리한다 (미리 받는 쪽들은 차례로).
 */
class MangaTranslator private constructor(context: Context) {

    private val cacheDir = File(context.cacheDir, "translations")
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val translators = mutableMapOf<TranslationMode, Translator>()
    private val modelReady = mutableSetOf<TranslationMode>()
    private val lock = Mutex()

    /**
     * 이 쪽의 번역 덩어리 (쪽 비율 좌표). 번역할 게 없으면 빈 목록.
     * 모델을 받지 못하는 등 실패하면 예외 — 원래 쪽을 그대로 보인다.
     *
     * @param pageKey 쪽을 가리키는 값(주소) — 저장 이름에 쓴다
     */
    suspend fun translate(page: Bitmap, mode: TranslationMode, pageKey: String): List<TranslatedBlock> =
        withContext(Dispatchers.Default) {
            val file = File(cacheDir, sha1("$pageKey|$mode") + ".txt")
            readCache(file)?.let { return@withContext it }
            lock.withLock {
                readCache(file)?.let { return@withLock it }
                val blocks = recognizeAndTranslate(page, mode)
                cacheDir.mkdirs()
                file.writeText(TranslationCacheCodec.encode(blocks))
                blocks
            }
        }

    private suspend fun recognizeAndTranslate(page: Bitmap, mode: TranslationMode): List<TranslatedBlock> {
        val started = System.currentTimeMillis()
        val ocrImage = ocrInput(page)
        val text = recognizer.process(InputImage.fromBitmap(ocrImage, 0)).await()
        val w = ocrImage.width.toFloat()
        val h = ocrImage.height.toFloat()
        if (ocrImage !== page) ocrImage.recycle()

        fun Rect.toBox() = Box(left / w, top / h, right / w, bottom / h)
        val blocks = text.textBlocks.mapNotNull { block ->
            val box = block.boundingBox?.toBox() ?: return@mapNotNull null
            val lines = block.lines.mapNotNull { line -> line.boundingBox?.let { OcrLine(it.toBox(), line.text) } }
            OcrBlock(box, lines).takeIf { lines.isNotEmpty() }
        }

        val translator = translatorFor(mode)
        val result = mergeBlocks(blocks).mapNotNull { block ->
            val source = block.sourceText(mode)
            if (!worthTranslating(source, mode)) return@mapNotNull null
            val translated = translator.translate(source).await().trim()
            // 이름·효과음처럼 번역해도 같으면 원문을 그대로 둔다
            if (translated.isEmpty() || translated == source) null else TranslatedBlock(block.box, translated)
        }
        Timber.d("쪽 번역: 덩어리 ${blocks.size}개 → ${result.size}개, ${System.currentTimeMillis() - started}ms")
        return result
    }

    /** 글자가 작으면 인식이 떨어진다 — 쪽 높이가 1800px 아래면 키워서 읽는다 (최대 2배) */
    private fun ocrInput(page: Bitmap): Bitmap {
        val factor = (OCR_MIN_HEIGHT / page.height.toFloat()).coerceAtMost(2f)
        val source = if (page.config == Bitmap.Config.ARGB_8888) page else page.copy(Bitmap.Config.ARGB_8888, false)
        if (factor <= 1f) return source
        val scaled = source.scale((page.width * factor).toInt(), (page.height * factor).toInt())
        if (source !== page) source.recycle()
        return scaled
    }

    private suspend fun translatorFor(mode: TranslationMode): Translator {
        val translator = synchronized(translators) {
            translators.getOrPut(mode) {
                val source = if (mode == TranslationMode.JA_KO) TranslateLanguage.JAPANESE else TranslateLanguage.ENGLISH
                Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.KOREAN).build(),
                )
            }
        }
        if (mode !in modelReady) {
            // 처음 한 번 번역 모델을 받는다 (Wi-Fi가 아니어도)
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            modelReady += mode
        }
        return translator
    }

    private fun readCache(file: File): List<TranslatedBlock>? =
        if (file.isFile) runCatching { TranslationCacheCodec.decode(file.readText()) }.getOrNull() else null

    companion object {
        private const val OCR_MIN_HEIGHT = 1800f

        @Volatile
        private var instance: MangaTranslator? = null

        fun get(context: Context): MangaTranslator =
            instance ?: synchronized(this) {
                instance ?: MangaTranslator(context.applicationContext).also { instance = it }
            }

        private fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** ML Kit Task를 코루틴으로 기다린다 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
