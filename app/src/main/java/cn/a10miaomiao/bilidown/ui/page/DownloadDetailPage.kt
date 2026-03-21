package cn.a10miaomiao.bilidown.ui.page

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import cn.a10miaomiao.bilidown.BiliDownApp
import cn.a10miaomiao.bilidown.common.BiliDownFile
import cn.a10miaomiao.bilidown.common.BiliDownOutFile
import cn.a10miaomiao.bilidown.common.file.MiaoDocumentFile
import cn.a10miaomiao.bilidown.common.file.MiaoJavaFile
import cn.a10miaomiao.bilidown.common.molecule.collectAction
import cn.a10miaomiao.bilidown.common.molecule.rememberPresenter
import cn.a10miaomiao.bilidown.db.dao.OutRecord
import cn.a10miaomiao.bilidown.entity.DownloadInfo
import cn.a10miaomiao.bilidown.entity.DownloadItemInfo
import cn.a10miaomiao.bilidown.entity.DownloadType
import cn.a10miaomiao.bilidown.service.BiliDownService
import cn.a10miaomiao.bilidown.shizuku.localShizukuPermission
import cn.a10miaomiao.bilidown.state.TaskStatus
import cn.a10miaomiao.bilidown.ui.BiliDownScreen
import cn.a10miaomiao.bilidown.ui.components.DownloadDetailItem
import cn.a10miaomiao.bilidown.ui.components.DownloadListItem
import cn.a10miaomiao.bilidown.ui.components.FileNameInputDialog
import kotlinx.coroutines.flow.Flow
import java.io.File


data class DownloadDetailPageState(
    val detailInfo: DownloadInfo?,
    val outRecordMap: Map<String, OutRecord>,
)

sealed class DownloadDetailPageAction {
    class Export(
        val entryDirPath: String,
        val outFile: BiliDownOutFile,
    ): DownloadDetailPageAction()

    class AddTask(
        val entryDirPath: String,
        val outFilePath: String,
        val title: String,
        val cover: String,
    ): DownloadDetailPageAction()

    class BatchExport(
        val items: List<DownloadItemInfo>,
    ): DownloadDetailPageAction()
}

