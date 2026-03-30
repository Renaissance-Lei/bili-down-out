package cn.a10miaomiao.bilidown.ui.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import cn.a10miaomiao.bilidown.BiliDownApp
import cn.a10miaomiao.bilidown.R
import cn.a10miaomiao.bilidown.common.molecule.collectAction
import cn.a10miaomiao.bilidown.common.molecule.rememberPresenter
import cn.a10miaomiao.bilidown.db.dao.OutRecord
import cn.a10miaomiao.bilidown.service.BiliDownService
import cn.a10miaomiao.bilidown.state.TaskStatus
import cn.a10miaomiao.bilidown.ui.components.OutFolderDialog
import cn.a10miaomiao.bilidown.ui.components.RecordItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

data class OutListPageState(
    val status: TaskStatus,
    val recordList: List<OutRecord>,
)

sealed class OutListPageAction {
    data object GetRecordList : OutListPageAction()

    class OpenVideo(
        val record: OutRecord
    ) : OutListPageAction()

    class DeleteRecords(
        val records: List<OutRecord>,
        val isDeleteFile: Boolean,
    ) : OutListPageAction()
}

@Composable
fun OutListPagePresenter(
    context: Context,
    action: Flow<OutListPageAction>,
): OutListPageState {
    val appState = remember(context) {
        (context.applicationContext as BiliDownApp).state
    }
    val taskStatus by appState.taskStatus.collectAsState()

    var recordList by remember {
        mutableStateOf(emptyList<OutRecord>())
    }

    suspend fun getRecordList(
        biliDownService: BiliDownService
    ) {
        recordList = biliDownService.getRecordList(OutRecord.STATUS_SUCCESS)
        withContext(Dispatchers.IO) {
            recordList = recordList.map { record ->
                if (record.status == OutRecord.STATUS_SUCCESS) {
                    val exists = File(record.outFilePath).exists()
                    record.copy(
                        status = if (exists) {
                            OutRecord.STATUS_SUCCESS
                        } else {
                            -1
                        },
                    )
                } else {
                    record
                }
            }
        }
    }

    action.collectAction {
        when (it) {
            OutListPageAction.GetRecordList -> {
                val biliDownService = BiliDownService.getService(context)
                getRecordList(biliDownService)
            }

            is OutListPageAction.OpenVideo -> {
                val videoFile = File(it.record.outFilePath)
                if (videoFile.exists()) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        intent.flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                        FileProvider.getUriForFile(
                            context,
                            "cn.a10miaomiao.bilidown.fileprovider",
                            videoFile
                        )
                    } else {
                        Uri.fromFile(videoFile)
                    }
                    intent.setDataAndType(uri, "video/*")
                    context.startActivity(intent)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "视频文件不存在", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }

            is OutListPageAction.DeleteRecords -> {
                val biliDownService = BiliDownService.getService(context)
                biliDownService.delTasks(it.records, it.isDeleteFile)
                getRecordList(biliDownService)
            }
        }
    }

    return OutListPageState(
        status = taskStatus,
        recordList = recordList,
    )
}

