# Contributing

`cloud-itonami-isic-4663` accepts contributions to the OSS blueprint, the
Potable Water Safety Governor, decision-rule tests, documentation and
operator model.

## Development
The capability layer is SELF-CONTAINED. There is no pre-existing bespoke
building-materials-wholesale capability library to wrap; the counterparty-
credit / contract-on-file / lead-free-certification / sanctions-screening
checks live directly in `buildmattrade.governor`. This repo holds the
business blueprint, the langgraph-clj actor and the operator contracts.

```bash
clojure -M:dev:test
clojure -M:lint
```

## Rules
- Do not commit real counterparty, credit, product-testing, or
  certification data.
- Keep physical dispatch and invoice settlement behind the Potable Water
  Safety Governor.
- Treat certification-compliance workflows as high-risk: add tests for
  spec-basis, evidence completeness, credit clearance, contract-on-file,
  lead-free certification, sanctions screening and audit logging.
- Keep `lead-free-certification-missing` type-gated on
  `:potable-water-contact?` -- do not silently apply it to ordinary
  construction material or heating equipment, and do not re-check it at
  `:invoice/settle` (see `docs/adr/0001-architecture.md` Decision 4 for
  why it is dispatch-only).
- Do not add a second heating-equipment-specific HARD check without first
  independently confirming a precisely-citable regulatory mandate (see
  `docs/adr/0001-architecture.md` Decision 5) -- do not overclaim a
  spec-basis to make coverage look bigger.
- Never fabricate a jurisdiction's building-materials-trade requirements
  in `buildmattrade.facts` -- cite a real official source or leave the
  jurisdiction out of the catalog.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which governor invariant is
affected, how it was tested, whether operator or certification docs need
updates.
