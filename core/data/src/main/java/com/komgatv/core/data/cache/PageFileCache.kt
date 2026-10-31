package com.komgatv.core.data.cache

import java.io.File

/**
 * Komga 페이지 이미지 파일 캐시. 세로 스크롤 리더가 파일에서 조각(타일)만 디코딩하려고 쓴다.
 */
interface PageFileCache {
    /**
     * 책 페이지 이미지 파일. 캐시에 있으면 그대로, 없으면 세션(인증 포함)으로 받아 저장한 뒤 돌려준다.
     *
     * @param pageNumber 1부터 시작
     * @throws HttpStatusException 서버가 성공이 아닌 응답을 줬을 때
     */
    suspend fun getPageFile(bookId: String, pageNumber: Int): File
}
