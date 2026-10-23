package dev.leetcodebreak.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import dev.leetcodebreak.analysis.*;
import dev.leetcodebreak.analysis.RecommendationEngine.*;
import dev.leetcodebreak.model.*;
import dev.leetcodebreak.settings.PracticeState;
import dev.leetcodebreak.catalog.CatalogLoader;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public final class PracticePanel extends JBPanel<PracticePanel> implements Disposable {
    private final Project project;
    private final PracticeState preferences = PracticeState.getInstance();
    private Site currentSite = preferences.site();
    private final BrowserPanel browser;
    private final JTabbedPane tabs = new JBTabbedPane();
    private final DefaultListModel<Recommendation> items = new DefaultListModel<>();
    private final JBList<Recommendation> list = new JBList<>(items);
    private final JPanel detail = new JPanel();
    private final JBLabel summary = new JBLabel("先分析项目，发现代码里值得练习的算法");
    private final JBLabel count = new JBLabel();
    private final JTextField query = new JBTextField();
    private final JComboBox<String> difficulty = new JComboBox<>(new String[]{"全部难度", "简单", "中等", "困难"});
    private final JComboBox<String> minutes = new JComboBox<>(new String[]{"不限用时", "10 分钟", "20 分钟", "30 分钟", "45 分钟"});
    private static final int[] BUDGETS = {0, 10, 20, 30, 45};
    private final JCheckBox hideDone = new JCheckBox("隐藏官网已通过", true);
    private final JButton scanProject = new JButton("分析项目");
    private final JButton scanFile = new JButton("分析当前文件");
    private final JButton cancel = new JButton("取消分析");
    private final JButton sync = new JButton("同步官方题库");
    private final JButton syncBrowser = new JButton("从内嵌官网同步");
    private final JButton cancelSync = new JButton("取消同步");
    private final JBLabel catalogStatus = new JBLabel("尚未同步题库；所有题目将从 LeetCode 获取");
    private List<Problem> catalog = List.of();
    private volatile ProgressIndicator catalogProgress;
    private int catalogGeneration;
    private boolean syncing;
    private Analysis analysis = Analysis.empty();
    private volatile boolean disposed;
    private volatile ProgressIndicator progress;

    public PracticePanel(Project project) {
        super(new BorderLayout()); this.project = project;
        browser = new BrowserPanel(currentSite); Disposer.register(this, browser);
        browser.setSyncAction(() -> syncCatalog(true));
        setBorder(JBUI.Borders.empty(12));
        add(header(), BorderLayout.NORTH);
        tabs.addTab("项目推荐", recommendations()); tabs.addTab("LeetCode", browser);
        add(tabs, BorderLayout.CENTER);
        var footer = new JBLabel("源码不上传 · 在官网做题 · 登录后同步官方通过状态");
        footer.setForeground(JBColor.GRAY); footer.setBorder(JBUI.Borders.emptyTop(10)); add(footer, BorderLayout.SOUTH);
        refresh();
    }

    private JComponent header() {
        var panel = new JPanel(new BorderLayout(16, 6)); panel.setBorder(JBUI.Borders.emptyBottom(16));
        var title = new JBLabel("LeetCode Break"); title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        panel.add(title, BorderLayout.NORTH);
        var sub = new JBLabel("把项目里的知识点，变成下一道练习题。"); sub.setForeground(JBColor.GRAY); panel.add(sub, BorderLayout.CENTER);
        var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        var site = new JComboBox<>(Site.values()); site.setSelectedItem(currentSite);
        site.addActionListener(e -> {
            Site target = (Site) site.getSelectedItem();
            catalogGeneration++;
            if (catalogProgress != null) catalogProgress.cancel();
            currentSite = target; preferences.setSite(target); browser.switchSite(target);
            catalog = List.of(); catalogStatus.setText("已切换站点，请同步该站点的官方题库。"); refresh();
        });
        var practice = new JButton("打开 LeetCode"); practice.addActionListener(e -> open(currentSite.problemSet()));
        row.add(site); row.add(practice); panel.add(row, BorderLayout.SOUTH); return panel;
    }

    private JComponent recommendations() {
        var panel = new JPanel(new BorderLayout(0, 12)); panel.setBorder(JBUI.Borders.emptyTop(12));
        var top = new JPanel(); top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scanProject.addActionListener(e -> scan(false)); scanFile.addActionListener(e -> scan(true));
        cancel.setEnabled(false); cancel.addActionListener(e -> { if (progress != null) progress.cancel(); });
        actions.add(scanProject); actions.add(scanFile); actions.add(cancel); top.add(actions);
        summary.setBorder(JBUI.Borders.empty(10, 6)); top.add(summary);
        var syncRow = new JPanel(new GridLayout(0, 2, 6, 4));
        sync.addActionListener(e -> syncCatalog(false)); syncBrowser.addActionListener(e -> syncCatalog(true));
        cancelSync.setEnabled(false); cancelSync.addActionListener(e -> { if (catalogProgress != null) catalogProgress.cancel(); });
        syncRow.add(sync); syncRow.add(syncBrowser); syncRow.add(cancelSync); top.add(syncRow);
        catalogStatus.setBorder(JBUI.Borders.empty(8, 6)); top.add(catalogStatus);
        query.setToolTipText("搜索题号、名称、英文标识或知识点");
        query.putClientProperty("JTextField.placeholderText", "搜索题号、题目或知识点…");
        query.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); top.add(query);
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        var filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        for (int i = 0; i < BUDGETS.length; i++) if (preferences.minutes() == BUDGETS[i]) minutes.setSelectedIndex(i);
        difficulty.addActionListener(e -> refresh());
        minutes.addActionListener(e -> { preferences.setMinutes(BUDGETS[minutes.getSelectedIndex()]); refresh(); });
        hideDone.addActionListener(e -> refresh());
        filters.add(difficulty); filters.add(minutes); filters.add(hideDone); top.add(filters);
        count.setBorder(JBUI.Borders.empty(0, 6, 8, 0)); top.add(count);
        panel.add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setCellRenderer(new ProblemRenderer());
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showDetail(list.getSelectedValue()); });
        list.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) openSelected(); } });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openProblem");
        list.getActionMap().put("openProblem", new AbstractAction() { public void actionPerformed(ActionEvent e) { openSelected(); } });
        list.getEmptyText().setText("请同步官方题库，或调整筛选条件");
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS)); detail.setBorder(JBUI.Borders.empty(12));
        OnePixelSplitter split = new OnePixelSplitter(true, 0.55f);
        split.setFirstComponent(new JBScrollPane(list)); split.setSecondComponent(new JBScrollPane(detail));
        panel.add(split, BorderLayout.CENTER); return panel;
    }

    private void refresh() {
        Recommendation selected = list.getSelectedValue();
        Problem.Difficulty level = difficulty.getSelectedIndex() == 0 ? null : Problem.Difficulty.values()[difficulty.getSelectedIndex() - 1];
        List<Recommendation> recommendations = new RecommendationEngine().recommend(catalog, analysis,
            new Filter(level, BUDGETS[minutes.getSelectedIndex()], query.getText(), hideDone.isSelected()));
        items.clear(); recommendations.forEach(items::addElement);
        long matched = recommendations.stream().filter(r -> r.score() > 0).count();
        count.setText(recommendations.size() + " 道题 · " + matched + " 道匹配项目 · 官网已通过 " + catalog.stream().filter(Problem::accepted).count() + " 道（本次同步）");
        int index = 0;
        if (selected != null) for (int i = 0; i < items.size(); i++) if (items.get(i).problem().slug().equals(selected.problem().slug())) index = i;
        if (!items.isEmpty()) list.setSelectedIndex(index); else showDetail(null);
    }

    private void showDetail(Recommendation recommendation) {
        detail.removeAll();
        if (recommendation == null) { detail.add(new JBLabel("选择一道题查看推荐依据")); finishDetail(); return; }
        Problem p = recommendation.problem();
        var title = new JBLabel("<html>" + p.id() + ". " + escape(p.title()) + "</html>");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f)); addDetail(title);
        addDetail(new JBLabel(p.difficulty().label + "  ·  参考用时 " + p.minutes() + " 分钟（按难度估算）" + (p.paidOnly() ? " · 会员题" : "")));
        addDetail(new JBLabel("<html>官网标签：" + escape(String.join(" / ", p.tags())) + "</html>"));
        if (p.accepted()) addDetail(new JBLabel("✓ LeetCode 已通过（最近同步）"));
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        JButton start = new JButton("开始练习 ↗"); start.addActionListener(e -> open(currentSite.problem(p.slug()))); buttons.add(start);
        addDetail(buttons);
        if (recommendation.evidence().isEmpty()) {
            addDetail(new JBLabel("<html>通用练习：尚无匹配的项目证据。点击“分析项目”获取相关推荐。</html>"));
        } else {
            addDetail(new JBLabel("为什么推荐这道题"));
            recommendation.evidence().stream().map(Evidence::topic).distinct().forEach(t ->
                addDetail(new JBLabel("<html>• " + escape(t.description) + "</html>")));
            addDetail(new JBLabel("项目中的线索（点击定位）"));
            recommendation.evidence().stream().limit(6).forEach(e -> {
                String label = ProjectScanner.relative(project, e.path()) + ":" + e.line() + " · " + e.signal();
                JButton link = new JButton(label); link.setHorizontalAlignment(SwingConstants.LEFT); link.setToolTipText(label);
                link.addActionListener(event -> {
                    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(e.path());
                    if (file != null && file.isValid()) new OpenFileDescriptor(project, file, e.line() - 1, 0).navigate(true);
                    else summary.setText("源文件已移动或删除，请重新分析项目。");
                }); addDetail(link);
            });
            var hint = new JBLabel("<html>基于代码标识符的启发式匹配；用时为练习估计。源码变化后请重新分析。</html>");
            hint.setForeground(JBColor.GRAY); addDetail(hint);
        }
        finishDetail();
    }
    private void addDetail(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT); detail.add(component); detail.add(Box.createVerticalStrut(8));
    }
    private void finishDetail() { detail.revalidate(); detail.repaint(); }
    private void openSelected() { Recommendation r = list.getSelectedValue(); if (r != null) open(currentSite.problem(r.problem().slug())); }
    private void open(String url) { tabs.setSelectedComponent(browser); browser.open(currentSite, url); }

    private void scan(boolean currentOnly) {
        VirtualFile selected = currentOnly ? FileEditorManager.getInstance(project).getSelectedFiles().length == 0 ? null :
            FileEditorManager.getInstance(project).getSelectedFiles()[0] : null;
        if (currentOnly && selected == null) { summary.setText("请先在编辑器打开一个源文件。"); return; }
        setScanning(true); summary.setText("正在本地分析…");
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "LeetCode Break：提取项目知识点", true) {
            private Analysis result;
            @Override public void run(@NotNull ProgressIndicator indicator) {
                progress = indicator; indicator.setIndeterminate(false);
                if (disposed) { indicator.cancel(); return; }
                result = new ProjectScanner().scan(project, selected, indicator);
            }
            @Override public void onSuccess() {
                if (disposed || result == null) return;
                analysis = result;
                long topics = result.evidence().stream().map(Evidence::topic).distinct().count();
                summary.setText(result.scope() + " · " + result.scannedFiles() + " 个文件 · " + topics + " 个知识点"
                    + (result.skippedFiles() > 0 ? " · 跳过 " + result.skippedFiles() : "") + (result.limited() ? " · 已达扫描上限" : ""));
                refresh();
            }
            @Override public void onCancel() { if (!disposed) summary.setText("分析已取消，保留上次结果。"); }
            @Override public void onThrowable(@NotNull Throwable error) { if (!disposed) summary.setText("分析失败，请检查项目文件是否可读后重试。"); }
            @Override public void onFinished() { progress = null; if (!disposed) setScanning(false); }
        });
    }
    private void setScanning(boolean value) { scanProject.setEnabled(!value); scanFile.setEnabled(!value); cancel.setEnabled(value); }
    private void syncCatalog(boolean fromBrowser) {
        if (syncing) return;
        Site target = currentSite;
        if (fromBrowser && !browser.canSync(target)) {
            catalogStatus.setText("请在内嵌官网完成登录/验证，然后点击网页上方“同步官方题库”。");
            open(target.problemSet()); return;
        }
        int request = ++catalogGeneration;
        syncing = true; sync.setEnabled(false); syncBrowser.setEnabled(false); cancelSync.setEnabled(true);
        catalogStatus.setText("正在从 " + target.host + " 分页获取真实题库…");
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "LeetCode Break：同步官方题库", true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                catalogProgress = indicator; indicator.setIndeterminate(false);
                if (disposed || request != catalogGeneration) { indicator.cancel(); return; }
                try {
                    CatalogLoader.Transport transport = fromBrowser ? body -> browser.fetchCatalog(target, body, indicator) : CatalogLoader.publicTransport(target);
                    CatalogLoader.load(target, transport, indicator, update -> ApplicationManager.getApplication().invokeLater(() -> {
                        if (disposed || request != catalogGeneration) return;
                        catalog = update.problems();
                        catalogStatus.setText(target.host + " · " + catalog.size() + " / " + update.total() + " 道 · "
                            + (update.complete() ? "同步完成" : "正在同步") + (fromBrowser ? " · 官网会话" : " · 公开题库")); refresh();
                    }));
                } catch (ProcessCanceledException e) { throw e; }
                catch (Exception e) { throw new RuntimeException("题库同步失败", e); }
            }
            @Override public void onCancel() { if (!disposed && request == catalogGeneration) catalogStatus.setText("同步已取消，保留已获取的官方题目。"); }
            @Override public void onThrowable(@NotNull Throwable error) {
                if (disposed || request != catalogGeneration) return;
                String reason = "网络或网页验证失败";
                for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                    if (cause instanceof com.intellij.util.io.HttpRequests.HttpStatusException status) reason = "官网返回 HTTP " + status.getStatusCode();
                    else if (cause instanceof dev.leetcodebreak.catalog.LeetCodeApi.SchemaException) reason = "官网接口字段已变化";
                    else if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.util.concurrent.TimeoutException) reason = "连接超时";
                }
                catalogStatus.setText(reason + "；已保留现有题目，可从内嵌官网重试。");
                catalogStatus.setToolTipText(catalogStatus.getText());
            }
            @Override public void onFinished() {
                catalogProgress = null; syncing = false;
                if (!disposed) { sync.setEnabled(true); syncBrowser.setEnabled(true); cancelSync.setEnabled(false); }
            }
        });
    }
    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    @Override public void dispose() { disposed = true; if (progress != null) progress.cancel(); if (catalogProgress != null) catalogProgress.cancel(); }

    private final class ProblemRenderer extends JPanel implements ListCellRenderer<Recommendation> {
        private final JLabel title = new JLabel();
        private final JLabel subtitle = new JLabel();
        ProblemRenderer() {
            super(new BorderLayout(0, 5)); setBorder(JBUI.Borders.empty(10, 12));
            title.setFont(title.getFont().deriveFont(Font.BOLD)); add(title, BorderLayout.NORTH); add(subtitle, BorderLayout.CENTER);
        }
        @Override public Component getListCellRendererComponent(JList<? extends Recommendation> list, Recommendation value, int index, boolean selected, boolean focus) {
            Problem p = value.problem();
            title.setText((p.accepted() ? "✓ " : "") + p.id() + ". " + p.title());
            subtitle.setText(p.difficulty().label + " · 约 " + p.minutes() + " 分钟" + (p.paidOnly() ? " · 会员" : "") + " · " + (value.score() > 0 ? "项目相关 · " + value.evidence().getFirst().topic().label : "通用练习"));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            title.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            subtitle.setForeground(selected ? list.getSelectionForeground() : JBColor.GRAY);
            return this;
        }
    }
}