@Composable
fun DownloadDetailPagePresenter(
    context: Context,
    packageName: String,
    dirPath: String,
    navController: NavHostController,
    action: Flow<DownloadDetailPageAction>,
): DownloadDetailPageState {
    var detailInfo by remember {
        mutableStateOf<DownloadInfo?>(null)
    }
    var path by remember {
        mutableStateOf("")
    }
    val outRecordMap = remember {
        mutableStateMapOf<String, OutRecord>()
    }
    LaunchedEffect(packageName, dirPath) {
        val biliDownService = BiliDownService.getService(context)
        val appState = (context.applicationContext as BiliDownApp).state
        val shizukuState = appState.shizukuState.value
        val biliDownFile = BiliDownFile(context, packageName, shizukuState.isEnabled)
//        val list = mutableListOf<DownloadInfo>()
        val dirFile = if (dirPath.startsWith("content://")) {
            MiaoDocumentFile(
                context,
                DocumentFile.fromTreeUri(
                    context,
                    Uri.parse(dirPath)
                )!!
            )
        } else {
            MiaoJavaFile(File(dirPath))
        }
        val list = biliDownFile.readDownloadDirectory(dirFile)
        val items = mutableListOf<DownloadItemInfo>()
        var isCompleted = true
        list.forEach {
            val biliEntry = it.entry
            var indexTitle = ""
            var itemTitle = ""
            var id = it.entry.avid ?: 0L
            var cid = 0L
            var epid = 0L
            var type = DownloadType.VIDEO
            val page = biliEntry.page_data
            if (page != null) {
                id = biliEntry.avid!!
                indexTitle = page.download_title ?: page.part ?: "${page.page}P"
                cid = page.cid
                type = DownloadType.VIDEO
                itemTitle = biliEntry.title
            }
            val ep = biliEntry.ep
            val source = biliEntry.source
            if (ep != null && source != null) {
                id = biliEntry.season_id!!.toLong()
                indexTitle = ep.index_title
                epid = ep.episode_id
                cid = source.cid
                type = DownloadType.BANGUMI
                itemTitle = ep.index + ep.index_title
            }
            val item = DownloadItemInfo(
                dir_path = it.entryDirPath,
                media_type = biliEntry.media_type,
                has_dash_audio = biliEntry.has_dash_audio,
                is_completed = biliEntry.is_completed,
                total_bytes = biliEntry.total_bytes,
                downloaded_bytes = biliEntry.downloaded_bytes,
                title = itemTitle,
                cover = biliEntry.cover,
                id = id,
                type = type,
                cid = cid,
                epid = epid,
                index_title = indexTitle,
            )
            items.add(item)
            if (!item.is_completed) {
                isCompleted = false
            }
        }
        if (items.isNotEmpty()) {
            val paths = items.map {
                it.dir_path
            }.toTypedArray()
            val records = biliDownService.getRecordList(paths)
            outRecordMap.clear()
            records.forEach {
                outRecordMap[it.entryDirPath] = it
            }
        }
        if (list.isEmpty()) {
            detailInfo = null
        } else {
            val biliEntry = list[0].entry
            val item = items[0]
            detailInfo = DownloadInfo(
                dir_path = list[0].pageDirPath,
                media_type = biliEntry.media_type,
                has_dash_audio = biliEntry.has_dash_audio,
                is_completed = isCompleted,
                total_bytes = biliEntry.total_bytes,
                downloaded_bytes = biliEntry.downloaded_bytes,
                title = biliEntry.title,
                cover = biliEntry.cover,
                cid = item.cid,
                id = item.id,
                type = item.type,
                items = items
            )
        }
    }
    action.collectAction {
        when (it) {
            is DownloadDetailPageAction.Export -> {
                val biliDownService = BiliDownService.getService(context)
                val isSuccess = biliDownService.exportBiliVideo(
                    it.entryDirPath,
                    it.outFile.file,
                )
                if (isSuccess) {
                    navController.navigate(BiliDownScreen.Progress.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            is DownloadDetailPageAction.AddTask -> {
                val biliDownService = BiliDownService.getService(context)
                biliDownService.addTask(
                    it.entryDirPath,
                    it.outFilePath,
                    it.title,
                    it.cover
                )
            }

            is DownloadDetailPageAction.BatchExport -> {
                val biliDownService = BiliDownService.getService(context)
                biliDownService.addTasks(
                    it.items.map { item ->
                        val fileName = item.title.replace(" ", "") + ".mp4"
                        val outFile = BiliDownOutFile(fileName)
                        outFile.autoRename()
                        BiliDownService.TaskInfo(
                            entryDirPath = item.dir_path,
                            outFilePath = outFile.path,
                            title = outFile.name,
                            cover = item.cover,
                        )
                    }
                )
                navController.navigate(BiliDownScreen.Progress.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
    return DownloadDetailPageState(
        detailInfo,
        outRecordMap,
    )
}

@Composable
fun DownloadDetailPage(
    navController: NavHostController,
    packageName: String,
    dirPath: String,
) {
    val context = LocalContext.current
    val appState = (context.applicationContext as BiliDownApp).state
    val taskStatus by appState.taskStatus.collectAsState()
    val (state, channel) = rememberPresenter(listOf(packageName, dirPath)) {
        DownloadDetailPagePresenter(context, packageName, dirPath, navController, it)
    }
    var selectedItem by remember {
        mutableStateOf<DownloadItemInfo?>(null)
    }

    // 多选模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateMapOf<String, DownloadItemInfo>() }

    // 返回键退出多选模式
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedItems.clear()
    }

    FileNameInputDialog(
        showInputDialog = selectedItem != null,
        fileName = selectedItem?.title ?: "",
        onDismiss = {
            selectedItem = null
        },
        confirmText = if (taskStatus is TaskStatus.InIdle) {
            "确定导出"
        } else { "添加到队列" },
        onConfirm = { outFile ->
            selectedItem?.let { item ->
                if (taskStatus is TaskStatus.InIdle) {
                    channel.trySend(DownloadDetailPageAction.Export(
                        entryDirPath = item.dir_path,
                        outFile = outFile,
                    ))
                } else {
                    channel.trySend(DownloadDetailPageAction.AddTask(
                        entryDirPath = item.dir_path,
                        outFilePath = outFile.path,
                        title = outFile.name,
                        cover = item.cover
                    ))
                }
            }
            selectedItem = null
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = if (isSelectionMode) 80.dp else 0.dp
            )
        ) {
            val detailInfo = state.detailInfo
            if (detailInfo != null) {
                item {
                    DownloadListItem(
                        item = detailInfo,
                        onClick = { },
                    )
                }
                items(detailInfo.items, { it.dir_path }) {
                    DownloadDetailItem(
                        item = it,
                        isOut = state.outRecordMap.containsKey(it.dir_path),
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedItems.containsKey(it.dir_path),
                        onClick = {
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedItems[it.dir_path] = it
                            }
                        },
                        onSelectToggle = {
                            if (selectedItems.containsKey(it.dir_path)) {
                                selectedItems.remove(it.dir_path)
                                if (selectedItems.isEmpty()) {
                                    isSelectionMode = false
                                }
                            } else {
                                selectedItems[it.dir_path] = it
                            }
                        },
                        onStartClick = {
                        },
                        onPauseClick = {
                        },
                        onExportClick = {
                            selectedItem = it
                        },
                    )
                }
            }
        }

        // 多选模式底部操作栏
        if (isSelectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                    val totalCount = state.detailInfo?.items?.size ?: 0
                    val selectedCount = selectedItems.size
                    val isAllSelected = totalCount > 0 && selectedCount == totalCount

                    // 全选/取消全选按钮
                    TextButton(
                        onClick = {
                            if (isAllSelected) {
                                selectedItems.clear()
                            } else {
                                state.detailInfo?.items?.forEach { item ->
                                    selectedItems[item.dir_path] = item
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

                    // 取消按钮
                    OutlinedButton(
                        onClick = {
                            isSelectionMode = false
                            selectedItems.clear()
                        }
                    ) {
                        Text("取消")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 一键导出按钮
                    Button(
                        onClick = {
                            val itemsToExport = selectedItems.values.toList()
                            channel.trySend(
                                DownloadDetailPageAction.BatchExport(itemsToExport)
                            )
                            isSelectionMode = false
                            selectedItems.clear()
                        },
                        enabled = selectedItems.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("一键导出")
                    }
                }
            }
        }
    }
}