package dev.leetcodebreak.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public final class OpenPracticeAction extends DumbAwareAction {
    @Override public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getProject() == null) return;
        var window = ToolWindowManager.getInstance(e.getProject()).getToolWindow("LeetCode Break");
        if (window != null) window.activate(null);
    }
    @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabledAndVisible(e.getProject() != null); }
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
}
