package dev.leetcodebreak.browser;

import dev.leetcodebreak.model.Site;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SessionCookieTest {
    @Test void preservesHttpOnlyCookieAndExpiration() throws Exception {
        var cookie = new SessionCookie("LEETCODE_SESSION", "test-token-not-a-real-credential", ".leetcode.cn", "/", true, true, 20_000);
        assertEquals(List.of(cookie), SessionCookie.decode(SessionCookie.encode(List.of(cookie)), Site.CN, 10_000));
        assertTrue(SessionCookie.decode(SessionCookie.encode(List.of(cookie)), Site.GLOBAL, 10_000).isEmpty());
        assertTrue(SessionCookie.decode(SessionCookie.encode(List.of(cookie)), Site.CN, 20_000).isEmpty());
    }
    @Test void rejectsLookalikeDomainsAndUnrelatedCookies() {
        assertFalse(Site.CN.ownsDomain("leetcode.cn.evil.test"));
        assertFalse(Site.CN.ownsDomain("evil-leetcode.cn"));
        assertTrue(Site.CN.ownsDomain(".leetcode.cn"));
        assertTrue(Site.CN.ownsDomain("www.leetcode.cn"));
        assertFalse(new SessionCookie("unrelated", "v", "leetcode.cn", "/", true, true, 99).validFor(Site.CN, 1));
    }
    @Test void rejectsMalformedStorageWithoutLeakingContents() {
        assertThrows(IOException.class, () -> SessionCookie.decode("bad-content", Site.CN, 1));
        assertThrows(IOException.class, () -> SessionCookie.decode("x".repeat(256_001), Site.CN, 1));
    }
    @Test void limitsGeneratedProblemUrlsAndExternalNavigation() {
        assertEquals("https://leetcode.cn/problems/two-sum/description/", Site.CN.problem("two-sum"));
        assertThrows(IllegalArgumentException.class, () -> Site.CN.problem("../accounts"));
        assertFalse(Site.isHttps("javascript:alert(1)"));
        assertFalse(Site.isHttps("file:///etc/passwd"));
        assertFalse(Site.isHttps("https://user:pass@leetcode.cn"));
    }
}
