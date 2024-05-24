;Sourced from https://blaster.ai/blog/posts/manage-git-hooks-w-babashka.html
(ns git-hooks
  (:require [babashka.fs :as fs]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(defn changed-files []
  (->> (sh "git" "diff" "--name-only" "--cached" "--diff-filter=ACM")
       :out
       str/split-lines
       (filter seq)
       seq))

(def extensions #{"clj" "cljx" "cljc" "cljs" "edn"})
(defn clj?
  [s]
  (when s
    (let [extension (last (str/split s #"\."))]
      (extensions extension))))

(def lein-clfmt-warning
  (str/join "\n" [""
                  "cljfmt binary not found, defaulting to lein plugin"
                  "For faster pre-commit formatting download the cljfmt standalone here:"
                  "https://github.com/weavejester/cljfmt?tab=readme-ov-file#standalone"
                  ""]))

(defn hook-text
  [hook]
  (format "#!/bin/sh
# Installed by babashka task on %s

bb hooks %s" (java.util.Date.) hook))

(defn spit-hook
  [hook]
  (println "Installing hook: " hook)
  (let [file (str ".git/hooks/" hook)]
    (spit file (hook-text hook))
    (fs/set-posix-file-permissions file "rwx------")
    (assert (fs/executable? file))))

(defmulti hooks (fn [& args] (first args)))

(defmethod hooks "install" [& _]
  (spit-hook "pre-commit"))

(defmethod hooks "pre-commit" [& _]
  (println "Running pre-commit hook")
  (when-let [files (filter clj? (changed-files))]
    (if (-> (sh "which" "cljfmt") :exit #{0})
      (apply sh "cljfmt" "fix" files)
      (do (println lein-clfmt-warning)
          (apply sh "lein" "cljfmt" "fix" files)))))

(defmethod hooks :default [& args]
  (println "Unknown command:" (first args)))
