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

(def gen-not-empty-trie
  (gen/fmap build-trie (gen/such-that (comp not empty?) gen-entries-map)))

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
  (prop/for-all [tr gen-not-empty-trie]
                (is (false? (t/tequals? tr (t/empty-trie))))))

(defspec pbt-get-entries-two-way iteration-num
  (prop/for-all [tr gen-trie]
                (let [tr2 (build-trie (trie->map tr))]
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
  (prop/for-all [tr gen-trie
                 k gen-key
                 v gen-value]
                (let [tr2 (t/insert tr k v)]
                  (is (= v (t/tget tr2 k))))))

(defspec pbt-delete-from-empty-is-empty iteration-num
  (prop/for-all [k gen-key]
                (is (t/tequals? (t/delete (t/empty-trie) k) (t/empty-trie)))))

(defspec pbt-filter-empty-is-empty iteration-num
  (prop/for-all [_ gen/boolean]
                (is (t/tequals? (t/tfilter (t/empty-trie) (fn [_ _] true))
                                (t/empty-trie)))))

(defspec pbt-map-empty-is-empty iteration-num
  (prop/for-all [_ gen/boolean]
                (is (t/tequals? (t/tmap (t/empty-trie) (fn [_ _] [(vec (seq "x")) 1]))
                                (t/empty-trie)))))

(defspec pbt-reducel-empty-returns-acc iteration-num
  (prop/for-all [acc gen/small-integer]
                (is (= acc (t/reducel (t/empty-trie) (fn [a _ _] (inc a)) acc)))))

(defspec pbt-reduces-sum-and-left-equals-right iteration-num
  (prop/for-all [tr gen-int-trie
                 acc gen/small-integer]
                (let [f  (fn [a _ v] (+ a v))
                      res-left (t/reducel tr f acc)
                      res-right (t/reducer tr f acc)]
                  (is (= res-left res-right)))))

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
