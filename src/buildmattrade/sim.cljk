(ns buildmattrade.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean potable-water-
  contact plumbing order through intake -> certification verification ->
  physical dispatch (escalate/approve/commit) -> invoice settlement
  (escalate/approve/commit), an ordinary construction-material order
  through the SAME lifecycle to prove type-gating, then shows HARD-hold
  scenarios: a jurisdiction with no spec-basis, a counterparty whose
  credit has not been cleared, an order with no contract-terms on file,
  a counterparty that has not passed sanctions screening, a potable-
  water-contact product missing its lead-free certification, a double
  dispatch, and a double invoice -- THEN the type-gating control pair
  that makes this vertical's own domain logic honest rather than a
  blanket rule:

    1. `:potable-water-contact?` type-gating -- a fully-certified
       potable-water-contact fixture (`bo-6`) dispatches CLEANLY
       end-to-end (verify -> dispatch -> invoice), proving the lead-free
       certification check is satisfiable, not merely a trap; `bo-1`
       (ordinary construction material, lumber) and `bo-8` (heating
       equipment, a gas furnace) both prove the check is a true NO-OP
       for non-potable-water-contact goods -- from TWO different
       categories ISIC 4663 itself names.

  Like every sibling actor's domain checks, this actor's checks
  (`credit-uncleared`, `contract-missing`, `lead-free-certification-
  missing`, `counterparty-sanctions-flag-unresolved`) are evaluated
  directly at `:delivery/dispatch` (sanctions also at `:invoice/settle`)
  rather than via a separate screening op -- a real dispatch decision
  validates counterparty credit, contract-on-file, lead-free
  certification and sanctions screening at the point of the act itself,
  not as a discrete pre-screening ceremony. Each check is still
  exercised directly and independently below, one order per HARD-hold
  scenario, following the SAME 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline `parksafety`'s
  ADR-2607071922 Decision 5 and every sibling since establish."
  (:require [langgraph.graph :as g]
            [buildmattrade.store :as store]
            [buildmattrade.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== order/intake bo-1 (USA lumber, ordinary construction material, clean) ==")
    (println (exec-op actor "t1" {:op :order/intake :subject "bo-1"
                                  :patch {:id "bo-1" :counterparty "Akita Building Materials Trading Co"}} operator))

    (println "== certification/verify bo-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :certification/verify :subject "bo-1"} operator))
    (println (approve! actor "t2"))

    (println "== delivery/dispatch bo-1 (always escalates -- :delivery/dispatch; construction material, no lead-free cert required) ==")
    (let [r (exec-op actor "t3" {:op :delivery/dispatch :subject "bo-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t3")))

    (println "== invoice/settle bo-1 (always escalates -- :invoice/settle) ==")
    (let [r (exec-op actor "t4" {:op :invoice/settle :subject "bo-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t4")))

    (println "== certification/verify bo-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :certification/verify :subject "bo-2"} operator))

    (println "== certification/verify bo-3 (escalates -- human approves; sets up the credit-uncleared test) ==")
    (println (exec-op actor "t6" {:op :certification/verify :subject "bo-3"} operator))
    (println (approve! actor "t6"))

    (println "== delivery/dispatch bo-3 (credit not cleared -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :delivery/dispatch :subject "bo-3"} operator))

    (println "== certification/verify bo-4 (escalates -- human approves; sets up the contract-missing test) ==")
    (println (exec-op actor "t8" {:op :certification/verify :subject "bo-4"} operator))
    (println (approve! actor "t8"))

    (println "== delivery/dispatch bo-4 (no contract-terms on file -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :delivery/dispatch :subject "bo-4"} operator))

    (println "== certification/verify bo-5 (escalates -- human approves; sets up the sanctions test) ==")
    (println (exec-op actor "t10" {:op :certification/verify :subject "bo-5"} operator))
    (println (approve! actor "t10"))

    (println "== delivery/dispatch bo-5 (sanctions screening not passed -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :delivery/dispatch :subject "bo-5"} operator))

    (println "== certification/verify bo-6 (potable-water-contact kitchen faucet, lead-free cert on file -- escalates -- human approves) ==")
    (println (exec-op actor "t12" {:op :certification/verify :subject "bo-6"} operator))
    (println (approve! actor "t12"))

    (println "== delivery/dispatch bo-6 (potable-water-contact product WITH a valid lead-free certification -> dispatches cleanly, escalates on actuation only) ==")
    (let [r (exec-op actor "t13" {:op :delivery/dispatch :subject "bo-6"} operator)]
      (println r)
      (println "-- human trading supervisor approves (lead-free-certification check is satisfiable, not a trap) --")
      (println (approve! actor "t13")))

    (println "== invoice/settle bo-6 (always escalates -- human approves) ==")
    (let [r (exec-op actor "t14" {:op :invoice/settle :subject "bo-6"} operator)]
      (println r)
      (println (approve! actor "t14")))

    (println "== certification/verify bo-7 (potable-water-contact copper pipe fitting, lab-tested but NO cert on file -- escalates -- human approves) ==")
    (println (exec-op actor "t15" {:op :certification/verify :subject "bo-7"} operator))
    (println (approve! actor "t15"))

    (println "== delivery/dispatch bo-7 (potable-water-contact, lead-free certificate not on file -> HARD hold) ==")
    (println (exec-op actor "t16" {:op :delivery/dispatch :subject "bo-7"} operator))

    (println "== certification/verify bo-8 (heating equipment -- gas furnace, NOT potable-water-contact -- escalates -- human approves) ==")
    (println (exec-op actor "t17" {:op :certification/verify :subject "bo-8"} operator))
    (println (approve! actor "t17"))

    (println "== delivery/dispatch bo-8 (heating equipment -> dispatches cleanly, NOT gated by the lead-free-certification check either -- second type-gating control) ==")
    (let [r (exec-op actor "t18" {:op :delivery/dispatch :subject "bo-8"} operator)]
      (println r)
      (println "-- human trading supervisor approves (heating equipment is out of scope for the potable-water check, by design -- see docs/adr/0001-architecture.md Decision 5) --")
      (println (approve! actor "t18")))

    (println "== delivery/dispatch bo-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec-op actor "t19" {:op :delivery/dispatch :subject "bo-1"} operator))

    (println "== invoice/settle bo-1 AGAIN (double-invoice -> HARD hold) ==")
    (println (exec-op actor "t20" {:op :invoice/settle :subject "bo-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft building-materials-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft building-materials-invoice records ==")
    (doseq [r (store/invoice-history db)] (println r))))
