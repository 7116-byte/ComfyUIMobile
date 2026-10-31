# 界面截图与来源

v0.1.73 首页展示：

- [首次连接指南](guide-v0.1.73-phone.png)
- [手机工作流](workflows-v0.1.73-phone.png)
- [手机参数](parameters-v0.1.73-phone.png)
- [平板深色参数](parameters-v0.1.73-tablet.png)

由 `OnboardingScreenTest` 和 `AdaptiveInterfaceTest` 使用 Robolectric 原生图形渲染生成。包含虚构地址、工作流与参数；不是用户数据，也不代表所有设备和云平台均已验收。首页横幅是本仓库原创 SVG 示意图。

v0.1.72 参数页开发预览（测试数据）：

- [手机悬浮操作槽](parameters-v0.1.72-phone.png)
- [小屏顶栏错误和完成提示](parameters-v0.1.72-feedback.png)
- [平板透明顶栏与提示](parameters-v0.1.72-toolbar-tablet.png)
- [工作流悬浮打开参数与全局提示](workflows-v0.1.72-floating.png)

v0.1.71 自适应界面预览（测试数据）：

- [手机后台页](backend-v0.1.71-phone.png)
- [手机参数页](parameters-v0.1.71-phone.png)
- [平板参数页](parameters-v0.1.71-tablet.png)
- [平板横屏深色设置](settings-v0.1.71-tablet-dark.png)

以上来自 Robolectric 原生渲染，不含用户工作流或作品。其余尺寸和页面运行测试后可在 `app/build/outputs/adaptive-preview` 查看；真机截图仅保存在被 Git 忽略的本地调试目录。

v0.1.70 设置页预览：

- [浅色设置首页](settings-v0.1.70-light.png)
- [深色设置首页](settings-v0.1.70-dark.png)

以上由 Robolectric 原生图形渲染生成，使用 Debug 测试变体，因此截图版本带 `-debug`；正式安装包版本为 0.1.70。截图不是 Android 真机验收记录。运行 `./dev.ps1 test` 后可在 `app/build/outputs/settings-preview` 查看其他页面和小屏放大字体预览。

更新公开截图时，优先从上述原生测试输出选择，注明版本和来源；不要把本机真机调试目录或用户作品直接提交到此目录。
