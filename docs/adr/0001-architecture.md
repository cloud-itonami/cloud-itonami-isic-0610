# ADR-0001: CrudeAdvisor ⊣ Well Safety Governor architecture

## Status

Accepted. `cloud-itonami-isic-0610` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-0610` publishes an OSS business blueprint for community
crude petroleum extraction (well intake, per-jurisdiction well-construction /
well-control / sour-service regulatory assessment, crude lift, and production
settlement). Like every prior actor in this fleet, the blueprint alone is not
an implementation: this ADR records the governed-actor architecture that
promotes it to real, tested code, following the same langgraph StateGraph +
independent Governor + Phase 0->3 rollout pattern established by
`cloud-itonami-isic-6511` (life insurance) and applied across 91 prior
siblings, most recently `cloud-itonami-isic-0162` (community agronomy).

Like `cloud-itonami-isic-0162` and `cloud-itonami-isic-0810` (quarrying),
this vertical has NO bespoke domain capability library in `kotoba-lang` to
wrap (verified: no `kotoba-lang/petroleum`-style repo exists, and
`kotoba-lang/robotics` is the generic cross-cutting robotics contract every
cloud-itonami vertical already uses, not a domain-specific library for this
vertical). This build therefore uses self-contained domain logic -- the same
pattern the majority of this fleet's actors use, and the explicit
differentiator from `cloud-itonami-isic-4920` (which wraps a pre-existing
`kotoba-lang/logistics` library). The well-safety range checks (reservoir-
pressure window, annular/MAASP, water cut, H2S/IDLH) live as pure functions
in `crude.registry` and are re-verified independently by the governor.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:well-safety-governor`, is grep-verified UNIQUE fleet-wide -- no naming-
collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:well-safety-governor` is grep-verified unique across every `blueprint.edn`
in this fleet. This build follows the SAME governed-actor architecture as
every prior actor, but with its own distinct governor identity.

### Decision 2: self-contained domain logic (no `kotoba-lang/petroleum` to wrap)

Unlike `cloud-itonami-isic-4920` (freight, which delegates tracking-number
validation to a real, pre-existing `kotoba-lang/logistics` capability
library), this crude-extraction vertical has NO pre-existing petroleum
capability library to delegate well-safety validation to. The four physical
range checks (reservoir-pressure window, annular-vs-MAASP, water cut,
H2S-vs-IDLH) are therefore pure functions defined in `crude.registry` and
called directly by `crude.governor` -- the SAME 'reuse a capability's own
validated function' discipline `retailops.governor`'s ean13 check
establishes for a capability library, here applied to this vertical's OWN
pure registry functions rather than a separate library. No literal code is
shared with any sibling (different domain), but the discipline is the same.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `well` entity

Unlike the retail sibling's `order` entity (distinguished by `:kind`,
alternative sale-or-reorder actions), this vertical's `lift` and `settle`
actuation events apply SEQUENTIALLY to the SAME `well` -- a crude lift
happens first (flow started against a live reservoir), production settlement
happens later (royalty / royalty-volume finalization, custody transfer), on
the same well record. This matches the repair-shop cluster's `ticket` and
the quarrying cluster's `extraction` shape (two real-world acts, in order,
on one entity). `high-stakes` is `#{:well/lift :production/settle}`;
neither ever auto-commits at any phase.

### Decision 4: the well-safety physical range-check suite -- honest reapplications of established fleet disciplines

The four physical range checks the governor runs on every `:well/lift` are
each an honest reapplication of an established fleet discipline to an
upstream-petroleum value, documented as such rather than claimed as novel
inventions (the same convention `cloud-itonami-isic-0162`'s Decision 3
establishes for `dose-matches-claim?`):

- `reservoir-pressure-out-of-range?` reapplies the **aerospace two-sided-
  tolerance** discipline to subsurface pressure: the well's measured reservoir
  pressure must stay inside its declared safe window `[min, max]`. Below `min`
  risks formation damage / water-or-gas coning; above `max` risks formation
  fracture and loss of zonal isolation (an underground-blowout precursor).
- `well-integrity-annular-pressure-excessive?` reapplies the **fabrication
  measured-ratio-vs-rated-limit** discipline to the annulus vs the Maximum
  Allowable Annular Surface Pressure (MAASP). Annular pressure above MAASP can
  lift the casing shoe off its seat and lose zonal isolation: a true (surface)
  blowout precursor, evaluated ahead of any flow-rate signal.
- `water-cut-excessive?` reapplies the **fabrication measured-ratio-vs-rated-
  limit** discipline to the basic-sediment-and-water (BS&W) cut vs the well's
  declared `water-cut-max` -- a lift-readiness gate (emulsion/separator
  protection), not a toxicity gate.
- `h2s-toxic?` reapplies the **fabrication measured-value-vs-rated-limit**
  discipline to the well's H2S concentration vs the jurisdiction's NIOSH
  Immediately-Dangerous-to-Life-or-Health threshold, grounded in the real
  NIOSH IDLH for hydrogen sulfide of **50 ppm** -- the internationally cited
  acute-toxicity reference each regulator's sour-service rules are ultimately
  grounded in.

Each returns `true` when the value is provably OUTSIDE the safe envelope; the
conservative well-safety choice, missing data is a violation (cannot verify
safe to lift). All four are evaluated UNCONDITIONALLY on every `:well/lift`.
No new unconditional-evaluation ordinals are claimed: every check in this
suite is a discipline-reapplication, documented per Decision 3 of
`cloud-itonami-isic-0162`.

