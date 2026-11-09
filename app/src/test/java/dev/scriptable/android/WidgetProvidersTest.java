package dev.scriptable.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Verifies the multi-size provider registry and appWidgetId-scoped data isolation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WidgetProvidersTest {
    private static final String[][] EXPECTED = {
            {WidgetProviders.SMALL, "2\u00d72", "dev.scriptable.android.WidgetSmallProvider"},
            {WidgetProviders.STANDARD, "3\u00d72", "dev.scriptable.android.ScriptWidgetProvider"},
            {WidgetProviders.WIDE, "4\u00d72", "dev.scriptable.android.WidgetWideProvider"},
            {WidgetProviders.LARGE, "4\u00d74", "dev.scriptable.android.WidgetLargeProvider"},
            {WidgetProviders.GRID_4X3, "4\u00d73", "dev.scriptable.android.Widget43Provider"},
            {WidgetProviders.GRID_4X5, "4\u00d75", "dev.scriptable.android.Widget45Provider"},
            {WidgetProviders.GRID_4X6, "4\u00d76", "dev.scriptable.android.Widget46Provider"},
            {WidgetProviders.GRID_4X7, "4\u00d77", "dev.scriptable.android.Widget47Provider"}
    };

    private Context context() { return RuntimeEnvironment.getApplication(); }

    @Test public void eightDistinctProvidersAreRegistered() {
        List<ComponentName> all = WidgetProviders.components(context());
        assertEquals(8, all.size());
        assertEquals(8, new HashSet<>(all).size());
    }

    @Test public void legacyStandardProviderKeepsItsOriginalClassName() {
        ComponentName legacy = new ComponentName(context(), ScriptWidgetProvider.class);
        assertEquals("dev.scriptable.android.ScriptWidgetProvider", legacy.getClassName());
        assertTrue(WidgetProviders.components(context()).contains(legacy));
        assertEquals(WidgetProviders.STANDARD, WidgetProviders.sizeOf(context(), legacy));
        assertEquals("3\u00d72", WidgetProviders.grid(WidgetProviders.STANDARD));
    }

    @Test public void everySizeHasItsOwnProviderAndGrid() {
        Set<String> classNames = new HashSet<>();
        Set<String> grids = new HashSet<>();
        for (String[] row : EXPECTED) {
            ComponentName provider = WidgetProviders.forSize(context(), row[0]);
            assertEquals(row[2], provider.getClassName());
            assertEquals(row[0], WidgetProviders.sizeOf(context(), provider));
            assertEquals(row[1], WidgetProviders.grid(row[0]));
            assertTrue(WidgetProviders.isScriptable(context(), provider));
            assertTrue(WidgetProviders.nameRes(row[0]) != 0);
            classNames.add(provider.getClassName());
            grids.add(row[1]);
        }
        assertEquals(8, classNames.size());
        assertEquals(8, grids.size());
        assertFalse(WidgetProviders.isScriptable(context(), new ComponentName(context(), MainActivity.class)));
    }

    @Test public void pinSizeAliasesResolveToTheRightProvider() {
        assertEquals(WidgetProviders.SMALL, WidgetProviders.normalizeSize("2x2"));
        assertEquals(WidgetProviders.SMALL, WidgetProviders.normalizeSize("2\u00d72"));
        assertEquals(WidgetProviders.SMALL, WidgetProviders.normalizeSize("WidgetSmallProvider"));
        assertEquals(WidgetProviders.WIDE, WidgetProviders.normalizeSize("4*2"));
        assertEquals(WidgetProviders.LARGE, WidgetProviders.normalizeSize("dev.scriptable.android.WidgetLargeProvider"));
        assertEquals(WidgetProviders.GRID_4X3, WidgetProviders.normalizeSize("4x3"));
        assertEquals(WidgetProviders.GRID_4X3, WidgetProviders.normalizeSize("4\u00d73"));
        assertEquals(WidgetProviders.GRID_4X5, WidgetProviders.normalizeSize("4*5"));
        assertEquals(WidgetProviders.GRID_4X6, WidgetProviders.normalizeSize("Widget46Provider"));
        assertEquals(WidgetProviders.GRID_4X7, WidgetProviders.normalizeSize("dev.scriptable.android.Widget47Provider"));
        assertEquals(WidgetProviders.STANDARD, WidgetProviders.normalizeSize("3x2"));
        assertEquals(WidgetProviders.STANDARD, WidgetProviders.normalizeSize(""));
        assertEquals(WidgetProviders.STANDARD, WidgetProviders.normalizeSize("unknown-size"));
    }

    @Test public void subclassesInheritTheWholeProviderLifecycle() {
        assertFalse(Modifier.isFinal(ScriptWidgetProvider.class.getModifiers()));
        Class<?>[] subclasses = {WidgetSmallProvider.class, WidgetWideProvider.class, WidgetLargeProvider.class,
                Widget43Provider.class, Widget45Provider.class, Widget46Provider.class, Widget47Provider.class};
        for (Class<?> type : subclasses) {
            assertTrue(ScriptWidgetProvider.class.isAssignableFrom(type));
            assertTrue(type.getDeclaredMethods().length == 0);
        }
        assertNotNull(WidgetProviders.allWidgetIds(context()));
    }

    @Test public void configuringTwoSizesDoesNotOverwriteEitherInstance() throws Exception {
        Context context = context();
        SharedPreferences store = context.getSharedPreferences("widget-preview", Context.MODE_PRIVATE);
        ScriptWidgetProvider.configure(context, 4101, "script-a", "param-a", "small");
        ScriptWidgetProvider.configure(context, 4102, "script-b", "param-b", "large");
        assertEquals("script-a", store.getString("script.4101", ""));
        assertEquals("param-a", store.getString("parameter.4101", ""));
        assertEquals("small", store.getString("family.4101", ""));
        assertEquals("script-b", store.getString("script.4102", ""));
        assertEquals("param-b", store.getString("parameter.4102", ""));
        assertEquals("large", store.getString("family.4102", ""));
    }

    @Test public void deletingOneSizeKeepsOtherInstanceData() throws Exception {
        Context context = context();
        SharedPreferences store = context.getSharedPreferences("widget-preview", Context.MODE_PRIVATE);
        ScriptWidgetProvider.configure(context, 4201, "script-a", "param-a", "medium");
        ScriptWidgetProvider.configure(context, 4202, "script-b", "param-b", "medium");
        new Widget47Provider().onDeleted(context, new int[]{4201});
        assertEquals("", store.getString("script.4201", ""));
        assertEquals("", store.getString("parameter.4201", ""));
        assertEquals("script-b", store.getString("script.4202", ""));
        assertEquals("param-b", store.getString("parameter.4202", ""));
    }
}
