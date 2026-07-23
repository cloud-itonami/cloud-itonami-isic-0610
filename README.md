# cloud-itonami-isic-0610

Open Business Blueprint for **ISIC Rev.5 0610**: Crude Petroleum
Extraction -- well intake, per-jurisdiction well-construction/well-
control/sour-service regulatory assessment, crude lifting, and
production settlement for a community operator.

This repository publishes a crude-extraction actor -- well intake,
per-jurisdiction well-safety regulatory assessment, crude lift and
production settlement -- as an OSS business that any qualified
operator can fork, deploy, run, improve and sell, so a regional
producer never surrenders well-integrity and production-accounting
data to a closed SCADA/production-AI SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **CrudeAdvisor ⊣ Well
Safety Governor**. This blueprint's own `:itonami.blueprint/governor`
keyword, `:well-safety-governor`, is a UNIQUE keyword fleet-wide
(grep-verified: no other blueprint declares it) -- a fresh,
independent build.

**Unlike `cloud-itonami-isic-4920` (which wraps a pre-existing
bespoke capability library `kotoba-lang/logistics`), this vertical is
SELF-CONTAINED**: there is no `kotoba-lang/petroleum` to delegate
well-safety validation to, so the reservoir-pressure / annular-vs-
MAASP / water-cut / H2S-vs-IDLH range checks live as pure functions
in `crude.registry` and are re-verified independently by the
governor, rather than wrapping an external capability library's own
validated function.

