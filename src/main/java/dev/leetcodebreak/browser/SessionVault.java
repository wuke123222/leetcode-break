package dev.leetcodebreak.browser;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.ui.jcef.JBCefCookie;
import com.intellij.ui.jcef.JBCefCookieManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.leetcodebreak.model.Site;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One serialized queue for all projects prevents restore/save/clear races. Never blocks EDT. */
@Service(Service.Level.APP)
public final class SessionVault implements Disposable {
    private final ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("LeetCode Break sessions", 1);
    private final Set<Site> restored = EnumSet.noneOf(Site.class);
    private final Map<Site, String> lastSaved = new EnumMap<>(Site.class);
    public static SessionVault getInstance() { return ApplicationManager.getApplication().getService(SessionVault.class); }
    private static CredentialAttributes key(Site site) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("LeetCode Break", site.host));
    }

    public void restore(Site site, JBCefCookieManager manager, Consumer<String> done) {
        submit(() -> {
            if (!restored.contains(site)) {
                long now = System.currentTimeMillis();
                // An existing live browser session always wins over an older stored session.
                boolean live = cookies(manager, site).stream().anyMatch(c -> c.getName().equals("LEETCODE_SESSION"));
                if (!live) {
                    String saved = PasswordSafe.getInstance().getPassword(key(site));
                    for (SessionCookie c : SessionCookie.decode(saved, site, now)) {
                        boolean ok = manager.setCookie(site.origin(), new JBCefCookie(c.name(), c.value(), c.domain(), c.path(),
                            c.secure(), c.httpOnly(), new Date(now), new Date(now), true, new Date(c.expiresAt()))).get(5, TimeUnit.SECONDS);
                        if (!ok) throw new IllegalStateException("Cookie restore failed");
                    }
                }
                restored.add(site);
            }
            done.accept("会话已准备；登录是否有效以官网为准");
        }, done);
    }

    public void save(Site site, JBCefCookieManager manager, Consumer<String> done) {
        submit(() -> {
            if (!restored.contains(site)) { done.accept("尚未恢复会话，请重新打开插件后重试。"); return; }
            long now = System.currentTimeMillis();
            List<SessionCookie> cookies = cookies(manager, site).stream().map(c -> new SessionCookie(c.getName(), c.getValue(),
                c.getDomain(), c.getPath(), c.isSecure(), c.isHttpOnly(), c.hasExpires() ? c.getExpires().getTime() : now + TimeUnit.DAYS.toMillis(7)))
                .filter(c -> c.validFor(site, now)).sorted(Comparator.comparing(SessionCookie::name).thenComparing(SessionCookie::domain)).toList();
            boolean hasSession = cookies.stream().anyMatch(c -> c.name().equals("LEETCODE_SESSION"));
            String encoded = hasSession ? SessionCookie.encode(cookies) : "";
            if (!Objects.equals(lastSaved.get(site), encoded)) {
                PasswordSafe.getInstance().setPassword(key(site), encoded.isEmpty() ? null : encoded);
                lastSaved.put(site, encoded);
            }
            done.accept(hasSession ? (PasswordSafe.getInstance().isMemoryOnly() ? "会话仅保存在内存（IDE 密码设置）" : "会话已保存 · 有效性以官网为准") : "未检测到登录会话 · 请在官网登录");
        }, done);
    }

    public void clear(Site site, JBCefCookieManager manager, Consumer<String> done) {
        submit(() -> {
            PasswordSafe.getInstance().setPassword(key(site), null);
            lastSaved.remove(site);
            restored.add(site); // A subsequent browser must not re-import a deleted session.
            for (String name : SessionCookie.AUTH_NAMES) {
                if (!manager.deleteCookies(site.origin(), name).get(5, TimeUnit.SECONDS)) throw new IllegalStateException("Cookie deletion failed");
            }
            done.accept("登录会话已清除");
        }, done);
    }

    private List<JBCefCookie> cookies(JBCefCookieManager manager, Site site) throws Exception {
        return manager.getCookies(site.origin(), true).get(5, TimeUnit.SECONDS).stream()
            .filter(c -> site.ownsDomain(c.getDomain()) && SessionCookie.AUTH_NAMES.contains(c.getName())).toList();
    }
    private void submit(CheckedRunnable action, Consumer<String> done) {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            try { action.run(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            // Never include exception messages: native/cookie implementations can contain secrets.
            catch (Exception e) { done.accept("会话存储暂不可用，请检查 IDE 密码设置；仍可在网页登录"); }
        });
    }
    @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
    @Override public void dispose() { executor.shutdownNow(); }
}
