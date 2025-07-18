(ns metabase.lib.column-group-test
  (:require
   #?@(:cljs ([metabase.test-runner.assert-exprs.approximately-equal]))
   [clojure.test :refer [deftest is testing]]
   [metabase.lib.column-group :as lib.column-group]
   [metabase.lib.core :as lib]
   [metabase.lib.equality :as lib.equality]
   [metabase.lib.field.util :as lib.field.util]
   [metabase.lib.join :as lib.join]
   [metabase.lib.test-metadata :as meta]
   [metabase.lib.test-util :as lib.tu]
   [metabase.util.malli.registry :as mr]))

#?(:cljs (comment metabase.test-runner.assert-exprs.approximately-equal/keep-me))

(deftest ^:parallel basic-test
  (let [query   (lib.tu/venues-query)
        columns (lib/orderable-columns query)
        groups  (lib/group-columns columns)]
    (is (not (mr/explain [:sequential @#'lib.column-group/ColumnGroup] groups)))
    (is (=? [{::lib.column-group/group-type :group-type/main
              :lib/type                     :metadata/column-group
              ::lib.column-group/columns    [{:name "ID", :display-name "ID"}
                                             {:name "NAME", :display-name "Name"}
                                             {:name "CATEGORY_ID", :display-name "Category ID"}
                                             {:name "LATITUDE", :display-name "Latitude"}
                                             {:name "LONGITUDE", :display-name "Longitude"}
                                             {:name "PRICE", :display-name "Price"}]}
             {::lib.column-group/group-type :group-type/join.implicit
              :lib/type                     :metadata/column-group
              ::lib.column-group/columns    [{:name "ID", :display-name "ID"}
                                             {:name "NAME", :display-name "Name"}]}]
            groups))
    (testing `lib/display-info
      (is (=? [[{:is-from-join           false
                 :is-implicitly-joinable false
                 :name                   "VENUES"
                 :display-name           "Venues"}
                "Venues"]
               [{:is-from-join           false
                 :is-implicitly-joinable true
                 :name                   "CATEGORY_ID"
                 :display-name           "Category"}
                "Category"]]
              (for [group groups]
                [(lib/display-info query group)
                 (lib/display-name query group)]))))
    (testing `lib/columns-group-columns
      (is (= columns
             (mapcat lib/columns-group-columns groups))))))

(deftest ^:parallel aggregation-and-breakout-test
  (let [query   (-> (lib.tu/venues-query)
                    (lib/aggregate (lib/sum (meta/field-metadata :venues :id)))
                    (lib/breakout (meta/field-metadata :venues :name)))
        columns (lib/orderable-columns query)
        groups  (lib/group-columns columns)]
    (is (=? [{::lib.column-group/group-type :group-type/main
              ::lib.column-group/columns    [{:display-name "Name", :lib/source :source/table-defaults, :lib/breakout? true}
                                             {:display-name "Sum of ID", :lib/source :source/aggregations}]}]
            groups))
    (testing `lib/display-info
      (is (=? [{:is-from-join           false
                :is-implicitly-joinable false
                :name                   "VENUES"
                :display-name           "Venues"}]
              (for [group groups]
                (lib/display-info query group)))))
    (testing `lib/columns-group-columns
      (is (= columns
             (mapcat lib/columns-group-columns groups))))))

(deftest ^:parallel multi-stage-test
  (let [query   (-> (lib.tu/venues-query)
                    (lib/aggregate (lib/sum (meta/field-metadata :venues :id)))
                    (lib/breakout (meta/field-metadata :venues :name))
                    (lib/append-stage))
        columns (lib/orderable-columns query)
        groups  (lib/group-columns columns)]
    (is (=? [{::lib.column-group/group-type :group-type/main
              ::lib.column-group/columns    [{:display-name "Name", :lib/source :source/previous-stage}
                                             {:display-name "Sum of ID", :lib/source :source/previous-stage}]}]
            groups))
    (testing `lib/display-info
      (is (=? [{:display-name           "Summaries"
                :is-main-group          true
                :is-from-join           false
                :is-implicitly-joinable false}]
              (for [group groups]
                (lib/display-info query group)))))
    (testing `lib/columns-group-columns
      (is (= columns
             (mapcat lib/columns-group-columns groups))))))

(deftest ^:parallel source-card-test
  (let [query   (lib.tu/query-with-source-card)
        columns (lib/orderable-columns query)
        groups  (lib/group-columns columns)]
    (is (=? [{::lib.column-group/group-type :group-type/main
              ::lib.column-group/columns    [{:display-name "User ID", :lib/source :source/card}
                                             {:display-name "Count", :lib/source :source/card}]}
             {::lib.column-group/group-type :group-type/join.implicit
              :fk-field-id                  (meta/id :checkins :user-id)
              :fk-join-alias                nil
              ::lib.column-group/columns    [{:display-name "ID", :lib/source :source/implicitly-joinable}
                                             {:display-name "Name", :lib/source :source/implicitly-joinable}
                                             {:display-name "Last Login", :lib/source :source/implicitly-joinable}]}]
            groups))
    (testing `lib/display-info
      (is (=? [{:name                   "My Card"
                :display-name           "My Card"
                :is-from-join           false
                :is-implicitly-joinable false}
               {:name                   "USER_ID"
                :display-name           "User"
                :long-display-name      "User ID"
                :semantic-type          :type/FK
                :effective-type         :type/Integer
                :is-aggregation         false
                :is-breakout            false
                :is-from-join           false
                :is-from-previous-stage false
                :is-implicitly-joinable true
                :is-calculated          false}]
              (for [group groups]
                (lib/display-info query group)))))
    (testing `lib/columns-group-columns
      (is (= columns
             (mapcat lib/columns-group-columns groups))))))

(deftest ^:parallel joins-test
  (let [query   (lib.tu/query-with-join)
        columns (lib/orderable-columns query)
        groups  (lib/group-columns columns)]
    (is (=? [{::lib.column-group/group-type :group-type/main
              ::lib.column-group/columns    [{:name "ID", :display-name "ID"}
                                             {:name "NAME", :display-name "Name"}
                                             {:name "CATEGORY_ID", :display-name "Category ID"}
                                             {:name "LATITUDE", :display-name "Latitude"}
                                             {:name "LONGITUDE", :display-name "Longitude"}
                                             {:name "PRICE", :display-name "Price"}]}
             {::lib.column-group/group-type :group-type/join.explicit
              :join-alias                   "Cat"
              ::lib.column-group/columns    [{:display-name "ID", :lib/source :source/joins}
                                             {:display-name "Name", :lib/source :source/joins}]}]
            groups))
    (testing `lib/display-info
      (is (=? [{:is-from-join           false
                :is-implicitly-joinable false
                :name                   "VENUES"
                :display-name           "Venues"}
               {:is-from-join           true
                :is-implicitly-joinable false
                :name                   "Cat"
                :display-name           "Categories"}]
              (for [group groups]
                (lib/display-info query group)))))
    (testing `lib/columns-group-columns
      (is (= columns
             (mapcat lib/columns-group-columns groups))))))

(deftest ^:parallel expressions-test
  (let [query   (lib.tu/query-with-expression)
        columns (lib/orderable-columns query)
        groups  (lib/group-columns columns)]
    (is (=? [{::lib.column-group/group-type :group-type/main
              ::lib.column-group/columns    [{:name "ID", :display-name "ID"}
                                             {:name "NAME", :display-name "Name"}
                                             {:name "CATEGORY_ID", :display-name "Category ID"}
                                             {:name "LATITUDE", :display-name "Latitude"}
                                             {:name "LONGITUDE", :display-name "Longitude"}
                                             {:name "PRICE", :display-name "Price"}
                                             {:display-name "expr", :lib/source :source/expressions}]}
             {::lib.column-group/group-type :group-type/join.implicit
              :lib/type                     :metadata/column-group
              ::lib.column-group/columns    [{:name "ID", :display-name "ID"}
                                             {:name "NAME", :display-name "Name"}]}]
            groups))
    (testing `lib/display-info
      (is (=? [{:is-from-join           false
                :is-implicitly-joinable false
                :name                   "VENUES"
                :display-name           "Venues"}
               {:is-from-join           false
                :is-implicitly-joinable true
                :name                   "CATEGORY_ID"
                :display-name           "Category"}]
              (for [group groups]
                (lib/display-info query group)))))
    (testing `lib/columns-group-columns
      (is (= columns
             (mapcat lib/columns-group-columns groups))))))

(deftest ^:parallel source-card-with-expressions-test
  (let [query   (-> (lib.tu/query-with-source-card)
                    (lib/expression "expr" (lib/absolute-datetime "2020" :month)))
        columns (lib/orderable-columns query)
        groups  (lib/group-columns columns)]
    (is (=? [{::lib.column-group/group-type :group-type/main
              ::lib.column-group/columns    [{:display-name "User ID", :lib/source :source/card}
                                             {:display-name "Count", :lib/source :source/card}
                                             {:display-name "expr", :lib/source :source/expressions}]}
             {::lib.column-group/group-type :group-type/join.implicit
              :fk-field-id                  (meta/id :checkins :user-id)
              :fk-join-alias                nil
              ::lib.column-group/columns    [{:display-name "ID", :lib/source :source/implicitly-joinable}
                                             {:display-name "Name", :lib/source :source/implicitly-joinable}
                                             {:display-name "Last Login", :lib/source :source/implicitly-joinable}]}]
            groups))
    (testing `lib/display-info
      (is (=? [{:name                   "My Card"
                :display-name           "My Card"
                :is-from-join           false
                :is-implicitly-joinable false}
               {:name                   "USER_ID"
                :display-name           "User"
                :long-display-name      "User ID"
                :semantic-type          :type/FK
                :effective-type         :type/Integer
                :is-aggregation         false
                :is-breakout            false
                :is-from-join           false
                :is-from-previous-stage false
                :is-implicitly-joinable true
                :is-calculated          false}]
              (for [group groups]
                (lib/display-info query group)))))
    (testing `lib/columns-group-columns
      (is (= columns
             (mapcat lib/columns-group-columns groups))))))

(deftest ^:parallel native-query-test
  (let [query  (lib.tu/native-query)
        groups (lib/group-columns (lib/orderable-columns query))]
    (is (=? [{::lib.column-group/group-type :group-type/main
              ::lib.column-group/columns    [{:display-name "another Field", :lib/source :source/native}
                                             {:display-name "sum of User ID", :lib/source :source/native}]}]
            groups))
    (testing `lib/display-info
      (is (=? [{:display-name           "Native query"
                :is-from-join           false
                :is-implicitly-joinable false}]
              (for [group groups]
                (lib/display-info query group)))))))

(deftest ^:parallel native-source-query-test
  (let [query  (-> (lib.tu/native-query)
                   lib/append-stage)
        groups (lib/group-columns (lib/orderable-columns query))]
    (is (=? [{::lib.column-group/group-type :group-type/main
              ::lib.column-group/columns    [{:display-name "another Field", :lib/source :source/previous-stage}
                                             {:display-name "sum of User ID", :lib/source :source/previous-stage}]}]
            groups))
    (testing `lib/display-info
      (is (=? [{:display-name           "Summaries"
                :is-main-group          true
                :is-from-join           false
                :is-implicitly-joinable false}]
              (for [group groups]
                (lib/display-info query group)))))))

(defn- rhs-columns [query join-or-joinable]
  (let [cols (lib/join-condition-rhs-columns query join-or-joinable nil nil)]
    (testing `lib/join-condition-rhs-columns
      (is (=? [{:name "ID"}
               {:name "NAME"}]
              cols)))
    cols))

(deftest ^:parallel join-condition-rhs-columns-group-columns-join-test
  (testing "#32509 with an existing join"
    (let [query  (lib.tu/query-with-join)
          [join] (lib/joins query)]
      (is (=? {:lib/type :mbql/join}
              join))
      (let [cols   (rhs-columns query join)
            groups (lib/group-columns cols)]
        (testing `lib/group-columns
          (is (=? [{:lib/type                     :metadata/column-group
                    :join-alias                   "Cat"
                    ::lib.column-group/group-type :group-type/join.explicit
                    ::lib.column-group/columns    [{:name                 "ID"
                                                    :table-id             (meta/id :categories)
                                                    ::lib.join/join-alias "Cat"}
                                                   {:name                 "NAME"
                                                    :table-id             (meta/id :categories)
                                                    ::lib.join/join-alias "Cat"}]}]
                  groups)))
        (testing `lib/display-info
          (is (=? [{:name         "Cat"
                    :display-name "Categories"
                    :is-from-join true}]
                  (for [group groups]
                    (lib/display-info query group)))))))))

(deftest ^:parallel join-condition-rhs-columns-group-columns-table-test
  (testing "#32509 when building a join against a Table"
    (let [cols   (rhs-columns (lib.tu/venues-query) (meta/table-metadata :categories))
          groups (lib/group-columns cols)]
      (testing `lib/group-columns
        (is (=? [{:lib/type                     :metadata/column-group
                  :table-id                     (meta/id :categories)
                  ::lib.column-group/group-type :group-type/join.explicit
                  ::lib.column-group/columns    [{:name "ID", :table-id (meta/id :categories)}
                                                 {:name "NAME", :table-id (meta/id :categories)}]}]
                groups)))
      (testing `lib/display-info
        (is (=? [{:name         "CATEGORIES"
                  :display-name "Categories"
                  :is-from-join true}]
                (for [group groups]
                  (lib/display-info (lib.tu/venues-query) group))))))))

(deftest ^:parallel join-condition-rhs-columns-group-columns-card-test
  (testing "#32509 when building a join against a Card"
    (doseq [{:keys [message card metadata-provider]}
            [{:message           "MBQL Card"
              :card              (:categories (lib.tu/mock-cards))
              :metadata-provider (lib.tu/metadata-provider-with-mock-cards)}
             {:message           "Native Card"
              :card              (:categories/native (lib.tu/mock-cards))
              :metadata-provider (lib.tu/metadata-provider-with-mock-cards)}]]
      (testing message
        (let [cols   (rhs-columns (lib.tu/venues-query) card)
              groups (lib/group-columns cols)]
          (testing `lib/group-columns
            (is (=? [{:lib/type                     :metadata/column-group
                      :card-id                      (:id card)
                      ::lib.column-group/group-type :group-type/join.explicit
                      ::lib.column-group/columns    [{:name "ID", :lib/card-id (:id card)}
                                                     {:name "NAME", :lib/card-id (:id card)}]}]
                    groups)))
          (testing `lib/display-info
            (testing "Card is not present in MetadataProvider"
              (is (=? [{:display-name (str "Question " (:id card))
                        :is-from-join true}]
                      (for [group groups]
                        (lib/display-info (lib.tu/venues-query) group)))))
            (testing "Card *is* present in MetadataProvider"
              (let [query  (assoc (lib.tu/venues-query) :lib/metadata metadata-provider)
                    groups (lib/group-columns (rhs-columns query card))]
                (is (=? [{:name         "Mock categories card"
                          :display-name "Mock Categories Card"
                          :is-from-join true}]
                        (for [group groups]
                          (lib/display-info query group))))))))))))

(deftest ^:parallel self-join-grouping-test
  (let [query        (-> (lib/query meta/metadata-provider (meta/table-metadata :orders))
                         (lib/with-fields (for [field [:id :tax]]
                                            (lib/ref (meta/field-metadata :orders field))))
                         (lib/join (-> (lib/join-clause (meta/table-metadata :orders)
                                                        [(lib/= (meta/field-metadata :orders :id)
                                                                (meta/field-metadata :orders :id))])
                                       (lib/with-join-fields (for [field [:id :tax]]
                                                               (lib/ref (meta/field-metadata :orders field)))))))
        ;; [[lib/visible-columns]] no longer returns desired column alias (since it's a function of which columns get
        ;; returned), however I don't feel like completely reworking this test so I'm just going to add them here.
        columns      (into []
                           (lib.field.util/add-source-and-desired-aliases-xform query)
                           (lib/visible-columns query))
        marked       (lib.equality/mark-selected-columns query -1 columns (lib/returned-columns query))
        user-cols    ["ID" "ADDRESS" "EMAIL" "PASSWORD" "NAME" "CITY" "LONGITUDE"
                      "STATE" "SOURCE" "BIRTH_DATE" "ZIP" "LATITUDE" "CREATED_AT"]
        product-cols ["ID" "EAN" "TITLE" "CATEGORY" "VENDOR" "PRICE" "RATING" "CREATED_AT"]
        implicit     (fn [field-names alias-prefix]
                       {::lib.column-group/group-type :group-type/join.implicit
                        :lib/type :metadata/column-group
                        ::lib.column-group/columns
                        (for [field-name field-names]
                          {:name                     field-name
                           :lib/desired-column-alias (str alias-prefix field-name)
                           :lib/source               :source/implicitly-joinable})})]
    (is (= 60 (count columns)))
    (is (= 4  (count (lib/returned-columns query))))
    (is (= 60 (count marked)))
    (is (= 4  (count (filter :selected? marked))))
    (is (=? [{::lib.column-group/group-type :group-type/main
              :lib/type :metadata/column-group
              ::lib.column-group/columns
              (for [field-name ["ID" "USER_ID" "PRODUCT_ID" "SUBTOTAL" "TAX"
                                "TOTAL" "DISCOUNT" "CREATED_AT" "QUANTITY"]]
                {:name field-name
                 :lib/desired-column-alias field-name
                 :lib/source :source/table-defaults})}
             {::lib.column-group/group-type :group-type/join.explicit
              :lib/type :metadata/column-group
              ::lib.column-group/columns
              (for [field-name ["ID" "USER_ID" "PRODUCT_ID" "SUBTOTAL" "TAX"
                                "TOTAL" "DISCOUNT" "CREATED_AT" "QUANTITY"]]
                {:name field-name
                 :lib/desired-column-alias (str "Orders__" field-name)
                 :lib/source :source/joins})}
             (implicit product-cols "PRODUCTS__via__PRODUCT_ID__")
             (implicit user-cols    "PEOPLE__via__USER_ID__")
             (implicit product-cols "PRODUCTS__via__PRODUCT_ID__via__Orders__")
             (implicit user-cols    "PEOPLE__via__USER_ID__via__Orders__")]
            (lib/group-columns marked)))))

(deftest ^:parallel self-joined-cards-duplicate-implicit-columns-test
  (testing "Duplicate columns from different foreign key paths should get grouped separately (#34742)"
    (let [card (:orders (lib.tu/mock-cards))
          query (lib/query (lib.tu/metadata-provider-with-mock-cards) card)
          clause (lib/join-clause card [(lib/= (first (lib/join-condition-lhs-columns query card nil nil))
                                               (first (lib/join-condition-rhs-columns query card nil nil)))])

          query (lib/join query clause)
          cols (into []
                     (lib.field.util/add-source-and-desired-aliases-xform query)
                     (lib/visible-columns query))
          [_ _ _ product-1 _ product-2 :as groups] (lib/group-columns cols)]
      (is (=? [{:display-name "Mock Orders Card"
                :is-from-join false,
                :is-implicitly-joinable false}
               ;; Not sure why this is inconsistent
               {:display-name "Mock orders card"
                :is-from-join true,
                :is-implicitly-joinable false}
               {:display-name "Product"
                :is-from-join false,
                :is-implicitly-joinable true}
               {:display-name "User"
                :is-from-join false,
                :is-implicitly-joinable true}
               ;; we always use LONG display names when the column comes from a previous stage.
               {:display-name "Mock orders card → Product"
                :is-from-join false,
                :is-implicitly-joinable true}
               {:display-name "Mock orders card → User"
                :is-from-join false,
                :is-implicitly-joinable true}]
              (map #(lib/display-info query %) groups)))
      (is (=? [{:lib/desired-column-alias "PEOPLE__via__USER_ID__ID"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__ADDRESS"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__EMAIL"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__PASSWORD"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__NAME"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__CITY"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__LONGITUDE"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__STATE"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__SOURCE"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__BIRTH_DATE"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__ZIP"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__LATITUDE"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__CREATED_AT"}]
              (::lib.column-group/columns product-1)))
      (is (=? [{:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__ID", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__ADDRESS", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__EMAIL", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__PASSWORD", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__NAME", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__CITY", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__LONGITUDE", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__STATE", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__SOURCE", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__BIRTH_DATE", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__ZIP", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__LATITUDE", :fk-join-alias "Mock orders card"}
               {:lib/desired-column-alias "PEOPLE__via__USER_ID__via__Mock orders card__CREATED_AT", :fk-join-alias "Mock orders card"}]
              (::lib.column-group/columns product-2))))))

(deftest ^:parallel column-group-order-test
  (testing "column groups should be returned in order: main, explicit joins, implicit joins"
    (let [query (-> (lib/query meta/metadata-provider (meta/table-metadata :orders))
                    (lib/join (lib/join-clause (meta/table-metadata :orders)
                                               [(lib/= (meta/field-metadata :orders :id)
                                                       (-> (meta/field-metadata :orders :id)
                                                           (lib/with-join-alias "Orders")))]))
                    (lib/join (lib/join-clause (meta/table-metadata :reviews)
                                               [(lib/= (meta/field-metadata :reviews :product-id)
                                                       (meta/field-metadata :orders :product-id))])))
          ;; Deliberately randomizing the order of the columns.
          cols  (shuffle (lib/visible-columns query))]
      (is (=? [{::lib.column-group/group-type :group-type/main}
               ;; Explicit joins, ordered by join alias.
               {::lib.column-group/group-type :group-type/join.explicit
                :join-alias                   "Orders"}
               {::lib.column-group/group-type :group-type/join.explicit
                :join-alias                   "Reviews - Product"}
               ;; Implicit joins, ordered by the join alias and then the FK column name:
               ;; - "PRODUCT_ID"
               ;; - "USER_ID"
               ;; - Orders.PRODUCT_ID
               ;; - Orders.USER_ID
               ;; - "Reviews - Product.PRODUCT_ID"
               {::lib.column-group/group-type :group-type/join.implicit
                :fk-join-alias                nil
                :fk-field-id                  (meta/id :orders :product-id)}
               {::lib.column-group/group-type :group-type/join.implicit
                :fk-join-alias                nil
                :fk-field-id                  (meta/id :orders :user-id)}
               {::lib.column-group/group-type :group-type/join.implicit
                :fk-join-alias                "Orders"
                :fk-field-id                  (meta/id :orders :product-id)}
               {::lib.column-group/group-type :group-type/join.implicit
                :fk-join-alias                "Orders"
                :fk-field-id                  (meta/id :orders :user-id)}
               {::lib.column-group/group-type :group-type/join.implicit
                :fk-join-alias                "Reviews - Product"
                :fk-field-id                  (meta/id :reviews :product-id)}]
              (lib/group-columns cols))))))
