# ComfyUI Mobile

ComfyUI Mobile 是一个面向可信局域网的原生 Android 客户端。它把 ComfyUI 工作流转换成手机友好的纵向参数表单，不在主界面显示节点画布。

> 当前版本：v0.1.0。首要兼容 ComfyUI 0.28.0、ComfyUI Frontend 1.45.21。

## 功能

- 保存、切换和自动扫描局域网 ComfyUI（默认端口 8188）
- 显示 ComfyUI/前端版本、GPU、显存、队列、运行节点和进度
- 工作流搜索、文件夹读取、导入、导出、复制、改名、移动、删除和冲突保护
- 隐藏 WebView 调用服务器自己的 ComfyUI 前端，使用真实节点定义执行 `loadGraphData()` 和 `graphToPrompt()`
- 原生参数控件：提示词、文本、数字、滑杆、开关、下拉、图片和视频上传；常用 WebUI 参数自动显示中文名称
- 每个工作流可保存参数名称、顺序、可见性及“主要/更多”分组
- 全局提示词历史最多 50 条，去重、搜索、复用、单删和清空
- 任务、历史、图片及视频结果；视频采用流式播放和流式保存
- App 自己提交的未完成任务使用前台通知显示进度
- GitHub Release 更新检查、SHA-256、包名、版本号和签名证书校验

## 使用

1. 在电脑上以 `--listen 0.0.0.0` 启动 ComfyUI，并允许 Windows 防火墙放行 8188 端口。
2. 确认手机和电脑连接同一个可信 Wi-Fi。
3. 在 App 输入 `http://电脑局域网IP:8188`，或点击“扫描局域网”。
4. 选择工作流，在“参数”页调整后生成。

复杂画板、遮罩、曲线等控件会保留原值并标记为特殊控件，可进入“高级编辑”使用服务器原始前端。高级编辑会优先切换为简体中文，返回时重新同步原生参数页。

## 局域网安全

本 App 只接受 HTTP 的 RFC1918 私有地址、localhost 和 `.local` 主机名，拒绝公网 IP、HTTPS 反向代理和认证登录。ComfyUI 默认没有账号鉴权，请勿把 8188 端口暴露到公网，也不要连接不可信 Wi-Fi 中的陌生服务器。

隐藏 WebView 没有注册 Android JavaScript 接口。自定义节点前端脚本只能在 WebView 的普通网页权限内运行，不能直接调用原生对象。

## 构建

要求 JDK 17、Android SDK 36：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Release 签名配置必须位于仓库外：

```text
../tools/signing/ComfyUIMobile-signing.properties
```

配置格式：

```properties
storeFile=C:/absolute/path/ComfyUIMobile-release.jks
storePassword=...
keyAlias=comfyuimobile
keyPassword=...
```

缺少该文件时，任何 Release 任务都会直接失败。密钥和密码不得提交到 Git。

## 截图

截图预留位置见 [`docs/screenshots`](docs/screenshots/README.md)。

## 已知边界

- 仅 Android、简体中文优先、可信局域网。
- 不提供原生节点画布；高级编辑仅作为特殊控件备用入口。
- 连接时依赖目标服务器自己的前端能力；不兼容或缺失节点时会停止生成并显示原因。
- 音频及其他输出首版只显示任务元数据，不提供专用播放器。

## 许可

[MIT License](LICENSE)
