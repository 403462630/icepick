(ns icepick.emitter
  (:require [icepick.analyzer :refer :all]
            [clojure.string :as string])
  (:import (icepick                     Icepick)
           (javax.lang.model.element    Element)))

(def ^:dynamic *filer*)

(def ^:private suffix Icepick/SUFFIX)

(defn- ->java [coll & {:keys [indent]}]
  (->> coll
       (map (partial apply str indent))
       (string/join "\n")))

;; Restore state

(def ^:private start-restore-view
  [["Bundle savedInstanceState = (Bundle) state;"]
   ["Parcelable superState = savedInstanceState.getParcelable(BASE_KEY + \"$$SUPER\");"]])

(def ^:private start-restore-obj
  [["if (state == null) {"]
   ["  return null;"]
   ["}"]
   ["Bundle savedInstanceState = state;"]])

(def ^:private end-restore-view
  [["return parent.restoreInstanceState(target, superState);"]])

(def ^:private end-restore-obj
  [["return parent.restoreInstanceState(target, savedInstanceState);"]])

(defn- restore [{method :bundle-method name :name cast :type-cast}]
  (let [key (str "BASE_KEY + \"" name "\"")]
    [["if (savedInstanceState.containsKey(" key ")) {"]
     ["  target." name " = " cast "savedInstanceState.get" method "(" key ");"]
     ["}"]]))

;; Save state

(def ^:private start-save-view
  [["Bundle outState = new Bundle();"]
   ["Parcelable superState = parent.saveInstanceState(target, state);"]
   ["outState.putParcelable(BASE_KEY + \"$$SUPER\", superState);"]])

(def ^:private start-save-obj
  [["parent.saveInstanceState(target, state);"]
   ["Bundle outState = state;"]])

(def ^:private end-save-view
  [["return outState;"]])

(def ^:private end-save-obj
  [["return outState;"]])

(defn- save [{method :bundle-method name :name primitive? :primitive?}]
  (let [key (str "BASE_KEY + \"" name "\"")]
    (if primitive?
      [["outState.put" method "(" key ", target." name ");"]]
      [["if (target." name " != null) {"]
       ["  outState.put" method "(" key ", target." name ");"]
       ["}"]])))

;; Brew

(defn- brew-source [class fields android-view?]
  (let [package (:package class)
        dollar-name (:dollar-name class)
        qualified-dollar-name (str package "." dollar-name suffix)
        target (:dotted-name class)
        state (if android-view?
                "Parcelable"
                "Bundle")
        helper-type (str "StateHelper<" state ">")
        parent-helper (if-let [parent (:qualified-parent-name class)]
                        (str "new " parent suffix "();")
                        (str "(" helper-type ") StateHelper.NO_OP;"))]
    [["// Generated code from Icepick. Do not modify!"]
     ["package " package ";"]
     ["import android.os.Bundle;"]
     ["import android.os.Parcelable;"]
     ["import icepick.StateHelper;"]
     ["public class " dollar-name " implements " helper-type " {"]
     [""]
     ["  private static final String BASE_KEY = \"" qualified-dollar-name ".\";"]
     ["  private final " helper-type " parent = " parent-helper]
     [""]
     ["  public " state " restoreInstanceState(Object obj, " state " state) {"]
     ["    " target " target = (" target ") obj;"]
     [(->java (if android-view? start-restore-view start-restore-obj) :indent "    ")]
     [(->java (mapcat restore fields) :indent "    ")]
     [(->java (if android-view? end-restore-view end-restore-obj) :indent "    ")]
     ["  }"]
     [""]
     ["  public " state " saveInstanceState(Object obj, " state " state) {"]
     ["    " target " target = (" target ") obj;"]
     [(->java (if android-view? start-save-view start-save-obj) :indent "    ")]
     [(->java (mapcat save fields) :indent "    ")]
     [(->java (if android-view? end-save-view end-save-obj) :indent "    ")]
     ["  }"]
     ["}"]]))

(defn emit-class! [[class fields]]
  (let [qualified-dotted-name (str (:package class) "." (:dotted-name class) suffix)
        element (:element class)
        file-object (.createSourceFile
                     *filer* qualified-dotted-name (into-array Element [element]))
        android-view? #_(.isAssignable
                         *types* (.asType element)
                         (.asType (.getTypeElement *elements* "android.view.View")))
        #_remove-me false
        _ (def quiz (->java (brew-source class fields android-view?)))]
    [class fields]
    (doto (.openWriter file-object)
      (.write (->java (brew-source class fields android-view?)))
      (.flush)
      (.close))))
