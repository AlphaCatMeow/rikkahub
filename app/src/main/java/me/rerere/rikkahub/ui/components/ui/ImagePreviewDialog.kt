package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.runtime.remember
import me.rerere.ui.toast.ToastType
import me.rerere.ui.toast.Toaster
import com.jvziyaoyao.scale.image.pager.ImagePager
import com.jvziyaoyao.scale.zoomable.pager.rememberZoomablePagerState
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.compose.koinInject

@Composable
fun ImagePreviewDialog(
    images: List<String>,
    initialPage: Int = 0,
    // 底部操作栏里额外的按钮，参数是当前页
    actions: @Composable RowScope.(page: Int) -> Unit = {},
    onDismissRequest: () -> Unit,
) {
    val state = rememberZoomablePagerState(initialPage = initialPage) { images.size }
    val toaster = LocalToaster.current
    val saveImage = rememberImageSaver()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box {
            ImagePager(
                modifier = Modifier.fillMaxSize(),
                pagerState = state,
                imageLoader = { index ->
                    val painter = rememberAsyncImagePainter(images[index])
                    return@ImagePager Pair(painter, painter.intrinsicSize)
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { saveImage(images[state.currentPage]) }
                ) {
                    Icon(HugeIcons.Download01, null, tint = Color.White)
                }
                actions(state.currentPage)
            }

            // Dialog 是独立窗口，Activity 里的 Toaster 会被盖住，这里再放一个显示在图片之上
            Toaster(
                state = toaster,
                modifier = Modifier.align(Alignment.TopCenter),
                overlay = true,
            )
        }
    }
}

/** 返回把图片保存到相册的操作，带「保存中 / 已保存 / 失败」提示。 */
@Composable
fun rememberImageSaver(): (String) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val filesManager: FilesManager = koinInject()
    val toaster = LocalToaster.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember(context, resources, filesManager, toaster, lifecycleOwner) {
        { imgUrl: String ->
            lifecycleOwner.lifecycleScope.launch {
                // 保存完成前一直显示，结束后收起，避免和结果提示叠在一起
                val saving = toaster.showProgress(resources.getString(R.string.image_preview_saving))
                runCatching {
                    filesManager.saveMessageImage(context, imgUrl)
                }.onSuccess {
                    saving.dismiss()
                    toaster.show(message = resources.getString(R.string.image_preview_saved), type = ToastType.Success)
                }.onFailure {
                    saving.dismiss()
                    it.printStackTrace()
                    toaster.show(
                        message = it.toString(),
                        type = ToastType.Error
                    )
                }
            }
            Unit
        }
    }
}
