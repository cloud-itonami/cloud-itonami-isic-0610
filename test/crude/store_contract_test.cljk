(ns crude.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [crude.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/well s "well-1"))))
      (is (= "Akita Petroleum Co" (:operator (store/well s "well-1"))))
      (is (= 35 (:api-gravity (store/well s "well-1"))))
      (is (= "ATL" (:jurisdiction (store/well s "well-2"))))
      (is (= 50.0 (:reservoir-pressure-mpa-actual (store/well s "well-3"))) "well-3 reservoir out of range")
      (is (= 30.0 (:annular-pressure-mpa (store/well s "well-4"))) "well-4 annular above MAASP")
      (is (= 8.0 (:water-cut-bsw (store/well s "well-5"))) "well-5 water cut excessive")
      (is (= 100 (:h2s-ppm (store/well s "well-6"))) "well-6 h2s toxic")
      (is (true? (:integrity-flag-raised? (store/well s "well-7"))))
      (is (false? (:integrity-flag-resolved? (store/well s "well-7"))))
      (is (false? (:crude-lifted? (store/well s "well-1"))))
      (is (false? (:production-settled? (store/well s "well-1"))))
      (is (= ["well-1" "well-2" "well-3" "well-4" "well-5" "well-6" "well-7"]
             (mapv :id (store/all-wells s))))
      (is (nil? (store/assessment-of s "well-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/lift-history s)))
      (is (= [] (store/production-history s)))
      (is (zero? (store/next-lift-sequence s "JPN")))
      (is (zero? (store/next-production-sequence s "JPN")))
      (is (false? (store/well-already-lifted? s "well-1")))
      (is (false? (store/well-already-settled? s "well-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :well/upsert
                                 :value {:id "well-1" :operator "Akita Petroleum Co"}})
        (is (= "Akita Petroleum Co" (:operator (store/well s "well-1"))))
        (is (= "JPN" (:jurisdiction (store/well s "well-1"))) "unrelated field preserved"))
      (testing "assessment payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["well-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "well-1"))))
      (testing "well lift drafts a record and advances the lift sequence"
        (store/commit-record! s {:effect :well/mark-lifted :path ["well-1"]})
        (is (= "JPN-LIFT-000000" (get (first (store/lift-history s)) "record_id")))
        (is (= "well-lift-draft" (get (first (store/lift-history s)) "kind")))
        (is (true? (:crude-lifted? (store/well s "well-1"))))
        (is (= 1 (count (store/lift-history s))))
        (is (= 1 (store/next-lift-sequence s "JPN")))
        (is (true? (store/well-already-lifted? s "well-1"))))
      (testing "production settlement drafts a record and advances the production sequence"
        (store/commit-record! s {:effect :well/mark-settled :path ["well-1"]})
        (is (= "JPN-PROD-000000" (get (first (store/production-history s)) "record_id")))
        (is (= "production-settlement-draft" (get (first (store/production-history s)) "kind")))
        (is (true? (:production-settled? (store/well s "well-1"))))
        (is (= 1 (count (store/production-history s))))
        (is (= 1 (store/next-production-sequence s "JPN")))
        (is (true? (store/well-already-settled? s "well-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/well s "nope")))
    (is (= [] (store/all-wells s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/lift-history s)))
    (is (= [] (store/production-history s)))
    (is (zero? (store/next-lift-sequence s "JPN")))
    (is (zero? (store/next-production-sequence s "JPN")))
    (store/with-wells s {"x" {:id "x" :field-name "f" :well-name "x-1" :operator "c"
                              :api-gravity 35 :sulfur-percent 1.5 :h2s-ppm 5
                              :water-cut-bsw 0.5 :water-cut-max 5.0
                              :reservoir-pressure-mpa-actual 30.0
                              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
                              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
                              :flow-rate-bbl-day 5000
                              :integrity-flag-raised? false :integrity-flag-resolved? false
                              :crude-lifted? false :production-settled? false
                              :jurisdiction "JPN" :status :intake}})
    (is (= "c" (:operator (store/well s "x"))))))
