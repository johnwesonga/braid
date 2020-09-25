(ns braid.core.server.migrate
  (:require
   [braid.core.server.db :as db]
   [braid.core.server.db.group :as group]
   [braid.core.server.db.user :as user]
   [braid.quests.server.db :as quests]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as string]
   [datomic.api :as d]))

(defn migrate-2017-05-24
  "add slug to groups"
  []
  @(d/transact db/conn
     [{:db/ident :group/slug
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db/unique :db.unique/value}
      ; retract :group/name uniqueness constraint
      [:db/retract :group/name :db/unique :db.unique/identity]
      [:db/add :db.part/db :db.alter/attribute :group/name]])

  (let [slugify (fn [text]
                  (-> text
                      string/trim
                      string/lower-case
                      (string/replace #"[ -+|,/?%#&\.\!:$'@]*" "")))
        groups (->> (d/q '[:find ?group ?group-name
                           :in $
                           :where
                           [?group :group/name ?group-name]]
                         (d/db db/conn)))
        txs (->> groups
                 (map (fn [[group group-name]]
                        [:db/add group
                         :group/slug (slugify group-name)])))]
      @(d/transact db/conn (doall txs))))

(defn migrate-2017-04-19
  "Add bot notify-all-messages boolean"
  []
  @(d/transact
     db/conn
     [{:db/ident :bot/notify-all-messages?
       :db/doc "Indicates that this bot should recieve all visible messages in its group"
       :db/valueType :db.type/boolean
       :db/cardinality :db.cardinality/one}]))

(defn migrate-2017-04-11
  "add bot change events"
  []
  @(d/transact
     db/conn
     [{:db/ident :bot/event-webhook-url
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}]))

(defn migrate-2016-08-18
  "schema change for quests"
  []
  @(d/transact db/conn
     [
      ; quest-records
      {:db/ident :quest-record/id
       :db/valueType :db.type/uuid
       :db/cardinality :db.cardinality/one
       :db/unique :db.unique/identit}
      {:db/ident :quest-record/quest-id
       :db/valueType :db.type/keyword
       :db/cardinality :db.cardinality/one}
      {:db/ident :quest-record/user
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one}
      {:db/ident :quest-record/state
       :db/valueType :db.type/keyword
       :db/cardinality :db.cardinality/one}
      {:db/ident :quest-record/progress
       :db/valueType :db.type/long
       :db/cardinality :db.cardinality/one}])

  (let [user-ids (->> (d/q '[:find ?user-id
                             :in $
                             :where
                             [?u :user/id ?user-id]]
                           (d/db db/conn))
                      (map first))]
    (db/run-txns!
      (mapcat (fn [user-id]
                (quests/activate-first-quests-txn [:user/id user-id]))
              user-ids))))

(defn migrate-2016-07-29
  "Add watched threads for bots"
  []
  (d/transact db/conn
    [{:db/ident :bot/watched
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many}]))

(defn migrate-2016-06-27
  "Add uploads schema"
  []
  (d/transact db/conn
    [{:db/ident :upload/id
      :db/valueType :db.type/uuid
      :db/cardinality :db.cardinality/one
      :db/unique :db.unique/identity}
     {:db/ident :upload/thread
      :db/doc "The thread this upload is associated with"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one}
     {:db/ident :upload/url
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}
     {:db/ident :upload/uploaded-at
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one}
     {:db/ident :upload/uploaded-by
      :db/doc "User that uploaded this file"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one}]))

(defn migrate-2016-06-08
  "Add bot schema"
  []
  (d/transact db/conn
    [{:db/ident :user/is-bot?
      :db/valueType :db.type/boolean
      :db/cardinality :db.cardinality/one}

     {:db/ident :bot/id
      :db/valueType :db.type/uuid
      :db/cardinality :db.cardinality/one
      :db/unique :db.unique/identity}
     {:db/ident :bot/token
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}
     {:db/ident :bot/name
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}
     {:db/ident :bot/avatar
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}
     {:db/ident :bot/webhook-url
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}
     {:db/ident :bot/group
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one}
     {:db/ident :bot/user
      :db/doc "Fake user bot posts under"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one}]))


(defn migrate-2016-06-04
  "Remove old-style extensions"
  []
  (->> (d/q '[:find [?e ...]
              :where
              [?e :extension/id]]
            (d/db db/conn))
       (mapv (fn [e] [:db.fn/retractEntity e]))
       (d/transact db/conn)
       deref))

