(ns ragtacts.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ragtacts.embedding.open-ai :refer [open-ai-embedding]]
            [ragtacts.llm.base :as llm]
            [ragtacts.llm.open-ai]
            [ragtacts.loader.doc :as doc]
            [ragtacts.loader.web :as web]
            [ragtacts.util :refer [f-string]]
            [ragtacts.prompt.langchain :as langchain]
            [ragtacts.vector-store.milvus :refer [milvus]]
            [ragtacts.splitter.recursive :refer [recursive-splitter]]
            [ragtacts.vector-store.base :as vector-store]
            [ragtacts.vector-store.in-memory :refer [in-memory-vector-store]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; llm

(def ask llm/ask)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; prompt 

(defn prompt
  "Returns a string that prompts the user to answer a question.

   Args:
   - template: Python `str.fomrat` string.
   - params: A map of parameters to pass to the prompt template.

   Returns:
   - String: The prompt to ask the user.

   Example:
   ```clojure
   (prompt \"Question: { question }\" {:question \"Hello!\"})
   ```
   "
  [template params]
  (f-string template params))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; vector Store 

(def save vector-store/save)

(def search vector-store/search)

(def embed vector-store/embed)

(defn vector-store
  "Return a vector store.
   
   Args:
   - embedding: A map with the following
     - `:type`: A keyword with the embedding type.
   - splitter: A map with the following
   - db: A map with the following
     - `:type`: A keyword with the db type.
   
   Example:
   ```clojure
   (vector-store)

   (vector-store {:embedding (open-ai-embedding)})
   
   (vector-store {:splitter (recursive-splitter {:size 500 :overlap 10})
                  :db (in-memory-vector-store)})
   
   (vector-store {:db (milvus {:collection \"animals\"})})
   ```"
  ([]
   (vector-store {}))
  ([{:keys [embedding splitter db]}]
   (if (and db (not (:type db)))
     (throw (ex-info "db must have a `:type` key" {:db db}))
     {:embedding (or embedding (open-ai-embedding))
      :splitter (or splitter (recursive-splitter {:size 500 :overlap 10}))
      :db (or db (in-memory-vector-store))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; loader

(defn- http? [data-sources]
  (some? (or (re-find #"^https://" data-sources)
             (re-find #"^http://" data-sources))))

(defn- path? [data-sources]
  (let [path (str/replace data-sources #"~" (System/getProperty "user.home"))]
    (try
      (.exists (io/file path))
      (catch Exception _
        false))))

(defn get-text [source]
  (cond
    (http? source) (web/get-text source)
    (path? source) (doc/get-text source)
    :else (throw (ex-info (str "Unknown data source:" source) {:source source}))))

(comment

  ;; 물어보기
  (ask "Hello!")


  (-> (ask "Hello!") last :ai)


  ;; 모델 바꿔서 물어보기
  (ask "Hello!" {:model "gpt-4-turbo"})


  ;; 프롬프트 템플릿 쓰기
  (-> (prompt "Context: { context }\nQuestion: { question }"
              {:context "Constacts는 새로운 소셜 미디어입니다."
               :question "Constacts는 무엇인가요?"})
      ask
      last)

   ;; Langchain Hub 프롬프트 템플릿 쓰기
  (require '[ragtacts.prompt.langchain :as langchain])
  (-> (prompt (langchain/hub "rlm/rag-prompt")
              {:context "Constacts는 새로운 소셜 미디어입니다."
               :question "Constacts는 무엇인가요?"})
      ask
      last)


  ;; 퓨샷 러닝
  (-> (ask [{:user "정말 멋지네요!"}
            {:ai "state: 부정"}
            {:user "이건 나쁘다!"}
            {:ai "state: 긍정"}
            {:user "와우 그 영화 정말 멋졌어요!"}
            {:ai "state: 긍정"}
            {:user "정말 끔찍한 쇼였어!"}])
      last)


  ;; 대화 이어서 물어보기 (메모리)
  (-> (ask "안녕, 나는 토니 스타크라고 해.")
      (conj "내 이름이 뭐였지?")
      ask
      last)


  (defn ^{:desc "Get the current weather in a given location"} get-current-weather
    [^{:type "string" :desc "The city, e.g. San Francisco"} location]
    (case (str/lower-case location)
      "tokyo" {:location "Tokyo" :temperature "10" :unit "fahrenheit"}
      "san francisco" {:location "San Francisco" :temperature "72" :unit "fahrenheit"}
      "paris" {:location "Paris" :temperature "22" :unit "fahrenheit"}
      {:location location :temperature "unknown"}))

  (ask "What 's the weather like in San Francisco, Tokyo, and Paris?"
       {:tools [#'get-current-weather] :as :values})


  ;; 벡터 저장소에 저장하기
  (let [db (vector-store)]
    (save db ["토끼는 3살" "곰은 12살" "다람쥐는 14살" "강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"]))


  ;; 벡터 저장소에서 유사한 문서 검색하기
  (let [db (vector-store)]
    (save db ["토끼는 3살" "곰은 12살" "다람쥐는 14살" "강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"])
    (search db "토끼와 호랑이 중에 누가 더 나이가 많습니까?"))


  ;; 벡터 저장소에 메타데이터와 함께 저장하기
  (let [db (vector-store)]
    (save db [{:text "토끼는 3살"
               :metadata {:animal "형"}}
              {:text "토끼는 5살"
               :metadata {:animal "동생"}}])
    (search db "토끼와 호랑이 중에 누가 더 나이가 많습니까?"))


  ;; 메타데이터로 필터링해서 검색하기
  (let [db (vector-store)]
    (save db [{:text "토끼는 3살"
               :metadata {:animal "형"}}
              {:text "토끼는 5살"
               :metadata {:animal "동생"}}])
    (search db "토끼와 호랑이 중에 누가 더 나이가 많습니까?" {:metadata {:animal "형"}}))


  ;; 여러 벡터 디비에서 검색하기
  (let [db1 (vector-store {:db (milvus {:collection (str "animals" (gensym))})})
        db2 (vector-store {:db (milvus {:collection (str "animals" (gensym))})})]
    (save db1 ["토끼는 3살" "곰은 12살" "토끼와 호랑이" "토끼와 호랑이 중에 누가 더 나이가 많습니까?"])
    (save db2 ["강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"])
    (search [db1 db2] "토끼와 호랑이 중에 누가 더 나이가 많습니까?" {:weights [0.4 0.6]}))


  ;; Milvus 벡터 저장소 사용하기
  (let [db (vector-store {:db (milvus {:collection "animals"})})]
    (save db ["토끼는 3살" "곰은 12살" "다람쥐는 14살" "강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"])
    (search db "토끼와 호랑이 중에 누가 더 나이가 많습니까?"))


  ;; Milvus 벡터 저장소 필터링해서 검색하기
  (let [db (vector-store {:db (milvus {:collection (str "animals" (gensym))})})]
    (save db [{:text "토끼는 3살"
               :metadata {:animal "형"}}
              {:text "토끼는 5살"
               :metadata {:animal "동생"}}])
    (search db "토끼와 호랑이 중에 누가 더 나이가 많습니까?" {:metadata {:animal "형"}}))


  ;; 웹 문서에서 텍스트 가져와서 벡터 저장소에 저장하기
  (let [db (vector-store)
        text (web/get-text "https://aws.amazon.com/what-is/retrieval-augmented-generation/")]
    (save db [text])
    (search db "What is RAG?"))


  ;; PDF 문서에서 텍스트 가져오기
  (let [db (vector-store)
        text (doc/get-text "~/papers/RAPTOR.pdf")]
    (save db [text])
    (search db "What is RAPTOR?"))


  ;; 웹 페이지가 바뀌면 바뀐 내용을 가져오기
  (def web-wather
    (web/watch {:url "https://aws.amazon.com/what-is/retrieval-augmented-generation/"
                :interval 1000}
               (fn [change-log]
                 (println change-log))))

  (web/stop-watch web-wather)


  ;; 폴더가 바뀌면 바뀐 내용을 가져오기 WIP
  (def folder-wather
    (doc/watch {:path "~/papers"}
               (fn [change-log]
                 (println change-log))))

  (doc/stop-watch folder-wather)


  ;; 벡터 저장소에서 가져온 내용을 문맥으로 질문하기
  (let [rag-prompt (langchain/hub "rlm/rag-prompt")
        db (vector-store)
        question "토끼와 호랑이 중에 누가 더 나이가 많습니까?"]
    (save db ["토끼는 3살" "곰은 12살" "다람쥐는 14살" "강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"])
    (-> (ask (prompt rag-prompt {:context (str/join "\n" (search db question))
                                 :question question}))
        last))

  ;;
  )