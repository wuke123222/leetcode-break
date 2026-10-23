# LeetCode Break

Practice on the official LeetCode website inside JetBrains IDEs, with local project-based problem recommendations. Free and open source under the [MIT license](LICENSE).

[Source code](https://github.com/wuke123222/leetcode-break) · [Issues](https://github.com/wuke123222/leetcode-break/issues) · [Privacy](PRIVACY.md)

在 IntelliJ IDEA 系列 IDE 里直接使用 LeetCode 官网刷题，并从当前项目的代码知识点推荐相关题目。

**没有自建题库、题目编辑器或评测服务。** 插件从 LeetCode 网站的 GraphQL 接口分页获取题目；看题、写代码、运行、提交、会员权限与登录均由内嵌的 LeetCode 官网负责。

## 使用

1. `Settings → Plugins → ⚙ → Install Plugin from Disk…`，选择 `build/distributions/leetcode-break-0.1.0.zip`，按 IDE 提示重启。
2. 在右侧打开 **LeetCode Break**，或使用 `Tools → LeetCode Break — 开始刷题`。
3. 选择力扣中国站 / LeetCode 国际站。点击 **同步官方题库**，分页获取该站点的真实题目、难度、标签和会员标记。
4. 如站点要求登录或网页验证，点击 **打开 LeetCode**，在官方页面完成，然后点击网页上方 **同步官方题库**。这条路径使用当前内嵌浏览器的会话，并同步官方已通过状态。
5. 点击 **分析项目** 或 **分析当前文件**。推荐按项目中出现的知识点排序；点击文件与行号可回到代码。
6. 按难度、参考用时、题号、题名或标签筛选。点击 **开始练习** 或双击题目，进入内嵌官网的真实题目页面。
7. 直接使用官网编辑器运行和提交。提交后再次从内嵌官网同步，可更新官方通过状态。

同步是显式操作，题库按页显示，可取消；当前窗口保留已收到的题目。无匹配证据的官方题目显示为“通用练习”。未同步或接口失败时，不会用样例题目填充。普通公开接口同步没有账号身份，不能反映已登录账号的通过状态；通过状态以最近一次官网会话同步及官网页面为准。

## 项目关联

- 本地扫描 Java、Kotlin、Python、JavaScript/TypeScript、Go、C/C++、Rust、SQL 等源文件中的标识符/API，不依赖语言插件。
- 识别哈希表、缓存设计、堆、栈、队列、树、图、前缀树、二分查找、排序、滑动窗口、动态规划、链表、SQL、并发协调。
- 将检测结果映射到 **LeetCode 返回的真实标签**。例如 `PriorityQueue` → `heap-priority-queue`，`ReentrantLock` → `concurrency`，`LinkedHashMap` → 缓存设计 / 链表。
- 推荐展示对应文件与行号；同一文件重复出现的相同特征不会无限增加权重。
- 这是可解释的词法启发式匹配，不是完整语义分析。普通注释和字符串会被忽略；应用字符串里的 SQL 不会被识别，独立 `.sql` 文件支持查询分析。
- 参考用时按难度估算为简单 10、中等 25、困难 45 分钟，不是官网承诺用时。
- 扫描运行在可取消的后台任务中，每次最多 1,500 个文件，单文件 256 KiB，累计最多约 12 Mi 字符；达到上限会提示。
- 跳过 IDE 排除目录、常见依赖/构建目录、生成文件、符号链接、二进制与不支持的文件。当前编辑器已打开的文档包含尚未保存的修改。代码内容与证据只保留在内存，不发送给 LeetCode 或任何模型服务。

## 登录态

- 用户在官方 HTTPS 页面自行登录；不提供预置账号，不读取系统浏览器 Cookie，不收集密码。
- 只持久化当前站点的 `LEETCODE_SESSION` / `csrftoken`，使用 **IntelliJ Password Safe**，不写入项目、XML 配置或日志。
- 浏览器创建后先恢复会话，再加载官网。每 15 秒自动保存，页面加载完成也会触发保存，提供手动保存按钮。
- 中国站和国际站凭据分开保存；多项目窗口共用一个串行会话服务，避免保存/恢复/清除交错。
- 无过期时间的会话 Cookie 恢复期限设为 7 天；官网仍可能更早撤销或要求重新验证。若 IDE 密码设置为仅内存，则不跨重启保存，界面会提示。
- **清除会话**删除当前站点已保存的认证 Cookie 与浏览器中的相应 Cookie，不清理其他站点。
- 使用 IDE 的 JCEF Cookie 管理器，同一个 IDE 进程内的 JCEF 浏览器可能共享网站 Cookie；它不是独立浏览器配置文件。
- 官网同步请求在对应站点页面内通过同源 `fetch` 执行。网页消息桥仅接收带临时请求 ID 的题库 JSON，并校验站点，不暴露源码、文件操作或系统命令。

## 兼容范围与限制

当前构建目标为 **IntelliJ Platform 2026.2（262.*）、Java 25、带 JCEF 的 JetBrains Runtime**，以本机 IntelliJ IDEA 2026.2.3 编译验证。只依赖通用平台和 JCEF 模块，可面向同平台版本的其他 JetBrains IDE；其他 IDE 尚未逐一实机验证。未声明对 2025.x / 2024.x 兼容。

- 2026.2 起 JCEF 为单独的内置插件，因此显式依赖 `com.intellij.modules.jcef`。
- 不支持 JCEF 的运行时仍可查看推荐，并提供系统浏览器入口。系统浏览器登录态与内嵌浏览器独立。
- LeetCode GraphQL 是官网使用的接口，不是有稳定兼容承诺的公开开发者 API。适配了 `questionList` 与中国站 `problemsetQuestionList` 两种响应，接口变更会报告错误。
- 遇到 403、验证码或 Cloudflare 校验时，需要用户在内嵌官网完成正常验证；不绕过网站校验。
- 弹出链接在内嵌窗口打开。依赖 `window.opener` 或禁止嵌入浏览器的第三方 OAuth 流程可能不可用，优先使用官网支持的直接登录方式。
- 会员题照常标记，查看与提交权限由官网控制。
- 未实现题库离线缓存、自动后台周期同步、账号切换后的自动状态刷新或云端 AI 语义推荐。

## 开发与验证

需要 JDK 25；Gradle Wrapper 已包含。

```bash
# 下载目标 IDE 后运行测试并打包
./gradlew test buildPlugin

# 使用本机 IDE，避免重复下载 SDK（macOS 示例）
./gradlew test buildPlugin -PlocalIdePath="/Applications/IntelliJ IDEA.app/Contents"

# 启动隔离的插件开发沙箱，不向日常 IDE 安装
./gradlew runIde -PlocalIdePath="/Applications/IntelliJ IDEA.app/Contents"
```

安装包位于 `build/distributions/`；测试报告位于 `build/reports/tests/test/index.html`。

测试覆盖：源文件特征提取、注释/字符串排除、扫描过滤、推荐排序与官方通过状态筛选、官方接口响应解析、分页去重/停滞/字段回退、凭据域名边界和过期处理。测试中的少量协议样例只用于测试，不随插件作为题库发布。

发布前人工检查：

- 中国站和国际站分别完成登录，打开题目，运行代码并手动提交；
- 保存会话后重启开发沙箱，确认官网仍识别账号；清除会话后再次重启应保持未登录；
- 在官网完成一道题后同步，确认“隐藏官网已通过”会排除该题；
- 切换站点与取消同步后，不出现之前站点的结果；
- 多项目、浅色/深色主题、小宽度窗口以及非 JCEF 运行时。

这些操作涉及个人账号与实际提交，不在自动测试中代为完成。

实现参考：[JetBrains JCEF 文档](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html)、[Password Safe 文档](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html)。本项目与 LeetCode 无隶属关系。
