;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.contrib.experimental.ryssel_algorithm
  (:use conexp.base
        conexp.fca.contexts
        conexp.fca.implications))

(ns-doc "An implementation of Ryssels Algorithm")

;;;

(defn- minimal-covers [base-set sets]
  (let [covers (if (subset? base-set (reduce union sets))
                 [sets]
                 [])]
    (loop [covers         covers,
           minimal-covers []]
      (if (empty? covers)
        minimal-covers
        (let [next-cover     (first covers),
              smaller-covers (filter #(subset? base-set (reduce union %))
                                     (map #(disj next-cover %) next-cover))]
          (if (not-empty smaller-covers)
            (recur (concat smaller-covers (rest covers))
                   minimal-covers)
            (recur (rest covers)
                   (conj minimal-covers next-cover))))))))

(defn- cover [base-set candidates m extent]
  (minimal-covers (difference base-set (extent m))
                  (map #(difference base-set (extent %))
                       candidates)))

(defn ryssel-base
  "Returns the set of implications computed by Ryssels Algorithm."
  [ctx]
  (let [attribute-extent (memoize #(attribute-derivation ctx #{%}))]
    (loop [implications #{},
           atts         (attributes ctx)]
      (if (empty? atts)
        implications
        (let [m            (first atts),
              implications (union implications
                                  (set-of (make-implication #{m} #{n})
                                          [n (disj (attributes ctx) m)
                                           :when (subset? (attribute-extent m)
                                                          (attribute-extent n))])),
              candidates   (set-of n
                                   [n (attributes ctx)
                                    :when (not (exists [p (attributes ctx)]
                                                 (and (subset? (intersection (attribute-extent n)
                                                                             (attribute-extent m))
                                                               (attribute-extent p))
                                                      (proper-subset? (attribute-extent p)
                                                                      (attribute-extent m)))))]),
              candidates   (disj candidates (attribute-extent m)),
              covers       (cover (objects ctx) candidates m attribute-extent),
              implications (union implications
                                  (set-of (make-implication X #{m})
                                          [X covers
                                           :when (not (contains? X m))]))]
          (recur implications (rest atts)))))))          

;;;

nil
