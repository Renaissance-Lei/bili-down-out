package cn.a10miaomiao.bilidown.ui.page

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import cn.a10miaomiao.bilidown.BiliDownApp
import cn.a10miaomiao.bilidown.R
import cn.a10miaomiao.bilidown.common.BiliDownFile
import cn.a10miaomiao.bilidown.common.BiliDownOutFile
import cn.a10miaomiao.bilidown.common.BiliDownUtils
import cn.a10miaomiao.bilidown.common.ListPageBatchExportFileNameResolver
import cn.a10miaomiao.bilidown.common.MiaoLog
import cn.a10miaomiao.bilidown.common.datastore.DataStoreKeys
import cn.a10miaomiao.bilidown.common.datastore.rememberDataStorePreferencesFlow
import cn.a10miaomiao.bilidown.common.lifecycle.LaunchedLifecycleObserver
import cn.a10miaomiao.bilidown.common.localStoragePermission
import cn.a10miaomiao.bilidown.common.molecule.collectAction
import cn.a10miaomiao.bilidown.common.molecule.rememberPresenter
import cn.a10miaomiao.bilidown.entity.DownloadInfo
import cn.a10miaomiao.bilidown.entity.DownloadItemInfo
import cn.a10miaomiao.bilidown.entity.DownloadType
import cn.a10miaomiao.bilidown.service.BiliDownService
import cn.a10miaomiao.bilidown.shizuku.localShizukuPermission
import cn.a10miaomiao.bilidown.state.TaskStatus
import cn.a10miaomiao.bilidown.ui.BiliDownScreen
import cn.a10miaomiao.bilidown.ui.components.DownloadListItem
import cn.a10miaomiao.bilidown.ui.components.PermissionDialog
import cn.a10miaomiao.bilidown.ui.components.SwipeToRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.ShizukuProvider


data class DownloadListPageState(
    val list: List<DownloadInfo>,
    val path: String,
    val canRead: Boolean,
    val loading: Boolean,
    val refreshing: Boolean,
    val failMessage: String,
)

sealed class DownloadListPageAction {
    class GetList(
        val packageName: String,
        val enabledShizuku: Boolean,
    ) : DownloadListPageAction()

    class RefreshList(
        val packageName: String,
        val enabledShizuku: Boolean,
    ) : DownloadListPageAction()

    class BatchExport(
        val groups: List<DownloadInfo>,
    ) : DownloadListPageAction()
}

