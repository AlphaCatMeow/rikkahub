package me.rerere.rikkahub.ui.pages.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import kotlin.uuid.Uuid

/** 主界面（对话、生图、翻译）共用的侧边栏：[bigScreen] 时常驻在左侧，否则是抽屉，[open] 打开它 */
class MainDrawer(val bigScreen: Boolean, val open: () -> Unit)

val LocalMainDrawer = staticCompositionLocalOf { MainDrawer(bigScreen = false, open = {}) }

/**
 * 侧边栏放在页面外层，切换对话、生图、翻译时只换右侧内容，侧边栏不跟着重建。
 * 栈顶 [current] 是设置等二级页面时隐藏侧边栏、禁用滑出手势，布局层级保持不变，页面状态不丢。
 */
@Composable
fun MainShell(
    current: NavKey?,
    content: @Composable () -> Unit,
) {
    val section = when (current) {
        is Screen.Chat -> DrawerSection.CHAT
        is Screen.MediaCreation -> DrawerSection.IMAGE
        is Screen.Translator, is Screen.TranslationHistory -> DrawerSection.TRANSLATION
        else -> null
    }
    val showDrawer = section != null
    val currentChatId = (current as? Screen.Chat)?.id?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val focusManager: FocusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val windowSize = currentWindowDpSize()
    val bigScreen = windowSize.width > windowSize.height && windowSize.width >= 1100.dp

    // 切到大屏或二级页面时收起抽屉，避免旋转回竖屏后抽屉残留为打开且关不掉（#1304）
    LaunchedEffect(bigScreen, showDrawer) {
        if ((bigScreen || !showDrawer) && drawerState.isOpen) drawerState.snapTo(DrawerValue.Closed)
    }
    // 打开抽屉时清掉输入焦点，弹窗动画结束后键盘不会再弹出来
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    val mainDrawer = remember(bigScreen) { MainDrawer(bigScreen) { scope.launch { drawerState.open() } } }
    val drawer: @Composable () -> Unit = {
        ChatDrawerContent(
            settings = settings,
            currentSection = section ?: DrawerSection.CHAT,
            currentChatId = currentChatId,
            onNavigate = { if (!bigScreen) scope.launch { drawerState.close() } },
        )
    }

    CompositionLocalProvider(LocalMainDrawer provides mainDrawer) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (bigScreen && showDrawer) drawer()
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !bigScreen && showDrawer,
                drawerContent = { if (!bigScreen && showDrawer) drawer() },
                modifier = Modifier.weight(1f),
                content = content,
            )
        }
        // 放在页面后面注册，抽屉打开时返回键先关抽屉
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }
}

/**
 * 主界面顶栏左侧：从别的页面推进来时是返回键；作为主界面时手机上打开侧边栏，大屏侧边栏常驻时不显示
 */
@Composable
fun MainDrawerButton() {
    val drawer = LocalMainDrawer.current
    if (LocalNavController.current.canGoBack) {
        BackButton()
    } else if (!drawer.bigScreen) {
        IconButton(onClick = drawer.open) {
            Icon(HugeIcons.Menu03, contentDescription = "Menu")
        }
    }
}
