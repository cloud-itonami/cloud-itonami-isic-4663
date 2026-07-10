(ns buildmattrade.store
  "SSoT for the building-materials-wholesale actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/buildmattrade/store_contract_test.clj), which is the whole
  point: the actor, the Potable Water Safety Governor and the audit
  ledger never know which SSoT they run on.

  Like the fuel-wholesale / ag-machinery-wholesale / household-goods-
  wholesale siblings' own order entities, this vertical's `dispatch` and
  `settle` actuation events apply SEQUENTIALLY to the SAME
  `building-order` -- physical dispatch happens first (materials leave
  the wholesale yard/distribution center), invoice settlement happens
  later, on the same order record. This matches the sequential
  dual-actuation shape, with dedicated double-actuation-guard booleans
  (`:dispatched?`/`:invoiced?`, never a `:status` value).

  The `building-order` record carries a TYPE gate, `:potable-water-
  contact?` -- is this SKU a pipe, pipe fitting, plumbing fitting or
  fixture intended to convey or dispense water for human consumption,
  which triggers the Reduction of Lead in Drinking Water Act /
  NSF/ANSI/CAN 372+61 lead-free-certification regime? Together with its
  two evidentiary sub-facts, `:lead-content-tested?` and
  `:lead-free-certificate-on-file?`, this is a PRE-SHIPMENT
  certification concern -- evaluated once, at `:delivery/dispatch`, and
  (unlike the household-goods sibling's own post-hoc active-recall flag)
  deliberately NOT re-checked at `:invoice/settle` -- see
  `buildmattrade.governor` namespace docstring for the full reasoning.

  The ledger stays append-only on every backend: 'which building-order
  was verified for a jurisdiction with no official spec-basis, which
  counterparty had credit-uncleared / no contract / a missing lead-free
  certification / an unresolved sanctions-screening flag, which order
  was dispatched, which invoice was settled, on what jurisdictional
  basis, approved by whom' is always a query over an immutable log --
  the audit trail a regulator, a counterparty, or an operator trusting a
  building-materials-wholesale actor needs, and the evidence an operator
  needs if a dispatch or an invoice is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [buildmattrade.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (building-order [s id])
  (all-building-orders [s])
  (assessment-of [s building-order-id] "committed certification assessment, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only building-materials-dispatch history (buildmattrade.registry drafts)")
  (invoice-history [s] "the append-only building-materials-invoice history (buildmattrade.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-invoice-sequence [s jurisdiction] "next invoice-number sequence for a jurisdiction")
  (building-order-already-dispatched? [s building-order-id] "has these materials already been dispatched?")
  (building-order-already-invoiced? [s building-order-id] "has this order's invoice already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-building-orders [s building-orders] "replace/seed the building-order directory (map id->building-order)"))

;; ----------------------------- demo data -----------------------------

(defn- base-order
  "The neutral, clean building-order shape (every field in its safe
  state), so each demo order below isolates exactly ONE failure mode by
  overriding a single field. The base shape is an ORDINARY construction
  material (dimensional lumber -- not potable-water-contact) -- the
  lead-free-certification facts are their true no-op defaults, proving
  the common case carries no certification overhead at all."
  [overrides]
  (merge {:id "bo-1" :order-id "BO-2026-0001" :product-category :lumber
          :sku "BM-SKU-10001"
          :counterparty "Akita Building Materials Trading Co"
          :price 6800.00 :contract-terms "FOB wholesale yard, net 30 days"
          :credit-cleared? true :sanctions-screened? true
          :potable-water-contact? false
          :lead-content-tested? false
          :lead-free-certificate-on-file? false
          :dispatched? false :invoiced? false
          :jurisdiction "USA" :status :intake
          :dispatch-number nil :invoice-number nil}
         overrides))

(defn demo-data
  "A small, self-contained building-order set covering both actuation
  lifecycles (dispatch, invoice settlement), the Potable Water Safety
  Governor's own generic checks, AND -- the defining proof of this
  vertical -- the type-gated lead-free-certification check (`bo-6`/
  `bo-7`), with TWO independent non-potable-water control cases
  (`bo-1`/`bo-8`) drawn from two DIFFERENT categories ISIC 4663 itself
  names (construction materials vs. heating equipment):

    - `bo-1` (ordinary construction material, dimensional lumber, no
      potable-water-contact overhead) dispatches CLEANLY -- proving a
      general construction material is NEVER blocked by the lead-free-
      certification check, even with neither evidentiary sub-fact on
      file.
    - `bo-6` (a potable-water-contact product, a kitchen faucet, WITH
      both `:lead-content-tested?` and
      `:lead-free-certificate-on-file?` true) dispatches CLEANLY --
      proving a fully-certified potable-water-contact product is not
      penalized merely for being one.
    - `bo-7` (a potable-water-contact product, a copper pipe fitting,
      lab-tested but with NO lead-free certificate actually on file --
      `:lead-content-tested?` true, `:lead-free-certificate-on-file?`
      false) HARD-holds on `:lead-free-certification-missing` --
      proving the fold requires the ACTUAL certificate on file, not
      merely that the underlying NSF/372 lab test was run (a realistic
      gap: testing often completes before the NSF/61-scope certificate
      paperwork itself is finalized).
    - `bo-8` (heating equipment, a gas furnace, NOT potable-water-
      contact) dispatches CLEANLY -- a SECOND, independently-motivated
      no-op control case: heating equipment is explicitly named in ISIC
      4663's own scope, and this build deliberately did NOT add a
      dedicated heating-equipment safety-certification check in this R0
      (see `buildmattrade.governor` namespace docstring) -- `bo-8`
      proves the lead-free-certification check correctly does not
      overreach into heating equipment either."
  []
  {:building-orders
   (into {}
         (for [o [(base-order {:id "bo-1" :order-id "BO-2026-0001"})
                  (base-order {:id "bo-2" :order-id "BO-2026-0002"
                               :product-category :hardware
                               :sku "BM-SKU-10002"
                               :counterparty "Atlantis Hardware Imports Ltd"
                               :jurisdiction "ATL"})
                  (base-order {:id "bo-3" :order-id "BO-2026-0003"
                               :product-category :drywall
                               :sku "BM-SKU-10003"
                               :counterparty "Cedar Building Supply Distributors"
                               :credit-cleared? false})
                  (base-order {:id "bo-4" :order-id "BO-2026-0004"
                               :product-category :hardware
                               :sku "BM-SKU-10004"
                               :counterparty "Delta Fastener Wholesalers BV"
                               :contract-terms nil})
                  (base-order {:id "bo-5" :order-id "BO-2026-0005"
                               :product-category :lumber
                               :sku "BM-SKU-10005"
                               :counterparty "Eagle Timber Traders SA"
                               :sanctions-screened? false})
                  (base-order {:id "bo-6" :order-id "BO-2026-0006"
                               :product-category :plumbing-fixture
                               :sku "BM-SKU-10006"
                               :counterparty "Fenwick Plumbing Fixtures Wholesalers Inc"
                               :potable-water-contact? true
                               :lead-content-tested? true
                               :lead-free-certificate-on-file? true})
                  (base-order {:id "bo-7" :order-id "BO-2026-0007"
                               :product-category :pipe-fitting
                               :sku "BM-SKU-10007"
                               :counterparty "Granger Pipe & Fitting KK"
                               :potable-water-contact? true
                               :lead-content-tested? true
                               :lead-free-certificate-on-file? false})
                  (base-order {:id "bo-8" :order-id "BO-2026-0008"
                               :product-category :heating-equipment
                               :sku "BM-SKU-10008"
                               :counterparty "Harrow Heating Equipment Traders Co"})]]
           [(:id o) o]))})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-order!
  "Backend-agnostic `:order/mark-dispatched` -- looks up the building-
  order via the protocol and drafts the building-materials-dispatch
  record, and returns {:result .. :building-order-patch ..} for the
  caller to persist."
  [s building-order-id]
  (let [bo (building-order s building-order-id)
        seq-n (next-dispatch-sequence s (:jurisdiction bo))
        result (registry/register-dispatch-record building-order-id (:jurisdiction bo) seq-n)]
    {:result result
     :building-order-patch {:dispatched? true
                            :dispatch-number (get result "dispatch_number")}}))

(defn- invoice-order!
  "Backend-agnostic `:order/mark-invoiced` -- looks up the building-
  order via the protocol and drafts the building-materials-invoice
  record, and returns {:result .. :building-order-patch ..} for the
  caller to persist."
  [s building-order-id]
  (let [bo (building-order s building-order-id)
        seq-n (next-invoice-sequence s (:jurisdiction bo))
        result (registry/register-invoice-record building-order-id (:jurisdiction bo) seq-n)]
    {:result result
     :building-order-patch {:invoiced? true
                            :invoice-number (get result "invoice_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (building-order [_ id] (get-in @a [:building-orders id]))
  (all-building-orders [_] (sort-by :id (vals (:building-orders @a))))
  (assessment-of [_ building-order-id] (get-in @a [:assessments building-order-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (invoice-history [_] (:invoices @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-invoice-sequence [_ jurisdiction] (get-in @a [:invoice-sequences jurisdiction] 0))
  (building-order-already-dispatched? [_ building-order-id] (boolean (get-in @a [:building-orders building-order-id :dispatched?])))
  (building-order-already-invoiced? [_ building-order-id] (boolean (get-in @a [:building-orders building-order-id :invoiced?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (swap! a update-in [:building-orders (:id value)] merge value)

      :certification-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :order/mark-dispatched
      (let [building-order-id (first path)
            {:keys [result building-order-patch]} (dispatch-order! s building-order-id)
            jurisdiction (:jurisdiction (building-order s building-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:building-orders building-order-id] merge building-order-patch)
                       (update :dispatches registry/append result))))
        result)

      :order/mark-invoiced
      (let [building-order-id (first path)
            {:keys [result building-order-patch]} (invoice-order! s building-order-id)
            jurisdiction (:jurisdiction (building-order s building-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:invoice-sequences jurisdiction] (fnil inc 0))
                       (update-in [:building-orders building-order-id] merge building-order-patch)
                       (update :invoices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-building-orders [s building-orders] (when (seq building-orders) (swap! a assoc :building-orders building-orders)) s))

(defn seed-db
  "A MemStore seeded with the demo building-order set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :dispatch-sequences {} :dispatches []
                           :invoice-sequences {} :invoices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, dispatch/
  invoice records) are stored as EDN strings so `langchain.db` doesn't
  expand them into sub-entities -- the same convention every sibling
  actor's store uses."
  {:building-order/id                    {:db/unique :db.unique/identity}
   :assessment/building-order-id         {:db/unique :db.unique/identity}
   :ledger/seq                           {:db/unique :db.unique/identity}
   :dispatch/seq                         {:db/unique :db.unique/identity}
   :invoice/seq                          {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction       {:db/unique :db.unique/identity}
   :invoice-sequence/jurisdiction        {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; Every building-order field is stored as its own Datomic attr so a
;; governor pull reads the exact ground truth (no blob decode). Boolean
;; fields are coerced on read so a missing attr reads back as false
;; (parity with MemStore). [field-key tx-attr boolean?]
(def ^:private building-order-fields
  [[:id :building-order/id false]
   [:order-id :building-order/order-id false]
   [:product-category :building-order/product-category false]
   [:sku :building-order/sku false]
   [:counterparty :building-order/counterparty false]
   [:price :building-order/price false]
   [:contract-terms :building-order/contract-terms false]
   [:credit-cleared? :building-order/credit-cleared? true]
   [:sanctions-screened? :building-order/sanctions-screened? true]
   [:potable-water-contact? :building-order/potable-water-contact? true]
   [:lead-content-tested? :building-order/lead-content-tested? true]
   [:lead-free-certificate-on-file? :building-order/lead-free-certificate-on-file? true]
   [:dispatched? :building-order/dispatched? true]
   [:invoiced? :building-order/invoiced? true]
   [:jurisdiction :building-order/jurisdiction false]
   [:status :building-order/status false]
   [:dispatch-number :building-order/dispatch-number false]
   [:invoice-number :building-order/invoice-number false]])

(defn- building-order->tx [bo]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get bo k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:building-order/id (:id bo)}
          building-order-fields))

(def ^:private building-order-pull (mapv second building-order-fields))

(defn- pull->building-order [m]
  (when (:building-order/id m)
    (reduce (fn [bo [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc bo k (boolean v))
                  (some? v)    (assoc bo k v)
                  :else        bo)))
            {:id (:building-order/id m)}
            building-order-fields)))

(defrecord DatomicStore [conn]
  Store
  (building-order [_ id]
    (pull->building-order (d/pull (d/db conn) building-order-pull [:building-order/id id])))
  (all-building-orders [_]
    (->> (d/q '[:find [?id ...] :where [?e :building-order/id ?id]] (d/db conn))
         (map #(pull->building-order (d/pull (d/db conn) building-order-pull [:building-order/id %])))
         (sort-by :id)))
  (assessment-of [_ building-order-id]
    (dec* (d/q '[:find ?p . :in $ ?boid
                :where [?a :assessment/building-order-id ?boid] [?a :assessment/payload ?p]]
              (d/db conn) building-order-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (invoice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :invoice/seq ?s] [?e :invoice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-invoice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :invoice-sequence/jurisdiction ?j] [?e :invoice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (building-order-already-dispatched? [s building-order-id]
    (boolean (:dispatched? (building-order s building-order-id))))
  (building-order-already-invoiced? [s building-order-id]
    (boolean (:invoiced? (building-order s building-order-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (d/transact! conn [(building-order->tx value)])

      :certification-assessment/set
      (d/transact! conn [{:assessment/building-order-id (first path) :assessment/payload (enc payload)}])

      :order/mark-dispatched
      (let [building-order-id (first path)
            {:keys [result building-order-patch]} (dispatch-order! s building-order-id)
            jurisdiction (:jurisdiction (building-order s building-order-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(building-order->tx (assoc building-order-patch :id building-order-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :order/mark-invoiced
      (let [building-order-id (first path)
            {:keys [result building-order-patch]} (invoice-order! s building-order-id)
            jurisdiction (:jurisdiction (building-order s building-order-id))
            next-n (inc (next-invoice-sequence s jurisdiction))]
        (d/transact! conn
                     [(building-order->tx (assoc building-order-patch :id building-order-id))
                      {:invoice-sequence/jurisdiction jurisdiction :invoice-sequence/next next-n}
                      {:invoice/seq (count (invoice-history s)) :invoice/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-building-orders [s building-orders]
    (when (seq building-orders) (d/transact! conn (mapv building-order->tx (vals building-orders)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:building-orders ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [building-orders]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-building-orders s building-orders))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo building-order set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