> **Why an actor layer at all?** An LLM is great at drafting a well
> summary, normalizing records, and reading a pressure gauge -- but it
> has **no notion of which jurisdiction's well-construction/well-
> control/sour-service law is official, no license to open a real well
> to flow against a live reservoir or settle real production, and no
> way to know on its own whether the measured reservoir pressure
> actually lies inside the declared safe window, whether the annulus
> pressure is actually below the MAASP, or whether the H2S
> concentration is actually below the IDLH**. Letting it lift crude
> or settle production directly invites fabricated regulatory
> citations, a well flowing with a lost zonal-isolation envelope, and
> a lift starting into a sour column above the IDLH -- exposing the
> crew to a lethal blowout and the operator to real liability, for
> whoever runs it. This project seals the CrudeAdvisor into a single
> node and wraps it with an independent **Well Safety Governor**, a
> human **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers well intake through well-construction/well-control/
sour-service regulatory assessment, crude lift and production
settlement. It does **not**, by itself, hold any mining/concession
right or operating authority required to run a crude-extraction
business in a given jurisdiction, and it does not claim to. It also
does not perform the actual physical drilling/workover itself, or
judge reservoir economics -- reservoir simulation and recovery-factor
optimization (the blueprint's own `:optimization` technology) is a
follow-up slice, not in this R0. Whoever deploys and operates a live
instance (a qualified production superintendent/operator) supplies
any jurisdiction-specific operating authority, the real
wellhead/workover-robot dispatch integration and the real SCADA/
production-accounting integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so that operator does not have to build the
compliance layer from scratch.

### Actuation

**Lifting crude from a real well and settling real production are
never autonomous, at any phase, by construction.** Two independent
layers enforce this (`crude.governor`'s `:well/lift`/`:production/
settle` high-stakes gate and `crude.phase`'s phase table, which never
puts either op in any phase's `:auto` set) -- see `crude.phase`'s
docstring and `test/crude/phase_test.clj`'s `well-lift-never-auto-at-
any-phase`/`production-settle-never-auto-at-any-phase`. The actor may
draft, check and recommend; a human production superintendent is
always the one who actually opens a well to flow or settles a
production period. Grounded in well-control doctrine (the same
discipline every regulator in `crude.facts` codifies: a real lift and
a real settlement are human sign-off acts) -- a genuine DUAL-
actuation shape, applied SEQUENTIALLY to the SAME well (lift first,
settlement later), unlike `retailops`/4711's own `:kind`-distinguished
alternative-action shape.

## The core contract

```
well intake + jurisdiction facts (crude.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ CrudeAdvisor          │ ─────────────▶ │ Well Safety Governor   │  (independent system)
   │ (sealed)              │  + citations    │ spec-basis · evidence- │
   └───────────────────────┘                 │ incomplete · reservoir-│
          │                 commit ◀┼ pressure-out-of-range (NEW, │
          │                         │ two-sided tolerance) · annular- │
    record + ledger        escalate ┼ pressure-excessive (NEW, ratio) │
          │              (ALWAYS for│ · water-cut-excessive (NEW) ·  │
          │       :well/lift/       │ h2s-toxic-threshold (NEW) ·    │
          │       :production/      │ integrity-flag-unresolved ·    │
          │       settle)           │ already-lifted · already-settled│
          ▼                          └───────────────────────┘
      human approval
```

**The CrudeAdvisor never lifts crude from a well or settles production
the Well Safety Governor would reject, and never does so without a
human sign-off.** Hard violations (fabricated regulatory
requirements; unsupported evidence; a reservoir pressure outside the
safe window; an annular pressure above MAASP; a water cut above the
BSW limit; an H2S concentration above the IDLH; an unresolved
integrity flag; a double lift/settlement) force **hold** and *cannot*
be approved past; a clean lift/settlement proposal still always
routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean lift + settlement lifecycle, plus six HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an autonomous wellhead or
workover robot performs the physical act of opening a well tree to
flow (and eventually closing it), under the actor, gated by the
independent **Well Safety Governor**. The governor never dispatches
hardware itself: a lift-clearing action must have cleared the same
sign-off a human production superintendent would need. This restates
the fleet-wide robotics premise three ways (ADR-2607011000): the
blueprint declares `:robotics true`, the README names the robot that
performs the physical act, and the Well Safety Governor is the
independent gate that robot's command must pass -- a robot may turn
the valve, but only after the governor and a human superintendent
both agree it is safe to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Well Safety Governor, lift/settlement draft records, audit ledger |
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
`0610`). Unlike the freight sibling, this vertical is NOT backed by a
separate bespoke domain capability lib: the well-safety range checks
(reservoir-pressure window, annular-vs-MAASP, water cut, H2S-vs-IDLH)
are self-contained pure functions in `crude.registry`, on top of the
generic robotics/identity/forms/dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/crude/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + lift AND production history (dual history). The double-actuation guard checks dedicated `:crude-lifted?`/`:production-settled?` booleans rather than a `:status` value |
| `src/crude/registry.cljc` | Lift/settlement draft records, plus the self-contained well-safety range-check pure functions (`reservoir-pressure-out-of-range?`, `well-integrity-annular-pressure-excessive?`, `water-cut-excessive?`, `h2s-toxic?`) the governor re-verifies against -- no external capability library to delegate to |
| `src/crude/facts.cljc` | Per-jurisdiction well-construction/well-control/sour-service catalog with an official spec-basis citation + NIOSH H2S IDLH per entry, honest coverage reporting |
| `src/crude/crudeadvisor.cljc` | **CrudeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/reservoir-assessment/lift/settlement proposals |
| `src/crude/governor.cljc` | **Well Safety Governor** -- 7 HARD checks (spec-basis · evidence-incomplete · reservoir-pressure-out-of-range, the aerospace two-sided-tolerance discipline · well-integrity-annular-pressure-excessive, the fabrication ratio discipline · water-cut-excessive · h2s-toxic-threshold · integrity-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/crude/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (lift/settlement always human; well intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/crude/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/crude/sim.cljc` | demo driver |
| `test/crude/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers well intake through well-construction/well-control/
sour-service regulatory assessment, crude lift and production
settlement -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Well intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:well/intake`/`:reservoir/assess`) | Real SCADA/wellhead-robot integration, reservoir simulation and recovery-factor optimization |
| Crude lift, HARD-gated on full evidence, an in-window reservoir pressure, an annular pressure below MAASP, an acceptable water cut, an H2S below the IDLH and no open integrity flag, plus a double-lift guard (`:well/lift`) | |
| Production settlement, HARD-gated on full evidence and no double-settlement (`:production/settle`) | |
| Immutable audit ledger for every intake/assessment/lift/settlement decision | |

Extending coverage is additive: add the next gate (e.g. a flaring-
permit check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship ops already establish.

## Jurisdiction coverage (honest)

`crude.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `crude.facts/catalog` --
currently 5 seeded (JPN, USA, GBR, NOR, BRA) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `crude.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `CrudeAdvisor` + `Well Safety Governor` run as real,
tested code (see `Run` above), promoted from the originally-published
`:blueprint`-tier scaffold, following the SAME governed-actor
architecture as the other prior actors across this fleet, with its
own distinct, independently-named governor and its own self-contained
well-safety range checks. See `docs/adr/0001-architecture.md` for the
history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
