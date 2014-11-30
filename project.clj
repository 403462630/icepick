(defproject com.github.frankiesardo/icepick-processor "2.4.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.github.frankiesardo/icepick "2.3.6"]]
  :profiles {:dev {:dependencies [[com.google.testing.compile/compile-testing "0.4"]]}
             :provided {:dependencies [[com.google.android/android "4.1.1.4"]]}
             :uberjar {:aot :all}})
