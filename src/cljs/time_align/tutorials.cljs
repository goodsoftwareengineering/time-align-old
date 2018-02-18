(ns time-align.tutorials
  ;; (:require )
  )

(def hello-lesson
  {:id :hello-lesson
   :description "Time Align is for planning out how to spend time
                 and then recording how you actually spend time."
   :position :unattached})

(def home-page-intro-lesson
  {:id :home-page-intro-lesson
   :description "This is the home page and displays a day. We will come back to this. First click the ☰ (menu) button."
   :continue {:event :click
              :selector [:div#menu-button]}})

(def menu-lesson
  {:id :menu-lesson
   :description "Let's go to the list page!"
   :attach [:a#list-menu-item]
   :position :bottom
   :continue {:event :click
              :selector [:a#list-menu-item]}})

(def structure-lesson
  {:id :structure-lesson
   :description "Organization is important. There are three heiarchical leves: Categories > Tasks > Periods."})

(def make-a-category
  {:id :make-a-category
   :description "Make your first category."
   :attach [:a#add-category-button]
   :position :bottom-right
   :continue {:event :click
              :selector [:a#add-category-button]}})

(def main-tutorial
  {:id :intro-tutorial
   :description "Align what you want with what you do."
   :precedence 1
   :lessons [hello-lesson
             home-page-intro-lesson
             menu-lesson
             structure-lesson
             make-a-category]})
