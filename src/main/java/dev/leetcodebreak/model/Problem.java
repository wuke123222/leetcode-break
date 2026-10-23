package dev.leetcodebreak.model;

import java.util.Set;

public record Problem(String id, String title, String slug, Difficulty difficulty, int minutes, Set<Topic> topics,
                      java.util.List<String> tags, boolean paidOnly, String status) {
    public Problem { topics = Set.copyOf(topics); tags = java.util.List.copyOf(tags); }
    public boolean accepted() { return "ac".equalsIgnoreCase(status) || "SOLVED".equalsIgnoreCase(status); }
    public enum Difficulty {
        EASY("简单"), MEDIUM("中等"), HARD("困难");
        public final String label;
        Difficulty(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
}
