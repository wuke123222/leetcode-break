package dev.leetcodebreak.catalog;

import dev.leetcodebreak.model.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LeetCodeApiTest {
    // Small protocol fixtures, never shipped as a runtime problem catalog.
    static final String RESPONSE = """
        {"data":{"problemsetQuestionList":{"total":4087,"questions":[
          {"frontendQuestionId":"1","title":"Two Sum","translatedTitle":"两数之和","titleSlug":"two-sum",
           "difficulty":"Easy","status":"ac","isPaidOnly":false,
           "topicTags":[{"name":"Hash Table","slug":"hash-table","translatedName":"哈希表"}]}
        ]}}}
        """;
    @Test void parsesActualEndpointShapeWithTranslationsAndAcceptedStatus() throws Exception {
        var page = LeetCodeApi.parse(RESPONSE); var p = page.problems().getFirst();
        assertEquals(4087, page.total()); assertEquals(1, page.received());
        assertEquals("两数之和", p.title()); assertTrue(p.accepted()); assertFalse(p.paidOnly());
        assertEquals(Set.of(Topic.HASH), p.topics()); assertEquals("哈希表", p.tags().getFirst());
    }
    @Test void supportsNonNumericIdsAndInternationalTitles() throws Exception {
        var p = LeetCodeApi.parse(RESPONSE.replace("\"1\"", "\"LCR 001\"").replace("\"两数之和\"", "null")).problems().getFirst();
        assertEquals("LCR 001", p.id()); assertEquals("Two Sum", p.title());
    }
    @Test void doesNotReplaceErrorsWithSampleProblems() {
        assertThrows(LeetCodeApi.SchemaException.class, () -> LeetCodeApi.parse("{\"errors\":[{\"message\":\"field removed\"}]}"));
        assertThrows(IOException.class, () -> LeetCodeApi.parse("<html>verification required</html>"));
        assertThrows(IOException.class, () -> LeetCodeApi.parse("{\"data\":null}"));
        assertThrows(IOException.class, () -> LeetCodeApi.parse(RESPONSE.replace("two-sum", "../login")));
    }
    @Test void mapsRealTagsToProjectConcepts() {
        assertTrue(LeetCodeApi.topics(Set.of("design", "hash-table", "linked-list")).contains(Topic.CACHE));
        assertEquals(Set.of(Topic.SQL), LeetCodeApi.topics(Set.of("database")));
        assertEquals(Set.of(Topic.GRAPH), LeetCodeApi.topics(Set.of("graph-theory")));
    }
    @Test void sendsReadOnlyPaginatedQueries() {
        String query = LeetCodeApi.request(100, LeetCodeApi.Schema.QUESTION_LIST);
        assertTrue(query.contains("\"skip\":100")); assertFalse(query.contains("mutation"));
        assertTrue(query.contains("status")); assertTrue(query.contains("topicTags"));
    }
}
