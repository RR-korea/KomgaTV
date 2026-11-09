package com.komgatv.feature.reader.enhance

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * 자동 채색 모델 — manga-colorization-v2 생성기(qweasdd)의 fp16 ONNX 판.
 * 원래 저장소에 라이선스 파일이 없어 앱에 넣지 않고, 처음 켤 때 Hugging Face에서 받아 이 기기에만 둔다.
 */
internal object ColorModel {
    sealed interface State {
        data object Missing : State
        data class Downloading(val percent: Int) : State
        data class Ready(val path: String) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: OrtSession? = null
    private var sessionPath: String? = null

    private fun file(context: Context) = File(context.noBackupFilesDir, "models/$MODEL_FILE")

    /** 모델이 없으면 받기 시작한다 (받는 중이면 그대로). 받다 실패했으면 다시 받는다 */
    @Synchronized
    fun ensure(context: Context) {
        when (_state.value) {
            is State.Ready, is State.Downloading -> return
            else -> Unit
        }
        val target = file(context.applicationContext)
        // 받을 때 해시를 맞춰 보고 이름을 바꾸므로, 있으면 온전한 파일이다
        if (target.exists()) {
            _state.value = State.Ready(target.path)
            return
        }
        _state.value = State.Downloading(0)
        scope.launch {
            _state.value = runCatching { download(target) }.fold({ State.Ready(target.path) }, { State.Failed })
        }
    }

