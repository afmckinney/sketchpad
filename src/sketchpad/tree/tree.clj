(ns sketchpad.tree.tree
  (:use [seesaw core keystroke border meta]
        [sketchpad utils prefs buffer-edit]
        [sketchpad.tree.utils]
        [clojure.pprint])
  (:require [seesaw.color :as c]
            [seesaw.chooser :as chooser]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [sketchpad.config :as config]
            [sketchpad.tab-builder :as tab-builder]
            [sketchpad.file :as file]
            [sketchpad.project.project :as project]
            [sketchpad.project.state :as project-state]
            [sketchpad.buffer-new :as buffer-new]
            [sketchpad.tree.popup :as popup]
            [sketchpad.lein-manager :as lein-manager]
            [leiningen.core.project :as lein-project])
  (:import 
         (java.io File StringReader BufferedWriter OutputStreamWriter FileOutputStream)
         (java.awt GridLayout)
         (javax.swing JButton JTree JOptionPane JWindow)
         (javax.swing.event TreeSelectionListener
                            TreeExpansionListener)
         (java.awt.event MouseAdapter MouseListener)
         (javax.swing.tree DefaultTreeCellRenderer DefaultMutableTreeNode DefaultTreeModel
                           TreePath TreeSelectionModel)
         (org.fife.ui.rsyntaxtextarea SyntaxConstants RSyntaxDocument)))

(defn popup-trigger?
[e]
(.isPopupTrigger e))

(defn double-click?
[e]
(= (.getClickCount e) 2))

(defn handle-filetree-double-click
[e app]
(let [tree (:docs-tree app)
      path (.getPathForLocation tree (.getX e) (.getY e))]
  (awt-event
    (save-tree-selection tree path))))

(defn handle-single-click [row path app-atom]
  (.setSelectionRow (@app-atom :docs-tree) row))

(defn handle-double-click [row path app-atom]
(try 
  (let [file (.. path getLastPathComponent getUserObject)
        proj (.getPathComponent path 1)
        proj-str (trim-parens (last (string/split (.toString proj) #"   ")))]
    (when (file/text-file? file) ;; handle if dir is selected instead of file
      (do 
        (buffer-new/buffer-from-file! (get-selected-file-path @app-atom) (project/get-project proj-str))
        (save-tree-selection tree path))))
  (catch java.lang.NullPointerException e)))

(defn handle-right-click [row path app]
(.setSelectionRow (app :docs-tree) row))

(defn tree-listener
[app-atom]
(let [app @app-atom
      tree (app :docs-tree)
      listener (proxy [MouseAdapter] []
                  (mousePressed [e]
                    (let [sel-row (.getRowForLocation tree (.getX e) (.getY e))
                          sel-path (.getPathForLocation tree (.getX e) (.getY e))
                          click-count (.getClickCount e)]
                      (cond
                        (= click-count 1)
                          (if (.isMetaDown e)
                            (handle-right-click sel-row sel-path app)
                            (handle-single-click sel-row sel-path app-atom))
                        (= click-count 2)
                          (handle-double-click sel-row sel-path app-atom))))
                  (mouseClicked [e]
                    (if (double-click? e)
                      (handle-filetree-double-click e app))))]
      listener))


(defn tree-model [app]
(let [model (DefaultTreeModel. nil)]
  model))

(defn setup-tree [app-atom]
(let [app @app-atom
      tree (:docs-tree app)
      save #(save-expanded-paths tree)]
  (config! tree :popup (popup/make-filetree-popup))
  (doto tree
    (.setRootVisible false)
    (.setShowsRootHandles true)
    (.. getSelectionModel (setSelectionMode TreeSelectionModel/CONTIGUOUS_TREE_SELECTION))
    (.addTreeExpansionListener
      (reify TreeExpansionListener
        (treeCollapsed [this e] (save))
        (treeExpanded [this e] (save))))

   (.addTreeSelectionListener
     (reify TreeSelectionListener
       (valueChanged [this e]
         (awt-event
           (save-tree-selection tree (.getNewLeadSelectionPath e))))))
  (.addMouseListener (tree-listener app-atom)))))

(defn file-tree
[app-atom]
(let [docs-tree             (tree   :model          (tree-model @app-atom)
                                    :id             :file-tree
                                    :class          :file-tree
                                    :background config/file-tree-bg)
      docs-tree-scroll-pane (scrollable             docs-tree
                                    :id             :file-tree-scrollable
                                    :class          :file-tree
                                    :background config/file-tree-bg)
      docs-tree-label       (horizontal-panel 
                                    :items          [(label :text "Projects"
                                                           :foreground config/file-tree-fg
                                                           :border (empty-border :thickness 5))]
                                    :id             :file-tree-label
                                    :class          :file-tree
                                    :background config/file-tree-bg)
      docs-tree-label-panel (horizontal-panel       
                                    :items          [docs-tree-label
                                                     :fill-h]
                                    :id             :docs-tree-label-panel
                                    :class          :file-tree
                                    :background config/file-tree-bg)
      docs-tree-panel (vertical-panel 
                                    :items          [docs-tree-label-panel
                                                    docs-tree-scroll-pane]
                                    :id             :file-tree-panel
                                    :class          :file-tree
                                    :background config/file-tree-bg)]
  (let [cell-renderer (cast DefaultTreeCellRenderer (.getCellRenderer docs-tree))]
    (.setBackgroundNonSelectionColor cell-renderer config/file-tree-bg))
  (project-state/add-projects-to-app app-atom)
  (swap! app-atom conj (gen-map
                          docs-tree
                          docs-tree-scroll-pane
                          docs-tree-label
                          docs-tree-panel))
  docs-tree-panel))