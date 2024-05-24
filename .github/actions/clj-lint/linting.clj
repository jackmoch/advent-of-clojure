#!/usr/bin/env bb

(ns linting
  (:require [babashka.process :refer [shell]]
            [org.httpkit.client :as hk-client]
            [babashka.fs :as fs]
            [cheshire.core :as cc]
            [clojure.edn :as edn]
            [clojure.pprint :as pp])
  (:import (java.util Date)))

(def check-runs-base-uri
  (str "https://api.github.com/repos/" (System/getenv "GITHUB_REPOSITORY") "/check-runs"))

(defn build-headers
      ([] (build-headers (System/getenv "INPUT_GITHUB_TOKEN")))
      ([bearer-token]
       {"Content-Type"  "application/json"
        "Accept"        "application/vnd.github.antiope-preview+json"
        "Authorization" (str "Bearer " bearer-token)
        "User-Agent"    "clojure-lint-action"}))

(defn create-check
      "Creates a new check run in Github using the provided env-vars"
      []
      (let [{:strs [GITHUB_SHA CHECK_NAME]} (System/getenv)
            {:keys [status] :as resp}
            @(hk-client/request
               {:url     check-runs-base-uri
                :method  :post
                :headers (build-headers)
                :body    (cc/generate-string
                           {:name       CHECK_NAME
                            :head_sha   GITHUB_SHA
                            :status     "in_progress"
                            :started_at (Date.)})})]
           (if-not (= status 201)
                   (do (println "Check run creation failed")
                       (pp/pprint resp)
                       (System/exit 1))
                   resp)))

(defn parse-check-id [{:keys [body]}]
      (-> body (cc/parse-string true) :id))

(defmulti build-update-check-request-body
          "A multimethod for building different types of update-check-run HTTP payloads based on the status of the check"
          :status)

(defmethod build-update-check-request-body :in_progress [{:keys [status output]}]
           (let [{:strs [GITHUB_SHA CHECK_NAME]} (System/getenv)]
                {"name"     CHECK_NAME
                 "head_sha" GITHUB_SHA
                 "status"   status
                 "output"   output}))

(defmethod build-update-check-request-body :completed [{:keys [status output conclusion]}]
           (let [{:strs [GITHUB_SHA CHECK_NAME]} (System/getenv)]
                (merge {"name"         CHECK_NAME
                        "head_sha"     GITHUB_SHA
                        "status"       status
                        "completed_at" (Date.)
                        "conclusion"   conclusion}
                       (when output
                             {"output" output}))))

(defn update-check [id opts]
      @(hk-client/request
         {:url     (str check-runs-base-uri "/" id)
          :method  :patch
          :headers (build-headers)
          :body    (cc/generate-string (build-update-check-request-body opts))}))

(defn directories->clj-or-edn-files
      "Takes a list of directories and filters for either clj or edn files"
      [dirs]
      (into [] (comp (mapcat #(file-seq (fs/file %)))
                     (filter #(-> % fs/directory? not))
                     (filter #(-> % fs/extension #{"clj" "edn"}))
                     (map str))
            dirs))

(defn format-linting-summary [{:keys [duration error warning info]}]
      (format "Linting took %sms, errors: %s, warnings: %s, infos: %s" duration error warning info))

(defn clj-kondo-findings->annotations
      "Converts a list of clj-kondo findings into annotations to be sent to the corresponding Github check"
      [findings]
      (let [level-lookup {"warning" "warning"
                          "info"    "notice"
                          "error"   "failure"}]
           (map (fn [{:keys        [filename level row message]
                      finding-type :type}]
                    {:path             filename
                     :start_line       row
                     :end_line         row
                     :annotation_level (level-lookup level)
                     :message          (str "[" finding-type "] " message)})
                findings)))

(def check-id (-> (create-check) parse-check-id))

(try
  (let [{:strs [LINTING_TARGETS CLJ_KONDO_CONFIG CHECK_NAME]} (System/getenv)
        files (-> LINTING_TARGETS edn/read-string directories->clj-or-edn-files)
        _ (println "Filtered linting targets:" files)
        clj-kondo-config (->> CLJ_KONDO_CONFIG edn/read-string (merge {:output {:format :json}}))
        _ (println "Linting config:" clj-kondo-config)
        {:keys [exit out]} (apply shell {:continue true :out :string} "clj-kondo"
                                  "--config" clj-kondo-config
                                  "--lint" files)
        {:keys [findings summary]} (cc/parse-string out true)
        formatted-summary (format-linting-summary summary)]
       (condp contains? exit
              #{2 3} (do (update-check
                           check-id
                           {:status :in_progress
                            :output {:title       CHECK_NAME
                                     :summary     formatted-summary
                                     :annotations (clj-kondo-findings->annotations findings)}})
                         (update-check
                           check-id
                           {:status     :completed
                            :conclusion :failure
                            :output     {:title   CHECK_NAME
                                         :summary formatted-summary}})
                         (System/exit exit))
              #{0} (do (update-check
                         check-id
                         {:status     :completed
                          :conclusion :success})
                       (System/exit exit))
              (throw (Exception. "Failed to run clj-kondo"))))
  (catch Exception e
    (pp/pprint e)
    (update-check
      check-id
      {:status     :completed
       :conclusion :failure})
    (System/exit 1)))