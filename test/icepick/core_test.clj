(ns icepick.core-test
  (:require [clojure.test :refer :all]
            [icepick.core :refer :all]
            [clojure.string :as string])
  (:import [icepick.processor IcepickProcessor]
           [javax.tools JavaFileObject]
           [com.google.testing.compile JavaFileObjects]
           [com.google.testing.compile JavaSourceSubjectFactory]
           [org.truth0 Truth]))

(defn- java-source []
  (JavaSourceSubjectFactory/javaSource))

(defn- icepick-processors []
  [(IcepickProcessor.)])

(defn- make-source [{:keys [file content]}]
  (JavaFileObjects/forSourceString file (string/join "\n" content)))

(defn- check-fails [input]
  (is (-> (Truth/ASSERT)
          (.about (java-source))
          (.that (make-source input))
          (.processedWith (icepick-processors))
          (.failsToCompile))))

(defn- check-compiles
  ([input]
     (let [input-source (make-source input)]
       (is (-> (Truth/ASSERT)
               (.about (java-source))
               (.that input-source)
               (.processedWith (icepick-processors))
               (.compilesWithoutError)))))
  ([input output & outputs]
     (let [[first & rest] (seq (map make-source (cons output outputs)))]
       (-> (check-compiles input)
           (.and)
           (.generatesSources first (into-array JavaFileObject rest))))))

(deftest a-test
  (testing "Invalid modifier"
    (check-fails
     {:file "test.Test"
      :content ["package test;"
                "import icepick.Icicle;"
                "public class Test {"
                "  @Icicle private int thing;"
                "}"]}))
  (testing "Simple"
    (check-compiles
     {:file "test.Test"
      :content
      ["package test;"
       "import icepick.Icicle;"
       "public class Test {"
       "  @Icicle int thing;"
       "  @Icicle String str;"
       "}"]}
     {:file "test.Test$$Icicle"
      :content
      ["package test;"
       "import android.os.Bundle;"
       "import android.os.Parcelable;"
       "import icepick.StateHelper;"
       "public class Test$$Icicle implements StateHelper<Bundle> {"
       "  private static final String BASE_KEY = \"test.Test$$Icicle.\";"
       "  private final StateHelper<Bundle> parent = (StateHelper<Bundle>) StateHelper.NO_OP;"
       "  public Bundle restoreInstanceState(Object obj, Bundle state) {"
       "    Test target = (Test) obj;"
       "    if (state == null) {"
       "      return null;"
       "    }"
       "    Bundle savedInstanceState = state;"
       "    target.thing = savedInstanceState.getInt(BASE_KEY + \"thing\");"
       "    return parent.restoreInstanceState(target, savedInstanceState);"
       "  }"
       "  public Bundle saveInstanceState(Object obj, Bundle state) {"
       "    Test target = (Test) obj;"
       "    parent.saveInstanceState(target, state);"
       "    Bundle outState = state;"
       "    outState.putInt(BASE_KEY + \"thing\", target.thing);"
       "    return outState;"
       "  }"
       "}"]}))
  #_(testing "Parent"
      (check-compiles
       {:file "test.Test"
        :content ["package test;"
                  "import icepick.Icicle;"
                  "public class Test<T> {"
                  "  @Icicle String field1;"
                  "  static class Inner extends Test<String> {"
                  "    @Icicle int field2;"
                  "    @Icicle java.util.ArrayList<String> field3;"
                  "  }"
                  "}"]}
       {:file "test.Test$$Icicle"
        :content ["package test;"
                  "import android.os.Bundle;"
                  "import android.os.Parcelable;"
                  "import icepick.StateHelper;"
                  "public class Test$$Icicle implements StateHelper<Bundle> {"
                  "  private static final String BASE_KEY = \"test.Test$$Icicle.\";"
                  "  private final StateHelper<Bundle> parent = (StateHelper<Bundle>) StateHelper.NO_OP;"
                  "  public Bundle restoreInstanceState(Object obj, Bundle state) {"
                  "    Test target = (Test) obj;"
                  "    if (state == null) {"
                  "      return null;"
                  "    }"
                  "    Bundle savedInstanceState = state;"
                  "    target.thing = savedInstanceState.getFloat(BASE_KEY + \"thing\");"
                  "    return parent.restoreInstanceState(target, savedInstanceState);"
                  "  }"
                  "  public Bundle saveInstanceState(Object obj, Bundle state) {"
                  "    Test target = (Test) obj;"
                  "    parent.saveInstanceState(target, state);"
                  "    Bundle outState = state;"
                  "    outState.putFloat(BASE_KEY + \"thing\", target.thing);"
                  "    return outState;"
                  "  }"
                  "}"]})))
