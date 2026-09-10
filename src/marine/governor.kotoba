(ns marine.governor
  "MarineEngineeringGovernor — the independent safety/traceability layer
  for ship engineering operations. Gates maintenance scheduling, fuel logistics,
  and fault reporting.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. vessel-registered    — the vessel must be registered before any operation.
    2. equipment-registered — equipment referenced must be registered on the vessel.
    3. no-engine-control    — proposals must NEVER contain direct engine
                              control commands; only readings, scheduling, and
                              coordination are permitted.
    4. effect-is-propose    — :effect must be :propose only (the governor
                              never directly executes operations).

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    5. :flag-mechanical-fault — any mechanical fault flagging always escalates
                              to human review, regardless of confidence.
    6. low confidence (< confidence-floor)."
  (:require [marine.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:flag-mechanical-fault})

(defn- hard-violations [{:keys [request proposal]}]
  (let [{:keys [vessel-id equipment-id op]} proposal
        vessel-record (:vessel-record request)
        equipment-record (:equipment-record request)]
    (cond-> []
      (nil? vessel-record)
      (conj {:rule :no-vessel
             :detail "未登録 vessel — 登録していない船舶での作業不可"})

      (and equipment-id (nil? equipment-record))
      (conj {:rule :no-equipment
             :detail "未登録 equipment — 登録していない機器への操作不可"})

      (and (some #{:engine-control :throttle :fuel-cutoff :propeller-pitch}
                 [op]))
      (conj {:rule :no-engine-control
             :detail "エンジン直接制御は禁止（governor の権限外。機関長の指示のみ）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は直接実行しない）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `marine.store/Store`. Pure — never mutates
  the store, never executes engine control commands."
  [request context proposal store]
  (let [vessel-id (:vessel-id proposal)
        equipment-id (:equipment-id proposal)
        vessel-record (store/vessel store vessel-id)
        equipment-record (when equipment-id
                          (store/equipment store vessel-id equipment-id))
        request-with-records (merge request
                                    {:vessel-record vessel-record
                                     :equipment-record equipment-record})
        hard (hard-violations {:request request-with-records :proposal proposal})
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
