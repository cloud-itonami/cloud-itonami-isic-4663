(ns buildmattrade.governor
  "Potable Water Safety Governor -- the independent compliance layer
  that earns the BuildMatTradeAdvisor the right to commit. The LLM has
  no notion of jurisdictional building-materials-trade law, whether a
  counterparty's credit has actually been cleared, whether contract
  terms are actually on file, whether THIS specific plumbing product
  actually has a valid, currently-effective lead-free certification on
  file (when it is a potable-water-contact product), whether OFAC /
  equivalent sanctions screening has actually been passed, or when an
  act stops being a draft and becomes a real dispatch of physical
  building materials or a real invoice settlement, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD.

  Like every principal-trading sibling's own governor, this
  building-materials-wholesale vertical has NO pre-existing
  building-materials-trading capability library to delegate to -- so
  the domain checks (credit-clearance, contract-on-file, lead-free
  certification, sanctions-screening) are direct entity boolean reads
  off the `building-order` record, evaluated directly here, NOT
  delegated to a separate library's validated function.

  `:itonami.blueprint/governor` is `:potable-water-safety-governor`,
  grep-verified UNIQUE fleet-wide -- no naming-collision precedent
  question, a fresh independent build following the SAME governed-actor
  architecture (langgraph StateGraph + independent Governor + Phase
  0->3 rollout) established by `cloud-itonami-isic-6511` and applied by
  the fuel-wholesale (`cloud-itonami-isic-4671`), ag-machinery-wholesale
  (`cloud-itonami-isic-4653`) and household-goods-wholesale
  (`cloud-itonami-isic-4649`) siblings.

  ============================================================
  CRITICAL STRUCTURAL DECISION: ONE domain-defining check, folding TWO
  evidentiary arms of the SAME real-world determination -- the
  household-goods sibling's own Children's Product Certificate shape,
  NOT the ag-machinery sibling's own two-independent-checks shape --
  PLUS a reasoned decision NOT to add a second check for heating
  equipment.
  ============================================================

  This vertical's defining regulatory content is the US Reduction of
  Lead in Drinking Water Act of 2011 (Pub. L. 111-380), amending Safe
  Drinking Water Act §1417 (42 U.S.C. §300g-6): any pipe, pipe fitting,
  plumbing fitting or fixture intended to convey or dispense water for
  human consumption must be 'lead free' -- not more than 0.25% lead by
  WEIGHTED AVERAGE of the wetted surfaces (a real, significant
  tightening from the prior 8% ceiling, effective January 4, 2014).
  Compliance is demonstrated via independent third-party certification
  to NSF/ANSI/CAN 372 (the weighted-average lead-content TEST) and
  NSF/ANSI/CAN 61 (the broader health-effects CERTIFICATE for drinking
  water system components) -- see `buildmattrade.facts` for the full
  citation.

  `lead-free-certification-missing-violations` is type-gated on a
  SINGLE fact, `:potable-water-contact?` (is this SKU a pipe, pipe
  fitting, plumbing fitting or fixture intended to convey or dispense
  water for human consumption), and internally requires BOTH
  `:lead-content-tested?` (the NSF/372 weighted-average lead-content lab
  test) AND `:lead-free-certificate-on-file?` (the NSF/61-scope
  certificate itself). This is deliberately modeled as the household-
  goods sibling's own Children's Product Certificate shape (ONE check,
  gated on ONE fact, that internally folds TWO evidentiary sub-facts
  into ONE rule) -- NOT the ag-machinery sibling's own shape (TWO
  SEPARATE checks, each gated on its OWN independent boolean). The
  reason: `:lead-content-tested?` and `:lead-free-certificate-on-file?`
  are TWO EVIDENTIARY ARMS OF THE SAME determination -- a compliant
  lead-free certificate cannot exist without the underlying NSF/372
  weighted-average lab test, and a lab test without the certificate
  itself actually on file is not yet a compliant certification either.
  Missing EITHER one is EQUALLY 'no valid lead-free certification on
  file'. Contrast the ag-machinery sibling's own emissions-certificate/
  ROPS-certificate pair, which are governed by TWO GENUINELY DIFFERENT
  regulatory regimes (air quality vs. operator safety) triggered by TWO
  INDEPENDENT machine properties that vary separately -- THIS
  vertical's own certification concern has no such independent-
  triggering-property structure: `:potable-water-contact?` is the ONLY
  gating fact, and the two evidentiary sub-facts are not independently
  triggered by anything else. `buildmattrade.store/demo-data`'s `bo-1`
  (ordinary construction material -- lumber, NEITHER evidentiary
  sub-fact on file) and `bo-8` (heating equipment -- a gas furnace, also
  not potable-water-contact) TOGETHER prove the type-gate from two
  DIFFERENT non-potable angles named explicitly in ISIC 4663's own
  scope (construction materials AND heating equipment): both dispatch
  cleanly, because neither has anything to certify against a potable-
  water standard in the first place. `bo-6` (a potable-water-contact
  kitchen faucet, BOTH sub-facts true) proves the check is satisfiable;
  `bo-7` (a potable-water-contact copper pipe fitting, lab-tested but NO
  certificate actually filed) proves the fold requires BOTH arms, not
  merely that testing occurred -- a realistic gap, since lab testing
  against NSF/372 often completes before the NSF/61-scope certificate
  paperwork itself is finalized and filed.

  DECISION NOT TO ADD A SECOND CHECK for heating-equipment safety
  certification (e.g. an ANSI Z21-series / CSA-International-style
  gas-appliance certification, or a UL/NRTL listing for oil-fired
  heating equipment), even though heating equipment is explicitly named
  in ISIC 4663's own scope and a real regulatory concern of this shape
  plausibly exists: this build's confidence in citing that regime
  PRECISELY is only MODERATE, not high, for two compounding reasons.
  First, unlike the Reduction of Lead in Drinking Water Act (a single,
  precisely-citable FEDERAL statute with an exact numeric threshold and
  an exact effective date), gas/oil-fired heating-equipment product
  safety certification in the US is NOT anchored in one uniform federal
  per-product certification mandate -- NRTL (Nationally Recognized
  Testing Laboratory) listing requirements for this equipment class
  flow through a PATCHWORK of state/local adoption of model codes (the
  International Fuel Gas Code, NFPA 54) and general OSHA/insurer
  practice, not a single statute this build could cite with the SAME
  confidence as SDWA §1417. Second, this build could not independently
  confirm the exact current standard identifiers (e.g. specific
  ANSI Z21.x / CSA x.x dual-listing numbers) it would need to write an
  honest `buildmattrade.facts`-style G2 citation without risking an
  OVERCLAIMED spec-basis -- the same discipline the household-goods
  sibling's own `docs/business-model.md` 'Jurisdiction coverage
  (honest)' section already established fleet-wide (flag genuine
  uncertainty rather than fabricate precision). Building a HARD governor
  check implies asserting a citable regulatory MANDATE; the confidence
  gap here is real, so this build scopes the check OUT of R0 rather than
  force a second check merely to match the household-goods /
  ag-machinery siblings' own two-check pattern. `bo-8` (heating
  equipment) is still exercised in the demo/tests as a `lead-free-
  certification-missing` NO-OP control case -- it simply is not gated by
  ANY dedicated heating-equipment-specific check in this R0. See
  `docs/adr/0001-architecture.md` Decision 5 for the full reasoning and
  alternatives considered.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `buildmattrade.phase`: for `:stake :delivery/
  dispatch`/`:invoice/settle` (a real dispatch or invoice settlement) NO
  phase ever allows auto-commit either. Two independent layers agree
  that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`buildmattrade.facts`), or
                                       invent one?
    2. Evidence incomplete         -- for `:delivery/dispatch`/
                                       `:invoice/settle`, has the
                                       jurisdiction actually been
                                       verified with a full GENERIC
                                       counterparty-diligence evidence
                                       checklist on file? Deliberately
                                       does NOT include the lead-free
                                       certification -- that is check 5
                                       below.
    3. Credit uncleared            -- for `:delivery/dispatch`, the
                                       counterparty's credit has NOT been
                                       cleared (the leasing collateral-
                                       coverage discipline, applied to
                                       counterparty credit). Evaluated
                                       before the goods leave.
    4. Contract missing            -- for `:delivery/dispatch`, no
                                       contract-terms are on file for the
                                       order. Evaluated before the goods
                                       leave.
    5. Lead-free certification
       missing                       -- for `:delivery/dispatch`, WHEN
                                       `:potable-water-contact?` is true,
                                       no valid lead-free certification
                                       (BOTH `:lead-content-tested?` AND
                                       `:lead-free-certificate-on-file?`)
                                       is on file. NO-OP for ordinary
                                       construction material, hardware,
                                       or non-potable heating equipment
                                       -- THIS check has no analog in ANY
                                       prior sibling's governor: it is
                                       this vertical's own PRE-SHIPMENT
                                       defining regulatory content. See
                                       'CRITICAL STRUCTURAL DECISION'
                                       above.
    6. Counterparty sanctions flag
       unresolved                    -- for `:delivery/dispatch` and
                                       `:invoice/settle`, the counterparty
                                       has NOT passed OFAC / equivalent
                                       sanctions screening -- a HARD,
                                       un-overridable hold. Evaluated
                                       UNCONDITIONALLY at both actuation
                                       ops.
    7. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:delivery/dispatch`/
                                       `:invoice/settle` (REAL acts)
                                       -> escalate.

  Two more guards, double-dispatch/double-invoice prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-violations`/
  `already-invoiced-violations` refuse to dispatch/invoice the SAME
  building-order twice, off dedicated `:dispatched?`/`:invoiced?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320).

  Unlike the household-goods sibling's `active-recall-unresolved`
  check, `lead-free-certification-missing` is evaluated ONLY at
  `:delivery/dispatch`, NOT re-checked at `:invoice/settle` -- it is a
  genuine PRE-SHIPMENT, onboarding-time fact (like credit-clearance and
  contract-on-file), not a post-hoc, discovered-defect flag. This build
  deliberately does NOT introduce a CPSC-recall-style re-checked flag
  for this vertical: a lead-free certification, once validly on file at
  the moment of dispatch, is not the kind of fact that later
  'un-happens' the way a product-safety recall newly opens after a sale
  -- see `docs/adr/0001-architecture.md` Decision 4 for the full
  reasoning."
  (:require [buildmattrade.facts :as facts]
            [buildmattrade.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching real physical building materials (construction materials,
  hardware, plumbing fixtures/pipes, heating equipment) from the
  wholesale yard/distribution center to a counterparty and settling a
  real invoice (real money moving between counterparty and wholesaler)
  are the two real-world actuation events this actor performs -- a
  two-member set, matching every dual-actuation sibling's own shape."
  #{:delivery/dispatch :invoice/settle})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:certification/verify` (or `:delivery/dispatch`/`:invoice/settle`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's building-materials-trade / potable-water-
  product / sanctions requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:certification/verify :delivery/dispatch :invoice/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:delivery/dispatch`/`:invoice/settle`, the jurisdiction's
  required GENERIC counterparty-diligence evidence (credit-clearance
  record, contract/PO, sanctions-screening record) must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone. Deliberately does NOT check lead-free certification -- that is
  `lead-free-certification-missing-violations` below, its own
  dedicated, independently type-gated check rather than a checklist
  item."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [bo (store/building-order st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction bo) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(信用審査記録/契約書またはPO/制裁スクリーニング記録)が充足していない状態での提案"}]))))

(defn- credit-uncleared-violations
  "For `:delivery/dispatch`, refuses to dispatch real building materials
  to a counterparty whose credit has NOT been cleared -- counterparty
  credit not cleared (the leasing collateral-coverage discipline,
  applied to counterparty credit). Evaluated at the yard/distribution
  center, ahead of any physical handoff."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [bo (store/building-order st subject)]
      (when (not (true? (:credit-cleared? bo)))
        [{:rule :credit-uncleared
          :detail (str subject " の取引先信用審査(credit-clearance)が未了 -- 出荷提案は進められない")}]))))

(defn- contract-missing-violations
  "For `:delivery/dispatch`, refuses to dispatch real building materials
  when no contract-terms are on file for the order."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [bo (store/building-order st subject)]
      (when (or (nil? (:contract-terms bo)) (= "" (:contract-terms bo)))
        [{:rule :contract-missing
          :detail (str subject " に契約条項(contract-terms)の記録が無い -- 出荷提案は進められない")}]))))

(defn- lead-free-certification-missing-violations
  "For `:delivery/dispatch`, WHEN `:potable-water-contact?` is true,
  refuses to dispatch a SKU with no valid lead-free certification on
  file -- folding BOTH evidentiary arms of the SAME Reduction of Lead in
  Drinking Water Act / Safe Drinking Water Act §1417 (42 U.S.C.
  §300g-6) determination into ONE rule: `:lead-content-tested?` (the
  NSF/ANSI/CAN 372 weighted-average lead-content lab test, <= 0.25% of
  wetted surfaces) AND `:lead-free-certificate-on-file?` (the
  NSF/ANSI/CAN 61-scope certificate itself). THIS check has no analog
  in ANY prior sibling's governor: it is this vertical's own
  PRE-SHIPMENT defining regulatory content, and is a genuine NO-OP for
  ordinary construction material, hardware, or non-potable heating
  equipment (`:potable-water-contact?` false) --
  `buildmattrade.store/demo-data`'s `bo-1` (lumber, a construction
  material, NEITHER evidentiary sub-fact on file) and `bo-8` (a gas
  furnace, heating equipment, also not potable-water-contact) BOTH prove
  this directly: the orders still dispatch cleanly, because neither has
  anything to certify against a potable-water standard in the first
  place. See namespace docstring 'CRITICAL STRUCTURAL DECISION' for why
  this is ONE check folding two evidentiary arms, and for why this build
  deliberately does NOT add a second, independently-gated check for
  heating-equipment safety certification."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [bo (store/building-order st subject)]
      (when (and (true? (:potable-water-contact? bo))
                 (not (and (true? (:lead-content-tested? bo))
                           (true? (:lead-free-certificate-on-file? bo)))))
        [{:rule :lead-free-certification-missing
          :detail (str subject " (" (name (:product-category bo)) ") は飲用水接触製品だが"
                       "鉛含有量試験(NSF/372)および飲用水安全認証(NSF/61)の記録が無い -- 出荷提案は進められない")}]))))

(defn- counterparty-sanctions-flag-unresolved-violations
  "For `:delivery/dispatch` and `:invoice/settle`, an unresolved
  sanctions-screening flag -- the counterparty has NOT passed OFAC /
  equivalent sanctions screening -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY at both actuation ops: neither materials nor
  money moves against an unscreened counterparty."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [bo (store/building-order st subject)]
      (when (not (true? (:sanctions-screened? bo)))
        [{:rule :counterparty-sanctions-flag-unresolved
          :detail (str subject " の取引先制裁スクリーニング(OFAC等)が未了 -- 出荷・請求提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:delivery/dispatch`, refuses to dispatch the SAME building-order
  twice, off a dedicated `:dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (when (store/building-order-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に出荷済み")}])))

(defn- already-invoiced-violations
  "For `:invoice/settle`, refuses to settle the SAME building-order's
  invoice twice, off a dedicated `:invoiced?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :invoice/settle)
    (when (store/building-order-already-invoiced? st subject)
      [{:rule :already-invoiced
        :detail (str subject " は既に請求済み")}])))

(defn check
  "Censors a BuildMatTradeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (credit-uncleared-violations request st)
                           (contract-missing-violations request st)
                           (lead-free-certification-missing-violations request st)
                           (counterparty-sanctions-flag-unresolved-violations request st)
                           (already-dispatched-violations request st)
                           (already-invoiced-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
