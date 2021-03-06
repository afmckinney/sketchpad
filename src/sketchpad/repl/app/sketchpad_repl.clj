(ns sketchpad.repl.app.sketchpad-repl
	(:use [sketchpad.util.utils])
  (:require [clojure.main :as main]
  	[sketchpad.buffer.action :as buffer.action]
  	[seesaw.core :as seesaw])
  (:import [clojure.lang LineNumberingPushbackReader]))

(defn repl-writer
	[rsta]
	(let [writer (proxy [java.io.StringWriter] []
		(append
		([csq]
			(proxy-super append csq))
		([csq arg]
			(proxy-super append csq arg))
		([csq arg1 arg2]
			(proxy-super append csq arg1 arg2))
		)

		(close []
			(proxy-super close))

		(flush []
			(proxy-super flush))

		(getBuffer []
			(proxy-super getBuffer))

		(toString []
			(proxy-super toString))

		(write
		([c]
			(seesaw/invoke-later
				(buffer.action/append-text rsta (str c)))
			(proxy-super write c))
		([cbuf off len]
			(seesaw/invoke-later
				(buffer.action/append-text rsta (str cbuf)))
			(proxy-super write cbuf off len))))]
		writer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom SketchPad REPL
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sketchpad-repl
  "Generic, reusable, read-eval-print loop. By default, reads from *in*,
  writes to *out*, and prints exception summaries to *err*. If you use the
  default :read hook, *in* must either be an instance of
  LineNumberingPushbackReader or duplicate its behavior of both supporting
  .unread and collapsing CR, LF, and CRLF into a single \\newline. Options
  are sequential keyword-value pairs. Available options and their defaults:

     - :init, function of no arguments, initialization hook called with
       bindings for set!-able vars in place.
       default: #()

     - :need-prompt, function of no arguments, called before each
       read-eval-print except the first, the user will be prompted if it
       returns true.
       default: (if (instance? LineNumberingPushbackReader *in*)
                  #(.atLineStart *in*)
                  #(identity true))

     - :prompt, function of no arguments, prompts for more input.
       default: repl-prompt

     - :flush, function of no arguments, flushes output
       default: flush

     - :read, function of two arguments, reads from *in*:
         - returns its first argument to request a fresh prompt
           - depending on need-prompt, this may cause the repl to prompt
             before reading again
         - returns its second argument to request an exit from the repl
         - else returns the next object read from the input stream
       default: repl-read

     - :eval, funtion of one argument, returns the evaluation of its
       argument
       default: eval

     - :print, function of one argument, prints its argument to the output
       default: prn

     - :caught, function of one argument, a throwable, called when
       read, eval, or print throws an exception or error
       default: repl-caught"
  [rsta & options]
  (let [cl (.getContextClassLoader (Thread/currentThread))]
    (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))
	(let [err-writer (repl-writer rsta)
		  out-writer (repl-writer rsta)]
		(binding [*out* out-writer
				  *err* err-writer]
		  (let [{:keys [init need-prompt prompt flush read eval print caught]
		         :or {init        #()
		              need-prompt (if (instance? LineNumberingPushbackReader *in*)
		                            #(.atLineStart ^LineNumberingPushbackReader *in*)
		                            #(identity true))
		              prompt      main/repl-prompt
		              flush       flush
		              read        main/repl-read
		              eval        eval
		              print       prn
		              caught      main/repl-caught}}
		        (apply hash-map options)
		        request-prompt (Object.)
		        request-exit (Object.)
		        read-eval-print
		        (fn []
		          (try
		           (let [input (read request-prompt request-exit)]
		             (or (#{request-prompt request-exit} input)
		                 (let [value (eval input)]
		                   (print value)
		                   (set! *3 *2)
		                   (set! *2 *1)
		                   (set! *1 value))))
		           (catch Throwable e
		             (caught e)
		             (set! *e e))))]
		    (clojure.main/with-bindings
		     (try
		      (init)
		      (catch Throwable e
		        (caught e)
		        (set! *e e)))
		     (use '[clojure.repl :only (source apropos dir pst doc find-doc)])
		     (use '[clojure.java.javadoc :only (javadoc)])
		     (use '[clojure.pprint :only (pp pprint)])
		     (prompt)
		     (flush)
		     (loop []
		       (when-not
		       	 (try (= (read-eval-print) request-exit)
			  (catch Throwable e
			   (caught e)
			   (set! *e e)
			   nil))
		         (when (need-prompt)
		           (prompt)
		           (flush))
		         (recur))))))))
