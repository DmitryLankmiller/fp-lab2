(ns trie)

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

(defn create-trie [children-map]
  (->RootNode children-map))

(defn create-node [nkey nval has-value? node-children]
  (->TrieNode nkey nval has-value? node-children))

(defn- create-leaf [key-chars val]
  (let [rkey-chars (reverse key-chars)]
    (loop [left-chars (rest rkey-chars)
           node       (->TrieNode (first rkey-chars) val true {})]
      (if (empty? left-chars)
        node (recur
              (rest left-chars)
              (->TrieNode
               (first left-chars) nil false {(:nkey node) node}))))))

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

(defn- get-value-root [root key-chars]
  (let [[way left-chars] (get-way root key-chars)]
    (if (seq left-chars) nil
        (if (not (:has-value? (last way))) nil
            (:nval (last way))))))

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

(defn- delete-root [root key-chars]
  (let [[way left-chars] (get-way root key-chars)]
    (if (seq left-chars) root
        (let [last-node (last way)]
          (if (not (:has-value? last-node)) root
              (loop [nodes (rest (reverse way))
                     new-node (assoc last-node :nval nil :has-value? false)]
                (if (empty? nodes)
                  (if (or (:has-value? new-node)
                          (seq (:children new-node)))
                    (assoc root :children (assoc (:children root) (:nkey new-node) new-node))
                    (assoc root :children (dissoc (:children root) (:nkey new-node))))
                  (recur (rest nodes)
                         (let [next-node (first nodes)]
                           (if (and (empty? (:children new-node)) (not (:has-value? new-node)))
                             (assoc next-node :children (dissoc (:children next-node) (:nkey new-node)))
                             (assoc next-node :children (assoc (:children next-node) (:nkey new-node) new-node))))))))))))

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

(defn empty-trie [] (->RootNode {}))

(defn trie-from-entries [entries]
  (loop [trie (empty-trie)
         left-entries entries]
    (if (empty? left-entries) trie
        (recur (insert-root
                trie
                (first (first left-entries))
                (second (first left-entries)))
               (rest left-entries)))))

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
    (if (> 1 (count entries)) val
        (let [[first-key first-value] (first entries)]
          (loop [left-entries (rest entries)
                 acc (f val first-key first-value)]
            (if (empty? left-entries) acc
                (let [[cur-key cur-value] (first left-entries)]
                  (recur (rest left-entries) (f acc cur-key cur-value)))))))))

(defn- reduce-right-root [f val root]
  (let [entries (get-entries root)]
    (if (> 1 (count entries)) val
        (let [[first-key first-value] (last entries)]
          (loop [left-entries (rest (reverse entries))
                 acc (f val first-key first-value)]
            (if (empty? left-entries) acc
                (let [[cur-key cur-value] (first left-entries)]
                  (recur (rest left-entries) (f acc cur-key cur-value)))))))))

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

(defn- equals-node [node another]
  (if (not (or (instance? TrieNode node) (instance? TrieNode another)))
    (throw (ex-info "Wrong type of args" {:args [node another]}))
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
    (throw (ex-info "Wrong type of args" {:args [root another]}))
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

(comment
  (def trie (->RootNode {\a (->TrieNode \a nil false {\b (->TrieNode \b 5 true {\c (->TrieNode \c 5 true {})}) \c (->TrieNode \c 6 true {})})}))
  (tget trie "ab")
  (tget trie "abcd")
  (insert trie "cb" 7)
  (get-entries trie)
  (trie-from-entries (get-entries trie))
  (get-keys trie)
  (get-values trie)
  (filter-root (fn [_ v] (> v 4)) trie)
  (tfilter trie (fn [_ v] (> v 5)))
  (tmap trie (fn [k v] [k (* v v)]))
  (reduce-left-root (fn [acc _ v] (str acc v)) "str: " trie)
  (reduce-right-root (fn [acc _ v] (str acc v)) "str: " trie)
  (reducel trie (fn [acc _ v] (str acc v)) "str: ")
  (reducer trie (fn [acc _ v] (str acc v)) "str: ")
  (join-root trie [trie trie])
  (def trie-b (->RootNode {\b (->TrieNode \b nil false {\b (->TrieNode \b 5 true {\c (->TrieNode \c 5 true {})}) \c (->TrieNode \c 6 true {})})}))
  (def trie-c (->RootNode {\a (->TrieNode \a nil false {\b (->TrieNode \b 10 true {\d (->TrieNode \d 5 true {})}) \c (->TrieNode \c 6 true {})})}))
  (get-keys trie-b)
  (join-root trie [trie-b])
  (get-keys (join-root trie [trie-b]))
  (join-root trie (empty-trie))
  (join trie trie-c)
  (get-entries (join trie trie-c))
  (get-entries trie)
  (get-entries trie-c)
  (equals-root trie trie)
  (equals-root trie trie-b)
  (equals-root trie trie-c)
  (equals-root trie (empty-trie))
  (equals-root (empty-trie) (empty-trie))
  (tequals? trie trie)
  (tequals? trie trie-b))
