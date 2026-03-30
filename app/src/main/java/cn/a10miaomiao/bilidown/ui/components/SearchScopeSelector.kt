package cn.a10miaomiao.bilidown.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class SearchScope(
    val matchTitle: Boolean = true,
    val matchOwner: Boolean = true,
) {
    fun updateMatchTitle(checked: Boolean): SearchScope {
        // 至少保留一个搜索维度，避免误触后把结果直接筛空。
        return if (!checked && !matchOwner) this else copy(matchTitle = checked)
    }

    fun updateMatchOwner(checked: Boolean): SearchScope {
        // 至少保留一个搜索维度，避免误触后把结果直接筛空。
        return if (!checked && !matchTitle) this else copy(matchOwner = checked)
    }

    fun matches(
        query: String,
        title: String,
        ownerName: String,
    ): Boolean {
        if (query.isBlank()) {
            return true
        }
        val titleMatched = matchTitle && title.contains(query, ignoreCase = true)
        val ownerMatched = matchOwner && ownerName.contains(query, ignoreCase = true)
        return titleMatched || ownerMatched
    }
}

@Composable
fun SearchScopeSelector(
    scope: SearchScope,
    onScopeChange: (SearchScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "搜索范围",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        SearchScopeOption(
            label = "标题",
            checked = scope.matchTitle,
            onCheckedChange = {
                onScopeChange(scope.updateMatchTitle(it))
            },
        )
        SearchScopeOption(
            label = "博主",
            checked = scope.matchOwner,
            onCheckedChange = {
                onScopeChange(scope.updateMatchOwner(it))
            },
        )
    }
}

@Composable
private fun SearchScopeOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
