(ns crude.facts-test
  (:require [clojure.test :refer [deftest is]]
            [crude.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest all-seeded-jurisdictions-have-an-h2s-idlh
  ;; every seeded upstream jurisdiction actually has a real H2S IDLH
  ;; threshold reported honestly here
  (doseq [iso3 ["JPN" "USA" "GBR" "NOR" "BRA"]]
    (is (some? (facts/idlh-ppm iso3)) (str iso3 " idlh-ppm"))
    (is (number? (facts/idlh-ppm iso3)) (str iso3 " idlh-ppm is numeric"))))

(deftest bra-has-a-spec-basis
  (is (some? (facts/spec-basis "BRA")))
  (is (string? (:provenance (facts/spec-basis "BRA"))))
  (is (= 4 (count (facts/evidence-checklist "BRA")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest unknown-jurisdiction-has-no-idlh
  (is (nil? (facts/idlh-ppm "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))
