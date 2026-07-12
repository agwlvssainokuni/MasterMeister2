# nfr-design-patterns.md — U7: Saved Query / Execution / History

`u7-saved-query-execution-history-nfr-design-plan.md`（Question 1〜4、全回答A）に基づく
設計パターン。

---

## 1. Logical Components Patterns

### 1.1 `queryexecution`パッケージ内部のクラス構成（Question 1）

- `QueryExecutionService`（オーケストレーション：フロー2手順1-9の呼び出し順序を制御、
  `executeAdhocSql`/`executeSavedQuery`のエントリポイント）から、以下3つの専用クラスへ
  責務を分割する。いずれも`cherry.mastermeister.queryexecution`パッケージに配置し、
  `common`への切り出しは行わない（U6 Q1と同じ判断基準：`queryexecution`が所有する単一実装で
  あり、複数ユニットが実装を追加する拡張ポイントではない）。
  - `ReadOnlySqlValidator`: JSqlParserによる`Select`型判定（`business-rules.md` 2.1）。
    タイムアウト制御（1.2節）もこのクラスが担う。
  - `SqlParamDetector`: 正規表現によるパラメータ検出（`business-rules.md` 3節）。
  - `PagingSqlBuilder`: サブクエリラップ＋`DialectStrategy.buildPagingClause`
    （`business-rules.md` 4節）。
- 各クラスが単一責任を持つことで、Code Generation時のテスト容易性（`ReadOnlySqlValidator`
  単体でのJSqlParserパーステスト等）が向上する。
- **依存方向**: `queryexecution → common, audit, rdbmsconnection, queryhistory, savedquery`
  （`component-dependency.md`確定済み）の一方向のみ。`querybuilder`（U6）・`masterdata`
  （U5）・`permission`（U4）への依存は持たない（`business-rules.md` 2.2、U4非依存の申し
  送り事項）。

### 1.2 GEN-13手入力SQLに対するJSqlParserタイムアウト制御の実装パターン（Question 2）

- `ReadOnlySqlValidator`（1.1節）が保持する固定サイズの共有`ExecutorService`
  （`Executors.newFixedThreadPool(n)`、設定キー`mm.app.query-execution.parse-executor-
  pool-size`、既定値`4`、U6の`querybuilder.parse-executor-pool-size`と同じパターンだが
  別スコープの設定キーとして独立）に、`CCJSqlParserUtil.parse(sql)`を呼び出す
  `Callable<Statement>`を`submit`する。
- `Future.get(parseTimeoutSeconds, TimeUnit.SECONDS)`（`mm.app.query-execution.
  parse-timeout`、既定値`5`秒、NFR Requirements確定済み）でタイムアウトを待つ。
- `TimeoutException`発生時は`future.cancel(true)`で該当タスクへの割り込みを試みたうえで、
  `business-rules.md` 2.1の「パース自体に失敗した場合」と同じ扱い（`ValidationException`、
  実行を拒否）とする。U6（`ParseResult.fullyParsed = false`で通常応答）とは異なり、GEN-13には
  「一部だけ解析できた結果を返す」概念が存在しないため、タイムアウトは構文的な非対応と同様
  「安全側に倒して拒否」する。
- `ExecutorService`は`ReadOnlySqlValidator`のインスタンスフィールドとしてBean生成時に1回だけ
  生成し、リクエストのたびに使い捨て生成は行わない（U6と同じ理由：スレッド生成コストの回避）。

### 1.3 `QueryHistoryService.recordExecution`の失敗時の扱い（Question 3）

- `QueryHistoryService.recordExecution`は`AuditLogService.record`（U1確定）と同じ設計パターン
  （`@Transactional(propagation = Propagation.REQUIRES_NEW)`＋`try-catch`でログ出力のみ、
  例外を呼び出し元へ伝播しない）を踏襲する。
- `QueryExecutionService`（オーケストレーション、1.1節）は、対象RDBMSへのSELECT実行が成功した
  時点で`QueryResult`の構築を完了しており、その後の`QueryHistoryService.recordExecution`・
  `AuditLogService.record`の呼び出し（フロー2手順8、いずれも失敗を伝播しない）が失敗しても、
  既に構築済みの`QueryResult`をそのまま呼び出し元へ返す。
- `business-logic-model.md` P7（QueryHistory・AuditLog双方が常に1件追加される不変条件）は
  「正常系での期待動作」を表す性質であり、内部DB全体障害時のような極端な異常系まで形式的に
  保証するものではないと解釈する。

---

## 2. Scalability/Performance Patterns（インデックス、Question 4）

- `QueryHistory`に`@Table(indexes = {@Index(columnList = "connectionId, executedAt"),
  @Index(columnList = "savedQueryId")})`の2つの明示的インデックスを追加する。
  - `(connectionId, executedAt)`: `listHistory`の絞り込み（`connectionId`必須条件）・
    日時降順ソート（`business-rules.md` 5.1）をカバーする複合インデックス。
  - `savedQueryId`: `getStatuses`の`savedQueryId IN (...)`バッチ検索（`business-rules.md`
    5.2）をカバーする単独インデックス。
  - `QueryHistory`はNFR Requirements（4節）で「リテンションなし（無期限保持）」と確定済みで
    あり、本プロジェクトで唯一無制限に増加し続ける内部DBエンティティであるため、明示的な
    インデックスを追加する。
- `SavedQuery`には追加の明示的インデックスを設けない。1接続あたりの保存クエリ件数は小規模
  （U2 Q4的な「小規模内部利用が前提」の想定）にとどまると見込まれ、`connectionId`・
  `ownerId`によるテーブルスキャンで実用上問題ないと判断する。`QueryHistory`と`SavedQuery`で
  非対称な判断となるが、前者は「無制限増加」という明確な性質を持つのに対し、後者は
  ユーザが能動的に作成・整理する性質のデータであり増加ペースが本質的に異なる。

---

## 3. Security Patterns

- 該当なし。読み取り専用SQL検証（`:paramN`パラメータ化によるSQLインジェクション対策は
  `NamedParameterJdbcTemplate`が担保）・`queryexecution`のU4非依存は`business-rules.md`
  2.1、2.2でFunctional Design段階において既に確定済みであり、NFR Requirements・NFR Design
  いずれの段階でも新たな論点は生じていない（`security-baseline`拡張は無効、`aidlc-state.md`）。

---

## 4. Resilience Patterns

- 該当なし。GEN-11/13の対象RDBMSへのSQL実行は本ユニットの主機能そのものの結果であり、
  U3 `nfr-design-patterns.md` 6.（対象RDBMS接続失敗は副次的な失敗ではない）・U5
  `nfr-design-plan.md`の判断基準と同じく、本ユニット固有の新規Resilience Patternは設けない
  （`resiliency-baseline`拡張は無効、`aidlc-state.md`）。

---

## 5. PBT適用性（property-based-testing拡張）

- 本ステージ（NFR Design）ではPBT-09（フレームワーク選定）を含むいずれのPBTルールも対象外
  （`property-based-testing.md`のEnforcement Integration表: NFR Designは対象外ステージ）。
  U1〜U6のNFR Design承認時と同様、N/Aとして扱う。