(ns icepick.core
  (:require [icepick.analyzer :refer :all]
            [icepick.emitter :refer :all])
  (:import (icepick                     Icicle)
           (javax.annotation.processing RoundEnvironment)
           (javax.lang.model            SourceVersion))
  (:gen-class
   :name icepick.processor.IcepickProcessor
   :extends javax.annotation.processing.AbstractProcessor
   :exposes {processingEnv {:get processingEnv}}
   :main false))

;;(alter-var-root #'*use-context-classloader* (constantly false))

(defn -getSupportedSourceVersion [this]
  (SourceVersion/latestSupported))

(defn -getSupportedAnnotationTypes [this]
  #{(.getName Icicle)})

(defn -process
  "docstring"
  [this annotations ^RoundEnvironment env]
  (binding [*messager* (-> this .processingEnv .getMessager)
            *elements* (-> this .processingEnv .getElementUtils)
            *types*    (-> this .processingEnv .getTypeUtils)
            *filer*    (-> this .processingEnv .getFiler)]
    (doseq [ann annotations]
      (->> (.getElementsAnnotatedWith env ann)
           (map analyze-field)
           (group-by :enclosing-class)
           (map emit-class!)
           (doall))))
  true)
