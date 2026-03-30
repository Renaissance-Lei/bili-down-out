package cn.a10miaomiao.bilidown.common

import cn.a10miaomiao.bilidown.entity.DownloadInfo
import cn.a10miaomiao.bilidown.entity.DownloadItemInfo

object ListPageBatchExportFileNameResolver {

    private data class TitleParts(
        val baseTitle: String,
        val suffixNumber: Int?,
    )

    private val trailingSuffixRegex = Regex("^(.*)\\((\\d+)\\)$")

    fun resolve(
        groups: List<DownloadInfo>,
        includeOwnerPrefix: Boolean = false,
    ): Map<String, String> {
        if (groups.isEmpty()) {
            return emptyMap()
        }
        val duplicateTitleCounts = groups
            .map { buildGroupTitle(it, includeOwnerPrefix) }
            .groupingBy { it }
            .eachCount()
        val duplicateTitleIndexes = mutableMapOf<String, Int>()
        val usedFileNames = mutableSetOf<String>()
        val resolvedNames = linkedMapOf<String, String>()

        groups.forEach { group ->
            val groupTitle = buildGroupTitle(group, includeOwnerPrefix)
            val duplicateIndex = duplicateTitleIndexes[groupTitle] ?: 0
            duplicateTitleIndexes[groupTitle] = duplicateIndex + 1

            val resolvedGroupTitle = if ((duplicateTitleCounts[groupTitle] ?: 0) > 1) {
                appendDuplicateSuffix(groupTitle, duplicateIndex)
            } else {
                groupTitle
            }

            group.items.forEachIndexed { itemIndex, item ->
                val proposedTitle = buildItemTitle(
                    groupTitle = resolvedGroupTitle,
                    item = item,
                    itemIndex = itemIndex,
                    itemCount = group.items.size,
                )
                val uniqueTitle = reserveUniqueTitle(proposedTitle, usedFileNames)
                resolvedNames[item.dir_path] = "$uniqueTitle.mp4"
            }
        }

        return resolvedNames
    }

    private fun buildItemTitle(
        groupTitle: String,
        item: DownloadItemInfo,
        itemIndex: Int,
        itemCount: Int,
    ): String {
        if (itemCount <= 1) {
            return groupTitle
        }
        val indexTitle = sanitizeTitle(item.index_title)
        if (indexTitle.isNotBlank()) {
            return groupTitle + indexTitle
        }
        return groupTitle + "P${itemIndex + 1}"
    }

    private fun reserveUniqueTitle(
        title: String,
        usedTitles: MutableSet<String>,
    ): String {
        if (usedTitles.add(title)) {
            return title
        }
        val titleParts = splitTitle(title)
        var index = titleParts.suffixNumber ?: 0
        while (true) {
            index += 1
            val candidate = "${titleParts.baseTitle}($index)"
            if (usedTitles.add(candidate)) {
                return candidate
            }
        }
    }

    private fun appendDuplicateSuffix(
        title: String,
        duplicateIndex: Int,
    ): String {
        return if (duplicateIndex == 0) {
            title
        } else {
            "$title($duplicateIndex)"
        }
    }

    private fun buildGroupTitle(
        group: DownloadInfo,
        includeOwnerPrefix: Boolean,
    ): String {
        val title = sanitizeTitle(group.title)
        if (!includeOwnerPrefix) {
            return title
        }
        val ownerName = sanitizeOwnerName(group.ownerName)
        return if (ownerName.isBlank()) {
            title
        } else {
            "\u3010$ownerName\u3011$title"
        }
    }

    private fun sanitizeTitle(title: String): String {
        return title.replace(" ", "").ifBlank { "video" }
    }

    private fun sanitizeOwnerName(ownerName: String): String {
        return ownerName.replace(" ", "")
    }

    private fun splitTitle(title: String): TitleParts {
        val suffixMatch = trailingSuffixRegex.matchEntire(title)
        if (suffixMatch != null) {
            return TitleParts(
                baseTitle = suffixMatch.groupValues[1],
                suffixNumber = suffixMatch.groupValues[2].toInt(),
            )
        }
        return TitleParts(
            baseTitle = title,
            suffixNumber = null,
        )
    }
}
