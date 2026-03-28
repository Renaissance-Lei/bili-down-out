package cn.a10miaomiao.bilidown.common

import cn.a10miaomiao.bilidown.entity.DownloadInfo
import cn.a10miaomiao.bilidown.entity.DownloadItemInfo
import cn.a10miaomiao.bilidown.entity.DownloadType
import org.junit.Assert.assertEquals
import org.junit.Test

class ListPageBatchExportFileNameResolverTest {

    @Test
    fun `duplicate top level titles get suffixed in list page batch export`() {
        val groups = listOf(
            createGroup(
                dirPath = "group-1",
                title = "same-title",
                items = listOf(
                    createItem(
                        dirPath = "item-1",
                        title = "same-title",
                    )
                )
            ),
            createGroup(
                dirPath = "group-2",
                title = "same-title",
                items = listOf(
                    createItem(
                        dirPath = "item-2",
                        title = "same-title",
                    )
                )
            ),
        )

        val names = ListPageBatchExportFileNameResolver.resolve(groups)

        assertEquals("same-title.mp4", names["item-1"])
        assertEquals("same-title(1).mp4", names["item-2"])
    }

    @Test
    fun `multi part groups keep part names while duplicate top level titles still get suffixed`() {
        val groups = listOf(
            createGroup(
                dirPath = "group-1",
                title = "series",
                items = listOf(
                    createItem(
                        dirPath = "item-1",
                        title = "series",
                        indexTitle = "P1"
                    ),
                    createItem(
                        dirPath = "item-2",
                        title = "series",
                        indexTitle = "P2"
                    ),
                )
            ),
            createGroup(
                dirPath = "group-2",
                title = "series",
                items = listOf(
                    createItem(
                        dirPath = "item-3",
                        title = "series",
                        indexTitle = "P1"
                    ),
                    createItem(
                        dirPath = "item-4",
                        title = "series",
                        indexTitle = "P2"
                    ),
                )
            ),
        )

        val names = ListPageBatchExportFileNameResolver.resolve(groups)

        assertEquals("seriesP1.mp4", names["item-1"])
        assertEquals("seriesP2.mp4", names["item-2"])
        assertEquals("series(1)P1.mp4", names["item-3"])
        assertEquals("series(1)P2.mp4", names["item-4"])
    }

    @Test
    fun `resolver increments existing numeric suffix instead of nesting suffixes`() {
        val groups = listOf(
            createGroup(
                dirPath = "group-1",
                title = "video",
                items = listOf(
                    createItem(
                        dirPath = "item-1",
                        title = "video",
                    )
                )
            ),
            createGroup(
                dirPath = "group-2",
                title = "video(1)",
                items = listOf(
                    createItem(
                        dirPath = "item-2",
                        title = "video(1)",
                    )
                )
            ),
            createGroup(
                dirPath = "group-3",
                title = "video",
                items = listOf(
                    createItem(
                        dirPath = "item-3",
                        title = "video",
                    )
                )
            ),
        )

        val names = ListPageBatchExportFileNameResolver.resolve(groups)

        assertEquals("video.mp4", names["item-1"])
        assertEquals("video(1).mp4", names["item-2"])
        assertEquals("video(2).mp4", names["item-3"])
    }

    private fun createGroup(
        dirPath: String,
        title: String,
        items: List<DownloadItemInfo>,
    ) = DownloadInfo(
        dir_path = dirPath,
        media_type = 1,
        has_dash_audio = false,
        is_completed = true,
        total_bytes = 0L,
        downloaded_bytes = 0L,
        title = title,
        cover = "",
        id = 1L,
        cid = 1L,
        type = DownloadType.VIDEO,
        items = items.toMutableList(),
    )

    private fun createItem(
        dirPath: String,
        title: String,
        indexTitle: String = "",
    ) = DownloadItemInfo(
        dir_path = dirPath,
        media_type = 1,
        has_dash_audio = false,
        is_completed = true,
        total_bytes = 0L,
        downloaded_bytes = 0L,
        title = title,
        cover = "",
        id = 1L,
        type = DownloadType.VIDEO,
        index_title = indexTitle,
        cid = 1L,
        epid = 0L,
    )
}
