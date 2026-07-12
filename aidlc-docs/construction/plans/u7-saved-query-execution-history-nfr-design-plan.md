# NFR Design Plan — U7: Saved Query / Execution / History

## ユニット適用可否の判定

`construction/nfr-design.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`、
`execution-plan.md`）に基づき、U7は **実行（EXECUTE）** と判定（全ユニット共通でNFR Design
はEXECUTE）。

- **Logical Components**: `unit-of-work.md`で`SavedQueryService`/`QueryExecutionService`/
  `QueryHistoryService`の3サービス（パッケージ単位）は確定済みだが、`queryexecution`内部で
  読み取り専用検証（JSqlParser）・パラメータ検出（正規表現）・ページングSQL組み立てを
  別クラスに分割するか単一サービスに集約するかが未確定（U6 Q1と同種の論点）。
- **Performance/Tech Stack**: NFR Requirements Q3で「`mm.app.query-execution.sql-max-length`/
  `parse-timeout`を新設する」と方針のみ確定済みだが、具体的な実装パターン（Executor構成、
  タイムアウト超過時の扱い）が未確定（U6 Q2と同種の論点。ただしU6は「解析結果の一部として
  notice表示」だったのに対し、U7は「実行可否の判定」であるため扱いが異なりうる）。
- **Reliability**: `AuditLogService.record`はU1で「内部DB書き込み失敗時も例外を呼び出し元へ
  伝播しない」設計が確定済み（`REQUIRES_NEW`＋`try-catch`）。一方`QueryHistoryService.
  recordExecution`（GEN-14/15の主要機能であり、`AuditLog`と異なり「概要のみ」ではなく詳細を
  保持する役割）が同じ失敗時非伝播方針を踏襲するか、それとも呼び出し元（`executeAdhocSql`/
  `executeSavedQuery`）へ失敗を伝播させるかが未確定。
- **Scalability/Performance（インデックス）**: `QueryHistory`はNFR Requirements（4節）で
  「リテンションなし（無期限保持）」と確定済みであり、本プロジェクトで唯一無制限に増加し
  続ける内部DBエンティティとなる。`listHistory`（絞り込み・ページング）・`getStatuses`
  （`savedQueryId`によるバッチ検索）双方の性能を担保するインデックス設計が未確定。
  `SavedQuery`（`listQueries`のownerId/connectionId/visibility絞り込み）についても同様。
- **Security**: 該当なし。`security-baseline`拡張は無効。`queryexecution`のU4非依存は
  Functional Design（`business-rules.md` 2.2）で申し送り済みで、本ステージでの新規検討対象
  ではない。
- **Resilience**: `resiliency-baseline`拡張は無効。GEN-11/13の対象RDBMSへのSQL実行は本ユニット
  の主機能そのものの結果であり、U3 `nfr-design-patterns.md` 6.（対象RDBMS接続失敗は副次的な
  失敗ではない）・U5 `nfr-design-plan.md`の判断基準と同じく、本ユニット固有の新規Resilience
  Patternは設けない（個別質問は設けない）。

→ 上記の中から4問を構成する。

---

## Step 1: NFR Requirements成果物の分析

- [x] `nfr-requirements.md`（1.1-1.3 ページング・JSqlParserガード・キャッシュ非新設、
      2.1-2.2 クエリタイムアウト・executionCountアトミック更新、3.1 DBカラム型、
      4. 他NFR領域（リテンション含む）は新規論点なし、5. PBT）確認
- [x] `tech-stack-decisions.md`（決定事項7件、依存関係追加なし——JSqlParserはU6からの
      再利用）確認
- [x] `functional-design/domain-entities.md`（`SavedQuery`/`QueryHistory`の2エンティティ、
      `QueryResult`/`PagingOption`/`HistoryFilterCriteria`/`SavedQueryStatus`等）確認
- [x] `functional-design/business-rules.md`（1: 可視性・編集・廃止権限、2: 読み取り専用SQL
      検証、3: パラメータ検出、4: ページング、5: 履歴の絞り込み・可視性マトリクス・
      マスキング、6: U6↔U7連携、7: 大量データ対策、8: API認可）確認
- [x] `functional-design/business-logic-model.md`（フロー1-4、P1〜P10。P7の
      「QueryHistory・AuditLog双方が常に1件追加される」不変条件を確認）確認
