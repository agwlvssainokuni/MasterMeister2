# NFR Requirements Plan — U7: Saved Query / Execution / History

## ユニット適用可否の判定

`construction/nfr-requirements.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`、
`execution-plan.md`）に基づき、U7は **実行（EXECUTE）** と判定（全ユニット共通でNFR
Requirements/NFR DesignはEXECUTE）。

- **Performance/Scalability**: `listHistory`（GEN-15）のページングデフォルト・選択肢値が
  未確定。
- **Reliability**: `mm.app.query-execution.query-timeout`（Functional Design Q11で既に
  キー名・既定値`30`秒は確定済み）の具体的な実装方式（どのJDBC呼び出しに適用するか）が
  未確定。
- **Security/Reliability**: GEN-13の手入力SQLに対するJSqlParser解析（`business-rules.md`
  2.1）は`queryexecution`パッケージ独自の呼び出しであり、U6の`querybuilder.parse-max-length`/
  `parse-timeout`（`querybuilder`スコープのみ）とは別に、極端に長い/病的な入力に対する
  ガードが未検討。
- **Tech Stack**: `SavedQuery.sql`・`QueryHistory.sql`/`params`はいずれも可変長文字列だが、
  DBカラム型（`TEXT`相当か上限付き`VARCHAR`か）が未確定。
- **Reliability/Scalability**: `SavedQuery.executionCount`のアトミックインクリメント
  （Functional Design Q3で「`UPDATE ... SET execution_count = execution_count + 1`相当」と
  方針は確定済み）の具体的な実装方式（Spring Data JPAでの表現方法）が未確定。

→ 上記5件を質問として構成する。

---

## Step 1: Functional Design成果物の分析

- [x] `domain-entities.md`（`SavedQuery`/`QueryHistory`の2エンティティ、`QueryResult`/
      `PagingOption`/`HistoryFilterCriteria`/`SavedQueryStatus`等のサポート型、新規例外
      クラスなし）確認
- [x] `business-rules.md`（1: 可視性・編集・廃止権限、2: 読み取り専用SQL検証（JSqlParser
      直接利用）、3: パラメータ検出、4: ページング、5: 履歴の絞り込み・可視性マトリクス・
      マスキング、6: U6↔U7連携、7: 大量データ対策（`query-timeout`/`max-result-rows`の
      キー名・既定値は確定済み）、8: API認可）確認
- [x] `business-logic-model.md`（フロー1-4、Testable Properties P1-P10）確認
- [x] `frontend-components.md`（`features/{savedQuery,queryExecution,queryHistory}/`の
      コンポーネント構成、U6との連携実装方針）確認
- [x] U1 `nfr-requirements.md`（監査ログ保持期間ポリシー「本フェーズでは実装しない」の先例）、
      U5 `nfr-requirements.md`（`listRecords`ページング検証方式、対象RDBMSクエリタイムアウト
      `setQueryTimeout`の先例）、U6 `nfr-requirements.md`（JSqlParserライセンス確定・
      `parse-max-length`/`parse-timeout`ガードの先例、ただし`querybuilder`スコープのみで
      `queryexecution`には適用されない）を前提として参照し、重複質問を避ける

---

## 成果物生成タスク（`construction/nfr-requirements.md` Step 6）

- [x] `aidlc-docs/construction/u7-saved-query-execution-history/nfr-requirements/nfr-requirements.md`
- [x] `aidlc-docs/construction/u7-saved-query-execution-history/nfr-requirements/tech-stack-decisions.md`

---

## Question 1: Performance/Scalability — 実行履歴一覧（listHistory）のページングデフォルト・選択肢値

`business-rules.md` 5.1の`listHistory`は`common.PageRequest`/`PageResult`（U1既存）を使うが、
既定ページサイズ・選択可能なページサイズ一覧が未確定。U5の`listRecords`（Q1）と同じ
「既定値＋選択肢リスト」方式を踏襲するか確認する。

A. U5の`listRecords`と同じ方式を踏襲し、設定キー`mm.app.query-history.default-page-size`
   （既定値`50`）・`mm.app.query-history.page-size-options`（既定値`50,100,200`）を新設する。
   検証ロジックも`AuditLogService.resolvePageSize`と同様（リクエストされたページサイズが
   `page-size-options`に存在しない場合は`default-page-size`へフォールバック）とする（推奨）

B. U5とは異なる既定値・選択肢を設定する（具体的な値を指定）

C. ページングは行わず全件返却する（実行履歴が少量であることを前提とする）

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 2: Reliability — クエリ実行タイムアウトの実装方式・適用範囲

`mm.app.query-execution.query-timeout`（Functional Design Q11でキー名・既定値`30`秒は
確定済み）を実際にどのJDBC呼び出しへ適用するかが未確定。U5の`masterdata`（Q2）と同様の
実装方式で良いか確認する。

