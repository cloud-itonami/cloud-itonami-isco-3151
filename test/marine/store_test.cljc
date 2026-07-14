(ns marine.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [marine.store :as store]))

(deftest mem-store-vessel-lookup
  (testing "lookup registered vessel"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "MV Pacific" :imo "9123456"})]
      (is (= {:vessel-id "v1" :name "MV Pacific" :imo "9123456"}
             (store/vessel s "v1")))))
  (testing "unregistered vessel returns nil"
    (let [s (store/mem-store)]
      (is (nil? (store/vessel s "nonexistent"))))))

(deftest mem-store-equipment-lookup
  (testing "lookup registered equipment"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "MV Pacific"})
          _ (store/register-equipment! s {:vessel-id "v1" :equipment-id "e1" :name "Main Engine"})]
      (is (= {:vessel-id "v1" :equipment-id "e1" :name "Main Engine"}
             (store/equipment s "v1" "e1")))))
  (testing "unregistered equipment returns nil"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "MV Pacific"})]
      (is (nil? (store/equipment s "v1" "nonexistent"))))))

(deftest mem-store-commit-record
  (testing "commit-record persists record"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "MV Pacific"})
          record {:vessel-id "v1" :op :log-engine-reading :payload {:rpm 500}}]
      (store/commit-record! s record)
      (is (some #{record} (store/records-of s "v1")))))
  (testing "commit-record! requires vessel-id"
    (let [s (store/mem-store)]
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (store/commit-record! s {:op :foo :payload {}}))))))

(deftest mem-store-records
  (testing "records starts empty"
    (let [s (store/mem-store)]
      (is (empty? (store/records-of s "v1")))))
  (testing "multiple commits are persisted"
    (let [s (store/mem-store)
          _ (store/register-vessel! s {:vessel-id "v1" :name "MV Pacific"})
          r1 {:vessel-id "v1" :op :log-engine-reading :payload {:rpm 500}}
          r2 {:vessel-id "v1" :op :schedule-maintenance :payload {:date "2026-07-20"}}]
      (store/commit-record! s r1)
      (store/commit-record! s r2)
      (is (= 2 (count (store/records-of s "v1")))))))

(deftest mem-store-audit-ledger
  (testing "ledger starts empty"
    (let [s (store/mem-store)]
      (is (empty? (store/ledger s)))))
  (testing "append-ledger! persists entry"
    (let [s (store/mem-store)]
      (store/append-ledger! s {:disposition :commit :record {:op :foo}})
      (is (seq (store/ledger s))))))
