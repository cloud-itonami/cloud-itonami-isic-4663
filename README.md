# cloud-itonami-isic-4663

Open Business Blueprint for **ISIC Rev.5 4663**: Wholesale of
Construction Materials, Hardware, Plumbing and Heating Equipment --
building-order intake, per-jurisdiction counterparty-diligence /
potable-water-product regulatory verification, physical dispatch, and
invoice settlement for a wholesale trader of construction materials,
hardware, plumbing fixtures/pipes/fittings and heating equipment.

This repository publishes a building-materials-wholesale actor --
building-order intake, per-jurisdiction contract / sanctions /
potable-water-product regulatory verification, physical dispatch and
invoice settlement -- as an OSS business that any qualified operator
can fork, deploy, run, improve and sell, so a regional building-
materials wholesaler never surrenders counterparty, credit, product-
certification and trade data to a closed distribution / ERP SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **BuildMatTradeAdvisor
⊣ Potable Water Safety Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:potable-water-safety-
governor`, is a fresh, independent build.

**Like the fuel-wholesale / ag-machinery-wholesale / household-goods-
wholesale siblings, this vertical is SELF-CONTAINED**: there is no
`kotoba-lang/buildmattrade` to delegate building-materials-trading
validation to, so the credit-clearance / contract-on-file / lead-free-
certification / sanctions-screening checks live as direct entity
boolean reads in `buildmattrade.governor` (off dedicated
`:credit-cleared?` / `:contract-terms` / `:lead-free-certificate-on-
file?` / `:sanctions-screened?` facts on the `building-order` record),
rather than wrapping an external capability library's own validated
function.

> **Why an actor layer at all?** An LLM is great at drafting an order
> summary, normalizing records, and reading a compliance file -- but it
> has **no notion of which jurisdiction's potable-water-product law is
> official, no license to dispatch real building materials to a
> counterparty or settle a real invoice, and no way to know on its own
> whether the counterparty's credit has actually been cleared, whether a
> plumbing fixture actually has a valid lead-free certification on
> file, or whether OFAC / equivalent sanctions screening has actually
> been passed**. Letting it dispatch goods or settle an invoice
> directly invites fabricated regulatory citations, lead-contaminated
> plumbing fixtures reaching a household's drinking water, and an
> invoice settling against a sanctioned party -- exposing the operator
> to real enforcement, product-liability and financial liability, for
> whoever runs it. This project seals the BuildMatTradeAdvisor into a
> single node and wraps it with an independent **Potable Water Safety
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers building-order intake through contract / sanctions /
potable-water-product regulatory verification, physical dispatch and
invoice settlement. It does **not**, by itself, hold any wholesale
licence, importer-of-record registration or operating authority
required to run a building-materials-wholesale business in a given
jurisdiction, and it does not claim to. It also does not perform the
actual physical yard pick/pack/ship itself, or judge distribution-
network economics -- pallet/bundle-level automation and route
optimization (the blueprint's own `:optimization` technology) is a
follow-up slice, not in this R0. This build also deliberately does NOT
add a dedicated heating-equipment safety-certification check -- see
`docs/adr/0001-architecture.md` Decision 5 for the full reasoning.
Whoever deploys and operates a live instance (a qualified trading
supervisor / yard operator) supplies any jurisdiction-specific
operating authority, the real yard-automation and ERP / accounts-
receivable integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution scaffold
so that operator does not have to build the compliance layer from
scratch.

### Actuation

**Dispatching real building materials to a counterparty from the
wholesale yard/distribution center and settling a real invoice are
never autonomous, at any phase, by construction.** Two independent
layers enforce this (`buildmattrade.governor`'s `:delivery/dispatch`/
`:invoice/settle` high-stakes gate and `buildmattrade.phase`'s phase
table, which never puts either op in any phase's `:auto` set) -- see
`buildmattrade.phase`'s docstring and
`test/buildmattrade/phase_test.clj`'s
`delivery-dispatch-never-auto-at-any-phase`/
`invoice-settle-never-auto-at-any-phase`. The actor may draft, check
and recommend; a human trading supervisor is always the one who
actually dispatches building materials or settles an invoice. Grounded
in potable-water-product-safety doctrine (the same discipline every
regulator in `buildmattrade.facts` codifies: a real dispatch and a real
invoice settlement are human sign-off acts) -- a genuine DUAL-actuation
shape, applied SEQUENTIALLY to the SAME building-order (dispatch first,
invoice settlement later), matching the fuel-wholesale / ag-machinery-
wholesale / household-goods-wholesale siblings' own sequential shape.

## The core contract

```
building-order intake + jurisdiction facts (buildmattrade.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌─────────────────────────┐
   │ BuildMatTradeAdvisor   │ ─────────────▶ │ Potable Water Safety     │  (independent system)
   │ (sealed)               │  + citations    │ Governor                │
   └───────────────────────┘                 │ spec-basis · evidence-   │
          │                 commit ◀┼ incomplete · credit-uncleared ·  │
          │                         │ contract-missing · lead-free-    │
    record + ledger        escalate ┼ certification-missing ·          │
          │              (ALWAYS for│ counterparty-sanctions-flag-     │
          │       :delivery/        │ unresolved · already-dispatched ·│
          │       dispatch/         │ already-invoiced                 │
          │       :invoice/         └─────────────────────────┘
          │       settle)
          ▼
      human approval
```

**The BuildMatTradeAdvisor never dispatches building materials to a
counterparty or settles an invoice the Potable Water Safety Governor
would reject, and never does so without a human sign-off.** Hard
violations (fabricated regulatory requirements; unsupported evidence;
an uncleared counterparty credit; no contract-terms on file; a potable-
water-contact product with no valid lead-free certification; an
unresolved sanctions-screening flag; a double dispatch/invoice) force
**hold** and *cannot* be approved past; a clean dispatch/invoice
proposal still always routes to a human.

## One domain-defining check, honestly type-gated -- and a reasoned decision against a second

This vertical's own defining regulatory content is the US Reduction of
Lead in Drinking Water Act of 2011 (Pub. L. 111-380), amending Safe
Drinking Water Act §1417 (42 U.S.C. §300g-6): any pipe, pipe fitting,
plumbing fitting or fixture intended to convey or dispense water for
human consumption must be "lead free" -- not more than 0.25% lead by
WEIGHTED AVERAGE of the wetted surfaces (a real, significant tightening
from the prior 8% ceiling, effective January 4, 2014). This build
models `lead-free-certification-missing` as ONE check folding TWO
evidentiary arms of the SAME NSF/ANSI/CAN 372 (lead-content test) +
NSF/ANSI/CAN 61 (health-effects certificate) determination into ONE
rule, type-gated on `:potable-water-contact?` -- the household-goods
sibling's own Children's Product Certificate shape (see
`docs/adr/0001-architecture.md` Decision 4).

**Proven type-gated by TWO different non-potable-water control cases,
not just one.** `bo-1` (ordinary construction material, lumber) and
`bo-8` (heating equipment, a gas furnace) are BOTH `:potable-water-
contact?` false -- and both dispatch cleanly with neither evidentiary
sub-fact on file. ISIC 4663 explicitly names both construction
materials AND heating equipment in its own scope, so proving the check
is a true no-op for BOTH is a stronger proof than either control case
alone. `bo-6` (a fully-certified kitchen faucet) proves the check is
satisfiable; `bo-7` (a copper pipe fitting, lab-tested but no
certificate filed) proves the fold requires BOTH arms.

**This build deliberately did NOT add a second HARD check for heating-
equipment safety certification** (e.g. an ANSI Z21-series / CSA-
International / UL-NRTL-style regime for gas/oil-fired appliances),
even though heating equipment is explicitly named in ISIC 4663's own
scope. Unlike SDWA §1417 (a single, precisely-citable federal statute
with an exact numeric threshold and effective date), this build's
confidence in citing a precise, uniform federal-level product-safety
certification mandate for heating equipment is only MODERATE -- see
`docs/adr/0001-architecture.md` Decision 5 for the full reasoning and
alternatives considered.

## Run

```bash
clojure -M:dev:run     # walk one clean dispatch + invoice lifecycle, the type-gating control pair, plus HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work** where a real, fixed, robot-
actuatable apparatus exists for this actor's own governed entity. Here
an automated racking/stacking crane / gantry system stages the SKU's
pallet, pipe bundle, or carton at the wholesale yard/distribution-center
dock -- bulk lumber, pipe and rebar are commonly handled via automated
racking/stacking/gantry-crane systems, and palletized hardware,
plumbing fixtures and boxed heating equipment are carton/pallet-scale
goods well-suited to AS/RS automation, in real-world building-
materials-yard practice today (unlike, say, the ag-machinery sibling's
own large self-propelled/towed equipment, which is driven off the lot
by a human operator and has no comparable fixed apparatus --
`:robotics false` there). The governor never dispatches hardware
itself: a dispatch-clearing action must have cleared the same sign-off
a human trading supervisor would need. This restates the fleet-wide
robotics premise three ways (ADR-2607011000): the blueprint declares
`:robotics true`, the README names the robot that performs the physical
act, and the Potable Water Safety Governor is the independent gate that
robot's command must pass -- a robot may stage a pallet or bundle, but
only after the governor and a human supervisor both agree it is safe
to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Potable Water Safety Governor, dispatch/invoice draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4663`). Like the fuel-wholesale / ag-machinery-wholesale / household-
goods-wholesale siblings, this vertical is NOT backed by a separate
bespoke domain capability lib: the building-materials-trading checks
(credit-clearance, contract-on-file, lead-free certification,
sanctions-screening) are direct entity boolean reads in
`buildmattrade.governor`, on top of the generic
robotics/identity/forms/dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/buildmattrade/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + dispatch AND invoice history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:invoiced?` booleans rather than a `:status` value |
| `src/buildmattrade/registry.cljc` | Dispatch/invoice draft records (record construction only -- the Potable Water Safety Governor's checks are direct entity boolean reads, so there are no pure range-check functions to host here) |
| `src/buildmattrade/facts.cljc` | Per-jurisdiction generic counterparty-diligence catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/buildmattrade/buildmattradeadvisor.cljc` | **BuildMatTradeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/certification-verification/dispatch/invoice proposals |
| `src/buildmattrade/governor.cljc` | **Potable Water Safety Governor** -- 6 HARD checks (spec-basis · evidence-incomplete · credit-uncleared · contract-missing · lead-free-certification-missing · counterparty-sanctions-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/buildmattrade/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (dispatch/invoice always human; order intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/buildmattrade/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/buildmattrade/sim.cljc` | demo driver |
| `test/buildmattrade/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers building-order intake through contract / sanctions /
potable-water-product regulatory verification, physical dispatch and
invoice settlement -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Building-order intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:order/intake`/`:certification/verify`) | Real yard-automation/ERP integration, pallet/bundle routing and distribution-network economics |
| Physical dispatch, HARD-gated on full evidence, a credit-cleared counterparty, contract-terms on file, a valid lead-free certification for potable-water-contact products, a passed sanctions screen and no double-dispatch (`:delivery/dispatch`) | A dedicated heating-equipment safety-certification check (see `docs/adr/0001-architecture.md` Decision 5) |
| Invoice settlement, HARD-gated on full evidence, a passed sanctions screen and no double-invoice (`:invoice/settle`) | |
| Immutable audit ledger for every intake/verification/dispatch/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a heating-
equipment safety-certification check once a precisely-citable regime is
independently confirmed) as its own governed op or dedicated HARD check
with its own tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`buildmattrade.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `buildmattrade.facts/catalog`
-- currently 2 seeded (USA, JPN) out of ~194 jurisdictions worldwide.
This is a starting catalog to prove the governor contract end-to-end,
not a claim of global coverage. Adding a jurisdiction is additive: one
map entry in `buildmattrade.facts/catalog`, citing a real official
source -- never fabricate a jurisdiction's requirements to make
coverage look bigger. See `docs/business-model.md` "Jurisdiction
coverage (honest)" for this build's own confidence caveats on specific
citations (independent verification recommended before operational
reliance, especially for the JPN entry).

## Maturity

`:implemented` -- `BuildMatTradeAdvisor` + `Potable Water Safety
Governor` run as real, tested code (see `Run` above), following the
SAME governed-actor architecture as the other prior actors across this
fleet, with its own distinct, independently-named governor and its own
direct-entity-boolean building-materials-trading checks. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
