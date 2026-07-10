# ADR-0001: BuildMatTradeAdvisor ⊣ Potable Water Safety Governor architecture

## Status

Accepted. `cloud-itonami-isic-4663` published directly at `:implemented`
in the `kotoba-lang/industry` registry (no prior `:blueprint`-tier-only
scaffold stage for this repo).

## Context

`cloud-itonami-isic-4663` publishes an OSS business blueprint for
wholesale of construction materials, hardware, plumbing and heating
equipment (building-order intake, per-jurisdiction contract / sanctions
regulatory verification, potable-water-product certification compliance
verification, physical dispatch, and invoice settlement). Like every
prior actor in this fleet, the blueprint alone is not an implementation:
this ADR records the governed-actor architecture that establishes it as
real, tested code, following the same langgraph StateGraph + independent
Governor + Phase 0->3 rollout pattern established by
`cloud-itonami-isic-6511` (life insurance) and applied across many prior
siblings, most closely the fuel-wholesale (`cloud-itonami-isic-4671`),
ag-machinery-wholesale (`cloud-itonami-isic-4653`) and household-goods-
wholesale (`cloud-itonami-isic-4649`) siblings.

Like those three siblings, this vertical has NO bespoke domain
capability library in `kotoba-lang` to wrap (no
`kotoba-lang/buildmattrade`-style repo exists). This build therefore
uses self-contained domain logic -- the same pattern the majority of
this fleet's actors use. The building-materials-trading checks
(credit-clearance, contract-on-file, lead-free certification, sanctions-
screening) are direct entity boolean reads in `buildmattrade.governor`,
off dedicated `:credit-cleared?` / `:contract-terms` / `:lead-free-
certificate-on-file?` / `:sanctions-screened?` facts on the
`building-order` record -- NO pure range-check functions are needed.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:potable-water-safety-governor`, is a fresh, independent build --
grep-verified against every `blueprint.edn` in the fleet (including the
existing `cloud-itonami-isic-3600` `:water-safety-governor` for water
utility supply/treatment, and `cloud-itonami-isic-4322`
`:plumbing-trade-governor` for plumbing INSTALLATION trade -- this
build's own governor identity is a distinct exact string, and its own
domain, wholesale PRODUCT trading rather than utility operations or
installation trade, does not overlap either sibling's).

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:potable-water-safety-governor` is grep-verified unique across every
`blueprint.edn` in this fleet. This build follows the SAME
governed-actor architecture as every prior actor, but with its own
distinct governor identity.

### Decision 2: self-contained domain logic, direct entity booleans (no `kotoba-lang/buildmattrade` to wrap, and no range-check functions to host)

Unlike a sibling that delegates to a real, pre-existing bespoke
capability library, and unlike the crude-extraction sibling (which
hosts pure physical range-check functions in its registry because its
governor re-verifies measured physical values), this building-
materials-wholesale vertical needs NEITHER: there is no pre-existing
building-materials-trading capability library to delegate to, AND the
governor's domain checks (credit-clearance, contract-on-file, lead-free
certification, sanctions-screening) are direct entity boolean reads off
the `building-order` record's own dedicated facts -- not measured-
value-vs-limit range comparisons. So `buildmattrade.registry` is RECORD
CONSTRUCTION ONLY (no range-check functions), and `buildmattrade.
governor` reads the order's booleans directly.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `building-order` entity

Like the fuel-wholesale / ag-machinery-wholesale / household-goods-
wholesale siblings' own order entities, this vertical's `dispatch` and
`settle` actuation events apply SEQUENTIALLY to the SAME `building-
order` -- physical dispatch happens first (materials leave the
wholesale yard/distribution center), invoice settlement happens later
(the money side of the trade, custody / financial transfer), on the
same order record. This matches the sequential shape every principal-
trading sibling in this fleet uses, unlike a `:kind`-distinguished
alternative-action shape. `high-stakes` is `#{:delivery/dispatch
:invoice/settle}`; neither ever auto-commits at any phase.

### Decision 4: lead-free-certification-missing is ONE check folding TWO evidentiary arms of the SAME determination -- the household-goods shape, NOT the ag-machinery shape -- and is dispatch-only, NOT a post-hoc re-checked flag

