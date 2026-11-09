package dev.scriptable.android;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Registry of every AppWidget provider shipped by the app.
 *
 * The launcher offers one entry per (receiver, appwidget-provider metadata) pair, so a single
 * provider with several XML files would still show only one entry. Each size therefore has its own
 * receiver. All receivers extend {@link ScriptWidgetProvider}, which owns the complete lifecycle
 * and per-widget data model; the subclasses contain no duplicated logic.
 *
 * Compatibility: the original class name {@code ScriptWidgetProvider} is kept and stays registered
 * as the 3x2 "standard" provider. Widget IDs already on a user's home screen are bound to that
 * ComponentName, and their configuration lives in SharedPreferences keyed only by appWidgetId,
 * so upgrading does not orphan them.
 */
final class WidgetProviders {
    static final String SMALL = "small";
    static final String STANDARD = "standard";
    static final String WIDE = "wide";
    static final String LARGE = "large";
    static final String GRID_4X3 = "4x3";
    static final String GRID_4X5 = "4x5";
    static final String GRID_4X6 = "4x6";
    static final String GRID_4X7 = "4x7";

    private static final Class<?>[] TYPES = {
            ScriptWidgetProvider.class,      // legacy 3x2, do not rename
            WidgetSmallProvider.class,       // 2x2
            WidgetWideProvider.class,        // 4x2
            WidgetLargeProvider.class,       // 4x4
            Widget43Provider.class,          // 4x3
            Widget45Provider.class,          // 4x5
            Widget46Provider.class,          // 4x6
            Widget47Provider.class           // 4x7
    };
    private static final String[] SIZES = {
            STANDARD, SMALL, WIDE, LARGE, GRID_4X3, GRID_4X5, GRID_4X6, GRID_4X7
    };

    private WidgetProviders() { }

    /** All eight receivers, in a stable order. */
    static List<ComponentName> components(Context context) {
        List<ComponentName> result = new ArrayList<>(TYPES.length);
        for (Class<?> type : TYPES) result.add(new ComponentName(context, type));
        return result;
    }

    /** Live widget IDs across every size, deduplicated and in a stable order. */
    static int[] allWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Set<Integer> ids = new LinkedHashSet<>();
        for (ComponentName provider : components(context)) {
            int[] found = null;
            try { found = manager.getAppWidgetIds(provider); } catch (Exception ignored) { }
            if (found == null) continue;
            for (int id : found) ids.add(id);
        }
        int[] result = new int[ids.size()];
        int index = 0;
        for (int id : ids) result[index++] = id;
        return result;
    }

    static boolean isScriptable(Context context, ComponentName provider) {
        if (provider == null) return false;
        for (ComponentName known : components(context)) if (known.equals(provider)) return true;
        return false;
    }

    static String sizeOf(Context context, ComponentName provider) {
        if (provider != null) {
            List<ComponentName> known = components(context);
            for (int i = 0; i < known.size(); i++) if (known.get(i).equals(provider)) return SIZES[i];
        }
        return STANDARD;
    }

    /**
     * Accepts a size name ("small"/"standard"/"wide"/"large"), a grid ("2x2", "4x7", also with
     * the Unicode multiplication sign or an asterisk) or a provider class name so scripts can
     * pin a specific size. Anything unknown falls back to the legacy 3x2 provider.
     */
    static ComponentName forSize(Context context, String requested) {
        return new ComponentName(context, typeForSize(normalizeSize(requested)));
    }

    static String normalizeSize(String requested) {
        String key = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT)
                .replace('\u00d7', 'x').replace('*', 'x').replace(" ", "")
                .replace("_", "").replace("-", "");
        int dot = key.lastIndexOf('.');
        if (dot >= 0) key = key.substring(dot + 1);
        switch (key) {
            case "small": case "2x2": case "widgetsmall": case "widgetsmallprovider":
                return SMALL;
            case "wide": case "4x2": case "widgetwide": case "widgetwideprovider":
                return WIDE;
            case "large": case "4x4": case "widgetlarge": case "widgetlargeprovider":
                return LARGE;
            case "4x3": case "grid43": case "widget43": case "widget43provider":
                return GRID_4X3;
            case "4x5": case "grid45": case "widget45": case "widget45provider":
                return GRID_4X5;
            case "4x6": case "grid46": case "widget46": case "widget46provider":
                return GRID_4X6;
            case "4x7": case "grid47": case "widget47": case "widget47provider":
                return GRID_4X7;
            case "standard": case "3x2": case "medium": case "default":
            case "widgetstandard": case "widgetstandardprovider": case "scriptwidgetprovider":
            case "legacy": case "":
            default:
                return STANDARD;
        }
    }

    static String grid(String size) {
        switch (size) {
            case SMALL: return "2\u00d72";
            case WIDE: return "4\u00d72";
            case LARGE: return "4\u00d74";
            case GRID_4X3: return "4\u00d73";
            case GRID_4X5: return "4\u00d75";
            case GRID_4X6: return "4\u00d76";
            case GRID_4X7: return "4\u00d77";
            default: return "3\u00d72";
        }
    }

    static int nameRes(String size) {
        switch (size) {
            case SMALL: return R.string.widget_name_small;
            case WIDE: return R.string.widget_name_wide;
            case LARGE: return R.string.widget_name_large;
            case GRID_4X3: return R.string.widget_name_4x3;
            case GRID_4X5: return R.string.widget_name_4x5;
            case GRID_4X6: return R.string.widget_name_4x6;
            case GRID_4X7: return R.string.widget_name_4x7;
            default: return R.string.widget_name_standard;
        }
    }

    private static Class<?> typeForSize(String size) {
        for (int i = 0; i < SIZES.length; i++) if (SIZES[i].equals(size)) return TYPES[i];
        return ScriptWidgetProvider.class;
    }
}
