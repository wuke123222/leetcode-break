package dev.leetcodebreak.catalog;

import com.intellij.openapi.progress.ProgressIndicator;
import dev.leetcodebreak.model.Site;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CatalogLoaderTest {
    private ProgressIndicator indicator() {
        return (ProgressIndicator) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ProgressIndicator.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? false : method.getReturnType() == double.class ? 0.0 : null);
    }
    @Test void usesReturnedPageLengthRatherThanRequestedLimit() throws Exception {
        var calls = new AtomicInteger(); var updates = new ArrayList<CatalogLoader.Update>();
        CatalogLoader.load(Site.GLOBAL, body -> {
            int page = calls.getAndIncrement();
            if (page == 1) assertTrue(body.contains("\"skip\":1"));
            return LeetCodeApiTest.RESPONSE.replace("4087", "2").replace("two-sum", page == 0 ? "first-question" : "second-question");
        }, indicator(), updates::add);
        assertEquals(2, calls.get()); assertEquals(2, updates.getLast().problems().size()); assertTrue(updates.getLast().complete());
    }
    @Test void stopsWhenServerRepeatsAPage() {
        assertThrows(IOException.class, () -> CatalogLoader.load(Site.GLOBAL, body -> LeetCodeApiTest.RESPONSE, indicator(), update -> {}));
    }
    @Test void switchesSchemaOnlyWhenGraphqlReportsErrors() throws Exception {
        var calls = new AtomicInteger();
        CatalogLoader.load(Site.CN, body -> calls.getAndIncrement() == 0 ? "{\"errors\":[{}]}" : LeetCodeApiTest.RESPONSE.replace("4087", "1"), indicator(), update -> {});
        assertEquals(2, calls.get());
    }
}
