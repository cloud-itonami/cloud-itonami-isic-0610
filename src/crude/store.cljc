(ns crude.store
  "SSoT for the community-crude actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/crude/store_contract_test.clj), which is the whole point:
  the actor, the Well Safety Governor and the audit ledger never know
  which SSoT they run on.

  Unlike `retailops`/4711's own `order` entity (distinguished by
  `:kind`), this vertical's `lift` and `settle` actuation events
  apply SEQUENTIALLY to the SAME `well` -- a crude lift happens first
  (flow started against a live reservoir), production settlement
  happens later, on the same well record. This matches the repair-shop
  cluster's own `ticket` shape more closely (two real-world acts, in
  order, on one entity), with dedicated double-actuation-guard booleans
  (`:crude-lifted?`/`:production-settled?`, never a `:status` value).

  The ledger stays append-only on every backend: 'which well was
  screened for a reservoir pressure outside its safe window, an annular
  pressure above its MAASP, a water cut above its BSW limit, an H2S
  concentration above the IDLH, or an unresolved integrity flag, which
  well had crude lifted, which production was settled, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a regulator, a royalty owner, or an
  operator trusting a crude-extraction actor needs, and the evidence an
  operator needs if a lift or a settlement is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [crude.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (well [s id])
  (all-wells [s])
  (assessment-of [s well-id] "committed reservoir safety assessment, or nil")
  (ledger [s])
  (lift-history [s] "the append-only crude-lift history (crude.registry drafts)")
  (production-history [s] "the append-only production-settlement history (crude.registry drafts)")
  (next-lift-sequence [s jurisdiction] "next lift-number sequence for a jurisdiction")
  (next-production-sequence [s jurisdiction] "next production-number sequence for a jurisdiction")
  (well-already-lifted? [s well-id] "has crude already been lifted from this well?")
  (well-already-settled? [s well-id] "has this well's production already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-wells [s wells] "replace/seed the well directory (map id->well)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained well set covering both actuation lifecycles
  (lift, settlement) plus the governor's own well-safety checks, so the
  actor + tests run offline. Each violation well isolates exactly ONE
  failure mode (the rest stay clean) following the 'exercise the failure
  mode directly, never only via a happy-path actuation' discipline every
  sibling governor's demo data establishes."
  []
  {:wells
   {"well-1" {:id "well-1" :field-name "Minami-Akita" :well-name "Akita-1"
              :operator "Akita Petroleum Co"
              :api-gravity 35 :sulfur-percent 1.5 :h2s-ppm 5
              :water-cut-bsw 0.5 :water-cut-max 5.0
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-bbl-day 5000
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :crude-lifted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-2" {:id "well-2" :field-name "Atlantis-Field" :well-name "Atlantis-1"
              :operator "Atlantis Drilling"
              :api-gravity 35 :sulfur-percent 1.5 :h2s-ppm 5
              :water-cut-bsw 0.5 :water-cut-max 5.0
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-bbl-day 5000
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :crude-lifted? false :production-settled? false
              :jurisdiction "ATL" :status :intake}
    "well-3" {:id "well-3" :field-name "Minami-Akita" :well-name "Akita-3"
              :operator "Akita Petroleum Co"
              :api-gravity 35 :sulfur-percent 1.5 :h2s-ppm 5
              :water-cut-bsw 0.5 :water-cut-max 5.0
              :reservoir-pressure-mpa-actual 50.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-bbl-day 5000
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :crude-lifted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-4" {:id "well-4" :field-name "Minami-Akita" :well-name "Akita-4"
              :operator "Akita Petroleum Co"
              :api-gravity 35 :sulfur-percent 1.5 :h2s-ppm 5
              :water-cut-bsw 0.5 :water-cut-max 5.0
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 30.0 :maasp-mpa 25.0
              :flow-rate-bbl-day 5000
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :crude-lifted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-5" {:id "well-5" :field-name "Minami-Akita" :well-name "Akita-5"
              :operator "Akita Petroleum Co"
              :api-gravity 35 :sulfur-percent 1.5 :h2s-ppm 5
              :water-cut-bsw 8.0 :water-cut-max 5.0
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-bbl-day 5000
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :crude-lifted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-6" {:id "well-6" :field-name "Minami-Akita" :well-name "Akita-6"
              :operator "Akita Petroleum Co"
              :api-gravity 35 :sulfur-percent 1.5 :h2s-ppm 100
              :water-cut-bsw 0.5 :water-cut-max 5.0
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-bbl-day 5000
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :crude-lifted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-7" {:id "well-7" :field-name "Minami-Akita" :well-name "Akita-7"
              :operator "Akita Petroleum Co"
              :api-gravity 35 :sulfur-percent 1.5 :h2s-ppm 5
              :water-cut-bsw 0.5 :water-cut-max 5.0
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-bbl-day 5000
              :integrity-flag-raised? true :integrity-flag-resolved? false
              :crude-lifted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- lift-well!
  "Backend-agnostic `:well/mark-lifted` -- looks up the well via the
  protocol and drafts the crude-lift record, and returns {:result ..
  :well-patch ..} for the caller to persist."
  [s well-id]
  (let [w (well s well-id)
        seq-n (next-lift-sequence s (:jurisdiction w))
        result (registry/register-well-lift well-id (:jurisdiction w) seq-n)]
    {:result result
     :well-patch {:crude-lifted? true
                  :lift-number (get result "lift_number")}}))

(defn- settle-production!
  "Backend-agnostic `:well/mark-settled` -- looks up the well via the
  protocol and drafts the production-settlement record, and returns
  {:result .. :well-patch ..} for the caller to persist."
  [s well-id]
  (let [w (well s well-id)
        seq-n (next-production-sequence s (:jurisdiction w))
        result (registry/register-production-settlement well-id (:jurisdiction w) seq-n)]
    {:result result
     :well-patch {:production-settled? true
                  :settlement-number (get result "settlement_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (well [_ id] (get-in @a [:wells id]))
  (all-wells [_] (sort-by :id (vals (:wells @a))))
  (assessment-of [_ well-id] (get-in @a [:assessments well-id]))
  (ledger [_] (:ledger @a))
  (lift-history [_] (:lifts @a))
  (production-history [_] (:production @a))
  (next-lift-sequence [_ jurisdiction] (get-in @a [:lift-sequences jurisdiction] 0))
  (next-production-sequence [_ jurisdiction] (get-in @a [:production-sequences jurisdiction] 0))
  (well-already-lifted? [_ well-id] (boolean (get-in @a [:wells well-id :crude-lifted?])))
  (well-already-settled? [_ well-id] (boolean (get-in @a [:wells well-id :production-settled?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :well/upsert
      (swap! a update-in [:wells (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :well/mark-lifted
      (let [well-id (first path)
            {:keys [result well-patch]} (lift-well! s well-id)
            jurisdiction (:jurisdiction (well s well-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:lift-sequences jurisdiction] (fnil inc 0))
                       (update-in [:wells well-id] merge well-patch)
                       (update :lifts registry/append result))))
        result)

      :well/mark-settled
      (let [well-id (first path)
            {:keys [result well-patch]} (settle-production! s well-id)
            jurisdiction (:jurisdiction (well s well-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:production-sequences jurisdiction] (fnil inc 0))
                       (update-in [:wells well-id] merge well-patch)
                       (update :production registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-wells [s wells] (when (seq wells) (swap! a assoc :wells wells)) s))

(defn seed-db
  "A MemStore seeded with the demo well set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :lift-sequences {} :lifts []
                           :production-sequences {} :production []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, lift/
  production records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:well/id                        {:db/unique :db.unique/identity}
   :assessment/well-id             {:db/unique :db.unique/identity}
   :ledger/seq                     {:db/unique :db.unique/identity}
   :lift/seq                       {:db/unique :db.unique/identity}
   :production/seq                 {:db/unique :db.unique/identity}
   :lift-sequence/jurisdiction     {:db/unique :db.unique/identity}
   :production-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; Every well field is stored as its own Datomic attr so a governor
;; pull reads the exact ground truth (no blob decode). Boolean fields
;; are coerced on read so a missing attr reads back as false (parity
;; with MemStore). [field-key tx-attr boolean?]
(def ^:private well-fields
  [[:id :well/id false]
   [:field-name :well/field-name false]
   [:well-name :well/well-name false]
   [:operator :well/operator false]
   [:api-gravity :well/api-gravity false]
   [:sulfur-percent :well/sulfur-percent false]
   [:h2s-ppm :well/h2s-ppm false]
   [:water-cut-bsw :well/water-cut-bsw false]
   [:water-cut-max :well/water-cut-max false]
   [:reservoir-pressure-mpa-actual :well/reservoir-pressure-mpa-actual false]
   [:reservoir-pressure-mpa-min :well/reservoir-pressure-mpa-min false]
   [:reservoir-pressure-mpa-max :well/reservoir-pressure-mpa-max false]
   [:annular-pressure-mpa :well/annular-pressure-mpa false]
   [:maasp-mpa :well/maasp-mpa false]
   [:flow-rate-bbl-day :well/flow-rate-bbl-day false]
   [:integrity-flag-raised? :well/integrity-flag-raised? true]
   [:integrity-flag-resolved? :well/integrity-flag-resolved? true]
   [:crude-lifted? :well/crude-lifted? true]
   [:production-settled? :well/production-settled? true]
   [:jurisdiction :well/jurisdiction false]
   [:status :well/status false]
   [:lift-number :well/lift-number false]
   [:settlement-number :well/settlement-number false]])

(defn- well->tx [w]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get w k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:well/id (:id w)}
          well-fields))

(def ^:private well-pull (mapv second well-fields))

(defn- pull->well [m]
  (when (:well/id m)
    (reduce (fn [w [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc w k (boolean v))
                  (some? v)    (assoc w k v)
                  :else        w)))
            {:id (:well/id m)}
            well-fields)))

(defrecord DatomicStore [conn]
  Store
  (well [_ id]
    (pull->well (d/pull (d/db conn) well-pull [:well/id id])))
  (all-wells [_]
    (->> (d/q '[:find [?id ...] :where [?e :well/id ?id]] (d/db conn))
         (map #(pull->well (d/pull (d/db conn) well-pull [:well/id %])))
         (sort-by :id)))
  (assessment-of [_ well-id]
    (dec* (d/q '[:find ?p . :in $ ?wid
                :where [?a :assessment/well-id ?wid] [?a :assessment/payload ?p]]
              (d/db conn) well-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (lift-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :lift/seq ?s] [?e :lift/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (production-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :production/seq ?s] [?e :production/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-lift-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :lift-sequence/jurisdiction ?j] [?e :lift-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-production-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :production-sequence/jurisdiction ?j] [?e :production-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (well-already-lifted? [s well-id]
    (boolean (:crude-lifted? (well s well-id))))
  (well-already-settled? [s well-id]
    (boolean (:production-settled? (well s well-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :well/upsert
      (d/transact! conn [(well->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/well-id (first path) :assessment/payload (enc payload)}])

      :well/mark-lifted
      (let [well-id (first path)
            {:keys [result well-patch]} (lift-well! s well-id)
            jurisdiction (:jurisdiction (well s well-id))
            next-n (inc (next-lift-sequence s jurisdiction))]
        (d/transact! conn
                     [(well->tx (assoc well-patch :id well-id))
                      {:lift-sequence/jurisdiction jurisdiction :lift-sequence/next next-n}
                      {:lift/seq (count (lift-history s)) :lift/record (enc (get result "record"))}])
        result)

      :well/mark-settled
      (let [well-id (first path)
            {:keys [result well-patch]} (settle-production! s well-id)
            jurisdiction (:jurisdiction (well s well-id))
            next-n (inc (next-production-sequence s jurisdiction))]
        (d/transact! conn
                     [(well->tx (assoc well-patch :id well-id))
                      {:production-sequence/jurisdiction jurisdiction :production-sequence/next next-n}
                      {:production/seq (count (production-history s)) :production/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-wells [s wells]
    (when (seq wells) (d/transact! conn (mapv well->tx (vals wells)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:wells ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [wells]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-wells s wells))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo well set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
