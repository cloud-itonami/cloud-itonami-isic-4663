# Governance

`cloud-itonami-isic-4663` is an OSS open-business blueprint for wholesale
of construction materials, hardware, plumbing and heating equipment.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a building-order whose jurisdiction has no official spec-basis can never
  be verified, dispatched or invoiced.
- the Potable Water Safety Governor remains independent of the advisor.
- hard governor violations (a fabricated spec-basis, incomplete
  counterparty-diligence evidence, an uncleared counterparty credit, a
  missing contract, a missing lead-free certification on a potable-water-
  contact product, an unresolved OFAC-style sanctions flag, a double
  dispatch, or a double invoice) cannot be overridden by human approval.
- `lead-free-certification-missing` remains type-gated on
  `:potable-water-contact?` and evaluated ONLY at `:delivery/dispatch` --
  never applied as a blanket rule, and never re-checked at
  `:invoice/settle` without a documented ADR revision.
- every intake, certification verification, dispatch, settlement and hold
  is auditable.
- counterparty, credit, product-testing and certification data stays
  outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing dispatch or invoice-settlement policy checks
- mishandling counterparty, credit, product-testing, or certification data
- misrepresenting certification status
- failing to respond to security incidents
