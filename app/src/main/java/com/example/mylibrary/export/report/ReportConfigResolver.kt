package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.compatibleWith
import com.example.mylibrary.ui.settings.EMPTY_REPORT_CONTENT_MESSAGE
import com.example.mylibrary.ui.settings.MAX_REPORT_WORK_CUSTOM_FIELDS
import com.example.mylibrary.ui.settings.ReportExportConfig
import com.example.mylibrary.ui.settings.ReportWorkOption
import com.example.mylibrary.ui.settings.reportOrder

sealed interface ReportConfigResolution {
    data class Success(val config: ResolvedReportConfig) : ReportConfigResolution
    data class Invalid(val message: String) : ReportConfigResolution
}

class ReportConfigResolver {
    fun resolve(
        source: ReportExportConfig,
        itemTypes: List<ItemType>,
        fields: List<DynamicFieldDefinition>
    ): ReportConfigResolution {
        val period = resolvePeriod(source) ?: return ReportConfigResolution.Invalid(
            "报告年月无效"
        )
        val orderedTypes = itemTypes.sortedWith(
            compareBy<ItemType> { it.sortOrder }.thenBy { it.id }
        )
        val selectedTypes = if (source.typeId == null) {
            orderedTypes
        } else {
            orderedTypes.filter { it.id == source.typeId }
        }
        val selectedTypeIds = selectedTypes.mapTo(linkedSetOf()) { it.id }
        val typeById = selectedTypes.associateBy(ItemType::id)
        val orderedFields = fields
            .asSequence()
            .filter { it.typeId in selectedTypeIds }
            .sortedWith(
                compareBy<DynamicFieldDefinition> {
                    typeById[it.typeId]?.sortOrder ?: Int.MAX_VALUE
                }.thenBy { it.sortOrder }.thenBy { it.id }
            )
            .toList()

        val workFields = orderedFields
            .asSequence()
            .filter { it.id in source.workCustomFieldIds }
            .filter { it.enabled && !it.isFixed && it.scope == FieldScope.ITEM }
            .take(MAX_REPORT_WORK_CUSTOM_FIELDS)
            .map { field -> field.resolve(typeById, aggregation = null) }
            .toList()

        val fieldById = orderedFields.associateBy(DynamicFieldDefinition::id)
        val statisticFields = source.statisticSelections
            .asSequence()
            .mapNotNull { selection ->
                val field = fieldById[selection.fieldId] ?: return@mapNotNull null
                if (!field.enabled || field.isFixed) return@mapNotNull null
                val allowed = field.aggregations.compatibleWith(field.dataType)
                if (selection.aggregation !in allowed) return@mapNotNull null
                field.resolve(typeById, selection.aggregation)
            }
            .sortedWith(
                compareBy<ResolvedReportField> { it.itemTypeSortOrder }
                    .thenBy { it.fieldSortOrder }
                    .thenBy { it.fieldId }
                    .thenBy { requireNotNull(it.aggregation).reportOrder() }
            )
            .toList()

        val resolved = ResolvedReportConfig(
            period = period,
            selectedItemTypeIds = selectedTypeIds,
            basicStatistics = source.statistics.toCollection(linkedSetOf()),
            workFields = workFields,
            statisticFields = statisticFields,
            includeCover = ReportWorkOption.COVER in source.workFields,
            includeTitle = ReportWorkOption.TITLE in source.workFields,
            includeCreator = ReportWorkOption.CREATOR in source.workFields,
            includeStatus = ReportWorkOption.STATUS in source.workFields,
            includeTags = ReportWorkOption.TAGS in source.workFields,
            includeQuotes = source.includeQuotes,
            includeAllStatuses = source.includeAllStatuses,
            statusIds = source.statusIds.toCollection(linkedSetOf())
        )
        return if (resolved.hasContent()) {
            ReportConfigResolution.Success(resolved)
        } else {
            ReportConfigResolution.Invalid(EMPTY_REPORT_CONTENT_MESSAGE)
        }
    }

    private fun resolvePeriod(source: ReportExportConfig): ReportPeriod? {
        if (source.year !in 1900..9999) return null
        return source.month?.let { month ->
            if (month !in 1..12) null else ReportPeriod.Month(source.year, month)
        } ?: ReportPeriod.Year(source.year)
    }

    private fun DynamicFieldDefinition.resolve(
        typeById: Map<Long, ItemType>,
        aggregation: FieldAggregation?
    ): ResolvedReportField {
        val type = requireNotNull(typeById[typeId])
        return ResolvedReportField(
            fieldId = id,
            itemTypeId = typeId,
            itemTypeName = type.name,
            itemTypeSortOrder = type.sortOrder,
            fieldName = name,
            fieldType = dataType,
            scope = scope,
            unit = unit,
            aggregation = aggregation,
            fieldSortOrder = sortOrder,
            optionDefinitions = optionDefinitions.map { it.copy() }
        )
    }
}