(defn migrate-2016-05-13
  "Threads have associated groups"
  []
  @(d/transact db/conn
     [{:db/ident :thread/group
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one}])
  (let [threads (->> (d/q '[:find [?t ...]
                            :where
                            [?t :thread/id]]
                          (d/db db/conn))
                     (d/pull-many
                       (d/db db/conn)
                       [:thread/id
                        {:thread/mentioned [:user/id]}
                        {:thread/tag [:tag/group]}
                        {:message/_thread [:message/created-at
                                           {:message/user [:user/id]}]}]))
        tx
        (vec
          (for [th threads]
            (let [author (->> (th :message/_thread)
                              (sort-by :message/created-at)
                              first
                              :message/user)
                  author-grp (some->> author :user/id group/user-groups first :id)
                  fallback-group (:group/id (d/pull (d/db db/conn) [:group/id] [:group/name "Braid"]))]
              (cond
                (seq (th :thread/tag))
                (let [grp (get-in th [:thread/tag 0 :tag/group :db/id])]
                  (when (nil? grp)
                    (println "Nil by tag " (th :thread/id)))
                  [:db/add [:thread/id (th :thread/id)]
                   :thread/group grp])

                (seq (th :thread/mentioned))
                (let [grps (apply
                             set/intersection
                             (map (comp :id
                                        group/user-groups
                                        :user/id)
                                  (cons author (th :thread/mentioned))))
                      grp (or (first grps) author-grp fallback-group)]
                  (when (nil? grp)
                    (println "Nil by mentions " (th :thread/id)))
                  [:db/add [:thread/id (th :thread/id)]
                   :thread/group [:group/id grp]])

                :else
                (let [grp (or author-grp fallback-group)]
                  (when (nil? grp)
                    (println "nil by author" (th :thread/id)))
                  [:db/add [:thread/id (th :thread/id)]
                   :thread/group [:group/id grp]])))))]
    @(d/transact db/conn tx)))

(defn migrate-2016-05-07
  "Change how user preferences are stored"
  []
  ; rename old prefs
  @(d/transact db/conn [{:db/id :user/preferences
                         :db/ident :user/preferences-old}])
  ; create new entity type
  @(d/transact db/conn
     [{:db/ident :user/preferences
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many}

      {:db/ident :user.preference/key
       :db/valueType :db.type/keyword
       :db/cardinality :db.cardinality/one}
      {:db/ident :user.preference/value
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}])

  ; migrate to new style
  (let [prefs (d/q '[:find (pull ?u [:user/id :user/preferences-old])
                     :where [?u :user/id]]
                   (d/db db/conn))]
    (doseq [[p] prefs]
      (let [u-id (:user/id p)
            u-prefs (edn/read-string (:user/preferences-old p))]
        (doseq [[k v] u-prefs]
          (when k (db/run-txns! (user/user-set-preference-txn u-id k v))))))))

(defn migrate-2016-05-03
  "Add tag descriptions"
  []
  @(d/transact db/conn
     [{:db/ident :tag/description
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}]))

(defn migrate-2016-04-29
  "Add group admins"
  []
  @(d/transact db/conn
     [{:db/ident :group/admins
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many}]))

(defn migrate-2016-03-28
  "Add group settings"
  []
  @(d/transact db/conn
     [{:db/ident :group/settings
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}]))

(defn migrate-2016-03-21
  "Add user preferences"
  []
  @(d/transact db/conn
     [{:db/ident :user/preferences
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}]))

(defn migrate-2016-03-04
  "Add extension type as attribute"
  []
  @(d/transact db/conn
     [{:db/ident :extension/type
       :db/valueType :db.type/keyword
       :db/cardinality :db.cardinality/one}]))

(defn migrate-2016-03-02
  "Add extension user"
  []
  @(d/transact db/conn
     [{:db/ident :extension/user
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one}]))

