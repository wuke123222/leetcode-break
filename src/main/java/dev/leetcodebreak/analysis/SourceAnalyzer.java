package dev.leetcodebreak.analysis;

import dev.leetcodebreak.model.Evidence;
import dev.leetcodebreak.model.Topic;
import java.util.*;
import java.util.regex.Pattern;
import static dev.leetcodebreak.model.Topic.*;

/** Bounded, language-independent lexical signals; deliberately not a semantic/AI claim. */
public final class SourceAnalyzer {
    private record Rule(Topic topic, Pattern pattern, String signal) {}
    private static Rule r(Topic topic, String pattern, String signal) {
        return new Rule(topic, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE), signal);
    }
    private static final List<Rule> RULES = List.of(
        r(HASH, "\\b(HashMap|HashSet|ConcurrentHashMap|Dictionary|unordered_map|unordered_set|Counter|defaultdict)\\b|\\bnew\\s+(Map|Set)\\s*[(<]|\\bmap\\s*\\[", "键值映射 / 去重集合"),
        r(CACHE, "\\b(LRUCache|LFUCache|LinkedHashMap|Caffeine|CacheBuilder|lru_cache|Cacheable|CacheEvict)\\b|\\b(maximumSize|expireAfterWrite|removeEldestEntry)\\s*\\(", "缓存与淘汰策略"),
        r(HEAP, "\\b(PriorityQueue|PriorityBlockingQueue|heapq|heappush|heappop|priority_queue)\\b|\\bheap\\.(Push|Pop|Init)\\b", "堆 / 优先队列 API"),
        r(STACK, "\\b(Stack|ArrayDeque)\\b|\\b(push|pop)\\s*\\(", "栈操作 / 双端结构"),
        r(QUEUE, "\\b(Queue|Deque|ArrayDeque|BlockingQueue|LinkedBlockingQueue|deque)\\b|\\b(offer|poll|popleft)\\s*\\(", "队列与任务调度"),
        r(TREE, "\\b(TreeNode|BinaryTree|TreeMap|TreeSet|NavigableMap|NavigableSet)\\b", "树节点 / 有序树容器"),
        r(GRAPH, "\\b(Graph|DirectedGraph|adjacencyList|adjacency_list|inDegree|in_degree|topologicalSort|topological_sort|dijkstra|UnionFind)\\b", "图结构 / 依赖拓扑"),
        r(TRIE, "\\b(Trie|TrieNode|PrefixTree|prefix_tree|RadixTree)\\b", "前缀检索结构"),
        r(SEARCH, "\\b(binarySearch|binary_search|bisect_left|bisect_right|lower_bound|upper_bound|SearchInts)\\b", "二分查找 API"),
        r(SORT, "\\b(sort|sorted|sortBy|sortWith|Comparator|TreeSet)\\s*[(<.]|\\bORDER\\s+BY\\b", "排序 / 有序数据"),
        r(WINDOW, "\\b(slidingWindow|sliding_window|windowSize|window_size|RateLimiter|SlidingWindowRateLimiter)\\b", "窗口 / 限流状态"),
        r(DP, "\\b(memo|memoize|memoized|memoization|dp)\\s*[\\[(=:.]|\\b(lru_cache|cache)\\s*\\(", "记忆化 / 动态规划状态"),
        r(LINKED, "\\b(LinkedList|ListNode|DoublyLinkedList|LinkedHashMap|forward_list)\\b", "链式节点结构"),
        r(SQL, "\\b(SELECT\\b.{0,180}\\bFROM|GROUP\\s+BY|LEFT\\s+JOIN|INNER\\s+JOIN|DENSE_RANK|ROW_NUMBER)\\b", "关系查询 / 聚合 / 窗口函数"),
        r(CONCURRENCY, "\\b(synchronized|Semaphore|ReentrantLock|CountDownLatch|CyclicBarrier|Mutex|WaitGroup|Condition|LockSupport)\\b", "锁与并发协调")
    );

    public List<Evidence> analyze(String path, String source) {
        String clean = stripCommentsAndStrings(source, path);
        String[] lines = clean.split("\\R", -1);
        var found = new ArrayList<Evidence>();
        var counts = new EnumMap<Topic, Integer>(Topic.class);
        for (int i = 0; i < lines.length; i++) {
            for (Rule rule : RULES) {
                if (counts.getOrDefault(rule.topic, 0) < 3 && rule.pattern.matcher(lines[i]).find()) {
                    found.add(new Evidence(rule.topic, path, i + 1, rule.signal));
                    counts.merge(rule.topic, 1, Integer::sum);
                }
            }
        }
        return List.copyOf(found);
    }

    // Preserve line numbers while ignoring comments and ordinary string literals.
    // SQL strings in application code are intentionally not inspected; .sql files are supported.
    static String stripCommentsAndStrings(String s, String path) {
        boolean python = path.endsWith(".py");
        boolean sql = path.endsWith(".sql");
        var out = new StringBuilder(s.length());
        for (int i = 0; i < s.length();) {
            char c = s.charAt(i);
            if ((python && c == '#') || (sql && s.startsWith("--", i)) || (!python && !sql && s.startsWith("//", i))) {
                while (i < s.length() && s.charAt(i) != '\n') { out.append(' '); i++; }
            } else if (!python && s.startsWith("/*", i)) {
                out.append("  "); i += 2;
                while (i < s.length() && !s.startsWith("*/", i)) { out.append(s.charAt(i++) == '\n' ? '\n' : ' '); }
                if (i < s.length()) { out.append("  "); i += 2; }
            } else if (c == '\'' || c == '"' || c == '`') {
                String quote = python && s.startsWith(String.valueOf(c).repeat(3), i) ? String.valueOf(c).repeat(3) : String.valueOf(c);
                out.append(" ".repeat(quote.length())); i += quote.length();
                while (i < s.length()) {
                    if (s.startsWith(quote, i)) { out.append(" ".repeat(quote.length())); i += quote.length(); break; }
                    char next = s.charAt(i++);
                    out.append(next == '\n' ? '\n' : ' ');
                    if (next == '\\' && i < s.length()) out.append(s.charAt(i++) == '\n' ? '\n' : ' ');
                }
            } else { out.append(c); i++; }
        }
        return out.toString();
    }
}
