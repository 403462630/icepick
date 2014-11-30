(ns icepick.analyzer
  (:require [clojure.string :as string])
  (:import (icepick                     Icicle)
           (javax.annotation.processing Messager)
           (javax.lang.model            SourceVersion)
           (javax.lang.model.type       TypeMirror
                                        TypeKind)
           (javax.lang.model.element    Element
                                        TypeElement
                                        Modifier)
           (javax.lang.model.util       Elements
                                        ElementFilter
                                        Types)
           (javax.tools                 Diagnostic$Kind)))

(def ^:dynamic *messager*)
(def ^:dynamic *elements*)
(def ^:dynamic *types*)

(defn- log-error [elem msg]
  (.printMessage ^Messager *messager* Diagnostic$Kind/ERROR msg elem))

;; Bundle method

(def ^:private method-dictionary
  [["boolean" "Boolean"]
   ["boolean[]" "BooleanArray"]
   ["byte" "Byte"]
   ["byte[]" "ByteArray"]
   ["char" "Char"]
   ["char[]" "CharArray"]
   ["short" "Short"]
   ["short[]" "ShortArray"]
   ["int" "Int"]
   ["int[]" "IntArray"]
   ["long" "Long"]
   ["long[]" "LongArray"]
   ["float" "Float"]
   ["float[]" "FloatArray"]
   ["double" "Double"]
   ["double[]" "DoubleArray"]
   ["java.lang.String" "String"]
   ["java.lang.String[]" "StringArray"]
   ["java.lang.CharSequence" "CharSequence"]
   ["java.lang.CharSequence[]" "CharSequenceArray"]
   ["java.util.ArrayList<java.lang.Integer>" "IntegerArrayList"]
   ["java.util.ArrayList<java.lang.String>" "StringArrayList"]
   ["java.util.ArrayList<java.lang.CharSequence>" "CharSequenceArrayList"]
;   ["java.util.ArrayList<? extends android.os.Parcelable>" "ParcelableArrayList"]
;   ["android.util.SparseArray<? extends android.os.Parcelable>" "SparseParcelableArray"]
;   ["android.os.Bundle" "Bundle"]
;   ["android.os.Parcelable" "Parcelable"]
;   ["android.os.Parcelable[]" "ParcelableArray"]
   ["java.io.Serializable" "Serializable"]])

(defmulti ->type
  (fn [name]
    (cond
     (not= -1 (.indexOf name "<")) :generic
     (.endsWith name "[]")         :array
     (= -1 (.indexOf name "."))    :primitive
     :else                         :simple)))

