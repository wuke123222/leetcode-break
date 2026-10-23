package dev.leetcodebreak.model;

import java.util.List;

public record Analysis(List<Evidence> evidence, int scannedFiles, int skippedFiles, boolean limited, String scope) {
    public Analysis { evidence = List.copyOf(evidence); }
    public static Analysis empty() { return new Analysis(List.of(), 0, 0, false, "尚未分析项目"); }
}
