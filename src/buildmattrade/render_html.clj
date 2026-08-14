(ns buildmattrade.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  This repo shipped a `docs/samples/operator-console.html` that was
  HAND-WRITTEN: there was no generator at all, so nothing tied the page
  to the actor. This namespace replaces that with a real build step --
  it drives THIS repo's own actor stack (`buildmattrade.operation` ->
  `buildmattrade.governor` -> `buildmattrade.store`, over a real
  `langgraph.graph` StateGraph with `interrupt-before #{:request-approval}`)
  through a scenario built from this repo's own seeded
  `buildmattrade.store/demo-data` ids (`bo-1`..`bo-8`), and renders the
  page from what the run actually produced.

  Nothing on the page is hand-typed domain data. Every building-order
  field, every hold rule, every dispatch/invoice reference number, every
  jurisdiction citation and every ledger row is read back out of the
  store after the run. The op/phase gate matrix is derived from
  `buildmattrade.phase/phases` and `buildmattrade.governor/high-stakes`
  rather than described in prose, so it cannot drift from the code.

  Deterministic: no timestamps, no wall-clock, no map-iteration order
  in the output (every map is walked in sorted key order), so two runs
  against the same seed are byte-identical.

  `-main` REFUSES to write the page if the run produced zero
  `:governor-hold` facts. A console that shows only happy paths is not
  evidence that the governor works, so the HARD-hold requirement is a
  build-time invariant here rather than a convention someone has to
  remember.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [buildmattrade.facts :as facts]
            [buildmattrade.governor :as governor]
            [buildmattrade.operation :as op]
            [buildmattrade.phase :as phase]
            [buildmattrade.store :as store]
            [langgraph.graph :as g]))

;; ----------------------------- scenario -----------------------------

