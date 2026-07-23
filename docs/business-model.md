# Business Model: Community Crude Petroleum Extraction

## Classification
- Repository: `cloud-itonami-isic-0610`
- ISIC Rev.5: `0610` — extraction of crude petroleum
- Domain: `upstream/crude-extraction`
- Social impact: crew safety, environmental protection, transparency
- Governor: `:well-safety-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers well intake through per-jurisdiction well-construction /
well-control / sour-service regulatory assessment, crude lift (opening a real
well to flow against a live reservoir), and production settlement (royalty /
royalty-volume finalization and custody transfer) for a community crude
producer. It does **not**, by itself, hold any mining or concession right or
operating authority required to run a crude-extraction business in a given
jurisdiction, perform the actual physical drilling or workover, or judge
reservoir economics (reservoir simulation and recovery-factor optimization is
a follow-up slice, not this R0). Whoever deploys a live instance supplies the
jurisdiction-specific operating authority, the real wellhead/workover-robot
and SCADA/production-accounting integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited execution
scaffold so the operator does not have to build the compliance layer from
scratch.

## Customer
- regional and community crude producers and field operators
- independent operators and marginal/onshore-field operators leaving closed
  SCADA / production-AI SaaS
- national-oil-company subsidiaries running community fields
- royalty owners and regulators who need an auditable, spec-cited well record

## Offer
- well intake and directory management
- per-jurisdiction well-construction / well-control / sour-service regulatory
  assessment with an official spec-basis citation
- crude lift (starting flow) gated on full evidence and a clean well-integrity
  / sour-service envelope
- production settlement (royalty / royalty-volume finalization, custody
  transfer) with double-settlement prevention
- evidence checklisting (mining/concession right, casing-integrity log, BOP
  test record, cementing record)
- integrity-flag and exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per operator / field
- support retainer with SLA
- SCADA and production-accounting integration

## The `:well-safety-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:well-safety-governor`. It
is the single authority that stands between "a well could be opened to flow"
and "a well is allowed to flow," and between "a production period could be
settled" and "it is allowed to settle." Every rule it enforces is traceable
to the domain (Community Crude Petroleum Extraction, ISIC 0610) and to the
three `:social-impact` tags in `blueprint.edn` (`:safety`, `:environmental-
protection`, `:transparency`).

This is the rule the companion contract test (`test/crude/governor_contract_
test.clj`) encodes end-to-end: the CrudeAdvisor never lifts crude from a well
or settles production the Well Safety Governor would reject, `:well/lift` and
`:production/settle` NEVER auto-commit at any phase, `:well/intake` (no direct
capital risk) MAY auto-commit when clean, and every decision (commit OR hold)
leaves exactly one ledger fact.

**Authorizes a crude lift (`:well/lift`) or production settlement
(`:production/settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:reservoir/assess`, `:well/lift`, or
   `:production/settle` proposal whose jurisdiction has no entry in the
   `crude.facts` catalog (`:no-spec-basis`). This is the direct enforcement of
   `:transparency`: a jurisdiction whose well-construction / well-control /
   sour-service requirements cannot be traced to an OFFICIAL public source is
   never guessed. The advisor must not fabricate a jurisdiction's requirements.
2. **The jurisdiction's required evidence is fully on file** -- for a lift or
   settlement the well's jurisdiction must have been assessed with a complete
   well-construction / well-control evidence checklist on record: the
   mining/concession right, the casing-integrity log, the blowout-preventer
   (BOP) test record, and the cementing record (`:evidence-incomplete`). This
   protects `:safety` and `:environmental-protection`: a well that cannot prove
   zonal isolation and well-control readiness never flows.
3. **The measured reservoir pressure stays inside its declared safe window
   `[min, max]`** -- the governor INDEPENDENTLY re-verifies the well's own
   recorded reservoir pressure against its two-sided safe operating envelope
   (`crude.registry/reservoir-pressure-out-of-range?`, the aerospace two-sided-
   tolerance discipline applied to subsurface pressure). Below `min` risks
   formation damage / water-or-gas coning; above `max` risks formation fracture
   and loss of zonal isolation -- the precursor to an underground blowout
   (`:reservoir-pressure-out-of-range`).
4. **The annular pressure is below the Maximum Allowable Annular Surface
   Pressure (MAASP)** -- the governor INDEPENDENTLY re-verifies the well's own
   annulus pressure against its MAASP via the pure function
   `crude.registry/well-integrity-annular-pressure-excessive?` (the fabrication
   measured-ratio-vs-rated-limit discipline). Annular pressure above MAASP can
   lift the casing shoe off its seat and lose zonal isolation: this is a true
   (surface) blowout precursor, evaluated ahead of any flow-rate signal
   (`:well-integrity-annular-pressure-excessive`).
5. **The basic-sediment-and-water (BS&W) cut is below the declared maximum** --
   the governor INDEPENDENTLY re-verifies the well's water cut against its
   declared `water-cut-max` (`crude.registry/water-cut-excessive?`). Producing
   above the water-cut limit risks emulsion-handling overload and separator
   carry-over; it is a lift-readiness gate (`:water-cut-excessive`).
6. **The H2S concentration is below the NIOSH Immediately-Dangerous-to-Life-or-
   Health (IDLH) threshold (50 ppm)** -- the governor INDEPENDENTLY re-verifies
   the well's H2S concentration against the jurisdiction's IDLH
   (`crude.registry/h2s-toxic?`, the fabrication measured-value-vs-rated-limit
   discipline, applied to sour-service toxicity). Lifting from a well whose H2S
   exceeds the IDLH without the sour-service controls the evidence checklist
   demands exposes the crew to a lethal gas (`:h2s-toxic-threshold`).
7. **No unresolved integrity flag is open on the well** -- an integrity flag
   raised by this proposal itself or already on file, and not yet resolved, is
   a hard, un-overridable hold (`:integrity-flag-unresolved`). Integrity
   concerns cannot be silently suppressed to force a lift or settlement through.
8. **The well has not already been lifted, and production has not already been
   settled** -- a double lift of the same well is refused off a dedicated
   `:crude-lifted?` fact, and a double settlement off a dedicated
   `:production-settled?` fact (never a `:status` value), the double-actuation
   guard every sibling actor in this fleet enforces (`:already-lifted` /
   `:already-settled`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of the
above fail.** A proposal with no spec-basis, incomplete evidence, a reservoir
pressure outside its window, an annular pressure above MAASP, a water cut above
the BSW limit, an H2S concentration above the IDLH, an open integrity flag, or
a double lift/settlement is held at the governor node -- a human approver
cannot override these, by construction.

**Always escalates to a human (never auto-commits) for `:well/lift` and
`:production/settle`**, even when every check above is clean. Lifting crude
from a real well (starting flow against a live reservoir) and settling real
production (real money and real royalty volumes moving between operator and
royalty owner) are the two real-world actuation events this actor performs;
both are always a human production superintendent's call. This is enforced by
TWO independent layers that agree on purpose: the governor's confidence /
actuation SOFT gate (a `:well/lift` / `:production/settle` stake always
escalates) and `crude.phase`'s phase table, which never puts either op in any
phase's `:auto` set. The `:environmental-protection` tag is enforced upstream
of the governor, in the reservoir-assessment evidence step -- the governor's
job is lift/settlement authorization integrity, not recovery-factor
optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Community Crude Petroleum Extraction |
|---|---|
| `:robotics` | The autonomous wellhead/workover robot that performs the physical act of opening a well tree to flow (and eventually closing it). The governor never dispatches hardware itself: a lift-clearing action must have cleared the same sign-off a human production superintendent would need (see Robotics Premise). |
| `:identity` | Operator, production-superintendent, and crew identity plus role-based access, so the governor's sign-off is tied to *who* authorized a lift or settlement, not just *that* someone did. |
| `:forms` | Structured intake for well booking, per-jurisdiction evidence capture (mining right, casing log, BOP test, cementing record), and integrity-flag submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:well-safety-governor` Decision Rule itself (spec-basis, evidence completeness, the four physical range checks, the integrity flag, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> assess -> lift -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across well intake, reservoir assessment, crude lift, and production settlement, including the integrity-flag escalation gate. |
| `:audit-ledger` | The immutable record of every assessment, lift, settlement, integrity flag, and hold -- this is what "an auditable, spec-cited well record for every lift and settlement" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a lift or settlement is later disputed by a royalty owner or regulator. |
| `:optimization` | Reservoir simulation and recovery-factor optimization -- selects the recovery strategy for a field. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:petroleum` capability library in this stack (unlike the
freight sibling's `:logistics`): the well-safety range checks (reservoir-
pressure window, annular/MAASP, water cut, H2S/IDLH) are self-contained pure
functions in `crude.registry`, on top of the generic robotics/identity/forms/
dmn/bpmn/audit-ledger stack (see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be assessed, lifted, or
  settled against
- a lift never starts with incomplete well-construction / well-control evidence
- a lift never starts outside the reservoir-pressure window, above the annular
  MAASP, above the water-cut limit, above the H2S IDLH, or with an open
  integrity flag
- integrity flags cannot be silently suppressed
- the same well can never be lifted or settled twice
- a lift or settlement never auto-commits; both always need a human
  production superintendent
- every lift and settlement (commit OR hold) leaves exactly one immutable
  ledger fact
- well, reservoir, and production data stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by `crude.governor` as nine
HARD checks (a human approver cannot override them) plus one SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on every
  `:reservoir/assess`, `:well/lift`, and `:production/settle`.
- `evidence-incomplete-violations` -- the evidence-completeness check above,
  for `:well/lift` / `:production/settle`.
- `reservoir-pressure-out-of-range-violations` -- the two-sided reservoir-
  pressure window above, an honest reapplication of the aerospace two-sided-
  tolerance discipline to subsurface pressure; evaluated unconditionally on
  every `:well/lift`.
- `well-integrity-annular-pressure-excessive-violations` -- the annular/MAASP
  check above, an honest reapplication of the fabrication measured-ratio-vs-
  rated-limit discipline; a true blowout precursor, evaluated unconditionally
  on every `:well/lift`.
- `water-cut-excessive-violations` -- the BS&W check above, an honest
  reapplication of the fabrication ratio discipline; evaluated on every
  `:well/lift`.
- `h2s-toxic-threshold-violations` -- the H2S/IDLH check above, an honest
  reapplication of the fabrication measured-value-vs-rated-limit discipline
  to sour-service toxicity, grounded in the NIOSH IDLH (50 ppm); evaluated
  unconditionally on every `:well/lift`.
- `integrity-flag-unresolved-violations` -- the open-integrity-flag check
  above (the same open-flag-unresolved discipline the freight sibling's
  delivery-exception-unresolved check establishes); evaluated unconditionally
  on every `:well/lift`.
- `already-lifted-violations` / `already-settled-violations` -- the double-
  actuation guards above, off dedicated `:crude-lifted?` / `:production-
  settled?` booleans (never a `:status` value), the same discipline every
  sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:well/lift` / `:production/settle` stake, escalates to a human; and
  `crude.phase` independently never auto-commits either op at any phase.

`:well/lift` and `:production/settle` are the two real-world actuation events
(`#{:well/lift :production/settle}`), applied SEQUENTIALLY to the SAME well
(lift first, settlement later) rather than the retail sibling's `:kind`-
distinguished alternative-action shape -- the same sequential dual-actuation
shape the repair-shop and quarrying clusters use. Neither ever auto-commits at
any phase. Reservoir simulation and recovery-factor optimization (the
`:optimization` line above) is a follow-up slice, not in this R0 build -- see
README `Business-process coverage`.

## Capability layer

Unlike `cloud-itonami-isic-4920` (which wraps a pre-existing bespoke
capability library `kotoba-lang/logistics`), this vertical is SELF-CONTAINED:
there is no `kotoba-lang/petroleum` to delegate well-safety validation to. The
reservoir-pressure / annular-vs-MAASP / water-cut / H2S-vs-IDLH range checks
live as pure functions in `crude.registry` and are re-verified independently
by the governor, rather than wrapping an external capability library's own
validated function -- the same 'reuse a capability's own validated function'
discipline, here applied to this vertical's OWN pure registry functions.

## Jurisdiction coverage (honest)

`crude.facts/catalog` currently seeds 5 jurisdictions with an official spec-
basis, each a REAL regime: Japan (METI Mine Safety Regulations / 鉱山保安規則
over crude-oil and natural-gas wells), the United States (BSEE, 30 C.F.R. Part
250, plus OSHA Process Safety Management), the United Kingdom (HSE Offshore
Safety Division), Norway (Petroleum Safety Authority, Activities
Regulations), and Brazil (ANP, Resolução ANP nº 46/2016, Sistema de
Gerenciamento da Integridade de Poços -- SGIP). The NIOSH H2S IDLH (50 ppm) is
the internationally cited acute-toxicity reference each of these regulators'
sour-service rules are ultimately grounded in. This is a starting catalog to
prove the governor contract end-to-end, not a claim of global coverage (5 of
~194 jurisdictions worldwide). Adding a jurisdiction is additive: one map entry in
`crude.facts/catalog`, citing a real official source -- never fabricate a
jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `CrudeAdvisor` + `Well Safety Governor` run as real, tested
code (`clojure -M:dev:test`: 41 tests / 204 assertions, 0 failures; lint
clean), promoted from the originally-published `:blueprint`-tier scaffold,
following the SAME governed-actor architecture as the other prior actors
across this fleet, with its own distinct, independently-named governor and its
own self-contained well-safety range checks. See `docs/adr/0001-architecture.
md` for the history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. In this domain an
autonomous wellhead or workover robot performs the physical act of opening a
well tree to flow (and eventually closing it), under the actor, gated by the
independent **Well Safety Governor**. The governor never dispatches hardware
itself: a lift-clearing action must have cleared the same sign-off a human
production superintendent would need. A robot may turn the valve, but only
after the governor (every HARD check clean) and a human superintendent both
agree it is safe to -- the same operating-state-machine-gated-by-governor
premise every cloud-itonami vertical restates (ADR-2607011000): the blueprint
declares `:robotics true`, the README names the robot that performs the
physical act, and the Well Safety Governor is the independent gate that
robot's command must pass.
