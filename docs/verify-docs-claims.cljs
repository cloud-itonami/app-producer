#!/usr/bin/env nbb
;; verify-docs-claims.cljs — README.md と docs/operator-quickstart.md が
;; 「測った」と言っている数を、実際に測り直して突き合わせる。
;;
;;   nbb docs/verify-docs-claims.cljs
;;
;; exit 0 = PASS / 1 = FAIL / 3 = 判定できなかった（0 でも 1 でもない）
;;
;; ⚠ **custody はここで検査しない。** 「継承した 5 blob が etzhayyim/root@691c245 と
;;   SHA 一致する」はこの repo の中心的な事実だが、その検査には *別の repo* が要る。
;;   手元に etzhayyim/root が無いときの「引けなかった」を「一致した」と同じ値で
;;   返すと、測れなかったことが緑になる。その 1 件は operator-quickstart.md §4 に
;;   置いて人間に引かせる（app-maps 版と同じ判断）。
;;
;; ⚠ **「ビルドできない」も直接は検査しない。** tsc / esbuild がこの機械に無いとき、
;;   「走らせられなかった」が「解決できなかった」と同じ顔になる。代わりに *原因* —
;;   ビルドマニフェストが 0 件であること、import 指定子が 3 つとも repo 内で
;;   解決先を持たないこと — を検査する。これは repo の中だけで決まる。