(def ^:private supervisor
  "Phase 3 (supervised-auto) trading supervisor -- the default operator."
  {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(def ^:private junior-supervisor
  "Phase 1 (assisted-intake) operator. Used for ONE step, deliberately:
  at phase 1 `:order/intake` is a permitted write but is NOT in the
  phase's `:auto` set, so it escalates to a human even though the
  governor is clean. That is the only way to route an `:order/upsert`
  commit through the approval node, which is what the approver-
  attribution probe below needs in order to MEASURE (rather than
  assume) whether the store keeps the approver identity on that path."
  {:actor-id "op-2" :actor-role :trading-supervisor :phase 1})

(defn- runner
  "Returns [exec! approve! reject! runs-atom]. Every graph result is
  retained so the render can read the in-memory `:audit` channel and
  compare it against what the store actually persisted."
  [actor]
  (let [runs (atom [])
        keep! (fn [r] (swap! runs conj r) r)]
    [(fn exec! [tid request context]
       (keep! (g/run* actor {:request request :context context} {:thread-id tid})))
     (fn approve! [tid by]
       (keep! (g/run* actor {:approval {:status :approved :by by}}
                      {:thread-id tid :resume? true})))
     (fn reject! [tid by]
       (keep! (g/run* actor {:approval {:status :rejected :by by}}
                      {:thread-id tid :resume? true})))
     runs]))

(defn run-demo!
  "Drives a freshly seeded store through every disposition this actor
  can reach, using only real seeded `bo-*` ids and real ops.

  Happy paths (full lifecycle, each actuation human-approved because
  `:delivery/dispatch`/`:invoice/settle` are never auto at ANY phase):
    - bo-1  ordinary construction material (dimensional lumber):
            intake -> verify -> dispatch -> settle
    - bo-6  potable-water-contact kitchen faucet WITH both evidentiary
            arms on file: verify -> dispatch -> settle. Proves the
            lead-free check is satisfiable, not a trap.
    - bo-8  heating equipment (gas furnace): dispatch is refused first
            (no assessment yet), then verify -> dispatch clears, then
            its invoice is REJECTED by the human approver -- the one
            path where a human refuses rather than the governor.

  HARD holds (un-overridable, never reach a human), one distinct rule
  each:
    - bo-2  `:no-spec-basis`  (its seeded jurisdiction \"ATL\" has no
            entry in `buildmattrade.facts/catalog`)
    - bo-8  `:evidence-incomplete` (dispatch attempted before the
            jurisdiction assessment was committed)
    - bo-3  `:credit-uncleared`
    - bo-4  `:contract-missing`
    - bo-5  `:counterparty-sanctions-flag-unresolved`
    - bo-7  `:lead-free-certification-missing` (potable-water-contact,
            NSF/372 lab test done but no NSF/61-scope certificate on
            file -- this vertical's defining check)
    - bo-1  `:already-dispatched`, then `:already-invoiced`

  Plus one phase-1 `:order/intake` on bo-2 whose patch re-states values
  already in the seed (so it invents nothing) purely to route an
  `:order/upsert` through the approval node for the attribution probe.

  Returns {:db store :runs [graph-results]}."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        [exec! approve! reject! runs] (runner actor)]

    ;; --- bo-1: ordinary construction material, full clean lifecycle ---
    (exec! "t01" {:op :order/intake :subject "bo-1"
                  :patch {:id "bo-1"
                          :counterparty "Akita Building Materials Trading Co"}}
           supervisor)
    (exec! "t02" {:op :certification/verify :subject "bo-1"} supervisor)
    (approve! "t02" "op-1")
    (exec! "t03" {:op :delivery/dispatch :subject "bo-1"} supervisor)
    (approve! "t03" "op-1")
    (exec! "t04" {:op :invoice/settle :subject "bo-1"} supervisor)
    (approve! "t04" "op-1")
    ;; double-actuation guards, off dedicated booleans (never :status)
    (exec! "t05" {:op :delivery/dispatch :subject "bo-1"} supervisor)
    (exec! "t06" {:op :invoice/settle :subject "bo-1"} supervisor)

    ;; --- bo-2: jurisdiction with no official spec-basis ---
    (exec! "t07" {:op :certification/verify :subject "bo-2"} supervisor)
    ;; phase-1 intake -> escalates on :phase-approval -> approved ->
    ;; commits via :order/upsert. Probe input, not a demo of new facts.
    (exec! "t08" {:op :order/intake :subject "bo-2"
                  :patch {:id "bo-2"
                          :counterparty "Atlantis Hardware Imports Ltd"}}
           junior-supervisor)
    (approve! "t08" "op-2")

    ;; --- bo-8: heating equipment. Refused, then cleared, then refused
    ;;     by the human on the money side. ---
    (exec! "t09" {:op :delivery/dispatch :subject "bo-8"} supervisor)
    (exec! "t10" {:op :certification/verify :subject "bo-8"} supervisor)
    (approve! "t10" "op-1")
    (exec! "t11" {:op :delivery/dispatch :subject "bo-8"} supervisor)
    (approve! "t11" "op-1")
    (exec! "t12" {:op :invoice/settle :subject "bo-8"} supervisor)
    (reject! "t12" "op-1")

    ;; --- bo-3 / bo-4 / bo-5: one generic counterparty-diligence hold each ---
    (exec! "t13" {:op :certification/verify :subject "bo-3"} supervisor)
    (approve! "t13" "op-1")
    (exec! "t14" {:op :delivery/dispatch :subject "bo-3"} supervisor)

    (exec! "t15" {:op :certification/verify :subject "bo-4"} supervisor)
    (approve! "t15" "op-1")
    (exec! "t16" {:op :delivery/dispatch :subject "bo-4"} supervisor)

    (exec! "t17" {:op :certification/verify :subject "bo-5"} supervisor)
    (approve! "t17" "op-1")
    (exec! "t18" {:op :delivery/dispatch :subject "bo-5"} supervisor)

    ;; --- bo-6: certified potable-water-contact product, clears end-to-end ---
    (exec! "t19" {:op :certification/verify :subject "bo-6"} supervisor)
    (approve! "t19" "op-1")
    (exec! "t20" {:op :delivery/dispatch :subject "bo-6"} supervisor)
    (approve! "t20" "op-1")
    (exec! "t21" {:op :invoice/settle :subject "bo-6"} supervisor)
    (approve! "t21" "op-1")

    ;; --- bo-7: the defining HARD hold of this vertical ---
    (exec! "t22" {:op :certification/verify :subject "bo-7"} supervisor)
    (approve! "t22" "op-1")
    (exec! "t23" {:op :delivery/dispatch :subject "bo-7"} supervisor)

    {:db db :runs @runs}))

;; ------------------- approver-attribution probe (measured) -------------------

(defn- attribution-key?
  "Is `k` a key that would carry an approver's identity? Deliberately
  broad and name-based so the probe keeps working if the store starts
  retaining attribution under a different key."
  [k]
  (and (keyword? k)
       (contains? #{"by" "approved-by" "approved_by" "approver" "approver-id"}
                  (name k))))

(defn- attribution-keys
  "Every approver-identity key reachable anywhere inside `v`. Scanned,
  never assumed -- the disclosure rendered on the page is derived from
  what this actually finds, so the page self-corrects if the store is
  ever changed to retain the approver."
  [v]
  (cond
    (map? v)  (into (into (sorted-set) (filter attribution-key?) (keys v))
                    (mapcat attribution-keys (vals v)))
    (coll? v) (into (sorted-set) (mapcat attribution-keys v))
    :else     (sorted-set)))

(defn- probe-attribution
  "Measures, surface by surface, whether the approver identity survives
  into the SSoT. Returns {:surfaces [..] :grants n :rejections n}."
  [db runs]
  (let [orders      (store/all-building-orders db)
        assessments (vec (keep #(store/assessment-of db (:id %)) orders))
        led         (vec (store/ledger db))
        dispatches  (vec (store/dispatch-history db))
        invoices    (vec (store/invoice-history db))
        run-audit   (vec (mapcat #(get-in % [:state :audit]) runs))
        grants      (filterv #(= :approval-granted (:t %)) run-audit)
        rejections  (filterv #(= :approval-rejected (:t %)) run-audit)
        surface (fn [label effect persisted? coll]
                  {:label label :effect effect :persisted? persisted?
                   :n (count coll) :keys (attribution-keys coll)})]
    {:grants (count grants)
     :rejections (count rejections)
     :surfaces
     [(surface "building-order records" ":order/upsert" true orders)
      (surface "certification assessments" ":certification-assessment/set" true assessments)
      (surface "dispatch drafts" ":order/mark-dispatched" true dispatches)
      (surface "invoice drafts" ":order/mark-invoiced" true invoices)
      (surface "append-only audit ledger" "store/append-ledger!" true led)
      (surface "graph :audit channel (in-memory only)" "not persisted" false run-audit)]}))

;; ----------------------------- html helpers -----------------------------

(defn- esc
  "Escape once. Called on RAW values only -- static markup below never
  passes through here, so nothing can be escaped twice."
  [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- money [v]
  (if (number? v)
    (String/format java.util.Locale/ROOT "%.2f" (into-array Object [(double v)]))
    ""))

(defn- kw-str [v] (if (keyword? v) (name v) (str v)))

(defn- yn [b] (if (true? b) "yes" "no"))

(defn- flag [b true-class true-text false-class false-text]
  (if (true? b)
    (str "<span class=\"" true-class "\">" true-text "</span>")
    (str "<span class=\"" false-class "\">" false-text "</span>")))

(defn- row [& cells] (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers body-rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" body-rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lead body]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       body
       "  </section>\n"))

;; ----------------------------- derived views -----------------------------

(defn- facts-for-subject [ledger subject]
  (filterv #(= subject (:subject %)) ledger))

(defn- last-fact-for-op [ledger subject op]
  (last (filter #(and (= subject (:subject %)) (= op (:op %))) ledger)))

(defn- basis-of [f] (vec (:basis f)))

(defn- verdict-cell
  "The last thing that happened to this order, as the ledger recorded it."
  [ledger subject]
  (let [f (last (facts-for-subject ledger subject))]
    (cond
      (nil? f) "<span class=\"muted\">no ledger activity</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold · "
           (esc (str/join ", " (map kw-str (basis-of f)))) "</span>")
      (= :approval-rejected (:t f))
      (str "<span class=\"err\">approver rejected · "
           (esc (str/join ", " (map kw-str (basis-of f)))) "</span>")
      (= :committed (:t f))
      (str "<span class=\"ok\">committed · " (esc (kw-str (:op f))) "</span>")
      :else (str "<span class=\"muted\">" (esc (kw-str (:t f))) "</span>"))))

(defn- order-rows [ledger orders]
  (for [{:keys [id order-id product-category sku counterparty jurisdiction
                price potable-water-contact? dispatched? invoiced?
                dispatch-number invoice-number]} orders]
    (row (str "<code>" (esc id) "</code>")
         (esc order-id)
         (esc (kw-str product-category))
         (str "<code>" (esc sku) "</code>")
         (esc counterparty)
         (esc jurisdiction)
         (str "<span class=\"num\">" (esc (money price)) "</span>")
         (if (true? potable-water-contact?)
           "<span class=\"tag water\">potable-water</span>" "<span class=\"muted\">—</span>")
         (if dispatched?
           (str "<span class=\"ok\">" (esc dispatch-number) "</span>")
           "<span class=\"muted\">not dispatched</span>")
         (if invoiced?
           (str "<span class=\"ok\">" (esc invoice-number) "</span>")
           "<span class=\"muted\">not invoiced</span>")
         (verdict-cell ledger id))))

(defn- type-gate-rows
  "The lead-free-certification type gate, order by order, showing what
  the governor ACTUALLY did at `:delivery/dispatch` -- including the
  orders where the check never fired because a different rule held
  first, which is the honest way to show a no-op."
  [ledger orders]
  (for [{:keys [id product-category potable-water-contact?
                lead-content-tested? lead-free-certificate-on-file?]} orders
        :let [f (last-fact-for-op ledger id :delivery/dispatch)
              b (set (basis-of f))
              fired? (contains? b :lead-free-certification-missing)]]
    (row (str "<code>" (esc id) "</code>")
         (esc (kw-str product-category))
         (flag potable-water-contact? "tag water" "true" "muted" "false")
         (esc (yn lead-content-tested?))
         (esc (yn lead-free-certificate-on-file?))
         (cond
           (nil? f) "<span class=\"muted\">dispatch not attempted this run</span>"
           fired? "<span class=\"critical\">HARD hold · lead-free-certification-missing</span>"
           (= :committed (:t f))
           (if (true? potable-water-contact?)
             "<span class=\"ok\">satisfied · dispatched</span>"
             "<span class=\"ok\">no-op · dispatched</span>")
           :else
           (str "<span class=\"err\">held on other rule · "
                (esc (str/join ", " (map kw-str (basis-of f)))) "</span>")))))

(defn- gate-matrix-rows
  "Derived from `buildmattrade.phase/phases` and
  `buildmattrade.governor/high-stakes` -- not hand-described, so it
  cannot drift from the code."
  []
  (let [ops (sort-by str phase/write-ops)
        phs (sort (keys phase/phases))]
    (for [o ops]
      (apply row
             (str "<code>" (esc (str o)) "</code>")
             (concat
              (for [p phs
                    :let [{:keys [writes auto]} (get phase/phases p)]]
                (cond
                  (contains? auto o) "<span class=\"ok\">auto</span>"
                  (contains? writes o) "<span class=\"warn\">approval</span>"
                  :else "<span class=\"muted\">disabled</span>"))
              [(if (contains? governor/high-stakes o)
                 "<span class=\"critical\">always human</span>"
                 "<span class=\"muted\">—</span>")])))))

(defn- hold-rows [ledger]
  (for [f ledger
        :when (contains? #{:governor-hold :approval-rejected} (:t f))]
    (row (str "<code>" (esc (kw-str (:op f))) "</code>")
         (str "<code>" (esc (:subject f)) "</code>")
         (if (= :governor-hold (:t f))
           (str "<span class=\"critical\">" (esc (str/join ", " (map kw-str (basis-of f)))) "</span>")
           (str "<span class=\"err\">" (esc (str/join ", " (map kw-str (basis-of f)))) "</span>"))
         (esc (str/join " / " (map :detail (:violations f))))
         (str "<span class=\"num\">" (esc (:confidence f)) "</span>"))))

(defn- catalog-rows [orders]
  (let [used (set (keep :jurisdiction orders))
        known (sort (keys facts/catalog))
        unknown (sort (remove facts/catalog used))]
    (concat
     (for [iso3 known
           :let [{:keys [owner-authority provenance required-evidence]} (facts/spec-basis iso3)]]
       (row (str "<code>" (esc iso3) "</code>")
            (if (contains? used iso3)
              "<span class=\"ok\">in use by a seeded order</span>"
              "<span class=\"muted\">catalogued, not exercised</span>")
            (esc owner-authority)
            (esc (str/join "; " required-evidence))
            (str "<code>" (esc provenance) "</code>")))
     (for [iso3 unknown]
       (row (str "<code>" (esc iso3) "</code>")
            "<span class=\"critical\">no spec-basis</span>"
            "<span class=\"err\">absent from buildmattrade.facts/catalog</span>"
            "<span class=\"muted\">—</span>"
            "<span class=\"muted\">—</span>")))))

(defn- registry-rows [dispatches invoices]
  (concat
   (for [r dispatches]
     (row "<span class=\"tag\">dispatch</span>"
          (str "<code>" (esc (get r "record_id")) "</code>")
          (esc (get r "kind"))
          (str "<code>" (esc (get r "building_order_id")) "</code>")
          (esc (get r "jurisdiction"))
          (esc (yn (get r "immutable")))))
   (for [r invoices]
     (row "<span class=\"tag\">invoice</span>"
          (str "<code>" (esc (get r "record_id")) "</code>")
          (esc (get r "kind"))
          (str "<code>" (esc (get r "building_order_id")) "</code>")
          (esc (get r "jurisdiction"))
          (esc (yn (get r "immutable")))))))

(defn- ledger-rows [ledger]
  (map-indexed
   (fn [i f]
     (row (str "<span class=\"num\">" i "</span>")
          (str "<code>" (esc (kw-str (:t f))) "</code>")
          (str "<code>" (esc (kw-str (:op f))) "</code>")
          (str "<code>" (esc (:subject f)) "</code>")
          (esc (kw-str (:disposition f)))
          (esc (or (some->> (basis-of f) seq (map kw-str) (str/join ", "))
                   (some-> (:summary f) (subs 0 (min 90 (count (:summary f)))))
                   ""))))
   ledger))

(defn- attribution-rows [{:keys [surfaces]}]
  (for [{:keys [label effect persisted? n keys]} surfaces]
    (row (esc label)
         (str "<code>" (esc effect) "</code>")
         (if persisted? "<span class=\"ok\">persisted</span>"
             "<span class=\"muted\">in-memory</span>")
         (str "<span class=\"num\">" n "</span>")
         (if (seq keys)
           (str "<span class=\"ok\">" (esc (str/join ", " (map str keys))) "</span>")
           "<span class=\"critical\">none found</span>"))))

(defn- attribution-disclosure
  "Derived AT RENDER TIME from the probe, never hard-coded. If the
  store is ever changed to retain the approver, this paragraph changes
  by itself."
  [{:keys [surfaces grants rejections]}]
  (let [persisted (filter :persisted? surfaces)
        with-keys (filter #(seq (:keys %)) persisted)
        without   (filter #(and (pos? (:n %)) (empty? (:keys %))) persisted)
        volatile-only (filter #(and (not (:persisted? %)) (seq (:keys %))) surfaces)]
    (str
     "<p class=\"muted\">Measured, not assumed: this paragraph is generated by scanning every "
     "committed record for an approver key (any of <code>:by</code>, <code>:approved-by</code>, "
     "<code>:approved_by</code>, <code>:approver</code>, <code>:approver-id</code>) after the run. "
     "This run granted <strong>" grants "</strong> human approvals and recorded <strong>"
     rejections "</strong> human rejection(s). "
     (if (seq with-keys)
       (str "The approver identity IS retained by " (count with-keys) " persisted surface(s): "
            (esc (str/join ", " (map :label with-keys))) ". ")
       "No persisted surface retains the approver identity at all. ")
     (if (seq without)
       (str "It is NOT retained by " (count without) " non-empty persisted surface(s): "
            (esc (str/join ", " (map :label without))) ". ")
       "")
     (when (seq volatile-only)
       (str "The approver IS present in " (esc (str/join ", " (map :label volatile-only)))
            ", which is discarded when the graph run ends. "))
     "So for those surfaces a reader cannot distinguish "
     "&quot;nobody approved this&quot; from &quot;the store did not keep who approved it&quot; "
     "&mdash; the ledger row shows <code>:committed</code> either way. "
     "This is disclosed, NOT patched: changing what <code>commit-record!</code> reads, or what "
     "the commit node appends, is a governance change with its own contract tests "
     "(<code>test/buildmattrade/store_contract_test.clj</code>), not a rendering decision.</p>\n")))

;; ----------------------------- stylesheet -----------------------------

(def ^:private stylesheet
  ;; Deliberately a small, self-contained stylesheet rather than a
  ;; vendored design system: this generator must build offline with no
  ;; dependency beyond what deps.edn already resolves, and the page is
  ;; judged on traceability, not on byte count.
  (str/join
   "\n"
   ["body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }"
    "header.bar { display: flex; align-items: baseline; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; flex-wrap: wrap; }"
    "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }"
    "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }"
    "main { max-width: 1280px; margin: 24px auto; padding: 0 20px; }"
    ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }"
    "h2 { margin-top: 0; font-size: 15px; }"
    "table { width: 100%; border-collapse: collapse; font-size: 13px; }"
    "th, td { text-align: left; padding: 7px 9px; border-bottom: 1px solid #f0f0f0; vertical-align: top; }"
    "th { font-weight: 600; color: #555; font-size: 11px; text-transform: uppercase; letter-spacing: 0.04em; }"
    "code { font-size: 12px; background: #f5f5f5; padding: 1px 4px; border-radius: 3px; }"
    ".muted { color: #888; }"
    ".num { font-variant-numeric: tabular-nums; }"
    ".ok { color: #137a3f; }"
    ".warn { color: #b25c00; background: #fff8e1; padding: 1px 6px; border-radius: 4px; }"
    ".err { color: #b3261e; background: #fbe9e7; padding: 1px 6px; border-radius: 4px; }"
    ".critical { color: #fff; background: #b3261e; padding: 1px 6px; border-radius: 4px; font-weight: 600; }"
    ".tag { font-size: 11px; padding: 1px 6px; border-radius: 10px; background: #eee; color: #555; }"
    ".tag.water { background: #e3f0fb; color: #1a5f9e; }"]))

;; ----------------------------- render -----------------------------

(defn render
  "Renders the console from a store that has already been driven by
  `run-demo!` (or any other real scenario) plus that run's results."
  [{:keys [db runs]}]
  (let [ledger     (vec (store/ledger db))
        orders     (vec (store/all-building-orders db))
        dispatches (vec (store/dispatch-history db))
        invoices   (vec (store/invoice-history db))
        probe      (probe-attribution db runs)
        holds      (filterv #(= :governor-hold (:t %)) ledger)
        hold-rules (sort (distinct (mapcat basis-of holds)))
        cov        (facts/coverage)]
    (str
     "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-4663 · buildmattrade operator console</title>\n"
     "<style>\n" stylesheet "\n</style></head><body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Wholesale of construction materials, hardware, plumbing and heating equipment (ISIC 4663) — Operator Console</h1>\n"
     "  <span class=\"badge\">generated by <code>buildmattrade.render-html</code> · read-only · governor-gated</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "What this page is"
      (str "Every row below was produced by running this repo's own actor "
           "(<code>buildmattrade.operation</code>, a <code>langgraph</code> StateGraph with "
           "<code>interrupt-before #{:request-approval}</code>) against the seeded store in "
           "<code>buildmattrade.store/demo-data</code>, then reading the result back out of the store. "
           "Nothing here is hand-typed. This run committed to the ledger "
           "<strong>" (count ledger) "</strong> decision facts across <strong>" (count orders)
           "</strong> building-orders, produced <strong>" (count holds)
           "</strong> HARD governor holds covering <strong>" (count hold-rules)
           "</strong> distinct rules, and drafted <strong>" (count dispatches)
           "</strong> dispatch and <strong>" (count invoices) "</strong> invoice records. "
           "The build refuses to write this file if the HARD-hold count is zero.")
      (table ["Property" "Value"]
             [(row "HARD governor holds" (str "<span class=\"num\">" (count holds) "</span>"))
              (row "Distinct HARD hold rules"
                   (str "<span class=\"critical\">"
                        (esc (str/join ", " (map kw-str hold-rules))) "</span>"))
              (row "Human approvals granted this run"
                   (str "<span class=\"num\">" (:grants probe) "</span>"))
              (row "Human rejections this run"
                   (str "<span class=\"num\">" (:rejections probe) "</span>"))
              (row "Jurisdictions with an official spec-basis"
                   (str "<span class=\"num\">" (:covered cov) "</span> ("
                        (esc (str/join ", " (:covered-jurisdictions cov))) ")"))
              (row "Governor" (str "<code>" (esc :potable-water-safety-governor) "</code>"))
              (row "Confidence floor"
                   (str "<span class=\"num\">" (esc governor/confidence-floor) "</span>"))]))

     (section
      "Building-order register"
      (str "The full seeded directory, as the store holds it after the run. "
           "Dispatch and invoice reference numbers are drafted by "
           "<code>buildmattrade.registry</code> with a jurisdiction-scoped sequence; "
           "an order with no number was never actuated.")
      (table ["ID" "Order" "Category" "SKU" "Counterparty" "Juris." "Price"
              "Type gate" "Dispatch ref" "Invoice ref" "Last ledger fact"]
             (order-rows ledger orders)))

     (section
      "Type-gating proof — lead-free certification (SDWA §1417 / NSF/ANSI/CAN 372 + 61)"
      (str "This vertical's defining check is gated on ONE fact, <code>:potable-water-contact?</code>, "
           "and internally requires BOTH evidentiary arms: the NSF/372 weighted-average lead-content "
           "lab test AND the NSF/61-scope certificate actually on file. The last column is what the "
           "governor did at <code>:delivery/dispatch</code> in this run, not a description of what it "
           "should do. Orders shown as held on another rule are honest no-ops: the lead-free check did "
           "not fire for them at all.")
      (table ["ID" "Category" "potable-water-contact?" "NSF/372 tested?"
              "NSF/61 certificate on file?" "Observed at dispatch"]
             (type-gate-rows ledger orders)))

     (section
      "HARD holds and human refusals observed in this run"
      (str "HARD governor violations are un-overridable and never reach a human approver. "
           "The single <code>approval-rejected</code> row is the opposite case: the governor was "
           "clean, the actuation escalated as it always does, and the human said no.")
      (table ["Op" "Subject" "Rule(s)" "Detail (as the governor wrote it)" "Confidence"]
             (hold-rows ledger)))

     (section
      "Op / phase gate matrix"
      (str "Derived at build time from <code>buildmattrade.phase/phases</code> and "
           "<code>buildmattrade.governor/high-stakes</code>, so it cannot drift from the code. "
           "Note that <code>:delivery/dispatch</code> and <code>:invoice/settle</code> are absent "
           "from every phase's auto set, including phase 3 — two independent layers (the phase gate "
           "and the governor's high-stakes set) agree that actuation is always a human call.")
      (table (concat ["Op"] (map #(str "Phase " %) (sort (keys phase/phases))) ["Governor high-stakes"])
             (gate-matrix-rows)))

     (section
      "Jurisdiction spec-basis catalog"
      (str "The governor rejects any proposal citing a jurisdiction that is not in "
           "<code>buildmattrade.facts/catalog</code>. " (esc (:note cov)))
      (table ["ISO3" "Status" "Owner authority" "Required evidence" "Provenance"]
             (catalog-rows orders)))

     (section
      "Registry drafts written this run"
      (str "Produced by <code>buildmattrade.registry</code>. Every certificate this actor emits is "
           "UNSIGNED (<code>status: draft-unsigned</code>, <code>issued_by_registry: false</code>) — "
           "signature is the operator's act, not the actor's.")
      (table ["Kind" "Record ID" "Record kind" "Building-order" "Jurisdiction" "Immutable"]
             (registry-rows dispatches invoices)))

     (section
      "Approver attribution — what the store actually keeps"
      (str "One thing this console cannot show you honestly without measuring it first: "
           "whether the SSoT remembers WHO approved each escalated act.")
      (str (attribution-disclosure probe)
           (table ["Surface" "Commit effect" "Durability" "Records scanned" "Approver keys found"]
                  (attribution-rows probe))))

     (section
      "Audit ledger (this run)"
      (str "The append-only decision log, in order. <code>:committed</code> rows carry the citation "
           "the advisor used; <code>:governor-hold</code> rows carry the rules that blocked it.")
      (table ["#" "Fact" "Op" "Subject" "Disposition" "Basis / summary"]
             (ledger-rows ledger)))

     "</main>\n</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds (filterv #(= :governor-hold (:t %)) ledger)
        hold-rules (sort (distinct (mapcat basis-of holds)))]
    (when (empty? holds)
      (throw (ex-info
              (str "REFUSING to write " out ": the scenario produced ZERO :governor-hold facts. "
                   "An operator console that shows only happy paths is not evidence that the "
                   "Potable Water Safety Governor works. Fix the scenario in "
                   "buildmattrade.render-html/run-demo! (or the governor) before regenerating.")
              {:out out :ledger-facts (count ledger) :governor-holds 0})))
    (spit out (render result))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count holds) " HARD holds over " (count hold-rules) " distinct rules: "
                  (str/join ", " (map kw-str hold-rules)) ", "
                  (count (store/dispatch-history db)) " dispatch drafts, "
                  (count (store/invoice-history db)) " invoice drafts, "
                  (count runs) " graph runs)"))))
