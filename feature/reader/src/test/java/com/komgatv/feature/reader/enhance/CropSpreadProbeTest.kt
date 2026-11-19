package com.komgatv.feature.reader.enhance

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 조사용 — 연속된 두 쪽 쌍(좌/우)에 matchCrop 을 적용했을 때 결과를 그린다.
 * SPREAD_LIST, SPREAD_OUT 환경 변수가 없으면 건너뛴다.
 */
class CropSpreadProbeTest {

    @Test
    fun probeSpreads() {
        val list = System.getenv("SPREAD_LIST")
        val out = System.getenv("SPREAD_OUT")
        assumeTrue(list != null && out != null)
        File(out!!).mkdirs()

        val lines = File(list!!).readLines().filter { it.isNotBlank() }
        var pairIndex = 0

        var i = 0
        while (i < lines.size - 1) {
            val p1 = lines[i]
            val p2 = lines[i + 1]
            i += 2

            val imgL = ImageIO.read(File(p1)) ?: continue
            val imgR = ImageIO.read(File(p2)) ?: continue

            val wL = imgL.width
            val hL = imgL.height
            val pxL = IntArray(wL * hL)
            imgL.getRGB(0, 0, wL, hL, pxL, 0, wL)
            val bL = findBorders(pxL, wL, hL, topBottom = true, leftRight = true) ?: IntArray(4)

            val wR = imgR.width
            val hR = imgR.height
            val pxR = IntArray(wR * hR)
            imgR.getRGB(0, 0, wR, hR, pxR, 0, wR)
            val bR = findBorders(pxR, wR, hR, topBottom = true, leftRight = true) ?: IntArray(4)

            // [위, 아래, 바깥, 안쪽] 비율
            // 왼쪽 쪽: outer = left(bL[2]), inner = right(bL[3])
            val ownL = floatArrayOf(bL[0] / hL.toFloat(), bL[1] / hL.toFloat(), bL[2] / wL.toFloat(), bL[3] / wL.toFloat())
            // 오른쪽 쪽: outer = right(bR[3]), inner = left(bR[2])
            val ownR = floatArrayOf(bR[0] / hR.toFloat(), bR[1] / hR.toFloat(), bR[3] / wR.toFloat(), bR[2] / wR.toFloat())

            val cropL = matchCrop(ownL, ownR)
            val cropR = matchCrop(ownR, ownL)

            val cutTopL = (cropL[0] * hL).toInt()
            val cutBotL = (cropL[1] * hL).toInt()
            val cutLeftL = (cropL[2] * wL).toInt()  // outer
            val cutRightL = (cropL[3] * wL).toInt() // inner

            val cutTopR = (cropR[0] * hR).toInt()
            val cutBotR = (cropR[1] * hR).toInt()
            val cutLeftR = (cropR[3] * wR).toInt()  // inner
            val cutRightR = (cropR[2] * wR).toInt() // outer

            println("SPREAD $pairIndex L:[$cutTopL,$cutBotL,$cutLeftL,$cutRightL] R:[$cutTopR,$cutBotR,$cutLeftR,$cutRightR] $p1 | $p2")

            // 시각화: 높이 600 기준 캔버스
            val targetH = 600
            val scaleL = targetH.toDouble() / hL
            val scaleR = targetH.toDouble() / hR
            val swL = (wL * scaleL).toInt()
            val swR = (wR * scaleR).toInt()

            val origW = swL + swR
            val cwL = maxOf(1, wL - cutLeftL - cutRightL)
            val chL = maxOf(1, hL - cutTopL - cutBotL)
            val cwR = maxOf(1, wR - cutLeftR - cutRightR)
            val chR = maxOf(1, hR - cutTopR - cutBotR)

            val cropScaleL = targetH.toDouble() / chL
            val cropScaleR = targetH.toDouble() / chR
            val cswL = (cwL * cropScaleL).toInt()
            val cswR = (cwR * cropScaleR).toInt()
            val croppedW = cswL + cswR

            val totalW = origW + 20 + croppedW
            val canvas = BufferedImage(totalW, targetH, BufferedImage.TYPE_INT_RGB)
            val g = canvas.createGraphics()
            g.color = Color.DARK_GRAY
            g.fillRect(0, 0, totalW, targetH)

            // 1. 원본 두 쪽 + matchCrop 빨간 선
            g.drawImage(imgL, 0, 0, swL, targetH, null)
            g.drawImage(imgR, swL, 0, swR, targetH, null)
            g.color = Color.RED
            g.stroke = BasicStroke(2f)

            // L 선
            g.drawLine(0, (cutTopL * scaleL).toInt(), swL, (cutTopL * scaleL).toInt())
            g.drawLine(0, ((hL - cutBotL) * scaleL).toInt(), swL, ((hL - cutBotL) * scaleL).toInt())
            g.drawLine((cutLeftL * scaleL).toInt(), 0, (cutLeftL * scaleL).toInt(), targetH)
            g.drawLine(((wL - cutRightL) * scaleL).toInt(), 0, ((wL - cutRightL) * scaleL).toInt(), targetH)

            // R 선
            g.drawLine(swL, (cutTopR * scaleR).toInt(), swL + swR, (cutTopR * scaleR).toInt())
            g.drawLine(swL, ((hR - cutBotR) * scaleR).toInt(), swL + swR, ((hR - cutBotR) * scaleR).toInt())
            g.drawLine(swL + (cutLeftR * scaleR).toInt(), 0, swL + (cutLeftR * scaleR).toInt(), targetH)
            g.drawLine(swL + ((wR - cutRightR) * scaleR).toInt(), 0, swL + ((wR - cutRightR) * scaleR).toInt(), targetH)

            // 구분선
            g.color = Color.YELLOW
            g.fillRect(origW + 8, 0, 4, targetH)

            // 2. 잘라낸 두 쪽
            val subL = imgL.getSubimage(cutLeftL, cutTopL, cwL, chL)
            val subR = imgR.getSubimage(cutLeftR, cutTopR, cwR, chR)
            val startCropX = origW + 20
            g.drawImage(subL, startCropX, 0, cswL, targetH, null)
            g.drawImage(subR, startCropX + cswL, 0, cswR, targetH, null)

            g.dispose()
            ImageIO.write(canvas, "jpg", File(out, "%03d.jpg".format(pairIndex)))
            pairIndex++
        }
    }
}