(require '["node:child_process" :as cp]
         '["node:fs" :as fs]
         '[clojure.string :as str])

(defn- die! [code & msg]
  (binding [*print-fn* *print-err-fn*] (apply println msg))
  (js/process.exit code))

(defn- git [& args]
  (try
    (str/trim (str (cp/execFileSync "git" (clj->js (vec args)) #js {:encoding "utf8"})))
    (catch :default e
      (die! 3 "UNDETERMINED: git" (str/join " " args) "が失敗した —"
            (or (some-> e .-message) "(理由不明)")))))

(defn- slurp* [p]
  (when-not (fs/existsSync p)
    (die! 3 "UNDETERMINED:" p "が無い。この repo のルートで実行すること"))
  (fs/readFileSync p "utf8"))

;; ---------------------------------------------------------------- 入力の床
;; 「入力が無いとき pass を返さない」（ADR-2608136000 の 1 番目）。

(def ^:private tracked
  (let [ls (remove str/blank? (str/split-lines (git "ls-files")))]
    (when (zero? (count ls))
      (die! 3 "UNDETERMINED: git ls-files が空。commit の無い repo か、repo 外で実行している"))
    ls))

;; この repo が継承した 5 ファイル + 抽出が足した 2 ファイル。
;; 文書の数はこの集合について述べており、後から足す文書は含まない。
(def ^:private inherited
  ["NOTICE" "PROJECT.jsonld" "appview/README.md"
   "cdn/producer-ui/src/lib/grpc/transport.ts"
   "cdn/producer-ui/src/lib/server/storyboardService.ts"])
(def ^:private canonical-records ["README.edn" "migration.edn"])
(def ^:private pre-existing (into inherited canonical-records))

(defn- blob-bytes [p]
  (let [n (js/parseInt (git "cat-file" "-s" (git "rev-parse" (str "HEAD:" p))) 10)]
    (if (js/isNaN n)
      (die! 3 "UNDETERMINED:" p "の blob サイズが読めなかった")
      n)))

(def ^:private results (atom []))
(defn- check! [label ok? detail]
  (swap! results conj {:label label :ok? (boolean ok?) :detail detail}))

;; ---------------------------------------------------------------- 検査

;; 1) 継承した 7 ファイルが全部まだ在る（後から消えたら文書は嘘になる）
(let [missing (remove (set tracked) pre-existing)]
  (check! "pre-existing files still tracked" (empty? missing)
          (if (empty? missing) "7/7 tracked" (str "missing: " (str/join ", " missing)))))

;; 2) その 7 ファイルの合計 = 7433 B、継承 5 ファイルの合計 = 6744 B
(let [total (reduce + 0 (map blob-bytes pre-existing))]
  (check! "pre-existing bytes = 7433" (= 7433 total) (str total " B")))
(let [inh (reduce + 0 (map blob-bytes inherited))]
  (check! "inherited bytes = 6744 (migration.edn :bytes)" (= 6744 inh) (str inh " B")))

;; 3) migration.edn 自身が 5 / 6744 / 691c245 / 37a4aac を主張し続けている
(let [m (slurp* "migration.edn")]
  (check! "migration.edn claims :tracked-files 5" (str/includes? m ":tracked-files 5") "")
  (check! "migration.edn claims :bytes 6744" (str/includes? m ":bytes 6744") "")
  (check! "migration.edn pins revision 691c245d"
          (str/includes? m "691c245da48f3acb11dd757218f189ff2482b1c8") "")
  (check! "migration.edn pins git-tree 37a4aac9"
          (str/includes? m "37a4aac95d8eeedb76096b73fa9122b919c798ae") ""))

;; 4) ビルドマニフェストが 0 件（「ビルドできない」の原因）
(let [manifests (filter #(re-find #"(?i)(^|/)(package\.json|package-lock\.json|tsconfig[^/]*\.json|svelte\.config\.[jt]s|vite\.config\.[jt]s|deps\.edn|shadow-cljs\.edn)$" %) tracked)]
  (check! "zero build manifests" (empty? manifests)
          (if (empty? manifests) "0" (str/join ", " manifests))))

;; 5) test ファイルが 0 件
(let [tests (filter #(re-find #"(?i)(^|/)(test|tests|spec)/|\.(test|spec)\.[a-z]+$" %) tracked)]
  (check! "zero test files" (empty? tests)
          (if (empty? tests) "0" (str/join ", " tests))))

;; 6) import 指定子はちょうど 3 つで、どれも repo 内に解決先が無い
(let [ts-files (filter #(str/ends-with? % ".ts") tracked)
      _ (when (zero? (count ts-files))
          (die! 3 "UNDETERMINED: .ts ファイルが 1 つも無い。文書の前提が崩れている"))
      specs (->> ts-files
                 (mapcat #(re-seq #"from '([^']+)'" (slurp* %)))
                 (map second) set)]
  (check! "three import specifiers" (= 3 (count specs))
          (str/join ", " (sort specs)))
  (check! "specifier $app/environment present" (contains? specs "$app/environment") "")
  (check! "specifier @connectrpc/connect present" (contains? specs "@connectrpc/connect") "")
  (check! "specifier $lib/grpc/transport present" (contains? specs "$lib/grpc/transport") "")
  ;; $lib/ と $app/ は SvelteKit の alias。それを定義するものが repo に無いこと。
  (check! "no SvelteKit config to define $lib/$app aliases"
          (not-any? #(re-find #"(?i)svelte\.config" %) tracked) ""))

;; 7) NOTICE が指す CHARTER-RIDER.md が無い
(let [n (slurp* "NOTICE")]
  (check! "NOTICE references CHARTER-RIDER.md" (str/includes? n "CHARTER-RIDER.md") "")
  (check! "CHARTER-RIDER.md absent (dangling reference)"
          (not (some #{"CHARTER-RIDER.md"} tracked)) ""))

;; 8) PROJECT.jsonld の空っぽさ、と scheduler.jsonld の不在
(let [p (try (js->clj (js/JSON.parse (slurp* "PROJECT.jsonld")) :keywordize-keys false)
             (catch :default e
               (die! 3 "UNDETERMINED: PROJECT.jsonld が JSON として読めない —"
                     (or (some-> e .-message) ""))))]
  (check! "PROJECT.jsonld description = TBD" (= "TBD" (get p "description")) (str (get p "description")))
  (check! "PROJECT.jsonld status = Planned" (= "Planned" (get p "status")) (str (get p "status")))
  (check! "capabilities.terms empty" (zero? (count (get-in p ["capabilities" "terms"]))) "")
  (let [si (get p "survivalIndicators")]
    (check! "2 survivalIndicators, all null-valued"
            (and (= 2 (count si)) (every? #(nil? (get % "value")) si))
            (str (count si) " indicators")))
  (check! "scheduler ref = scheduler.jsonld" (= "scheduler.jsonld" (get p "scheduler")) "")
  (check! "scheduler.jsonld absent (dangling reference)"
          (not (some #{"scheduler.jsonld"} tracked)) ""))

;; 9) appview/README.md が「実装済み」と書く component が repo に無い
(let [a (slurp* "appview/README.md")]
  (check! "appview/README.md names producer-mcp-component"
          (str/includes? a "producer-mcp-component") "")
  (check! "producer-mcp-component absent from repo"
          (not-any? #(str/includes? % "producer-mcp-component") tracked) ""))

;; 10) 文書が上の数を実際に書いている（測定値と本文の結び付け）
(let [r (slurp* "README.md")
      q (slurp* "docs/operator-quickstart.md")]
  (check! "README states 7,433 bytes" (str/includes? r "7,433") "")
  (check! "README states 7 tracked files" (str/includes? r "7 tracked files") "")
  (check! "README states 9 errors / 3 TS2307" (and (str/includes? r "9 errors") (str/includes? r "TS2307")) "")
  (check! "README states 15 errors on default flags" (str/includes? r "15 errors") "")
  (check! "quickstart states 7433 total" (str/includes? q "7433 total") "")
  (check! "quickstart states 6,744 / 5 files" (and (str/includes? q "6,744") (str/includes? q "6744")) ""))

;; ---------------------------------------------------------------- 報告
;; evidence floor: 実行本数の床。0 件を clean にしない。
(let [rs @results
      n (count rs)
      failed (remove :ok? rs)]
  (when (< n 25)
    (die! 3 "UNDETERMINED: 検査が" n "件しか走らなかった（25 件以上を期待）。"
          "検査自身が壊れている疑いがある"))
  (println (str "CHECKED\t" n))
  (doseq [{:keys [label ok? detail]} rs]
    (println (str (if ok? "ok  " "FAIL") "\t" label (when (seq detail) (str "\t— " detail)))))
  (if (seq failed)
    (die! 1 (str "\n" (count failed) " / " n " claim(s) no longer true."))
    (do (println (str "\nPASS — " n " claims re-measured, all matched."))
        (js/process.exit 0))))
