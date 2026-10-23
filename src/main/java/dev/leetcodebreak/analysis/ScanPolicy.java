package dev.leetcodebreak.analysis;

import java.util.Locale;
import java.util.Set;

public final class ScanPolicy {
    public static final int MAX_FILES = 1500;
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_TOTAL_CHARS = 12 * 1024 * 1024;
    private static final Set<String> EXCLUDED = Set.of(".git", ".idea", ".gradle", ".next", ".venv", "venv", "node_modules", "vendor", "build", "dist", "out", "target", "coverage", "__pycache__", "generated", ".intellijPlatform");
    private static final Set<String> EXTENSIONS = Set.of("java", "kt", "kts", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "hpp", "cs", "swift", "scala", "sql", "rb", "php");
    private ScanPolicy() {}
    public static boolean excludedPath(String path) {
        for (String part : path.replace('\\', '/').split("/")) if (EXCLUDED.contains(part)) return true;
        return false;
    }
    public static boolean supported(String path) {
        int dot = path.lastIndexOf('.');
        return !excludedPath(path) && dot >= 0 && EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
