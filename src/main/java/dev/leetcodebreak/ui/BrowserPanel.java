package dev.leetcodebreak.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.jcef.*;
import com.intellij.util.ui.JBUI;
import com.google.gson.*;
import com.intellij.openapi.progress.ProgressIndicator;
import dev.leetcodebreak.browser.SessionVault;
import dev.leetcodebreak.model.Site;
import org.cef.browser.*;
import org.cef.handler.*;
import org.cef.network.CefRequest;
import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.*;

public final class BrowserPanel extends JBPanel<BrowserPanel> implements Disposable {
    private final JBLabel status = new JBLabel("在这里打开题目，直接使用 LeetCode 编辑器与评测");
    private final JPanel surface = new JPanel(new BorderLayout());
    private final Timer timer;
    private volatile JBCefBrowser browser;
    private JBCefJSQuery catalogQuery;
    private record Pending(Site site, CompletableFuture<String> result) {}
    private final ConcurrentHashMap<String, Pending> requests = new ConcurrentHashMap<>();
    private volatile Site site;
    private volatile boolean disposed;
    private volatile boolean ready;
    private boolean savePending;
    private int generation;
    private String pendingUrl;
    private volatile String visibleUrl;
    private Runnable syncAction = () -> {};

    public BrowserPanel(Site site) {
        super(new BorderLayout(0, 6)); this.site = site;
        var tools = new JPanel(new GridLayout(0, 3, 6, 4));
        addButton(tools, "←", "后退", () -> { if (browser != null) browser.getCefBrowser().goBack(); });
        addButton(tools, "刷新", "重新加载页面", () -> { if (browser != null) browser.getCefBrowser().reload(); });
        addButton(tools, "题库", "打开官方题库", () -> open(this.site, this.site.problemSet()));
        addButton(tools, "登录", "在官方页面登录", () -> open(this.site, this.site.login()));
        addButton(tools, "保存会话", "会话每 15 秒自动保存，也可手动保存", this::saveSession);
        addButton(tools, "同步官方题库", "使用当前官网会话获取题库与做题状态", () -> syncAction.run());
        addButton(tools, "清除会话", "清除当前站点的登录凭据", this::clearSession);
        addButton(tools, "浏览器打开", "在系统浏览器打开（登录态独立）", () -> BrowserUtil.browse(Site.isHttps(visibleUrl) ? visibleUrl : this.site.problemSet()));
        var north = new JPanel(new BorderLayout(0, 8));
        north.setBorder(JBUI.Borders.empty(8)); north.add(tools, BorderLayout.NORTH); north.add(status, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        surface.add(new JBLabel("选择推荐题目后，在这里开始练习", SwingConstants.CENTER), BorderLayout.CENTER);
        add(surface, BorderLayout.CENTER);
        timer = new Timer(15_000, e -> saveSession());
    }

    private static void addButton(JPanel panel, String title, String hint, Runnable action) {
        JButton button = new JButton(title); button.setToolTipText(hint); button.addActionListener(e -> action.run()); panel.add(button);
    }
    public void switchSite(Site target) {
        if (site == target) return;
        site = target; generation++; ready = false;
        visibleUrl = target.problemSet(); pendingUrl = visibleUrl;
        if (browser != null) prepareSession();
    }
    public void open(Site target, String url) {
        if (disposed || !Site.isHttps(url)) return;
        if (site != target) switchSite(target);
        pendingUrl = url; visibleUrl = url;
        if (browser == null) createBrowser();
        else if (ready) browser.loadURL(url);
    }

    private void createBrowser() {
        if (!JBCefApp.isSupported()) {
            surface.removeAll();
            var fallback = new JPanel(new GridLayout(0, 1, 0, 12));
            fallback.setBorder(JBUI.Borders.empty(28));
            fallback.add(new JBLabel("当前运行时不支持 JCEF，请使用带 JCEF 的 JetBrains Runtime。"));
            JButton external = new JButton("在系统浏览器打开 LeetCode");
            external.addActionListener(e -> BrowserUtil.browse(pendingUrl)); fallback.add(external);
            surface.add(fallback, BorderLayout.NORTH); surface.revalidate(); surface.repaint(); return;
        }
        browser = new JBCefBrowser();
        Disposer.register(this, browser);
        catalogQuery = JBCefJSQuery.create((JBCefBrowserBase) browser);
        Disposer.register(this, catalogQuery);
        catalogQuery.addHandler(message -> {
            if (message.length() > 4_100_000) return null;
            try {
                JsonObject packet = JsonParser.parseString(message).getAsJsonObject();
                String id = packet.get("id").getAsString();
                Pending pending = requests.get(id);
                if (pending == null || !onSite(pending.site(), browser.getCefBrowser().getURL())) return null;
                requests.remove(id);
                if (packet.has("error")) pending.result().completeExceptionally(new java.io.IOException("官网请求失败，请完成网页验证或重新登录后同步。"));
                else pending.result().complete(packet.get("data").toString());
            } catch (RuntimeException ignored) { /* Invalid page messages cannot invoke IDE actions. */ }
            return null;
        });
        // Use JBCef's multiplexer so the platform's own lifecycle handlers remain installed.
        browser.getJBCefClient().addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override public void onAfterCreated(CefBrowser cef) { ui(BrowserPanel.this::prepareSession); }
            @Override public boolean onBeforePopup(CefBrowser cef, CefFrame frame, String url, String target) {
                if (Site.isHttps(url)) ui(() -> browser.loadURL(url));
                return true;
            }
        }, browser.getCefBrowser());
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override public void onLoadingStateChange(CefBrowser cef, boolean loading, boolean back, boolean forward) {
                String url = cef.getURL();
                ui(() -> {
                    if (Site.isHttps(url)) visibleUrl = url;
                    if (loading) status.setText("正在加载 LeetCode…");
                    else if (ready) saveSession();
                });
            }
            @Override public void onLoadError(CefBrowser cef, CefFrame frame, ErrorCode code, String text, String failedUrl) {
                if (frame.isMain() && code != ErrorCode.ERR_ABORTED)
                    ui(() -> status.setText("页面加载失败：" + code + "。可刷新或在浏览器打开。"));
            }
        }, browser.getCefBrowser());
        browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override public boolean onBeforeBrowse(CefBrowser cef, CefFrame frame, CefRequest request, boolean gesture, boolean redirect) {
                String url = request.getURL();
                if (!frame.isMain() || "about:blank".equals(url) || Site.isHttps(url)) return false;
                ui(() -> status.setText("此页面尝试打开非 HTTPS 链接，请使用系统浏览器完成该操作。"));
                return true;
            }
        }, browser.getCefBrowser());
        surface.removeAll(); surface.add(browser.getComponent(), BorderLayout.CENTER); surface.revalidate(); surface.repaint();
    }
    private void prepareSession() {
        if (disposed || browser == null) return;
        ready = false;
        int request = ++generation;
        status.setText("正在恢复登录会话…");
        SessionVault.getInstance().restore(site, browser.getJBCefCookieManager(), message -> ui(() -> {
            if (request != generation) return;
            ready = true; status.setText(message); timer.start();
            browser.loadURL(pendingUrl == null ? site.problemSet() : pendingUrl);
        }));
    }
    private void saveSession() {
        if (browser == null || !ready || disposed || savePending) return;
        savePending = true;
        int request = generation;
        SessionVault.getInstance().save(site, browser.getJBCefCookieManager(), message -> ui(() -> {
            savePending = false;
            if (request == generation) status.setText(message);
        }));
    }
    public void setSyncAction(Runnable action) { syncAction = action; }
    public boolean canSync(Site target) { return browser != null && ready && !disposed && site == target && onSite(target, visibleUrl); }
    private static boolean onSite(Site target, String url) {
        if (!Site.isHttps(url)) return false;
        URI uri = URI.create(url);
        return target.host.equalsIgnoreCase(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443);
    }
    public String fetchCatalog(Site target, String body, ProgressIndicator indicator) throws Exception {
        if (!canSync(target)) throw new java.io.IOException("请先打开对应站点的内嵌官网，并完成登录或网页验证。");
        String id = UUID.randomUUID().toString();
        CompletableFuture<String> result = new CompletableFuture<>(); requests.put(id, new Pending(target, result));
        var gson = new Gson();
        String script = "(async () => { const id = " + gson.toJson(id) + "; try { "
            + "if (location.origin !== " + gson.toJson(target.origin()) + ") throw new Error('origin');"
            + "const csrf = document.cookie.split('; ').find(c => c.startsWith('csrftoken='));"
            + "const headers = {'Content-Type':'application/json'}; if(csrf) headers['x-csrftoken'] = decodeURIComponent(csrf.substring(10));"
            + "const response = await fetch('/graphql/', {method:'POST', credentials:'same-origin', headers, body:"
            + gson.toJson(body) + ", signal: AbortSignal.timeout(25000)});"
            + "if(!response.ok) throw new Error('HTTP'); const data = await response.json();"
            + catalogQuery.inject("JSON.stringify({id, data})") + ";"
            + "} catch(e) {" + catalogQuery.inject("JSON.stringify({id, error:true})") + ";} })();";
        ui(() -> {
            if (!canSync(target)) result.completeExceptionally(new java.io.IOException("网页站点已切换，请重新同步。"));
            else browser.getCefBrowser().executeJavaScript(script, target.origin(), 0);
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                indicator.checkCanceled();
                try { return result.get(500, TimeUnit.MILLISECONDS); }
                catch (TimeoutException ignored) {}
            }
            throw new java.io.IOException("官网同步超时，请保持网页打开并完成验证后重试。");
        } finally { requests.remove(id); }
    }
    private void clearSession() {
        if (browser == null || !ready) return;
        ready = false; timer.stop();
        int request = ++generation;
        status.setText("正在清除会话…");
        browser.loadURL("about:blank");
        SessionVault.getInstance().clear(site, browser.getJBCefCookieManager(), message -> ui(() -> {
            if (request != generation) return;
            ready = true; status.setText(message); timer.start(); browser.loadURL(site.login());
        }));
    }
    private void ui(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(() -> { if (!disposed) runnable.run(); });
    }
    @Override public void dispose() {
        disposed = true; timer.stop();
        requests.values().forEach(p -> p.result().completeExceptionally(new java.io.IOException("浏览器已关闭"))); requests.clear();
    }
}
