# Operator Guide

## First Deployment
1. Register wholesalers, yards/distribution centers, building-orders,
   and trading supervisors.
2. Import building-order, counterparty, credit, sanctions and trade
   history.
3. Seed the per-jurisdiction spec-basis catalog (`buildmattrade.facts`)
   for the jurisdictions you actually trade in, citing real official
   sources only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure sanctions / credit escalation and accounts-receivable
   accounts.
6. Publish a dry-run dispatch/invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, dispatch, or invoice
- full counterparty-diligence evidence (credit-clearance record,
  contract/PO, sanctions-screening record) before any dispatch
- credit-clearance, contract-on-file, lead-free certification (for
  potable-water-contact products) and sanctions-screening checks before
  any dispatch; sanctions-screening checks before any invoice
- sanctions / credit escalation gate
- audit export for every dispatch, invoice, and hold
- backup manual dispatch and invoicing process

## A Day in the Life: Intake → Verify → Dispatch → Settle → Audit

Wholesale of Construction Materials, Hardware, Plumbing and Heating
Equipment (ISIC 4663, `cloud-itonami-isic-4663`) runs on the same
intake / advise / govern / decide / commit-or-hold loop as every
itonami blueprint, but here the loop is concrete: a regional building-
materials wholesaler needs to bring a building-order (say, a pallet of
lead-free-certified kitchen faucets destined for a plumbing contractor
in the United States) from intake through certification verification
to a physical dispatch and an invoice settlement. Walking through one
order, end to end:

1. **Intake.** The wholesaler books the building-order through
   `:forms`: order-id, product-category, SKU, counterparty, price,
   contract-terms, jurisdiction, whether it is a potable-water-contact
   product, and the order's own diligence record (credit-cleared?,
   sanctions-screened?). This creates a building-order record at
   `:order/intake` status. The BuildMatTradeAdvisor only normalizes the
   patch; it does not invent the order-id, counterparty, jurisdiction,
   product-category, or any commercial/diligence value.
2. **Verify.** The BuildMatTradeAdvisor drafts a per-jurisdiction
   contract / sanctions evidence checklist (`:certification/verify`)
   from `buildmattrade.facts`, citing the jurisdiction's official
   spec-basis (owner authority, legal basis, provenance) and listing
   the required evidence (credit-clearance record, contract/PO,
   sanctions-screening record). The `:potable-water-safety-governor`
   sign-off gate must clear: it checks the jurisdiction actually has an
   official spec-basis on file (never invent one). A jurisdiction with
   no spec-basis is a HARD hold at the governor node -- it never even
   reaches a human. This verification always escalates to a human for
   approval; it is never auto.
3. **Dispatch.** Before building materials can leave the wholesale yard,
   the `:potable-water-safety-governor` sign-off gate runs the full HARD
   check set against the order's own ground truth: the spec-basis
   exists, the evidence checklist is complete, the counterparty's credit
   has been cleared, contract-terms are on file, IF this is a potable-
   water-contact product it has a valid lead-free certification on file
   (both the underlying NSF/372 lead-content lab test and the NSF/61-
   scope certificate itself), the counterparty has passed sanctions
   screening, and the order has not already been dispatched. Any
   failure is a HARD hold that a human cannot override. If every check
   is clean, the proposal STILL always escalates to a human trading
   supervisor -- a `:delivery/dispatch` never auto-commits at any phase.
   On approval, the dispatch record is drafted
   (`<JURISDICTION>-DISPATCH-000001`) and the order's `:dispatched?`
   flag is set.
4. **Settle.** Once building materials have actually been dispatched,
   the invoice is settled (`:invoice/settle`): the money side of the
   trade, custody / financial transfer. The governor re-checks the
   spec-basis, the evidence completeness, the sanctions screening, and
   that this order's invoice has not already been settled -- note that,
   unlike sanctions screening, the lead-free certification is NOT
   re-checked here (it is a pre-shipment, onboarding-time fact fixed at
   dispatch, not a post-hoc concern -- see `docs/adr/
   0001-architecture.md` Decision 4). As with the dispatch, a clean
   invoice STILL always escalates to a human trading supervisor --
   `:invoice/settle` never auto-commits. On approval the invoice record
   is drafted (`<JURISDICTION>-INVOICE-000001`) and the order's
   `:invoiced?` flag is set.
5. **Audit.** The verification, the dispatch sign-off, the dispatch
   record, the invoice sign-off, and the invoice record are all appended
   to the `:audit-ledger` -- immutable and exportable, so a counterparty
   or regulatory dispute can be traced back to the exact spec-basis
   citation, evidence checklist, and supervisor sign-off that authorized
   the dispatch and invoice. If something is wrong with the order (a
   credit deterioration, a sanctions hit, a missing lead-free
   certification), that gets raised as a flag and routed through the
   escalation gate instead of being silently suppressed -- a dispatch or
   invoice for that order then waits on governor sign-off of the flag's
   resolution.

### A special case: why the lead-free certification check does NOT need a mid-lifecycle re-check

Unlike a product-safety recall (which can be discovered from field data
AFTER a product has already shipped, and so must be re-checked at every
later actuation), a lead-free certification is a pre-shipment fact: once
a specific plumbing product's NSF/372 lead-content test and NSF/61-scope
certificate are validly on file at the moment of dispatch, that
compliance status does not later "un-happen" the way a newly-discovered
defect does. This build therefore deliberately evaluates
`lead-free-certification-missing` ONLY at `:delivery/dispatch`, the
SAME onboarding-time span as credit-clearance and contract-on-file --
not re-checked at `:invoice/settle`. See `docs/adr/
0001-architecture.md` Decision 4 for the full reasoning, including why
this build considered and rejected modeling it as a post-hoc, re-checked
flag (the shape a sibling actor uses for a genuinely different,
discovered-defect regulatory concern).

## Feel the Decision Gate: `clojure -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:potable-water-safety-governor` gate exists is the
bundled demo, which walks one clean building-order through intake →
verify → dispatch → settle (each dispatch/settle pausing for human
approval), then a fully-certified potable-water-contact product through
the same lifecycle, then exercises every HARD-hold failure mode in
isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a counterparty whose credit has not been cleared → HOLD
  (`:credit-uncleared`),
- an order with no contract-terms on file → HOLD (`:contract-missing`),
- a counterparty that has not passed sanctions screening → HOLD
  (`:counterparty-sanctions-flag-unresolved`),
- a potable-water-contact product with no valid lead-free certification
  on file → HOLD (`:lead-free-certification-missing`),
- a double dispatch of the same order → HOLD (`:already-dispatched`),
- a double invoice of the same order → HOLD (`:already-invoiced`) --
  and, crucially, an ordinary construction material AND a piece of
  heating equipment BOTH dispatch cleanly with none of these
  certification facts on file at all, proving the check is genuinely
  type-gated rather than a blanket rule.

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed dispatch readiness (credit-clearance, contract-on-file,
lead-free certification for potable-water-contact products, sanctions-
screening), and human review for every dispatch- and invoice-affecting
action.