This vertical's own defining regulatory content is the US Reduction of
Lead in Drinking Water Act of 2011 (Pub. L. 111-380), amending Safe
Drinking Water Act §1417 (42 U.S.C. §300g-6): any pipe, pipe fitting,
plumbing fitting or fixture intended to convey or dispense water for
human consumption must be "lead free" -- not more than 0.25% lead by
WEIGHTED AVERAGE of the wetted surfaces (a real, significant tightening
from the prior 8% ceiling the original 1986 SDWA lead-ban amendments had
set), effective January 4, 2014. Compliance is demonstrated via
independent third-party certification to NSF/ANSI/CAN 372 (the
weighted-average lead-content TEST) and NSF/ANSI/CAN 61 (the broader
health-effects CERTIFICATE for drinking water system components).

`lead-free-certification-missing-violations` is type-gated on a SINGLE
fact, `:potable-water-contact?`, and internally requires BOTH
`:lead-content-tested?` (the NSF/372 lab test) AND `:lead-free-
certificate-on-file?` (the NSF/61-scope certificate itself).

**Why the household-goods sibling's Children's Product Certificate
shape fits here, and the ag-machinery sibling's own two-independent-
checks shape does NOT.** The household-goods sibling's own
`childrens-product-certificate-missing-violations` folds TWO
evidentiary arms of the SAME determination (a lab test, and the
certificate itself) into ONE rule, gated on a SINGLE fact
(`:childrens-product?`), because the two arms are not independently
triggered by anything else -- a CPC cannot exist without the underlying
lab test, and a lab test without a filed CPC is not yet a compliant
certificate either. THIS vertical's own two facts,
`:lead-content-tested?` and `:lead-free-certificate-on-file?`, have the
IDENTICAL relationship: a compliant NSF/61-scope lead-free certificate
cannot exist without the underlying NSF/372 weighted-average lab test,
and a lab test without the certificate itself actually on file is not
yet a compliant certification either. Missing EITHER one is EQUALLY 'no
valid lead-free certification on file'. Contrast the ag-machinery
sibling's own emissions-certificate/ROPS-certificate pair, which are
governed by TWO GENUINELY DIFFERENT regulatory regimes (air quality vs.
operator safety) triggered by TWO INDEPENDENT machine properties that
vary separately (a machine can be engine-powered without being ride-on,
and vice versa) -- THIS vertical has no such independent-triggering-
property structure for its own certification concern:
`:potable-water-contact?` is the ONLY gating fact.

`buildmattrade.store/demo-data`'s `bo-1` (ordinary construction
material -- lumber, NEITHER evidentiary sub-fact on file) and `bo-8`
(heating equipment -- a gas furnace, also not potable-water-contact)
TOGETHER prove the type-gate from TWO DIFFERENT non-potable-water
angles that ISIC 4663 itself names in its own scope (construction
materials AND heating equipment): both dispatch cleanly, because
neither has anything to certify against a potable-water standard in the
first place -- a stronger proof than either alone, since it shows the
type-gate correctly excludes BOTH other major product categories this
actor trades in, not merely the one used as the 'base' demo order.
`bo-6` (a potable-water-contact kitchen faucet, BOTH sub-facts true)
proves the check is satisfiable; `bo-7` (a potable-water-contact copper
pipe fitting, lab-tested but NO certificate actually filed) proves the
fold requires BOTH arms, not merely that testing occurred -- a
realistic gap, since lab testing against NSF/372 often completes before
the NSF/61-scope certificate paperwork itself is finalized and filed.

