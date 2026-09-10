(ns marine.store
  "In-memory store for vessel/equipment records and operational ledger.
  Implements the Store protocol: vessel registration, equipment records,
  maintenance schedules, and append-only audit ledger.")

(defprotocol Store
  "Persistent store for marine engineering records and audit trail."
  (vessel [this vessel-id] "Fetch registered vessel record.")
  (register-vessel! [this vessel-record] "Register a vessel.")
  (equipment [this vessel-id equipment-id] "Fetch equipment record for a vessel.")
  (register-equipment! [this equipment-record] "Register equipment on a vessel.")
  (commit-record! [this record] "Commit an operation record.")
  (records-of [this vessel-id] "Fetch all records for a vessel.")
  (append-ledger! [this entry] "Append audit ledger entry.")
  (ledger [this] "Fetch the append-only audit ledger."))

(deftype MemStore [vessels equipment records ledger]
  Store
  (vessel [_ vessel-id]
    (get @vessels vessel-id))
  (register-vessel! [_ vessel-record]
    (swap! vessels assoc (:vessel-id vessel-record) vessel-record)
    vessel-record)
  (equipment [_ vessel-id equipment-id]
    (get-in @equipment [vessel-id equipment-id]))
  (register-equipment! [_ equipment-record]
    (let [vid (:vessel-id equipment-record)]
      (swap! equipment assoc-in [vid (:equipment-id equipment-record)] equipment-record)
      equipment-record))
  (commit-record! [_ record]
    (when-not (:vessel-id record)
      (throw (ex-info "commit-record! requires :vessel-id" {:record record})))
    (swap! records update (:vessel-id record) (fnil conj []) record)
    record)
  (records-of [_ vessel-id]
    (get @records vessel-id []))
  (append-ledger! [_ entry]
    (swap! ledger conj entry)
    entry)
  (ledger [_]
    @ledger))

(defn mem-store
  "Create an in-memory store for testing and local operation."
  []
  (MemStore. (atom {}) (atom {}) (atom {}) (atom [])))
