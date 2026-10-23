package dev.leetcodebreak.analysis;

import dev.leetcodebreak.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationEngineTest {
    private Problem p(String id, String slug, Set<Topic> topics, String status) {
        return new Problem(id, slug, slug, Problem.Difficulty.MEDIUM, 25, topics, List.of(), false, status);
    }
    @Test void usesOnlySuppliedOfficialResultsAndRanksRelevantTopics() {
        var unrelated = p("1", "unrelated", Set.of(Topic.TREE), "");
        var relevant = p("146", "lru-cache", Set.of(Topic.CACHE, Topic.HASH), "");
        var analysis = new Analysis(List.of(new Evidence(Topic.CACHE, "Cache.java", 4, "cache")), 1, 0, false, "project");
        var result = new RecommendationEngine().recommend(List.of(unrelated, relevant), analysis, new RecommendationEngine.Filter(null, 0, "", false));
        assertEquals(List.of(relevant, unrelated), result.stream().map(RecommendationEngine.Recommendation::problem).toList());
        assertEquals(1, result.getFirst().evidence().size());
        assertEquals(0, result.getLast().score());
    }
    @Test void respectsOfficialAcceptedStatusAndBudget() {
        var accepted = p("1", "solved", Set.of(), "ac");
        var open = p("2", "remaining", Set.of(), "");
        var engine = new RecommendationEngine();
        assertEquals(List.of(open), engine.recommend(List.of(accepted, open), Analysis.empty(), new RecommendationEngine.Filter(null, 30, "", true)).stream().map(RecommendationEngine.Recommendation::problem).toList());
        assertTrue(engine.recommend(List.of(open), Analysis.empty(), new RecommendationEngine.Filter(null, 10, "", false)).isEmpty());
        assertTrue(engine.recommend(List.of(), Analysis.empty(), new RecommendationEngine.Filter(null, 0, "", false)).isEmpty());
    }
    @Test void repeatedLinesDoNotInflateRelevance() {
        var p = p("1", "hash", Set.of(Topic.HASH), "");
        var one = new Evidence(Topic.HASH, "a.java", 1, "HashMap");
        var repeated = new Evidence(Topic.HASH, "a.java", 2, "HashMap");
        var engine = new RecommendationEngine();
        var filter = new RecommendationEngine.Filter(null, 0, "", false);
        assertEquals(engine.recommend(List.of(p), new Analysis(List.of(one), 1, 0, false, ""), filter).getFirst().score(),
            engine.recommend(List.of(p), new Analysis(List.of(one, repeated), 1, 0, false, ""), filter).getFirst().score());
    }
}
