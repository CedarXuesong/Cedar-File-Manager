package com.openxida.cedarmanager.ui

import android.app.Activity
import android.content.Context.INPUT_METHOD_SERVICE
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.openxida.cedarmanager.data.TerminalSessionController
import com.openxida.cedarmanager.domain.ShellArguments
import com.openxida.cedarmanager.ui.effects.BackdropCapture
import com.openxida.cedarmanager.ui.effects.LiquidGlassCircleButton
import com.openxida.cedarmanager.ui.effects.LiquidGlassPopupSurface
import com.openxida.cedarmanager.ui.effects.rememberSampledLayerBackdrop
import com.openxida.cedarmanager.ui.effects.sampledLayerBackdrop
import com.openxida.cedarmanager.ui.effects.verticalGradientBlur
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import com.openxida.cedarmanager.ui.components.CedarDialogActions
import com.openxida.cedarmanager.ui.components.CedarDialogSurface
import com.kyant.backdrop.drawPlainBackdrop

@Composable
internal fun TerminalScreen(
    rootAvailable: Boolean,
    fontSize: Int,
    workingDirectory: String,
    initialRoot: Boolean = false,
    initialCommand: String? = null,
    showTerminalView: Boolean = true,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val controller = remember(context) { TerminalSessionController(context.applicationContext) }
    val sessionState by controller.state.collectAsStateWithLifecycle()
    var terminalSession by remember(controller) { mutableStateOf<TerminalSession?>(null) }
    val currentSession = remember { mutableStateOf<TerminalSession?>(null) }
    val terminalViewHolder = remember { TerminalViewHolder() }
    val fontSizeState = remember(fontSize) { mutableIntStateOf(fontSize) }
    val controlHeld = remember { mutableStateOf(false) }
    val altHeld = remember { mutableStateOf(false) }
    var rootMode by remember(initialRoot) { mutableStateOf(initialRoot) }
    var moreVisible by remember { mutableStateOf(false) }
    var morePresented by remember { mutableStateOf(false) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val backdrop = rememberSampledLayerBackdrop(
        maxFps = 30,
        capture = BackdropCapture.Top(topInset + 82.dp),
    ) {
        drawRect(Color.Black)
        drawContent()
    }
    val textSizePx = remember(fontSizeState.intValue, density) {
        with(density) { fontSizeState.intValue.sp.toPx().roundToInt() }
    }

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val previousDisplayModeId = window?.attributes?.preferredDisplayModeId
        val previousRefreshRate = window?.attributes?.preferredRefreshRate

        if (window != null) {
            val display = context.display
            val currentMode = display?.mode
            val supportedModes = display?.supportedModes.orEmpty()
            val sameResolutionModes = supportedModes.filter { mode ->
                currentMode == null ||
                    (mode.physicalWidth == currentMode.physicalWidth &&
                        mode.physicalHeight == currentMode.physicalHeight)
            }
            val preferredMode = sameResolutionModes.ifEmpty { supportedModes.toList() }
                .maxWithOrNull(
                    compareBy<Display.Mode> { it.refreshRate }
                        .thenBy { it.modeId },
                )
            if (preferredMode != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = preferredMode.modeId
                    preferredRefreshRate = preferredMode.refreshRate
                }
            }
        }

        onDispose {
            if (window != null && previousDisplayModeId != null && previousRefreshRate != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = previousDisplayModeId
                    preferredRefreshRate = previousRefreshRate
                }
            }
        }
    }

    DisposableEffect(controller) {
        controller.onScreenUpdated = { changed ->
            terminalViewHolder.view?.takeIf { it.currentSession === changed }?.onScreenUpdated()
            backdrop.requestRefresh()
        }
        terminalSession = controller.start(initialRoot, workingDirectory, initialCommand)
        currentSession.value = terminalSession
        onDispose { controller.onScreenUpdated = null; controller.close() }
    }
    BackHandler(onBack = onBack)

    val viewClient = remember(controller) {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                fontSizeState.intValue = (fontSizeState.intValue * scale).roundToInt().coerceIn(10, 30)
                return 1f
            }

            override fun onSingleTapUp(e: MotionEvent) {
                terminalViewHolder.view?.let { view ->
                    view.requestFocus()
                    (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            override fun shouldBackButtonBeMappedToEscape() = false
            override fun shouldEnforceCharBasedInput() = false
            override fun shouldUseCtrlSpaceWorkaround() = true
            override fun isTerminalViewSelected() = true
            override fun copyModeChanged(copyMode: Boolean) = Unit
            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
            override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
            override fun onLongPress(event: MotionEvent) = false
            override fun readControlKey() = controlHeld.value
            override fun readAltKey() = altHeld.value
            override fun readShiftKey() = false
            override fun readFnKey() = false

            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
                if (controlHeld.value) controlHeld.value = false
                if (altHeld.value) altHeld.value = false
                return false
            }

            override fun onEmulatorSet() {
                currentSession.value?.let { ready ->
                    if (terminalViewHolder.preparedSession !== ready) {
                        terminalViewHolder.preparedSession = ready
                        terminalViewHolder.view?.invalidate()
                    }
                    Handler(Looper.getMainLooper()).postDelayed({ controller.onEmulatorReady(ready) }, 100L)
                }
            }

            override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { android.util.Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { android.util.Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag, "", e) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().sampledLayerBackdrop(backdrop)) {
                if (showTerminalView) AndroidView(
                    // ANSI clear sequences always home to row zero, so row zero must live below the toolbar.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset + 82.dp, start = 8.dp, end = 8.dp),
                    factory = { viewContext ->
                        TerminalView(viewContext, null).apply {
                            setBackgroundColor(AndroidColor.BLACK)
                            setTextSize(textSizePx)
                            setTerminalViewClient(viewClient)
                            setOnTouchListener { _, event ->
                                if (event.actionMasked == MotionEvent.ACTION_MOVE) backdrop.requestRefresh()
                                false
                            }
                            isFocusable = true
                            isFocusableInTouchMode = true
                            terminalViewHolder.view = this
                        }
                    },
                    update = { view ->
                        view.setTerminalViewClient(viewClient)
                        if (view.tag as? Int != textSizePx) {
                            view.setTextSize(textSizePx)
                            view.tag = textSizePx
                        }
                        terminalSession?.let {
                            currentSession.value = it
                            if (view.currentSession !== it) {
                                view.attachSession(it)
                                view.setTerminalCursorBlinkerState(true, true)
                                view.requestFocus()
                            }
                        }
                    },
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(topInset + 82.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                0.62f to Color.Black.copy(alpha = 0.92f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topInset + 82.dp)
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { verticalGradientBlur() },
                    ),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = topInset + 6.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TerminalGlassButton("back", "返回", backdrop, onBack)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        sessionState.title,
                        color = Color(0xFFE9EBED),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        sessionState.workingDirectory,
                        color = Color(0xFF9CA3A8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                if (!moreVisible && !morePresented) {
                    TerminalGlassButton("more", "终端工具", backdrop) { moreVisible = true }
                } else {
                    Box(Modifier.width(48.dp))
                }
            }

            LiquidGlassPopupSurface(
                visible = moreVisible,
                onDismiss = { moreVisible = false },
                onPresentationChanged = { morePresented = it },
                backdrop = backdrop,
                dark = true,
                expandedWidth = 204.dp,
                surfaceColor = Color(0xFF222629),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = topInset + 60.dp, end = 12.dp),
            ) {
                Text(
                    if (sessionState.running) "${if (sessionState.root) "root" else "shell"} · 运行中"
                    else "已退出 · ${sessionState.exitStatus ?: 0}",
                    color = Color(0xFF9CA3A8),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 15.dp, top = 5.dp, bottom = 5.dp),
                )
                TerminalMenuItem("root", if (rootMode) "切换到 Shell" else "切换到 Root", rootAvailable) {
                    rootMode = !rootMode
                    terminalSession = controller.start(rootMode, workingDirectory)
                    currentSession.value = terminalSession
                    moreVisible = false
                }
                TerminalMenuItem("keyboard", "显示键盘", sessionState.running) {
                    terminalViewHolder.view?.let { view ->
                        view.requestFocus()
                        (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                    moreVisible = false
                }
                TerminalMenuItem("clear", "清屏") { controller.clear(); moreVisible = false }
                TerminalMenuItem("stop", "发送 Ctrl+C", sessionState.running) {
                    controller.sendControl(3); moreVisible = false
                }
                TerminalMenuItem("refresh", "重新启动") {
                    terminalSession = controller.start(rootMode, workingDirectory)
                    currentSession.value = terminalSession
                    moreVisible = false
                }
            }
        }

        TerminalExtraKeys(
            control = controlHeld.value,
            alt = altHeld.value,
            onControl = { controlHeld.value = !controlHeld.value },
            onAlt = { altHeld.value = !altHeld.value },
            onKey = controller::write,
            onControlCode = controller::sendControl,
        )
        Box(Modifier.fillMaxWidth().height(bottomInset).background(Color.Black))
    }
}

@Composable
private fun TerminalGlassButton(
    icon: String,
    description: String,
    backdrop: com.kyant.backdrop.Backdrop,
    onClick: () -> Unit,
) {
    LiquidGlassCircleButton(backdrop, onClick = onClick, surfaceColor = Color(0xFF272B2E)) {
        CedarSvgIcon(icon, description, tint = Color.White, size = 20.dp)
    }
}

@Composable
private fun TerminalMenuItem(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) Color(0xFFE6E8EA) else Color(0xFF777D82)
        CedarSvgIcon(icon, label, tint = tint, size = 18.dp)
        Text(label, color = tint, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun TerminalExtraKeys(
    control: Boolean,
    alt: Boolean,
    onControl: () -> Unit,
    onAlt: () -> Unit,
    onKey: (String) -> Unit,
    onControlCode: (Int) -> Unit,
) {
    var alternate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (alternate) {
                TerminalKey("^C", Modifier.weight(1f)) { onControlCode(3) }
                TerminalKey("^D", Modifier.weight(1f)) { onControlCode(4) }
                TerminalKey("^Z", Modifier.weight(1f)) { onControlCode(26) }
                TerminalKey("/", Modifier.weight(1f)) { onKey("/") }
                TerminalKey("-", Modifier.weight(1f)) { onKey("-") }
                TerminalKey("Ins", Modifier.weight(1f)) { onKey("\u001b[2~") }
            } else {
                TerminalKey("Esc", Modifier.weight(1f)) { onKey("\u001b") }
                TerminalKey("Tab", Modifier.weight(1f)) { onKey("\t") }
                TerminalKey("PgUp", Modifier.weight(1f)) { onKey("\u001b[5~") }
                TerminalKey("Home", Modifier.weight(1f)) { onKey("\u001b[H") }
                TerminalKey("▲", Modifier.weight(1f)) { onKey("\u001b[A") }
                TerminalKey("End", Modifier.weight(1f)) { onKey("\u001b[F") }
            }
            TerminalKey("⋮", Modifier.weight(1f), selected = alternate) { alternate = !alternate }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TerminalKey("Ctrl", Modifier.weight(1f), selected = control, onClick = onControl)
            TerminalKey("Alt", Modifier.weight(1f), selected = alt, onClick = onAlt)
            TerminalKey("PgDn", Modifier.weight(1f)) { onKey("\u001b[6~") }
            TerminalKey("◀", Modifier.weight(1f)) { onKey("\u001b[D") }
            TerminalKey("▼", Modifier.weight(1f)) { onKey("\u001b[B") }
            TerminalKey("▶", Modifier.weight(1f)) { onKey("\u001b[C") }
            TerminalKey("↵", Modifier.weight(1f)) { onKey("\r") }
        }
    }
}

