package com.example.samsungalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungalarm.data.Alarm
import com.example.samsungalarm.ui.theme.GlassPrimary
import com.example.samsungalarm.ui.theme.glassBorder
import com.example.samsungalarm.ui.theme.glassFill
import com.example.samsungalarm.ui.theme.glassShadow
import com.example.samsungalarm.ui.theme.isDark
import com.example.samsungalarm.ui.theme.textPrimary
import com.example.samsungalarm.ui.theme.textSecondary
import kotlinx.coroutines.delay

private val ItemSpacing = 14.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    vm: AlarmViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val groups by vm.grouped.collectAsStateWithLifecycle()
    var showAddGroup by remember { mutableStateOf(false) }
    var openedGroupId by remember { mutableStateOf<Long?>(null) }
    var applying by remember { mutableStateOf(false) }

    // ---- 长按拖动排序 ----
    // pointerInput 的 key 只用 id，绝不能带上会随拖动变化的状态：
    // key 一变就会重启手势协程，onDragStart 刚设的状态立刻被丢弃，
    // 表现就是「按住有震动，但完全拖不动」。
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var targetIndex by remember { mutableStateOf(-1) }
    val heights = remember { mutableStateMapOf<Long, Int>() }

    // pointerInput 块不会因 groups 变化重启，直接捕获会拿到旧列表；
    // 用 rememberUpdatedState 保证手势里读到的永远是最新顺序。
    val currentGroups by rememberUpdatedState(groups)

    val density = LocalDensity.current
    val spacingPx = with(density) { ItemSpacing.toPx() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(applying) {
        if (applying) {
            delay(650)
            applying = false
        }
    }

    BackHandler(enabled = openedGroupId != null) { openedGroupId = null }

    AnimatedContent(
        targetState = openedGroupId,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val spec = tween<IntOffset>(durationMillis = 300)
            if (targetState != null) {
                (slideInHorizontally(spec) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally(spec) { -it } + fadeOut())
            } else {
                (slideInHorizontally(spec) { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally(spec) { it } + fadeOut())
            }
        },
        label = "groupNav"
    ) { gid ->
        val opened = groups.firstOrNull { it.group.id == gid }
        if (gid != null && opened != null) {
            GroupDetailScreen(
                vm = vm,
                entry = opened,
                onBack = { openedGroupId = null }
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "闹钟分组",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary()
                                )
                            },
                            actions = {
                                IconButton(onClick = onOpenSettings) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = "外观设置",
                                        tint = textPrimary()
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddGroup = true },
                            containerColor = GlassPrimary,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "新建分组")
                        }
                    }
                ) { inner ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(ItemSpacing)
                    ) {
                        item { Spacer(Modifier.height(2.dp)) }

                        items(groups, key = { it.group.id }) { entry ->
                            val id = entry.group.id
                            val fromIndex = groups.indexOfFirst { it.group.id == id }
                            val isDragging = draggingId == id

                            val offsetY = when {
                                isDragging -> dragOffset
                                draggingId != null -> {
                                    val from = groups.indexOfFirst { it.group.id == draggingId }
                                    val draggedH = (heights[draggingId] ?: 0) + spacingPx
                                    when {
                                        from < 0 -> 0f
                                        targetIndex > from && fromIndex in (from + 1)..targetIndex -> -draggedH
                                        targetIndex < from && fromIndex in targetIndex until from -> draggedH
                                        else -> 0f
                                    }
                                }
                                else -> 0f
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer { translationY = offsetY }
                                    .onGloballyPositioned { heights[id] = it.size.height }
                                    .pointerInput(id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val list = currentGroups
                                                draggingId = id
                                                dragOffset = 0f
                                                targetIndex = list.indexOfFirst { it.group.id == id }
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val list = currentGroups
                                                val from = list.indexOfFirst { it.group.id == draggingId }
                                                if (from < 0) return@detectDragGesturesAfterLongPress
                                                var idx = from
                                                if (dragOffset > 0f) {
                                                    var acc = 0f
                                                    for (i in from + 1 until list.size) {
                                                        val h = heights[list[i].group.id] ?: break
                                                        acc += h + spacingPx
                                                        if (dragOffset > acc) idx = i else break
                                                    }
                                                } else {
                                                    var acc = 0f
                                                    for (i in from - 1 downTo 0) {
                                                        val h = heights[list[i].group.id] ?: break
                                                        acc += h + spacingPx
                                                        if (-dragOffset > acc) idx = i else break
                                                    }
                                                }
                                                targetIndex = idx
                                            },
                                            onDragEnd = {
                                                val list = currentGroups
                                                val from = list.indexOfFirst { it.group.id == draggingId }
                                                val to = targetIndex
                                                if (from >= 0 && to >= 0 && from != to) {
                                                    val reordered = list.toMutableList()
                                                    val moved = reordered.removeAt(from)
                                                    reordered.add(to.coerceIn(0, reordered.size), moved)
                                                    vm.reorderGroups(reordered.map { it.group.id })
                                                }
                                                draggingId = null
                                                dragOffset = 0f
                                                targetIndex = -1
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragOffset = 0f
                                                targetIndex = -1
                                            }
                                        )
                                    }
                            ) {
                                GroupCard(
                                    entry = entry,
                                    dragging = isDragging,
                                    onOpen = { openedGroupId = entry.group.id },
                                    onToggleGroup = { on -> vm.toggleGroup(entry.group, on) },
                                    onRename = { name -> vm.renameGroup(entry.group, name) },
                                    onDeleteGroup = { vm.deleteGroup(entry.group) }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }

                if (applying) {
                    ApplyingOverlay()
                }
            }
        }
    }

    if (showAddGroup) {
        NameDialog(
            title = "新建分组",
            initial = "",
            onDismiss = { showAddGroup = false },
            onConfirm = { name -> vm.addGroup(name); showAddGroup = false }
        )
    }
}

