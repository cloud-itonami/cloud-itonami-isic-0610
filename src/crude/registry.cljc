(ns crude.registry
  "Pure-function crude-lift + production-settlement record construction
  -- an append-only well book-of-record draft -- AND the pure well-
  safety range-check functions the Well Safety Governor calls to re-
  verify a well's own physical ground truth before any lift.

  Unlike `freightops`/4920's own registry (which delegates tracking-
  number validation to a real, pre-existing bespoke capability library
  `kotoba-lang/logistics`), this crude-extraction vertical has NO
  pre-existing capability library to wrap -- there is no 'kotoba-lang/
  petroleum' to call. So this namespace is self-contained: the range
  checks (reservoir pressure two-sided window, annular pressure vs
  MAASP, water cut vs BSW limit, H2S vs IDLH) are pure functions
  defined HERE, not delegated. The actor layer adds the governed
  proposal/approval loop on top; the governor calls these same pure
  functions to INDEPENDENTLY re-verify the well's own recorded values
  before any real-world lift, rather than trusting the advisor's self-
  reported confidence.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a crude-lift or production-settlement
  record -- every operator/jurisdiction assigns its own reference
  format. This namespace does NOT invent one beyond a jurisdiction-
  scoped sequence number; it validates the record's required fields,
  the same honest, non-fabricating discipline `crude.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real SCADA/production-accounting system. It builds the
  RECORD an operator would keep, not the act of lifting crude from a
  real well or settling real production itself (that is `crude.
  operation`'s `:well/lift`/`:production/settle`, always human-gated --
  see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- well-safety range checks (pure) -----------------------------
;;
;; The Well Safety Governor calls these to INDEPENDENTLY re-verify the
;; well's own recorded physical values before authorizing a lift. Each
;; returns true when the value is provably OUTSIDE the safe envelope --
;; the conservative well-safety choice, matching the two-sided-tolerance
;; discipline of the aerospace siblings and the ratio/threshold
;; discipline of the fabrication siblings: a value that cannot be
;; certified inside the safe envelope is treated as a violation, not as
;; 'unknown therefore ok'. Missing data -> violation (cannot verify safe
;; to lift).

(defn reservoir-pressure-out-of-range?
  "Two-sided reservoir-pressure window (the aerospace two-sided-tolerance
  pattern, applied to subsurface pressure): the well's measured
  reservoir pressure must stay within its declared safe operating window
  [min, max]. Actual below min risks formation damage / water-or-gas
  coning; above max risks formation fracture and loss of zonal isolation
  -- the precursor to an underground blowout. Missing any bound ->
  unsafe (cannot verify the safe window before opening the well to
  flow)."
  [actual min max]
  (cond
    (or (nil? actual) (nil? min) (nil? max)) true
    (or (< actual min) (> actual max))       true
    :else                                    false))

(defn well-integrity-annular-pressure-excessive?
  "Annular pressure vs Maximum Allowable Annular Seat Pressure (MAASP)
  -- the fabrication 'measured ratio exceeds rated limit' pattern,
  applied to well integrity. When the annulus pressure exceeds MAASP,
  the casing shoe can be lifted off its seat and zonal isolation is
  lost: this is a true blowout precursor, evaluated ahead of any
  surface flow-rate signal. Missing either bound -> unsafe."
  [annular-pressure-mpa maasp-mpa]
  (cond
    (or (nil? annular-pressure-mpa) (nil? maasp-mpa)) true
    (> annular-pressure-mpa maasp-mpa)                true
    :else                                             false))

(defn water-cut-excessive?
  "Basic sediment and water (BS&W) cut vs the well's declared maximum
  acceptable water cut -- the fabrication 'measured ratio exceeds rated
  limit' pattern. Producing above the water-cut limit risks emulsion
  handling overload and separator carry-over; it is a lift-readiness
  gate, not a toxicity gate. Missing either value -> unsafe."
  [water-cut-bsw water-cut-max]
  (cond
    (or (nil? water-cut-bsw) (nil? water-cut-max)) true
    (> water-cut-bsw water-cut-max)                true
    :else                                          false))

(defn h2s-toxic?
  "Hydrogen-sulfide concentration vs the jurisdiction's Immediately
  Dangerous to Life or Health (IDLH) threshold -- the fabrication
  'measured value exceeds rated limit' pattern, applied to sour-service
  toxicity. Lifting from a well whose H2S concentration exceeds the
  IDLH without the sour-service controls the evidence checklist
  demands exposes the crew to a lethal gas. Missing either value ->
  unsafe."
  [h2s-ppm idlh-ppm]
  (cond
    (or (nil? h2s-ppm) (nil? idlh-ppm)) true
    (> h2s-ppm idlh-ppm)                true
    :else                               false))

;; ----------------------------- record construction -----------------------------

(defn register-well-lift
  "Validate + construct the CRUDE-LIFT registration DRAFT -- the
  operator's own legal act of opening a real well to flow against a
  live reservoir. Pure function -- does not touch any real SCADA or
  production system; it builds the RECORD an operator would keep.
  `crude.governor` independently re-verifies the well's own reservoir-
  pressure window, annular-pressure/MAASP ratio, water cut, H2S/IDLH
  and integrity-flag ground truth, and blocks a double-lift of the
  same well, before this is ever allowed to commit."
  [well-id jurisdiction sequence]
  (when-not (and well-id (not= well-id ""))
    (throw (ex-info "well-lift: well_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "well-lift: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "well-lift: sequence must be >= 0" {})))
  (let [lift-number (str (str/upper-case jurisdiction) "-LIFT-" (zero-pad sequence 6))
        record {"record_id" lift-number
                "kind" "well-lift-draft"
                "well_id" well-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "lift_number" lift-number
     "certificate" (unsigned-certificate "WellLift" lift-number lift-number)}))

(defn register-production-settlement
  "Validate + construct the PRODUCTION-SETTLEMENT registration DRAFT --
  the operator's own legal act of settling a real production period
  (royalty / royalty-volume finalization, custody transfer). Pure
  function -- does not touch any real production-accounting system; it
  builds the RECORD an operator would keep. `crude.governor`
  independently re-verifies the well's own evidence completeness and
  blocks a double-settlement of the same well, before this is ever
  allowed to commit."
  [well-id jurisdiction sequence]
  (when-not (and well-id (not= well-id ""))
    (throw (ex-info "production-settlement: well_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "production-settlement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "production-settlement: sequence must be >= 0" {})))
  (let [settlement-number (str (str/upper-case jurisdiction) "-PROD-" (zero-pad sequence 6))
        record {"record_id" settlement-number
                "kind" "production-settlement-draft"
                "well_id" well-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "settlement_number" settlement-number
     "certificate" (unsigned-certificate "ProductionSettlement" settlement-number settlement-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
