# Operator Guide

## First Deployment
1. Register operators, fields, wells, and production superintendents.
2. Import well, reservoir, and production history.
3. Seed the per-jurisdiction spec-basis catalog (`crude.facts`) for the
   jurisdictions you actually operate in, citing real official sources only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure integrity-flag escalation and production-accounting accounts.
6. Publish a dry-run settlement and audit export.

## Minimum Production Controls
- spec-basis validation before any assessment, lift, or settlement
- full well-construction / well-control evidence (mining right, casing log,
  BOP test, cementing record) before any lift
- reservoir-pressure window, annular/MAASP, water-cut, H2S/IDLH and integrity-
  flag checks before any lift
- integrity-flag escalation gate
- audit export for every lift, settlement, and hold
- backup manual lift and production-settlement process

## A Day in the Life: Intake → Assess → Lift → Settle → Audit

Community Crude Petroleum Extraction (ISIC 0610, `cloud-itonami-isic-0610`)
runs on the same intake / advise / govern / decide / commit-or-hold loop as
every itonami blueprint, but here the loop is concrete: a regional producer
needs to bring a well (say, an onshore oil well in the Minami-Akita field)
from intake through reservoir safety assessment to a crude lift and a
production settlement. Walking through one well, end to end:

1. **Intake.** The operator books the well through `:forms`: field name, well
   name, operator, jurisdiction, and the well's own physical record (API
   gravity, sulfur content, H2S concentration, BS&W water cut and its declared
   maximum, measured reservoir pressure with its safe window [min, max],
   annular pressure and MAASP, flow rate). This creates a well record at
   `:well/intake` status. The CrudeAdvisor only normalizes the patch; it does
   not invent the field name, operator, jurisdiction, or any physical value.
2. **Assess.** The CrudeAdvisor drafts a per-jurisdiction well-construction /
   well-control / sour-service evidence checklist (`:reservoir/assess`) from
   `crude.facts`, citing the jurisdiction's official spec-basis (owner
   authority, legal basis, provenance) and listing the required evidence
   (mining/concession right, casing-integrity log, BOP test record, cementing
   record). The `:well-safety-governor` sign-off gate must clear: it checks the
   jurisdiction actually has an official spec-basis on file (never invent one).
   A jurisdiction with no spec-basis is a HARD hold at the governor node -- it
   never even reaches a human. This assessment always escalates to a human for
   approval; it is never auto.
3. **Lift.** Before the well can be opened to flow, the `:well-safety-governor`
   sign-off gate runs the full HARD check set against the well's own ground
   truth: the spec-basis exists, the evidence checklist is complete, the
   measured reservoir pressure is inside `[min, max]`, the annular pressure is
   below MAASP, the BS&W water cut is below its maximum, the H2S concentration
   is below the NIOSH IDLH (50 ppm), no integrity flag is open, and the well
   has not already been lifted. Any failure is a HARD hold that a human cannot
   override. If every check is clean, the proposal STILL always escalates to a
   human production superintendent -- a `:well/lift` never auto-commits at any
   phase. On approval, the lift record is drafted (`<JURISDICTION>-LIFT-000001`)
   and the well's `:crude-lifted?` flag is set.
4. **Settle.** Once crude has actually been lifted, the production period is
   settled (`:production/settle`): royalty / royalty-volume finalization and
   custody transfer. The governor re-checks the spec-basis, the evidence
   completeness, and that this well's production has not already been settled.
   As with the lift, a clean settlement STILL always escalates to a human
   production superintendent -- `:production/settle` never auto-commits. On
   approval the settlement record is drafted (`<JURISDICTION>-PROD-000001`)
   and the well's `:production-settled?` flag is set.
5. **Audit.** The assessment, the lift sign-off, the lift record, the
   settlement sign-off, and the settlement record are all appended to the
   `:audit-ledger` -- immutable and exportable, so a royalty or custody
   dispute can be traced back to the exact spec-basis citation, evidence
   checklist, and superintendent sign-off that authorized the lift and
   settlement. If something is wrong with the well (a pressure anomaly, a
   casing concern, a sour-service excursion), that gets raised as an integrity
   flag and routed through the escalation gate instead of being silently
   suppressed -- a lift for that well then waits on governor sign-off of the
   flag's resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: a well assessed against a fabricated
spec-basis, a lift started with incomplete evidence or outside the reservoir-
pressure window, an integrity flag suppressed to force a lift through, or a
settlement posted without a human sign-off.

## Feel the Decision Gate: `clojure -M:dev:run`

This vertical has no companion playable prototype yet (unlike the freight
sibling's `itonami/freight-dispatch` game). The fastest hands-on way to feel
why the `:well-safety-governor` gate exists is the bundled demo, which walks
one clean well through intake → assess → lift → settle (each lift/settle
pausing for human approval) and then exercises every HARD-hold failure mode
in isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a measured reservoir pressure outside the safe window → HOLD
  (`:reservoir-pressure-out-of-range`),
- an annular pressure above MAASP → HOLD
  (`:well-integrity-annular-pressure-excessive`),
- a BS&W water cut above the limit → HOLD (`:water-cut-excessive`),
- an H2S concentration above the IDLH → HOLD (`:h2s-toxic-threshold`),
- an unresolved integrity flag → HOLD (`:integrity-flag-unresolved`),
- a double lift of the same well → HOLD (`:already-lifted`),
- a double settlement of the same well → HOLD (`:already-settled`).

Each HOLD settles at the governor node and never reaches a human approver --
the same failure mode the audit ledger is built to catch and the minimum
production controls above are built to prevent. It is not a substitute for
those controls, but it is the fastest way for a new operator (or a reviewer)
to feel, hands-on, why the gate exists before touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded assessment, evidence-backed
lift readiness (reservoir pressure, annular/MAASP, water cut, H2S/IDLH,
integrity flag), and human review for every lift- and settlement-affecting
action.
