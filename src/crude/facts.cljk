(ns crude.facts
  "Per-jurisdiction upstream well-safety regulatory catalog -- the
  G2-style spec-basis table the Well Safety Governor checks every
  `:reservoir/assess` proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's well-construction /
  well-control / sour-service requirements, or did it invent one?').

  Each entry below is a REAL jurisdiction with a REAL upstream oil & gas
  safety regime: Japan's METI Mine Safety jurisdiction over crude-oil
  and natural-gas wells, the US BSEE Outer Continental Shelf rule
  (30 CFR Part 250) plus OSHA Process Safety Management, the UK HSE
  Offshore Safety Directive regime, the Norwegian Petroleum Safety
  Authority's Activities Regulations, and Brazil's ANP well-integrity
  regime (Resolução ANP nº 46/2016, Sistema de Gerenciamento da
  Integridade de Poços -- SGIP). The required-evidence set
  (mining/concession right, casing-integrity log, BOP test record,
  cementing record) mirrors the well-construction and well-control
  evidence a regulator actually demands before a well is opened to
  flow; `:h2s-idlh-ppm` is the NIOSH Immediately-Dangerous-to-Life-or-
  Health threshold for hydrogen sulfide (50 ppm), the internationally
  cited acute-toxicity reference each of these regulators' sour-service
  rules are ultimately grounded in.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the well-
  construction/well-control evidence set (mining/concession right,
  casing-integrity log, BOP test record, cementing record); `:legal-
  basis` / `:owner-authority` / `:provenance` are the G2 citation the
  governor requires before any `:reservoir/assess` proposal can commit.
  `:h2s-idlh-ppm` is the NIOSH IDLH the governor's `h2s-toxic-threshold`
  check is grounded in."
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 資源エネルギー庁 (METI Agency for Natural Resources and Energy)"
          :legal-basis "鉱山保安規則 (Mine Safety Regulations), 原油及び天然ガスの採掘に係る部分; 石油コンビナート等災害防止法 (Act on the Prevention of Disasters in Petroleum Industrial Complexes and Other Petroleum Facilities)"
          :provenance "https://www.meti.go.jp/policy/safety_security/industrial_safety/sangyo-anzen.html"
          :required-evidence ["採掘権（鉱業権）記録 (mining/concession-right record)"
                              "套管（ケーシング）完整性ログ (casing-integrity log)"
                              "BOP（爆発予防装置）試験記録 (blowout-preventer test record)"
                              "固井（セメンチング）記録 (cementing record)"]
          :h2s-idlh-ppm 50}
   "USA" {:name "United States"
          :owner-authority "Bureau of Safety and Environmental Enforcement (BSEE) / OSHA"
          :legal-basis "BSEE Oil and Gas and Sulphur Operations in the Outer Continental Shelf (30 C.F.R. Part 250); OSHA Process Safety Management of Highly Hazardous Chemicals (29 C.F.R. §1910.119)"
          :provenance "https://www.ecfr.gov/current/title-30/chapter-II/subchapter-B/part-250"
          :required-evidence ["Lease/concession-right record"
                              "Casing-integrity log"
                              "Blowout-preventer (BOP) test record"
                              "Cementing record"]
          :h2s-idlh-ppm 50}
   "GBR" {:name "United Kingdom"
          :owner-authority "Health and Safety Executive (HSE), Offshore Safety Division"
          :legal-basis "Offshore Safety Act 1992; Offshore Design and Construction Regulations (DCR) / Prevention of Fire and Explosion and Emergency Response Regulations (PFEER); Offshore Installations (Safety Case) Regulations 2005"
          :provenance "https://www.hse.gov.uk/offshore/"
          :required-evidence ["Licence/concession-right record"
                              "Casing-integrity log"
                              "Blowout-preventer (BOP) test record"
                              "Cementing record"]
          :h2s-idlh-ppm 50}
   "NOR" {:name "Norway"
          :owner-authority "Petroleum Safety Authority Norway (Petroleumstilsynet, PSA)"
          :legal-basis "Activities Regulations (Aktivitetsforskriftenen); Framework Regulations (Rammeforskriftenen); Facilities Regulations (Innretninger)"
          :provenance "https://www.ptil.no/en/regulations/all-regulations/"
          :required-evidence ["Licence/concession-right record (petroleum)"
                              "Casing-integrity log"
                              "Blowout-preventer (BOP) test record"
                              "Cementing record"]
          :h2s-idlh-ppm 50}
   "BRA" {:name "Brazil"
          :owner-authority "Agência Nacional do Petróleo, Gás Natural e Biocombustíveis (ANP)"
          :legal-basis "Resolução ANP nº 46, de 1º de novembro de 2016 (institui o Regime de Segurança Operacional para Integridade de Poços de Petróleo e Gás Natural); Regulamento Técnico do Sistema de Gerenciamento da Integridade de Poços (SGIP)"
          :provenance "https://www.gov.br/anp/pt-br/assuntos/exploracao-e-producao-de-oleo-e-gas/seguranca-operacional/sistema-de-gerenciamento-da-integridade-de-pocos-sgip"
          :required-evidence ["Contrato de concessão/partilha de produção com a ANP (E&P concession/production-sharing contract record)"
                              "Registro de integridade do revestimento (casing-integrity log)"
                              "Registro de teste do preventor de erupção -- BOP (blowout-preventer test record)"
                              "Registro de cimentação (cementing record)"]
          :h2s-idlh-ppm 50}
   ;; CAN citations independently fetched+read directly this session
   ;; (2026-07-23) from laws-lois.justice.gc.ca's own FullText.html for
   ;; both the parent Act and its drilling regulations (both HTTP 200,
   ;; no bot-detection challenge). Confirmed verbatim on the Act's own
   ;; s.5.02 (Safety of Works and Activities): "The Commission of the
   ;; Canadian Energy Regulator shall, before issuing an authorization
   ;; for a work or activity ..., consider the safety of the work or
   ;; activity by reviewing, in consultation with the Chief Safety
   ;; Officer, the system as a whole and its components, including its
   ;; installations, equipment, operating procedures and personnel."
   ;; Confirmed verbatim on the Regulations' own s.36(1) (Well Control):
   ;; "The operator shall ensure that, during all well operations,
   ;; reliably operating well control equipment is installed to control
   ;; kicks, prevent blow-outs and safely carry out all well activities
   ;; and operations, including drilling, completion and workover
   ;; operations."; s.42 (Waiting on Cement Time): "After the cementing
   ;; of any casing or casing liner and before drilling out the casing
   ;; shoe, the operator shall ensure that the cement has reached the
   ;; minimum compressive strength sufficient to support the casing and
   ;; provide zonal isolation."; s.43 titled "Casing Pressure Testing".
   ;; HONEST DISCLOSED INCONSISTENCY (not papered over): the
   ;; Regulations' own Interpretation section still defines "Board" as
   ;; "the National Energy Board established by section 3 of the
   ;; National Energy Board Act" -- this defined term has NOT been
   ;; textually amended since the Regulations' own last-amended date of
   ;; 2009-12-31 (per the Regulations' own consolidation metadata,
   ;; "current to 2026-05-26"), even though the parent Act's own s.5.02
   ;; (confirmed above, itself amended in 2019) already uses "Commission
   ;; of the Canadian Energy Regulator" -- the National Energy Board's
   ;; 2019 successor under the Canadian Energy Regulator Act. This entry
   ;; cites the Act's own current terminology (CER) as :owner-authority
   ;; rather than the Regulations' own stale "Board"/NEB defined term.
   "CAN" {:name "Canada"
          :owner-authority "Canada Energy Regulator (CER) -- the Commission of the Canadian Energy Regulator, in consultation with the Chief Safety Officer (Canada Oil and Gas Operations Act s.5.02)"
          :legal-basis "Canada Oil and Gas Operations Act (R.S.C., 1985, c. O-7); Canada Oil and Gas Drilling and Production Regulations (SOR/2009-315)"
          :national-spec "Act s.5.02 (Safety of Works and Activities): the Commission reviews, in consultation with the Chief Safety Officer, the safety of installations/equipment/operating procedures/personnel before authorizing a work or activity; Regulations s.36(1) (Well Control): reliably operating well control equipment must be installed to control kicks and prevent blow-outs; s.42 (Waiting on Cement Time) and s.43 (Casing Pressure Testing) set casing/cementing verification requirements"
          :provenance "https://laws-lois.justice.gc.ca/eng/acts/o-7/ ; https://laws-lois.justice.gc.ca/eng/regulations/SOR-2009-315/"
          :required-evidence ["Lease/concession-right record"
                              "Casing-integrity log"
                              "Blowout-preventer (BOP) test record"
                              "Cementing record"]
          :h2s-idlh-ppm 50}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to lift crude or
  settle production on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-0610 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `crude.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn idlh-ppm
  "The jurisdiction's NIOSH Immediately-Dangerous-to-Life-or-Health H2S
  threshold (ppm), or nil -- nil means this jurisdiction has no seeded
  spec-basis, so the governor's spec-basis check catches the proposal
  before the H2S check ever runs. The governor passes this value to
  `crude.registry/h2s-toxic?`."
  [iso3]
  (:h2s-idlh-ppm (spec-basis iso3)))
