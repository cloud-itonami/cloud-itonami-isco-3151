(ns marine.advisor
  "Marine Engineering Advisor: proposes engine maintenance, fuel logistics,
  and fault-response actions based on vessel state and operational readings.
  The advisor is subordinate to the governor; the governor gates every proposal.")

(defprotocol Advisor
  "Marine engineering advice layer."
  (-advise [this store request] "Advise on an operational request."))

(defn mock-advisor
  "Minimal advisor for testing: accepts any request and proposes it as-is."
  []
  (reify Advisor
    (-advise [_ _ request]
      (merge request {:effect :propose :confidence 0.8}))))

(defn basic-advisor
  "Marine advisor with simple heuristics.
  Raises confidence for routine operations (log-reading, scheduling).
  Lowers confidence for fault flagging (requires domain knowledge)."
  []
  (reify Advisor
    (-advise [_ store request]
      (let [{:keys [op]} request
            conf (case op
                   :log-engine-reading 0.9
                   :schedule-maintenance 0.7
                   :flag-mechanical-fault 0.4
                   :coordinate-fuel-bunkering 0.75
                   0.5)]
        (assoc request :effect :propose :confidence conf)))))
