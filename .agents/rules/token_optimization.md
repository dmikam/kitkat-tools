# Token Optimization Rules

1. File Operations: Do NOT scan the entire repository or read unasked files. Only inspect files explicitly mentioned in the task or prompt.
2. Conciseness: Skip preamble, explanations, and summaries. Provide only code diffs or short direct answers unless explicitly requested.
3. Execution Scope: Do not make unrequested fixes outside the primary scope. Do not auto-run test suites or linting unless instructed.
4. Planning Phase: Keep implementation plans short (under 5 bullet points) for simple tasks. Do not write multi-page architectural specs for simple fixes.