# Changelog

## 0.1.1 — 2026-10-23

- Replace deprecated read-action APIs in project scanning with cancellable, non-blocking read actions.
- Allow pending IDE writes to interrupt and retry scans without accumulating duplicate file entries.
- Keep scan cancellation and project disposal connected to each read action.

## 0.1.0 — 2026-10-23

- Embed the official LeetCode China and international websites in an IDE tool window.
- Synchronize real problem metadata from LeetCode with pagination and cancellation.
- Synchronize accepted status through the user's embedded website session.
- Analyze local project files and recommend problems using official topic tags, with file and line evidence.
- Filter problems by difficulty, estimated practice time, title, ID, and topic.
- Store LeetCode authentication cookies using IntelliJ Password Safe.
- Target IntelliJ Platform 2026.2 with JCEF and Java 25.

Initial release. Website authentication, verification challenges, and submissions are handled by LeetCode. Account login, restart persistence, and submission flows still require account-level validation.
