# Operator Guide: Marine Engineering Coordination Actor

## Overview

The MarineEngineeringActor helps ship engineers log readings, schedule
maintenance, flag faults, and coordinate fuel resupply in a structured,
auditable manner. This guide describes the four main operations and how
the actor gates them.

## Setup

### Register a Vessel

Before any operation, the vessel must be registered:

```clojure
(store/register-vessel! store
  {:vessel-id "mv-pacific-001"
   :name "MV Pacific"
   :imo-number "9123456"
   :callsign "WPAC"})
```

### Register Equipment

Register all machinery and equipment on the vessel:

```clojure
(store/register-equipment! store
  {:vessel-id "mv-pacific-001"
   :equipment-id "engine-main"
   :name "Main Diesel Engine"
   :type "Wärtsilä 8L32"
   :installed-year 2018})
```

### Initialize the Actor

```clojure
(require '[marine.actor :as actor]
         '[marine.store :as store])

(let [st (store/mem-store)
      graph (actor/build-graph {:store st})]
  ;; ... use graph to run requests
  )
```

## Operations

### 1. Log Engine Reading

**Operation:** `:log-engine-reading`

**Purpose:** Record routine engine and machinery parameters.

**Request:**

```clojure
{:vessel-id "mv-pacific-001"
 :op :log-engine-reading
 :equipment-id "engine-main"
 :reading {:rpm 500
           :fuel-pressure 250
           :oil-temperature 45
           :coolant-temp 50
           :cylinder-oil-viscosity 380}}
```

**Governor Check:**
- Vessel must be registered ✓
- Equipment must be registered on that vessel ✓
- No engine control commands ✓

**Confidence:** 0.9 (routine measurement)

**Outcome:** Record committed immediately.

**Audit Trail:** `{:node :commit :record {...}}`

### 2. Schedule Maintenance

**Operation:** `:schedule-maintenance`

**Purpose:** Propose a maintenance action or service interval.

**Request:**

```clojure
{:vessel-id "mv-pacific-001"
 :op :schedule-maintenance
 :equipment-id "engine-main"
 :maintenance-type :oil-change
 :due-date "2026-07-30"
 :estimated-duration-hours 8
 :parts-list [{:part-id "OIL-SAE-40" :quantity 50 :unit "liters"}]
 :notes "Annual oil change interval"}
```

**Governor Check:**
- Vessel must be registered ✓
- Equipment must be registered on that vessel ✓

**Confidence:** 0.7 (routine scheduling)

**Outcome:** Record committed, maintenance schedule logged.

**Audit Trail:** Stored for shore office coordination and crew planning.

### 3. Flag Mechanical Fault

**Operation:** `:flag-mechanical-fault`

**Purpose:** Escalate a mechanical anomaly for engineering officer review.

**Request:**

```clojure
{:vessel-id "mv-pacific-001"
 :op :flag-mechanical-fault
 :equipment-id "engine-main"
 :severity :high
 :fault-description "Crankcase pressure spiking to 4.0 bar (normal 0.5–1.5)"
 :detected-at "2026-07-14T14:32:00Z"
 :suspected-cause "Possible piston ring blowby or oil leakage"
 :immediate-action "Reduce RPM to 400, monitor closely"}
```

**Governor Check:**
- Vessel must be registered ✓
- Equipment must be registered ✓

**Confidence:** 0.4 (fault diagnosis is engineer expertise)

**Outcome:** **ESCALATED** → Request human approval.

**Escalation Interrupt:** The actor pauses and waits for engineering officer
sign-off. This ensures human judgment gates all fault handling.

**Resume:**

```clojure
(actor/approve! graph "thread-id")
```

Once approved, the fault is recorded with approval timestamp and officer
identifier.

**Audit Trail:** Includes fault detection, description, severity, immediate
actions, approval decision, and approver identity.

### 4. Coordinate Fuel Bunkering

**Operation:** `:coordinate-fuel-bunkering`

**Purpose:** Log a fuel resupply plan for shore office coordination.

**Request:**

```clojure
{:vessel-id "mv-pacific-001"
 :op :coordinate-fuel-bunkering
 :fuel-quantity 50000
 :fuel-type "IFO 380"
 :port "Singapore"
 :estimated-delivery "2026-07-20T10:00:00Z"
 :supplier "Shell Singapore"
 :fuel-spec {:sulfur-content-max 3.5
             :viscosity-min 350
             :viscosity-max 500}
 :expected-cost-usd 12500000}
```

**Governor Check:**
- Vessel must be registered ✓
- No engine control commands ✓

**Confidence:** 0.75 (routine logistics)

**Outcome:** Record committed; shore office receives bunkering plan.

**Audit Trail:** Fuel order logged with quantity, port, timing, supplier,
and cost estimate.

## Error Handling

### Unregistered Vessel

If the vessel is not registered, the governor blocks the operation:

```clojure
{:hard? true
 :violations [{:rule :no-vessel
               :detail "未登録 vessel — 登録していない船舶での作業不可"}]
 :disposition :hold}
```

**Fix:** Register the vessel first (see Setup).

### Unregistered Equipment

If equipment is not registered on the vessel:

```clojure
{:hard? true
 :violations [{:rule :no-equipment
               :detail "未登録 equipment"}]
 :disposition :hold}
```

**Fix:** Register the equipment (see Setup).

### Engine Control Command (HARD REJECT)

If a proposal contains an engine control command (e.g., `:throttle`):

```clojure
{:hard? true
 :violations [{:rule :no-engine-control
               :detail "エンジン直接制御は禁止"}]
 :disposition :hold}
```

**Rationale:** Engine control is the engineering officer's exclusive
authority. The actor proposes; the officer executes.

### Low Confidence (ESCALATION)

If advisor confidence is below 0.6:

```clojure
{:hard? false
 :escalate? true
 :confidence 0.4
 :disposition :request-approval}
```

**Outcome:** Human review required before commit. Engineering officer
approves or rejects.

## Testing

Run the test suite:

```bash
clojure -M:test
```

Expected: 6 tests, 14 assertions green.

## Integration Notes

This actor is designed to integrate with:

- **Vessel Data Registry** (DNV, ABS, Lloyd's class society records)
- **Crew Management System** (crew qualifications, watch rotation)
- **Shore Office Coordination** (fleet management, fuel suppliers)
- **Regulatory Reporting** (IMO SOLAS, ISM Code, flag-state submissions)

All operations produce append-only audit ledger entries; compliance audits
can replay the entire decision log.
