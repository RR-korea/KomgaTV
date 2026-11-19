package com.komgatv.feature.reader.enhance

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

class GutterSurveyTest {

    @Test
    fun surveyKingdomGutters() {
        val surveyDirEnv = System.getProperty("GUT_SURVEY_DIR") ?: System.getenv("GUT_SURVEY_DIR")
        assumeTrue("GUT_SURVEY_DIR 환경 변수가 설정되지 않아 건너뜁니다", !surveyDirEnv.isNullOrBlank())
        val surveyDir = File(surveyDirEnv!!)
        assumeTrue("GUT_SURVEY_DIR 폴더가 존재하지 않습니다: $surveyDirEnv", surveyDir.exists() && surveyDir.isDirectory)

        val outDirEnv = System.getProperty("GUT_SURVEY_OUT") ?: System.getenv("GUT_SURVEY_OUT") ?: "D:\\Project\\KomgaTV-gutters-data\\survey"
        val outDir = File(outDirEnv)
        outDir.mkdirs()
        val imagesBaseDir = File(outDir, "images")
        imagesBaseDir.mkdirs()

        val csvFile = File(outDir, "survey_results.csv")
        val isNewCsv = !csvFile.exists() || csvFile.length() == 0L
        val csvWriter = PrintWriter(OutputStreamWriter(FileOutputStream(csvFile, true), StandardCharsets.UTF_8), true)
        if (isNewCsv) {
            csvWriter.println("volume,page,size,width,height,before_col_ratio,before_median_len,after_col_ratio,after_median_len,reduced_rows,ink_diff_ratio,time_ms,note")
        }

        // 대상 권 필터링: GUT_SURVEY_VOLS (예: "34,43" 또는 "01,10,20")
        val volsFilter = (System.getProperty("GUT_SURVEY_VOLS") ?: System.getenv("GUT_SURVEY_VOLS"))
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

        val subDirs = surveyDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        val targetDirs = (if (subDirs.isNotEmpty()) subDirs else listOf(surveyDir)).filter { dir ->
            volsFilter == null || dir.name in volsFilter || dir.name.trimStart('0') in volsFilter.map { it.trimStart('0') }
        }

        for (dir in targetDirs) {
            val volName = dir.name
            println("=== 권 조사 시작: $volName (${dir.absolutePath}) ===")
            val volImagesDir = File(imagesBaseDir, volName)
            volImagesDir.mkdirs()

            val imageFiles = dir.listFiles { f ->
                val ext = f.extension.lowercase()
                ext in setOf("jpg", "jpeg", "png", "webp")
            }?.sortedBy { it.name } ?: emptyList()

            if (imageFiles.isEmpty()) {
                println("  이미지 파일이 없습니다.")
                continue
            }

            for (imgFile in imageFiles) {
                val pageName = imgFile.nameWithoutExtension
                val origImg = try {
                    ImageIO.read(imgFile) ?: continue
                } catch (e: Exception) {
                    System.err.println("  이미지 읽기 실패: ${imgFile.name} (${e.message})")
                    continue
                }

                val origW = origImg.width
                val origH = origImg.height
                if (origW < 8 || origH < 32) continue

                // 1) 4K TV: 거의 원본 크기 (orig)
                runSurveyForSize(
                    volName = volName,
                    pageName = pageName,
                    sizeLabel = "orig",
                    srcImg = origImg,
                    csvWriter = csvWriter,
                    volImagesDir = volImagesDir,
                    saveImage = true,
                )

                // 2) 1080p 기기: 높이 약 1100px
                val targetH = 1100
                if (origH > targetH) {
                    val targetW = (origW.toDouble() * targetH / origH).roundToInt().coerceAtLeast(8)
                    val resizedImg = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
                    val g = resizedImg.createGraphics()
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    g.drawImage(origImg, 0, 0, targetW, targetH, null)
                    g.dispose()

                    runSurveyForSize(
                        volName = volName,
                        pageName = pageName,
                        sizeLabel = "1100",
                        srcImg = resizedImg,
                        csvWriter = csvWriter,
                        volImagesDir = volImagesDir,
                        saveImage = false, // 1100은 숫자만 비교하고 다를 때만 확인
                    )
                }
            }
        }
        csvWriter.close()
        println("=== 조사 완료: ${csvFile.absolutePath} ===")
    }

