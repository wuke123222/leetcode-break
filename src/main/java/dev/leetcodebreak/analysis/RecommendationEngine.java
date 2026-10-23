package dev.leetcodebreak.analysis;

import dev.leetcodebreak.model.*;
import java.util.*;

public final class RecommendationEngine {
    public record Recommendation(Problem problem, int score, List<Evidence> evidence) {}
    public record Filter(Problem.Difficulty difficulty, int maxMinutes, String query, boolean hideDone) {}

    public List<Recommendation> recommend(List<Problem> catalog, Analysis analysis, Filter filter) {
        String query = filter.query() == null ? "" : filter.query().strip().toLowerCase(Locale.ROOT);
        return catalog.stream()
            .filter(p -> filter.difficulty() == null || p.difficulty() == filter.difficulty())
            .filter(p -> filter.maxMinutes() <= 0 || p.minutes() <= filter.maxMinutes())
            .filter(p -> !filter.hideDone() || !p.accepted())
            .filter(p -> query.isEmpty() || (p.id() + " " + p.title() + " " + p.slug() + " " + p.topics() + " " + p.tags()).toLowerCase(Locale.ROOT).contains(query))
            .map(p -> {
                List<Evidence> evidence = analysis.evidence().stream().filter(e -> p.topics().contains(e.topic())).toList();
                // Each distinct topic+file votes once; a giant file cannot dominate the ranking.
                int score = evidence.stream().map(Evidence::topic).distinct().mapToInt(t -> {
                    long files = evidence.stream().filter(e -> e.topic() == t).map(Evidence::path).distinct().count();
                    return 10 + (int) Math.min(files, 5) * 2;
                }).sum();
                return new Recommendation(p, score, evidence);
            })
            .sorted(Comparator.comparingInt(Recommendation::score).reversed()
                .thenComparingInt(r -> r.problem().minutes()).thenComparing(r -> r.problem().id(), RecommendationEngine::compareIds))
            .toList();
    }
    private static int compareIds(String a, String b) {
        try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
        catch (NumberFormatException e) { return a.compareTo(b); }
    }
}
