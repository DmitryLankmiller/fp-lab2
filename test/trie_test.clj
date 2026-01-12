(ns trie-test
  (:require [trie :as t]
            [clojure.test :refer [deftest is]]))

(defn rand-char []
  (char (+ (int \space) (rand-int (- (int \~) (int \space))))))

(defn rand-key "Generate random key with length n (must be > 1)"
  [n]
  {:pre [(>= n 1)]}
  (apply str (vec (for [_ (range n)]
                    (rand-char)))))

(deftest trie-get
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})]
    (is (= 5 (t/tget trie "ab")))
    (is (= 6 (t/tget trie "c")))
    (is (= nil (t/tget trie "abc")))
    (is (= nil (t/tget trie "dfe")))))

(deftest get-from-empty-trie
  (let [trie (t/empty-trie)]
    (dotimes [_ 10]
      (is (= nil (t/tget trie (rand-key (inc (rand-int 10)))))))))

(deftest trie-get-entries
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        entries (t/get-entries trie)]
    (is (contains? (set entries) [[\a \b] 5]))
    (is (contains? (set entries) [[\c] 6]))
    (loop [left-entries entries]
      (if (seq left-entries) true
          (let [[k v] (first left-entries)]
            (is (= v (t/tget trie k)))
            (recur (rest left-entries)))))))

(deftest trie-equals
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        same-trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        another-trie-1 (t/create-trie {\a (t/create-node \a nil false {\c (t/create-node \c 5 true {})}) \c (t/create-node \c 6 true {})})
        another-trie-2 (t/create-trie {\d (t/create-node \d nil false {\f (t/create-node \f 5 true {})}) \g (t/create-node \g 6 true {})})]
    (is (true? (t/tequals? trie same-trie)))
    (is (true? (t/tequals? (t/empty-trie) (t/empty-trie))))
    (is (false? (t/tequals? trie another-trie-1)))
    (is (false? (t/tequals? trie another-trie-2)))
    (is (false? (t/tequals? trie (t/empty-trie))))))

(deftest trie-trie-from-entries
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-entries [[[\a \b] 5] [[\c] 6]]]
    (is (t/tequals? trie (t/trie-from-entries expected-entries)))
    (is (t/tequals? trie (t/trie-from-entries (t/get-entries trie))))
    (is (t/tequals? (t/empty-trie) (t/trie-from-entries [])))))

(deftest trie-insert
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {\d (t/create-node \d 2 true {})})}) \c (t/create-node \c 6 true {})})]
    (is (t/tequals? (t/insert trie "abd" 2) expected-trie))
    (is (t/tequals? (t/insert trie "ab" 5) trie))
    (is (t/tequals? (t/insert trie "c" 6) trie))
    (is (t/tequals? (t/insert (t/empty-trie) "z" \z) (t/create-trie {\z (t/create-node \z \z true {})})))))

(deftest trie-update
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 8 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 10 true {})})]
    (is (t/tequals? (t/insert trie "ab" 8) expected-trie-1))
    (is (t/tequals? (t/insert trie "c" 10) expected-trie-2))))

(deftest trie-delete
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-1 (t/create-trie {\c (t/create-node \c 6 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})})})]
    (is (t/tequals? (t/delete trie "ab") expected-trie-1))
    (is (t/tequals? (t/delete trie "c") expected-trie-2))
    (is (t/tequals? (t/delete trie "cd") trie))
    (is (t/tequals? (t/delete trie "abc") trie))
    (is (t/tequals? (t/delete trie "UIDSfh344") trie))
    (dotimes [_ 10]
      (is (= (t/empty-trie) (t/delete (t/empty-trie) (rand-key (inc (rand-int 10)))))))))

(deftest trie-filter
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-1 (t/create-trie {\c (t/create-node \c 6 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})})})]
    (is (t/tequals? (t/tfilter trie (fn [_ _] true)) trie))
    (is (t/tequals? (t/tfilter trie (fn [k _] (= k [\c]))) expected-trie-1))
    (is (t/tequals? (t/tfilter trie (fn [k _] (= k [\a \b]))) expected-trie-2))
    (is (t/tequals? (t/tfilter trie (fn [_ _] false)) (t/empty-trie)))
    (dotimes [_ 10]
      (t/tequals? (t/tfilter (t/empty-trie) (fn [_ _] (> (rand) 0.5))) (t/empty-trie)))))

