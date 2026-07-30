package com.example.mylibrary.export.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.export.calendar.CalendarSceneCoverLoader
import com.example.mylibrary.export.calendar.CalendarSceneStyle
import com.example.mylibrary.export.calendar.CalendarSnapshotSceneRenderer
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.ui.settings.ReportShowcaseStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class ReportPageSpec(
    val width: Int,
    val height: Int,
    val marginLeft: Float,
    val marginTop: Float,
    val marginRight: Float,
    val marginBottom: Float,
    val coverDecodeScale: Float
) {
    val contentWidth: Float get() = width - marginLeft - marginRight
    val contentHeight: Float get() = height - marginTop - marginBottom

    companion object {
        val PNG = ReportPageSpec(
            width = 1_080,
            height = 1_440,
            marginLeft = ReportSpacing.PAGE_HORIZONTAL,
            marginTop = ReportSpacing.PAGE_VERTICAL,
            marginRight = ReportSpacing.PAGE_HORIZONTAL,
            marginBottom = ReportSpacing.PAGE_VERTICAL,
            coverDecodeScale = 1f
        )
        val PDF_A4 = ReportPageSpec(
            width = 595,
            height = 842,
            marginLeft = 44f,
            marginTop = 40f,
            marginRight = 44f,
            marginBottom = 40f,
            coverDecodeScale = 4.2f
        )
    }
}

fun interface ReportCoverLoader {
    suspend fun load(path: String, maxDimension: Int): Bitmap?
}