@Composable
private fun GroupCard(
    entry: GroupWithAlarms,
    dragging: Boolean,
    onOpen: () -> Unit,
    onToggleGroup: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onDeleteGroup: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(LocalGlassSettings.current.corner.dp)
    val count = entry.alarms.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                if (dragging) 22.dp else 12.dp,
                shape,
                clip = false,
                ambientColor = glassShadow(),
                spotColor = glassShadow()
            )
            .clip(shape)
            .background(glassFill())
            .border(BorderStroke(1.dp, glassBorder()), shape)
            // 这里必须是 clickable，不能用 detectTapGestures：
            // detectTapGestures 在按下瞬间就 consume 事件，外层
            // detectDragGesturesAfterLongPress 收不到按下，长按永远不触发。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.group.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary()
                )
                Text(
                    (if (entry.group.enabled) "已开启" else "已关闭") + " · " + count + " 个闹钟",
                    fontSize = 12.sp,
                    color = textSecondary()
                )
            }

            Icon(
                Icons.Default.DragHandle,
                contentDescription = "长按拖动排序",
                tint = textSecondary(),
                modifier = Modifier.size(18.dp)
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = textSecondary())
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("进入分组") },
                        onClick = { menu = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名分组") },
                        onClick = { menu = false; renaming = true }
                    )
                    DropdownMenuItem(
                        text = { Text("删除分组") },
                        onClick = { menu = false; onDeleteGroup() }
                    )
                }
            }
            Switch(
                checked = entry.group.enabled,
                onCheckedChange = onToggleGroup,
                colors = SwitchDefaults.colors(checkedTrackColor = GlassPrimary)
            )
        }
    }

    if (renaming) {
        NameDialog(
            title = "重命名分组",
            initial = entry.group.name,
            onDismiss = { renaming = false },
            onConfirm = { name -> onRename(name); renaming = false }
        )
    }
}

