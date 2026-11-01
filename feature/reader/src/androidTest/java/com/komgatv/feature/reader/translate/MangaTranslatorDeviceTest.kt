package com.komgatv.feature.reader.translate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.graphics.scale
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgatv.core.model.TranslationMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File

/**
 * 시험용 쪽(직접 그린 막대 인물·말풍선, docs/translate/make_test_pages.py)을 실제 ML Kit로 번역해 본다.
 * 앱처럼 화면에는 높이 1080으로 줄인 그림을 쓰고, 글자 인식은 원본으로 한다. 결과 그림을 기기에 남긴다:
 *   /sdcard/Android/data/<테스트 패키지>/files/translate/{ja,en}_out.png
 */
@RunWith(AndroidJUnit4::class)
class MangaTranslatorDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    init {
        // 인식한 원문과 번역을 로그로 본다
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
    }

    private fun original(name: String): Bitmap =
        instrumentation.context.assets.open("translate/$name").use { BitmapFactory.decodeStream(it) }

    private fun run(name: String, mode: TranslationMode): List<TranslatedBlock> = runBlocking {
        val source = original("${name}_page.png")
        val input = source.scale(source.width * 1080 / source.height, 1080)
        // 저장된 결과가 아니라 매번 새로 인식·번역하게 키를 바꾼다
        val blocks = MangaTranslator.get(context).translate(input, mode, "device-test/$name/${System.nanoTime()}") {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        blocks.forEach { Log.i("TranslateTest", "$name ${it.box} → ${it.text}") }
        val out = File(context.getExternalFilesDir(null), "translate").apply { mkdirs() }
        File(out, "${name}_out.png").outputStream().use {
            TranslatePainter.paint(input, blocks).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        blocks
    }

    private fun List<TranslatedBlock>.allKorean() = all { b -> b.text.any { it in '가'..'힣' } }

    @Test
    fun 일본어_세로쓰기_말풍선을_한국어로() {
        val blocks = run("ja", TranslationMode.JA_KO)
        // 말풍선 4개 → 덩어리 4개 (한 말풍선의 세로 열들을 합쳤다)
        assertEquals(4, blocks.size)
        assertTrue(blocks.allKorean())
    }

    @Test
    fun 영어_대문자_말풍선을_한국어로() {
        val blocks = run("en", TranslationMode.EN_KO)
        assertEquals(4, blocks.size)
        assertTrue(blocks.allKorean())
    }
}
