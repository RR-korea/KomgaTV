package com.komgatv.feature.reader.translate

import android.content.Context
import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import com.komgatv.core.model.TranslationMode
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * 페이지 리더(Coil)용 번역 — 쪽을 디코딩한 뒤 글자를 읽고 번역해 그려 넣는다.
 * 이웃 쪽 미리 받기도 같은 요청이라 미리 번역된다. 실패하면 원래 쪽을 그대로 보인다.
 */
internal class TranslateTransformation(
    private val context: Context,
    private val mode: TranslationMode,
    private val pageKey: String,
) : Transformation() {

    override val cacheKey: String = "translate-$mode"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = try {
        val blocks = MangaTranslator.get(context).translate(input, mode, pageKey) {
            // 글자는 받아 둔 원본 파일에서 읽는다 (화면 크기 그림보다 훨씬 잘 읽는다)
            OcrSource.load(context, pageKey)
        }
        TranslatePainter.paint(input, blocks)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "쪽 번역 실패 — 원문으로 보인다: $pageKey")
        input
    }
}
