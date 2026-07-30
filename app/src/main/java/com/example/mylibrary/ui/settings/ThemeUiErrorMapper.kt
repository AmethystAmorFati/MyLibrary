package com.example.mylibrary.ui.settings

import com.example.mylibrary.ui.theme.ThemeApplyError
import com.example.mylibrary.ui.theme.importer.ThemeDeleteError
import com.example.mylibrary.ui.theme.importer.ThemePackageError

object ThemeUiErrorMapper {
    fun importFailure(error: ThemePackageError): String = when (error) {
        is ThemePackageError.SourceReadFailed ->
            "无法读取所选文件"

        is ThemePackageError.NotZipArchive ->
            "不是有效的 ZIP 主题包"

        ThemePackageError.MissingManifest ->
            "缺少 manifest.json"
        ThemePackageError.MissingChecksums ->
            "缺少 checksums.json"

        is ThemePackageError.ChecksumsInvalid ->
            "完整性清单格式错误"
        is ThemePackageError.ChecksumEntryMissing ->
            "完整性清单缺少文件校验值"
        is ThemePackageError.ChecksumExtraEntry ->
            "完整性清单包含多余条目"
        is ThemePackageError.ChecksumMismatch ->
            "文件校验值不一致"
        is ThemePackageError.ArchiveEntryCrcMismatch,
        is ThemePackageError.ArchiveEntrySizeMismatch ->
            "主题包已损坏或内容不完整"

        is ThemePackageError.ManifestParseFailed ->
            "Manifest 格式错误，无法解析"
        is ThemePackageError.ThemeValidationFailed ->
            "Manifest 字段无效"
        is ThemePackageError.ManifestResourceMissing ->
            "Manifest 引用了不存在的资源文件"
        is ThemePackageError.ManifestResourceExtra ->
            "主题包含 Manifest 未声明的额外文件"

        is ThemePackageError.ThemeResolutionFailed ->
            "主题资源无效"

        is ThemePackageError.PackageTooLarge ->
            "主题包超过允许大小"
        is ThemePackageError.TooManyEntries ->
            "主题包条目过多"
        is ThemePackageError.TooManyFiles ->
            "主题包文件数量过多"
        is ThemePackageError.ArchiveUncompressedSizeExceeded ->
            "主题包解压后超过允许大小"
        is ThemePackageError.CompressionRatioExceeded ->
            "主题包压缩比异常"
        is ThemePackageError.ManifestTooLarge ->
            "Manifest 文件过大"
        is ThemePackageError.ChecksumsTooLarge ->
            "完整性清单文件过大"

        is ThemePackageError.EncryptedZipUnsupported ->
            "不支持加密 ZIP 主题包"
        is ThemePackageError.DuplicateEntry ->
            "主题包含重复条目"
        is ThemePackageError.CaseCollision ->
            "主题包含大小写冲突的路径"
        is ThemePackageError.ZipPathInvalid ->
            "主题包含无效路径"
        is ThemePackageError.ZipPathEscapesRoot ->
            "主题包含越界路径"
        is ThemePackageError.UnsupportedEntryType ->
            "主题包含不受支持的条目类型"
        is ThemePackageError.UnexpectedEntry ->
            "主题包含额外文件"

        is ThemePackageError.InstallFailed ->
            "无法写入主题目录"
        is ThemePackageError.RollbackFailed ->
            "主题安装失败且无法回退到旧版本"
        is ThemePackageError.RecoveryFailed ->
            "主题存储恢复失败，请重试"
    }

    fun applyFailure(
        error: ThemeApplyError,
        installedDuringOperation: Boolean = false
    ): String = when (error) {
        is ThemeApplyError.InstalledThemeInvalid ->
            "该主题已损坏，无法应用"
        is ThemeApplyError.PreferenceWriteFailed ->
            if (installedDuringOperation) {
                "主题已安装，但无法保存当前选择"
            } else {
                "无法保存当前主题选择"
            }
        is ThemeApplyError.PreferenceReadFailed ->
            "无法读取当前主题设置"
    }

    fun deleteFailure(error: ThemeDeleteError): String = when (error) {
        ThemeDeleteError.DefaultThemeProtected ->
            "默认主题不能删除"
        is ThemeDeleteError.InvalidThemeId,
        is ThemeDeleteError.DeleteFailed ->
            "无法删除主题文件"
    }
}
