# 更新记录

## 未发布

- 系统「添加小部件」列表新增 8 个独立尺寸条目：2×2、3×2（原标准）、4×2、4×3、4×4、4×5、4×6、4×7，各自带独立 Provider、尺寸 metadata、名称、描述与预览图。
- 保留原 `ScriptWidgetProvider` 类名与 `widget_info.xml` 作为 3×2 标准组件；旧桌面组件及其脚本绑定在同签名覆盖升级后继续可用。
- 新增 `WidgetProviders` 注册表；保存、实例配置、后台刷新、删除与 pin 成功回调改为遍历全部 Provider，不再只查询单一 Provider。
- `widget.pin` 支持 `widgetSize`（兼容 `size` / `provider`）指定尺寸，应用内「添加到主屏幕」增加 8 项尺寸下拉；配置页显示当前组件尺寸。
- 所有尺寸使用 `targetCellWidth` / `targetCellHeight`（Android 12+）与 `minWidth` / `minHeight`、`minResizeWidth` / `minResizeHeight`、`resizeMode`（旧版）；移除 `configuration_optional`，保证添加时进入配置页，保留 `reconfigurable` 供长按重新配置。
- 新增 7 项 Robolectric 回归，覆盖 8 尺寸注册、尺寸别名解析、生命周期继承、多实例配置互不覆盖与删除隔离；同步 README、兼容说明、呈现契约及新增的 `docs/widget-sizes.md` 实现说明。
- 验证：`./gradlew testDebugUnitTest assembleDebug lint`（17/17 单测、lint 0 error / 48 warning）、`npm test`（122/122）、`npm run test:ui`、`node tools/check-api-surface.cjs`（731/731），并用 `aapt2` 核对 APK 内 8 个 receiver 与 8 份 metadata。
- 待小米 HyperOS 实机测试：选择器条目与预览图、默认网格是否被裁剪（尤其 4×7）、多实例配置/刷新/删除。渲染仍受 250000 像素位图上限约束，本阶段未改造 Canvas 渲染、高清字体或尺寸自适应 JS API。

## 0.2.1

- 修复默认 Alert 文本框颜色为 JSON `null` 时触发 `Unknown color`，影响问候与随手记等示例。
- 修复通知默认空链接被当成字符串 `"null"`，以及无取消动作时出现异常按钮。
- 修复组件空图片 tint、空链接和表格默认颜色的原生解析。
- 新增全部 20 个内置示例的执行检查及保存、取消、失败路径测试。
- 新增 10 项 Robolectric 安卓框架回归，并纳入 Android 打包流程。

## 0.2.0

- 增加图形、图片、表格、隔离网页和安卓系统服务桥接。
- 增加独立桌面组件配置、后台刷新及持久通知。
- 扩展为 20 个示例、60 组离线 API 说明。
- 改进品牌图标、代码高亮、补全、移动布局、主题和系统栏安全区。

## 0.1.0

- 初始安卓实现：脚本库、Worker 运行器、编辑器、网络、本地存储与基础组件。