A. `NamedParameterJdbcTemplate`の`JdbcOperations.setQueryTimeout(int)`（U5と同じ機構）を
   `executeAdhocSql`・`executeSavedQuery`の両方に一律適用する。U3で確立済みの
   `connection-timeout`（コネクション取得までの待機時間、既定5秒）とは独立した別軸の設定
   であり、両者を混同しない（U5 Q2の先例を踏襲、推奨）

B. `executeAdhocSql`と`executeSavedQuery`で異なるタイムアウト値を設定する

C. アプリケーション側での`Future`/`CompletableFuture`によるタイムアウト制御を別途追加する
   （JDBCドライバの`setQueryTimeout`サポート状況に依存しない代替手段）

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 3: Security/Reliability — GEN-13手入力SQLに対するJSqlParser解析のガード

`business-rules.md` 2.1の読み取り専用検証は`queryexecution`パッケージから直接JSqlParserを
呼び出す（U6の`querybuilder.parse-max-length`/`parse-timeout`とは別スコープ、適用されない）。
極端に長い入力や病的な構造によるパーサの処理時間肥大化・リソース枯渇のリスクへの対策が
未確定。

A. U6 Q2と同じ考え方を`queryexecution`独自の設定キーとして新設する。
   `mm.app.query-execution.sql-max-length`（既定値`10000`文字）を超える入力は
   JSqlParser呼び出し前に`ValidationException`で拒否し、`mm.app.query-execution.parse-timeout`
   （既定値`5`秒、`ExecutorService`によるタイムアウト制御）でパース処理時間を制限する（推奨）

B. U6の`querybuilder.parse-max-length`/`parse-timeout`の設定値をそのまま流用する
   （`queryexecution`専用のキーは新設しない）

C. ガードを設けない（認証済みユーザのみが呼び出せるため、リスクは限定的と判断する）

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 4: Tech Stack — SQL・パラメータを保持するDBカラムの型

`SavedQuery.sql`・`QueryHistory.sql`/`params`はいずれも長さの上限が定まらない可変長文字列。
H2（内部DB）上でのカラム型・長さ上限が未確定。

A. いずれも`@Column(columnDefinition = "CLOB")`相当（H2の`CLOB`型、JPAでは`@Lob`）で
   長さ上限を設けない。GEN-13が「クエリビルダーの範囲を超えた複雑なSQL」も対象とする
   （`business-rules.md` 2.1）ため、`VARCHAR`の上限設定によって正当なSQLが保存できなくなる
   リスクを避ける（Question 3の`sql-max-length`はJSqlParser解析対象の入力サイズガードで
   あり、DBカラムの保存長とは別軸——保存自体は無制限とする、推奨）

B. `VARCHAR(10000)`等、上限付きの文字列型とする（Question 3の`sql-max-length`と同じ上限値に
   揃える）

C. その他（具体的な方針を指定）

[Answer]: A

---

## Question 5: Reliability/Scalability — `executionCount`アトミック更新の実装方式

Functional Design Q3で「`UPDATE ... SET execution_count = execution_count + 1`相当」と
方針は確定済みだが、Spring Data JPAでの具体的な表現方法が未確定（複数ユーザが同一の
保存クエリを同時実行した場合の競合を避ける必要がある）。

A. Spring Data JPAの`@Modifying @Query("UPDATE SavedQuery s SET s.executionCount =
   s.executionCount + 1 WHERE s.id = :id")`（`clearAutomatically = true`）による
   1文でのアトミック更新を用いる。楽観ロック（`@Version`）や「読み込み→加算→保存」方式は
   採用しない（同時実行時のロスト・アップデートを構造的に避けられるため、推奨）

B. `@Version`による楽観ロックを`SavedQuery`に追加し、「読み込み→加算→保存」を
   リトライループで実行する

C. その他（具体的な方針を指定）

[Answer]: A

---

## Scalability/Availability/Usability/Maintainability — 新規論点なし

- **リテンション（`QueryHistory`の保持期間）**: U1 NFR Requirements（Q5、
  `aidlc-docs/construction/u1-platform-foundation/nfr-requirements/tech-stack-decisions.md`
  #12「監査ログの保持期間ポリシーは本フェーズでは実装しない」）の先例をそのまま適用する。
  `QueryHistory`も自動削除・アーカイブは実装せず、将来的な運用課題として記録のみ行う。
- **可用性・単一ノードデプロイ前提**: U1〜U6のNFR Requirementsで確立した「小規模内部利用が
  前提」「単一ノードデプロイ」という判断方針をそのまま適用する。
- **`SavedQueryService.getStatuses`のキャッシュ**: Question 1で確定するページサイズ上限
  （最大`200`件）により1回の呼び出し規模が構造的に抑えられるため、追加のキャッシュ機構は
  導入しない（U4の`EffectivePermissionResolver`キャッシュとは独立した新規呼び出しだが、
  規模が小さいため不要と判断）。

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。