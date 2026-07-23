# Governance

`cloud-itonami-isic-0610` is an OSS open-business blueprint for community
crude petroleum extraction: well intake, per-jurisdiction well-construction/
well-control/sour-service regulatory assessment, crude lift and production
settlement.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a well lift or production settlement can never be approved without a
  spec-basis citation, complete evidence, an in-window reservoir pressure,
  an annular pressure below MAASP, an acceptable water cut, an H2S
  concentration below the IDLH, and no unresolved integrity flag.
- the Well Safety Governor remains independent of CrudeAdvisor.
- hard policy violations (fabricated spec-basis, unsupported evidence,
  out-of-range reservoir/annular pressure, excessive water cut, H2S above
  IDLH, unresolved integrity flag, double lift/settlement) cannot be
  overridden by human approval.
- every intake, assessment, lift and settlement decision is auditable
  (immutable ledger).
- well, lease/concession and production data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage
contract, public business model, operator certification or license should add
or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing well-lift or production-settlement policy checks
- mishandling well, lease/concession or production data
- misrepresenting certification status
- failing to respond to security incidents