- [x] `functional-design/frontend-components.md`確認
- [x] U1 `nfr-design-patterns.md`（`AuditLogService`の`REQUIRES_NEW`＋非伝播パターン、
      `AuditLog`の`@Table(indexes = {...})`パターン）、U2 `nfr-design-plan.md`
      （Question 4のインデックス実装判断基準——unique制約による暗黙インデックス vs
      明示インデックス）、U4 `nfr-design-plan.md`（Question 4のインデックス実装判断基準——
      複合インデックスの左端列一致原則）、U5 `nfr-design-plan.md`（Question 3の
      ステートメント単位タイムアウトの判断基準、Resilience非該当の判断基準）、U6
      `nfr-design-plan.md`（Question 1のサービス分割判断基準、Question 2のJSqlParser
      タイムアウト実装パターン）を前提として参照し、判断基準を揃える

---

## 成果物生成タスク（`construction/nfr-design.md` Step 6）

U1〜U6の先例（`nfr-design-patterns.md`／`logical-components.md`の2ファイルを常に生成）に
倣い、本ユニットも同様とする。

- [ ] `aidlc-docs/construction/u7-saved-query-execution-history/nfr-design/nfr-design-patterns.md`
- [ ] `aidlc-docs/construction/u7-saved-query-execution-history/nfr-design/logical-components.md`

---

## Question 1: Logical Components — `queryexecution`パッケージ内部のクラス構成

`SavedQueryService`/`QueryExecutionService`/`QueryHistoryService`の3サービス（パッケージ単位）
は確定済みだが、`QueryExecutionService`が担う3つの異なる責務（読み取り専用検証・パラメータ
検出・ページングSQL組み立て）を単一クラスに集約するか、専用クラスに分割するかが未確定。

A. `QueryExecutionService`（オーケストレーション：フロー2手順1-9の呼び出し順序を制御）から、
   `ReadOnlySqlValidator`（JSqlParserによる`Select`型判定、`business-rules.md` 2.1）・
   `SqlParamDetector`（正規表現によるパラメータ検出、`business-rules.md` 3節）・
   `PagingSqlBuilder`（サブクエリラップ＋`DialectStrategy.buildPagingClause`、
   `business-rules.md` 4節）の3つの専用クラスに分割する。いずれも`cherry.mastermeister.
   queryexecution`パッケージ内に配置し、`common`への切り出しは行わない（U6 Q1と同じ判断
   基準：`queryexecution`が所有する単一実装であり、複数ユニットが実装を追加する拡張ポイント
   ではない）。各クラスが単一責任を持つことで、Code Generation時のテスト容易性
   （`ReadOnlySqlValidator`単体でのJSqlParserパーステスト等）も向上する（推奨）
B. `QueryExecutionService`1クラスに全ロジックをprivateメソッドとして実装し、専用クラスへの
   分割は行わない（責務ごとのクラス数を最小化する）
C. その他（具体的な構成を指定）

[Answer]: A

---

## Question 2: Performance/Tech Stack — GEN-13手入力SQLに対するJSqlParserタイムアウト制御の実装パターン

NFR Requirements Q3で「`mm.app.query-execution.parse-timeout`（既定5秒）でパース処理時間を
制限する」と方針決定済みだが、具体的な実装（Executor構成、タイムアウト超過時の扱い）が
未確定。U6 Q2は「タイムアウト超過時は例外化せず`ParseResult.fullyParsed = false`として通常
応答する」という設計だったが、U7のGEN-13は「実行するか拒否するか」の二択であり、U6のような
「一部だけ解析できた結果を返す」という選択肢が存在しない。

A. `ReadOnlySqlValidator`（Question 1で新設）が保持する固定サイズの共有`ExecutorService`
   （`Executors.newFixedThreadPool(n)`、設定キー`mm.app.query-execution.parse-executor-
   pool-size`既定値`4`、U6の`querybuilder.parse-executor-pool-size`と同じパターンだが別
   スコープの設定キーとして独立させる）に、`CCJSqlParserUtil.parse(sql)`を呼び出す
   `Callable<Statement>`を`submit`する。`Future.get(parseTimeoutSeconds, TimeUnit.SECONDS)`
   でタイムアウトを待ち、`TimeoutException`発生時は`future.cancel(true)`で該当タスクの中断を
   試みたうえで、`business-rules.md` 2.1の「パース自体に失敗した場合」と同じ扱い
   （`ValidationException`、実行を拒否）とする。理由：GEN-13には「一部だけ解析できた結果を
   返す」概念が存在せず、タイムアウトは構文的な非対応（サブクエリ等ではなく処理時間の限界）と
   同様「安全側に倒して拒否」すべき事象である（推奨）