(defmethod ->type :generic [name]
  (let [class-name (subs name 0 (.indexOf name "<"))
        generic-name (subs name (inc (.indexOf name "<")) (.indexOf name ">"))
        class-elem (.getTypeElement *elements* class-name)
        generic-type (if (.startsWith generic-name "?")
                       (-> (subs generic-name (count "? extends "))
                           (->type)
                           (#(.getWildcardType *types* % nil)))
                       (->type generic-name))]
    (.getDeclaredType *types* class-elem (into-array TypeMirror [generic-type]))))

(defmethod ->type :array [name]
  (->> (string/replace name "[]" "") ->type (.getArrayType *types*)))

(defmethod ->type :primitive [name]
  (->> name string/upper-case TypeKind/valueOf (.getPrimitiveType *types*)))

(defmethod ->type :simple [name]
  (->> name (.getTypeElement *elements*) (.asType)))

(defn- assignable? [^TypeMirror type1 ^TypeMirror type2]
  (.isAssignable *types* type1 type2))

;;TODO memoize ->type ?
(defn- bundle-method [^TypeMirror type]
  (first (for [[signature method] method-dictionary
               :let [signature-type (->type signature)]
               :when (assignable? type signature-type)]
           method)))

;; Enclosing class

(def ^:private analyzed-classes
  (atom {:annotated {}
         :not-annotated #{}}))

(defn- package-name [^TypeElement elem]
  (-> ^Elements *elements* (.getPackageOf elem) .getQualifiedName str))

;; TODO duplication
(defn- qualified-dollar-name [^TypeElement elem]
  (let [qualified-name (str (.getQualifiedName elem))
        package (package-name elem)
        dotted-name (subs qualified-name (inc (count package)))
        dollar-name (string/replace dotted-name #"\." "\\$")]
    (str package "." dollar-name)))

(defn- annotated-class? [^TypeElement elem]
  (seq (for [field (ElementFilter/fieldsIn (.getEnclosedElements elem))
             ann (.getAnnotationMirrors field)
             :when (= (.getName Icicle)
                      (-> ann .getAnnotationType .asElement str))]
         field)))

(def ^:private java-prefix "java.")
(def ^:private android-prefix "android.")

(defn- qualified-parent-name [^TypeElement elem]
  (loop [^TypeMirror type (.getSuperclass elem)]
    (when-not (= (.getKind type) TypeKind/NONE)
      (let [class-element (.asElement type)
            class-name (str class-element)]
        (when-not (or (.startsWith class-name java-prefix)
                      (.startsWith class-name android-prefix))
            (cond
             (contains? (:annotated @analyzed-classes) class-name)
             (get-in @analyzed-classes [:annotated class-name])

             (contains? (:not-annotated @analyzed-classes) class-name)
             (recur (.getSuperclass class-element))

             (annotated-class? class-element)
             (let [qualified-name (qualified-dollar-name class-element)]
               (swap! analyzed-classes assoc-in
                      [:annotated class-name] qualified-name)
               qualified-name)

             :else
             (do
               (swap! analyzed-classes update-in [:not-annotated] conj class-name)
               (recur (.getSuperclass class-element)))))))))

(defn- enclosing-class [^TypeElement elem]
  (when (some #{Modifier/PRIVATE} (.getModifiers elem))
    (log-error elem "Enclosing class must not be private"))
  (let [package (package-name elem)
        qualified-name (str (.getQualifiedName elem))
        dotted-name (subs qualified-name (inc (count package)))
        dollar-name (string/replace dotted-name #"\." "\\$")]
    {:element elem
     :package package
     :dotted-name dotted-name
     :dollar-name dollar-name
     :qualified-parent-name (qualified-parent-name elem)}))

;; Type cast

(def ^:private needs-type-erasure
  {"ParcelableArrayList" "java.util.ArrayList"
   "SparseParcelableArray" "android.util.SparseArray"})

(defn- force-type-erasure [field-type bundle-method]
  (when-let [erasure (get needs-type-erasure bundle-method)]
    (str "(" field-type ") (" erasure ")")))

(def ^:private needs-type-cast
  #{"IntegerArrayList" "StringArrayList" "CharSequenceArrayList" "CharSequence"
    "CharSequenceArray" "Parcelable" "ParcelableArray" "Serializable"})

(defn- simple-type-cast [field-type bundle-method]
  (when (needs-type-cast bundle-method)
    (str "(" field-type ")")))

(defn- type-cast [field-type bundle-method]
  (or (force-type-erasure field-type bundle-method)
      (simple-type-cast field-type bundle-method)))

;; Analyze field

(defn analyze-field
  "Converts javax Element into a suitable representation for code generation."
  [^Element elem]
  (when (some #{Modifier/PRIVATE Modifier/STATIC Modifier/FINAL} (.getModifiers elem))
    (log-error elem "Field must not be private, static or final"))
  (let [^TypeMirror type (.asType elem)
        bundle-method (bundle-method type)]
    (when-not bundle-method
      (log-error elem (str "Don't know how to put a " type " inside a Bundle")))
    {:name (-> elem .getSimpleName .toString)
     :type (str type)
     :type-cast (type-cast type bundle-method)
     :enclosing-class (enclosing-class (.getEnclosingElement elem))
     :bundle-method bundle-method
     :primitive? (-> type .getKind .isPrimitive)}))
