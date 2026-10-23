package dev.leetcodebreak.analysis;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import dev.leetcodebreak.model.*;
import java.io.IOException;
import java.util.*;

public final class ProjectScanner {
    public Analysis scan(Project project, VirtualFile selected, ProgressIndicator indicator) {
        var files = new ArrayList<VirtualFile>();
        boolean[] limited = {false};
        if (selected != null) files.add(selected);
        else ReadAction.run(() -> {
            ProjectFileIndex index = ProjectFileIndex.getInstance(project);
            index.iterateContent(file -> {
                indicator.checkCanceled();
                if (!file.isDirectory() && ScanPolicy.supported(file.getPath())) {
                    if (files.size() >= ScanPolicy.MAX_FILES) { limited[0] = true; return false; }
                    files.add(file);
                }
                return true;
            }, file -> !file.is(com.intellij.openapi.vfs.VFileProperty.SYMLINK) && !ScanPolicy.excludedPath(relative(project, file))
                && !index.isExcluded(file) && !index.isUnderIgnored(file) && !index.isInGeneratedSources(file));
        });
        var evidence = new ArrayList<Evidence>();
        var analyzer = new SourceAnalyzer();
        int scanned = 0, skipped = 0, chars = 0;
        for (VirtualFile file : files) {
            indicator.checkCanceled();
            if (project.isDisposed()) break;
            indicator.setText2(relative(project, file));
            indicator.setFraction((scanned + skipped) / (double) Math.max(1, files.size()));
            String text = ReadAction.compute(() -> {
                if (!file.isValid() || file.isDirectory() || file.is(com.intellij.openapi.vfs.VFileProperty.SYMLINK) || !ScanPolicy.supported(file.getPath())
                    || file.getLength() > ScanPolicy.MAX_FILE_BYTES || file.getFileType().isBinary()) return null;
                var document = FileDocumentManager.getInstance().getCachedDocument(file);
                if (document != null) return document.getTextLength() <= ScanPolicy.MAX_FILE_BYTES ? document.getText() : null;
                try { return VfsUtilCore.loadText(file); }
                catch (IOException ignored) { return null; }
            });
            if (text == null) { skipped++; continue; }
            if (chars + text.length() > ScanPolicy.MAX_TOTAL_CHARS) { limited[0] = true; break; }
            chars += text.length();
            evidence.addAll(analyzer.analyze(file.getPath(), text));
            scanned++;
        }
        return new Analysis(evidence, scanned, skipped, limited[0], selected == null ? "当前项目" : selected.getName());
    }

    public static String relative(Project project, VirtualFile file) { return relative(project, file.getPath()); }
    public static String relative(Project project, String path) {
        String base = project.getBasePath();
        return base != null && path.startsWith(base + "/") ? path.substring(base.length() + 1) : path;
    }
}
