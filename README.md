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

Вспомогательные функции для генерации ключей:

```clojure
(defn rand-char []
  (char (+ (int \space) (rand-int (- (int \~) (int \space))))))

(defn rand-key [n] "Generate random key with length n (must be > 1)"
  {:pre [(>= n 1)]}
  (apply str (vec (for [_ (range n)]
                    (rand-char)))))
```

Текст тестов (названия тестов соответствуют проверяемой логике):

```clojure
(deftest trie-get
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})]
    (is (= 5 (t/tget trie "ab")))
    (is (= 6 (t/tget trie "c")))
    (is (= nil (t/tget trie "abc")))
    (is (= nil (t/tget trie "dfe")))))

(deftest get-from-empty-trie
  (let [trie (t/empty-trie)]
    (dotimes [i 10]
      (is (= nil (t/tget trie (rand-key (inc (rand-int 10)))))))))

(deftest trie-get-entries
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        entries (t/get-entries trie)]
    (is (contains? (set entries) [[\a \b] 5]))
    (is (contains? (set entries) [[\c] 6]))
    (loop [left-entries entries]
      (if-not (empty? left-entries)
        (let [[k v] (first left-entries)]
          (is (= v (t/tget trie k))))))))

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
        expected-entries [[[\a \b] 5] [[\c] 6]]
        entries (t/get-entries trie)]
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
    (dotimes [i 10]
      (is (= (t/empty-trie) (t/delete (t/empty-trie) (rand-key (inc (rand-int 10)))))))))

(deftest trie-filter
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-1 (t/create-trie {\c (t/create-node \c 6 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})})})]
    (is (t/tequals? (t/tfilter trie (fn [k v] true)) trie))
    (is (t/tequals? (t/tfilter trie (fn [k v] (= k [\c]))) expected-trie-1))
    (is (t/tequals? (t/tfilter trie (fn [k v] (= k [\a \b]))) expected-trie-2))
    (is (t/tequals? (t/tfilter trie (fn [k v] false)) (t/empty-trie)))
    (dotimes [i 10]
      (t/tequals? (t/tfilter (t/empty-trie) (fn [k v] (> (rand 0.5)))) (t/empty-trie)))))

(deftest trie-map
  (let [trie (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        expected-trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 1 true {})}) \c (t/create-node \c 1 true {})})
        expected-trie-2 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 25 true {})}) \c (t/create-node \c 36 true {})})
        expected-trie-3 (t/create-trie {\c (t/create-node \c nil false {\d (t/create-node \d 7 true {})}) \e (t/create-node \e 8 true {})})]
    (is (t/tequals? (t/tmap trie (fn [k v] [k v])) trie))
    (is (t/tequals? (t/tmap trie (fn [k v] [k 1])) expected-trie-1))
    (is (t/tequals? (t/tmap trie (fn [k v] [k (* v v)])) expected-trie-2))
    (is (t/tequals? (t/tmap trie (fn [k v] [(map #(char (+ 2 (int %))) k) (+ 2 v)])) expected-trie-3))
    (dotimes [i 10]
      (t/tequals? (t/tmap (t/empty-trie) (fn [k v] [(rand-key (inc (rand-int 10))) (rand-int 100)])) (t/empty-trie)))))

(deftest trie-reducel
  (let [trie-1 (t/create-trie {\a (t/create-node \a nil false {\b (t/create-node \b 5 true {})}) \c (t/create-node \c 6 true {})})
        fn-1 (fn [acc k v] (str acc v))
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
        fn-1 (fn [acc k v] (str acc v))
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
```

Отдельно отмечу property-based тесты.
<trie, join> - множество префиксных деревьев с операцией объединения - моноид. Нейтральный элемент - пустое дерево.

Проверка свойств моноида: умножение на нейтральный элемент и ассоциативность:

```clojure
(is (t/tequals? expected-trie-5 (t/join trie-1 [trie-2 trie-3])))
(is (t/tequals? expected-trie-5 (t/join (t/join trie-1 trie-2) trie-3)))
(is (t/tequals? expected-trie-5 (t/join trie-1 (t/join trie-2 trie-3))))
(is (t/tequals? trie-1 (t/join trie-1 (t/empty-trie))))
(is (t/tequals? trie-1 (t/join (t/empty-trie) trie-1)))
(is (t/tequals? (t/empty-trie) (t/join (t/empty-trie) (t/empty-trie))))
```

Удаление из пустого дерева, а также фильтрация отображение на пустом дереве дадут то же пустое дерево. В случае со свёрткой - вернёт начальное значение `acc`:
```clojure
(dotimes [i 10]
      (is (= (t/empty-trie) (t/delete (t/empty-trie) (rand-key (inc (rand-int 10)))))))
(dotimes [i 10]
      (t/tequals? (t/tfilter (t/empty-trie) (fn [k v] (> (rand 0.5)))) (t/empty-trie)))
(dotimes [i 10]
      (t/tequals? (t/tmap (t/empty-trie) (fn [k v] [(rand-key (inc (rand-int 10))) (rand-int 100)])) (t/empty-trie)))
(is (= acc-1 (t/reducel (t/empty-trie) fn-1 acc-1)))
```

При вставке/обновлении в дереве ключей теми же значениями - дерево не изменится:
```clojure
(is (t/tequals? (t/insert trie "ab" 5) trie))
(is (t/tequals? (t/insert trie "c" 6) trie))
```

## Выводы
Для реализации структуры данных я использовал `defprotocol` + `defrecord`. `defrecord` по умолчанию даёт поведение как у `map`, за счёт чего было удобно обращаться к полям структуры. `defprotocol` позволил объявить интерфейс, который в последствии был реализован. Для поддержания неизменяемости данных пришлось делать проходку по дереву в две стороны: сперва сверху вниз, чтобы собрать все узлы на пути ключа, затем снизу вверх, чтобы присоединить обновлённый узёл к его родительским узлам, вплоть до корневого узла. При реализации я сделал приватными все вспомогательные функции, а также реализации протокола, оставив лишь сам протокол, функции-конструкторы для `trie` и `node` и несколько вспомогательных функций.