(deftest trie-map
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 1 true {})}) \c (t/create-node \c 1 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 25 true {})}) \c (t/create-node \c 36 true {})})
        expected-trie-3 (t/create-trie {\c (t/create-node \c nil false {\d (t/create-node \d 7 true {})}) \e (t/create-node \e 8 true {})})]
    (is (t/tequals? (t/tmap trie (fn [k v] [k v])) trie))
    (is (t/tequals? (t/tmap trie (fn [k _] [k 1])) expected-trie-1))
    (is (t/tequals? (t/tmap trie (fn [k v] [k (* v v)])) expected-trie-2))
    (is (t/tequals? (t/tmap trie (fn [k v] [(map #(char (+ 2 (int %))) k) (+ 2 v)])) expected-trie-3))
    (dotimes [_ 10]
      (t/tequals? (t/tmap (t/empty-trie) (fn [_ _] [(rand-key (inc (rand-int 10))) (rand-int 100)])) (t/empty-trie)))))

(deftest trie-reducel
  (let [trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        fn-1 (fn [acc _ v] (str acc v))
        acc-1 "str: "
        expected-value-1 "str: 56"
        trie-2 (t/create-trie {1 (t/create-node 1 nil false {2 (t/create-node 2 5 true {})}) 3 (t/create-node 3 6 true {})})
        fn-2 (fn [acc k v] (+ acc v (apply + k)))
        acc-2 5
        expected-value-2 (+ 1 2 3 5 6 5)]
    (is (= expected-value-1 (t/reducel trie-1 fn-1 acc-1)))
    (is (= expected-value-2 (t/reducel trie-2 fn-2 acc-2)))
    (is (= acc-1 (t/reducel (t/empty-trie) fn-1 acc-1)))))

(deftest trie-reducer
  (let [trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        fn-1 (fn [acc _ v] (str acc v))
        acc-1 "str: "
        expected-value-1 "str: 65"
        trie-2 (t/create-trie {1 (t/create-node 1 nil false {2 (t/create-node 2 5 true {})}) 3 (t/create-node 3 6 true {})})
        fn-2 (fn [acc k v] (+ acc v (apply + k)))
        acc-2 5
        expected-value-2 (+ 1 2 3 5 6 5)]
    (is (= expected-value-1 (t/reducer trie-1 fn-1 acc-1)))
    (is (= expected-value-2 (t/reducer trie-2 fn-2 acc-2)))))

(deftest trie-join
  (let [trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        trie-2 (t/create-trie {\b (t/create-node \b nil false {\b (t/create-node \b 8 true {})}) \d (t/create-node \d 10 true {})})
        trie-3 (t/create-trie {\a (t/create-node \a nil false {\c (t/create-node \c 1 true {})}) \d (t/create-node \d 2 true {})})
        trie-4 (t/create-trie {\d (t/create-node \d nil false {\c (t/create-node \c 1 true {})}) \a (t/create-node \a 2 true {})})
        expected-trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {}) \b (t/create-node \b nil false {\b (t/create-node \b 8 true {})}) \d (t/create-node \d 10 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {}) \c (t/create-node \c 1 true {})}) \c (t/create-node \c 6 true {}) \d (t/create-node \d 2 true {})})
        expected-trie-3 (t/create-trie {\a (t/create-node \a 2 true {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {}) \d (t/create-node \d nil false {\c (t/create-node \c 1 true {})})})
        expected-trie-4 (t/create-trie {\a (t/create-node \a 2 true {\b (t/create-node \b 5 true {}) \c (t/create-node \c 1 true {})}) \c (t/create-node \c 6 true {}) \b (t/create-node \b nil false {\b (t/create-node \b 8 true {})}) \d (t/create-node \d 2 true {\c (t/create-node \c 1 true {})})})
        expected-trie-5 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {}) \c (t/create-node \c 1 true {})}) \c (t/create-node \c 6 true {}) \b (t/create-node \b nil false {\b (t/create-node \b 8 true {})}) \d (t/create-node \d 2 true {})})]
    (is (t/tequals? expected-trie-1 (t/join trie-1 trie-2)))
    (is (t/tequals? expected-trie-2 (t/join trie-1 trie-3)))
    (is (t/tequals? expected-trie-3 (t/join trie-1 trie-4)))
    (is (t/tequals? expected-trie-4 (t/join trie-1 [trie-2 trie-3 trie-4])))
    (is (t/tequals? expected-trie-4 (t/join (t/join trie-1 trie-2) (t/join trie-3 trie-4))))
    (is (t/tequals? expected-trie-4 (t/join (t/join trie-1 [trie-2 trie-3]) trie-4)))
    (is (t/tequals? expected-trie-5 (t/join trie-1 [trie-2 trie-3])))
    (is (t/tequals? expected-trie-5 (t/join (t/join trie-1 trie-2) trie-3)))
    (is (t/tequals? expected-trie-5 (t/join trie-1 (t/join trie-2 trie-3))))
    (is (t/tequals? trie-1 (t/join trie-1 (t/empty-trie))))
    (is (t/tequals? trie-1 (t/join (t/empty-trie) trie-1)))
    (is (t/tequals? trie-2 (t/join trie-2 (t/empty-trie))))
    (is (t/tequals? trie-2 (t/join (t/empty-trie) trie-2)))
    (is (t/tequals? (t/empty-trie) (t/join (t/empty-trie) (t/empty-trie))))))