@Composable
private fun ApplyingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (isDark()) Color(0xFF232733) else Color(0xFFFFFFFF))
                .padding(horizontal = 26.dp, vertical = 20.dp)
        ) {
            CircularProgressIndicator(color = GlassPrimary, strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text("正在应用…", fontSize = 13.sp, color = textPrimary())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDetailScreen(
    vm: AlarmViewModel,
    entry: GroupWithAlarms,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var creating by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }

    LaunchedEffect(applying) {
        if (applying) {
            delay(650)
            applying = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text("已选 " + selectedIds.size, color = textPrimary()) },
                        navigationIcon = {
                            IconButton(onClick = {
                                selectionMode = false
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "退出", tint = textPrimary())
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                entry.group.name,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary()
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = textPrimary()
                                )
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = textPrimary())
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("重命名分组") },
                                        onClick = { menu = false; renaming = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("全部开启") },
                                        onClick = { menu = false; vm.setAllInGroup(entry.group.id, true) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("全部关闭") },
                                        onClick = { menu = false; vm.setAllInGroup(entry.group.id, false) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除分组") },
                                        onClick = { menu = false; vm.deleteGroup(entry.group); onBack() }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                if (selectionMode) {
                    BottomAppBar(containerColor = Color.Transparent) {
                        TextButton(onClick = { selectedIds = entry.alarms.map { it.id }.toSet() }) {
                            Text("全选", color = textPrimary())
                        }
                        TextButton(onClick = {
                            val picked = entry.alarms.filter { selectedIds.contains(it.id) }
                            picked.forEach { vm.toggleAlarm(it, true) }
                            selectionMode = false
                            selectedIds = emptySet()
                        }) { Text("开启", color = textPrimary()) }
                        TextButton(onClick = {
                            val picked = entry.alarms.filter { selectedIds.contains(it.id) }
                            picked.forEach { vm.toggleAlarm(it, false) }
                            selectionMode = false
                            selectedIds = emptySet()
                        }) { Text("关闭", color = textPrimary()) }
                        TextButton(onClick = {
                            vm.deleteAlarms(entry.alarms.filter { selectedIds.contains(it.id) })
                            selectionMode = false
                            selectedIds = emptySet()
                        }) { Text("删除", color = Color(0xFFE0533D)) }
                    }
                }
            },
            floatingActionButton = {
                if (!selectionMode) {
                    FloatingActionButton(
                        onClick = { creating = true },
                        containerColor = GlassPrimary,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加闹钟")
                    }
                }
            }
        ) { inner ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(2.dp)) }

                if (entry.alarms.isEmpty()) {
                    item {
                        val shape = RoundedCornerShape(LocalGlassSettings.current.corner.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(glassFill())
                                .border(BorderStroke(1.dp, glassBorder()), shape)
                                .padding(16.dp)
                        ) {
                            Text("这个分组还没有闹钟", fontSize = 14.sp, color = textSecondary())
                        }
                    }
                } else {
                    items(entry.alarms, key = { it.id }) { alarm ->
                        GlassAlarmRow(
                            alarm = alarm,
                            groupEnabled = entry.group.enabled,
                            selectionMode = selectionMode,
                            selected = selectedIds.contains(alarm.id),
                            onToggle = { on -> vm.toggleAlarm(alarm, on) },
                            onDuplicate = { vm.duplicateAlarm(alarm) },
                            onDelete = { vm.deleteAlarm(alarm) },
                            onEdit = { editing = alarm },
                            onLongPress = {
                                selectionMode = true
                                selectedIds = selectedIds + alarm.id
                            },
                            onSelectToggle = {
                                selectedIds = if (selectedIds.contains(alarm.id))
                                    selectedIds - alarm.id
                                else selectedIds + alarm.id
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        if (applying) {
            ApplyingOverlay()
        }
    }

    if (creating) {
        AlarmEditDialog(
            alarm = null,
            groupId = entry.group.id,
            onDismiss = { creating = false },
            onSave = { a -> vm.saveAlarm(a); creating = false }
        )
    }

    val target = editing
    if (target != null) {
        AlarmEditDialog(
            alarm = target,
            groupId = target.groupId,
            onDismiss = { editing = null },
            onSave = { a -> vm.saveAlarm(a); editing = null }
        )
    }

    if (renaming) {
        NameDialog(
            title = "重命名分组",
            initial = entry.group.name,
            onDismiss = { renaming = false },
            onConfirm = { name -> vm.renameGroup(entry.group, name); renaming = false }
        )
    }
}

@Composable
private fun GlassAlarmRow(
    alarm: Alarm,
    groupEnabled: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    onSelectToggle: () -> Unit
) {
    val active = alarm.enabled && groupEnabled
    val shape = RoundedCornerShape(LocalGlassSettings.current.corner.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) GlassPrimary.copy(alpha = 0.15f) else glassFill())
            .border(BorderStroke(1.dp, glassBorder()), shape)
            .pointerInput(selectionMode) {
                detectTapGestures(
                    onTap = { if (selectionMode) onSelectToggle() else onEdit() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onSelectToggle() },
                colors = CheckboxDefaults.colors(checkedColor = GlassPrimary)
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                String.format("%02d:%02d", alarm.hour, alarm.minute),
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) textPrimary() else textSecondary()
            )
            Text(
                buildSubtitle(alarm) +
                        (if (selectionMode) (if (alarm.enabled) " · 已开启" else " · 已关闭") else ""),
                fontSize = 11.sp,
                color = textSecondary()
            )
        }

        if (!selectionMode) {
            IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "复制闹钟",
                    tint = textSecondary(),
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除闹钟",
                    tint = textSecondary(),
                    modifier = Modifier.size(17.dp)
                )
            }
            TextButton(onClick = onEdit, modifier = Modifier.size(width = 46.dp, height = 34.dp)) {
                Text("编辑", fontSize = 12.sp, color = GlassPrimary)
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = GlassPrimary)
            )
        }
    }
}

private fun buildSubtitle(alarm: Alarm): String {
    val days = if (alarm.repeatDays.isEmpty()) "仅一次" else listOf(1, 2, 3, 4, 5, 6, 7)
        .filter { alarm.repeatDays.contains(it) }
        .joinToString(" ") { d -> dayChar(d) }
    val label = if (alarm.label.isBlank()) "" else " · " + alarm.label
    return days + label
}

private fun dayChar(d: Int): String = when (d) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"
    5 -> "五"; 6 -> "六"; else -> "日"
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = textPrimary()) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                Text("确定", color = GlassPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = textSecondary()) }
        },
        containerColor = if (isDark()) Color(0xFF1B1F2A) else Color(0xFFF7F9FC)
    )
}
