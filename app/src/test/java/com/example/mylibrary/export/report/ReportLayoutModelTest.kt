package com.example.mylibrary.export.report

import com.example.mylibrary.export.calendar.ExportIntRect
import com.example.mylibrary.export.calendar.calendarCoverSlotRects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ReportLayoutModelTest {
    @Test
    fun openingPageUsesCenteredTokenizedRhythmWithoutAStatusDivider() {
        assertEquals(0.5f, ReportOpeningLayoutPolicy.CONTENT_CENTER_FRACTION)
        assertFalse(ReportOpeningLayoutPolicy.DRAWS_STATUS_DIVIDER)
        assertTrue(ReportSpacing.OPENING_MAX_WIDTH in 720f..780f)
        assertTrue(ReportSpacing.OPENING_SUBTITLE_TO_INTRO in 64f..76f)
        assertTrue(ReportSpacing.OPENING_STATUS in 72f..96f)
    }

    @Test
    fun pdfUsesOneUniformScaleAndPreservesTheThreeByFourDesignRatio() {
        val transform = calculateReportPdfPageTransform(
            pageWidth = 595,
            pageHeight = 842
        )

        assertEquals(595f / 1_080f, transform.scale, 0.0001f)
        assertEquals(
            3f / 4f,
            transform.contentWidth / transform.contentHeight,
            0.0001f
        )
        assertEquals(0f, transform.offsetX, 0.0001f)
        assertTrue(transform.offsetY > 0f)

        val designCircleDiameter = 240f
        val pdfCircleWidth = designCircleDiameter * transform.scale
        val pdfCircleHeight = designCircleDiameter * transform.scale
        assertEquals(pdfCircleWidth, pdfCircleHeight, 0.0001f)
    }

    @Test
    fun calendarExportSlotsUseTheSharedHomeCalendarCoverGeometry() {
        (1..4).forEach { count ->
            val slots = calendarCoverSlotRects(
                ExportIntRect(0, 0, 100, 120),
                count
            )
            assertEquals(count, slots.size)
        }
    }

    @Test
    fun dynamicTableColumnsMeasureContentAndStayInsideAvailableWidth() {
        val layout = calculateReportTableColumnWidths(
            headers = listOf("书籍", "分类", "评分"),
            rows = listOf(
                listOf("制造消费者", "个人成长", "4.5"),
                listOf("子弹笔记", "个人成长", "4.0")
            ),
            availableWidth = 920f,
            gap = 28f,
            measureText = { text, _ -> text.length * 20f }
        )

        assertEquals(3, layout.widths.size)
        assertTrue(layout.totalWidth <= 920f)
        assertTrue(layout.widths[0] >= 920f * 0.28f)
        assertTrue(layout.widths.drop(1).all { it >= 920f * 0.15f })
        assertFalse(ReportItemTablePolicy.DRAWS_DIVIDERS)
        assertEquals(3, ReportItemTablePolicy.MAX_FIELD_COLUMNS)
    }

    @Test
    fun collageRowsHaveNoHorizontalGapsAndPreserveAspectRatios() {
        val ratios = listOf(2.0 / 3.0, 0.75, 1.0, 2.0 / 3.0, 0.8)
        val placements = calculateReportCollagePlacements(
            aspectRatios = ratios,
            availableWidth = 920f,
            availableHeight = 1_150f
        )

        assertEquals(ratios.size, placements.size)
        placements.zip(ratios).forEach { (placement, ratio) ->
            assertEquals(
                ratio,
                (placement.width / placement.height).toDouble(),
                0.001
            )
        }
        placements.groupBy { it.top }.values.forEach { row ->
            val ordered = row.sortedBy { it.left }
            assertEquals(0f, ordered.first().left, 0.001f)
            assertEquals(920f, ordered.last().right, 0.001f)
            ordered.zipWithNext().forEach { (left, right) ->
                assertEquals(left.right, right.left, 0.001f)
            }
        }
        assertTrue(placements.maxOf { it.bottom } <= 1_150f)
    }

    @Test
    fun singleCollageCoverIsCenteredWithoutCropping() {
        val placement = calculateReportCollagePlacements(
            aspectRatios = listOf(2.0 / 3.0),
            availableWidth = 920f,
            availableHeight = 1_150f
        ).single()

        assertEquals(
            2.0 / 3.0,
            (placement.width / placement.height).toDouble(),
            0.001
        )
        assertEquals(460f, (placement.left + placement.right) / 2f, 0.001f)
        assertEquals(575f, (placement.top + placement.bottom) / 2f, 0.001f)
    }
}
