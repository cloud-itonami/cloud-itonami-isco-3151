(ns marine.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [marine.actor :as actor]
            [marine.store :as store]
            [marine.advisor :as advisor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-vessel! st {:vessel-id "vessel-1" :name "MV Pacific"
                               :imo-number "9123456"})
    (store/register-equipment! st {:vessel-id "vessel-1"
                                  :equipment-id "engine-1"
                                  :name "Main Diesel Engine"
                                  :type "Wärtsilä 8L32"})
    st))

(deftest commits-a-routine-engine-reading
  (let [st (fresh-store)
        graph (actor/build-graph {:store st :advisor (advisor/mock-advisor)})
        request {:vessel-id "vessel-1"
                 :op :log-engine-reading
                 :equipment-id "engine-1"
                 :reading {:rpm 500 :fuel-pressure 250 :oil-temp 45}}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "vessel-1"))))))

(deftest holds-operation-on-unregistered-vessel
  (let [st (fresh-store)
        graph (actor/build-graph {:store st :advisor (advisor/mock-advisor)})
        request {:vessel-id "vessel-999"
                 :op :log-engine-reading
                 :equipment-id "engine-1"
                 :reading {:rpm 500}}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "vessel-999")))))

(deftest holds-operation-on-unregistered-equipment
  (let [st (fresh-store)
        graph (actor/build-graph {:store st :advisor (advisor/mock-advisor)})
        request {:vessel-id "vessel-1"
                 :op :log-engine-reading
                 :equipment-id "engine-999"
                 :reading {:rpm 500}}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-and-escalates-mechanical-fault-flagging
  (let [st (fresh-store)
        graph (actor/build-graph {:store st :advisor (advisor/basic-advisor)})
        request {:vessel-id "vessel-1"
                 :op :flag-mechanical-fault
                 :equipment-id "engine-1"
                 :fault-description "High crankcase pressure detected"
                 :severity :high}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "vessel-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "vessel-1")))))))

(deftest commits-fuel-bunkering-coordination
  (let [st (fresh-store)
        graph (actor/build-graph {:store st :advisor (advisor/basic-advisor)})
        request {:vessel-id "vessel-1"
                 :op :coordinate-fuel-bunkering
                 :fuel-quantity 50000
                 :fuel-type "IFO 380"
                 :port "Singapore"}
        result (actor/run-request! graph request {} "thread-5")]
    (is (or (= :done (:status result))
            (= :interrupted (:status result))))
    (is (some? (get-in result [:state :proposal])))))

(deftest rejects-engine-control-command
  (let [st (fresh-store)
        graph (actor/build-graph {:store st :advisor (advisor/mock-advisor)})
        request {:vessel-id "vessel-1"
                 :op :engine-control
                 :equipment-id "engine-1"
                 :command :throttle}
        result (actor/run-request! graph request {} "thread-6")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "vessel-1")))))