@Composable
internal fun ReconfirmDeleteDialog(
    channel: Channel<OutListPageAction>,
    action: OutListPageAction.DeleteRecords?,
    onDismiss: () -> Unit,
) {
    if (action != null) {
        val title = if (action.records.size == 1) {
            action.records.first().title
        } else {
            "共 ${action.records.size} 条记录"
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                if (action.isDeleteFile) {
                    Text(text = "确认删除记录并删除文件？")
                } else {
                    Text(text = "确认删除记录？")
                }
            },
            text = {
                Column {
                    Text("删除：$title")
                    if (action.isDeleteFile) {
                        Text(
                            color = Color.Red,
                            text = "将同时删除已导出文件，此操作不可恢复",
                        )
                    } else {
                        Text("仅删除导出记录，不删除文件")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        channel.trySend(action)
                        onDismiss()
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun OutListPage(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val (state, channel) = rememberPresenter {
        OutListPagePresenter(context, it)
    }
    LaunchedEffect(
        channel, state.status,
    ) {
        channel.send(OutListPageAction.GetRecordList)
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateMapOf<String, OutRecord>() }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedItems.clear()
    }

    val filteredList = remember(state.recordList, searchQuery) {
        if (searchQuery.isBlank()) {
            state.recordList
        } else {
            state.recordList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.ownerName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(state.recordList) {
        val validKeys = state.recordList.mapTo(mutableSetOf()) { it.entryDirPath }
        selectedItems.keys.toList().forEach { key ->
            if (key !in validKeys) {
                selectedItems.remove(key)
            }
        }
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
    }

    var showOutFolderDialog by remember {
        mutableStateOf(false)
    }
    OutFolderDialog(
        showOutFolderDialog = showOutFolderDialog,
        onDismiss = {
            showOutFolderDialog = false
        },
    )

    var reconfirmDeleteDialogAction by remember {
        mutableStateOf<OutListPageAction.DeleteRecords?>(null)
    }
    ReconfirmDeleteDialog(
        channel = channel,
        action = reconfirmDeleteDialogAction,
        onDismiss = {
            reconfirmDeleteDialogAction = null
        },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                placeholder = { Text("搜索已导出视频...") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                },
                trailingIcon = {
                    IconButton(onClick = {
                        searchQuery = ""
                        isSearchVisible = false
                        keyboardController?.hide()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭搜索")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                ),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = if (isSelectionMode) 168.dp else 80.dp
                ),
            ) {
                if (state.recordList.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 96.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (state.status != TaskStatus.InIdle && state.status !is TaskStatus.Error) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 4.dp,
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_movie_pay_area_limit),
                                    contentDescription = "空空如也",
                                    modifier = Modifier.size(150.dp, 150.dp)
                                )
                                Text(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    text = "空空如也",
                                )
                            }
                        }
                    }
                } else {
                    if (!isSearchVisible && !isSelectionMode) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "共 ${state.recordList.size} 个已导出视频",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                IconButton(onClick = { isSearchVisible = true }) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = "搜索",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    if (searchQuery.isNotBlank() && filteredList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "没有找到匹配的导出记录",
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    items(filteredList, { it.id!! }) { item ->
                        RecordItem(
                            title = item.title,
                            cover = item.cover,
                            status = item.status,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedItems.containsKey(item.entryDirPath),
                            onClick = {
                                channel.trySend(
                                    OutListPageAction.OpenVideo(item)
                                )
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedItems[item.entryDirPath] = item
                                }
                            },
                            onSelectToggle = {
                                if (selectedItems.containsKey(item.entryDirPath)) {
                                    selectedItems.remove(item.entryDirPath)
                                    if (selectedItems.isEmpty()) {
                                        isSelectionMode = false
                                    }
                                } else {
                                    selectedItems[item.entryDirPath] = item
                                }
                            },
                            onDeleteClick = {
                                reconfirmDeleteDialogAction = OutListPageAction.DeleteRecords(
                                    records = listOf(item),
                                    isDeleteFile = it,
                                )
                            }
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { showOutFolderDialog = true }
                        ) {
                            Text(text = "导出文件夹在哪？")
                        }
                    }
                }
            }

            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val totalCount = filteredList.size
                        val selectedCount = selectedItems.size
                        val isAllSelected = totalCount > 0 && selectedCount >= totalCount

                        TextButton(
                            onClick = {
                                if (isAllSelected) {
                                    selectedItems.clear()
                                } else {
                                    filteredList.forEach { item ->
                                        selectedItems[item.entryDirPath] = item
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = if (isAllSelected) "取消全选" else "全选"
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "已选 $selectedCount 项",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        OutlinedButton(
                            onClick = {
                                isSelectionMode = false
                                selectedItems.clear()
                            }
                        ) {
                            Text("取消")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                reconfirmDeleteDialogAction = OutListPageAction.DeleteRecords(
                                    records = selectedItems.values.toList(),
                                    isDeleteFile = false,
                                )
                            },
                            enabled = selectedItems.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除记录")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                reconfirmDeleteDialogAction = OutListPageAction.DeleteRecords(
                                    records = selectedItems.values.toList(),
                                    isDeleteFile = true,
                                )
                            },
                            enabled = selectedItems.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删文件")
                        }
                    }
                }
            }
        }
    }
}
