# 桌面小组件尺寸实现说明

本文说明系统「添加小部件」列表中的 8 个独立尺寸是如何实现的、旧组件如何保持兼容，以及后续新增尺寸需要同步修改哪些文件。面向使用者的操作说明见 [README](../README.md#桌面小组件尺寸)，渲染与 Provider 的 API 契约见 [呈现契约](presentation-contract.md)。

## 设计前提

Android 启动器为**每一对 (BroadcastReceiver, `android.appwidget.provider` metadata)** 生成一个选择器条目。一个 Provider 配多份 XML 不会产生多个条目，应用内配置菜单也无法影响系统选择器。因此 8 个尺寸对应 8 个独立 receiver，每个 receiver 指向自己的 `res/xml/widget_info*.xml`。

用户要求不要复制运行逻辑，所以所有 receiver 都是 `ScriptWidgetProvider` 的空子类。原类去掉 `final`，保留类名并继续作为 3×2 标准 Provider，同时充当公共基类，独享 `onUpdate` / `onDeleted` / `onRestored` / `onAppWidgetOptionsChanged` / `onReceive` 与全部按 `appWidgetId` 索引的数据读写。

## 尺寸清单与文件对应

| 显示名称 | 默认网格 | Receiver 类 | metadata | 预览图 |
| --- | --- | --- | --- | --- |
| Scriptable 小号 | 2×2 | `WidgetSmallProvider` | `widget_info_small.xml` | `widget_preview_small.png` |
| Scriptable 标准 | 3×2 | `ScriptWidgetProvider`（旧类名） | `widget_info.xml`（旧文件名） | `widget_preview_standard.png` |
| Scriptable 宽版 | 4×2 | `WidgetWideProvider` | `widget_info_wide.xml` | `widget_preview_wide.png` |
| Scriptable 4×3 | 4×3 | `Widget43Provider` | `widget_info_4x3.xml` | `widget_preview_4x3.png` |
| Scriptable 大号 | 4×4 | `WidgetLargeProvider` | `widget_info_large.xml` | `widget_preview_large.png` |
| Scriptable 4×5 | 4×5 | `Widget45Provider` | `widget_info_4x5.xml` | `widget_preview_4x5.png` |
| Scriptable 4×6 | 4×6 | `Widget46Provider` | `widget_info_4x6.xml` | `widget_preview_4x6.png` |
| Scriptable 4×7 | 4×7 | `Widget47Provider` | `widget_info_4x7.xml` | `widget_preview_4x7.png` |

名称与描述在 `res/values/strings.xml`（`widget_name_*` / `widget_description_*`），由 Manifest 的 `android:label` 和 metadata 的 `android:description` 引用。预览图放在 `res/drawable-nodpi/`，按 100 dp/格生成（例如 4×7 为 400×700 px），避免被按密度缩放。

## 尺寸声明规则

每份 metadata 同时声明两套尺寸，不使用任何厂商私有接口：

- **Android 12+（API 31）**：`targetCellWidth` / `targetCellHeight` 指定默认网格。
- **旧版 Android**：`minWidth` / `minHeight` 按标准公式 `70 × 格数 − 30` dp；例如 4×7 为 `250dp × 460dp`。
- 全部尺寸另有 `minResizeWidth="110dp"`、`minResizeHeight="110dp"`、`resizeMode="horizontal|vertical"`、`updatePeriodMillis="1800000"`、`initialLayout="@layout/widget"`、`widgetCategory="home_screen"`。
- `android:configure` 统一指向 `WidgetConfigureActivity`；`widgetFeatures` 只保留 `reconfigurable`。移除 `configuration_optional` 是为了避免启动器跳过配置页、导致新组件没有绑定脚本；长按重新配置在 Android 12+ 仍可用。

`configure` 活动以 `ACTION_APPWIDGET_CONFIGURE` 导出，通过 `AppWidgetManager.getAppWidgetInfo(widgetId).provider` 判断当前尺寸并在页面顶部显示尺寸名，脚本、参数、family 的保存仍走 `ScriptWidgetProvider.configure(...)`。

## 注册表 `WidgetProviders`

`app/src/main/java/dev/scriptable/android/WidgetProviders.java` 是唯一的尺寸清单，`TYPES` 与 `SIZES` 两个数组按相同下标一一对应：

| 方法 | 用途 |
| --- | --- |
| `components(Context)` | 返回全部 8 个 `ComponentName`，顺序稳定 |
| `allWidgetIds(Context)` | 逐个 Provider 调 `getAppWidgetIds`，用 `LinkedHashSet` 去重后返回全部实例 ID |
| `isScriptable(Context, ComponentName)` | 判断 provider 是否属于本应用（pin 结果校验） |
| `sizeOf(Context, ComponentName)` | 由 provider 反查尺寸名，配置页使用 |
| `normalizeSize(String)` | 接受 `small/standard/wide/large`、`4x3`/`4×3`/`4*3`、Provider 类名等别名，未知值回落到 `standard` |
| `forSize(Context, String)` | 由尺寸名得到目标 `ComponentName`（pin 使用） |
| `grid(String)` / `nameRes(String)` | 显示用的网格文本与名称资源 |

`ScriptWidgetProvider.save(...)` 和 `instanceConfigs(...)` 已改为调用 `allWidgetIds(...)`，原先的 `getAppWidgetIds(new ComponentName(context, ScriptWidgetProvider.class))` 只能看到 3×2 实例。`WidgetRefreshJob` 通过 `instanceConfigs(...)` 取得待刷新实例，因此**无需修改**就自动覆盖全部尺寸；`HeadlessWidgetRunner` 后台执行 `widget.save` 时携带 `targetWidgetId`，保证只更新当前实例。

`requestPin(...)` 读取 `widgetSize`（兼容 `size` / `provider`），经 `normalizeSize` + `forSize` 选择目标 Provider 后调用 `requestPinAppWidget(target, null, callback)`；省略时仍使用旧 3×2 Provider。pin 成功广播由共享的 `ScriptWidgetProvider` receiver 处理，校验条件从「等于旧 Provider」改为 `WidgetProviders.isScriptable(...)`。返回值附带 `size`、`grid`、`provider` 便于排查。

应用内组件预览页的尺寸下拉位于 `app/src/main/assets/app.js`，最终以 `hostCall('widget.pin', { ..., widgetSize })` 传入；`MainActivity` 的 `widget.pin` 分支原样转发 payload。

## 旧组件兼容

- 保留 `dev.scriptable.android.ScriptWidgetProvider` 类名与 `widget_info.xml` 文件名。已添加到桌面的旧组件绑定的是这个 ComponentName，改类名会让它们在升级后失去 Provider。
- 配置数据存于 SharedPreferences `widget-preview`，键为 `script.<id>`、`parameter.<id>`、`family.<id>`、`updated.<id>`、`refreshAfter.<id>`；布局树为 `files/widgets/instance-<id>.json`。全部只按 `appWidgetId` 索引，与 Provider 种类无关，新增尺寸不会串数据。
- `onDeleted` 只删除传入 ID 的数据，`updateInstance` 只替换单个实例的树；测试中覆盖了「配置两个尺寸互不覆盖」和「删除一个尺寸不影响另一个实例」。
- 覆盖升级仍需**相同签名**的 Release 包；自行签名的 Debug APK 不能覆盖官方 Release，这一点未变。

## 新增一个尺寸的检查清单

1. 新建空的 `Widget<Size>Provider.java`，`extends ScriptWidgetProvider`。
2. 在 `AndroidManifest.xml` 添加 receiver：`android:label`、`APPWIDGET_UPDATE` intent-filter、`android.appwidget.provider` meta-data。
3. 新建 `res/xml/widget_info_<size>.xml`，按上述公式填写 `targetCell*` 与 `min*`，其余属性与现有文件保持一致。
4. 在 `res/values/strings.xml` 添加 `widget_name_*` 与 `widget_description_*`。
5. 生成 `res/drawable-nodpi/widget_preview_<size>.png`。
6. 在 `WidgetProviders.java` 的 `TYPES`、`SIZES` 同步插入（下标必须对齐），并扩展 `normalizeSize`、`grid`、`nameRes`。
7. 在 `app/src/main/assets/app.js` 的尺寸下拉中加一项。
8. 在 `WidgetProvidersTest.java` 的 `EXPECTED` 中加一行，并更新断言数量。
9. 同步 [README](../README.md#桌面小组件尺寸)、[呈现契约](presentation-contract.md) 的尺寸表与本文表格；如发版则更新 [更新记录](../CHANGELOG.md)。
10. 执行 `./gradlew testDebugUnitTest assembleDebug lint`，并用 `aapt2 dump xmltree app-debug.apk --file AndroidManifest.xml` 核对 receiver 与 metadata 都进入 APK。

## 已知限制

- 本阶段只增加**尺寸条目**。渲染仍走原 Canvas → `RemoteViews` 管线，受 `WidgetRenderer` 的 250000 像素位图上限约束，竖向高尺寸更容易被降采样；链接点击仍使用 8×8 透明命中网格。没有尺寸自适应 JS API，也没有高清字体/图片渲染改造。
- 启动器桌面网格行数少于 7 时，声明为 4×7 的默认尺寸可能被裁剪或 clamp；这是平台行为，未用私有接口绕过。本次验证的 HyperOS 设备上未出现裁剪。
- 后台刷新时机仍由系统调度与电池策略决定，`refreshAfterDate` 只是期望的最早时间。

## 小米 HyperOS 实机验证

已在小米 HyperOS 设备上完成实机验证，结果正常：

- 系统「添加小部件」列表中 8 个尺寸条目全部出现，可分别添加。
- 默认网格符合声明，包括 4×7 未被裁剪。
- 多个不同尺寸的实例可分别绑定不同脚本与参数，配置、刷新与删除互不干扰。

上述结论只覆盖启动器的选择器与实例行为；权限、后台调度、系统栏等其他系统能力不在本次验证范围内，仍需单独真机反馈。编译与 Robolectric 通过本身不能替代启动器验收，这里的结论来自实机。

## 验证命令

```bash
./gradlew testDebugUnitTest assembleDebug lint
npm test
npm run test:ui
node tools/check-api-surface.cjs
node --test tests/presentation.test.cjs
```

本次结果：17/17 Robolectric（其中 7 项为多尺寸用例）、lint 0 error / 48 warning、Worker 122/122、浏览器检查全通过、公开入口 731/731，APK 内 8 个 receiver 与 8 份 metadata 经 `aapt2` 核对一致。桌面组件另已在小米 HyperOS 实机验证，详见上一节。