    private fun runSurveyForSize(
        volName: String,
        pageName: String,
        sizeLabel: String,
        srcImg: BufferedImage,
        csvWriter: PrintWriter,
        volImagesDir: File,
        saveImage: Boolean,
    ) {
        val w = srcImg.width
        val h = srcImg.height
        val srcPixels = IntArray(w * h)
        srcImg.getRGB(0, 0, w, h, srcPixels, 0, w)

        val srcLum = IntArray(w * h) { i -> luminance(srcPixels[i]) }
        val bg = calcBgLuminance(srcLum)
        val beforeInk = countInk(srcLum, bg)
        val (beforeColRatio, beforeMedLen) = measureBorderGutters(srcLum, bg, w, h)

        val t0 = System.nanoTime()
        val outPixels = collapseGutters(srcPixels, w, h)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0

        val afterPixels = outPixels ?: srcPixels
        val afterLum = if (outPixels != null) IntArray(w * h) { i -> luminance(afterPixels[i]) } else srcLum
        val afterInk = if (outPixels != null) countInk(afterLum, bg) else beforeInk
        val (afterColRatio, afterMedLen) = if (outPixels != null) {
            measureBorderGutters(afterLum, bg, w, h)
        } else {
            beforeColRatio to beforeMedLen
        }

        // 줄인 줄 수 (상하단 새로 생긴 빈 줄 수)
        val reducedRows = if (outPixels == null) {
            0
        } else {
            val srcTopPad = countEmptyRowsTop(srcLum, bg, w, h)
            val srcBottomPad = countEmptyRowsBottom(srcLum, bg, w, h)
            val afterTopPad = countEmptyRowsTop(afterLum, bg, w, h)
            val afterBottomPad = countEmptyRowsBottom(afterLum, bg, w, h)
            maxOf(0, (afterTopPad + afterBottomPad) - (srcTopPad + srcBottomPad))
        }

        val inkDiff = abs(afterInk - beforeInk)
        val inkDiffRatio = if (beforeInk == 0) 0.0 else inkDiff.toDouble() / beforeInk.toDouble()

        val note = when {
            inkDiffRatio > 0.03 -> "잘림 확인 필요"
            outPixels == null && beforeColRatio > 0.3 -> "미감소"
            afterColRatio > 0.3 && afterMedLen >= h * 0.01 -> "여백 잔존"
            else -> ""
        }

        csvWriter.printf(
            java.util.Locale.US,
            "%s,%s,%s,%d,%d,%.4f,%d,%.4f,%d,%d,%.4f,%.2f,%s%n",
            volName, pageName, sizeLabel, w, h,
            beforeColRatio, beforeMedLen,
            afterColRatio, afterMedLen,
            reducedRows, inkDiffRatio, elapsedMs, note
        )

        if (saveImage) {
            saveSideBySideJpg(
                volName = volName,
                pageName = pageName,
                srcImg = srcImg,
                afterPixels = afterPixels,
                w = w,
                h = h,
                volImagesDir = volImagesDir,
            )
        }
    }

    private fun saveSideBySideJpg(
        volName: String,
        pageName: String,
        srcImg: BufferedImage,
        afterPixels: IntArray,
        w: Int,
        h: Int,
        volImagesDir: File,
    ) {
        val targetH = 1100
        val targetW = (w.toDouble() * targetH / h).roundToInt().coerceAtLeast(8)

        val afterImg = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        afterImg.setRGB(0, 0, w, h, afterPixels, 0, w)

        val sideBySide = BufferedImage(targetW * 2, targetH, BufferedImage.TYPE_INT_RGB)
        val g = sideBySide.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(srcImg, 0, 0, targetW, targetH, null)
        g.drawImage(afterImg, targetW, 0, targetW, targetH, null)
        g.dispose()

        val outJpg = File(volImagesDir, "${pageName}.jpg")
        try {
            val writers = ImageIO.getImageWritersByFormatName("jpg")
            if (writers.hasNext()) {
                val writer = writers.next()
                val writeParam = writer.defaultWriteParam
                writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
                writeParam.compressionQuality = 0.85f
                FileImageOutputStream(outJpg).use { stream ->
                    writer.output = stream
                    writer.write(null, IIOImage(sideBySide, null, null), writeParam)
                }
                writer.dispose()
            } else {
                ImageIO.write(sideBySide, "JPG", outJpg)
            }
        } catch (e: Exception) {
            System.err.println("  비교 JPG 저장 실패: ${outJpg.name} (${e.message})")
        }
    }

