# Contributing

We welcome contributions from the community. This repository follows the
patterns established by cloud-itonami ISCO actor projects.

## Code Standards

- All source code must be `.cljc` (portable, no JVM-only constructs).
- Follow the existing structure: `src/marine/` namespace, tests in
  `test/marine/`.
- Use `langgraph.graph` for state machine definitions.
- All proposals must be `:effect :propose` (advisory, not actuation).

## Testing

Run the test suite before submitting a pull request:

```bash
clojure -M:test
```

All tests must pass. Aim for comprehensive coverage of governor invariants
and escalation paths.

## License

By contributing, you agree that your contributions will be licensed under the
AGPL-3.0-or-later license.
