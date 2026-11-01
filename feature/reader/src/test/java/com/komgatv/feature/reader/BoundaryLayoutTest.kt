package com.komgatv.feature.reader

import com.komgatv.core.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** 갈 권은 그리로 가는 키가 있는 쪽에 */
class BoundaryLayoutTest {

    @Test
    fun `오른쪽에서 왼쪽 - 다음 권은 왼쪽(◀), 이전 권은 오른쪽(▶)`() {
        assertEquals(BoundaryLayout(true, "◀", "▶"), BoundaryLayout.of(toNext = true, ReadingDirection.RIGHT_TO_LEFT, continuous = false))
        assertEquals(BoundaryLayout(false, "▶", "◀"), BoundaryLayout.of(toNext = false, ReadingDirection.RIGHT_TO_LEFT, continuous = false))
    }

    @Test
    fun `왼쪽에서 오른쪽 - 다음 권은 오른쪽(▶), 이전 권은 왼쪽(◀)`() {
        assertEquals(BoundaryLayout(false, "▶", "◀"), BoundaryLayout.of(toNext = true, ReadingDirection.LEFT_TO_RIGHT, continuous = false))
        assertEquals(BoundaryLayout(true, "◀", "▶"), BoundaryLayout.of(toNext = false, ReadingDirection.LEFT_TO_RIGHT, continuous = false))
    }

    @Test
    fun `세로 스크롤은 읽기 방향과 관계없이 아래가 다음 권, 위가 이전 권`() {
        assertEquals(BoundaryLayout(false, "▼", "▲"), BoundaryLayout.of(toNext = true, ReadingDirection.RIGHT_TO_LEFT, continuous = true))
        assertEquals(BoundaryLayout(true, "▲", "▼"), BoundaryLayout.of(toNext = false, ReadingDirection.RIGHT_TO_LEFT, continuous = true))
    }

    @Test
    fun `화살표는 지금 권에서 갈 권 쪽을 가리킨다`() {
        assertEquals("‹", BoundaryLayout(true, "◀", "▶").arrow)
        assertEquals("›", BoundaryLayout(false, "▶", "◀").arrow)
    }
}