(defn migrate-2016-02-26
  "Add extension schema"
  []
  @(d/transact db/conn
     [{:db/ident :extension/id
       :db/valueType :db.type/uuid
       :db/cardinality :db.cardinality/one
       :db/unique :db.unique/identity}
      {:db/ident :extension/group
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one}
      {:db/ident :extension/token
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}
      {:db/ident :extension/refresh-token
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}
      {:db/ident :extension/config
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}
      {:db/ident :extension/watched-threads
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many}]))


(defn migrate-2016-01-14
  "All users must have a nickname"
  []
  (let [give-nicks (->> (d/q '[:find (pull ?u [:user/id :user/email :user/nickname])
                               :where
                               [?u :user/id]]
                             (d/db db/conn))
                        (map first)
                        (filter (comp nil? :user/nickname))
                        (mapv (fn [u] [:db/add [:user/id (:user/id u)]
                                       :user/nickname (-> (:user/email u) (string/split #"@") first)])))]
    @(d/transact db/conn give-nicks)))

(defn migrate-2016-01-01
  "Change email uniqueness to /value, add thread mentions"
  []
  @(d/transact db/conn
     [{:db/id :user/email
       :db/unique :db.unique/value}
      {:db/ident :thread/mentioned
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many}]))

(defn migrate-2015-12-19
  "Add user nicknames"
  []
  @(d/transact db/conn
     [{:db/ident :user/nickname
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db/unique :db.unique/value}]))

(defn migrate-2015-12-12
  "Make content fulltext"
  []
  ; rename content
  @(d/transact db/conn [{:db/id :message/content :db/ident :message/content-old}])
  @(d/transact db/conn [{:db/ident :message/content
                         :db/valueType :db.type/string
                         :db/fulltext true
                         :db/cardinality :db.cardinality/one}])
  (let [messages (->> (d/q '[:find (pull ?e [:message/id
                                             :message/content-old
                                             :message/created-at
                                             {:message/user [:user/id]}
                                             {:message/thread [:thread/id]}])
                             :where [?e :message/id]]
                           (d/db db/conn))
                      (map first))]
    (let [msg-tx (->> messages
                      (map (fn [msg]
                             [:db/add [:message/id (msg :message/id)]
                              :message/content (msg :message/content-old)])))]
      @(d/transact db/conn (doall msg-tx)))))

(defn migrate-2015-07-29
  "Schema changes for groups"
  []
  @(d/transact db/conn
     [{:db/ident :tag/group
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one}

      ; groups
      {:db/ident :group/id
       :db/valueType :db.type/uuid
       :db/cardinality :db.cardinality/one
       :db/unique :db.unique/identity}
      {:db/ident :group/name
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db/unique :db.unique/identity}
      {:db/ident :group/user
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many}])
  (println "You'll now need to create a group and add existing users & tags to that group"))

(defn create-group-for-users-and-tags
  "Helper function for migrate-2015-07-29 - give a group name to create that
  group and add all existing users and tags to that group"
  [group-name]
  (let [[group] (db/run-txns! (group/create-group-txn {:id (db/uuid) :name group-name}))
        all-users (->> (d/q '[:find ?u :where [?u :user/id]] (d/db db/conn)) (map first))
        all-tags (->> (d/q '[:find ?t :where [?t :tag/id]] (d/db db/conn)) (map first))]
    @(d/transact db/conn (mapv (fn [u] [:db/add [:group/id (group :id)] :group/user u]) all-users))
    @(d/transact db/conn (mapv (fn [t] [:db/add t :tag/group [:group/id (group :id)]]) all-tags))))

(defn migrate-2015-08-26
  "schema change for invites"
  []
  @(d/transact db/conn
     [
      ; invitations
      {:db/ident :invite/id
       :db/valueType :db.type/uuid
       :db/cardinality :db.cardinality/one
       :db/unique :db.unique/identity}
      {:db/ident :invite/group
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one}
      {:db/ident :invite/from
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/one}
      {:db/ident :invite/to
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}
      {:db/ident :invite/created-at
       :db/valueType :db.type/instant
       :db/cardinality :db.cardinality/one}]))


