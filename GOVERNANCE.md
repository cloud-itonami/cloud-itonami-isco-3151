# Governance

This repository implements an ISCO-08 occupation blueprint for marine
engineering coordination (ISCO unit-group 3151).

## Decision-Making

Major decisions regarding the actor's scope, governor invariants, and
escalation rules are made through the cloud-itonami ADR (Architecture Decision
Record) process.

Changes to:
- Hard invariants in the governor
- Core proposal operations
- Governor escalation thresholds

...require community discussion and documented rationale (ADR or GitHub issue).

## Scope

This actor is **not** responsible for:
- Engine control (start, stop, throttle, fuel cutoff, propeller pitch)
- Navigation decisions
- Command-authority actions
- Real-time system monitoring (that's a separate operational dashboard)

This actor **is** responsible for:
- Structured operational record logging
- Maintenance scheduling proposals
- Mechanical fault escalation
- Fuel resupply coordination

## Safety First

All proposals affecting safe ship operation must include:
1. Clear rationale (why this operation?)
2. Explicit scope boundaries (what this actor does and does NOT do)
3. Governance rules (what the governor gates)
4. Human escalation path (how humans override or veto)

## License

AGPL-3.0-or-later. Derivative works must remain open source.
