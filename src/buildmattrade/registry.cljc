(ns buildmattrade.registry
  "Pure-function building-materials-dispatch + building-materials-
  invoice record construction -- an append-only building-materials-
  wholesale book-of-record draft.

  Like the fuel-wholesale / ag-machinery-wholesale / household-goods-
  wholesale siblings' own registries, this building-materials-wholesale
  vertical's Potable Water Safety Governor needs NO registry range-
  check functions at all: its domain checks (credit-uncleared,
  contract-missing, lead-free-certification-missing, counterparty-
  sanctions-flag-unresolved) are direct entity boolean reads in
  `buildmattrade.governor`, off dedicated `:credit-cleared?` /
  `:contract-terms` / `:lead-free-certificate-on-file?` /
  `:sanctions-screened?` facts on the `building-order` record. So this
  namespace is RECORD CONSTRUCTION ONLY -- no pure range checks to host
  here.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a building-materials-dispatch or
  building-materials-invoice record -- every operator/jurisdiction
  assigns its own reference format. This namespace does NOT invent one
  beyond a jurisdiction-scoped sequence number; it validates the
  record's required fields, the same honest, non-fabricating discipline
  `buildmattrade.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real yard/ERP/billing system. It builds the RECORD an
  operator would keep, not the act of dispatching real building
  materials from the wholesale yard/distribution center or settling a
  real invoice itself (that is `buildmattrade.operation`'s
  `:delivery/dispatch`/`:invoice/settle`, always human-gated -- see
  README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-dispatch-record
  "Validate + construct the BUILDING-MATERIALS-DISPATCH registration
  DRAFT -- the operator's own legal act of dispatching real building
  materials from the wholesale yard/distribution center to a
  counterparty. Pure function -- does not touch any real yard/ERP
  system; it builds the RECORD an operator would keep.
  `buildmattrade.governor` independently re-verifies the counterparty's
  credit-clearance, contract-on-file, lead-free-certification (when
  applicable) and evidence-completeness ground truth, and blocks a
  double-dispatch of the same building-order, before this is ever
  allowed to commit."
  [building-order-id jurisdiction sequence]
  (when-not (and building-order-id (not= building-order-id ""))
    (throw (ex-info "building-materials-dispatch: building_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "building-materials-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "building-materials-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DISPATCH-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "building-materials-dispatch-draft"
                "building_order_id" building-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "BuildingMaterialsDispatch" dispatch-number dispatch-number)}))

(defn register-invoice-record
  "Validate + construct the BUILDING-MATERIALS-INVOICE registration
  DRAFT -- the operator's own legal act of settling a real building-
  materials invoice (the money side of a wholesale trade, custody/
  financial transfer). Pure function -- does not touch any real billing
  or accounts-receivable system; it builds the RECORD an operator would
  keep. `buildmattrade.governor` independently re-verifies the
  sanctions-screening and evidence-completeness ground truth, and blocks
  a double-invoice of the same building-order, before this is ever
  allowed to commit."
  [building-order-id jurisdiction sequence]
  (when-not (and building-order-id (not= building-order-id ""))
    (throw (ex-info "building-materials-invoice: building_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "building-materials-invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "building-materials-invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper-case jurisdiction) "-INVOICE-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "building-materials-invoice-draft"
                "building_order_id" building-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "BuildingMaterialsInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
