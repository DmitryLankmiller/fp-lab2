# Лабораторная работа №2

---

Студент: Курочка Дмитрий Сергеевич
ИСУ: 373305
Группа: P3312
Вариант: `pre-dict`

---

## Требования

Интерфейс - `dict`, структура данных - `Prefix Tree`.

1. Функции:
   - добавление и удаление элементов;
   - фильтрация;
   - отображение (map);
   - свертки (левая и правая);
   - структура должна быть [моноидом](https://ru.m.wikipedia.org/wiki/Моноид).
2. Структура данных должны быть неизменяемой.
3. Библиотека должна быть протестирована в рамках unit testing.
4. Библиотека должна быть протестирована в рамках property-based тестирования.
5. Структура должна быть полиморфной.
6. Требуется использовать идиоматичный для технологии стиль программирования.
7. Должна быть эффективная реализация функции сравнения, реализованная на уровне API, а не внутреннего представления.

## Ключевые элементы реализации

Объвление протокола и реализации

```clojure
(defprotocol Trie
  (tget [this key])
  (insert [this key val])
  (delete [this key])
  (tfilter [this pred] "pred gets 2 args: [key val] and must return bool value")
  (tmap [this f] "f gets 2 args: [key val] and must return 1 vector: [modified-key modified-val]")
  (reducel [this f val] "f gets 3 args: [acc key val] and must return 1 value")
  (reducer [this f val] "f gets 3 args: [acc key val] and must return 1 value")
  (join [this other] "Joins tries into one. Others may be single trie or coll of tries. If key occurs in more than one trie - the mapping from the last will be in the result")
  (tequals? [this another]))

(declare get-value-root
         insert-root
         delete-root
         filter-root
         map-root
         reduce-left-root
         reduce-right-root
         join-root
         equals-root)

(defrecord ^:private RootNode [children]
  Trie
  (tget [this key]
    (when-not (seqable? key) (throw (ex-info "Key must be seqable" {:key key})))
    (get-value-root this (seq key)))
  (insert [this key val]
    (when-not (seqable? key) (throw (ex-info "Key must be seqable" {:key key})))
    (when (empty? key) (throw (ex-info "Key must be not empty" {:key key})))
    (insert-root this (seq key) val))
  (delete [this key]
    (when-not (seqable? key) (throw (ex-info "Key must be seqable" {:key key})))
    (delete-root this (seq key)))
  (tfilter [this pred]
    (filter-root pred this))
  (tmap [this f]
    (map-root f this))
  (reducel [this f val]
    (reduce-left-root f val this))
  (reducer [this f val]
    (reduce-right-root f val this))
  (join [this other]
    (join-root this (if (and (coll? other) (not (instance? RootNode other))) other [other])))
  (tequals? [this another]
    (equals-root this another)))

(defrecord ^:private TrieNode [nkey nval has-value? children])
```

`RootNode` отличается от `TrieNode` тем, что в корневом узле не может быть значения.

Вспомогательная функция, которая строит вектор из узлов, расположенных по пути ключа

```clojure
(defn- get-way [root key-chars]
  (if (not (contains? (:children root) (first key-chars))) [[] (seq key-chars)]
      (loop [nodes []
             left-chars (rest key-chars)
             n (get (:children root) (first key-chars))]
        (let [nkey      (first left-chars)
              nchildren (:children n)]
          (if (contains? nchildren nkey)
            (recur (conj nodes n)
                   (rest left-chars)
                   (get nchildren nkey))
            [(conj nodes n) left-chars])))))
```

Получение элемента:

```clojure
(defn- get-value-root [root key-chars]
  (let [[way left-chars] (get-way root key-chars)]
    (if (not (empty? left-chars)) nil
        (if (not (:has-value? (last way))) nil
            (:nval (last way))))))
```

Добавление/обновление элементов:

```clojure
(defn- insert-root [root key-chars val]
  (let [[way left-chars] (get-way root key-chars)]
    (loop [nodes (if (empty? left-chars) (rest (reverse way)) (reverse way))
           child (if (empty? left-chars) (assoc (last way) :nval val) (create-leaf left-chars val))]
      (if (empty? nodes) (assoc root :children (assoc (:children root) (first key-chars) child))
          (let [new-node (first nodes)
                left-nodes (rest nodes)]
            (recur left-nodes
                   (assoc new-node :children
                          (assoc (:children new-node)
                                 (:nkey child)
                                 child))))))))
```

Удаление элементов:

```clojure
(defn- delete-root [root key-chars]
  (let [[way left-chars] (get-way root key-chars)]
    (if (not (empty? left-chars)) root
        (let [last-node (last way)]
          (if (not (:has-value? last-node)) root
              (loop [nodes (rest (reverse way))
                     new-node (assoc last-node :nval nil :has-value? false)]
                (if (empty? nodes)
                  (if (or (:has-value? new-node)
                          (not (empty? (:children new-node))))
                    (assoc root :children (assoc (:children root) (:nkey new-node) new-node))
                    (assoc root :children (dissoc (:children root) (:nkey new-node))))
                  (recur (rest nodes)
                         (let [next-node (first nodes)]
                           (if (and (empty? (:children new-node)) (not (:has-value? new-node)))
                             (assoc next-node :children (dissoc (:children next-node) (:nkey new-node)))
                             (assoc next-node :children (assoc (:children next-node) (:nkey new-node) new-node))))))))))))
```

Получение пар ключ/значение:

```clojure
(defn get-entries [root]
  (loop [entries []
         queue (map (fn [v] [[] v]) (vals (:children root)))
         keys #{}]
    (if (empty? queue) entries
        (let [[prefix cur-node] (first queue)
              left-queue (rest queue)
              cur-key (conj prefix (:nkey cur-node))
              next-nodes (map (fn [e] [cur-key e]) (vals (:children cur-node)))]
          (if (and (:has-value? cur-node) (not (contains? keys cur-key)))
            (recur (conj entries [cur-key (:nval cur-node)])
                   (into left-queue next-nodes)
                   (conj keys cur-key))
            (recur entries (into left-queue next-nodes) keys))))))

(defn get-keys [root]
  (map first (get-entries root)))

(defn get-values [root]
  (map second (get-entries root)))

(defn trie-from-entries [entries]
  (loop [trie (empty-trie)
         left-entries entries]
    (if (empty? left-entries) trie
        (recur (insert-root
                trie
                (first (first left-entries))
                (second (first left-entries)))
               (rest left-entries)))))
```

На основе пар ключ/значение реализованы фильтрация, отображение, свёртки и объединение:

```clojure
(defn- filter-root [f root]
  (trie-from-entries (loop [entries (get-entries root)
                            filtered-entries []]
                       (if (empty? entries) filtered-entries
                           (recur (rest entries)
                                  (let [cur-entry (first entries)
                                        [k v] cur-entry]
                                    (if (f k v) (conj filtered-entries cur-entry) filtered-entries)))))))

(defn- map-root [f root]
  (trie-from-entries (loop [entries (get-entries root)
                            mapped-entries []]
                       (if (empty? entries) mapped-entries
                           (recur (rest entries)
                                  (let [cur-entry (first entries)
                                        [k v] cur-entry
                                        modified-entry (f k v)]
                                    (conj mapped-entries modified-entry)))))))

(defn- reduce-left-root [f val root]
  (let [entries (get-entries root)]
    (if (< 1 (count entries)) val)
    (let [[first-key first-value] (first entries)]
      (loop [left-entries (rest entries)
             acc (f val first-key first-value)]
        (if (empty? left-entries) acc
            (let [[cur-key cur-value] (first left-entries)]
              (recur (rest left-entries) (f acc cur-key cur-value))))))))

(defn- reduce-right-root [f val root]
  (let [entries (get-entries root)]
    (if (< 1 (count entries)) val)
    (let [[first-key first-value] (last entries)]
      (loop [left-entries (rest (reverse entries))
             acc (f val first-key first-value)]
        (if (empty? left-entries) acc
            (let [[cur-key cur-value] (first left-entries)]
              (recur (rest left-entries) (f acc cur-key cur-value))))))))

(defn- join-root [root others]
  (loop [result-root root
         left-tries others]
    (if (empty? left-tries) result-root
        (recur (let [cur-entries (get-entries (first left-tries))]
                 (loop [cur-root result-root
                        left-entries cur-entries]
                   (if (empty? left-entries) cur-root
                       (recur (insert-root cur-root (first (first left-entries)) (second (first left-entries)))
                              (rest left-entries)))))
               (rest left-tries)))))
```

Фукнция сравнения спускается вглубь и проверяет все ключи и значения:

```clojure
(defn- equals-node [node another]
  (if (not (or (instance? TrieNode node) (instance? TrieNode another)))
    (throw (ex-info "Wrong type of args"))
    (let [node-children (:children node)
          another-children (:children another)
          equals-count (= (count node-children) (count another-children))
          equals-fields (and
                         (= (:nval node-children) (:nval another-children))
                         (= (:nkey node-children) (:nkey another-children))
                         (= (:has-value? node-children) (:has-value? another-children)))]
      (if (or (not equals-count) (not equals-fields)) false
          (loop [node-keys (keys node-children)]
            (if (empty? node-keys) true
                (let [cur-key (first node-keys)
                      left-keys (rest node-keys)]
                  (if (or
                       (not (contains? another-children cur-key))
                       (not (equals-node (get node-children cur-key) (get another-children cur-key))))
                    false
                    (recur left-keys)))))))))

(defn- equals-root [root another]
  (if (not (or (instance? RootNode root) (instance? RootNode another)))
    (throw (ex-info "Wrong type of args"))
    (let [root-children (:children root)
          another-children (:children another)
          equals-count (= (count root-children) (count another-children))]
      (if (not equals-count)  false
          (loop [root-keys (keys root-children)]
            (if (empty? root-keys) true
                (let [cur-key (first root-keys)
                      left-keys (rest root-keys)]
                  (if (or
                       (not (contains? another-children cur-key))
                       (not (equals-node (get root-children cur-key) (get another-children cur-key))))
                    false
                    (recur left-keys)))))))))
```

## Тесты

Текст тестов (названия тестов соответствуют проверяемой логике):

```clojure
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
    (is (false? (t/tequals? trie another-trie-1)))
    (is (false? (t/tequals? trie another-trie-2)))))

(deftest trie-trie-from-entries
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-entries [[[\a \b] 5] [[\c] 6]]]
    (is (t/tequals? trie (t/trie-from-entries expected-entries)))))

(deftest trie-insert
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {\d (t/create-node \d 2 true {})})}) \c (t/create-node \c 6 true {})})]
    (is (t/tequals? (t/insert trie "abd" 2) expected-trie))
    (is (t/tequals? (t/insert trie "ab" 5) trie))
    (is (t/tequals? (t/insert trie "c" 6) trie))))

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
    (is (t/tequals? (t/delete trie "UIDSfh344") trie))))

(deftest trie-filter
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-1 (t/create-trie {\c (t/create-node \c 6 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})})})]
    (is (t/tequals? (t/tfilter trie (fn [_ _] true)) trie))
    (is (t/tequals? (t/tfilter trie (fn [k _] (= k [\c]))) expected-trie-1))
    (is (t/tequals? (t/tfilter trie (fn [k _] (= k [\a \b]))) expected-trie-2))
    (is (t/tequals? (t/tfilter trie (fn [_ _] false)) (t/empty-trie)))))

(deftest trie-map
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 1 true {})}) \c (t/create-node \c 1 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 25 true {})}) \c (t/create-node \c 36 true {})})
        expected-trie-3 (t/create-trie {\c (t/create-node \c nil false {\d (t/create-node \d 7 true {})}) \e (t/create-node \e 8 true {})})]
    (is (t/tequals? (t/tmap trie (fn [k v] [k v])) trie))
    (is (t/tequals? (t/tmap trie (fn [k _] [k 1])) expected-trie-1))
    (is (t/tequals? (t/tmap trie (fn [k v] [k (* v v)])) expected-trie-2))
    (is (t/tequals? (t/tmap trie (fn [k v] [(map #(char (+ 2 (int %))) k) (+ 2 v)])) expected-trie-3))))

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
    (is (= expected-value-2 (t/reducel trie-2 fn-2 acc-2)))))

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
    (is (t/tequals? expected-trie-5 (t/join trie-1 [trie-2 trie-3])))))
```

Property-based тесты:
<trie, join> - множество префиксных деревьев с операцией объединения - моноид. Нейтральный элемент - пустое дерево.


```clojure
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
  (prop/for-all [m gen-entries-map]
                (let [tr (build-trie m)]
                  (is (t/tequals? (t/join tr (t/empty-trie)) tr))
                  (is (t/tequals? (t/join (t/empty-trie) tr) tr)))))

(defspec pbt-join-associativity iteration-num
  (prop/for-all [m1 gen-entries-map
                 m2 gen-entries-map
                 m3 gen-entries-map]
                (let [t1 (build-trie m1)
                      t2 (build-trie m2)
                      t3 (build-trie m3)

                      a (t/join (t/join t1 t2) t3)
                      b (t/join t1 (t/join t2 t3))
                      c (t/join t1 [t2 t3])

                      expected (merge m1 m2 m3)]
                  (is (t/tequals? a b))
                  (is (t/tequals? a c))
                  (is (= expected (trie->map a))))))

(defspec pbt-join-last-write iteration-num
  (prop/for-all [m1 gen-entries-map
                 m2 gen-entries-map]
                (let [t1 (build-trie m1)
                      t2 (build-trie m2)
                      joined (t/join t1 t2)
                      expected (merge m1 m2)]
                  (is (= expected (trie->map joined))))))
```

## Выводы
Для реализации структуры данных я использовал `defprotocol` + `defrecord`. `defrecord` по умолчанию даёт поведение как у `map`, за счёт чего было удобно обращаться к полям структуры. `defprotocol` позволил объявить интерфейс, который в последствии был реализован. Для поддержания неизменяемости данных пришлось делать проходку по дереву в две стороны: сперва сверху вниз, чтобы собрать все узлы на пути ключа, затем снизу вверх, чтобы присоединить обновлённый узёл к его родительским узлам, вплоть до корневого узла. При реализации я сделал приватными все вспомогательные функции, а также реализации протокола, оставив лишь сам протокол, функции-конструкторы для `trie` и `node` и несколько вспомогательных функций.