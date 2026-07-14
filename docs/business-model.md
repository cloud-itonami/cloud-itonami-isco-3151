# Business Model: Marine Engineering Coordination

## Context

A ship's engineering department maintains operational records and schedules maintenance to keep main engines, auxiliary systems, and support equipment in safe operating condition. Currently, many vessels still rely on paper logs, email scheduling, and informal escalation paths for fault reporting—a practice that creates liability gaps and makes audit trails fragmented.

## Problem

- **No structured operational record.** Handwritten logs cannot be indexed,
  searched, or validated in real-time.
- **Fault escalation is ad-hoc.** Critical mechanical faults may be reported
  informally (a mention in conversation, a note on a status board) rather than
  through a formal process.
- **Maintenance scheduling is opaque.** Planned services, inspections, and
  parts replacements live in multiple documents (crew notices, shore office
  emails, vessel-specific forms).
- **Audit trail is fragmented.** Regulators, class societies, and flag-state
  administrations need a clear chain of custody for operational decisions and
  their outcomes.

## Solution: MarineEngineeringActor

This actor codifies four core operations under a governor gate:

1. **log-engine-reading** — Routine readings (RPM, pressures, temperatures,
   flows, frequencies) are logged as timestamped records with equipment
   metadata. High confidence (0.9); routine approval.

2. **schedule-maintenance** — Maintenance proposals (scheduled service interval,
   planned replacement, inspection type, estimated duration) are logged and
   stored for shore office and crew coordination. Moderate confidence (0.7);
   routine approval.

3. **flag-mechanical-fault** — A mechanical anomaly (high crankcase pressure,
   unusual vibration, coolant leak, abnormal smoke, bearing temperature, etc.)
   is flagged with severity, description, and recommended response. **Always
   escalates to human sign-off.** Low confidence (0.4) reflects that fault
   assessment is a domain expertise task (engineer's judgment, not algorithmic).

4. **coordinate-fuel-bunkering** — Fuel resupply proposals (port, quantity,
   fuel grade, supplier) are logged for shore office coordination and fuel
   certification. Moderate confidence (0.75).

## Governor Role

The MarineEngineeringGovernor enforces four HARD invariants:

1. **Vessel registered** — the ship must be in the actor's vessel registry
   before any operation.
2. **Equipment registered** — machinery referenced in a proposal must be
   registered on that vessel.
3. **No engine control** — the actor will never propose direct engine
   commands (throttle, start/stop, fuel cutoff, propeller pitch).
   **These remain the engineering officer's exclusive authority.**
4. **Effect is propose** — all proposals are advisory (`:effect :propose`).
   The governor does not dispatch systems or make binding operational
   decisions.

Escalation (human sign-off required):

- **`:flag-mechanical-fault`** — any fault is escalated, because fault
  severity assessment and response strategy require the engineer's judgment
  and domain knowledge. No automation bypasses human review.
- **Low confidence** (< 0.6) — if the advisor's confidence in a reading or
  proposal is low, the governor escalates to the engineering officer for
  manual verification.

## Example Flow

### Routine Engine Reading

```
Engineer: "Main engine RPM 500, fuel pressure 250 bar, oil temp 45°C"
  ↓
Actor intake: {:op :log-engine-reading :equipment-id "engine-1" :reading {...}}
  ↓
Advisor: confidence 0.9 (routine reading)
  ↓
Governor: vessel registered? ✓ equipment registered? ✓ no control commands? ✓
  → :ok? true, :hard? false, :escalate? false
  ↓
Decision: :commit
  ↓
Record stored: {:op :log-engine-reading :vessel-id "..." :payload {...}}
```

### Mechanical Fault Escalation

```
Engineer: "Crankcase pressure spiking to 4 bar (normal is 0.5–1.5)"
  ↓
Actor intake: {:op :flag-mechanical-fault :equipment-id "engine-1"
               :severity :high :description "..."}
  ↓
Advisor: confidence 0.4 (fault diagnosis is domain-specific)
  ↓
Governor: vessel registered? ✓ equipment registered? ✓
  → :escalate? true (always escalate faults)
  ↓
Decision: :request-approval (human sign-off interrupt)
  ↓
[PAUSE: awaiting engineering officer sign-off]
  ↓
Engineering Officer: reviews, approves escalation
  ↓
Actor resumes: :commit
  ↓
Record stored + audit ledger: fault flag acknowledged + approved timestamp
```

### Fuel Bunkering Coordination

```
Engineering Officer: "Plan fuel delivery at Port of Singapore, 50,000 metric
tons IFO 380"
  ↓
Actor intake: {:op :coordinate-fuel-bunkering :fuel-quantity 50000
               :fuel-type "IFO 380" :port "Singapore"}
  ↓
Advisor: confidence 0.75 (logistics coordination, routine)
  ↓
Governor: vessel registered? ✓ no control commands? ✓
  → :ok? true, :hard? false, :escalate? false
  ↓
Decision: :commit
  ↓
Record stored: fuel order proposal logged with vessel, port, quantity,
estimated delivery date, supplier reference
```

## Human Authority & Liability

The actor **never** executes engine control commands, makes navigation
decisions, or overrides the engineering officer's judgment. All proposals
are advisory. The engineering officer:

- Reviews escalated faults and approves/rejects remediation.
- Reviews fuel, maintenance, and scheduling proposals.
- Executes engine commands (start, stop, throttle, fuel adjustments)
  directly, based on the actor's readings and recommendations.
- Retains full responsibility for safe ship operation.

The actor provides:

- **Structured, timestamped operational records** (no more scattered logs).
- **Formal escalation path** for faults (clear chain of custody).
- **Audit trail** (every proposal and decision is logged with metadata).

## Regulatory Alignment

Compliance with:
- **IMO SOLAS** (International Maritime Organization Safety of Life at Sea):
  requires operational records and crew qualification tracking.
- **International Safety Management Code (ISM):** mandates documented
  procedures and incident reporting.
- **Class Society (DNV, ABS, Lloyd's):** expect audit-ready logs and
  maintenance schedules.
- **Flag-state records:** most flag states require legible operational logs
  and master-signed incident reports.

This actor bridges the gap by providing machine-readable, audit-trail-backed
operational records that satisfy both crew workflow (quick logging) and
regulatory reporting (complete chain of custody).
