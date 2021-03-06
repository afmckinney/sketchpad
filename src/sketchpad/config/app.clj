(ns sketchpad.config.app
	(:use [seesaw.core :only [select]])
	(:require [sketchpad.state.state :as sketchpad.state]
						[sketchpad.util.tab :as tab]))

(defonce app sketchpad.state.state/app)

(defn buffer
"return the text from the current buffer component"
[]
(tab/current-buffer))

(defn buffer-text
"return the text from the current buffer text"
[]
	(try
		(.getText (tab/current-buffer (tab/current-tab (@app :buffer-tabbed-panel))))
		(catch java.lang.IllegalArgumentException e
			(println "no buffer open in editor"))))

(defn buffer-title
"return the title of the current buffer component"
[]
(tab/title-at (tab/current-tab-index)))

(defn repl 
"return the current repl component"
[]
	(tab/current-buffer (tab/current-tab (@app :repl-tabbed-panel))))

(defn repl-text 
"return the text from the current repl buffer text"
[]
	(try
		(.getText (tab/current-buffer (tab/current-tab (@app :repl-tabbed-panel))))
		(catch java.lang.IllegalArgumentException e
			(println "no buffer open in editor"))))

(defn focus-repl []
"Focus the REPL tabbed panel. This will focus the current REPL tab and ready it for text input."
(.requestFocusInWindow (:repl-tabbed-panel @app)))

(defn focus-editor []
"Focus the editor tabbed panel. This will focus the current editor tab and ready it for text input."
	(.requestFocusInWindow (:buffer-tabbed-panel @app)))

(defn focus-file-tree []
"Focus the file tree tabbed panel. This will focus the current file tree tab and ready it for text input."
	(.requestFocusInWindow (:docs-tree @app)))
