# cloud-itonami-isco-3151

Open Occupation Blueprint for **ISCO-08 3151**: Ships' Engineers.

This repository designs a marine engineering coordination actor for ship operations: vessel maintenance scheduling, engine parameter logging, mechanical fault escalation, and fuel logistics coordination under a governor-gated actor, so a ship's engineering officer keeps structured operational records instead of ad-hoc logs.

**Maturity: `:implemented`.** `src/marine/` implements the
`MarineEngineeringActor` as a `langgraph.graph/state-graph`
(`marine.actor`) wired to a `Marine Advisor` (`marine.advisor`)
and an independent `MarineEngineeringGovernor` (`marine.governor`),
following the itonami actor pattern (ADR-2607011000): `:intake -> :advise
-> :govern -> :decide -+-> :commit (:ok?) +-> :request-approval (:escalate?,
human-in-the-loop interrupt) +-> :hold (:hard?)`. 6 tests / 14 assertions
green (`clojure -M:test`). 

## What This Actor Does NOT Do

**This actor coordinates maintenance, logistics, and fault reporting only.
It does NOT:**

- **Control engines, propellers, or fuel systems directly.** Engine start/stop,
  throttle adjustment, and fuel-cutoff decisions remain the ship's exclusive
  human authority (engineering officer/master at sea). The actor logs readings
  and proposes actions; humans execute commands.
- **Make navigation decisions.** Route planning, heading, course correction,
  and collision avoidance are the master's sole responsibility.
- **Exercise command authority.** The actor advises; the engineering officer
  decides. No actor vote supersedes human judgment at sea.

## Scope & HARD invariants

Proposal ops (closed allowlist, all `:effect :propose`):

- `:log-engine-reading` — routine engine/machinery parameter logging (RPM,
  fuel pressure, oil temperature, coolant flow, etc.)
- `:schedule-maintenance` — maintenance scheduling proposal (upcoming service
  intervals, inspections, parts replacements)
- `:flag-mechanical-fault` — surface a mechanical fault/anomaly, ALWAYS escalates
- `:coordinate-fuel-bunkering` — fuel resupply coordination proposal (quantity,
  port, supplier, fuel type)

**HARD invariants** (always `:hold`, never overridable):

1. **vessel-registered** — the vessel must be registered before any operation.
2. **equipment-registered** — equipment referenced must be registered on the
   vessel.
3. **no-engine-control** — proposals must NEVER contain direct engine control
   commands (`:engine-control`, `:throttle`, `:fuel-cutoff`, `:propeller-pitch`).
   Only readings, scheduling, and coordination are permitted.
4. **effect-is-propose** — `:effect` must be `:propose` only (the governor
   never directly executes operations).

**ESCALATION invariants** (always human sign-off):

5. **`:flag-mechanical-fault`** always escalates, regardless of confidence.
6. **Low confidence** (< 0.6) escalates to human review.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3151`). Required capabilities:

- :audit-ledger
- :forms
- :identity

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
