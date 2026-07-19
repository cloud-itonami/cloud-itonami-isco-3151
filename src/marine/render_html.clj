(ns marine.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.
  Closes the ISCO no-demo checklist item for `cloud-itonami-isco-3151`
  (ISCO-08 3151 ship engineering officer, `marine` domain) -- this
  repo previously had NO demo page and no generator at all. Pattern
  lifted from `cloud-itonami-isco-1211`'s `finmgmt.render-html`
  (cloud-itonami maturity loop iter9, ADR-2607189200), adapted to
  this repo's own real `marine.actor` / `marine.governor` /
  `marine.store` / `marine.advisor` shape.

  This namespace drives the REAL actor stack (`marine.actor` ->
  `marine.governor` -> `marine.store`, advised by the real
  `marine.advisor/basic-advisor`) through a scenario built from real,
  exercised store data and renders the result deterministically -- no
  invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed.

  `vessel-1` (\"MV Pacific\", imo-number \"9123456\") and its
  equipment `engine-1` (\"Main Diesel Engine\", \"Wartsila 8L32\") are
  lifted VERBATIM from the proven-passing
  `test/marine/actor_test.cljc` `fresh-store` fixture (the
  ground-truth fixture per this repo's own actor-level tests;
  `governor_test.cljc`/`store_test.cljc` use different ad-hoc inline
  ids/keys such as \"v1\"/\"Ship 1\" and an `:imo` vs `:imo-number` key
  spelling for their own per-test data -- those are NOT a shared
  fixture and are not used here, only noted).

  `vessel-2` (\"MV Aurora\") and its equipment `generator-1`
  (\"Auxiliary Generator\") are ADDITIONAL demo data registered via
  the store's own real `register-vessel!`/`register-equipment!`
  protocol calls -- disclosed here plainly, not presented as if
  pre-existing fixture. `vessel-2` exists to prove the graph and
  store handle a second, independent vessel cleanly.

  Every other field this page displays is real output read after
  `run-demo!` actually executed the graph.

  Advisor choice: this scenario drives the graph with the real
  `marine.advisor/basic-advisor` (rather than the actor's default
  `mock-advisor`) because `basic-advisor` is the ONLY real advisor in
  this repo whose confidence table has a branch below the governor's
  0.6 confidence floor (its `case` default of 0.5, for any `:op` not
  in its `{:log-engine-reading :schedule-maintenance
  :flag-mechanical-fault :coordinate-fuel-bunkering}` table) -- this
  lets the scenario reach the low-confidence escalation rule for
  real, rather than disclosing it as unreachable. The
  `v1-unclassified-low-confidence` step deliberately uses an `:op`
  outside that table (`:inspect-general-condition`) to land in that
  default branch; it is not a claim that this is a distinct marine
  operation type, only an honest exercise of `basic-advisor`'s real
  fallback code path.

  Governor coverage (`marine.governor`, confirmed by reading the real
  source): of its 4 HARD rules + 2 ESCALATION rules, this scenario
  reaches 3 hard rules (`:no-vessel` / `:no-equipment` /
  `:no-engine-control`) and BOTH escalation rules
  (`:flag-mechanical-fault` always-escalate + low confidence), all
  through the REAL `run-request!`/`approve!` path. One rule is
  structurally UNREACHABLE through that real path and is honestly
  excluded from the scenario rather than faked:
    - `:no-actuation` (`effect` must be `:propose`) -- both real
      advisors in this repo (`mock-advisor`'s `(merge request
      {:effect :propose ...})` and `basic-advisor`'s `(assoc request
      :effect :propose ...)`) unconditionally force `:effect
      :propose` on every proposal; there is no request shape that
      makes either real advisor emit a non-`:propose` effect.
      `governor_test.cljc`'s `hard-violations-non-propose-effect`
      only reaches this rule by calling `governor/check` directly
      with a hand-built proposal that bypasses the advisor entirely
      -- which this page's harness deliberately does not do (it only
      drives the real graph).

  Usage: `clojure -M:render-html [out-file]` (default
  `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [marine.store :as store]
            [marine.advisor :as advisor]
            [marine.actor :as actor]))

(defn- run-op! [graph tid request]
  (let [r1 (actor/run-request! graph request {} tid)]
    (if (= :interrupted (:status r1))
      (let [r2 (actor/approve! graph tid)]
        {:thread-id tid :request request
         :outcome :approved-and-committed
         :record (get-in r2 [:state :record])})
      (let [disposition (get-in r1 [:state :disposition])]
        (if (= :hold disposition)
          {:thread-id tid :request request
           :outcome :hard-hold
           :verdict (get-in r1 [:state :verdict])
           :rule (-> r1 :state :verdict :violations first :rule)}
          {:thread-id tid :request request
           :outcome :auto-committed
           :record (get-in r1 [:state :record])})))))

(def ^:private op-specs
  [["v1-log-reading-ok"
    {:vessel-id "vessel-1" :op :log-engine-reading :equipment-id "engine-1"
     :reading {:rpm 500 :fuel-pressure 250 :oil-temp 45}}]
   ["v1-schedule-maintenance-ok"
    {:vessel-id "vessel-1" :op :schedule-maintenance
     :task "Annual class survey"}]
   ["v1-fuel-bunkering-ok"
    {:vessel-id "vessel-1" :op :coordinate-fuel-bunkering
     :fuel-quantity 50000 :fuel-type "IFO 380" :port "Singapore"}]
   ["v-unregistered-no-vessel"
    {:vessel-id "vessel-999" :op :log-engine-reading
     :reading {:rpm 500}}]
   ["v1-unknown-equipment"
    {:vessel-id "vessel-1" :op :log-engine-reading :equipment-id "engine-999"
     :reading {:rpm 500}}]
   ["v1-throttle-blocked"
    {:vessel-id "vessel-1" :op :throttle :equipment-id "engine-1"
     :command :throttle-up}]
   ["v1-fault-escalate"
    {:vessel-id "vessel-1" :op :flag-mechanical-fault :equipment-id "engine-1"
     :fault-description "High crankcase pressure detected" :severity :high}]
   ["v1-unclassified-low-confidence"
    {:vessel-id "vessel-1" :op :inspect-general-condition}]
   ["v2-log-reading-ok"
    {:vessel-id "vessel-2" :op :log-engine-reading :equipment-id "generator-1"
     :reading {:rpm 1800 :fuel-pressure 300 :oil-temp 60}}]])

(defn run-demo! []
  (let [db (store/mem-store)]
    (store/register-vessel! db {:vessel-id "vessel-1" :name "MV Pacific"
                                :imo-number "9123456"})
    (store/register-equipment! db {:vessel-id "vessel-1"
                                   :equipment-id "engine-1"
                                   :name "Main Diesel Engine"
                                   :type "Wartsila 8L32"})
    (store/register-vessel! db {:vessel-id "vessel-2" :name "MV Aurora"
                                :imo-number "9234567"})
    (store/register-equipment! db {:vessel-id "vessel-2"
                                   :equipment-id "generator-1"
                                   :name "Auxiliary Generator"
                                   :type "Cummins QSK19"})
    (let [graph (actor/build-graph {:store db :advisor (advisor/basic-advisor)})
          runs (mapv (fn [[tid request]] (run-op! graph tid request)) op-specs)]
      {:store db :runs runs})))

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- outcome-cell [{:keys [outcome rule]}]
  (case outcome
    :auto-committed "<span class=\"ok\">committed</span>"
    :approved-and-committed "<span class=\"ok\">approved &amp; committed</span>"
    :hard-hold (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")
    "<span class=\"muted\">in progress</span>"))

(defn- vessels-rows [db vessel-ids]
  (->> vessel-ids
       (map (fn [vid]
              (let [v (store/vessel db vid)]
                (str "<tr><td>" (esc (:vessel-id v)) "</td><td>" (esc (:name v))
                     "</td><td>" (esc (:imo-number v)) "</td></tr>"))))
       (str/join "\n")))

(defn- equipment-rows [db pairs]
  (->> pairs
       (map (fn [[vid eid]]
              (let [e (store/equipment db vid eid)]
                (str "<tr><td>" (esc (:vessel-id e)) "</td><td>" (esc (:equipment-id e))
                     "</td><td>" (esc (:name e)) "</td><td>" (esc (:type e)) "</td></tr>"))))
       (str/join "\n")))

(defn- request-summary [{:keys [op equipment-id] :as request}]
  (let [extras (dissoc request :vessel-id :op :equipment-id)]
    (str (name op)
         (when equipment-id (str " &middot; equipment=" (esc equipment-id)))
         (when (seq extras)
           (str " &middot; " (esc (pr-str extras)))))))

(defn- audit-rows [runs]
  (->> runs
       (map (fn [{:keys [thread-id request] :as r}]
              (str "<tr><td><code>" (esc thread-id) "</code></td><td>" (esc (:vessel-id request))
                   "</td><td>" (request-summary request) "</td><td>" (outcome-cell r) "</td></tr>")))
       (str/join "\n")))

(def ^:private gate-rows
  [["HARD" ":no-vessel" "the vessel must be registered before any operation."]
   ["HARD" ":no-equipment" "equipment referenced by :equipment-id must be registered on the vessel."]
   ["HARD" ":no-engine-control" "proposals must never contain direct engine control commands (:engine-control/:throttle/:fuel-cutoff/:propeller-pitch) -- only readings, scheduling, and coordination are permitted."]
   ["HARD" ":no-actuation" ":effect must be :propose only (structurally unreachable via either real advisor -- see namespace docstring)."]
   ["ESCALATE" ":flag-mechanical-fault" "any mechanical fault flagging always escalates to human review, regardless of confidence."]
   ["ESCALATE" "low confidence (< 0.6)" "confidence floor is 0.6; basic-advisor's case default (0.5, for any :op outside its known table) reaches this for real."]])

(defn- gate-table-rows []
  (->> gate-rows
       (map (fn [[kind rule detail]]
              (str "<tr><td><span class=\""
                   (if (= kind "HARD") "critical" "warn") "\">" kind "</span></td><td><code>"
                   (esc rule) "</code></td><td>" (esc detail) "</td></tr>")))
       (str/join "\n")))

(def ^:private style
  "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:0;padding:2rem;background:#0b0d12;color:#e6e9ef}
h1{font-size:1.4rem;margin:0 0 .25rem}
h2{font-size:1.05rem;margin:2rem 0 .5rem;color:#9fb3c8}
.sub{color:#8792a3;margin:0 0 1.5rem;font-size:.9rem}
table{width:100%;border-collapse:collapse;margin-bottom:1rem;font-size:.88rem}
th,td{text-align:left;padding:.45rem .6rem;border-bottom:1px solid #232833}
th{color:#8792a3;font-weight:600;text-transform:uppercase;font-size:.72rem;letter-spacing:.03em}
code{background:#151922;padding:.1rem .35rem;border-radius:4px;font-size:.85em}
.ok{color:#4ade80;font-weight:600}
.warn{color:#fbbf24;font-weight:600}
.critical{color:#f87171;font-weight:600}
.muted{color:#8792a3}
.card{background:#12151c;border:1px solid #232833;border-radius:10px;padding:1.25rem 1.5rem;margin-bottom:1.5rem}")

(defn render [{:keys [store runs]}]
  (str/join
   "\n"
   ["<!doctype html>"
    "<html><head><meta charset=\"utf-8\">"
    "<title>marine operator console -- cloud-itonami-isco-3151</title>"
    (str "<style>" style "</style></head><body>")
    "<h1>marine operator console</h1>"
    (str "<p class=\"sub\">ISCO-08 3151 &middot; ship engineering officer actor &middot; "
         "generated at build time by driving the real <code>marine.actor</code> StateGraph "
         "(<code>intake &rarr; advise &rarr; govern &rarr; decide &rarr; commit/hold</code>, "
         "human-approval interrupt for escalations) &mdash; no invented data, no timestamps.</p>")

    "<div class=\"card\">"
    "<h2>Registered vessels</h2>"
    "<table><thead><tr><th>vessel-id</th><th>name</th><th>imo-number</th></tr></thead><tbody>"
    (vessels-rows store ["vessel-1" "vessel-2"])
    "</tbody></table>"
    "<h2>Registered equipment</h2>"
    "<table><thead><tr><th>vessel-id</th><th>equipment-id</th><th>name</th><th>type</th></tr></thead><tbody>"
    (equipment-rows store [["vessel-1" "engine-1"] ["vessel-2" "generator-1"]])
    "</tbody></table>"
    "</div>"

    "<div class=\"card\">"
    "<h2>Governor action gate (marine.governor/check)</h2>"
    "<table><thead><tr><th>kind</th><th>rule</th><th>meaning</th></tr></thead><tbody>"
    (gate-table-rows)
    "</tbody></table>"
    "</div>"

    "<div class=\"card\">"
    "<h2>Audit trail (this scenario's real graph runs)</h2>"
    "<table><thead><tr><th>thread-id</th><th>vessel</th><th>request</th><th>outcome</th></tr></thead><tbody>"
    (audit-rows runs)
    "</tbody></table>"
    "</div>"

    "</body></html>"]))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        html (render result)]
    (clojure.java.io/make-parents out)
    (spit out html)
    (println "wrote" out)))
