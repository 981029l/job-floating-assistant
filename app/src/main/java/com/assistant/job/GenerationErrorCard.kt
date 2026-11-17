package com.assistant.job

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun GenerationErrorCard(report: String, onClear: () -> Unit) {
    if (report.isBlank()) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by remember(report) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("最近一次生成错误", style = MaterialTheme.typography.titleSmall)
            Text("保存在本机，可复制后粘贴反馈。", fontSize = 12.sp)
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                clipboard.setText(AnnotatedString(report))
                Toast.makeText(context, "错误详情已复制", Toast.LENGTH_SHORT).show()
            }) { Text("复制错误详情") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "查看详情") }
                TextButton(onClick = onClear) { Text("清除") }
            }
            if (expanded) {
                SelectionContainer {
                    Text(report, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
                }
            }
        }
    }
}
