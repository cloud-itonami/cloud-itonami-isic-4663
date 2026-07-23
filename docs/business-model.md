# Business Model: Wholesale of Construction Materials, Hardware, Plumbing and Heating Equipment

## Classification
- Repository: `cloud-itonami-isic-4663`
- ISIC Rev.5: `4663` — wholesale of construction materials, hardware,
  plumbing fixtures/pipes/fittings and heating equipment
- Domain: `downstream/building-materials-wholesale`
- Social impact: public health, safety, transparency
- Governor: `:potable-water-safety-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers building-order intake through per-jurisdiction
contract / sanctions / potable-water-product regulatory verification,
physical dispatch (releasing real building materials for a counterparty
from the wholesale yard/distribution center), and invoice settlement
(the money side of a wholesale trade, custody / financial transfer) for
a building-materials wholesaler. It does **not**, by itself, hold any
wholesale licence, importer-of-record registration or operating
authority required to run a building-materials-wholesale business in a
given jurisdiction, perform the actual physical yard pick/pack/ship, or
judge distribution-network economics (pallet/bundle routing and
distribution-network optimization is a follow-up slice, not this R0).
Whoever deploys a live instance supplies the jurisdiction-specific
operating authority, the real yard-automation and ERP / accounts-
receivable integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution scaffold
so the operator does not have to build the compliance layer from
scratch.

## Customer
- regional and independent building-materials wholesalers and yard
  operators
- plumbing-fixture / hardware / heating-equipment distributors leaving
  closed distribution / ERP SaaS
- contractors, plumbing wholesalers, and big-box supply buyers who need
  an auditable, spec-cited supply chain
- counterparties, banks and regulators who need an auditable, spec-cited
  trade record

## Offer
- building-order intake and directory management
- per-jurisdiction contract / sanctions regulatory verification with an
  official spec-basis citation
- lead-free certification compliance (NSF/ANSI/CAN 372 lead-content
  testing + NSF/ANSI/CAN 61 certificate on file) gated on the order's
  own `:potable-water-contact?` fact
- physical dispatch gated on full evidence, a credit-cleared
  counterparty, contract-terms on file, a valid lead-free certification
  (when applicable), and a passed sanctions screen
- invoice settlement with double-invoice prevention
- evidence checklisting (credit-clearance record, contract/PO,
  sanctions-screening record)
- sanctions and credit exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per wholesaler / yard
- support retainer with SLA
- ERP and accounts-receivable integration

## The `:potable-water-safety-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is
`:potable-water-safety-governor`. It is the single authority that
stands between "building materials could be dispatched to a
counterparty" and "they are allowed to leave the wholesale yard," and
between "an invoice could be settled" and "it is allowed to settle."
Every rule it enforces is traceable to the domain (Wholesale of
Construction Materials, Hardware, Plumbing and Heating Equipment, ISIC
4663) and to the three `:social-impact` tags in `blueprint.edn`
(`:public-health`, `:safety`, `:transparency`).

This is the rule the companion contract test
(`test/buildmattrade/governor_contract_test.clj`) encodes end-to-end:
the BuildMatTradeAdvisor never dispatches building materials to a
counterparty or settles an invoice the Potable Water Safety Governor
would reject, `:delivery/dispatch` and `:invoice/settle` NEVER
auto-commit at any phase, `:order/intake` (no direct capital risk) MAY
auto-commit when clean, and every decision (commit OR hold) leaves
exactly one ledger fact.

