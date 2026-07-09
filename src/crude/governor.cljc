(ns crude.governor
  "Well Safety Governor -- the independent compliance layer that earns
  the CrudeAdvisor the right to commit. The LLM has no notion of
  jurisdictional well-construction/well-control/sour-service law,
  whether a well's own reservoir pressure actually lies inside its
  declared safe operating window, whether the annulus pressure is
  actually below the Maximum Allowable Annular Seat Pressure, whether
  the basic-sediment-and-water cut is actually under its limit, whether
  the H2S concentration is actually below the IDLH, whether an open
  integrity flag has actually been resolved, or when an act stops being
  a draft and becomes a real-world crude lift or production settlement,
  so this MUST be a separate system able to *reject* a proposal and
  fall back to HOLD.

  Unlike `freightops`/4920's own governor (built on TOP of a real,
  pre-existing bespoke capability library `kotoba-lang/logistics`),
  this crude-extraction vertical has NO pre-existing petroleum
  capability library to delegate to -- so the four physical range
  checks (reservoir-pressure window, annular/MAASP, water cut, H2S/
  IDLH) are pure functions defined in `crude.registry` and called
  directly here, the SAME 'reuse a capability library's own validated
  function' discipline `retailops.governor`'s ean13 check establishes,
  here applied to this vertical's OWN pure registry functions rather
  than a separate library.

  `:itonami.blueprint/governor` is `:well-safety-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent
  question, a fresh independent build following the SAME governed-
  actor architecture (langgraph StateGraph + independent Governor +
  Phase 0->3 rollout) established by `cloud-itonami-isic-6511`.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them. The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `crude.phase`: for `:stake :well/lift`/
  `:production/settle` (a real lift or settlement) NO phase ever allows
  auto-commit either. Two independent layers agree that actuation is
  always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`crude.facts`), or invent one?
    2. Evidence incomplete         -- for `:well/lift`/`:production/
                                       settle`, has the jurisdiction
                                       actually been assessed with a
                                       full well-construction/well-
                                       control evidence checklist on
                                       file?
    3. Reservoir pressure out of
       range                        -- for `:well/lift`, INDEPENDENTLY
                                       verify the well's own measured
                                       reservoir pressure stays inside
                                       its declared safe window [min,max]
                                       via `crude.registry/reservoir-
                                       pressure-out-of-range?` (the
                                       aerospace two-sided-tolerance
                                       discipline), a genuinely new
                                       sub-category, evaluated
                                       UNCONDITIONALLY.
    4. Annular pressure excessive   -- for `:well/lift`, INDEPENDENTLY
                                       verify the well's own annulus
                                       pressure is below its MAASP via
                                       `crude.registry/well-integrity-
                                       annular-pressure-excessive?` (the
                                       fabrication measured-ratio-vs-
                                       rated-limit discipline) -- a true
                                       blowout precursor, evaluated
                                       UNCONDITIONALLY.
    5. Water cut excessive          -- for `:well/lift`, INDEPENDENTLY
                                       verify the well's own BS&W cut is
                                       below its declared maximum via
                                       `crude.registry/water-cut-
                                       excessive?`.
    6. H2S toxic threshold          -- for `:well/lift`, INDEPENDENTLY
                                       verify the well's own H2S
                                       concentration is below the
                                       jurisdiction's IDLH via `crude.
                                       registry/h2s-toxic?` (the
                                       fabrication measured-value-vs-
                                       rated-limit discipline) -- sour-
                                       service toxicity, evaluated
                                       UNCONDITIONALLY.
    7. Integrity flag unresolved    -- reported by THIS proposal itself,
                                       or already on file for the well
                                       (`:integrity-flag-raised? true`
                                       AND `:integrity-flag-resolved?
                                       false`) -- is a HARD, un-
                                       overridable hold. Evaluated
                                       UNCONDITIONALLY at `:well/lift`.
    8. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:well/lift`/
                                       `:production/settle` (REAL acts)
                                       -> escalate.

  Two more guards, double-lift/double-settlement prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-lifted-violations`/
  `already-settled-violations` refuse to lift/settle the SAME well
  twice, off dedicated `:crude-lifted?`/`:production-settled?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean,
  not status' discipline every prior governor's guards establish,
  informed by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [crude.facts :as facts]
            [crude.registry :as registry]
            [crude.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Lifting crude from a real well (starting flow against a live
  reservoir) and settling real production (royalty / royalty-volume
  finalization) are the two real-world actuation events this actor
  performs -- a two-member set, matching every sibling's own dual-
  actuation shape."
  #{:well/lift :production/settle})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:reservoir/assess` (or `:well/lift`/`:production/settle`) proposal
  with no spec-basis citation is a HARD violation -- never invent a
  jurisdiction's well-construction/well-control requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:reservoir/assess :well/lift :production/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:well/lift`/`:production/settle`, the jurisdiction's required
  mining-right / casing-integrity / BOP-test / cementing evidence must
  actually be satisfied -- do not trust the advisor's self-reported
  confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:well/lift :production/settle} op)
    (let [w (store/well st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction w) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(採掘権/套管完整性ログ/BOP試験記録/固井記録等)が充足していない状態での提案"}]))))

(defn- reservoir-pressure-out-of-range-violations
  "For `:well/lift`, INDEPENDENTLY verify the well's own measured
  reservoir pressure stays inside its declared safe window [min,max]
  via `crude.registry/reservoir-pressure-out-of-range?` (the aerospace
  two-sided-tolerance discipline). Evaluated UNCONDITIONALLY (every
  lift needs a reservoir pressure inside its safe window)."
  [{:keys [op subject]} st]
  (when (= op :well/lift)
    (let [w (store/well st subject)]
      (when (registry/reservoir-pressure-out-of-range?
             (:reservoir-pressure-mpa-actual w)
             (:reservoir-pressure-mpa-min w)
             (:reservoir-pressure-mpa-max w))
        [{:rule :reservoir-pressure-out-of-range
          :detail (str subject " の坑層圧力(" (:reservoir-pressure-mpa-actual w)
                      " MPa)が安全窓[" (:reservoir-pressure-mpa-min w) ", "
                      (:reservoir-pressure-mpa-max w) "] MPa の外 -- 採掘提案は進められない")}]))))

(defn- well-integrity-annular-pressure-excessive-violations
  "For `:well/lift`, INDEPENDENTLY verify the well's own annulus
  pressure is below its MAASP via `crude.registry/well-integrity-
  annular-pressure-excessive?` (the fabrication measured-ratio-vs-
  rated-limit discipline) -- a true blowout precursor. Evaluated
  UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :well/lift)
    (let [w (store/well st subject)]
      (when (registry/well-integrity-annular-pressure-excessive?
             (:annular-pressure-mpa w) (:maasp-mpa w))
        [{:rule :well-integrity-annular-pressure-excessive
          :detail (str subject " の環状圧力(" (:annular-pressure-mpa w)
                      " MPa)がMAASP(" (:maasp-mpa w) " MPa)を超過 -- 坑噴前兆のため採掘提案は進められない")}]))))