**Why this check is dispatch-only, NOT a post-hoc re-checked flag like
the household-goods sibling's own `active-recall-unresolved`.** The
household-goods sibling's active-recall check is evaluated at BOTH
`:delivery/dispatch` AND `:invoice/settle` because a product-safety
recall is discoverable from field data AFTER a SKU has already
dispatched cleanly -- CPSA §15(b) is fundamentally a "keep monitoring"
duty, not a "prove clean once" duty. This build considered mirroring
that shape for lead-free certification and explicitly REJECTED it: a
lead-free certification, once validly on file for a specific SKU at the
moment of dispatch, is not the kind of fact that later "un-happens" the
way a newly-discovered product defect does. There is no analogous
real-world regulatory mechanic here requiring a re-check at invoice-
settlement time -- SDWA §1417 is a "prove clean before the wetted
surfaces meet the wetted-surfaces public" duty (a pre-shipment
certification duty), structurally like `:credit-cleared?`/
`:contract-terms` and the household-goods sibling's own Children's
Product Certificate check, NOT like CPSA §15(b)'s post-hoc monitoring
duty. Modeling it as dispatch-only rather than force-fitting a
post-hoc-flag shape onto a regulatory mechanic that does not have one is
the honest choice, following the SAME "genuinely different regulatory
shapes get genuinely different check shapes, not forced uniformity"
discipline the household-goods sibling's own Decision 5 established.

### Decision 5: deliberately NO second HARD check for heating-equipment safety certification

ISIC 4663 explicitly names heating equipment alongside construction
materials, hardware and plumbing in its own scope. A real,
citable-in-general-shape regulatory concern for gas/oil-fired heating-
equipment safety certification (an ANSI Z21-series / CSA-International
dual-listing regime for gas appliances, or UL/NRTL listings for
oil-fired equipment) plausibly exists, and would have been a natural
candidate for a SECOND, independently-gated HARD check -- mirroring
either the household-goods sibling's own two-genuinely-different-
regulatory-shapes pattern, or the ag-machinery sibling's own
two-independent-boolean-gated-checks pattern.

This build considered that path and explicitly REJECTED it for THIS
R0, for reasons distinct from (and more fundamental than) mere
scope-trimming:

1. **Confidence-tier mismatch with SDWA §1417.** The Reduction of Lead
   in Drinking Water Act is a single, precisely-citable FEDERAL statute
   with an exact numeric threshold (0.25% weighted average) and an
   exact effective date (January 4, 2014) -- this build's confidence in
   that citation is HIGH. US gas/oil-fired heating-equipment product-
   safety certification, by contrast, is NOT anchored in one uniform
   federal per-product certification mandate of the same kind: NRTL
   (Nationally Recognized Testing Laboratory) listing requirements for
   this equipment class flow through a PATCHWORK of state/local
   adoption of model codes (the International Fuel Gas Code, NFPA 54)
   and general insurer/market practice, not a single statute this build
   could cite with the SAME confidence tier.
2. **Precise-citation risk.** Building a HARD governor check implies
   asserting a citable regulatory MANDATE, not merely a plausible
   regulatory shape. This build could not independently confirm the
   exact current ANSI Z21.x / CSA x.x dual-listing standard identifiers
   it would need to write an honest `buildmattrade.facts`-style G2
   citation without risking an OVERCLAIMED spec-basis -- the same
   discipline the household-goods sibling's own `docs/business-model.md`
   "Jurisdiction coverage (honest)" section already established
   fleet-wide (flag genuine uncertainty rather than fabricate
   precision, and do not force a second check merely to match a
   sibling's own pattern when the citation confidence is not there).

`buildmattrade.store/demo-data`'s `bo-8` (heating equipment, a gas
furnace) is still exercised in the demo/tests -- as a `lead-free-
certification-missing` NO-OP control case (see Decision 4), proving the
EXISTING check correctly does not overreach into heating equipment.
This is NOT the same as heating equipment being ungoverned: it simply
is not gated by ANY dedicated heating-equipment-specific HARD check in
this R0. A future PR could add one once a precisely-citable regime is
independently confirmed, following this fleet's "extending coverage is
additive" convention.

### Decision 6: `counterparty-sanctions-flag-unresolved?` -- the open-flag-unresolved discipline

