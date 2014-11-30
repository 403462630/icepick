(ns icepick.emitter
  (:require [icepick.analyzer :refer :all]
            [clojure.string :as string])
  (:import (icepick Icepick)))

(def ^:dynamic *filer*)

(def ^:private suffix Icepick/SUFFIX)

;; Restore state

(def ^:private start-restore-view
  ["Bundle savedInstanceState = (Bundle) state;"
   "Parcelable superState = savedInstanceState.getParcelable(BASE_KEY + \"$$SUPER\");"])

(def ^:private start-restore-obj
  ["if (state == null) {"
   "  return null;"
   "}"
   "Bundle savedInstanceState = state;"])

(def ^:private end-restore-view
  ["return parent.restoreInstanceState(target, superState);"])

(def ^:private end-restore-obj
  ["return parent.restoreInstanceState(target, savedInstanceState);"])

(defn- restore [fields]
  [])

;; Save state

(def ^:private start-save-view
  ["Bundle outState = new Bundle();"
   "Parcelable superState = parent.saveInstanceState(target, state);"
   "outState.putParcelable(BASE_KEY + \"$$SUPER\", superState);"])

(def ^:private start-save-obj
  ["parent.saveInstanceState(target, state);"
   "Bundle outState = state;"])

(def ^:private end-save-view
  ["return outState;"])

(def ^:private end-save-obj
  ["return outState;"])

;;

(defn- join-and-indent [coll]
  (->> coll
       (map (partial apply str "    "))
       (string/join "\n")))

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
     [(join-and-indent (if android-view? start-restore-view start-restore-obj))]
     [(join-and-indent (restore fields))]
     [(join-and-indent (if android-view? end-restore-view end-restore-obj))]
     ["  }"]
     [""]
     ["  public " state "saveInstanceState(Object obj, " state " state) {"]
     ["    " target " target = (" target ") obj;"]
     [(string/join "\n" (start-restore android-view?))]
     [(string/join "\n" (restore fields))]
     [(string/join "\n" (end-restore android-view?))]
     ["  }"]
     ]))

(defn emit-class! [[class fields]]
  (let [qualified-dotted-name (str (:package class) "." (:dotted-name class) suffix)
        element (:element class)
        file-object (.createSourceFile *filer* qualified-dotted-name element)
        android-view? (.isAssignable
                       *types* (.asType element)
                       (.asType (.getTypeElement *elements* "android.view.View")))]
    (doto (.openWriter file-object)
      (.write (->> (brew-source class fields android-view?)
                   (map string/join)
                   (string/join "\n")))
      (.flush)
      (.close))))