internal data class ReportRelativePlacement(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

object ReportLayoutEngine {
    suspend fun draw(
        canvas: Canvas,
        page: ReportPageModel,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot,
        coverLoader: ReportCoverLoader,
        drawBackground: Boolean = true,
        allowTransparency: Boolean = true
    ) {
        if (drawBackground) {
            drawThemeBackground(canvas, spec, theme)
        }
        when (page) {
            is ReportPageModel.TimeAndStatus ->
                drawTimeAndStatus(canvas, page, spec, theme)
            is ReportPageModel.WorkShowcase ->
                drawWorkShowcase(
                    canvas,
                    page,
                    spec,
                    theme,
                    coverLoader,
                    allowTransparency
                )
            is ReportPageModel.AnnualCalendarOverview ->
                drawAnnualCalendarOverview(canvas, page, spec, theme, coverLoader)
            is ReportPageModel.ItemCustomInformation ->
                drawItemCustomInformation(canvas, page, spec, theme)
            is ReportPageModel.CustomInformationInsights ->
                drawCustomInformationInsights(canvas, page, spec, theme)
            is ReportPageModel.QuoteSelection ->
                drawQuoteSelection(canvas, page, spec, theme)
        }
    }

    private suspend fun drawAnnualCalendarOverview(
        canvas: Canvas,
        page: ReportPageModel.AnnualCalendarOverview,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot,
        coverLoader: ReportCoverLoader
    ) {
        drawPageHeading(canvas, page.heading, page.subtitle, spec, theme)
        val unit = unit(spec)
        val columns = 3
        val rows = 4
        val gapX = 20f * unit
        val gapY = 18f * unit
        val summaryHeight = if (page.rhythmSummary.isEmpty()) 0f else 120f * unit
        val gridTop = spec.marginTop + 128f * unit
        val gridBottom = spec.height - spec.marginBottom - summaryHeight
        val blockWidth = (spec.contentWidth - gapX * (columns - 1)) / columns
        val blockHeight = (gridBottom - gridTop - gapY * (rows - 1)) / rows
        page.months.take(12).forEachIndexed { index, month ->
            val left = spec.marginLeft + (index % columns) * (blockWidth + gapX)
            val top = gridTop + (index / columns) * (blockHeight + gapY)
            drawAnnualMonth(
                canvas,
                month,
                page.mediaScope,
                RectF(left, top, left + blockWidth, top + blockHeight),
                spec,
                theme,
                coverLoader
            )
        }
        if (page.rhythmSummary.isNotEmpty()) {
            var baseline = gridBottom + 34f * unit
            page.rhythmSummary.take(3).forEach { line ->
                canvas.drawText(
                    ellipsize(
                        line,
                        spec.contentWidth,
                        textPaint(
                            theme.textSecondary,
                            ReportTypography.METADATA * unit,
                            theme.contentTypeface
                        )
                    ),
                    spec.marginLeft,
                    baseline,
                    textPaint(
                        theme.textSecondary,
                        ReportTypography.METADATA * unit,
                        theme.contentTypeface
                    )
                )
                baseline += ReportTypography.METADATA_LINE_HEIGHT * unit
            }
        }
    }

    private suspend fun drawAnnualMonth(
        canvas: Canvas,
        month: ReportAnnualMonth,
        mediaScope: ReportMediaScope,
        bounds: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot,
        coverLoader: ReportCoverLoader
    ) {
        val unit = unit(spec)
        canvas.drawText(
            "${month.month}月 / ${ENGLISH_MONTHS[month.month - 1]}",
            bounds.left,
            bounds.top + 20f * unit,
            textPaint(theme.textPrimary, 16f * unit, theme.headingTypeface)
        )
        val weekdayTop = bounds.top + 31f * unit
        val weekdayHeight = 14f * unit
        val statsHeight = 54f * unit
        val calendarTop = weekdayTop + weekdayHeight
        val calendarBottom = bounds.bottom - statsHeight
        val cellWidth = bounds.width() / 7f
        val rowCount = month.calendar.rowCount
        val cellHeight = (calendarBottom - calendarTop) / rowCount
        MINI_WEEKDAYS.forEachIndexed { index, label ->
            canvas.drawText(
                label,
                bounds.left + (index + 0.5f) * cellWidth,
                weekdayTop + 10f * unit,
                textPaint(
                    theme.textSecondary,
                    8f * unit,
                    theme.contentTypeface,
                    Paint.Align.CENTER
                )
            )
        }
        val dayCells = month.calendar.cells.indices.map { index ->
            RectF(
                bounds.left + index % 7 * cellWidth,
                calendarTop + index / 7 * cellHeight,
                bounds.left + (index % 7 + 1) * cellWidth,
                calendarTop + (index / 7 + 1) * cellHeight
            )
        }
        CalendarSnapshotSceneRenderer.drawCells(
            canvas = canvas,
            snapshot = month.calendar,
            dayCells = dayCells,
            style = CalendarSceneStyle(
                dateTextSize = 7f * unit,
                emptyCellInset = 0f,
                emptyCellCornerRadius = 1.5f * unit,
                emptyCellAlpha = 0,
                coverCornerRadius = 1.5f * unit
            ),
            theme = theme,
            coverLoader = CalendarSceneCoverLoader { cover, maxDimension ->
                listOf(cover.coverPath, cover.thumbnailPath)
                    .filterNotNull()
                    .filter(String::isNotBlank)
                    .distinct()
                    .firstNotNullOfOrNull { path ->
                        coverLoader.load(
                            path,
                            (maxDimension * spec.coverDecodeScale)
                                .roundToInt()
                                .coerceAtLeast(1)
                        )
                    }
            }
        )
        val statistics = buildString {
            append(month.itemCount).append(
                when (mediaScope) {
                    ReportMediaScope.ALL -> " 部作品"
                    ReportMediaScope.BOOK -> " 本书"
                    ReportMediaScope.MOVIE -> " 部电影"
                }
            )
            month.totalDurationMinutes?.let {
                append(" · ").append(formatDuration(it))
            }
        }
        canvas.drawText(
            ellipsize(
                statistics,
                bounds.width(),
                textPaint(
                    theme.textSecondary,
                    10f * unit,
                    theme.contentTypeface
                )
            ),
            bounds.left,
            bounds.bottom - 30f * unit,
            textPaint(
                theme.textSecondary,
                10f * unit,
                theme.contentTypeface
            )
        )
        month.caption?.let {
            canvas.drawText(
                it,
                bounds.right,
                bounds.bottom - 14f * unit,
                textPaint(
                    theme.accent,
                    9f * unit,
                    theme.contentTypeface,
                    Paint.Align.RIGHT
                )
            )
        }
        canvas.drawLine(
            bounds.left,
            bounds.bottom - 5f * unit,
            bounds.left + bounds.width() * month.activityFraction,
            bounds.bottom - 5f * unit,
            strokePaint(theme.accent, maxOf(1f, 3f * unit))
        )
    }

    private fun drawThemeBackground(
        canvas: Canvas,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        canvas.drawColor(opaque(theme.backgroundColor))
        val background = theme.backgroundBitmap
            ?.takeUnless { it.isRecycled }
            ?: return
        try {
            drawCenterCrop(
                canvas,
                background,
                RectF(0f, 0f, spec.width.toFloat(), spec.height.toFloat())
            )
        } catch (error: Exception) {
            Log.w(TAG, "Unable to draw report theme background; using fallback", error)
        }
    }

    private fun drawTimeAndStatus(
        canvas: Canvas,
        page: ReportPageModel.TimeAndStatus,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        val centerX =
            spec.width * ReportOpeningLayoutPolicy.CONTENT_CENTER_FRACTION
        val contentWidth = minOf(
            spec.contentWidth,
            ReportSpacing.OPENING_MAX_WIDTH * unit
        )
        var y = spec.marginTop + 54f * unit
        canvas.drawText(
            page.heading,
            centerX,
            y,
            textPaint(
                theme.textPrimary,
                ReportTypography.PAGE_TITLE * unit,
                theme.headingTypeface,
                Paint.Align.CENTER
            )
        )
        y += ReportSpacing.OPENING_TITLE_TO_SUBTITLE * unit +
            ReportTypography.PAGE_SUBTITLE * unit
        canvas.drawText(
            page.subtitle,
            centerX,
            y,
            textPaint(
                theme.textSecondary,
                ReportTypography.PAGE_SUBTITLE * unit,
                theme.contentTypeface,
                Paint.Align.CENTER
            )
        )
        y += ReportSpacing.OPENING_SUBTITLE_TO_INTRO * unit
        y = drawParagraph(
            canvas = canvas,
            text = page.itemCountLine,
            x = centerX,
            firstBaseline = y,
            maxWidth = contentWidth,
            maxLines = 2,
            unit = unit,
            paint = textPaint(
                theme.textPrimary,
                ReportTypography.SECTION_TITLE * unit,
                theme.headingTypeface,
                Paint.Align.CENTER
            )
        )
        y += ReportSpacing.OPENING_INTRO_TO_STATISTICS * unit
        page.narrativeSections.forEachIndexed { sectionIndex, section ->
            section.heading?.let { heading ->
                canvas.drawText(
                    heading,
                    centerX,
                    y,
                    textPaint(
                        theme.accent,
                        ReportTypography.SECTION_TITLE * unit,
                        theme.headingTypeface,
                        Paint.Align.CENTER
                    )
                )
                y += 44f * unit
            }
            section.paragraphs.forEach { paragraph ->
                y = drawParagraph(
                    canvas = canvas,
                    text = paragraph,
                    x = centerX,
                    firstBaseline = y,
                    maxWidth = contentWidth,
                    maxLines = 3,
                    unit = unit,
                    paint = textPaint(
                        theme.textPrimary,
                        ReportTypography.BODY * unit,
                        theme.contentTypeface,
                        Paint.Align.CENTER
                    )
                )
                y += ReportSpacing.OPENING_PARAGRAPH * unit
            }
            if (sectionIndex != page.narrativeSections.lastIndex) {
                y += ReportSpacing.OPENING_MEDIA_SECTION * unit
            }
        }
        page.statusSentence?.let { sentence ->
            y += ReportSpacing.OPENING_STATUS * unit
            drawParagraph(
                canvas = canvas,
                text = sentence,
                x = centerX,
                firstBaseline = y,
                maxWidth = contentWidth,
                maxLines = 3,
                unit = unit,
                paint = textPaint(
                    theme.textSecondary,
                    ReportTypography.BODY * unit,
                    theme.contentTypeface,
                    Paint.Align.CENTER
                )
            )
        }
        page.closingLine?.let {
            canvas.drawText(
                it,
                centerX,
                spec.height - spec.marginBottom,
                textPaint(
                    theme.textSecondary,
                    ReportTypography.METADATA * unit,
                    theme.contentTypeface,
                    Paint.Align.CENTER
                )
            )
        }
    }

    private suspend fun drawWorkShowcase(
        canvas: Canvas,
        page: ReportPageModel.WorkShowcase,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot,
        coverLoader: ReportCoverLoader,
        allowTransparency: Boolean
    ) {
        drawPageHeading(canvas, page.heading, null, spec, theme)
        val unit = unit(spec)
        val area = RectF(
            spec.marginLeft,
            spec.marginTop + ReportSpacing.TITLE_BLOCK * unit,
            spec.width - spec.marginRight,
            spec.height - spec.marginBottom
        )
        when (page.style) {
            ReportShowcaseStyle.COLLAGE ->
                drawCollage(canvas, page.items, area, spec, coverLoader)
            ReportShowcaseStyle.GRID ->
                drawGrid(
                    canvas,
                    page.items,
                    area,
                    spec,
                    theme,
                    coverLoader,
                    allowTransparency
                )
        }
    }

    private suspend fun drawCollage(
        canvas: Canvas,
        items: List<ReportItemSnapshot>,
        area: RectF,
        spec: ReportPageSpec,
        coverLoader: ReportCoverLoader
    ) {
        val validItems = items.filter { it.resolvedCoverAspectRatio != null }
        val placements = calculateReportCollagePlacements(
            aspectRatios = validItems.map { requireNotNull(it.resolvedCoverAspectRatio) },
            availableWidth = area.width(),
            availableHeight = area.height()
        )
        validItems.zip(placements).forEach { (item, placement) ->
            val bounds = RectF(
                area.left + placement.left,
                area.top + placement.top,
                area.left + placement.right,
                area.top + placement.bottom
            )
            drawFullCover(canvas, item, bounds, spec, coverLoader)
        }
    }

    private suspend fun drawGrid(
        canvas: Canvas,
        items: List<ReportItemSnapshot>,
        area: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot,
        coverLoader: ReportCoverLoader,
        allowTransparency: Boolean
    ) {
        val unit = unit(spec)
        val gapX = ReportSpacing.COLUMN * unit
        val gapY = ReportSpacing.GRID_ROW * unit
        val columns = 2
        val rows = 3
        val cellWidth = (area.width() - gapX) / columns
        val cellHeight = (area.height() - gapY * (rows - 1)) / rows
        items.take(columns * rows).forEachIndexed { index, item ->
            val left = area.left + (index % columns) * (cellWidth + gapX)
            val top = area.top + (index / columns) * (cellHeight + gapY)
            val coverHeight = cellHeight - 76f * unit
            val coverWidth = minOf(cellWidth, coverHeight * 2f / 3f)
            val cover = RectF(left, top, left + coverWidth, top + coverHeight)
            drawCoverOrPlaceholder(
                canvas,
                item,
                cover,
                spec,
                theme,
                coverLoader,
                allowTransparency
            )

            val titlePaint = textPaint(
                theme.textPrimary,
                ReportTypography.GRID_TITLE * unit,
                theme.headingTypeface
            )
            val titleFirstBaseline = cover.bottom +
                (ReportSpacing.GRID_COVER_TO_TITLE + ReportTypography.GRID_TITLE) * unit
            val titleLineCount = drawWrappedText(
                canvas,
                item.title,
                left,
                titleFirstBaseline,
                cellWidth,
                ReportTypography.GRID_TITLE_LINE_HEIGHT * unit,
                2,
                titlePaint
            )
            item.creator?.takeIf(String::isNotBlank)?.let { creator ->
                val creatorPaint = textPaint(
                    theme.textSecondary,
                    ReportTypography.GRID_CREATOR * unit,
                    theme.contentTypeface
                )
                val creatorBaseline = titleFirstBaseline +
                    (titleLineCount - 1).coerceAtLeast(0) *
                    ReportTypography.GRID_TITLE_LINE_HEIGHT * unit +
                    (
                        ReportSpacing.GRID_TITLE_TO_CREATOR +
                            ReportTypography.GRID_CREATOR_LINE_HEIGHT
                        ) * unit
                canvas.drawText(
                    ellipsize(
                        creator,
                        cellWidth,
                        creatorPaint
                    ),
                    left,
                    creatorBaseline,
                    creatorPaint
                )
            }
        }
    }

    private fun drawItemCustomInformation(
        canvas: Canvas,
        page: ReportPageModel.ItemCustomInformation,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        drawPageHeading(canvas, page.heading, page.subtitle, spec, theme)
        val unit = unit(spec)
        var y = spec.marginTop + 152f * unit
        page.sections.forEach { section ->
            val headerPaint = textPaint(
                theme.textPrimary,
                ReportTypography.BODY * unit,
                theme.headingTypeface
            )
            val valuePaint = textPaint(
                theme.textSecondary,
                ReportTypography.METADATA * unit,
                theme.contentTypeface
            )
            val headers = listOf(section.itemHeading) +
                section.columns.map(ReportItemInformationColumn::heading)
            val rows = section.entries.map { entry ->
                listOf(entry.title) + entry.values
            }
            val tableLayout = calculateReportTableColumnWidths(
                headers = headers,
                rows = rows,
                availableWidth = spec.contentWidth,
                gap = ReportSpacing.TABLE_COLUMN_MAX_GAP * unit,
                measureText = { text, _ ->
                    maxOf(
                        headerPaint.measureText(text),
                        valuePaint.measureText(text)
                    )
                }
            )
            val tableLeft = spec.marginLeft +
                (spec.contentWidth - tableLayout.totalWidth) / 2f
            val columnX = mutableListOf<Float>()
            var nextX = tableLeft
            tableLayout.widths.forEach { width ->
                columnX += nextX
                nextX += width + tableLayout.gap
            }
            headers.forEachIndexed { index, header ->
                canvas.drawText(
                    ellipsize(header, tableLayout.widths[index], headerPaint),
                    columnX[index],
                    y,
                    headerPaint
                )
            }
            y += ReportSpacing.TABLE_HEADER_TO_BODY * unit
            section.entries.forEach { entry ->
                val values = listOf(entry.title) + entry.values
                var maximumLines = 1
                values.forEachIndexed { index, value ->
                    maximumLines = maxOf(
                        maximumLines,
                        drawWrappedText(
                            canvas,
                            value,
                            columnX[index],
                            y,
                            tableLayout.widths[index],
                            ReportTypography.METADATA_LINE_HEIGHT * unit,
                            2,
                            if (index == 0) {
                                textPaint(
                                    theme.textPrimary,
                                    ReportTypography.METADATA * unit,
                                    theme.headingTypeface
                                )
                            } else {
                                valuePaint
                            }
                        )
                    )
                }
                y += maximumLines * ReportTypography.METADATA_LINE_HEIGHT * unit +
                    ReportSpacing.TABLE_ROW * unit
            }
            y += ReportSpacing.SECTION * unit
        }
        if (page.truncated) {
            canvas.drawText(
                "更多作品信息可在资料库查看",
                spec.marginLeft,
                spec.height - spec.marginBottom,
                textPaint(
                    theme.textSecondary,
                    ReportTypography.METADATA * unit,
                    theme.contentTypeface
                )
            )
        }
    }

    private fun drawCustomInformationInsights(
        canvas: Canvas,
        page: ReportPageModel.CustomInformationInsights,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        drawPageHeading(canvas, page.heading, page.subtitle, spec, theme)
        val unit = unit(spec)
        val contentTop = spec.marginTop + 150f * unit
        val contentBottom = spec.height - spec.marginBottom
        val hasFields = page.fieldStatistics.isNotEmpty()
        val hasWords = page.wordCloud.isNotEmpty()
        val fieldBottom = when {
            hasFields && hasWords -> contentTop + (contentBottom - contentTop) * 0.58f
            hasFields -> contentBottom
            else -> contentTop
        }
        if (hasFields) {
            drawFieldStatisticBlocks(
                canvas,
                page.fieldStatistics,
                RectF(spec.marginLeft, contentTop, spec.width - spec.marginRight, fieldBottom),
                spec,
                theme
            )
        }
        if (hasWords) {
            val wordTop = if (hasFields) fieldBottom + 32f * unit else contentTop
            canvas.drawText(
                page.keywordHeading,
                spec.marginLeft,
                wordTop + 28f * unit,
                textPaint(
                    theme.textPrimary,
                    ReportTypography.SECTION_TITLE * unit,
                    theme.headingTypeface
                )
            )
            drawWordCloud(
                canvas,
                page.wordCloud,
                RectF(
                    spec.marginLeft,
                    wordTop + 50f * unit,
                    spec.width - spec.marginRight,
                    contentBottom
                ),
                spec,
                theme
            )
        }
    }

    private fun drawFieldStatisticBlocks(
        canvas: Canvas,
        blocks: List<ReportFieldStatisticBlock>,
        area: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        val gap = ReportSpacing.COLUMN * unit
        val columns = 2
        val rows = ceil(blocks.size / columns.toDouble()).toInt().coerceAtLeast(1)
        val width = (area.width() - gap) / columns
        val height = (area.height() - gap * (rows - 1)) / rows
        blocks.forEachIndexed { index, block ->
            val left = area.left + (index % columns) * (width + gap)
            val top = area.top + (index / columns) * (height + gap)
            drawFieldStatisticBlock(
                canvas,
                block,
                RectF(left, top, left + width, top + height),
                spec,
                theme
            )
        }
    }

    private fun drawFieldStatisticBlock(
        canvas: Canvas,
        block: ReportFieldStatisticBlock,
        bounds: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        val statistic = block.statistic
        val mediaPrefix = when (block.typeKind) {
            ItemTypeKind.BOOK -> "书籍 · "
            ItemTypeKind.MOVIE -> "电影 · "
            ItemTypeKind.CUSTOM -> ""
        }
        canvas.drawText(
            ellipsize(
                mediaPrefix + statistic.fieldName,
                bounds.width(),
                textPaint(
                    theme.textPrimary,
                    ReportTypography.PAGE_SUBTITLE * unit,
                    theme.headingTypeface
                )
            ),
            bounds.left,
            bounds.top + 27f * unit,
            textPaint(
                theme.textPrimary,
                ReportTypography.PAGE_SUBTITLE * unit,
                theme.headingTypeface
            )
        )
        when (statistic) {
            is CustomFieldStatistic.Numeric ->
                drawNumericStatistic(canvas, statistic, bounds, spec, theme)
            is CustomFieldStatistic.OptionDistribution ->
                drawOptionStatistic(
                    canvas,
                    statistic,
                    block.fieldType,
                    bounds,
                    spec,
                    theme
                )
            is CustomFieldStatistic.Rating ->
                drawRatingStatistic(canvas, statistic, bounds, spec, theme)
        }
    }

    private fun drawNumericStatistic(
        canvas: Canvas,
        statistic: CustomFieldStatistic.Numeric,
        bounds: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        val byAggregation = statistic.metrics.associateBy { it.aggregation }
        val hero = byAggregation[FieldAggregation.AVERAGE]
            ?: byAggregation[FieldAggregation.SUM]
            ?: statistic.metrics.firstOrNull()
            ?: return
        canvas.drawText(
            metricText(hero.value, hero.unit),
            bounds.left,
            bounds.top + 88f * unit,
            textPaint(
                theme.accent,
                ReportTypography.HERO_VALUE * unit,
                theme.headingTypeface
            )
        )
        val minimum = byAggregation[FieldAggregation.MINIMUM]
        val maximum = byAggregation[FieldAggregation.MAXIMUM]
        if (minimum != null && maximum != null) {
            val y = bounds.top + 137f * unit
            canvas.drawText(
                metricText(minimum.value, minimum.unit),
                bounds.left,
                y,
                textPaint(
                    theme.textSecondary,
                    ReportTypography.METADATA * unit,
                    theme.contentTypeface
                )
            )
            val rightPaint = textPaint(
                theme.textSecondary,
                ReportTypography.METADATA * unit,
                theme.contentTypeface,
                Paint.Align.RIGHT
            )
            canvas.drawText(
                metricText(maximum.value, maximum.unit),
                bounds.right,
                y,
                rightPaint
            )
            val lineLeft = bounds.left + bounds.width() * 0.25f
            val lineRight = bounds.right - bounds.width() * 0.25f
            canvas.drawLine(
                lineLeft,
                y - 6f * unit,
                lineRight,
                y - 6f * unit,
                strokePaint(theme.border, maxOf(1f, unit))
            )
            canvas.drawCircle(
                (lineLeft + lineRight) / 2f,
                y - 6f * unit,
                4f * unit,
                fillPaint(theme.accent)
            )
        }
        byAggregation[FieldAggregation.SUM]?.takeUnless { it === hero }?.let {
            canvas.drawText(
                "总计 ${metricText(it.value, it.unit)}",
                bounds.left,
                bounds.bottom - 14f * unit,
                textPaint(
                    theme.textSecondary,
                    ReportTypography.METADATA * unit,
                    theme.contentTypeface
                )
            )
        }
    }

    private fun drawOptionStatistic(
        canvas: Canvas,
        statistic: CustomFieldStatistic.OptionDistribution,
        fieldType: FieldDataType,
        bounds: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        val values = statistic.entries.take(5)
        val maximum = values.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: return
        val top = bounds.top + 58f * unit
        val rowHeight = minOf(34f * unit, (bounds.height() - 68f * unit) / values.size)
        values.forEachIndexed { index, value ->
            val baseline = top + index * rowHeight
            val labelPaint = textPaint(
                theme.textSecondary,
                ReportTypography.METADATA * unit,
                theme.contentTypeface
            )
            canvas.drawText(
                ellipsize(value.label, bounds.width() * 0.38f, labelPaint),
                bounds.left,
                baseline,
                labelPaint
            )
            val trackLeft = bounds.left + bounds.width() * 0.42f
            val trackRight = bounds.right - 35f * unit
            val fraction = value.count / maximum.toFloat()
            if (fieldType == FieldDataType.MULTI_SELECT) {
                val dots = (fraction * 5).roundToInt().coerceIn(1, 5)
                repeat(dots) { dot ->
                    canvas.drawCircle(
                        trackLeft + dot * 12f * unit,
                        baseline - 5f * unit,
                        3.5f * unit,
                        fillPaint(theme.accent)
                    )
                }
            } else {
                canvas.drawRect(
                    trackLeft,
                    baseline - 10f * unit,
                    trackLeft + (trackRight - trackLeft) * fraction,
                    baseline - 3f * unit,
                    fillPaint(theme.accent)
                )
            }
            canvas.drawText(
                value.count.toString(),
                bounds.right,
                baseline,
                textPaint(
                    theme.textPrimary,
                    ReportTypography.METADATA * unit,
                    theme.contentTypeface,
                    Paint.Align.RIGHT
                )
            )
        }
    }

    private fun drawRatingStatistic(
        canvas: Canvas,
        statistic: CustomFieldStatistic.Rating,
        bounds: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        statistic.average?.let {
            canvas.drawText(
                it,
                bounds.left,
                bounds.top + 88f * unit,
                textPaint(
                    theme.accent,
                    ReportTypography.HERO_VALUE * unit,
                    theme.headingTypeface
                )
            )
        }
        val values = statistic.distribution.take(5)
        val maximum = values.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: return
        val barWidth = (bounds.width() - 16f * unit * (values.size - 1)) /
            values.size.coerceAtLeast(1)
        values.forEachIndexed { index, value ->
            val left = bounds.left + index * (barWidth + 16f * unit)
            val height = 55f * unit * value.count / maximum
            canvas.drawRect(
                left,
                bounds.bottom - height - 25f * unit,
                left + barWidth,
                bounds.bottom - 25f * unit,
                fillPaint(theme.accent)
            )
            canvas.drawText(
                value.label,
                left + barWidth / 2f,
                bounds.bottom,
                textPaint(
                    theme.textSecondary,
                    13f * unit,
                    theme.contentTypeface,
                    Paint.Align.CENTER
                )
            )
        }
    }

    private fun drawWordCloud(
        canvas: Canvas,
        words: List<ReportWordCloudPlacement>,
        bounds: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        words.forEach { word ->
            val baseSize = when (word.tier) {
                ReportWordTier.HIGHEST -> ReportTypography.WORD_HIGHEST
                ReportWordTier.HIGH -> ReportTypography.WORD_HIGH
                ReportWordTier.MEDIUM -> ReportTypography.WORD_MEDIUM
                ReportWordTier.OTHER -> ReportTypography.WORD_OTHER
            } * unit
            val maxWidth = bounds.width() * word.maxWidthFraction
            val color = when (word.tier) {
                ReportWordTier.HIGHEST,
                ReportWordTier.HIGH -> theme.accent
                ReportWordTier.MEDIUM -> theme.textPrimary
                ReportWordTier.OTHER -> theme.textSecondary
            }
            val paint = textPaint(color, baseSize, theme.headingTypeface, Paint.Align.CENTER)
            shrinkTextToWidth(word.text, maxWidth, paint, 13f * unit)
            val x = bounds.left + bounds.width() * word.centerXFraction
            val centerY = bounds.top + bounds.height() * word.centerYFraction
            val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(word.text, x, baseline, paint)
        }
    }

    private fun drawQuoteSelection(
        canvas: Canvas,
        page: ReportPageModel.QuoteSelection,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        drawPageHeading(canvas, page.heading, null, spec, theme)
        val unit = unit(spec)
        val area = RectF(
            spec.marginLeft,
            spec.marginTop + ReportSpacing.TITLE_BLOCK * unit,
            spec.width - spec.marginRight,
            spec.height - spec.marginBottom
        )
        val cells = quoteCells(page.layout, area, 22f * unit)
        page.quotes.zip(cells).forEach { (selected, bounds) ->
            drawQuote(canvas, selected, bounds, spec, theme)
        }
    }

    private fun drawQuote(
        canvas: Canvas,
        selected: ReportSelectedQuote,
        bounds: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        canvas.drawText(
            "“",
            bounds.left,
            bounds.top + 48f * unit,
            textPaint(
                theme.accent,
                ReportTypography.QUOTE_MARK * unit,
                theme.headingTypeface
            )
        )
        val textTop = bounds.top + 72f * unit
        val attributionReserve = 72f * unit
        val maxLines = floor(
            (bounds.bottom - textTop - attributionReserve) /
                (ReportTypography.BODY_LINE_HEIGHT * unit)
        ).toInt().coerceIn(2, 6)
        drawWrappedText(
            canvas,
            selected.quote.content,
            bounds.left + 22f * unit,
            textTop,
            bounds.width() - 44f * unit,
            ReportTypography.BODY_LINE_HEIGHT * unit,
            maxLines,
            textPaint(
                theme.textPrimary,
                ReportTypography.BODY * unit,
                theme.contentTypeface
            )
        )
        canvas.drawText(
            "”",
            bounds.right,
            bounds.bottom - 58f * unit,
            textPaint(
                theme.accent,
                ReportTypography.QUOTE_MARK * unit,
                theme.headingTypeface,
                Paint.Align.RIGHT
            )
        )
        val location = listOfNotNull(
            selected.quote.chapter?.takeIf(String::isNotBlank),
            selected.quote.page?.takeIf(String::isNotBlank)?.let { "第${it}页" }
        ).joinToString(" · ")
        val attribution = listOfNotNull(
            selected.quote.itemTitle,
            selected.creator?.takeIf(String::isNotBlank),
            location.takeIf(String::isNotBlank)
        ).joinToString(" · ")
        canvas.drawText(
            ellipsize(
                attribution,
                bounds.width() - 44f * unit,
                textPaint(
                    theme.textSecondary,
                    ReportTypography.METADATA * unit,
                    theme.contentTypeface
                )
            ),
            bounds.right - 22f * unit,
            bounds.bottom - 13f * unit,
            textPaint(
                theme.textSecondary,
                ReportTypography.METADATA * unit,
                theme.contentTypeface,
                Paint.Align.RIGHT
            )
        )
    }

    private suspend fun drawFullCover(
        canvas: Canvas,
        item: ReportItemSnapshot,
        bounds: RectF,
        spec: ReportPageSpec,
        coverLoader: ReportCoverLoader
    ) {
        val path = item.coverPath ?: return
        val bitmap = coverLoader.load(
            path,
            (maxOf(bounds.width(), bounds.height()) * spec.coverDecodeScale)
                .roundToInt()
                .coerceAtLeast(1)
        )
        try {
            if (bitmap != null) {
                canvas.drawBitmap(
                    bitmap,
                    Rect(0, 0, bitmap.width, bitmap.height),
                    bounds,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
            }
        } finally {
            bitmap?.recycle()
        }
    }

    private suspend fun drawCoverOrPlaceholder(
        canvas: Canvas,
        item: ReportItemSnapshot,
        bounds: RectF,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot,
        coverLoader: ReportCoverLoader,
        allowTransparency: Boolean
    ) {
        val bitmap = item.coverPath?.let { path ->
            coverLoader.load(
                path,
                (maxOf(bounds.width(), bounds.height()) * spec.coverDecodeScale)
                    .roundToInt()
                    .coerceAtLeast(1)
            )
        }
        try {
            if (bitmap != null) {
                drawCenterCrop(canvas, bitmap, bounds)
            } else {
                canvas.drawRect(
                    bounds,
                    fillPaint(
                        if (allowTransparency) {
                            withAlpha(theme.border, 45)
                        } else {
                            opaque(theme.placeholderColor)
                        }
                    )
                )
                val marker = item.title.trim().firstOrNull()?.toString().orEmpty()
                if (marker.isNotEmpty()) {
                    val paint = textPaint(
                        theme.textSecondary,
                        minOf(bounds.width(), bounds.height()) * 0.2f,
                        theme.headingTypeface,
                        Paint.Align.CENTER
                    )
                    canvas.drawText(
                        marker,
                        bounds.centerX(),
                        bounds.centerY() - (paint.ascent() + paint.descent()) / 2f,
                        paint
                    )
                }
            }
        } finally {
            bitmap?.recycle()
        }
    }

    private fun drawPageHeading(
        canvas: Canvas,
        heading: String,
        subtitle: String?,
        spec: ReportPageSpec,
        theme: VisualExportThemeSnapshot
    ) {
        val unit = unit(spec)
        canvas.drawText(
            heading,
            spec.marginLeft,
            spec.marginTop + 48f * unit,
            textPaint(
                theme.textPrimary,
                ReportTypography.PAGE_TITLE * unit,
                theme.headingTypeface
            )
        )
        subtitle?.let {
            canvas.drawText(
                it,
                spec.marginLeft,
                spec.marginTop + 86f * unit,
                textPaint(
                    theme.textSecondary,
                    ReportTypography.PAGE_SUBTITLE * unit,
                    theme.contentTypeface
                )
            )
        }
    }

    private fun drawParagraph(
        canvas: Canvas,
        text: String,
        x: Float,
        firstBaseline: Float,
        maxWidth: Float,
        maxLines: Int,
        unit: Float,
        paint: Paint
    ): Float {
        val lines = drawWrappedText(
            canvas,
            text,
            x,
            firstBaseline,
            maxWidth,
            ReportTypography.BODY_LINE_HEIGHT * unit,
            maxLines,
            paint
        )
        return firstBaseline + lines * ReportTypography.BODY_LINE_HEIGHT * unit
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        firstBaseline: Float,
        maxWidth: Float,
        lineHeight: Float,
        maxLines: Int,
        paint: Paint
    ): Int {
        if (text.isBlank() || maxLines <= 0) return 0
        var remaining = text.trim()
        var line = 0
        while (remaining.isNotEmpty() && line < maxLines) {
            val count = paint.breakText(remaining, true, maxWidth, null)
                .coerceAtLeast(1)
            val hasMore = count < remaining.length
            var current = remaining.take(count)
            if (line == maxLines - 1 && hasMore) {
                current = ellipsize(remaining, maxWidth, paint)
                remaining = ""
            } else {
                remaining = remaining.drop(count).trimStart()
            }
            canvas.drawText(current, x, firstBaseline + line * lineHeight, paint)
            line += 1
        }
        return line
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = target.width() / target.height()
        val source = if (sourceRatio > targetRatio) {
            val width = bitmap.height * targetRatio
            val left = (bitmap.width - width) / 2f
            Rect(
                floor(left).toInt(),
                0,
                ceil(left + width).toInt(),
                bitmap.height
            )
        } else {
            val height = bitmap.width / targetRatio
            val top = (bitmap.height - height) / 2f
            Rect(
                0,
                floor(top).toInt(),
                bitmap.width,
                ceil(top + height).toInt()
            )
        }
        canvas.drawBitmap(
            bitmap,
            source,
            target,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = paint.breakText(
            text,
            true,
            maxWidth - paint.measureText(ellipsis),
            null
        ).coerceIn(0, text.length)
        while (end > 0 && paint.measureText(text.take(end) + ellipsis) > maxWidth) {
            end -= 1
        }
        return text.take(end).trimEnd() + ellipsis
    }

    private fun shrinkTextToWidth(
        text: String,
        maxWidth: Float,
        paint: Paint,
        minimumSize: Float
    ) {
        while (paint.textSize > minimumSize && paint.measureText(text) > maxWidth) {
            paint.textSize -= 1f
        }
    }

    private fun quoteCells(
        layout: ReportQuoteLayout,
        area: RectF,
        gap: Float
    ): List<RectF> = when (layout) {
        ReportQuoteLayout.ONE_CENTER -> listOf(
            RectF(
                area.left + area.width() * 0.08f,
                area.top + area.height() * 0.2f,
                area.right - area.width() * 0.08f,
                area.bottom - area.height() * 0.2f
            )
        )
        ReportQuoteLayout.TWO_ROWS -> {
            val height = (area.height() - gap) / 2f
            listOf(
                RectF(area.left, area.top, area.right, area.top + height),
                RectF(area.left, area.top + height + gap, area.right, area.bottom)
            )
        }
        ReportQuoteLayout.THREE_FEATURED -> {
            val topHeight = (area.height() - gap) * 0.5f
            val lowerTop = area.top + topHeight + gap
            val lowerWidth = (area.width() - gap) / 2f
            listOf(
                RectF(area.left, area.top, area.right, area.top + topHeight),
                RectF(area.left, lowerTop, area.left + lowerWidth, area.bottom),
                RectF(area.left + lowerWidth + gap, lowerTop, area.right, area.bottom)
            )
        }
        ReportQuoteLayout.FOUR_GRID,
        ReportQuoteLayout.SIX_GRID -> {
            val count = if (layout == ReportQuoteLayout.FOUR_GRID) 4 else 6
            val columns = 2
            val rows = count / columns
            val width = (area.width() - gap) / columns
            val height = (area.height() - gap * (rows - 1)) / rows
            List(count) { index ->
                val left = area.left + index % columns * (width + gap)
                val top = area.top + index / columns * (height + gap)
                RectF(left, top, left + width, top + height)
            }
        }
    }

    private fun textPaint(
        color: Int,
        textSize: Float,
        typeface: android.graphics.Typeface?,
        align: Paint.Align = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = textSize
        this.typeface = typeface
        textAlign = align
    }

    private fun fillPaint(color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun strokePaint(color: Int, width: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = width
            style = Paint.Style.STROKE
        }

    private fun metricText(value: String, unit: String?): String =
        unit?.takeIf(String::isNotBlank)?.let { "$value $it" } ?: value

    private fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 ->
                String.format(Locale.ROOT, "%d小时%d分钟", hours, rest)
            hours > 0 -> "${hours}小时"
            else -> "${rest}分钟"
        }
    }

    private fun unit(spec: ReportPageSpec): Float = spec.width / 1_080f

    private fun opaque(color: Int): Int =
        Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private const val TAG = "ReportLayout"
    private val MINI_WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
    private val ENGLISH_MONTHS = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    )
}

internal data class ReportTableColumnLayout(
    val widths: List<Float>,
    val gap: Float,
    val totalWidth: Float
)

internal fun calculateReportTableColumnWidths(
    headers: List<String>,
    rows: List<List<String>>,
    availableWidth: Float,
    gap: Float,
    measureText: (text: String, column: Int) -> Float
): ReportTableColumnLayout {
    require(headers.isNotEmpty())
    require(availableWidth > 0f)
    require(rows.all { it.size == headers.size })
    val columnCount = headers.size
    val resolvedGap = gap.coerceAtLeast(1f)
    val minimums = List(columnCount) { column ->
        availableWidth * if (column == 0) 0.28f else 0.15f
    }
    val maximums = List(columnCount) { column ->
        availableWidth * if (column == 0) 0.45f else 0.32f
    }
    val widths = MutableList(columnCount) { column ->
        val preferred = sequence {
            yield(headers[column])
            rows.forEach { yield(it[column]) }
        }.maxOf { measureText(it, column) } + 8f
        preferred.coerceIn(minimums[column], maximums[column])
    }
    val gapsWidth = resolvedGap * (columnCount - 1)
    var excess = (widths.sum() + gapsWidth - availableWidth).coerceAtLeast(0f)
    while (excess > 0.5f) {
        val column = widths.indices
            .filter { widths[it] > minimums[it] }
            .maxByOrNull { widths[it] - minimums[it] }
            ?: break
        val reduction = minOf(excess, widths[column] - minimums[column])
        widths[column] -= reduction
        excess -= reduction
    }
    return ReportTableColumnLayout(
        widths = widths,
        gap = resolvedGap,
        totalWidth = minOf(availableWidth, widths.sum() + gapsWidth)
    )
}

internal fun calculateReportCollagePlacements(
    aspectRatios: List<Double>,
    availableWidth: Float,
    availableHeight: Float
): List<ReportRelativePlacement> {
    if (aspectRatios.isEmpty()) return emptyList()
    require(availableWidth > 0f && availableHeight > 0f)
    val ratios = aspectRatios.map { it.coerceIn(0.2, 5.0) }
    if (ratios.size == 1) {
        val ratio = ratios.single().toFloat()
        val widthFromHeight = availableHeight * ratio
        val width = minOf(availableWidth, widthFromHeight)
        val height = width / ratio
        val left = (availableWidth - width) / 2f
        val top = (availableHeight - height) / 2f
        return listOf(ReportRelativePlacement(left, top, left + width, top + height))
    }

    val candidates = (1..minOf(4, ratios.size)).map { rowCount ->
        val rows = balancedRows(ratios, rowCount)
        val heights = rows.map { row ->
            availableWidth / row.sum().toFloat()
        }
        Triple(rows, heights, heights.sum())
    }
    val selected = candidates
        .filter { it.third <= availableHeight }
        .maxByOrNull { it.third }
        ?: candidates.minBy { it.third }
    val topOffset = ((availableHeight - selected.third) / 2f).coerceAtLeast(0f)
    val placements = mutableListOf<ReportRelativePlacement>()
    var top = topOffset
    selected.first.zip(selected.second).forEach { (row, rowHeight) ->
        var left = 0f
        row.forEachIndexed { index, ratio ->
            val right = if (index == row.lastIndex) {
                availableWidth
            } else {
                left + rowHeight * ratio.toFloat()
            }
            placements += ReportRelativePlacement(left, top, right, top + rowHeight)
            left = right
        }
        top += rowHeight
    }
    return placements
}

private fun balancedRows(
    ratios: List<Double>,
    rowCount: Int
): List<List<Double>> {
    val rows = MutableList(rowCount) { mutableListOf<Double>() }
    val targetSum = ratios.sum() / rowCount
    var rowIndex = 0
    ratios.forEachIndexed { index, ratio ->
        val remainingItems = ratios.size - index
        val remainingRowsAfterCurrent = rowCount - rowIndex - 1
        if (
            rowIndex < rowCount - 1 &&
            rows[rowIndex].isNotEmpty() &&
            (
                rows[rowIndex].sum() >= targetSum ||
                    remainingItems <= remainingRowsAfterCurrent
                )
        ) {
            rowIndex += 1
        }
        rows[rowIndex] += ratio
    }
    return rows
}