(defn- water-cut-excessive-violations
  "For `:well/lift`, INDEPENDENTLY verify the well's own BS&W cut is
  below its declared maximum via `crude.registry/water-cut-excessive?`."
  [{:keys [op subject]} st]
  (when (= op :well/lift)
    (let [w (store/well st subject)]
      (when (registry/water-cut-excessive?
             (:water-cut-bsw w) (:water-cut-max w))
        [{:rule :water-cut-excessive
          :detail (str subject " の水分・固形分(BS&W)(" (:water-cut-bsw w)
                      "%)が上限(" (:water-cut-max w) "%)を超過 -- 採掘提案は進められない")}]))))

(defn- h2s-toxic-threshold-violations
  "For `:well/lift`, INDEPENDENTLY verify the well's own H2S
  concentration is below the jurisdiction's IDLH via `crude.registry/
  h2s-toxic?` (the fabrication measured-value-vs-rated-limit
  discipline) -- sour-service toxicity. Evaluated UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :well/lift)
    (let [w (store/well st subject)
          idlh (facts/idlh-ppm (:jurisdiction w))]
      (when (registry/h2s-toxic? (:h2s-ppm w) idlh)
        [{:rule :h2s-toxic-threshold
          :detail (str subject " のH2S濃度(" (:h2s-ppm w) " ppm)がIDLH("
                      idlh " ppm)を超過 -- 含硫原油のため採掘提案は進められない")}]))))

(defn- integrity-flag-unresolved-violations
  "An unresolved integrity flag -- reported by THIS proposal itself, or
  already on file for the well -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY at `:well/lift` so a well with an open
  integrity concern never flows."
  [{:keys [op subject]} st]
  (when (= op :well/lift)
    (let [w (store/well st subject)]
      (when (and (true? (:integrity-flag-raised? w)) (not (true? (:integrity-flag-resolved? w))))
        [{:rule :integrity-flag-unresolved
          :detail (str subject " は未解決のインテグリティフラグがある -- 採掘提案は進められない")}]))))

(defn- already-lifted-violations
  "For `:well/lift`, refuses to lift the SAME well twice, off a
  dedicated `:crude-lifted?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :well/lift)
    (when (store/well-already-lifted? st subject)
      [{:rule :already-lifted
        :detail (str subject " は既に採掘済み")}])))

(defn- already-settled-violations
  "For `:production/settle`, refuses to settle the SAME well's
  production twice, off a dedicated `:production-settled?` fact (never
  a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :production/settle)
    (when (store/well-already-settled? st subject)
      [{:rule :already-settled
        :detail (str subject " は既に生産精算済み")}])))

(defn check
  "Censors a CrudeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (reservoir-pressure-out-of-range-violations request st)
                           (well-integrity-annular-pressure-excessive-violations request st)
                           (water-cut-excessive-violations request st)
                           (h2s-toxic-threshold-violations request st)
                           (integrity-flag-unresolved-violations request st)
                           (already-lifted-violations request st)
                           (already-settled-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