B. タイムアウト超過時のみ専用の例外メッセージ（「SQLの解析がタイムアウトしました」）を
   `ValidationException`に含め、構文エラーと区別できるようにする
C. `ExecutorService`はU6の`querybuilder.parse-executor-pool-size`のインスタンスをそのまま
   共有し、`queryexecution`独自のプールは新設しない
D. その他（具体的な実装パターンを指定）

[Answer]: A

---

## Question 3: Reliability — `QueryHistoryService.recordExecution`の失敗時の扱い

U1で確定済みの`AuditLogService.record`は、内部DB書き込み失敗時も例外を呼び出し元へ伝播しない
（`REQUIRES_NEW`＋`try-catch`でログ出力のみ）。`QueryHistoryService.recordExecution`
（GEN-14/15の主要機能であり、`AuditLog`と異なり「概要のみ」ではなく詳細を保持する役割、
`business-logic-model.md` P7）についても同じ非伝播方針を踏襲するか、それとも記録失敗時は
`executeAdhocSql`/`executeSavedQuery`呼び出し自体を失敗させる（対象RDBMSへのSELECT自体は
成功しているにも関わらず、既に取得済みの`QueryResult`を呼び出し元へ返さない）かが未確定。

A. `QueryHistoryService.recordExecution`も`AuditLogService.record`と同じ設計パターン
   （`@Transactional(propagation = REQUIRES_NEW)`＋`try-catch`でログ出力のみ、例外を
   呼び出し元へ伝播しない）を踏襲する。理由：対象RDBMSへのSELECT自体は既に成功しており、
   ユーザが取得した`QueryResult`を内部DBの副次的な書き込み失敗（一時的な内部DB障害等）を
   理由に破棄するのは本末転倒である。`business-logic-model.md` P7（QueryHistory・AuditLog
   双方が常に1件追加される不変条件）は「正常系での期待動作」を表す性質であり、内部DB全体
   障害時のような極端な異常系まで形式的に保証するものではないと解釈する（推奨）
B. `QueryHistoryService.recordExecution`の失敗は呼び出し元へ伝播させ、`executeAdhocSql`/
   `executeSavedQuery`全体を失敗として扱う（履歴の完全性を可用性より優先する）
C. その他（具体的な失敗時の扱いを指定）

[Answer]: A

---

## Question 4: Scalability/Performance — `SavedQuery`/`QueryHistory`のインデックス実装方針

`QueryHistory`はNFR Requirements（4節）で「リテンションなし（無期限保持）」と確定済みであり、
本プロジェクトで唯一無制限に増加し続ける内部DBエンティティとなる。`listHistory`
（`connectionId`・日時範囲・実行者による絞り込み、`business-rules.md` 5.1）・`getStatuses`
（`savedQueryId`によるバッチ検索、5.2）双方の性能を担保するインデックスが必要と考えられる。
`SavedQuery`の`listQueries`（`connectionId`・`ownerId`・`visibility`による絞り込み）は
1接続あたりの保存クエリ件数が小規模（U2 Q4的な「小規模内部利用が前提」の想定）にとどまると
見込まれ、扱いを分けたい。

A. `QueryHistory`に`@Table(indexes = {@Index(columnList = "connectionId, executedAt"),
   @Index(columnList = "savedQueryId")})`の2つの明示的インデックスを追加する（前者は
   `listHistory`の絞り込み・日時降順ソートを、後者は`getStatuses`の`savedQueryId IN (...)`
   検索をそれぞれカバーする、U1の`AuditLog`の複合インデックスパターンを踏襲）。`SavedQuery`
   には追加の明示的インデックスを設けない（1接続あたりの件数が小規模で、`connectionId`・
   `ownerId`によるテーブルスキャンで実用上問題ないと判断、U4 Q4で「小規模内部利用が前提」
   として追加インデックスを見送った箇所とは逆に`GroupMember`では追加した判断基準——
   本ユニットでは`QueryHistory`が「無制限増加」という明確な性質を持つため、`QueryHistory`
   のみインデックスを追加しSavedQueryは見送るという非対称な判断とする）（推奨）
B. `SavedQuery`にも`(connectionId, ownerId)`への明示的インデックスを追加する（`QueryHistory`
   と同様に将来の増加を見込む）
C. `QueryHistory`のインデックスは`savedQueryId`のみとし、`listHistory`の絞り込みは
   `executedAt`単独のインデックスのみとする（`connectionId`は複合条件に含めない）
D. その他（具体的なインデックス方針を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。