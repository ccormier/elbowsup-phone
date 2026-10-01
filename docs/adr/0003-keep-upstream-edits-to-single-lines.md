# Keep edits to upstream files to single lines

This is a fork of Fossify Phone and we want to pull upstream changes without painful merges. All blocker code lives in its own package with its own resources, and each edit to an upstream file is one line that calls into it, marked `// ELBOWSUP` and listed in `FORK.md`. Hooks use fully qualified names and no `import`, which looks odd but matters: a rehearsal merge of a year of upstream commits conflicted only on our import lines, never on the hook lines, because upstream edits the import block constantly.

The accepted exceptions are fork-setup edits that cannot move: the app id and version pin in the Gradle files, and the launcher name in each language's strings. They conflict on nearly every upstream release, and `FORK.md` has the one-minute resolution for each.
