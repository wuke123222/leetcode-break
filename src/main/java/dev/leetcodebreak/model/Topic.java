package dev.leetcodebreak.model;

public enum Topic {
    HASH("哈希表", "用键值索引、去重与计数减少重复查找"),
    CACHE("缓存设计", "用哈希表与链表实现有界缓存和淘汰策略"),
    HEAP("堆 / 优先队列", "从任务队列、排序数据中高效选择 Top K"),
    STACK("栈", "用后进先出处理嵌套结构与表达式"),
    QUEUE("队列", "用先进先出管理任务与层序遍历"),
    TREE("树", "理解层级数据、递归遍历与搜索树"),
    GRAPH("图", "将依赖关系建模为图，练习遍历与拓扑排序"),
    TRIE("前缀树", "通过公共前缀组织检索与自动补全"),
    SEARCH("二分查找", "在有序数据或单调答案空间中缩小搜索范围"),
    SORT("排序", "用排序组织区间、事件与有序数据"),
    WINDOW("滑动窗口", "维护时间窗口、连续区间和去重状态"),
    DP("动态规划", "通过状态转移与记忆化复用子问题"),
    LINKED("链表", "处理节点链接、指针更新和双向结构"),
    SQL("SQL", "练习关系查询、聚合、连接和窗口函数"),
    CONCURRENCY("并发协调", "用锁、信号量与线程间协调控制执行次序");

    public final String label;
    public final String description;
    Topic(String label, String description) { this.label = label; this.description = description; }
    @Override public String toString() { return label; }
}
