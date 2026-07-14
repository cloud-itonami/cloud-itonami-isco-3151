(ns marine.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [marine.governor :as gov]
            [marine.store :as store]))

(deftest hard-violations-unregistered-vessel
  (testing "unregistered vessel triggers hard violation"
    (let [proposal {:op :log-engine-reading :effect :propose :vessel-id :nonexistent}
          request {}
          s (store/mem-store)
          verdict (gov/check request nil proposal s)]
      (is (:hard? verdict))
      (is (not (:ok? verdict)))
      (is (seq (:violations verdict))))))

(deftest hard-violations-unregistered-equipment
  (testing "unregistered equipment triggers hard violation"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :log-engine-reading :effect :propose :vessel-id "v1" :equipment-id "e999"}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (:hard? verdict))
      (is (not (:ok? verdict))))))

(deftest hard-violations-non-propose-effect
  (testing "non-:propose effect triggers hard violation"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :log-engine-reading :effect :commit :vessel-id "v1"}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (:hard? verdict))
      (is (not (:ok? verdict))))))

(deftest hard-violations-engine-control
  (testing "engine-control is forbidden"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :engine-control :effect :propose :vessel-id "v1"}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (:hard? verdict))))
  (testing "throttle is forbidden"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :throttle :effect :propose :vessel-id "v1"}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (:hard? verdict))))
  (testing "fuel-cutoff is forbidden"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :fuel-cutoff :effect :propose :vessel-id "v1"}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (:hard? verdict)))))

(deftest escalation-mechanical-fault
  (testing "flag-mechanical-fault always escalates"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :flag-mechanical-fault :effect :propose :vessel-id "v1" :confidence 0.95}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (not (:ok? verdict))))))

(deftest escalation-low-confidence
  (testing "low confidence triggers escalation"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :log-engine-reading :effect :propose :vessel-id "v1" :confidence 0.5}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (not (:ok? verdict))))))

(deftest ok-proposal
  (testing "valid proposal with high confidence is ok"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :log-engine-reading :effect :propose :vessel-id "v1" :confidence 0.85}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict)))
      (is (:ok? verdict)))))

(deftest ok-schedule-maintenance
  (testing "schedule-maintenance proposal is ok with high confidence"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :schedule-maintenance :effect :propose :vessel-id "v1" :confidence 0.8}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (:ok? verdict)))))

(deftest ok-coordinate-fuel-bunkering
  (testing "coordinate-fuel-bunkering proposal is ok with high confidence"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "Ship 1"})
          proposal {:op :coordinate-fuel-bunkering :effect :propose :vessel-id "v1" :confidence 0.9}
          request {}
          verdict (gov/check request nil proposal s)]
      (is (:ok? verdict)))))