**Authorizes a physical dispatch (`:delivery/dispatch`) or invoice
settlement (`:invoice/settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:certification/verify`, `:delivery/
   dispatch`, or `:invoice/settle` proposal whose jurisdiction has no
   entry in the `buildmattrade.facts` catalog (`:no-spec-basis`). This
   is the direct enforcement of `:transparency`: a jurisdiction whose
   building-materials-trade / potable-water-product requirements cannot
   be traced to an OFFICIAL public source is never guessed. The advisor
   must not fabricate a jurisdiction's requirements.
2. **The jurisdiction's required GENERIC evidence is fully on file** --
   for a dispatch or invoice the order's jurisdiction must have been
   verified with a complete counterparty-diligence evidence checklist on
   record: the credit-clearance record, the contract / purchase order,
   and the sanctions-screening (OFAC / equivalent) record
   (`:evidence-incomplete`). Deliberately does NOT include the
   lead-free-certification status -- that is check 5 below, its own
   dedicated check.
3. **The counterparty's credit has been cleared** -- the governor reads
   the dedicated `:credit-cleared?` fact on the order and refuses to
   dispatch building materials when credit has NOT been cleared (the
   leasing collateral-coverage discipline, applied to counterparty
   credit) (`:credit-uncleared`). Evaluated at `:delivery/dispatch`.
4. **Contract-terms are on file** -- the governor refuses to dispatch
   when no `:contract-terms` are recorded for the order
   (`:contract-missing`). Building materials never leave the wholesale
   yard against an undocumented trade. Evaluated at `:delivery/
   dispatch`.
5. **A potable-water-contact product has a valid lead-free certification
   on file** -- WHEN the order's `:potable-water-contact?` fact is true
   (a pipe, pipe fitting, plumbing fitting or fixture intended to convey
   or dispense water for human consumption), the governor requires BOTH
   `:lead-content-tested?` (the NSF/ANSI/CAN 372 weighted-average
   lead-content lab test) AND `:lead-free-certificate-on-file?` (the
   NSF/ANSI/CAN 61-scope certificate itself) to be true -- a genuine
   NO-OP for ordinary construction material, hardware, or non-potable
   heating equipment (`:lead-free-certification-missing`). Evaluated at
   `:delivery/dispatch`. This is a PRE-SHIPMENT check, not re-checked at
   invoice settlement -- see "Why this check is dispatch-only" below.
6. **The counterparty has passed OFAC / equivalent sanctions screening**
   -- the governor reads the dedicated `:sanctions-screened?` fact and
   treats an unresolved sanctions-screening flag as a HARD, un-
   overridable hold (`:counterparty-sanctions-flag-unresolved`). Neither
   materials nor money moves against an unscreened counterparty.
   Evaluated UNCONDITIONALLY at both `:delivery/dispatch` and
   `:invoice/settle`.
7. **The order has not already been dispatched, and the invoice has not
   already been settled** -- a double dispatch of the same order is
   refused off a dedicated `:dispatched?` fact, and a double invoice off
   a dedicated `:invoiced?` fact (never a `:status` value), the double-
   actuation guard every sibling actor in this fleet enforces
   (`:already-dispatched` / `:already-invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, an
uncleared counterparty credit, no contract-terms on file, a missing
lead-free certification on a potable-water-contact product, an
unresolved sanctions-screening flag, or a double dispatch/invoice is
held at the governor node -- a human approver cannot override these, by
construction.

**Always escalates to a human (never auto-commits) for `:delivery/
dispatch` and `:invoice/settle`**, even when every check above is clean.
Dispatching real building materials to a counterparty from the wholesale
yard/distribution center and settling a real invoice (real money moving
between counterparty and wholesaler) are the two real-world actuation
events this actor performs; both are always a human trading
supervisor's call. This is enforced by TWO independent layers that
agree on purpose: the governor's confidence / actuation SOFT gate (a
`:delivery/dispatch` / `:invoice/settle` stake always escalates) and
`buildmattrade.phase`'s phase table, which never puts either op in any
phase's `:auto` set. The `:public-health` tag is enforced upstream of
the governor at the point check 5 evaluates it directly; the governor's
job is dispatch/invoice authorization integrity, not distribution-
network optimization.

## Why this check is dispatch-only (unlike the household-goods sibling's recall flag)

Unlike the household-goods-wholesale sibling's own `active-recall-
unresolved` check (a POST-HOC, re-checked flag, because a product-safety
recall can be discovered from field data AFTER a SKU has already
dispatched cleanly), `lead-free-certification-missing` is evaluated
ONLY at `:delivery/dispatch`, the SAME onboarding-time span as
`:credit-cleared?`/`:contract-terms`. A lead-free certification, once
validly on file for a specific SKU at the moment of dispatch, does not
subsequently "un-happen" the way a newly-discovered product-safety
defect does -- there is no analogous real-world mechanic here requiring
a re-check at invoice-settlement time. This build considered mirroring
the household-goods sibling's post-hoc-flag shape and explicitly
rejected it as dishonest to this vertical's own regulatory mechanic; see
`docs/adr/0001-architecture.md` Decision 4.

## Type-gating, and why this build did NOT add a second check for heating equipment

This vertical's defining regulatory content is the US Reduction of Lead
in Drinking Water Act of 2011 (Pub. L. 111-380), amending Safe Drinking
Water Act §1417 (42 U.S.C. §300g-6): any pipe, pipe fitting, plumbing
fitting or fixture intended to convey or dispense water for human
consumption must be "lead free" -- not more than 0.25% lead by WEIGHTED
AVERAGE of the wetted surfaces, a real and significant tightening from
the prior 8% ceiling the original 1986 SDWA lead-ban amendments had set,
effective January 4, 2014. `lead-free-certification-missing` folds TWO
evidentiary arms of this SAME determination into ONE check (the
household-goods sibling's own Children's Product Certificate shape),
type-gated on the SINGLE fact `:potable-water-contact?` -- see
`docs/adr/0001-architecture.md` Decision 4 for why this is the correct
shape rather than the ag-machinery sibling's own two-independent-checks
shape.

ISIC 4663 explicitly names heating equipment alongside construction
materials, hardware and plumbing in its own scope, and a real,
citable-in-general-shape regulatory concern for gas/oil-fired heating
equipment safety certification (ANSI Z21-series / CSA-International
dual-listing, UL/NRTL listings) plausibly exists. This build
deliberately did NOT add a second, independently-gated HARD check for
it in this R0: unlike SDWA §1417 (a single, precisely-citable federal
statute with an exact numeric threshold and effective date), US
gas/oil-fired heating-equipment product-safety certification is not
anchored in one uniform federal per-product certification mandate --
NRTL listing requirements for this equipment class flow through a
patchwork of state/local model-code adoption (the International Fuel
Gas Code, NFPA 54) and general insurer/market practice, not a single
statute this build could cite with the SAME confidence. This build also
could not independently confirm the exact current ANSI Z21.x/CSA x.x
standard identifiers it would need for an honest `buildmattrade.facts`-
style G2 citation without risking an overclaimed spec-basis. See
`docs/adr/0001-architecture.md` Decision 5 for the full reasoning and
alternatives considered. `bo-8` (heating equipment, a gas furnace) is
still exercised in the demo/tests as a `lead-free-certification-missing`
NO-OP control case, proving the existing check correctly does not
overreach into heating equipment -- it is simply not gated by ANY
dedicated heating-equipment-specific check in this R0.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Wholesale of Construction Materials, Hardware, Plumbing and Heating Equipment |
|---|---|
| `:robotics` | The automated racking/stacking crane / gantry system that stages a SKU's pallet, pipe bundle, or carton at the wholesale yard/distribution-center dock -- lumber, pipe and rebar are commonly handled via automated racking/stacking/gantry-crane systems, and palletized hardware, plumbing fixtures and boxed heating equipment are carton/pallet-scale goods well-suited to AS/RS automation, in real-world building-materials-yard practice today. The governor never dispatches hardware itself: a dispatch-clearing action must have cleared the same sign-off a human trading supervisor would need (see Robotics Premise). |
| `:identity` | Trader, trading-supervisor, yard-operator and counterparty identity plus role-based access, so the governor's sign-off is tied to *who* authorized a dispatch or invoice, not just *that* someone did. |
| `:forms` | Structured intake for building-order booking, per-jurisdiction evidence capture (credit-clearance record, contract/PO, sanctions-screening record), potable-water-contact lab-test/certificate recording, and sanctions / credit exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:potable-water-safety-governor` Decision Rule itself (spec-basis, evidence completeness, credit-clearance, contract-on-file, lead-free-certification, sanctions-screening, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> dispatch -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across building-order intake, certification verification, physical dispatch, and invoice settlement, including the sanctions / credit escalation gate. |
| `:audit-ledger` | The immutable record of every verification, dispatch, invoice, sanctions flag, and hold -- this is what "an auditable, spec-cited trade record for every dispatch and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a dispatch or an invoice is later disputed, or a lead-content claim is later litigated. |
| `:optimization` | Pallet/bundle routing and distribution-network optimization -- selects the profitable fulfillment strategy for a yard. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:buildmattrade` capability library in this stack
(unlike a sibling with its own bespoke domain library): the building-
materials-trading checks (credit-clearance, contract-on-file, lead-free
certification, sanctions-screening) are direct entity boolean reads in
`buildmattrade.governor`, on top of the generic
robotics/identity/forms/dmn/bpmn/audit-ledger stack (see Capability
layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified,
  dispatched, or invoiced against
- a dispatch never starts with incomplete counterparty-diligence
  evidence
- a dispatch never starts with an uncleared counterparty credit, no
  contract-terms on file, or a missing lead-free certification on a
  potable-water-contact product
- an invoice never settles against an unresolved sanctions-screening
  flag
- sanctions / credit flags cannot be silently suppressed
- the same order can never be dispatched or invoiced twice
- a dispatch or invoice never auto-commits; both always need a human
  trading supervisor
- every dispatch and invoice (commit OR hold) leaves exactly one
  immutable ledger fact
- counterparty, credit, product-testing and certification data stays
  outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by
`buildmattrade.governor` as six HARD checks (a human approver cannot
override them) plus one SOFT gate, plus two double-actuation guards:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:certification/verify`, `:delivery/dispatch`, and
  `:invoice/settle`.
- `evidence-incomplete-violations` -- the evidence-completeness check
  above, for `:delivery/dispatch` / `:invoice/settle`.
- `credit-uncleared-violations` -- the counterparty-credit check above
  (the leasing collateral-coverage discipline applied to counterparty
  credit); evaluated on every `:delivery/dispatch`.
- `contract-missing-violations` -- the contract-on-file check above;
  evaluated on every `:delivery/dispatch`.
- `lead-free-certification-missing-violations` -- the PRE-SHIPMENT check
  above, type-gated on `:potable-water-contact?`, folding
  `:lead-content-tested?` AND `:lead-free-certificate-on-file?` into one
  rule; evaluated on every `:delivery/dispatch`.
- `counterparty-sanctions-flag-unresolved-violations` -- the sanctions-
  screening check above (the same open-flag-unresolved discipline the
  fuel-wholesale sibling's own check establishes); evaluated
  unconditionally on both `:delivery/dispatch` and `:invoice/settle`.
- `already-dispatched-violations` / `already-invoiced-violations` -- the
  double-actuation guards above, off dedicated `:dispatched?` /
  `:invoiced?` booleans (never a `:status` value), the same discipline
  every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:delivery/dispatch` / `:invoice/settle` stake, escalates to a human;
  and `buildmattrade.phase` independently never auto-commits either op
  at any phase.

Unlike the crude-extraction sibling's governor (which calls pure
physical range-check functions in its registry), this governor needs no
range-check functions at all: its domain checks read the
`building-order` record's own dedicated booleans directly.
`:delivery/dispatch` and `:invoice/settle` are the two real-world
actuation events (`#{:delivery/dispatch :invoice/settle}`), applied
SEQUENTIALLY to the SAME building-order (dispatch first, invoice
settlement later), the same sequential dual-actuation shape the fuel-
wholesale / ag-machinery-wholesale / household-goods-wholesale siblings
use. Neither ever auto-commits at any phase. Pallet/bundle routing and
distribution-network optimization (the `:optimization` line above) is a
follow-up slice, not in this R0 build -- see README `Business-process
coverage`.

## Capability layer

Unlike a sibling with its own bespoke capability library, this vertical
is SELF-CONTAINED: there is no `kotoba-lang/buildmattrade` to delegate
building-materials-trading validation to. The credit-clearance /
contract-on-file / lead-free-certification / sanctions-screening checks
live as direct entity boolean reads in `buildmattrade.governor` (off
dedicated `:credit-cleared?` / `:contract-terms` / `:lead-free-
certificate-on-file?` / `:sanctions-screened?` facts on the
`building-order` record) -- this vertical's governor needs no pure
range-check functions at all, because its domain checks ARE direct
boolean reads.

## Jurisdiction coverage (honest)

`buildmattrade.facts/catalog` currently seeds 4 jurisdictions with an
official spec-basis: the United States, Japan, Brazil and Australia.
This is a starting catalog to prove the governor contract end-to-end,
not a claim of global coverage (4 of ~194 jurisdictions worldwide).
Adding a jurisdiction is additive: one map entry in `buildmattrade.facts/
catalog`, citing a real official source -- never fabricate a
jurisdiction's requirements to make coverage look bigger.

**This build's own confidence caveats, flagged for independent
verification before operational reliance (the same "honest R0 coverage"
discipline every sibling's own facts namespace follows):**

- **USA (HIGH confidence).** The Reduction of Lead in Drinking Water Act
  of 2011 (Pub. L. 111-380), the Safe Drinking Water Act §1417 (42
  U.S.C. §300g-6) amendment, the 0.25%-weighted-average lead-free
  definition, its January 4, 2014 effective date, and the prior 8%
  ceiling it replaced, are all cited with HIGH confidence -- these were
  supplied directly as this build's own task specification and are
  independently well-documented EPA/statutory facts. NSF/ANSI/CAN 372
  and NSF/ANSI/CAN 61 as the relevant third-party certification
  standards, and NSF International as the best-known ANSI-accredited
  certifier, are also cited with HIGH confidence. This build's
  confidence that CSA Group, IAPMO R&T and UL Solutions ALSO operate
  ANSI-accredited certification programs against these SAME standards
  (as opposed to NSF International holding some form of exclusivity) is
  MODERATE, not high -- flagged for independent verification.
- **JPN (MODERATE confidence -- flagged explicitly, do NOT rely on this
  entry operationally without independent re-verification).** This
  build's understanding that 水道法 (Waterworks Act, Act No. 177 of
  1957) Article 16 and its implementing ministerial ordinance (给水装置
  の构造及び材质の基准に关する省令, 平成9年厚生省令第14号 / 1997) impose a
  leaching (浸出) performance standard on potable-water-contact
  materials, structurally analogous to the US weighted-average
  lead-free rule, is of MODERATE confidence -- the exact numeric
  leaching limit(s) are NOT independently verified here. This build's
  citation of 厚生労働省 (MHLW) as the owner-authority is explicitly
  flagged as possibly STALE: this build's understanding is that Japan's
  2024 administrative reorganization transferred water-supply
  infrastructure jurisdiction toward 国土交通省 (MLIT) and water-quality
  standard-setting toward 环境省 (the Ministry of Environment), but this
  build has NOT independently re-verified the exact post-reorganization
  split of authority. This build's citation of JWWA (日本水道协会)
  规格适合品 certification as the relevant private conformity mark is
  also of MODERATE confidence -- NOT independently confirmed as the
  exclusive or mandatory conformity route under current 水道法 practice
  (as opposed to one of several accepted routes).
- **BRA (added 2026-07-23 -- coverage gap disclosed, not papered over).**
  This build directly verified INMETRO's Sistema Brasileiro de Avaliação
  da Conformidade (SBAC) / Programa de Avaliação da Conformidade (PAC)
  compulsory certification for two building-materials items (reinforcing
  steel bars/wire, Portaria Inmetro n.° 139/2021; malleable cast-iron
  pipe fittings, Portaria Inmetro n.° 390/2021) and the Código de Defesa
  do Consumidor (Lei n.° 8.078/1990) Art. 31 labeling requirement -- HIGH
  confidence, both fetched and read directly. This build did NOT find a
  Brazilian analog of the domain-defining lead-free potable-water-contact-
  material mandate (structurally analogous to USA's SDWA §1417 or JPN's
  水道法 leaching standard); the ANVISA/MS water-potability ordinance this
  build tried to check for a materials angle was unreachable this session
  (connection reset, no Wayback snapshot) -- see `buildmattrade.facts`
  namespace docstring "BRA coverage gap (honest)" for the full disclosure.
- **AUS (added 2026-07-23 -- HIGH confidence, and unlike BRA, a domain-
  defining lead-free mandate WAS found and independently verified).**
  National Construction Code (NCC) Volume Three -- the Plumbing Code of
  Australia (PCA) 2022 -- Clause A5G4 defines 'lead free' as a weighted
  average lead content of not more than 0.25% for any plumbing product or
  material in contact with drinking water, enforced via the Australian
  Building Codes Board (ABCB)'s mandatory WaterMark Certification Scheme.
  This build fetched and read this definition verbatim directly from two
  live ABCB pages (watermark.abcb.gov.au/certification/lead-free-plumbing-
  products and abcb.gov.au/news/2026/update-transition-lead-free-plumbing-
  products), and independently corroborated it via the National Health and
  Medical Research Council (NHMRC)'s Australian Drinking Water Guidelines
  Q&A page, which states this same PCA 2022 definition "aligns... with the
  United States Safe Drinking Water Act" and separately reports the ADWG's
  own health-based drinking-water guideline value for lead was lowered
  from 0.01 mg/L (1996) to 0.005 mg/L (June 2025 update). The NHMRC page
  itself was unreachable live this session (connection reset) and was
  instead fetched and read via an Internet Archive Wayback Machine
  snapshot -- flagged, not hidden. This build did NOT confirm whether
  AS/NZS 4020 is formally invoked for the lead-free determination
  specifically (the ABCB pages define "lead free" directly via Clause
  A5G4's own wording and never name AS/NZS 4020; Standards Australia
  paywalls the standard text itself), and did NOT independently cite a
  specific Australian sanctions statute (DFAT's sanctions page was
  unreachable live and had no Wayback snapshot this session) -- see
  `buildmattrade.facts` namespace docstring "AUS verification notes
  (honest)" for the full disclosure.
- **No second jurisdiction-agnostic heating-equipment-certification
  entry seeded.** As explained above ("Type-gating, and why this build
  did NOT add a second check for heating equipment"), this build's
  confidence in citing a precise, uniform federal-level product-safety
  certification mandate for gas/oil-fired heating equipment (as opposed
  to SDWA §1417's own precisely-citable mandate) is only MODERATE, so no
  `buildmattrade.facts` catalog entry or dedicated HARD check was built
  for it in this R0.

## Maturity

`:implemented` -- `BuildMatTradeAdvisor` + `Potable Water Safety
Governor` run as real, tested code (`clojure -M:dev:test`; lint clean),
following the SAME governed-actor architecture as the other prior
actors across this fleet, with its own distinct, independently-named
governor and its own direct-entity-boolean building-materials-trading
checks. See `docs/adr/0001-architecture.md` for the history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. In this domain
an automated racking/stacking crane / gantry system stages a SKU's
pallet, pipe bundle, or carton at the wholesale yard/distribution-center
dock, under the actor, gated by the independent **Potable Water Safety
Governor**. The governor never dispatches hardware itself: a dispatch-
clearing action must have cleared the same sign-off a human trading
supervisor would need. A robot may stage a pallet or bundle, but only
after the governor (every HARD check clean) and a human supervisor both
agree it is safe to -- the same operating-state-machine-gated-by-
governor premise every cloud-itonami vertical restates
(ADR-2607011000): the blueprint declares `:robotics true`, the README
names the robot that performs the physical act, and the Potable Water
Safety Governor is the independent gate that robot's command must pass.

**Why `:robotics true` here, unlike the ag-machinery-wholesale
sibling's own `:robotics false`?** Considered carefully, not defaulted:
the ag-machinery sibling's `:delivery/dispatch` op gates a
self-propelled machine driven off the lot by a human operator, or a
towed implement hitched by a human-driven tow vehicle -- no comparable
fixed, robot-actuatable apparatus exists for THAT entity in general
commercial practice. This vertical's own governed entity is different:
bulk lumber, pipe and rebar are commonly handled via automated racking/
stacking/gantry-crane systems in modern building-materials yards, and
palletized hardware, plumbing fixtures and boxed heating equipment are
carton/pallet-scale goods well-suited to AS/RS automation (the SAME
apparatus class the household-goods-wholesale sibling's own `:robotics
true` blueprint cites for carton-scale consumer goods, extended here to
yard-scale building materials via racking/crane automation) -- unlike a
self-propelled tractor that drives itself off a lot under a human
operator's direct control. See `docs/adr/0001-architecture.md` Decision
6 for the full reasoning, including the alternative considered and
rejected.
