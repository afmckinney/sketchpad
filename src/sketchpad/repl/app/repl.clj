(ns sketchpad.repl.app.repl
  (:import (java.io
             BufferedReader BufferedWriter
             InputStreamReader
             File PipedReader PipedWriter PrintWriter Writer
                    StringReader PushbackReader)
           (clojure.lang LineNumberingPushbackReader)
           (java.awt Rectangle)
           (java.net URL URLClassLoader URLDecoder))
  (:use [sketchpad.repl.app.util]
        [sketchpad.util.utils :only (attach-child-action-keys attach-action-keys
                            gen-map get-temp-file awt-event get-file-ns
                            when-lets get-text-str get-directories)]
        [clooj.brackets :only (find-line-group find-enclosing-brackets)]
        [clooj.help :only (get-var-maps)]
        [clj-inspector.jars :only (get-entries-in-jar jar-files)]
        [seesaw core color border meta]
        [sketchpad.util.tab]
        [clojure.tools.nrepl.server :only (start-server stop-server)])
  (:require [clojure.string :as string]
            [seesaw.rsyntax :as rsyntax]
            [clojure.java.io :as io]
            [sketchpad.config.config :as config]
            [sketchpad.buffer.action :as buffer.action]
            [sketchpad.editor.ui :as editor.ui]
            [sketchpad.wrapper.rtextscrollpane :as sp]
            [sketchpad.wrapper.rsyntaxtextarea :as wrapper.rsyntaxtextarea]
            [sketchpad.state.state :as state]
            [sketchpad.auto-complete.auto-complete :as auto-complete]
            [sketchpad.input.default :as input.default]
            [clojure.tools.nrepl :as repl]
            [sketchpad.repl.tab :as repl.tab]
            [sketchpad.repl.app.component :as repl.app.component]
            [leiningen.core.project :as lein])
  (:import 
           (java.io.IOException)))

