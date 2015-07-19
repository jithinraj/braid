(ns chat.test.server.db
  (:require [clojure.test :refer :all]
            [chat.server.db :as db]))

(use-fixtures :each
              (fn [t]
                (binding [db/*uri* "datomic:mem://chat-test"]
                  (db/init!)
                  (db/with-conn (t))
                  (datomic.api/delete-database db/*uri*))))

(deftest create-user
  (let [data {:id (db/uuid)
              :email "foo@bar.com"
              :password "foobar"
              :avatar "http://www.foobar.com/1.jpg"}
        user (db/create-user! data)]
    (testing "create returns a user"
      (is (= user (dissoc data :password))))))

(deftest fetch-users
  (let [user-1-data {:id (db/uuid)
                     :email "foo@bar.com"
                     :password "foobar"
                     :avatar "http://www.foobar.com/1.jpg"}
        user-1 (db/create-user! user-1-data)
        user-2-data  {:id (db/uuid)
                      :email "bar@baz.com"
                      :password "barbaz"
                      :avatar "http://www.barbaz.com/1.jpg"}
        user-2 (db/create-user! user-2-data)
        users (db/fetch-users)]
    (testing "returns all users"
      (is (= (set users) #{user-1 user-2})))))

(deftest authenticate-user
  (let [user-1-data {:id (db/uuid)
                     :email "foo@bar.com"
                     :password "foobar"
                     :avatar ""}
        _ (db/create-user! user-1-data)]

    (testing "returns user-id when email+password matches"
      (is (= (:id user-1-data) (db/authenticate-user (user-1-data :email) (user-1-data :password)))))

    (testing "returns nil when email+password wrong"
      (is (nil? (db/authenticate-user (user-1-data :email) "zzz"))))))

(deftest create-message-new
  (let [user-1 (db/create-user! {:id (db/uuid)
                                 :email "foo@bar.com"
                                 :password "foobar"
                                 :avatar "http://www.foobar.com/1.jpg"})
        thread-id (db/uuid)]

    (testing "create-message w/ new thread-id"
      (let [message-data {:id (db/uuid)
                          :user-id (user-1 :id)
                          :thread-id thread-id
                          :created-at (java.util.Date.)
                          :content "Hello?"}
            message (db/create-message! message-data)]
        (testing "returns message"
          (is (= message-data message)))
        (testing "user has thread open"
          (is (contains? (set (db/get-open-threads-for-user (user-1 :id))) thread-id)))
        (testing "user has thread subscribed"
          (is (contains? (set (db/get-subscribed-threads-for-user (user-1 :id))) thread-id)))))

    (testing "create-message w/ existing thread-id"
      (let [message-2-data {:id (db/uuid)
                            :user-id (user-1 :id)
                            :thread-id thread-id
                            :created-at (java.util.Date.)
                            :content "Goodbye."}
            message-2 (db/create-message! message-2-data)]
        (testing "returns message"
          (is (= message-2-data message-2)))
        (testing "user has thread open"
          (is (contains? (set (db/get-open-threads-for-user (user-1 :id))) thread-id)))
        (testing "user has thread subscribed"
          (is (contains? (set (db/get-subscribed-threads-for-user (user-1 :id))) thread-id)))))))


(deftest fetch-messages
  (let [user-1 (db/create-user! {:id (db/uuid)
                                 :email "foo@bar.com"
                                 :password "foobar"
                                 :avatar "http://www.foobar.com/1.jpg"})
        message-1-data {:id (db/uuid)
                        :user-id (user-1 :id)
                        :thread-id (db/uuid)
                        :created-at (java.util.Date.)
                        :content "Hello?"}
        message-1 (db/create-message! message-1-data)
        message-2-data {:id (db/uuid)
                        :user-id (user-1 :id)
                        :thread-id (message-1 :thread-id)
                        :created-at (java.util.Date.)
                        :content "Hello?"}
        message-2 (db/create-message! message-2-data)
        messages (db/fetch-messages)]
    (testing "create returns message"
      (is (= (set messages) #{message-1 message-2})))))

(deftest user-hide-thread
  (let [user-1 (db/create-user! {:id (db/uuid)
                                 :email "foo@bar.com"
                                 :password "foobar"
                                 :avatar ""})
        message-1 (db/create-message! {:id (db/uuid)
                                       :user-id (user-1 :id)
                                       :thread-id (db/uuid)
                                       :created-at (java.util.Date.)
                                       :content "Hello?"})
        message-2 (db/create-message! {:id (db/uuid)
                                       :user-id (user-1 :id)
                                       :thread-id (db/uuid)
                                       :created-at (java.util.Date.)
                                       :content "Hello?"})]
    (testing "thread 1 is open"
      (is (contains? (set (db/get-open-threads-for-user (user-1 :id))) (message-1 :thread-id))))
    (testing "thread 2 is open"
      (is (contains? (set (db/get-open-threads-for-user (user-1 :id))) (message-2 :thread-id))))
    (testing "user can hide thread"
      (db/user-hide-thread! (user-1 :id) (message-1 :thread-id))
      (is (not (contains? (set (db/get-open-threads-for-user (user-1 :id))) (message-1 :thread-id)))))))
