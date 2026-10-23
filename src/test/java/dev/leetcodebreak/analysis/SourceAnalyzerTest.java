package dev.leetcodebreak.analysis;

import dev.leetcodebreak.model.Topic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceAnalyzerTest {
    private final SourceAnalyzer analyzer = new SourceAnalyzer();
    @Test void findsActionableJavaEvidenceAtOriginalLines() {
        var result = analyzer.analyze("src/Cache.java", "// HashMap fake\nclass Cache {\n  LinkedHashMap<String, Item> entries;\n  PriorityQueue<Job> jobs;\n}");
        assertTrue(result.stream().anyMatch(e -> e.topic() == Topic.CACHE && e.line() == 3));
        assertTrue(result.stream().anyMatch(e -> e.topic() == Topic.HEAP && e.line() == 4));
        assertFalse(result.stream().anyMatch(e -> e.line() == 1));
    }
    @Test void ignoresCommentsAndStringLiterals() {
        assertTrue(analyzer.analyze("a.java", "/* HashMap\n PriorityQueue */\nString x = \"Semaphore HashMap\";").isEmpty());
        assertTrue(analyzer.analyze("a.py", "# heapq\n\"\"\"HashMap\nSemaphore\"\"\"\nx = 'PriorityQueue'").isEmpty());
    }
    @Test void findsPythonGoAndSqlSignals() {
        assertTrue(analyzer.analyze("a.py", "from collections import deque\nheapq.heappush(heap, x)").stream().anyMatch(e -> e.topic() == Topic.HEAP));
        assertTrue(analyzer.analyze("a.go", "var m map[string]int").stream().anyMatch(e -> e.topic() == Topic.HASH));
        assertTrue(analyzer.analyze("a.sql", "-- SELECT x FROM y\nSELECT department, COUNT(*) FROM employee GROUP BY department;").stream().anyMatch(e -> e.topic() == Topic.SQL && e.line() == 2));
    }
    @Test void capsEvidencePerTopicPerFile() {
        assertEquals(3, analyzer.analyze("a.java", "HashMap m;\n".repeat(1000)).stream().filter(e -> e.topic() == Topic.HASH).count());
    }
    @Test void excludesDependenciesGeneratedFilesAndSecrets() {
        for (String path : new String[]{"src/.env", "node_modules/a.js", "target/A.java", ".git/data.py", "build/generated/A.java", "keys.pem", "vendor/x.go"}) assertFalse(ScanPolicy.supported(path), path);
        assertTrue(ScanPolicy.supported("src/main/Cache.java"));
        assertTrue(ScanPolicy.supported("db/query.sql"));
    }
}