An unresolved sanctions-screening flag -- the counterparty has not
passed OFAC / equivalent sanctions screening -- is a HARD,
un-overridable hold. This reuses the SAME open-flag-unresolved
discipline the fuel-wholesale sibling's `counterparty-sanctions-flag-
unresolved-violations` check (and the freight sibling's
`delivery-exception-unresolved?` check) establish -- an open concern
cannot be silently suppressed to force a dispatch or invoice through.
Evaluated UNCONDITIONALLY at both `:delivery/dispatch` and
`:invoice/settle`.

### Decision 7: dedicated double-actuation-guard booleans

`:dispatched?` / `:invoiced?` are dedicated booleans on the
`building-order` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 8: Store protocol, MemStore + DatomicStore parity

`buildmattrade.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore`
(`langchain.db`-backed), proven to satisfy the same contract in
`test/buildmattrade/store_contract_test.clj`. The ledger stays
append-only on every backend: which building-order was verified for a
jurisdiction with no official spec-basis, which counterparty had
credit-uncleared / no contract / a missing lead-free certification / an
unresolved sanctions-screening flag, which order was dispatched, which
invoice was settled, on what jurisdictional basis, approved by whom --
always a query over an immutable log.

### Decision 9: Phase 0->3 with `:delivery/dispatch`/`:invoice/settle` NEVER auto

`buildmattrade.phase`'s phase table puts `:order/intake` (no direct
capital risk) in phase 3's `:auto` set as its only member;
`:delivery/dispatch` and `:invoice/settle` are deliberately ABSENT from
every phase's `:auto` set, including phase 3 -- a permanent structural
fact. `buildmattrade.governor`'s high-stakes gate enforces the same
invariant independently: two layers agree that actuation is always a
human trading supervisor's call.

### Decision 10: mock + LLM advisor pair

`buildmattrade.buildmattradeadvisor` provides a deterministic
`mock-advisor` (default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-dispatch
building materials or auto-settle an invoice.

### Decision 11: `:robotics true` -- yard/pallet-scale automation, a reasoned departure from the ag-machinery sibling's `:robotics false`

Considered carefully rather than defaulted either way. The ag-machinery
sibling set `:robotics false` because its own `:delivery/dispatch` op
gates a self-propelled machine driven off the lot by a human operator,
or a towed implement hitched by a human-driven tow vehicle -- no
comparable fixed, robot-actuatable apparatus exists for THAT entity in
general commercial practice today.

This vertical's own governed entity is materially different: bulk
lumber, pipe and rebar are commonly handled via automated racking/
stacking/gantry-crane systems in modern building-materials-yard
practice, and palletized hardware, plumbing fixtures and boxed heating
equipment are genuinely carton/pallet-scale SKUs well-suited to AS/RS
(automated storage and retrieval system) automation -- the SAME
apparatus class the household-goods-wholesale sibling's own `:robotics
true` blueprint already cites for carton-scale consumer goods, extended
here to yard-scale building materials via racking/gantry-crane
automation. `blueprint.edn` therefore sets `:itonami.blueprint/robotics
true`, and `:required-technologies` includes `:robotics`.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/buildmattrade` capability library.**
  Considered and explicitly ruled out: no such library exists. Forcing
  a false capability-library integration would be dishonest; this
  build correctly uses self-contained domain logic instead.
- **Splitting `lead-free-certification-missing` into TWO independent
  checks** (mirroring the ag-machinery sibling's emissions/ROPS split,
  one for the NSF/372 lab test and one for the NSF/61-scope certificate
  separately). Considered and ruled out (see Decision 4): the two
  evidentiary sub-facts are not independently triggered by anything
  else -- they are two arms of the SAME determination, not two
  genuinely different regulatory regimes with independently-varying
  triggering properties. Splitting them would manufacture a false
  independence the underlying law does not have.
- **Modeling `lead-free-certification-missing` as a post-hoc, re-checked
  flag** (mirroring the household-goods sibling's own
  `active-recall-unresolved`, re-evaluated at BOTH `:delivery/dispatch`
  and `:invoice/settle`). Considered and REJECTED (see Decision 4): this
  would misrepresent SDWA §1417's actual "prove clean before dispatch"
  mechanic as a "keep monitoring after the fact" mechanic it does not
  have -- a lead-free certification does not later "un-happen" the way
  a newly-discovered product-safety defect does.
