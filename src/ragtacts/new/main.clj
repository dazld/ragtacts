(ns ragtacts.new.main
  (:require [ragtacts.new.core :refer :all]
            [ragtacts.new.vector-store.milvus :refer [milvus]]
            [ragtacts.new.loader.web :as web]
            [ragtacts.new.loader.doc :as doc]
            [ragtacts.new.embedding.open-ai :refer [open-ai-embedding]]
            [clojure.string :as str]))

(comment

  ;; 물어보기
  (-> (ask "Hello!") last :ai)



  ;; 모델 바꿔서 물어보기
  (ask "Hello!" {:model "gpt-4-turbo"})



  ;; 프롬프트 템플릿 쓰기
  (-> (prompt "Context: {{ context }}\nQuestion: {{ question }}"
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


  ;; 벡터 저장소에 저장하기
  (let [db (vector-store)]
    (save db ["토끼는 3살" "곰은 12살" "다람쥐는 14살" "강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"]))


  ;; 벡터 저장소에서 유사한 문서 검색하기
  (let [db (vector-store)]
    (save db ["토끼는 3살" "곰은 12살" "다람쥐는 14살" "강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"])
    (search db "토끼와 호랑이 중에 누가 더 나이가 많습니까?"))


  ;; Milvus 벡터 저장소 사용하기
  (let [db (vector-store {:type :milvus
                          :collection "animals"})]
    (save db ["토끼는 3살" "곰은 12살" "다람쥐는 14살" "강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"])
    (search db "토끼와 호랑이 중에 누가 더 나이가 많습니까?"))


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

  ;; 벡터 저장소에서 가져온 내용을 문맥으로 질문하기
  (let [rag-prompt
        "You are an assistant for question-answering tasks. Use the following pieces of retrieved context to answer the question. If you don't know the answer, just say that you don't know. Use three sentences maximum and keep the answer concise.
        Question: {{ question }}
        Context: {{ context }}
        Answer:"
        db (vector-store)
        question "토끼와 호랑이 중에 누가 더 나이가 많습니까?"]
    (save db ["토끼는 3살" "곰은 12살" "다람쥐는 14살" "강아지는 5살" "고양이는 7살" "사자는 10살" "호랑이는 8살"])
    (-> (ask (prompt rag-prompt {:context (str/join "\n" (search db question))
                                 :question question}))
        last))

  ;; 함수 부르기
  (defn ^{:desc "TMI를 구하는 함수입니다"} tmi
    [^{:type "number" :desc "체중"} weight
     ^{:type "number" :desc "키"} height]
    (* weight height))

  (defn ^{:desc "DMI를 구하는 함수입니다"} dmi
    [^{:type "number" :desc "체중"} weight
     ^{:type "number" :desc "키"} height]
    0)

  ;; 함수를 사용해서 질문하기
  (ask "토끼 체중은 10kg이고 키는 0.1m입니다. DMI는 얼마입니까?" {:tools [#'tmi #'dmi]})

  ;; 웹 페이지가 바뀌면 바뀐 내용을 가져오기
  (def web-wather
    (web/watch {:url "https://aws.amazon.com/what-is/retrieval-augmented-generation/"
                :interval 1000}
               (fn [change-log]
                 (println change-log))))

  (web/stop-watch web-wather)

  ;; 폴더가 바뀌면 바뀐 내용을 가져오기
  (def folder-wather
    (doc/watch {:path "~/papers"}
               (fn [change-log]
                 (println change-log))))

  (doc/stop-watch folder-wather)
  ;;
  )