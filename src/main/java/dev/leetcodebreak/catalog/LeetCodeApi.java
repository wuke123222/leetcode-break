package dev.leetcodebreak.catalog;

import com.google.gson.*;
import dev.leetcodebreak.model.*;
import java.io.IOException;
import java.util.*;

/** Read-only queries to LeetCode's website endpoint. No statement/submission implementation. */
public final class LeetCodeApi {
    public static final int PAGE_SIZE = 100;
    public enum Schema { QUESTION_LIST, CN_PROBLEMSET }
    public record Page(int total, int received, List<Problem> problems) {}

    public static String request(int skip, Schema schema) {
        String fields = "title titleSlug translatedTitle difficulty status topicTags { name slug translatedName }";
        String selection = schema == Schema.QUESTION_LIST
            ? "problemsetQuestionList: questionList(categorySlug: \"\", limit: $limit, skip: $skip, filters: {}) { total: totalNum questions: data { frontendQuestionId: questionFrontendId isPaidOnly " + fields + " } }"
            : "problemsetQuestionList(categorySlug: \"\", limit: $limit, skip: $skip, filters: {}) { total questions { frontendQuestionId isPaidOnly: paidOnly " + fields + " } }";
        var body = new JsonObject();
        body.addProperty("query", "query LeetCodeBreakCatalog($skip: Int, $limit: Int) { " + selection + " }");
        var variables = new JsonObject(); variables.addProperty("skip", skip); variables.addProperty("limit", PAGE_SIZE);
        body.add("variables", variables); return body.toString();
    }

    public static Page parse(String json) throws IOException {
        if (json.length() > 4_000_000) throw new IOException("题库响应过大");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("errors")) throw new SchemaException();
            JsonObject result = root.getAsJsonObject("data").getAsJsonObject("problemsetQuestionList");
            int total = result.get("total").getAsInt();
            if (total < 0 || total > 100_000) throw new IOException("题库总数异常");
            var rows = result.getAsJsonArray("questions");
            var problems = new ArrayList<Problem>();
            for (JsonElement row : rows) {
                JsonObject q = row.getAsJsonObject();
                String id = string(q, "frontendQuestionId"), slug = string(q, "titleSlug");
                if (id.isBlank() || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new IOException("题目字段格式已变化");
                String title = string(q, "translatedTitle");
                if (title.isBlank()) title = string(q, "title");
                Problem.Difficulty difficulty = Problem.Difficulty.valueOf(string(q, "difficulty").toUpperCase(Locale.ROOT));
                var tags = new ArrayList<String>(); var slugs = new HashSet<String>();
                for (JsonElement tag : q.getAsJsonArray("topicTags")) {
                    JsonObject t = tag.getAsJsonObject(); slugs.add(string(t, "slug"));
                    String label = string(t, "translatedName"); tags.add(label.isBlank() ? string(t, "name") : label);
                }
                int estimate = switch (difficulty) { case EASY -> 10; case MEDIUM -> 25; case HARD -> 45; };
                problems.add(new Problem(id, title, slug, difficulty, estimate, topics(slugs), tags,
                    q.get("isPaidOnly").getAsBoolean(), string(q, "status")));
            }
            return new Page(total, rows.size(), List.copyOf(problems));
        } catch (IOException e) { throw e; }
        catch (RuntimeException e) { throw new IOException("LeetCode 返回了无法识别的题库数据，请在内嵌网页完成验证后重试。"); }
    }

    private static String string(JsonObject object, String key) {
        return !object.has(key) || object.get(key).isJsonNull() ? "" : object.get(key).getAsString();
    }
    public static Set<Topic> topics(Set<String> tags) {
        var topics = EnumSet.noneOf(Topic.class);
        Map<String, Topic> mapping = Map.ofEntries(
            Map.entry("hash-table", Topic.HASH), Map.entry("heap-priority-queue", Topic.HEAP),
            Map.entry("stack", Topic.STACK), Map.entry("monotonic-stack", Topic.STACK),
            Map.entry("queue", Topic.QUEUE), Map.entry("monotonic-queue", Topic.QUEUE),
            Map.entry("tree", Topic.TREE), Map.entry("binary-tree", Topic.TREE), Map.entry("binary-search-tree", Topic.TREE),
            Map.entry("graph", Topic.GRAPH), Map.entry("graph-theory", Topic.GRAPH), Map.entry("topological-sort", Topic.GRAPH), Map.entry("union-find", Topic.GRAPH),
            Map.entry("trie", Topic.TRIE), Map.entry("binary-search", Topic.SEARCH), Map.entry("sorting", Topic.SORT),
            Map.entry("sliding-window", Topic.WINDOW), Map.entry("dynamic-programming", Topic.DP), Map.entry("memoization", Topic.DP),
            Map.entry("linked-list", Topic.LINKED), Map.entry("database", Topic.SQL), Map.entry("concurrency", Topic.CONCURRENCY));
        tags.forEach(t -> { if (mapping.containsKey(t)) topics.add(mapping.get(t)); });
        if (tags.contains("design") && tags.contains("hash-table") && tags.contains("linked-list")) topics.add(Topic.CACHE);
        return topics;
    }
    public static final class SchemaException extends IOException {
        public SchemaException() { super("LeetCode GraphQL 接口字段不兼容，请稍后重试或直接使用官网题库。"); }
    }
}