@Composable
fun DownloadListPagePresenter(
    context: Context,
    navController: NavHostController,
    action: Flow<DownloadListPageAction>,
): DownloadListPageState {
    var list by remember {
        mutableStateOf(emptyList<DownloadInfo>())
    }
    var path by remember {
        mutableStateOf("")
    }
    var canRead by remember {
        mutableStateOf(true)
    }
    var failMessage by remember {
        mutableStateOf("")
    }
    var loading by remember {
        mutableStateOf(true)
    }
    var refreshing by remember {
        mutableStateOf(false)
    }

    suspend fun getList(
        packageName: String,
        enabledShizuku: Boolean,
    ) {
        try {
            MiaoLog.debug { "getList(packageName:$packageName, enabledShizuku: $enabledShizuku)" }
            val biliDownFile = BiliDownFile(context, packageName, enabledShizuku)
            canRead = biliDownFile.canRead()
            if (!canRead) {
                return
            }
            loading = true
            failMessage = ""
            val newList = mutableListOf<DownloadInfo>()
            biliDownFile.readDownloadList().forEach {
                val biliEntry = it.entry
                var indexTitle = ""
                var itemTitle = ""
                var id = biliEntry.avid ?: 0L
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
                    itemTitle = if (ep.index_title.isNotBlank()) {
                        ep.index_title
                    } else {
                        ep.index
                    }
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
                val last = newList.lastOrNull()
                if (last != null
                    && last.type == item.type
                    && last.id == item.id
                ) {
                    if (last.is_completed && !item.is_completed) {
                        last.is_completed = false
                    }
                    last.items.add(item)
                } else {
                    newList.add(
                        DownloadInfo(
                            dir_path = it.pageDirPath,
                            media_type = biliEntry.media_type,
                            has_dash_audio = biliEntry.has_dash_audio,
                            is_completed = biliEntry.is_completed,
                            total_bytes = biliEntry.total_bytes,
                            downloaded_bytes = biliEntry.downloaded_bytes,
                            title = biliEntry.title,
                            cover = biliEntry.cover,
                            cid = cid,
                            id = id,
                            type = type,
                            items = mutableListOf(item),
                            ownerName = biliEntry.owner_name.orEmpty(),
                        )
                    )
                }
            }
            list = newList.toList()
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            failMessage = if (enabledShizuku) {
                "连接Shizuku服务超时，建议您尝试停止并重新激活Shizuku！"
            } else {
                "读取缓存列表超时！"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            failMessage = "读取列表异常：" + (e.message ?: e.toString())
        } finally {
            loading = false
        }
    }

    action.collectAction {
        when (it) {
            is DownloadListPageAction.RefreshList -> {
                refreshing = true
                withContext(Dispatchers.IO) {
                    getList(it.packageName, it.enabledShizuku)
                }
                refreshing = false
            }

            is DownloadListPageAction.GetList -> {
                withContext(Dispatchers.IO) {
                    getList(it.packageName, it.enabledShizuku)
                }
            }

            is DownloadListPageAction.BatchExport -> {
                val biliDownService = BiliDownService.getService(context)
                val usedPaths = mutableSetOf<String>()
                val resolvedNames = ListPageBatchExportFileNameResolver.resolve(it.groups)
                biliDownService.addTasks(
                    it.groups.flatMap { group -> group.items }.map { item ->
                        val fileName = resolvedNames[item.dir_path]
                            ?: item.title.replace(" ", "") + ".mp4"
                        val outFile = BiliDownOutFile(fileName)
                        outFile.autoRename(usedPaths)
                        usedPaths.add(outFile.path)

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
    return DownloadListPageState(
        list,
        path,
        canRead,
        loading,
        refreshing,
        failMessage,
    )
}

@Composable
fun DownloadListPage(
    navController: NavHostController,
    packageName: String,
) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    val storagePermission = localStoragePermission()
    val permissionState = storagePermission.collectState()
    val shizukuPermission = localShizukuPermission()
    val shizukuPermissionState by shizukuPermission.collectState()

    val (state, channel) = rememberPresenter(listOf(packageName, permissionState)) {
        DownloadListPagePresenter(context, navController, it)
    }

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 多选模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateMapOf<String, DownloadInfo>() }

    // 返回键退出多选模式
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedItems.clear()
    }

    // 根据搜索关键字过滤列表
    val filteredList = remember(state.list, searchQuery) {
        if (searchQuery.isBlank()) {
            state.list
        } else {
            state.list.filter { info ->
                info.title.contains(searchQuery, ignoreCase = true)
                    || info.ownerName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(
        packageName,
        permissionState.isGranted,
        permissionState.isExternalStorage,
        shizukuPermissionState.isRunning,
        shizukuPermissionState.isEnabled,
    ) {
        if (state.list.isEmpty()
            && permissionState.isGranted
            && permissionState.isExternalStorage
        ) {
            channel.send(
                DownloadListPageAction.GetList(
                    packageName = packageName,
                    shizukuPermissionState.isEnabled,
                )
            )
        }
    }

    LaunchedLifecycleObserver(
        onResume = {
            if (state.list.isEmpty()) {
                channel.trySend(
                    DownloadListPageAction.GetList(
                        packageName = packageName,
                        shizukuPermissionState.isEnabled,
                    )
                )
            }
        }
    )

    fun resultCallBack() {
        if (!permissionState.isGranted || !permissionState.isExternalStorage) {
            showPermissionDialog = true
        }
    }

    fun openShizuku() {
        try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(ShizukuProvider.MANAGER_APPLICATION_ID)
            if (intent == null) {
                Toast.makeText(context, "未找到Shizuku", Toast.LENGTH_LONG)
                    .show()
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Shizuku启动失败", Toast.LENGTH_LONG)
                .show()
            e.printStackTrace()
        }
    }

    PermissionDialog(
        showPermissionDialog = showPermissionDialog,
        isGranted = permissionState.isGranted,
        onDismiss = { showPermissionDialog = false }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                placeholder = { Text("搜索视频标题...") },
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
            SwipeToRefresh(
                refreshing = state.refreshing,
                onRefresh = {
                    channel.trySend(
                        DownloadListPageAction.RefreshList(
                            packageName = packageName,
                            enabledShizuku = shizukuPermissionState.isEnabled,
                        )
                    )
                },
            ) {
               if (state.list.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 4.dp,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                "正在读取列表",
                                color = MaterialTheme.colorScheme.outline,
                            )
                        } else if (!permissionState.isGranted || !permissionState.isExternalStorage) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (permissionState.isGranted) {
                                    Text(text = "请授予所有文件的存储权限")
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            storagePermission.requestPermissions(::resultCallBack)
                                        }
                                    ) {
                                        Text(text = "授予所有文件的权限")
                                    }
                                } else {
                                    Text(text = "请授予存储权限")
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            storagePermission.requestPermissions(::resultCallBack)
                                        }
                                    ) {
                                        Text(text = "授予权限")
                                    }
                                }
                            }
                        } else if (!state.canRead) {
                            Text(text = "请授予文件夹权限")
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    val biliDownFile = BiliDownFile(context, packageName, shizukuPermissionState.isEnabled)
                                    biliDownFile.startFor(2)
                                }
                            ) {
                                Text(text = "授予权限")
                            }
                            TextButton(
                                onClick = {
                                    navController.navigate(BiliDownScreen.More.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            ) {
                                Text(text = "或使用Shizuku")
                            }
                        } else if (state.failMessage.isNotBlank()) {
                            Text(
                                text = state.failMessage,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                            if ("Shizuku" in state.failMessage) {
                                TextButton(
                                    onClick = ::openShizuku,
                                ) {
                                    Text(text = "跳转Shizuku")
                                }
                            }
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_movie_pay_area_limit),
                                contentDescription = "空空如也",
                                modifier = Modifier.size(200.dp, 200.dp)
                            )
                            Text(
                                modifier = Modifier.padding(vertical = 8.dp),
                                text = "空空如也",
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (isSelectionMode) 160.dp else 80.dp
                        ),
                    ) {
                        // 搜索按钮 + 视频总数
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
                                        text = "共 ${state.list.size} 个视频",
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
                                        text = "没有找到匹配的视频",
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }

                        items(filteredList, { it.dir_path }) { downloadInfo ->
                            DownloadListItem(
                                item = downloadInfo,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItems.containsKey(downloadInfo.dir_path),
                                onClick = {
                                    val dirPath = Uri.encode(downloadInfo.dir_path)
                                    navController.navigate(
                                        BiliDownScreen.Detail.route + "?packageName=${packageName}&dirPath=${dirPath}"
                                    )
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItems[downloadInfo.dir_path] = downloadInfo
                                    }
                                },
                                onSelectToggle = {
                                    if (selectedItems.containsKey(downloadInfo.dir_path)) {
                                        selectedItems.remove(downloadInfo.dir_path)
                                        if (selectedItems.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        selectedItems[downloadInfo.dir_path] = downloadInfo
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // 多选模式底部操作栏
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

                        // 全选/取消全选按钮
                        TextButton(
                            onClick = {
                                if (isAllSelected) {
                                    selectedItems.clear()
                                } else {
                                    filteredList.forEach { info ->
                                        selectedItems[info.dir_path] = info
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
                                // 收集所有选中视频组的子项
                                channel.trySend(
                                    DownloadListPageAction.BatchExport(
                                        selectedItems.values.toList()
                                    )
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

}
