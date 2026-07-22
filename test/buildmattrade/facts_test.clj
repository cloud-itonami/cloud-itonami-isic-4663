(ns buildmattrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [buildmattrade.facts :as facts]))

(deftest usa-has-a-spec-basis
  (is (some? (facts/spec-basis "USA")))
  (is (string? (:provenance (facts/spec-basis "USA")))))

(deftest bra-has-a-spec-basis-with-the-same-shape-as-usa-jpn
  ;; the third seeded jurisdiction (Brazil, added 2026-07-23) must have
  ;; the SAME map shape as USA/JPN -- :name/:owner-authority/:legal-basis/
  ;; :provenance/:required-evidence -- and a real (non-fabricated)
  ;; official-source provenance, following the same catalog discipline
  (let [bra (facts/spec-basis "BRA")]
    (is (some? bra))
    (is (= "BRA" (:name bra)))
    (is (string? (:owner-authority bra)))
    (is (string? (:legal-basis bra)))
    (is (string? (:provenance bra)))
    (is (re-find #"inmetro" (:provenance bra)) "provenance cites INMETRO, the verified official source")
    (is (= ["credit-clearance record"
            "contract/PO"
            "sanctions-screening (OFAC/equivalent) record"]
           (:required-evidence bra)))))

(deftest all-seeded-jurisdictions-have-required-evidence
  ;; every seeded building-materials-wholesale jurisdiction actually has
  ;; a real required-evidence set reported honestly here
  (doseq [iso3 ["USA" "JPN" "BRA"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["USA" "ATL" "JPN" "BRA"])]
    (is (= 3 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["BRA" "JPN" "USA"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "USA")]
    (is (facts/required-evidence-satisfied? "USA" all))
    (is (not (facts/required-evidence-satisfied? "USA" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest catalog-does-not-fold-lead-free-certification-into-the-checklist
  ;; the generic evidence checklist stays exactly 3 items (credit,
  ;; contract/PO, sanctions) for every seeded jurisdiction -- a
  ;; lead-free-certification is never a checklist entry, it is its own
  ;; dedicated governor check (see buildmattrade.governor namespace
  ;; docstring)
  (doseq [iso3 ["USA" "JPN" "BRA"]]
    (is (= 3 (count (facts/evidence-checklist iso3))) (str iso3 " checklist length"))))
