package cn.a10miaomiao.bilidown.ui.page

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import cn.a10miaomiao.bilidown.R
import cn.a10miaomiao.bilidown.common.BiliDownFile
import cn.a10miaomiao.bilidown.common.BiliDownOutFile
import cn.a10miaomiao.bilidown.common.ListPageBatchExportFileNameResolver
import cn.a10miaomiao.bilidown.common.MiaoLog
import cn.a10miaomiao.bilidown.common.lifecycle.LaunchedLifecycleObserver
import cn.a10miaomiao.bilidown.common.localStoragePermission
import cn.a10miaomiao.bilidown.common.molecule.collectAction
import cn.a10miaomiao.bilidown.common.molecule.rememberPresenter
import cn.a10miaomiao.bilidown.entity.DownloadInfo
import cn.a10miaomiao.bilidown.entity.DownloadItemInfo
import cn.a10miaomiao.bilidown.entity.DownloadType
import cn.a10miaomiao.bilidown.service.BiliDownService
import cn.a10miaomiao.bilidown.shizuku.localShizukuPermission
import cn.a10miaomiao.bilidown.ui.BiliDownScreen
import cn.a10miaomiao.bilidown.ui.components.DownloadListItem
import cn.a10miaomiao.bilidown.ui.components.PermissionDialog
import cn.a10miaomiao.bilidown.ui.components.SwipeToRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
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
        val includeOwnerPrefix: Boolean,
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
            MiaoLog.debug { "getList(packageName:$packageName, enabledShizuku:$enabledShizuku)" }
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
                if (last != null && last.type == item.type && last.id == item.id) {
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
                "Shizuku connection timed out"
            } else {
                "Reading cache list timed out"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            failMessage = "Failed to read list: ${e.message ?: e}"
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
                val resolvedNames = ListPageBatchExportFileNameResolver.resolve(
                    it.groups,
                    includeOwnerPrefix = it.includeOwnerPrefix,
                )
                biliDownService.addTasks(
                    it.groups.flatMap { group ->
                        group.items.map { item -> group to item }
                    }.map { (group, item) ->
                        val fileName = resolvedNames[item.dir_path]
                            ?: item.title.replace(" ", "") + ".mp4"
                        val outFile = BiliDownOutFile(fileName)
                        outFile.autoRename(usedPaths)
                        usedPaths.add(outFile.path)

                        BiliDownService.TaskInfo(
                            entryDirPath = item.dir_path,
                            outFilePath = outFile.path,
                            title = outFile.name,
                            ownerName = group.ownerName,
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
        list = list,
        path = path,
        canRead = canRead,
        loading = loading,
        refreshing = refreshing,
        failMessage = failMessage,
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

    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var isSelectionMode by remember { mutableStateOf(false) }
    var showBatchExportMenu by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateMapOf<String, DownloadInfo>() }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedItems.clear()
        showBatchExportMenu = false
    }

    val filteredList = remember(state.list, searchQuery) {
        if (searchQuery.isBlank()) {
            state.list
        } else {
            state.list.filter { info ->
                info.title.contains(searchQuery, ignoreCase = true) ||
                    info.ownerName.contains(searchQuery, ignoreCase = true)
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
        if (state.list.isEmpty() &&
            permissionState.isGranted &&
            permissionState.isExternalStorage
        ) {
            channel.send(
                DownloadListPageAction.GetList(
                    packageName = packageName,
                    enabledShizuku = shizukuPermissionState.isEnabled,
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
                        enabledShizuku = shizukuPermissionState.isEnabled,
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
                Toast.makeText(context, "Shizuku not found", Toast.LENGTH_LONG).show()
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch Shizuku", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    PermissionDialog(
        showPermissionDialog = showPermissionDialog,
        isGranted = permissionState.isGranted,
        onDismiss = { showPermissionDialog = false }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                placeholder = { Text("Search title / owner...") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            isSearchVisible = false
                            keyboardController?.hide()
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Search")
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
                        when {
                            state.loading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 4.dp,
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Loading list",
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }

                            !permissionState.isGranted || !permissionState.isExternalStorage -> {
                                if (permissionState.isGranted) {
                                    Text(text = "Please grant all file access")
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            storagePermission.requestPermissions(::resultCallBack)
                                        }
                                    ) {
                                        Text(text = "Grant All Files")
                                    }
                                } else {
                                    Text(text = "Please grant storage permission")
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            storagePermission.requestPermissions(::resultCallBack)
                                        }
                                    ) {
                                        Text(text = "Grant Permission")
                                    }
                                }
                            }

                            !state.canRead -> {
                                Text(text = "Please grant cache folder access")
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        val biliDownFile = BiliDownFile(
                                            context,
                                            packageName,
                                            shizukuPermissionState.isEnabled,
                                        )
                                        biliDownFile.startFor(2)
                                    }
                                ) {
                                    Text(text = "Grant Access")
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
                                    Text(text = "Or use Shizuku")
                                }
                            }

                            state.failMessage.isNotBlank() -> {
                                Text(
                                    text = state.failMessage,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(20.dp)
                                )
                                if ("Shizuku" in state.failMessage) {
                                    TextButton(onClick = ::openShizuku) {
                                        Text(text = "Open Shizuku")
                                    }
                                }
                            }

                            else -> {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_movie_pay_area_limit),
                                    contentDescription = "Empty",
                                    modifier = Modifier.size(200.dp, 200.dp)
                                )
                                Text(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    text = "Empty",
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (isSelectionMode) 160.dp else 80.dp
                        ),
                    ) {
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
                                        text = "Total ${state.list.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    IconButton(onClick = { isSearchVisible = true }) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = "Search",
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
                                        text = "No matching videos",
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
                                    val encodedDirPath = Uri.encode(downloadInfo.dir_path)
                                    navController.navigate(
                                        BiliDownScreen.Detail.route +
                                            "?packageName=$packageName&dirPath=$encodedDirPath"
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
                                    filteredList.forEach { info ->
                                        selectedItems[info.dir_path] = info
                                    }
                                }
                            }
                        ) {
                            Text(if (isAllSelected) "Deselect All" else "Select All")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Selected $selectedCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        OutlinedButton(
                            onClick = {
                                isSelectionMode = false
                                selectedItems.clear()
                                showBatchExportMenu = false
                            }
                        ) {
                            Text("Cancel")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box {
                            Button(
                                onClick = {
                                    showBatchExportMenu = true
                                },
                                enabled = selectedItems.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export")
                            }
                            DropdownMenu(
                                expanded = showBatchExportMenu,
                                onDismissRequest = { showBatchExportMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default") },
                                    onClick = {
                                        channel.trySend(
                                            DownloadListPageAction.BatchExport(
                                                groups = selectedItems.values.toList(),
                                                includeOwnerPrefix = false,
                                            )
                                        )
                                        showBatchExportMenu = false
                                        isSelectionMode = false
                                        selectedItems.clear()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("With Owner Prefix") },
                                    onClick = {
                                        channel.trySend(
                                            DownloadListPageAction.BatchExport(
                                                groups = selectedItems.values.toList(),
                                                includeOwnerPrefix = true,
                                            )
                                        )
                                        showBatchExportMenu = false
                                        isSelectionMode = false
                                        selectedItems.clear()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