    private fun luminance(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun calcBgLuminance(lum: IntArray): Int {
        val hist = IntArray(256)
        for (v in lum) hist[v]++
        val n = lum.size
        val bright = (200..255).sumOf { hist[it] }
        if (bright > n * 0.1) return (200..255).maxByOrNull { hist[it] } ?: 255
        val dark = (0..50).sumOf { hist[it] }
        if (dark > n * 0.1) return (0..50).maxByOrNull { hist[it] } ?: 0
        return (0..255).maxByOrNull { hist[it] } ?: 255
    }

    private fun countInk(lum: IntArray, bg: Int): Int {
        var count = 0
        for (v in lum) {
            if (abs(v - bg) >= 80) count++
        }
        return count
    }

    private fun countEmptyRowsTop(lum: IntArray, bg: Int, w: Int, h: Int): Int {
        val edgeX = (w * 0.02).roundToInt()
        for (y in 0 until h) {
            var inkCount = 0
            for (x in edgeX until w - edgeX) {
                if (abs(lum[y * w + x] - bg) >= 80) {
                    inkCount++
                    if (inkCount > w * 0.02) return y
                }
            }
        }
        return h
    }

    private fun countEmptyRowsBottom(lum: IntArray, bg: Int, w: Int, h: Int): Int {
        val edgeX = (w * 0.02).roundToInt()
        for (y in h - 1 downTo 0) {
            var inkCount = 0
            for (x in edgeX until w - edgeX) {
                if (abs(lum[y * w + x] - bg) >= 80) {
                    inkCount++
                    if (inkCount > w * 0.02) return h - 1 - y
                }
            }
        }
        return h
    }

    /**
     * 남은 여백: 종이색 세로 구간 가운데 위 끝과 아래 끝 둘 다 칸 테두리에 닿은 것만 센다.
     * 칸 테두리 = 그 끝 픽셀에서 좌우로 이어지는 가로(한 칸 옆으로 갈 때 위아래 2줄까지 허용) 잉크 선이 쪽 너비의 4% 이상.
     * 반환: (그런 구간이 있는 열의 비율, 구간 길이 중앙값)
     */
    private fun measureBorderGutters(lum: IntArray, bg: Int, width: Int, height: Int): Pair<Double, Int> {
        val edgeX = (width * 0.02).roundToInt()
        val x0 = edgeX
        val x1 = width - edgeX
        if (x1 <= x0) return 0.0 to 0

        fun isInk(x: Int, y: Int) = x in 0 until width && y in 0 until height && abs(lum[y * width + x] - bg) >= 80

        val minBorderRun = (width * 0.04).roundToInt().coerceAtLeast(4)

        // 특정 좌표 (startX, startY)가 가로 테두리 잉크선(너비 4% 이상)에 닿아 있는지 검사
        fun isHorizontalBorder(startX: Int, startY: Int): Boolean {
            val actualY = listOf(startY, startY - 1, startY + 1).firstOrNull { isInk(startX, it) } ?: return false

            // 좌측 탐색
            var minX = startX
            var curY = actualY
            for (x in startX - 1 downTo 0) {
                val nextY = listOf(curY, curY - 1, curY + 1, curY - 2, curY + 2).firstOrNull { isInk(x, it) }
                if (nextY != null) {
                    curY = nextY
                    minX = x
                } else {
                    break
                }
            }

            // 우측 탐색
            var maxX = startX
            curY = actualY
            for (x in startX + 1 until width) {
                val nextY = listOf(curY, curY - 1, curY + 1, curY - 2, curY + 2).firstOrNull { isInk(x, it) }
                if (nextY != null) {
                    curY = nextY
                    maxX = x
                } else {
                    break
                }
            }

            return (maxX - minX + 1) >= minBorderRun
        }

        val minGapLen = (height * 0.005).roundToInt().coerceAtLeast(3)
        val maxGapLen = (height * 0.15).roundToInt()

        var gutterColsCount = 0
        val allGutterLengths = ArrayList<Int>()

        for (x in x0 until x1) {
            var colHasGutter = false
            var runStart = -1

            for (y in 0 until height) {
                val ink = isInk(x, y)
                if (!ink) {
                    if (runStart < 0) runStart = y
                } else {
                    if (runStart >= 0) {
                        val runEnd = y - 1
                        val len = runEnd - runStart + 1
                        if (len in minGapLen..maxGapLen && runStart > 0 && runEnd < height - 1) {
                            if (isHorizontalBorder(x, runStart - 1) && isHorizontalBorder(x, runEnd + 1)) {
                                colHasGutter = true
                                allGutterLengths.add(len)
                            }
                        }
                        runStart = -1
                    }
                }
            }

            if (colHasGutter) {
                gutterColsCount++
            }
        }

        val totalCols = x1 - x0
        val colRatio = if (totalCols > 0) gutterColsCount.toDouble() / totalCols else 0.0
        val medianLen = if (allGutterLengths.isNotEmpty()) {
            allGutterLengths.sort()
            allGutterLengths[allGutterLengths.size / 2]
        } else {
            0
        }

        return colRatio to medianLen
    }
}
