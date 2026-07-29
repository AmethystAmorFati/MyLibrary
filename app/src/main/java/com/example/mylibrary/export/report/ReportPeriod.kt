package com.example.mylibrary.export.report

import java.time.LocalDate
import java.time.ZoneId

sealed interface ReportPeriod {
    val year: Int

    data class Month(
        override val year: Int,
        val month: Int
    ) : ReportPeriod

    data class Year(
        override val year: Int
    ) : ReportPeriod

    fun epochRange(zoneId: ZoneId = ZoneId.systemDefault()): ReportEpochRange {
        val startDate = when (this) {
            is Month -> LocalDate.of(year, month, 1)
            is Year -> LocalDate.of(year, 1, 1)
        }
        val endExclusiveDate = when (this) {
            is Month -> startDate.plusMonths(1)
            is Year -> startDate.plusYears(1)
        }
        return ReportEpochRange(
            startInclusive = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endExclusive = endExclusiveDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }
}

data class ReportEpochRange(
    val startInclusive: Long,
    val endExclusive: Long
) {
    init {
        require(startInclusive < endExclusive)
    }

    operator fun contains(value: Long): Boolean =
        value >= startInclusive && value < endExclusive
}
