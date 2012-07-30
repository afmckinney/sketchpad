(ns sketchpad.repl.print
  (:require [clojure.string :as string]
            [sketchpad.buffer.action :as buffer.action]
            [sketchpad.rsyntaxtextarea :as rsta]
            [sketchpad.tab :as tab]
            [sketchpad.state :as state]))

(defn prompt
([]
	(prompt (:editor-repl @state/app) nil))
([rsta prompt-ns]
  (buffer.action/append-text rsta (str \newline  "sketchpad.user=> "))
  ; (buffer.action/append-text rsta (str \newline (ns-name prompt-ns) "=> "))
  (.setCaretPosition rsta (.getLastVisibleOffset rsta))))

(defn pln [rsta & values]
  (buffer.action/append-text rsta (str values \n)))

(defn append-command
"Takes a command string to append, and the offset to possibly go back with and appends it to the editor repl."
	([cmd-str caret-offset]
		(prompt)
		(buffer.action/append-text-update (:editor-repl @state/app) cmd-str)
		(buffer.action/buffer-move-pos-by-char (:editor-repl @state/app) caret-offset)))