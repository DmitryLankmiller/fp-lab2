(ns trie-pbt-test
  {:clj-kondo/config '{:lint-as {clojure.test.check.clojure-test/defspec clojure.test/deftest
                                 clojure.test.check.properties/for-all clojure.core/let}}}
  (:require [clojure.test :refer [is]]
            [trie :as t]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(def iteration-num 500)

(def gen-key
  (gen/fmap (fn [chars] (apply str chars))
            (gen/vector gen/char-alpha 1 10)))

(def gen-value
  (gen/one-of [gen/small-integer gen/string gen/boolean gen/keyword]))

(def gen-entries-map
  (gen/map gen-key gen-value {:min-elements 0 :max-elements 40}))

(def ^:private val-sentinel ::val)

(defn- entries->prefix-tree [m]
  (reduce-kv
   (fn [acc k v]
     (assoc-in acc (conj (vec (seq k)) val-sentinel) v))
   {}
   m))

(defn- prefix-tree->node [ch subtree]
  (let [has? (contains? subtree val-sentinel)
        v    (get subtree val-sentinel)
        kids (dissoc subtree val-sentinel)
        children (into {}
                       (map (fn [[c st]] [c (prefix-tree->node c st)]))
                       kids)]
    (t/create-node ch v has? children)))

(defn- build-trie [entries-map]
  (let [pt (entries->prefix-tree entries-map)
        root-children (into {}
                            (map (fn [[c st]] [c (prefix-tree->node c st)]))
                            pt)]
    (t/create-trie root-children)))

(def gen-trie
  (gen/fmap build-trie gen-entries-map))

(def gen-int-entries-map
  (gen/map gen-key gen/small-integer {:min-elements 0 :max-elements 60}))

(def gen-int-trie
  (gen/fmap build-trie gen-int-entries-map))

(defn- trie->map [tr]
  (into {}
        (map (fn [[k v]] [(apply str k) v]))
        (t/get-entries tr)))

(defspec pbt-get-from-empty-trie iteration-num
  (prop/for-all [k gen-key]
                (is (nil? (t/tget (t/empty-trie) k)))))

(defspec pbt-empty-equals-empty iteration-num
  (prop/for-all [_ gen/boolean]
                (is (true? (t/tequals? (t/empty-trie) (t/empty-trie))))))

(defspec pbt-not-equals-empty-for-nonempty iteration-num
  (prop/for-all [m (gen/such-that (comp not empty?) gen-entries-map 50)]
                (let [tr (build-trie m)]
                  (is (false? (t/tequals? tr (t/empty-trie)))))))

(defspec pbt-get-entries-two-way iteration-num
  (prop/for-all [m gen-entries-map]
                (let [tr  (build-trie m)
                      m2  (trie->map tr)
                      tr2 (build-trie m2)]
                  (is (t/tequals? tr tr2)))))

(defspec pbt-trie-from-empty-entries-is-empty iteration-num
  (prop/for-all [_ gen/boolean]
                (is (t/tequals? (build-trie {}) (t/empty-trie)))))

(defspec pbt-insert-into-empty-works iteration-num
  (prop/for-all [k gen-key
                 v gen-value]
                (let [tr (t/insert (t/empty-trie) k v)]
                  (is (= v (t/tget tr k))))))

(defspec pbt-insert-then-get iteration-num
  (prop/for-all [m gen-entries-map
                 k gen-key
                 v gen-value]
                (let [tr (build-trie m)
                      tr2 (t/insert tr k v)]
                  (is (= v (t/tget tr2 k))))))

(defspec pbt-delete-from-empty-is-empty iteration-num
  (prop/for-all [k gen-key]
                (is (t/tequals? (t/delete (t/empty-trie) k) (t/empty-trie)))))

(defspec pbt-delete-existing-removes-key iteration-num
  (prop/for-all [m (gen/such-that (comp not empty?) gen-entries-map 50)]
                (let [k   (first (keys m))
                      tr  (build-trie m)
                      tr2 (t/delete tr k)]
                  (is (not (nil? (t/tget tr k))))
                  (is (nil? (t/tget tr2 k))))))

(defspec pbt-filter-empty-is-empty iteration-num
  (prop/for-all [_ gen/boolean]
                (is (t/tequals? (t/tfilter (t/empty-trie) (fn [_ _] true))
                                (t/empty-trie)))))

(defspec pbt-filter-correctness iteration-num
  (prop/for-all [m gen-entries-map
                 pred-kind (gen/elements [:all :none :short-key :number :bool :kw :str])]
                (let [pred (case pred-kind
                             :all       (fn [_ _] true)
                             :none      (fn [_ _] false)
                             :short-key (fn [k _] (< (count k) 4))
                             :number    (fn [_ v] (number? v))
                             :bool      (fn [_ v] (boolean? v))
                             :kw        (fn [_ v] (keyword? v))
                             :str       (fn [_ v] (string? v)))
                      tr (build-trie m)
                      tr2 (t/tfilter tr pred)
                      m2 (trie->map tr2)
                      expected (into {} (filter (fn [[ks v]]
                                                  (pred (vec (seq ks)) v))) m)]
                  (is (= expected m2)))))

(defspec pbt-map-empty-is-empty iteration-num
  (prop/for-all [_ gen/boolean]
                (is (t/tequals? (t/tmap (t/empty-trie) (fn [_ _] [(vec (seq "x")) 1]))
                                (t/empty-trie)))))

(defspec pbt-map-correctness iteration-num
  (prop/for-all [m gen-entries-map
                 kind (gen/elements [:id :inc-val :injective-key])]
                (let [f (case kind
                          :id (fn [k v] [k v])
                          :inc-val (fn [k v] [k (if (number? v) (inc v) v)])
                          :injective-key (fn [k v] [(conj k \x) v]))
                      tr (build-trie m)
                      tr2 (t/tmap tr f)
                      m2 (trie->map tr2)
                      expected (case kind
                                 :id m
                                 :inc-val (into {} (map (fn [[ks v]] [ks (if (number? v) (inc v) v)])) m)
                                 :injective-key (into {} (map (fn [[ks v]] [(str ks "x") v])) m))]
                  (is (= expected m2)))))

(defspec pbt-reducel-empty-returns-acc iteration-num
  (prop/for-all [acc gen/small-integer]
                (is (= acc (t/reducel (t/empty-trie) (fn [a _ _] (inc a)) acc)))))

(defspec pbt-reduces-sum-and-left-equals-right iteration-num
  (prop/for-all [m gen-int-entries-map
                 acc gen/small-integer]
                (let [tr (build-trie m)
                      f  (fn [a _ v] (+ a v))
                      expected (+ acc (reduce + 0 (vals m)))]
                  (is (= expected (t/reducel tr f acc)))
                  (is (= expected (t/reducer tr f acc))))))

;; Monads

(defspec pbt-join-identity iteration-num
  (prop/for-all [tr gen-trie]
                (is (t/tequals? (t/join tr (t/empty-trie)) tr))
                (is (t/tequals? (t/join (t/empty-trie) tr) tr))))

(defspec pbt-join-associativity iteration-num
  (prop/for-all [t1 gen-trie
                 t2 gen-trie
                 t3 gen-trie]
                (let [a (t/join (t/join t1 t2) t3)
                      b (t/join t1 (t/join t2 t3))]
                  (is (t/tequals? a b)))))
