(ns sketchpad.config
	(:use [clojure.pprint])
	(:require [seesaw.color :as c]
						[seesaw.keystroke :as key])
	(:import [org.fife.ui.rtextarea RTextArea]))


(load-file "config/default.clj")
  ;; set theme
  ; (let [doc-ta (app :doc-text-area)
  ;       repl-in-ta (app :repl-in-text-area)
  ;       repl-out-ta (app :repl-out-text-area)
  ;       theme (theme/theme (str "src/sketchpad/themes/" (config/prefs :theme)))]
  ;     (theme/apply! theme doc-ta)
  ;     (theme/apply! theme repl-in-ta)
  ;     (theme/apply! theme repl-out-ta))


(def editor-prefs-handlers
  ^{:doc "default handlers for config settings found in config/default.clj"}
	{
		:whitespace-visible 			(fn [rta pref] (.setWhitespaceVisible rta pref))
		; :clear-whitespace-lines 		(fn [rta pref] (.setClearWhitespaceEnabled rta pref))
		:line-wrap 					    (fn [rta pref] (.setLineWrap rta pref))
		; :highlight-current-line 		(fn [rta pref] (.setHighlightCurrentLine (cast RTextArea rta) pref))
		:rounded-selection-edges 		(fn [rta pref] (.setRoundedSelectionEdges rta pref))
;		:background-img 								"img/dir"
		:animate-bracket-matching 	    (fn [rta pref] (.setAnimateBracketMatching rta pref))
		:anti-aliasing 				    (fn [rta pref] (.setAntiAliasingEnabled rta pref))
		:code-folding 				    (fn [rta pref] (.setCodeFoldingEnabled rta pref))
		:auto-indent 				    (fn [rta pref] (.setAutoIndentEnabled rta pref))
		:clear-white-space-lines        (fn [rta pref] (.setClearWhitespaceLinesEnabled rta pref))
		:eol-marker 				    (fn [rta pref] (.setEOLMarkersVisible rta pref))
;		:font "Monaco"									;; override if you don't want default based on OS
		:tab-size 					    (fn [rta pref] (.setTabSize rta pref))
		:hyper-links-enabled			(fn [rta pref] (.setHyperlinksEnabled rta pref))
		:mark-occurences 			    (fn [rta pref] (.setMarkOccurrences rta pref))
		:mark-occurences-color 			(fn [rta pref] (.setMarkOccurrencesColor rta (c/color pref)))
		:paint-mark-occurences-border   (fn [rta pref] (.setPaintMarkOccurrencesBorder rta pref))
		:matched-bracket-bg-color 	    (fn [rta pref] (.setMatchedBracketBGColor rta (c/color pref)))
		:matched-bracket-border-color 	(fn [rta pref] (.setMatchedBracketBorderColor rta (c/color pref)))
		:tab-lines-enabled 				(fn [rta pref] (.setPaintTabLines rta pref))
		:tab-line-color 			    (fn [rta pref] (.setTabeLineColor rta (c/color pref)))
		; :templates-enabled 				(fn [rta pref] (.setTemplatesEnabled rta pref))
		:close-curly-braces 			(fn [rta pref] (.setCloseCurlyBraces rta pref))
	})

(def auto-completion-handlers
	{
  	:auto-complete                      (fn [ac pref] (.setAutoCompleteEnabled ac pref))
  	:auto-activation                    (fn [ac pref] (.setAutoActivationEnabled ac pref))
  	:auto-activation-delay              (fn [ac pref] (.setAutoActivationDelay ac pref))
  	:auto-complete-single-choice        (fn [ac pref] (.setAutoCompleteSingleChoices ac pref))
  	:show-description-window            (fn [ac pref] (.setShowDescWindow ac pref))
  	:description-window-size            (fn [ac pref] (.setDescriptionWindowSize ac (first pref) (second pref)))
	:choices-window-size                (fn [ac pref] (.setChoicesWindowSize ac (first pref) (second pref)))
	:parameter-assistance               (fn [ac pref] (.setParameterAssistanceEnabled ac pref))
  	:trigger-key                        (fn [ac pref] (.setTriggerKey ac (key/keystroke pref)))
  	})



(defn apply-editor-prefs! [prefs rta]
	(println "Apply default-editor-prefs")
	(doseq [[k pref] default-editor-prefs]
		(println k pref)
		((k editor-prefs-handlers) rta pref)))

(defn apply-auto-completion-prefs! [prefs ac]
	(println "Apply default-auto-completion-prefs")
	(doseq [[k pref] default-auto-completion-prefs]
		(println k pref)
		((k auto-completion-handlers) ac pref)))