### Decision 5: `integrity-flag-unresolved?` -- the open-flag-unresolved discipline

An integrity flag raised by the proposal itself or already on file, and not
yet resolved, is a HARD, un-overridable hold. This reuses the SAME
open-flag-unresolved discipline the freight sibling's
`delivery-exception-unresolved?` check (and the parksafety sibling's flag
checks) establish -- an open concern cannot be silently suppressed to force a
lift or settlement through. Evaluated UNCONDITIONALLY on every `:well/lift`.

### Decision 6: dedicated double-actuation-guard booleans

`:crude-lifted?` / `:production-settled?` are dedicated booleans on the
`well` record, never a single `:status` value -- the same discipline every
prior governor's guards establish, informed by `cloud-itonami-isic-6492`'s
real status-lifecycle bug (ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`crude.store/Store` is implemented by both `MemStore` (atom-backed, default
for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed), proven to
satisfy the same contract in `test/crude/store_contract_test.clj`. The
ledger stays append-only on every backend: which well was screened for a
reservoir pressure outside its window, an annular pressure above MAASP, a
water cut above the BSW limit, an H2S concentration above the IDLH, or an
open integrity flag, which well had crude lifted, which production was
settled, on what jurisdictional basis, approved by whom -- always a query
over an immutable log.

### Decision 8: Phase 0->3 with `:well/lift`/`:production/settle` NEVER auto

`crude.phase`'s phase table puts `:well/intake` (no direct capital risk) in
phase 3's `:auto` set as its only member; `:well/lift` and `:production/
settle` are deliberately ABSENT from every phase's `:auto` set, including
phase 3 -- a permanent structural fact. `crude.governor`'s high-stakes gate
enforces the same invariant independently: two layers agree that actuation
is always a human production superintendent's call.

### Decision 9: mock + LLM advisor pair

`crude.crudeadvisor` provides a deterministic `mock-advisor` (default, runs
offline) and an `llm-advisor` backed by a `langchain.model/ChatModel`. The
LLM advisor's EDN proposal is parsed defensively: any parse/shape failure
yields a safe low-confidence noop so the governor escalates/holds -- an LLM
hiccup can never auto-lift a well or auto-settle production.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/petroleum` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not petroleum-specific. Forcing a false
  capability-library integration would be dishonest; this build correctly
  uses self-contained domain logic instead.
- **A `:kind`-distinguished entity** (matching the retail sibling's `order`
  shape). Rejected: lift and settlement happen SEQUENTIALLY on the SAME well
  in this domain, not as alternative actions -- the repair-shop / quarrying
  cluster's sequential shape is the honest match here.
- **Claiming genuinely-new unconditional-evaluation ordinals for the physical
  range checks.** Rejected: each check reapplies an established fleet
  discipline (aerospace two-sided-tolerance, fabrication ratio/value-vs-rated-
  limit) to a new domain. Per `cloud-itonami-isic-0162` Decision 3's
  convention, these are documented as honest discipline-reapplications, not
  claimed as novel inventions -- the same honesty discipline that forbids
  fabricating coverage also forbids over-claiming novelty.
- **Building reservoir simulation / recovery-factor optimization in this R0.**
  Rejected in favor of a scoped R0 slice (the `:optimization` capability is
  correctly marked required, the integration is a follow-up), consistent with
  this fleet's 'extending coverage is additive' convention.

## Consequences

- 92nd actor in this fleet (91 implemented before this build).
- Establishes the well-safety physical range-check suite as honest
  reapplications of established fleet disciplines (two-sided-tolerance,
  ratio/value-vs-rated-limit, open-flag-unresolved) to upstream petroleum --
  no genuinely-new-concept check, all discipline-reuse documented as such per
  `cloud-itonami-isic-0162` Decision 3.
- `MemStore` || `DatomicStore` parity is proven by
  `test/crude/store_contract_test.clj`.
- 41 tests / 204 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean lift + settlement lifecycle, plus
  eight HARD-hold scenarios (no spec-basis, reservoir pressure, annular/
  MAASP, water cut, H2S/IDLH, integrity flag, double lift, double
  settlement), end-to-end.
- `blueprint.edn` required no field-sync fixes (already correct) -- only the
  `:maturity` flip itself.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4920/docs/adr/0001-architecture.md` (freight sibling;
  contrast: wraps a pre-existing `kotoba-lang/logistics` capability library)
- `cloud-itonami-isic-0162/docs/adr/0001-architecture.md` (origin of the
  'honest reapplication, documented as such' convention this build follows
  for its physical range checks)
- 鉱山保安規則 (Mine Safety Regulations), 原油及び天然ガスの採掘; 石油コンビナート等災害防止法 (Japan, METI)
- BSEE Oil and Gas and Sulphur Operations in the Outer Continental Shelf, 30 C.F.R. Part 250; OSHA Process Safety Management, 29 C.F.R. §1910.119 (US)
- Offshore Safety Act 1992; Offshore DCR / PFEER; Offshore Installations (Safety Case) Regulations 2005 (UK, HSE)
- Activities Regulations (Aktivitetsforskriftenen); Framework Regulations; Facilities Regulations (Norway, Petroleum Safety Authority)
- NIOSH Immediately Dangerous to Life or Health (IDLH) value for hydrogen sulfide: 50 ppm
