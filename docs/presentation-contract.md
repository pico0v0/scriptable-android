# Scriptable Android presentation implementation

The implementation follows the official [WebView](https://docs.scriptable.app/webview/), [UITable](https://docs.scriptable.app/uitable/), [UITableRow](https://docs.scriptable.app/uitablerow/), [UITableCell](https://docs.scriptable.app/uitablecell/), [CallbackURL](https://docs.scriptable.app/callbackurl/), [WidgetStack](https://docs.scriptable.app/widgetstack/) and [WidgetDate](https://docs.scriptable.app/widgetdate/) API contracts.

## Runtime extension

`runtime-ui.js` is imported after the base runtime has initialized `__runtime`. It registers WebView, UITable, UITableRow, UITableCell, CallbackURL, URLScheme, LinearGradient and WidgetDate. It expands ListWidget accessory preview methods and saves widget family, parameter and `config.widgetId` alongside the tree.

WebView supports instance loadURL/loadHTML/loadFile/loadRequest/evaluateJavaScript/getHTML/present/waitForLoad, static loadURL/loadHTML/loadFile/evaluateJavaScript, and an Android extension `close()`. Request loading uses the common HTTP transport, preserving arbitrary request methods, headers and bodies. HTML is rendered with the final response URL as its base. `shouldAllowRequest` is evaluated in the script worker; native navigation is held until the callback returns.

UITable supports mutable rows/cells, text/subtitles, base64 and URL images, buttons, relative cell widths, font/color/alignment, height, separators, selectable rows, button actions and reload while visible. Native table events execute JavaScript callbacks in the original worker. Async callbacks and callback failures participate in runtime completion tracking.

CallbackURL launches the target Android app with random-token x-success/x-error/x-cancel return URLs. It rejects cancellation, target errors, unavailable apps and a missing response after two minutes. URLScheme produces `scriptable-android://run`/`open` links; query arguments reach `args.queryParameters`. Safari.openInApp uses the isolated native browser surface.

## Native integration

Construct `NativePresentation(Activity, Callback)` on the UI thread. Callback exposes `resolve(String,Object)`, `reject(String,Exception)` and `event(String,JSONObject)`. Route actions accepted by `handles(String)` to `handle(String,String,JSONObject,String runId)` on the UI thread. Call `finishRun(String runId)` after completion to dispose hidden views, `cancel(Set<String>)` when stopping a run, `close()` on destruction, and `onIntent(Intent)` before normal URL dispatch to deliver x-callback results. A three-argument handle overload is available, but it does not attach a run owner for automatic cleanup.

All external/script HTML executes in separate native WebViews. They never receive the Android host interface. File access/content access/universal file URL access are disabled. Private file loading uses an HTTPS virtual origin with canonical-path checks and the app's private storage roots; that origin is accessible only to a view explicitly loaded with loadFile, and access ends if it navigates to an external page. JavaScript completion callbacks use evaluateJavascript polling rather than a privileged JS interface. TLS failures retain Android's default rejection behavior.

## Launcher widgets

The launcher uses a Canvas renderer for the full saved widget tree: horizontal/vertical nested stacks, fixed/flexible spacers, padding, size, text wrapping, line limits, fitting fonts, text/image alignment, opacity, shadows, images, image tint, backgrounds, linear gradients, rounded corners and borders. Dates support date/time/relative/offset/timer formatting at render time.

Each widget stores its own script ID, parameter, family, tree and refresh date. The launcher configuration screen selects a saved script, `args.widgetParameter` and family. A pin request's success callback binds the newly allocated widget ID. Rendering one script no longer overwrites unrelated widgets.

Provider contracts for background execution:

- `instanceConfigs(Context)` returns `{widgetId,scriptId,parameter,family,refreshAfterDate,updatedAt}` records.
- `save(Context,JSONObject)` respects an optional `targetWidgetId`; a background run must supply `config.widgetId`.
- `updateInstance(Context,int,JSONObject)` replaces only one widget tree.
- `requestPin(Context,JSONObject)` accepts optional scriptId/parameter/family/tree plus `widgetSize` (or `size`/`provider`), which selects the pinned provider; omitted sizes keep the legacy 3x2 provider.
- `configure(Context,int,String,String,String)` updates one instance and requests a refresh.
- `refreshNow(Context)` requests a background run.

Provider calls `WidgetRefreshJob.schedule(Context)` and `WidgetRefreshJob.request(Context,int[] nullable)` when widgets are saved, configured, resized or restored. The platform still controls background timing; refreshAfterDate is a requested earliest date, not a guaranteed exact deadline. Android launcher rendering uses a bitmap with a 250000-pixel cap to limit RemoteViews memory. Individual node links use a transparent 8x8 hit grid, so very small link regions may share a tap area. Accessory widget families have home-screen equivalents because Android launchers do not expose the iOS lock-screen widget system.

Register `WidgetConfigureActivity` as exported with ACTION_APPWIDGET_CONFIGURE. Every provider metadata references this activity, and it reads `AppWidgetManager.getAppWidgetInfo(widgetId).provider` to display the size being configured. `widgetFeatures` is `reconfigurable` only: `configuration_optional` was removed so hosts cannot silently skip script selection when adding any of the eight sizes; long-pressing a widget can still reconfigure it on Android 12+.

Eight independent receivers produce eight separate entries in the launcher widget picker. `WidgetProviders` is the single registry used by `save`, `instanceConfigs`, pin callbacks and the configuration screen:

| Receiver | Metadata | targetCellWidth x targetCellHeight | minWidth x minHeight |
| --- | --- | --- | --- |
| `WidgetSmallProvider` (小号) | `@xml/widget_info_small` | 2 x 2 | 110dp x 110dp |
| `ScriptWidgetProvider` (标准, legacy) | `@xml/widget_info` | 3 x 2 | 180dp x 110dp |
| `WidgetWideProvider` (宽版) | `@xml/widget_info_wide` | 4 x 2 | 250dp x 110dp |
| `Widget43Provider` (4×3) | `@xml/widget_info_4x3` | 4 x 3 | 250dp x 180dp |
| `WidgetLargeProvider` (大号) | `@xml/widget_info_large` | 4 x 4 | 250dp x 250dp |
| `Widget45Provider` (4×5) | `@xml/widget_info_4x5` | 4 x 5 | 250dp x 320dp |
| `Widget46Provider` (4×6) | `@xml/widget_info_4x6` | 4 x 6 | 250dp x 390dp |
| `Widget47Provider` (4×7) | `@xml/widget_info_4x7` | 4 x 7 | 250dp x 460dp |

Taller sizes follow the standard `70 * rows - 30` dp formula. `widgetSize` accepts both the named sizes (small/standard/wide/large) and the grid literals (`4x3`, `4x5`, `4x6`, `4x7`, also with `×` or `*`), plus provider class names.

`targetCellWidth`/`targetCellHeight` apply on Android 12+ (API 31); older launchers use `minWidth`/`minHeight` and `resizeMode`. Each metadata also declares `minResizeWidth`, `minResizeHeight`, `previewImage` and a size-specific `description`. The extra receivers are empty subclasses of `ScriptWidgetProvider`, which is no longer final and owns onUpdate/onDeleted/onRestored/onAppWidgetOptionsChanged plus all per-appWidgetId storage. The legacy class name is deliberately retained so home-screen widgets installed by older versions stay bound. The shared `ScriptWidgetProvider` receiver handles explicit pin-result broadcasts for every size; the callback validates the bound widget's provider against `WidgetProviders.isScriptable`.

The size-entry work (both the initial four sizes and the later 4x3/4x5/4x6/4x7 batch) only adds picker entries. Rendering still uses the existing Canvas pipeline and the launcher-reported option bundle; there is no size-adaptive JavaScript API or high-resolution rewrite. Tall widgets are more likely to be downscaled by the existing 250000-pixel bitmap cap, and a launcher whose home-screen grid has fewer than seven rows may clamp a declared 4x7 default size. Both are platform behaviour, not private MIUI hooks. Launcher-specific grid behaviour (notably HyperOS) requires device verification. See [widget-sizes.md](widget-sizes.md) for the implementation, upgrade-compatibility notes, the checklist for adding a size and the full device-verification list.

## Browser preview

Load presentation-browser.js before bridge.js and delegate its accepted actions from Host.call, preserving `options.onEvent` and `options.runId`. Call PresentationBrowser.cancel(runId) when a run ends. HTML executes inside sandboxed opaque-origin iframes with only a per-frame postMessage evaluation channel. Local HTML evaluation, asynchronous completion, native-style table callbacks and local-file resource substitution work in browser preview. Inspecting remote URL source is subject to browser CORS; pages that disallow inspection can be displayed, but evaluation requires the Android app.

## Validation

`node --test tests/presentation.test.cjs` exercises request routing, WebView completion payloads, static loader cleanup, full request transport, table callbacks/reload/async failure handling, navigation decisions, encoded callback URLs, widget date/gradient metadata, per-widget identity and input validation. Android Canvas appearance and launcher-specific behavior still require device/emulator verification.
