(ns buildmattrade.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [buildmattrade.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "USA" (:jurisdiction (store/building-order s "bo-1"))))
      (is (= "Akita Building Materials Trading Co" (:counterparty (store/building-order s "bo-1"))))
      (is (= :lumber (:product-category (store/building-order s "bo-1"))))
      (is (false? (:potable-water-contact? (store/building-order s "bo-1"))) "bo-1 is an ordinary construction material")
      (is (= "ATL" (:jurisdiction (store/building-order s "bo-2"))))
      (is (false? (:credit-cleared? (store/building-order s "bo-3"))) "bo-3 credit not cleared")
      (is (nil? (:contract-terms (store/building-order s "bo-4"))) "bo-4 no contract-terms")
      (is (false? (:sanctions-screened? (store/building-order s "bo-5"))) "bo-5 sanctions not screened")
      (is (true? (:potable-water-contact? (store/building-order s "bo-6"))) "bo-6 is a potable-water-contact product")
      (is (true? (:lead-free-certificate-on-file? (store/building-order s "bo-6"))) "bo-6 has a lead-free certificate on file")
      (is (true? (:potable-water-contact? (store/building-order s "bo-7"))) "bo-7 is a potable-water-contact product")
      (is (false? (:lead-free-certificate-on-file? (store/building-order s "bo-7"))) "bo-7 has no lead-free certificate on file")
      (is (true? (:lead-content-tested? (store/building-order s "bo-7"))) "bo-7 was lab-tested but has no certificate filed")
      (is (= :heating-equipment (:product-category (store/building-order s "bo-8"))))
      (is (false? (:potable-water-contact? (store/building-order s "bo-8"))) "bo-8 is heating equipment, not a potable-water-contact product")
      (is (false? (:dispatched? (store/building-order s "bo-1"))))
      (is (false? (:invoiced? (store/building-order s "bo-1"))))
      (is (= ["bo-1" "bo-2" "bo-3" "bo-4" "bo-5" "bo-6" "bo-7" "bo-8"]
             (mapv :id (store/all-building-orders s))))
      (is (nil? (store/assessment-of s "bo-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-dispatch-sequence s "USA")))
      (is (zero? (store/next-invoice-sequence s "USA")))
      (is (false? (store/building-order-already-dispatched? s "bo-1")))
      (is (false? (store/building-order-already-invoiced? s "bo-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "bo-1" :counterparty "Akita Building Materials Trading Co"}})
        (is (= "Akita Building Materials Trading Co" (:counterparty (store/building-order s "bo-1"))))
        (is (= "USA" (:jurisdiction (store/building-order s "bo-1"))) "unrelated field preserved"))
      (testing "certification-assessment payloads commit and read back"
        (store/commit-record! s {:effect :certification-assessment/set :path ["bo-1"]
                                 :payload {:jurisdiction "USA" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "USA" :checklist ["a" "b"]} (store/assessment-of s "bo-1"))))
      (testing "building-materials dispatch drafts a record and advances the dispatch sequence"
        (store/commit-record! s {:effect :order/mark-dispatched :path ["bo-1"]})
        (is (= "USA-DISPATCH-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "building-materials-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:dispatched? (store/building-order s "bo-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "USA")))
        (is (true? (store/building-order-already-dispatched? s "bo-1"))))
      (testing "invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :order/mark-invoiced :path ["bo-1"]})
        (is (= "USA-INVOICE-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "building-materials-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/building-order s "bo-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "USA")))
        (is (true? (store/building-order-already-invoiced? s "bo-1"))))
      (testing "lead-free-certification patches merge like any other upsert field"
        (store/commit-record! s {:effect :order/upsert :value {:id "bo-7" :lead-free-certificate-on-file? true}})
        (is (true? (:lead-free-certificate-on-file? (store/building-order s "bo-7"))))
        (is (= "Granger Pipe & Fitting KK" (:counterparty (store/building-order s "bo-7"))) "unrelated field preserved"))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/building-order s "nope")))
    (is (= [] (store/all-building-orders s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-dispatch-sequence s "USA")))
    (is (zero? (store/next-invoice-sequence s "USA")))
    (store/with-building-orders s {"x" {:id "x" :order-id "BO-X" :product-category :lumber
                                        :sku "BM-SKU-X"
                                        :counterparty "c" :price 6800.00
                                        :contract-terms "FOB wholesale yard, net 30 days"
                                        :credit-cleared? true :sanctions-screened? true
                                        :potable-water-contact? false
                                        :lead-content-tested? false
                                        :lead-free-certificate-on-file? false
                                        :dispatched? false :invoiced? false
                                        :jurisdiction "USA" :status :intake
                                        :dispatch-number nil :invoice-number nil}})
    (is (= "c" (:counterparty (store/building-order s "x"))))))