@Composable
private fun TerminalKey(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(46.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF8EB6FF) else Color(0xFFE2E3E5),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ScriptLaunchDialog(
    scriptPath: String,
    rootAvailable: Boolean,
    initialRoot: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRun: (Boolean, List<String>) -> Unit,
) {
    var arguments by remember(scriptPath) { mutableStateOf("") }
    var root by remember(scriptPath, initialRoot) { mutableStateOf(initialRoot && rootAvailable) }
    var error by remember(scriptPath) { mutableStateOf<String?>(null) }

    CedarDialogSurface(onDismiss) {
            Text("运行 Shell 脚本", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                scriptPath,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 3,
                overflow = TextOverflow.MiddleEllipsis,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            top.yukonga.miuix.kmp.basic.TextField(
                value = arguments,
                onValueChange = { arguments = it; error = null },
                label = "脚本参数",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (error != null) {
                Text(error.orEmpty(), fontSize = 12.sp, color = MiuixTheme.colorScheme.error)
            }
            TextButton("编辑脚本", onEdit, modifier = Modifier.padding(top = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Root Shell", fontSize = 14.sp)
                    Text(
                        if (rootAvailable) "以 Root 身份运行脚本" else "Root 不可用",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
                Switch(checked = root, onCheckedChange = if (rootAvailable) ({ root = it }) else null)
            }
            CedarDialogActions(
                onDismiss = onDismiss,
                onConfirm = {
                    runCatching { ShellArguments.parse(arguments) }
                        .onSuccess { onRun(root, it) }
                        .onFailure { error = it.message ?: "脚本参数无效" }
                },
                confirmLabel = "运行",
                enabled = true,
            )
    }
}

private class TerminalViewHolder {
    var view by mutableStateOf<TerminalView?>(null)
    var preparedSession: TerminalSession? = null
}
