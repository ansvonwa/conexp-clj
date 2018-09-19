;; Copyright ⓒ the conexp-clj developers; all rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.fca.more
  "More on FCA."
  (:require [conexp.base :refer :all]
            [clojure.core.reducers :as r]
            [conexp.fca
             [contexts :refer :all]
             [exploration :refer :all]
             [implications :refer :all]]
            [conexp.math.util :refer [eval-polynomial]]))

;;; Bonds

(defn smallest-bond
  "Returns the smallest bond between ctx-1 and ctx-2 that has the
  elements of rel as crosses."
  [ctx-1 ctx-2 rel]
  (loop [ctx-rel (make-context-nc (objects ctx-1) (attributes ctx-2) rel)]
    (let [next-rel (union (set-of [g m] [m (attributes ctx-2),
                                         g (attribute-derivation
                                            ctx-1
                                            (object-derivation
                                             ctx-1
                                             (attribute-derivation
                                              ctx-rel
                                              #{m})))])
                          (set-of [g m] [g (objects ctx-1),
                                         m (object-derivation
                                            ctx-2
                                            (attribute-derivation
                                             ctx-2
                                             (object-derivation
                                              ctx-rel
                                              #{g})))]))]
      (if (= next-rel (incidence ctx-rel))
        ctx-rel
        (recur (make-context-nc (objects ctx-1) (attributes ctx-2) next-rel))))))

(defn bond?
  "Checks whether context ctx is a bond between ctx-1 and ctx-2."
  [ctx-1 ctx-2 ctx]
  (and (context? ctx)
       (context? ctx-1)
       (context? ctx-2)
       (= (objects ctx-1) (objects ctx))
       (= (attributes ctx-2) (attributes ctx))
       (forall [g (objects ctx-1)]
         (let [g-R (object-derivation ctx #{g})]
           (= g-R (context-attribute-closure ctx g-R))))
       (forall [m (attributes ctx-2)]
         (let [m-R (attribute-derivation ctx #{m})]
           (= m-R (context-object-closure ctx m-R))))))

(defn all-bonds
  "Returns all bonds between ctx-1 and ctx-2."
  [ctx-1 ctx-2]
  (let [G-1     (objects ctx-1),
        M-2     (attributes ctx-2),
        impls-1 (set-of (make-implication (cross-product A #{m})
                                          (cross-product B #{m}))
                        [impl (stem-base (dual-context ctx-1)),
                         :let [A (premise impl),
                               B (conclusion impl)],
                         m M-2]),
        impls-2 (set-of (make-implication (cross-product #{g} A)
                                          (cross-product #{g} B))
                        [impl (stem-base ctx-2),
                         :let [A (premise impl),
                               B (conclusion impl)],
                         g G-1]),
        clop (clop-by-implications (union impls-1 impls-2))]
    (map #(make-context (objects ctx-1) (attributes ctx-2) %)
         (all-closed-sets (cross-product (objects ctx-1) (attributes ctx-2))
                          clop))))

;;; Shared Intents (implemented by Stefan Borgwardt)

(defn- next-shared-intent
  "The smallest shared intent of contexts ctx-1 and ctx-2 containing b."
  [ctx-1 ctx-2 b]
  (loop [shared-attrs b,
         objs-1       (attribute-derivation ctx-1 b),
         objs-2       (attribute-derivation ctx-2 b)]
    (let [new-shared-attrs-1 (set-of m [m (difference (attributes ctx-1) shared-attrs)
                                        :when (forall [g objs-1]
                                                ((incidence ctx-1) [g m]))]),
          new-shared-attrs-2 (set-of m [m (difference (attributes ctx-2) shared-attrs)
                                        :when (forall [g objs-2]
                                                ((incidence ctx-2) [g m]))])]
      (if (and (empty? new-shared-attrs-1) (empty? new-shared-attrs-2))
        shared-attrs
        (recur (union shared-attrs new-shared-attrs-1 new-shared-attrs-2)
               (set-of g [g objs-1
                          :when (forall [m new-shared-attrs-2]
                                  ((incidence ctx-1) [g m]))])
               (set-of g [g objs-2
                          :when (forall [m new-shared-attrs-1]
                                  ((incidence ctx-2) [g m]))]))))))

(defn all-shared-intents
  "All intents shared between contexts ctx-1 and ctx-2. ctx-1 and
  ctx-2 must have the same attribute set."
  [ctx-1 ctx-2]
  (assert (= (attributes ctx-1) (attributes ctx-2)))
  (all-closed-sets (attributes ctx-1) #(next-shared-intent ctx-1 ctx-2 %)))

(defn all-bonds-by-shared-intents
  "All bonds between ctx-1 and ctx-2, computed using shared intents."
  [ctx-1 ctx-2]
  (map #(make-context (objects ctx-1) (attributes ctx-2) %)
       (all-shared-intents (context-product (adiag-context (objects ctx-1)) ctx-2)
                           (dual-context (context-product ctx-1 (adiag-context (attributes ctx-2)))))))

;;; Context for Closure Operators

(defn- maximal-counterexample
  "For a given closure operator c on a set base-set, maximizes the set
  C (i.e. returns a superset of C) that is not a superset of
  B (i.e. there exists an element m in B without C that is also not in
  the result."
  [c base-set B C]
  (let [m (first (difference B C))]
    (loop [C C,
           elts (difference (disj base-set m)
                            C)]
      (if (empty? elts)
        C
        (let [n (first elts),
              new-C (c (conj C n))]
          (if (contains? new-C m)
            (recur C (rest elts))
            (recur new-C (rest elts))))))))

(defn context-from-clop
  "Returns a context whose intents are exactly the closed sets of the
  given closure operator on the given base-set."
  [base-set clop]
  (let [base-set (set base-set)]
    (:context
     (explore-attributes :context (make-context #{} base-set #{})
                         :handler (fn [_ _ impl]
                                    (let [A (premise impl),
                                          B (conclusion impl),
                                          C (clop A)]
                                      (when-not (subset? B C)
                                        [[(gensym) (maximal-counterexample clop base-set B C)]])))))))

;;; Certain contexts

(defn implication-context
  "Returns a context for a non-negative number n which has as it's
  extents the closure systems on the set {0 .. (n-1)} and as it's
  intents the corresponding implicational theory."
  [n]
  (assert (and (number? n) (<= 0 n))
          "Must be given a non-negative number.")
  (let [base-set  (set-of-range 0 n)
        base-sets (subsets base-set)]
    (make-context base-sets
                  (set-of (make-implication A #{x}) |
                          A base-sets
                          x (difference base-set base-sets))
                  respects?)))

;;; Concept Stability and the like

(defn concept-stability
  "Compute the concept stability of `concept' in `context'."
  [context concept]

  (assert (context? context)
          "First argument must be a formal context.")
  (assert (and (vector? concept)
               (= 2 (count concept))
               (concept? context concept))
          "Second argument must be a formal concept of the given context.")

  (let [[extent intent] [(first concept) (second concept)]
        counter         (fn counter
                          ;; Perform depth-first search to count all subsets of
                          ;; `extent' whose derivation in context yields `intent'.
                          ;; For this we keep the list `fixed-included' of already
                          ;; considered elements to be included in the target
                          ;; subset of `extent', and the list `unfixed' of
                          ;; unconsidered elements for which it is still to be
                          ;; decided of whether they will be included or not.
                          [fixed-included unfixed]
                          (if (empty? unfixed)
                            1
                            (let [some-element (first unfixed)]
                              (+ (counter (conj fixed-included some-element)
                                          (disj unfixed some-element))
                                 (if (= intent
                                        (object-derivation context
                                                           (union fixed-included
                                                                  (disj unfixed some-element))))
                                   (counter fixed-included (disj unfixed some-element))
                                   0)))))]
    (/ (counter #{} extent)
       (expt 2 (count extent)))))

(def ^:dynamic *fast-computation*
  "Enable computation of concept probability with floating point arithmetic
  instead of rationals"
  nil)

(defn concept-probability
  "Compute the probability of a `concept' in `context' 𝕂 in the following manner.
  Let pₘ be the relative frequence of attribute m in context.  The
  probability of a subset B ⊆ M in 𝕂 is the product of all pₘ for m ∈ B.
  Then the probability of a concept is defined by pr(A,B) := pr(B=B'')
  which is ∑_{k=0}^n {n choose k}·p_Bᵏ·(1-p_B)ⁿ⁻ᵏ·∏_{m ∈ M ∖ B}(1-p_mᵏ)."
  [context concept]
  (let [nr_of_objects (count (objects context))
        n  (if *fast-computation* (double nr_of_objects) nr_of_objects)
        M (attributes context)
        B (second concept) ; intent of concept
        P_M_B (mapv #(/ (count (attribute-derivation context #{%})) n ) (difference M B))
        p_B (r/fold * (map #(/ (count (attribute-derivation context #{%})) n) B))
        one_minus_p_B_n (expt (- 1 p_B) n)]
    (loop [k 1  ;; since for k=0 the last term is 0, we can start with 1
           result 0
           binomial n ;; since k=0 the start binomial is n
           p_B_k  p_B
           one_minus_p_B_k  (/ one_minus_p_B_n (- 1 p_B))
           P_M_B_k P_M_B ]
      (if (or (== k n) (== p_B_k 0)) ;; either done or underflowed probability (double)
        result
        (let [new_res
              (* binomial p_B_k one_minus_p_B_k
                 (r/fold * (map #(- 1 %) P_M_B_k)))]
          (recur
           (inc k)
           (+ new_res result)
           (* binomial (/ (- n k) (inc k)))
           (* p_B_k p_B)
           (/ one_minus_p_B_k (- 1 p_B))
           (mapv (partial *) P_M_B_k P_M_B)))))))


;;; Robustness of Concepts

(defn- concept-robustness-add-next-entry
  "Helper-function for `concept-robustness-polynomial'.

  This function computes the value e(Y,`concept'), based on the already computed
  values e(Z,`concept'), which are given in the second parameter `cache' in the
  form [Z, e(Z, `concept')].

  This function is needed in the algorithm on page 19 in \"Finding Robust
  Itemsets under Subsampling\" from Tatti, Moerchen, and Calders."
  [concept cache]
  (let [newvalue (reduce
                  (fn [x V] (- x (second V)))
                  0
                  (filter #(subset? (second (first %)) (second concept))
                          cache))]
    (conj cache [concept, newvalue])))

(defn concept-robustness-polynomial
  "Return the coefficients of the robustness polynomial of `concept'.

  For the given `concept' of a context, the coefficients of the polynomial p
  corresponding to the robustness is computed by using the seq `concepts' of
  all concepts of the context.  The optional boolean parameter `sorted?' allows
  to declare the seq of concepts as being already sorted by increasing
  attribute-set.  Thus if v is the result of (robustness-polynomial concept
  concepts), then (eval-polynomial v (- 1 alpha)) computes the robustness with
  parameter alpha.

  For details see \"Finding Robust Itemsets under Subsampling\" from Tatti,
  Moerchen, and Calders, pages 17–19."
  ([concept concepts sorted?]
   (let [B (second concept)
         used-concepts (drop 1 (if sorted?
                                 (filter #(subset? B (second %)) concepts)
                                 (sort-by #(count (second %))
                                          (filter #(subset? B (second %))
                                                  concepts))))
         ;; store for all subconcepts (C_A,C_B) of concept the vector [C, e(C_B, concept)]
         concepts-with-values (reduce
                               (fn [x y]
                                 (concept-robustness-add-next-entry y x))
                               [[concept, 1]]
                               used-concepts)
         sup (count (first concept))]
     ;; use the above computed values [C, e(C_B, concept)] to compute the polynomial
     (reduce
      (fn [old-coefficients entry]
        (let [index (- sup (count (first (first entry))))]
          (assoc old-coefficients
                 index (+ (nth old-coefficients index) (second entry)))))
      (vec (take (+ 1 sup) (repeat 0)))
      concepts-with-values)))
  ([concept concepts]
   (concept-robustness-polynomial concept concepts false)))

(defn concept-robustness
  "Computes the robustness of a `concept' in a context with parameter `alpha' by
  using the seq `concepts' consisting of all concepts of the context.  The
  optional boolean parameter `sorted?' allows to declare the seq of concepts as
  beeing already sorted by increasing size of the attribute set.  This function
  uses the function concept-robustness-polynomial."
  ([concept concepts alpha sorted?]
   (assert (and (number? alpha)
                (<= 0 alpha 1))
           "Third argument must be between 0 and 1!")
   (eval-polynomial (concept-robustness-polynomial concept concepts sorted?) (- 1 alpha)))
  ([concept concepts alpha]
   (concept-robustness concept concepts alpha false)))

(defn average-concept-robustness
  "Takes the seq `concepts' consisting of all concepts of a context and computes
  the average concept robustness with parmater `alpha'."
  [concepts alpha]
  (assert (and (number? alpha)
               (<= 0 alpha 1))
          "Second argument must be between 0 and 1!")
  (let [sorted-concepts (sort-by
                         #(count (second %))
                         concepts)
        n (count sorted-concepts)
        robustness-values (map
                            #(concept-robustness
                               (nth sorted-concepts %)
                               (drop % sorted-concepts)
                               alpha
                               true)
                            (range 0 n))]
    (/ (reduce + robustness-values) n)))


;;; Similarity Measures for Concepts (implemented by Anselm von Wangenheim)

(defn jaccard-index
  "Computes the Jaccard index of two sets. This is |x ∩ y| / |x ∪ y|."
  [x y]
  (/ (double (count (intersection x y))) (count (union x y))))

(defn sorensen-coefficient
  "Computes the Sorensen coefficient of two sets.
  This is 2 * |x ∩ y| / (|x| + |y|)."
  [x y]
  (/ (* 2.0 (count (intersection x y))) (+ (count x) (count y))))

(defn symmetric-difference
  "Computes the symmetric difference of two sets.
  This is 1 - (|(x\y) ∪ (y\x)| / |x ∪ y|)."
  [x y]
  (- 1.0 (/
          (count (union (difference x y) (difference y x)))
          (count (union x y)))))

(defn weighted-concept-similarity
  "Computes a weighted concept similarity for a given similatity measure `sim',
  two concepts [`C1' `C2'] and an optional weight `w' (default is 0.5).
  That is the weighted average of the similarity of the extents/object sets
  (weight `w') and the intents/attribute sets (weight 1-`w')"
  ([sim [C1 C2]] (weighted-concept-similarity sim [C1 C2] 0.5))
  ([sim [C1 C2] w]
   (assert (and (number? w)
                (<= 0 w 1))
           "Thrid argument must be between 0 and 1!")
   (+
    (* w       (sim (C1 0) (C2 0)))
    (* (- 1 w) (sim (C1 1) (C2 1))))))

;;;

nil
