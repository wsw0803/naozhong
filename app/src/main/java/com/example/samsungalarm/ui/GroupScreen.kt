package com.example.samsungalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsungalarm.data.Alarm
import com.example.samsungalarm.ui.theme.AppBackground
import com.example.samsungalarm.ui.theme.GlassPrimary
import com.example.samsungalarm.ui.theme.glassBorder
import com.example.samsungalarm.ui.theme.glassFill
import com.example.samsungalarm.ui.theme.glassShadow
import com.example.samsungalarm.ui.theme.isDark
import com.example.samsungalarm.ui.theme.textPrimary
import com.example.samsungalarm.ui.theme.textSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    vm: AlarmViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val groups by vm.grouped.collectAsStateWithLifecycle()
    var showAddGroup by remember { mutableStateOf(false) }
    var openedGroupId by remember { mutableStateOf<Long?>(null) }

    val opened = groups.firstOrNull { it.group.id == openedGroupId }
    if (opened != null) {
        GroupDetailScreen(
            vm = vm,
            entry = opened,
            onBack = { openedGroupId = null }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("闹钟分组", fontWeight = FontWeight.Bold, color = textPrimary()) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Palette, contentDescription = "外观设置", tint = textPrimary())
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
                ) { Icon(Icons.Default.Add, contentDescription = "新建分组") }
            }
        ) { inner ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(Modifier.height(2.dp)) }
                items(groups, key = { it.group.id }) { entry ->
                    GroupCard(
                        entry = entry,
                        onOpen = { openedGroupId = entry.group.id },
                        onToggleGroup = { on -> vm.toggleGroup(entry.group, on) },
                        onRename = { name -> vm.renameGroup(entry.group, name) },
                        onDeleteGroup = { vm.deleteGroup(entry.group) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
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
            .shadow(12.dp, shape, clip = false, ambientColor = glassShadow(), spotColor = glassShadow())
            .clip(shape)
            .background(glassFill())
            .border(BorderStroke(1.dp, glassBorder()), shape)
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.group.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = textPrimary())
                Text(
                    (if (entry.group.enabled) "已开启" else "已关闭") + " · " + count + " 个闹钟",
                    fontSize = 12.sp,
                    color = textSecondary()
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = textSecondary())
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("进入分组") }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("重命名分组") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("删除分组") }, onClick = { menu = false; onDeleteGroup() })
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

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text("已选 " + selectedIds.size, color = textPrimary()) },
                        navigationIcon = {
                            IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "退出", tint = textPrimary())
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                } else {
                    TopAppBar(
                        title = { Text(entry.group.name, fontWeight = FontWeight.Bold, color = textPrimary()) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textPrimary())
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = textPrimary())
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("重命名分组") }, onClick = { menu = false; renaming = true })
                                    DropdownMenuItem(text = { Text("全部开启") }, onClick = { menu = false; vm.setAllInGroup(entry.group.id, true) })
                                    DropdownMenuItem(text = { Text("全部关闭") }, onClick = { menu = false; vm.setAllInGroup(entry.group.id, false) })
                                    DropdownMenuItem(text = { Text("删除分组") }, onClick = { menu = false; vm.deleteGroup(entry.group); onBack() })
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
                        TextButton(onClick = { entry.alarms.filter { selectedIds.contains(it.id) }.forEach { vm.toggleAlarm(it, true) } }) { Text("开启", color = textPrimary()) }
                        TextButton(onClick = { entry.alarms.filter { selectedIds.contains(it.id) }.forEach { vm.toggleAlarm(it, false) } }) { Text("关闭", color = textPrimary()) }
                        TextButton(onClick = { vm.deleteAlarms(entry.alarms.filter { selectedIds.contains(it.id) }); selectionMode = false; selectedIds = emptySet() }) { Text("删除", color = Color(0xFFE0533D)) }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { creating = true },
                    containerColor = GlassPrimary,
                    contentColor = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "添加闹钟") }
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
                            onLongPress = { selectionMode = true; selectedIds = selectedIds + alarm.id },
                            onSelectToggle = { selectedIds = if (selectedIds.contains(alarm.id)) selectedIds - alarm.id else selectedIds + alarm.id }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
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
@OptIn(ExperimentalFoundationApi::class)
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
            .pointerInput(selectionMode) { detectTapGestures(onTap = { if (selectionMode) onSelectToggle() else onEdit() }, onLongPress = { onLongPress() }) }
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
            Text(buildSubtitle(alarm), fontSize = 11.sp, color = textSecondary())
        }
        if (!selectionMode) {
            IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制闹钟", tint = textSecondary(), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除闹钟", tint = textSecondary(), modifier = Modifier.size(17.dp))
            }
            TextButton(onClick = onEdit, modifier = Modifier.size(width = 46.dp, height = 34.dp)) {
                Text("编辑", fontSize = 12.sp, color = GlassPrimary)
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = GlassPrimary))
        }
    }
}

private fun buildSubtitle(alarm: Alarm): String {
    val days = if (alarm.repeatDays.isEmpty()) "仅一次" else
        listOf(1, 2, 3, 4, 5, 6, 7)
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