(use 'clojure.java.javadoc)

(def repl-history (atom {}))  

(def repls (atom {}))

(def ^:dynamic *printStackTrace-on-error* false)

(defn is-eof-ex? [throwable]
  (and (instance? clojure.lang.LispReader$ReaderException throwable)
       (or
         (.startsWith (.getMessage throwable) "java.lang.Exception: EOF while reading")
         (.startsWith (.getMessage throwable) "java.io.IOException: Write end dead"))))

(defn get-repl-ns [app]
  (let [repl-map @repls]
    (-> app :repl deref :project-path repl-map :ns)))

(defn setup-classpath [project-path]
  (when project-path
    (let [project-dir (File. project-path)]
      (when (and (.exists project-dir) (.isDirectory project-dir))
        (let [sub-dirs (get-directories project-dir)]
          (concat sub-dirs
                  (filter #(.endsWith (.getName %) ".jar")
                          (mapcat #(.listFiles %) (file-seq project-dir)))))))))

(defn selfish-class-loader [url-array parent]
  (proxy [URLClassLoader] [url-array nil]
    (findClass [classname]
      (try (proxy-super findClass classname)
           (catch ClassNotFoundException e
                  (.findClass parent classname))))))

(defn create-class-loader [project-path parent]
  (when project-path
    (let [files (setup-classpath project-path)
          urls (map #(.toURL %) files)]
      (println " Classpath:")
      (dorun (map #(println " " (.getAbsolutePath %)) files))
      (URLClassLoader.
        (into-array URL urls) parent
        ))))
    
(defn find-clojure-jar [class-loader]
  (when-let [url (.findResource class-loader "clojure/lang/RT.class")]
    (-> url .getFile URL. .getFile URLDecoder/decode (.split "!/") first)))

(defn clojure-jar-location
  "Find the location of a clojure jar in a project."
  [^String project-path]
  (let [lib-dir (str project-path "/lib")
        jars (filter #(.contains (.getName %) "clojure")
                     (jar-files lib-dir))]
    (first
      (remove nil?
              (for [jar jars]
                (when-not
                  (empty?
                    (filter #(= "clojure/lang/RT.class" %)
                            (map #(.getName %) (get-entries-in-jar jar))))
                  jar))))))
                       
        
(defn outside-repl-classpath [project-path]
  (let [clojure-jar-term (when-not (clojure-jar-location project-path)
                           (find-clojure-jar (.getClassLoader clojure.lang.RT)))]
    (filter identity [(str project-path "/src")
                      (when clojure-jar-term
                        clojure-jar-term)])))

(defn create-outside-lein-repl
  "This function creates an outside process with a clojure repl."
  [result-writer project-path]
  (let [clojure-jar (clojure-jar-location project-path)
        java (str (System/getProperty "java.home")
                  File/separator "bin" File/separator "java")
        classpath (str clojure-jar ":" project-path)
        builder (ProcessBuilder.
                  [java "-cp" classpath "lein.repl"])]
    (.redirectErrorStream builder true)
    (.directory builder (File. (or project-path ".")))
    (try
      (let [proc (.start builder)
            input-writer  (-> proc .getOutputStream (PrintWriter. true))
            repl {:input-writer input-writer
                  :project-path project-path
                  :thread nil
                  :proc proc
                  :var-maps (agent nil)
                  :last-start-line 1}
            is (.getInputStream proc)]
        (send-off (repl :var-maps) #(merge % (get-var-maps project-path classpath)))
        (future (io/copy is result-writer :buffer-size 1))
        repl)
      (catch java.io.IOException e
        (println "Could not create outside REPL for path: " project-path)
        (println e)))))

(defn create-outside-repl
  "This function creates an outside process with a clojure repl."
  [result-writer project-path]
  (let [clojure-jar (clojure-jar-location project-path)
        java (str (System/getProperty "java.home")
                  File/separator "bin" File/separator "java")
        pomegranate-path (str "~/.m2/repository/com/cemerick/pomegranate/pomegranate-0.0.13.jar")
        classpath (str clojure-jar ":" project-path "/src" ":" pomegranate-path)
        builder (ProcessBuilder.
                  [java "-cp" classpath "clojure.main"])]
    (.redirectErrorStream builder true)
    (.directory builder (File. (or project-path ".")))
    (try
      (let [proc (.start builder)
            input-writer  (-> proc .getOutputStream (PrintWriter. true))
            repl {:input-writer input-writer
                  :project-path project-path
                  :thread nil
                  :proc proc
                  :var-maps (agent nil)
                  :last-start-line 1}
            is (.getInputStream proc)]
        (send-off (repl :var-maps) #(merge % (get-var-maps project-path classpath)))
        (future (io/copy is result-writer :buffer-size 1))
        (swap! repls assoc project-path repl)
        repl)
      (catch java.io.IOException e
        (println "Could not create outside REPL for path: " project-path)))))

; (defn replace-first [coll x]
;   (cons x (next coll)))

(defn update-repl-history [app]
  (swap! (:items repl-history) replace-first
         (get-text-str (app :application-repl))))

; (defn send-to-repl
;   ([app cmd] (send-to-repl app cmd "NO_SOURCE_PATH" 0) :repl)
;   ([app cmd file line] (send-to-repl app cmd file line :file))
;   ([app cmd file line src-key]
;   (awt-event
;     (let [cmd-ln (str \newline (.trim cmd) \newline)
;           cmd-trim (.trim cmd)]
;       (cond
;           (= src-key :repl)
;             (buffer.action/append-text (app :doc-text-area) (str \newline))
;           (= src-key :file)
;             (buffer.action/append-text (app :doc-text-area) (str \newline)))
;       (let [cmd-str (cmd-attach-file-and-line cmd file line)]
;         (binding [*out* (:input-writer @(app :repl))]
;           (println cmd-str)
;           (flush)))
;       (when (not= cmd-trim (second @(:items repl-history)))
;         (swap! (:items repl-history)
;                replace-first cmd-trim)
;         (swap! (:items repl-history) conj ""))
;       (reset! (:pos repl-history) 0)))))

        
(defn scroll-to-last [text-area]
  (.scrollRectToVisible text-area
                        (Rectangle. 0 (.getHeight text-area) 1 1)))

(defn relative-file [app]
  (let [prefix (str (-> app :repl deref :project-path) File/separator
                    "src"  File/separator)]
    (when-lets [f @(app :current-file)
                path (.getAbsolutePath f)]
      (subs path (count prefix)))))

(defn selected-region [ta]
  (if-let [text (.getSelectedText ta)]
    {:text text
     :start (.getSelectionStart ta)
     :end   (.getSelectionEnd ta)}
    (let [[a b] (find-line-group ta)]
      (when (and a b (< a b))
        {:text (.. ta getDocument (getText a (- b a)))
         :start a
         :end b}))))

; (defn send-selected-to-repl [app]
;   (let [ta (app :doc-text-area)
;         region (selected-region ta)
;         txt (:text region)]
;     (if-not (and txt (correct-expression? txt))
;         (.setText (app :arglist-label) "Malformed expression")
;          (let [line (.getLineOfOffset ta (:start region))]
;            (send-to-repl app txt (relative-file app) line)))))

; (defn send-doc-to-repl [app]
;   (let [text (->> app :doc-text-area .getText)]
;     (send-to-repl app text (relative-file app) 0)))

(defn update-repl-text [app]
  (let [rsta (:application-repl app)
        last-pos @(:last-end-pos repl-history)
        items @(:items repl-history)]
    (when (pos? (count items))
      (if (> (- (.getLastVisibleOffset rsta) last-pos) 0)
        (do 
           (.insert rsta 
                (nth items @(:pos repl-history))
                (- last-pos (count (nth items (- @(:pos repl-history) 1)))))
        )
        (do 
           (println "remove last string from: " last-pos " of length: " (- (.getLastVisibleOffset rsta) last-pos))
          (.insert rsta 
            (nth items @(:pos repl-history))
            last-pos))))))

; (defn show-previous-repl-entry [app]
;   (when (zero? @(:pos repl-history))
;     (update-repl-history app))
;   (swap! (:pos repl-history)
;          #(min (dec (count @(:items repl-history))) (inc %)))
;   (update-repl-text app))

; (defn show-next-repl-entry [app]
;   (when (pos? @(:pos repl-history))
;     (swap! (:pos repl-history)
;            #(Math/max 0 (dec %)))
;     (update-repl-text app)))

; (defn load-file-in-repl [app]
;   (when-lets [f0 @(:current-file app)
;               f (or (get-temp-file f0) f0)]
;     (send-to-repl app (str "(load-file \"" (.getAbsolutePath f) "\")"))))

; (defn apply-namespace-to-repl [app]
;   (when-let [current-ns (get-file-ns (config (app :doc-text-area) :text))]
;     (send-to-repl app (str "(ns " current-ns ")"))
;     (swap! repls assoc-in
;            [(-> app :repl deref :project-path) :ns]
;            current-ns)))

; (defn restart-repl [app project-path]
;   (buffer.action/append-text (app :application-repl)
;                (str "\n=== RESTARTING " project-path " REPL ===\n"))
;   (when-let [proc (-> app :repl deref :proc)]
;     (.destroy proc))
;   (reset! (:repl app) (create-outside-repl (app :repl-out-writer) project-path))
;   (apply-namespace-to-repl app))

(defn switch-repl [app project-path]
  (when (and project-path
             (not= project-path (-> app :repl deref :project-path)))
    (buffer.action/append-text (app :application-repl)
                 (str "\n\n=== Switching to " project-path " REPL ===\n"))
    (let [repl (or (get @repls project-path)
                   (create-outside-repl (app :repl-out-writer) project-path))]
      (reset! (:repl app) repl))))

(defn add-repl-input-handler [rsta]
  (let [ta-in rsta
        application-repl-history (get-meta rsta :repl-history)
        get-caret-pos #(.getCaretPosition ta-in)
        submit #(invoke-later
                  (when-let [txt (buffer.action/get-last-cmd rsta)]
                    (let [pos (application-repl-history :pos)]
                      (if (correct-expression? txt)
                        (do
                          (send-to-application-repl rsta txt)
                          (swap! pos (fn [p] 0)))))))
        at-top #(zero? (.getLineOfOffset ta-in (get-caret-pos)))
        at-bottom #(= (.getLineOfOffset ta-in (get-caret-pos))
                      (.getLineOfOffset ta-in (.. ta-in getText length)))
        prev-hist #(update-repl-history-display-position ta-in :dec)
        next-hist #(update-repl-history-display-position ta-in :inc)]
    (attach-action-keys ta-in ["ENTER" submit])
    (attach-action-keys ta-in ["control UP" prev-hist]
                              ["control DOWN" next-hist])))

 (defn attach-tab-change-handler [repl-tabbed-panel]
   (listen repl-tabbed-panel :selection 
        (fn [e] 
          (let [num-tabs (tab-count repl-tabbed-panel)]
           (if (> 0 num-tabs)
             (swap! state/app assoc :doc-title-atom (current-editor-buffer repl-tabbed-panel)))))))

(defn init-repl-tabbed-panel [repl-tabbed-panel repl]
  (let [text-area (get-in repl [:component :text-area])
        scroller (get-in repl [:component :scroller])
        container (get-in repl [:component :container])]
    (.setUI repl-tabbed-panel (editor.ui/sketchpad-tab-ui repl-tabbed-panel))
    (add-tab! repl-tabbed-panel "Sketchpad" container)
    (repl-tab-component! repl-tabbed-panel repl)
    (wrapper.rsyntaxtextarea/set-input-map! text-area (input.default/default-input-map))
    (add-repl-input-handler text-area)
    (auto-complete/install-auto-completion text-area)
    (config! scroller :background config/app-color)
    (config/apply-repl-prefs! text-area)
    (send-to-application-repl text-area "(require 'sketchpad.user)(in-ns 'sketchpad.user)")))

(defn repl-tabbed-panel
  []
  (let [repl-tabbed-panel   (tabbed-panel :placement :top
                                            :overflow :wrap
                                            :background (color :black)
                                            :border nil)
        application-repl (repl.app.component/application-repl-component)]
    (init-repl-tabbed-panel repl-tabbed-panel application-repl)
    (attach-tab-change-handler repl-tabbed-panel)
    (swap! state/app conj (gen-map
                            repl-tabbed-panel
                            application-repl))
    {:type :repl-tabbed-panel
     :component {:container repl-tabbed-panel}}))
