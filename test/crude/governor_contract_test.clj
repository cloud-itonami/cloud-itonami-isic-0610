(ns crude.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    CrudeAdvisor never lifts crude from a well or settles production
    the Well Safety Governor would reject, `:well/lift`/
    `:production/settle` NEVER auto-commit at any phase, `:well/intake`
    (no direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [crude.store :as store]
            [crude.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :production-superintendent :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through reservoir assess -> approve, leaving an
  assessment on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :reservoir/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :well/intake :subject "well-1"
                   :patch {:id "well-1" :operator "Akita Petroleum Co"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Akita Petroleum Co" (:operator (store/well db "well-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest reservoir-assess-always-needs-approval
  (testing "reservoir assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :reservoir/assess :subject "well-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "well-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a reservoir/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :reservoir/assess :subject "well-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "well-2")) "no assessment written"))))

(deftest well-lift-without-assessment-is-held
  (testing "well/lift before any reservoir assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :well/lift :subject "well-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest reservoir-pressure-out-of-range-is-held-and-unoverridable
  (testing "a measured reservoir pressure outside the safe window -> HOLD, and never reaches request-approval -- a genuinely new sub-category (the aerospace two-sided-tolerance discipline applied to subsurface pressure)"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "well-3")
          res (exec-op actor "t5" {:op :well/lift :subject "well-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:reservoir-pressure-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/lift-history db))))))

(deftest well-integrity-annular-pressure-excessive-is-held-and-unoverridable
  (testing "an annular pressure above MAASP -> HOLD, and never reaches request-approval -- a genuinely new sub-category (the fabrication measured-ratio-vs-rated-limit discipline), a true blowout precursor evaluated before any surface flow signal"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre" "well-4")
          res (exec-op actor "t6" {:op :well/lift :subject "well-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:well-integrity-annular-pressure-excessive} (-> (store/ledger db) last :basis)))
      (is (empty? (store/lift-history db))))))

(deftest water-cut-excessive-is-held-and-unoverridable
  (testing "a water cut above the BSW limit -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "well-5")
          res (exec-op actor "t7" {:op :well/lift :subject "well-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:water-cut-excessive} (-> (store/ledger db) last :basis)))
      (is (empty? (store/lift-history db))))))

(deftest h2s-toxic-threshold-is-held-and-unoverridable
  (testing "an H2S concentration above the jurisdiction's IDLH -> HOLD, and never reaches request-approval -- sour-service toxicity (see this actor's governor ns docstring / the full accumulated ADR-0001 chain: parksafety's ADR-2607071922 Decision 5 through every sibling's own unconditional-evaluation grounding)"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "well-6")
          res (exec-op actor "t8" {:op :well/lift :subject "well-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:h2s-toxic-threshold} (-> (store/ledger db) last :basis)))
      (is (empty? (store/lift-history db))))))

(deftest integrity-flag-unresolved-is-held-and-unoverridable
  (testing "an unresolved integrity flag -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "well-7")
          res (exec-op actor "t9" {:op :well/lift :subject "well-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:integrity-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/lift-history db))))))

(deftest well-lift-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, in-window, annular<MAASP, water-cut-ok, h2s<IDLH, no-integrity-flag well still ALWAYS interrupts for human approval -- :well/lift is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "well-1")
          r1 (exec-op actor "t10" {:op :well/lift :subject "well-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, lift record drafted"
        (let [r2 (approve! actor "t10")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:crude-lifted? (store/well db "well-1"))))
          (is (= 1 (count (store/lift-history db))) "one draft lift record"))))))

(deftest production-settle-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, already-lifted well still ALWAYS interrupts for human approval -- :production/settle is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t11pre" "well-1")
          _ (exec-op actor "t11lift" {:op :well/lift :subject "well-1"} operator)
          _ (approve! actor "t11lift")
          r1 (exec-op actor "t11" {:op :production/settle :subject "well-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, settlement record drafted"
        (let [r2 (approve! actor "t11")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:production-settled? (store/well db "well-1"))))
          (is (= 1 (count (store/production-history db))) "one draft settlement record"))))))

(deftest well-lift-double-lift-is-held
  (testing "lifting the same well twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t12pre" "well-1")
          _ (exec-op actor "t12a" {:op :well/lift :subject "well-1"} operator)
          _ (approve! actor "t12a")
          res (exec-op actor "t12" {:op :well/lift :subject "well-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-lifted} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/lift-history db))) "still only the one earlier lift"))))

(deftest production-settle-double-settlement-is-held
  (testing "settling the same well's production twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t13pre" "well-1")
          _ (exec-op actor "t13lift" {:op :well/lift :subject "well-1"} operator)
          _ (approve! actor "t13lift")
          _ (exec-op actor "t13a" {:op :production/settle :subject "well-1"} operator)
          _ (approve! actor "t13a")
          res (exec-op actor "t13" {:op :production/settle :subject "well-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-settled} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/production-history db))) "still only the one earlier settlement"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :well/intake :subject "well-1"
                          :patch {:id "well-1" :operator "Akita Petroleum Co"}} operator)
      (exec-op actor "b" {:op :reservoir/assess :subject "well-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
