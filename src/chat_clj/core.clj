(ns chat-clj.core
  (:require [clojure.core.async          :as async :refer [>! <! >!! <!! go go-loop chan buffer close! put! thread alts! alts!! timeout]]
            [java-time                   :as t]
            [nikonyrh-utilities-clj.core :as u]
            [channels-clj.core           :as ch :refer :all]
            [taoensso.carmine            :as car]
            [seesaw.core                 :as s]
            [seesaw.invoke               :as s.invoke]
            [seesaw.dev                  :as s.dev])
  (:import org.pushingpixels.substance.api.SubstanceLookAndFeel)
  (:gen-class))

(def messages (atom []))

(defn collect-list-messages [redis-spec username sink]
  (go (let [names  (atom [])
            source (pipe-> (redis-source (str "chat-list-" username) redis-spec) (map-pipe :value) (window-pipe 1000) (atom-sink names))]
        (<! (timeout 500))
        (>! (:chan sink) (->> @names set sort (clojure.string/join ", ") (str (t/local-date) " " (t/local-time) " [system] Active users: ")))
        ((:close source))))
  nil)

(defn submit-list-message [redis-spec username other-username]
  (go (<! (timeout 200))
      (let [s (-> (str "chat-list-" other-username) (redis-sink redis-spec) :chan)]
        (>! s username)
        (close! s)))
  nil)


(defn make-window [redis-spec username exit?]
  (s/native!)
  (SubstanceLookAndFeel/setSkin "org.pushingpixels.substance.api.skin.GraphiteAquaSkin")
  (let [redis-spec (-> (zipmap [:host :port] (clojure.string/split redis-spec #":")) (update :port #(Integer. %)))
        msg-sink   (pipe-> (window-pipe 1000) (atom-sink messages))
        msg-fn     (fn [msg] (cond (-> msg :value (= "/list"))
                                   (if (-> msg :user (= username))
                                     (collect-list-messages redis-spec username msg-sink)
                                     (submit-list-message   redis-spec username (:user msg)))
                                   :else (apply format "%s [%s] %s" (map msg [:timestamp :user :value]))))
        
        msg-in     (pipe-> (timestamp-pipe) (map-pipe msg-fn) msg-sink)
        msg-source (pipe-> (redis-source "chat-messages" redis-spec) (map-pipe #(when (-> % :redis-source-id (not= redis-source-id)) (:value %))) msg-in)
        msg-out    (pipe-> (map-pipe #(hash-map :user username :value %)) (multicast-pipe (redis-sink "chat-messages" redis-spec) msg-in))
        
        font "MONOSPACED-PLAIN-14"
        f    (s/frame  :title (str "chat-clj [" username "]") :on-close :dispose)
        msgs (s/text   :font font :multi-line? true :editable? false)
        msg  (s/text   :font font)
        subm (s/button :font font :text ">>")
        
        subm-fn (fn [obj] (when-not (-> msg s/text empty?)
                            (->> msg s/text (>!! (:chan msg-out)))
                            (s/text! msg "")))]
    
    (add-watch messages :messages-watch
      (fn [_ _ _ value] (->> value (clojure.string/join "\n") (s/text! msgs))
                        (s/scroll! msgs :to :bottom)))
    
    (->> (java.awt.Dimension. 800 320) (.setPreferredSize f))
    (->> f s/pack! s/show!)
    (->> (s/top-bottom-split
           (s/scrollable msgs)
           (s/left-right-split msg subm :divider-location 0.90)
           :divider-location 0.85)
         (s/config! f :content))
    
    (s/listen subm :action (juxt subm-fn (fn [& args] (s/request-focus! msg))))
    (s/listen msg  :action subm-fn)
    (s/listen f    :window-closed (fn [& args] (close! (:chan msg-out)) (close! (:chan msg-in)) ((:close msg-source))
                                               (remove-watch messages :messages-watch) (when exit? (System/exit 0))))))

; (s/invoke-later (make-window "127.0.0.1:6379" "wrecked" false)))
; echo user1 user2 user3 | xargs -P0 -n1 java -jar target/chat-clj-0.0.1-SNAPSHOT-standalone.jar 127.0.0.1:6379
(defn -main [redis username & argv]
  (->> (make-window redis username true)
       s/invoke-later))