    private fun download(target: File) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: MODEL_SIZE
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        _state.value = State.Downloading((done * 100 / total).toInt().coerceIn(0, 99))
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(hash == MODEL_SHA256) { "hash $hash" }
            check(part.renameTo(target)) { "rename" }
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    /** 한 번 만든 세션은 계속 쓴다 (만들 때 1~2초, 모델 60MB) */
    @Synchronized
    fun session(path: String): OrtSession {
        session?.takeIf { sessionPath == path }?.let { return it }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return OrtEnvironment.getEnvironment().createSession(path, options).also {
            session = it
            sessionPath = path
        }
    }
}

/**
 * 흑백 쪽을 칠한다 — 모델은 폭 [COLOR_WIDTH]로 줄인 쪽에서 색만 정하고, 밝기는 원래 쪽 그대로 둔다(선이 흐려지지 않게).
 * 선·글자처럼 아주 어두운 곳과 종이처럼 아주 밝은 곳은 색을 덜 입힌다. 좌우 두 쪽 스캔은 쪽마다 따로 칠한다.
 * 이미 색이 있는 쪽(표지, 컬러 화보)은 그대로 둔다.
 *
 * @param infer 모델 — 폭 [COLOR_WIDTH], 높이 32의 배수인 흑백(0..1) 쪽을 받아 RGB(0..1, 채널별 평면)를 돌려준다
 * @return 칠한 픽셀, 칠하지 않으면 null
 */
internal fun colorizePage(pixels: IntArray, width: Int, height: Int, infer: (gray: FloatArray, h: Int) -> FloatArray): IntArray? {
    if (width < 32 || height < 32 || colorful(pixels)) return null
    val out = pixels.copyOf()
    val halves = if (width > height * SPREAD_RATIO) listOf(0 until width / 2, width / 2 until width) else listOf(0 until width)
    for (cols in halves) colorizePart(pixels, out, width, height, cols.first, cols.last + 1, infer)
    return out
}

private fun colorizePart(
    pixels: IntArray,
    out: IntArray,
    width: Int,
    height: Int,
    x0: Int,
    x1: Int,
    infer: (FloatArray, Int) -> FloatArray,
) {
    val w = x1 - x0
    val sw = COLOR_WIDTH
    val sh = ceil(height.toDouble() * sw / w).toInt().coerceAtLeast(1)
    val ph = (sh + 31) / 32 * 32
    // 줄인 흑백 쪽 — 칸마다 원래 픽셀의 평균 (아래 남는 곳은 종이)
    val gray = FloatArray(sw * ph) { 1f }
    for (sy in 0 until sh) {
        val ya = sy * height / sh
        val yb = maxOf(ya + 1, (sy + 1) * height / sh)
        for (sx in 0 until sw) {
            val xa = x0 + sx * w / sw
            val xb = maxOf(xa + 1, x0 + (sx + 1) * w / sw)
            var sum = 0
            for (y in ya until yb) for (x in xa until xb) sum += luma(pixels[y * width + x])
            gray[sy * sw + sx] = sum / (255f * (yb - ya) * (xb - xa))
        }
    }
    val rgb = infer(gray, ph)
    val plane = sw * ph
    // 줄인 쪽의 색 (Cb, Cr)
    val cb = FloatArray(sw * sh)
    val cr = FloatArray(sw * sh)
    for (i in 0 until sw * sh) {
        val r = rgb[i].coerceIn(0f, 1f) * 255f
        val g = rgb[plane + i].coerceIn(0f, 1f) * 255f
        val b = rgb[2 * plane + i].coerceIn(0f, 1f) * 255f
        cb[i] = -0.168736f * r - 0.331264f * g + 0.5f * b
        cr[i] = 0.5f * r - 0.418688f * g - 0.081312f * b
    }
    // 원래 크기로 늘린 색을 원래 밝기에 입힌다
    for (y in 0 until height) {
        val fy = ((y + 0.5f) * sh / height - 0.5f).coerceIn(0f, sh - 1f)
        val ya = fy.toInt()
        val yb = minOf(ya + 1, sh - 1)
        val ty = fy - ya
        for (x in x0 until x1) {
            val fx = ((x - x0 + 0.5f) * sw / w - 0.5f).coerceIn(0f, sw - 1f)
            val xa = fx.toInt()
            val xb = minOf(xa + 1, sw - 1)
            val tx = fx - xa
            fun lerp(p: FloatArray): Float {
                val top = p[ya * sw + xa] * (1 - tx) + p[ya * sw + xb] * tx
                val bottom = p[yb * sw + xa] * (1 - tx) + p[yb * sw + xb] * tx
                return top * (1 - ty) + bottom * ty
            }
            val i = y * width + x
            val l = luma(pixels[i]).toFloat()
            val k = ((l - DARK_LUMA) / DARK_RAMP).coerceIn(0f, 1f) * ((LIGHT_LUMA - l) / LIGHT_RAMP).coerceIn(0f, 1f)
            val u = lerp(cb) * k
            val v = lerp(cr) * k
            val r = (l + 1.402f * v).roundToInt().coerceIn(0, 255)
            val g = (l - 0.344136f * u - 0.714136f * v).roundToInt().coerceIn(0, 255)
            val b = (l + 1.772f * u).roundToInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

private fun luma(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

/** 이미 색이 있는 쪽 — 채도 있는 픽셀이 [COLORFUL_RATIO] 넘게 (7칸마다 본다) */
private fun colorful(pixels: IntArray): Boolean {
    var seen = 0
    var colored = 0
    for (i in pixels.indices step 7) {
        val c = pixels[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        seen++
        if (maxOf(abs(r - g), abs(g - b), abs(r - b)) > COLORFUL_DIFF) colored++
    }
    return colored > seen * COLORFUL_RATIO
}

/** 페이지 리더(Coil)용 — 작품 보기 설정 "자동 채색" ([modelPath]는 받아 둔 모델) */
internal class ColorizeTransformation(private val modelPath: String) : Transformation() {
    override val cacheKey: String = "colorize-1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val out = pageWorkLock.withLock {
            val pixels = IntArray(w * h)
            input.getPixels(pixels, 0, w, 0, 0, w, h)
            // 네이티브 라이브러리가 없는 기기(32비트 x86)나 모델을 못 여는 경우엔 흑백 그대로
            try {
                val session = ColorModel.session(modelPath)
                colorizePage(pixels, w, h) { gray, ph -> runModel(session, gray, ph) }
            } catch (e: Exception) {
                Timber.w(e, "colorize failed")
                null
            } catch (e: LinkageError) {
                Timber.w(e, "colorize unavailable")
                null
            }
        } ?: return input
        return createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }

    /** 입력 [1,5,H,W]: 흑백, 색 힌트 3, 힌트 표시 1 (힌트 없음 = 0). 출력 [1,3,H,W] RGB 0..1 */
    private fun runModel(session: OrtSession, gray: FloatArray, ph: Int): FloatArray {
        val plane = COLOR_WIDTH * ph
        val input = FloatArray(5 * plane)
        gray.copyInto(input)
        val env = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 5, ph.toLong(), COLOR_WIDTH.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val buffer = (result[0] as OnnxTensor).floatBuffer
                return FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
    }
}

private const val MODEL_FILE = "manga-colorize-fp16.onnx"
private const val MODEL_URL = "https://huggingface.co/Faridzar/manga-colorization-v2-onnx/resolve/main/manga-colorize-fp16.onnx"
private const val MODEL_SHA256 = "39660d0047ea6f1a0ddee6aa89054997f95ea566f4d56ff762f66dbcf1a1a7ef"
private const val MODEL_SIZE = 61_650_260L

/** 모델이 색을 정하는 폭 — 색만 쓰므로 작아도 된다 (576보다 두 배 넘게 빠르고 색은 거의 같다) */
internal const val COLOR_WIDTH = 384

/** 가로가 세로의 이만큼 넘으면 좌우 두 쪽 스캔 */
private const val SPREAD_RATIO = 1.15f

/** 이보다 어두운 곳(선·글자)은 칠하지 않고, [DARK_RAMP]에 걸쳐 차츰 칠한다 */
private const val DARK_LUMA = 30f
private const val DARK_RAMP = 50f

/** 이보다 밝은 곳(종이)은 칠하지 않고, [LIGHT_RAMP]에 걸쳐 차츰 칠한다 */
private const val LIGHT_LUMA = 250f
private const val LIGHT_RAMP = 25f

/** 이미 색 있는 쪽 — RGB 차이가 [COLORFUL_DIFF] 넘는 픽셀이 이만큼 넘게 */
private const val COLORFUL_DIFF = 40
private const val COLORFUL_RATIO = 0.05f
