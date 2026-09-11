(ns buildmattrade.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    BuildMatTradeAdvisor never dispatches building materials to a
    counterparty or settles an invoice the Potable Water Safety
    Governor would reject, `:delivery/dispatch`/`:invoice/settle` NEVER
    auto-commit at any phase, `:order/intake` (no direct capital risk)
    MAY auto-commit when clean, and every decision (commit OR hold)
    leaves exactly one ledger fact.

  PLUS this vertical's own defining proof: `lead-free-certification-
  missing` is genuinely TYPE-GATED on `:potable-water-contact?` (an
  ordinary construction material is a true NO-OP; heating equipment --
  a SECOND, independently-motivated non-potable-water category ISIC
  4663 itself names -- is ALSO a true NO-OP; a certified potable-water-
  contact product dispatches cleanly; an UNcertified potable-water-
  contact product HARD-holds) -- not a blanket certificate
  requirement."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [buildmattrade.store :as store]
            [buildmattrade.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through certification verify -> approve, leaving a
  certification assessment on file. Uses distinct thread-ids per call
  site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :certification/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order/intake :subject "bo-1"
                   :patch {:id "bo-1" :counterparty "Akita Building Materials Trading Co"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Akita Building Materials Trading Co" (:counterparty (store/building-order db "bo-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest certification-verify-always-needs-approval
  (testing "certification verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :certification/verify :subject "bo-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "bo-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a certification/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :certification/verify :subject "bo-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "bo-2")) "no assessment written"))))

(deftest dispatch-without-assessment-is-held
  (testing "delivery/dispatch before any certification verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :delivery/dispatch :subject "bo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest credit-uncleared-is-held-and-unoverridable
  (testing "a counterparty whose credit has not been cleared -> HOLD, and never reaches request-approval -- the leasing collateral-coverage discipline applied to counterparty credit"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "bo-3")
          res (exec-op actor "t5" {:op :delivery/dispatch :subject "bo-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credit-uncleared} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest contract-missing-is-held-and-unoverridable
  (testing "an order with no contract-terms on file -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "bo-4")
          res (exec-op actor "t6" {:op :delivery/dispatch :subject "bo-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contract-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest counterparty-sanctions-flag-unresolved-is-held-and-unoverridable
  (testing "a counterparty that has not passed OFAC / equivalent sanctions screening -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "bo-5")
          res (exec-op actor "t7" {:op :delivery/dispatch :subject "bo-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest ordinary-construction-material-is-a-no-op-for-the-lead-free-certification-check
  (testing "bo-1 (ordinary construction material -- lumber, NEITHER evidentiary sub-fact on file) dispatches CLEANLY -- proving genuine type-gating, not a blanket certification requirement"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "bo-1")
          r1 (exec-op actor "t8" {:op :delivery/dispatch :subject "bo-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval on actuation grounds ONLY -- no HARD hold")
      (let [r2 (approve! actor "t8")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/building-order db "bo-1"))))
        (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record")))))

(deftest heating-equipment-is-also-a-no-op-for-the-lead-free-certification-check
  (testing "bo-8 (heating equipment -- a gas furnace, NOT potable-water-contact) dispatches CLEANLY -- a SECOND, independently-motivated type-gating control case (ISIC 4663 names both construction materials AND heating equipment; neither triggers this check)"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "bo-8")
          r1 (exec-op actor "t9" {:op :delivery/dispatch :subject "bo-8"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval on actuation grounds ONLY -- no HARD hold")
      (let [r2 (approve! actor "t9")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/building-order db "bo-8"))))
        (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record")))))

(deftest potable-water-contact-product-with-a-valid-lead-free-certification-dispatches-cleanly
  (testing "bo-6 (potable-water-contact kitchen faucet, BOTH lead-content-tested AND lead-free-certificate-on-file true) dispatches CLEANLY -- the check is satisfiable, not a trap"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "bo-6")
          r1 (exec-op actor "t10" {:op :delivery/dispatch :subject "bo-6"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval on actuation grounds ONLY -- no HARD hold")
      (let [r2 (approve! actor "t10")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/building-order db "bo-6"))))
        (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record")))))

(deftest lead-free-certification-missing-is-held-and-unoverridable
  (testing "bo-7 (potable-water-contact copper pipe fitting, lab-tested but NO lead-free certificate actually on file) -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "bo-7")
          res (exec-op actor "t11" {:op :delivery/dispatch :subject "bo-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:lead-free-certification-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest delivery-dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, credit-cleared, contract-on-file, sanctions-screened order still ALWAYS interrupts for human approval -- :delivery/dispatch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "bo-1")
          r1 (exec-op actor "t12" {:op :delivery/dispatch :subject "bo-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t12")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/building-order db "bo-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest invoice-settle-always-escalates-then-human-decides
  (testing "a clean, fully-verified, already-dispatched order still ALWAYS interrupts for human approval -- :invoice/settle is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "bo-1")
          _ (exec-op actor "t13dispatch" {:op :delivery/dispatch :subject "bo-1"} operator)
          _ (approve! actor "t13dispatch")
          r1 (exec-op actor "t13" {:op :invoice/settle :subject "bo-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, invoice record drafted"
        (let [r2 (approve! actor "t13")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:invoiced? (store/building-order db "bo-1"))))
          (is (= 1 (count (store/invoice-history db))) "one draft invoice record"))))))

(deftest delivery-dispatch-double-dispatch-is-held
  (testing "dispatching the same building-order twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t14pre" "bo-1")
          _ (exec-op actor "t14a" {:op :delivery/dispatch :subject "bo-1"} operator)
          _ (approve! actor "t14a")
          res (exec-op actor "t14" {:op :delivery/dispatch :subject "bo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest invoice-settle-double-invoice-is-held
  (testing "settling the same building-order's invoice twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t15pre" "bo-1")
          _ (exec-op actor "t15dispatch" {:op :delivery/dispatch :subject "bo-1"} operator)
          _ (approve! actor "t15dispatch")
          _ (exec-op actor "t15a" {:op :invoice/settle :subject "bo-1"} operator)
          _ (approve! actor "t15a")
          res (exec-op actor "t15" {:op :invoice/settle :subject "bo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-invoiced} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/invoice-history db))) "still only the one earlier invoice"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order/intake :subject "bo-1"
                          :patch {:id "bo-1" :counterparty "Akita Building Materials Trading Co"}} operator)
      (exec-op actor "b" {:op :certification/verify :subject "bo-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