- **Adding a SECOND HARD check for heating-equipment safety
  certification** (an ANSI Z21-series / CSA-International / UL-NRTL-
  style check, mirroring either the household-goods sibling's own
  two-genuinely-different-shapes pattern or the ag-machinery sibling's
  own two-independent-checks pattern). Considered and explicitly
  REJECTED for this R0 (see Decision 5): this build's confidence in
  citing a precise, uniform federal-level mandate for this equipment
  class (as opposed to SDWA §1417's own precisely-citable mandate) is
  only MODERATE, and building a HARD check implies asserting a citable
  mandate this build cannot honestly back to the SAME confidence tier.
  Not forcing a second check merely to match a sibling's own pattern is
  the honest choice here, per the fleet's own "flag uncertainty rather
  than overclaim" discipline.
- **A `:kind`-distinguished entity** (matching the retail sibling's
  `order` shape). Rejected: dispatch and invoice settlement happen
  SEQUENTIALLY on the SAME building-order in this domain, not as
  alternative actions -- the fuel-wholesale / ag-machinery-wholesale /
  household-goods-wholesale cluster's sequential shape is the honest
  match here.
- **`:robotics false`, mirroring the ag-machinery sibling.** Considered
  and rejected (see Decision 11): this vertical's own catalog (bulk
  lumber/pipe/rebar via racking/gantry-crane automation, palletized
  hardware/fixtures/heating-equipment via AS/RS) is materially unlike
  the ag-machinery sibling's own self-propelled/towed large-equipment
  entity.
- **Building pallet/bundle routing / distribution-network optimization
  in this R0.** Rejected in favor of a scoped R0 slice (the
  `:optimization` capability is correctly marked required, the
  integration is a follow-up), consistent with this fleet's 'extending
  coverage is additive' convention.

## Consequences

- Fresh independent actor in this fleet, following the SAME
  governed-actor architecture as every prior sibling.
- Establishes a NEW, honestly-scoped type-gating proof for this fleet:
  a single check folding two evidentiary arms of one determination
  (the household-goods sibling's own shape), proven type-gated by TWO
  independent non-potable-water control cases drawn from two different
  named categories of the SAME ISIC code, plus an explicit, reasoned
  decision NOT to add a second domain check where citation confidence
  did not support one.
- `MemStore` || `DatomicStore` parity is proven by
  `test/buildmattrade/store_contract_test.clj`.
- Lint is clean; `clojure -M:dev:test` passes with 0 failures (see
  repository test-run output for current counts); the demo
  (`clojure -M:dev:run`) walks one clean dispatch + invoice lifecycle,
  the type-gating control pair (`bo-1`/`bo-8`), the satisfiable-
  certification proof (`bo-6`), plus six other HARD-hold scenarios,
  end-to-end.
- `blueprint.edn` sets `:robotics true` and includes `:robotics` in
  `:required-technologies`, a reasoned departure from the ag-machinery
  sibling's own `:robotics false` grounded in this vertical's own
  yard/pallet-scale catalog.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; origin of the sequential dual-actuation shape and
  the self-contained-domain-logic pattern this build follows)
- `cloud-itonami-isic-4653/docs/adr/0001-architecture.md` (ag-machinery-
  wholesale sibling; contrast: two checks split on two independent
  booleans, and `:robotics false` reasoning this build's own Decision 11
  distinguishes from)
- `cloud-itonami-isic-4649/docs/adr/0001-architecture.md` (household-
  goods-wholesale sibling; origin of the single-check-folding-two-
  evidentiary-arms shape this build's own Decision 4 follows, and of the
  post-hoc-re-checked-flag shape this build's own Decision 4 considered
  and rejected; also origin of the `:robotics true` carton/pallet-scale
  reasoning this build's own Decision 11 extends to yard-scale goods)
- Reduction of Lead in Drinking Water Act of 2011 (Pub. L. 111-380),
  amending Safe Drinking Water Act §1417 (42 U.S.C. §300g-6) -- US EPA
- NSF/ANSI/CAN 372 (Drinking Water System Components -- Lead Content);
  NSF/ANSI/CAN 61 (Drinking Water System Components -- Health Effects)
  -- ANSI-accredited third-party certification standards
- 水道法 (Waterworks Act, Act No. 177 of 1957) Article 16; 给水装置の
  构造及び材质の基准に关する省令 (平成9年厚生省令第14号, 1997) -- Japan,
  厚生労働省 (historically; post-2024-reorganization authority split not
  independently re-verified by this build, see `docs/business-model.md`
  "Jurisdiction coverage (honest)")
