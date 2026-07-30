package com.example.mylibrary.export.report

object ReportTypography {
    const val PAGE_TITLE = 43f
    const val PAGE_SUBTITLE = 22f
    const val SECTION_TITLE = 29f
    const val BODY = 24f
    const val BODY_LINE_HEIGHT = 36f
    const val METADATA = 17f
    const val METADATA_LINE_HEIGHT = 24f
    const val HERO_VALUE = 42f
    const val QUOTE_MARK = 54f
    const val WORD_HIGHEST = 40f
    const val WORD_HIGH = 32f
    const val WORD_MEDIUM = 25f
    const val WORD_OTHER = 20f
    const val GRID_TITLE = 20f
    const val GRID_TITLE_LINE_HEIGHT = 25f
    const val GRID_CREATOR = 16f
    const val GRID_CREATOR_LINE_HEIGHT = 20f
}

object ReportSpacing {
    const val PAGE_HORIZONTAL = 80f
    const val PAGE_VERTICAL = 72f
    const val TITLE_TO_SUBTITLE = 38f
    const val TITLE_BLOCK = 112f
    const val SECTION = 48f
    const val PARAGRAPH = 22f
    const val ROW = 18f
    const val COLUMN = 28f
    const val HAIRLINE = 1f
    const val OPENING_MAX_WIDTH = 760f
    const val OPENING_TITLE_TO_SUBTITLE = 22f
    const val OPENING_SUBTITLE_TO_INTRO = 70f
    const val OPENING_INTRO_TO_STATISTICS = 54f
    const val OPENING_PARAGRAPH = 30f
    const val OPENING_MEDIA_SECTION = 58f
    const val OPENING_STATUS = 84f
    const val GRID_COVER_TO_TITLE = 11f
    const val GRID_TITLE_TO_CREATOR = 5f
    const val GRID_ROW = 27f
    const val TABLE_COLUMN_MIN_GAP = 20f
    const val TABLE_COLUMN_MAX_GAP = 28f
    const val TABLE_HEADER_TO_BODY = 28f
    const val TABLE_ROW = 18f
}

internal object ReportOpeningLayoutPolicy {
    const val CONTENT_CENTER_FRACTION = 0.5f
    const val DRAWS_STATUS_DIVIDER = false
}

internal object ReportItemTablePolicy {
    const val MAX_FIELD_COLUMNS = 3
    const val DRAWS_DIVIDERS = false
}
