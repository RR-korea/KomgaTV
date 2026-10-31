package com.komgatv.feature.reader.continuous

/**
 * 세로 스크롤 리더의 보이는 타일 목록으로부터 저장할 진행 페이지 번호와 완독 여부를 계산합니다.
 */
object ContinuousProgressHelper {

    data class ProgressResult(
        val pageNumber: Int,
        val completed: Boolean,
    )

    /**
     * 현재 화면에 보이는 타일 목록을 바탕으로 읽기 진행 상태를 계산합니다.
     *
     * @param visibleTiles 현재 화면에 보이는 타일 목록
     * @param totalPages 책의 전체 페이지 수
     * @return 진행 상태 (타일이 없으면 null)
     */
    fun calculateProgress(
        visibleTiles: List<PageTile>,
        totalPages: Int,
    ): ProgressResult? {
        if (visibleTiles.isEmpty()) return null

        val maxPageNumber = visibleTiles.maxOf { it.pageNumber }
        val isCompleted = visibleTiles.any { tile ->
            tile.pageNumber == totalPages && tile.tileIndex == tile.totalTilesInPage - 1
        }

        return ProgressResult(
            pageNumber = maxPageNumber,
            completed = isCompleted,
        )
    }
}
