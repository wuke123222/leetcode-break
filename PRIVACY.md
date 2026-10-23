# Privacy

LeetCode Break does not operate an analytics, telemetry, advertising, or backend service.

- Project analysis runs locally. Source text and recommendation evidence remain in memory and are not uploaded to LeetCode, an AI provider, or the plugin developer.
- The plugin connects to the selected official LeetCode website to retrieve problem metadata and display the website. The website receives normal browser and network information and handles its own account, editor, submission, analytics, and cookies under its own policies.
- When you sign in on the official website, the plugin saves only the selected site's `LEETCODE_SESSION` and `csrftoken` authentication cookies in IntelliJ Password Safe. Storage behavior follows the IDE's password settings. No account password is requested by the plugin.
- The site preference and practice-time filter are stored in IDE settings. Fetched problem metadata and accepted status are held in memory for the current window.
- The **Clear session / 清除会话** control removes the plugin's saved authentication cookies and the corresponding cookies from the embedded browser. It does not delete your LeetCode account or submissions.
- The embedded browser uses the IDE's JCEF cookie manager. Other embedded browsers within the same IDE process may share cookies for the same website.

Report plugin privacy issues through [GitHub Issues](https://github.com/wuke123222/leetcode-break/issues). Do not post authentication cookies, passwords, private source code, or other secrets in public issues.
