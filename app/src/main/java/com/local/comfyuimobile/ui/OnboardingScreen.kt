package com.local.comfyuimobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private data class GuideStep(
    val label: String, val title: String, val subtitle: String, val icon: ImageVector,
    val instructions: List<Pair<String, String>>, val tip: String,
)

private val guideSteps = listOf(
    GuideStep("连接工作站", "把工作站，带到手边。", "手机负责操作，电脑上的 ComfyUI 负责生成。", Icons.Default.Wifi,
        listOf(
            "先准备电脑" to "需要你自己的 ComfyUI，且工作流已能在电脑网页正常生成。App 不附带模型，也不提供算力或云服务账号。",
            "让手机访问电脑" to "同一 Wi-Fi 下，在电脑启动 ComfyUI 时添加 --listen 0.0.0.0，并允许防火墙放行使用的端口（默认 8188）。启动器用户可在启动器里开启局域网访问。",
            "填入工作站地址" to "例如 http://192.168.1.20:8188；使用电脑的局域网 IP，不是手机的 IP 或 127.0.0.1。同网段也可尝试“扫描局域网”。",
        ), "先用手机浏览器打开同一地址；网页能访问，再回 App 连接。目前支持 HTTP，远程访问请使用可信 VPN，不要直接公开无认证端口。"),
    GuideStep("打开工作流", "熟悉的工作流，更顺手。", "从节点画布，走进适合触控的参数表单。", Icons.Default.Tune,
        listOf(
            "选择已有工作流" to "连接后进入“工作流”，浏览文件夹或搜索；点选一项，再点悬浮的“打开参数”。电脑端需已保存工作流，也可从文件或带工作流信息的图片导入。",
            "调整参数，再生成" to "展开部件，修改提示词、种子或图片，确认模型选项有效后再点“生成”。缺少节点、模型或选项失效，需要先在电脑端处理。",
            "复杂控件用高级编辑" to "遮罩、画板等特殊控件可用悬浮的“高级编辑”打开服务器原始前端，关闭后同步回当前工作副本。",
        ), "改参数不会自动覆盖服务器文件。点“保存 / 另存”才写入服务器；临时导入也要保存才长期保留。需要恢复未保存修改，可在设置中开启“工作流草稿”。"),
    GuideStep("保存作品", "让好作品，留在手机里。", "分清云端结果和本地作品，保存才安心。", Icons.Default.Image,
        listOf(
            "先查看结果" to "“结果 → 云端”读取工作站的输出；“本地”显示已经下载到手机的作品。云端结果不会因为能看到就自动保存在手机。",
            "设置自动保存规则" to "在参数页长按有效的输出部件，加入本地保存白名单，再到“设置 → 本地作品”开启自动保存。只保存本 App 提交或你明确接管、且命中规则的任务输出。",
            "手动保存与任务跟踪" to "也可在结果中手动保存原图。在“后台”查看队列、任务和工作站信息；通知权限用于显示本 App 任务进度，可按需授权。",
        ), "云端删除不影响已保存的本地作品。清理或卸载 App 前，请把重要作品另存或导出到手机相册 / 文件。"),
    GuideStep("遇到问题", "随时回来，继续创作。", "常见疑问，在这里有个答案。", Icons.AutoMirrored.Filled.HelpOutline,
        listOf(
            "连接卡住或失败？" to "按连接页显示的失败步骤检查：手机浏览器能否打开同一地址、电脑端口是否放行、VPN 是否可达。网页能打开但初始化失败，查看电脑端前端与自定义节点是否正常。",
            "返回或重开后页面变了？" to "回到桌面不等于保存工作流。重开后可从连接页的已保存工作站进入；未保存参数要依靠已开启的本地草稿恢复。预览版和正式版的数据各自独立。",
            "更新与反馈在哪里？" to "“设置 → 软件更新”检查并安装正式更新。旧版检查不到时，可从 GitHub Release 下载正式 APK 覆盖安装。需要反馈时，在“设置 → 诊断日志”导出并先检查、遮盖私人信息。",
        ), "以后可从连接页“使用指南”或“设置 → 新手引导”重新查看。引导不会自动连接、提交生成或改动你的工作流。"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingScreen(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val content = guideSteps[step]
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    BackHandler { if (step > 0) step-- else onFinish() }
    Surface(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("使用指南", style = MaterialTheme.typography.titleMedium) }, actions = {
                    TextButton(onClick = onFinish) { Text("暂时跳过") }
                })
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Row(Modifier.navigationBarsPadding().fillMaxWidth().widthIn(max = 1000.dp).padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (step > 0) OutlinedButton(onClick = { step-- }) { Text("上一步") }
                        Text("${step + 1} / ${guideSteps.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { if (step == guideSteps.lastIndex) onFinish() else step++ }) {
                            Text(if (step == guideSteps.lastIndex) "开始使用" else "下一步")
                        }
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val expanded = maxWidth >= 720.dp
                Row(Modifier.fillMaxSize().verticalScroll(scroll).padding(if (expanded) 32.dp else 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    if (expanded) {
                        Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("你的创作旅程", style = MaterialTheme.typography.titleMedium)
                            guideSteps.forEachIndexed { index, item ->
                                FilledTonalButton(onClick = { step = index }, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (index == step) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                                    Text("${index + 1}  ${item.label}")
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.size(64.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                            Icon(content.icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(content.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(content.title, style = MaterialTheme.typography.headlineMedium)
                            Text(content.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                content.instructions.forEachIndexed { index, (title, description) ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("0${index + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(title, style = MaterialTheme.typography.titleSmall)
                                            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        if (step == 0) ConnectionGuideDetails()
                        Text(content.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionGuideDetails() {
    var startup by rememberSaveable { mutableIntStateOf(0) }
    var showFirewall by rememberSaveable { mutableStateOf(false) }
    val methods = listOf("启动器", "便携版", "手动启动")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("电脑端怎么开启？", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            methods.forEachIndexed { index, name ->
                FilterChip(selected = startup == index, onClick = { startup = index }, label = { Text(name) })
            }
        }
        Text(when (startup) {
            0 -> "在启动器的设置 / 启动参数里，寻找“局域网访问”“监听地址”或“额外参数”。监听地址填 0.0.0.0，端口记下实际值；若只有额外参数，添加 --listen 0.0.0.0。保存后重启 ComfyUI。不同启动器名称可能不同，不需要生成分享链接。"
            1 -> "先复制一份原启动 .bat 留作备份。在运行 ComfyUI\\main.py 的那一行末尾追加 --listen 0.0.0.0（不要加到 pause 行），保留原来的显卡等参数。保存后用此文件重启。"
            else -> "在原有 Python 环境和 ComfyUI 目录运行：\npython main.py --listen 0.0.0.0 --port 8188\n已有启动命令时，只追加监听参数并保留原参数；端口可按实际情况修改。"
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Windows 查地址：运行 ipconfig，找到当前联网网卡的 IPv4 地址。手机填写 http://该地址:实际端口；0.0.0.0 是监听配置，不能当作连接地址。", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("Windows 防火墙怎么放行？", style = MaterialTheme.typography.titleMedium)
        Text("家庭可信局域网：将当前电脑网络设为“专用”，仅允许 ComfyUI 实际使用的 TCP 端口（默认 8188）。不用关闭防火墙，也不需要路由器公网端口映射。", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { showFirewall = !showFirewall }) {
            Text(if (showFirewall) "收起防火墙步骤" else "查看 Windows 10 / 11 放行步骤")
        }
        if (showFirewall) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("1. 电脑“设置 → 网络和 Internet”，打开当前 Wi-Fi / 以太网的属性，确认是你信任的网络，再选“专用网络”。公共场所或公司受管网络不要随意修改。")
                    Text("2. 按 Win + R，输入 wf.msc 回车，打开“高级安全 Windows Defender 防火墙”；需要管理员权限。")
                    Text("3. 左侧“入站规则”→ 右侧“新建规则”→“端口”→ 下一步。")
                    Text("4. 选 TCP 和“特定本地端口”，填实际端口，如 8188 → 下一步 →“允许连接”。")
                    Text("5. 家庭局域网仅勾选“专用”，命名“ComfyUI 8188”后完成。若使用不同端口，规则和手机地址都填实际值。")
                    Text("6. 打开新规则的属性 →“作用域”，把远程 IP 限制为手机 IP 或你的可信局域网网段。再用手机浏览器测试 http://电脑IP:端口。")
                    Text("启动时弹出安全询问：确认程序是该 ComfyUI 环境的 Python / 服务进程，再允许可信的专用网络；不要只放行启动器外壳。VPN 的网络类型与网段可能不同，需按实际 VPN 地址单独设置；公司策略或第三方防火墙需联系管理员。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        Text("哪些连接可以用？", style = MaterialTheme.typography.titleMedium)
        Text("支持：同一局域网、已互通的 VPN / 虚拟局域网，以及可信网络内的 HTTP 根地址直连。App 不负责创建 VPN 或隧道。", style = MaterialTheme.typography.bodyMedium)
        Text("云平台：只有提供完整 ComfyUI 实例、可直接访问 HTTP 根地址并透传原生接口及 WebSocket 时，才可能兼容，仍需实测。HTTPS 分享页、需要平台登录 / Cookie / API Key 的链接、带路径前缀或令牌参数的链接、纯 API / Serverless 服务，目前不支持。不要把 https 改成 http 来绕过。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
