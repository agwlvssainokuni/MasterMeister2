# nfr-requirements.md — U7: Saved Query / Execution / History

`u7-saved-query-execution-history-nfr-requirements-plan.md`（Q1〜Q5）の回答に基づく
非機能要件。全問回答Aのため、質問本文の推奨案どおりに確定する。

---

## 1. Performance/Scalability Requirements

### 1.1 実行履歴一覧（`listHistory`）のページング（Q1）

- U5の`listRecords`・`AuditLogService`と同じ「既定ページサイズ＋選択肢リストによる検証」
  方式を踏襲する。設定キー`mm.app.query-history.default-page-size`（既定値`50`）・
  `mm.app.query-history.page-size-options`（既定値`50,100,200`）を新設する。
- 検証ロジックは`AuditLogService.resolvePageSize`と同様：リクエストされたページサイズが
  `page-size-options`のリストに存在しない場合は`default-page-size`へフォールバックする。
- `SavedQueryService.getStatuses`（`business-rules.md` 5.2）は1回の`listHistory`呼び出しに
  つき1回のみ呼び出されるため、ページサイズ上限（最大`200`件）により呼び出し規模が構造的に
  抑えられる（1.3参照）。

### 1.2 GEN-13手入力SQLに対するJSqlParser解析のガード（Q3）

- U6の`querybuilder.parse-max-length`/`parse-timeout`と同じ考え方を`queryexecution`独自の
  設定キーとして新設する（`querybuilder`スコープの設定はU6の`SqlParsingService`専用であり、
  `queryexecution`の直接JSqlParser呼び出し（`business-rules.md` 2.1）には適用されないため）。
- 設定キー`mm.app.query-execution.sql-max-length`（既定値`10000`文字）を新設し、これを
  超える入力は`CCJSqlParserUtil.parse`呼び出し前に`ValidationException`で拒否する。
- 設定キー`mm.app.query-execution.parse-timeout`（既定値`5`秒）を新設し、`ExecutorService`
  によるタイムアウト制御でパース処理時間を制限する（U6 Q2と同じ実装パターン）。
- `executeAdhocSql`・`executeSavedQuery`（保存クエリ実行、GEN-11）の両方に一律適用する。
  保存クエリ実行時のSQLは保存時点で既にこのガードを一度通過しているが、実行のたびに再度
  検証する（`SavedQuery.sql`はUPDATE可能なフィールドであり、保存後に外部から改変される
  余地はないものの、検証タイミングの一貫性を優先する）。

### 1.3 `SavedQueryService.getStatuses`のキャッシュ（新規論点なし）

- 追加のキャッシュ機構は導入しない。1.1で確定したページサイズ上限（最大`200`件）により
  1回の呼び出し規模が構造的に抑えられるため、U4の`EffectivePermissionResolver`のような
  キャッシュ層は不要と判断する。

---

## 2. Reliability Requirements

### 2.1 対象RDBMSへのクエリ実行タイムアウト（Q2）

- `NamedParameterJdbcTemplate`（内部的には`JdbcOperations.setQueryTimeout(int)`、単位は秒）に
  クエリタイムアウトを設定する（U5 Q2と同じ機構）。
- `mm.app.query-execution.query-timeout`（Functional Design Q11でキー名・既定値`30`秒は
  確定済み）を`executeAdhocSql`・`executeSavedQuery`の両方に一律適用する。
- U3で確立した`connection-timeout`（既定5秒、コネクション取得までの待機時間）・U5で確立した
  `mm.app.master-data.query-timeout`（既定30秒、`masterdata`スコープ）とはいずれも独立した
  別の設定値であり、混同しない。本設定は`queryexecution`スコープでのSQL文実行時間のみを
  制限する。

### 2.2 `executionCount`アトミック更新の実装方式（Q5）

- Spring Data JPAの`@Modifying @Query("UPDATE SavedQuery s SET s.executionCount =
  s.executionCount + 1 WHERE s.id = :id")`（`clearAutomatically = true`）による1文での
  アトミック更新を用いる。
- 楽観ロック（`@Version`）や「読み込み→加算→保存」方式は採用しない——複数ユーザが同一の
  保存クエリを同時実行してもロスト・アップデートが構造的に発生しない。
- `QueryHistory.executionCount`（スナップショット）へは、上記UPDATE文の実行後に改めて
  `SavedQuery`を取得して反映するか、UPDATE文の戻り値（更新後の値）を利用するかは
  Code Generation時点で確定する（`domain-entities.md` Q3の設計自体には影響しない実装詳細）。

---

## 3. Tech Stack Requirements

### 3.1 SQL・パラメータを保持するDBカラムの型（Q4）

- `SavedQuery.sql`・`QueryHistory.sql`/`params`はいずれも`@Lob`（H2の`CLOB`型相当）とし、
  長さ上限を設けない。
- GEN-13が「クエリビルダーの範囲を超えた複雑なSQL」も対象とする（`business-rules.md` 2.1）
  ため、`VARCHAR`の上限設定によって正当なSQLが保存できなくなるリスクを避ける。
- 1.2で確定した`sql-max-length`（既定`10000`文字）はJSqlParser解析対象の入力サイズガードで
  あり、DBカラムの保存長とは別軸——保存自体は無制限とする（`sql-max-length`を超える入力は
  そもそも`ValidationException`で拒否されるため実行には至らないが、保存クエリの`sql`列自体に
  DBレベルの上限は課さない）。

---

## 4. Availability/Security/Usability/Maintainability Requirements

本ユニット固有の新規論点は見当たらない。U1〜U6のNFR Requirements・Functional Designで
確立した「小規模内部利用が前提」「単一ノードデプロイ」という判断方針をそのまま適用する。

- **リテンション（`QueryHistory`の保持期間）**: U1 NFR Requirements（`tech-stack-decisions.md`
  #12「監査ログの保持期間ポリシーは本フェーズでは実装しない」）の先例をそのまま適用する。
  `QueryHistory`も自動削除・アーカイブは実装せず、将来的な運用課題として記録のみ行う。
- **セキュリティ**: `business-rules.md` 2.2で申し送り済みの`queryexecution`のU4非依存
  （テーブル/カラム単位の読み取り権限フィルタを課さない設計判断）は、`security-baseline`
  拡張のオプトイン時に再検討する事項として引き続きフラグを残す（現時点では
  `security-baseline`拡張は無効のため本ステージでの新規検討は行わない）。

---

## 5. PBT Compliance（property-based-testing拡張）

- 本ステージ（NFR Requirements）で新たに適用されるPBTルールはない（U4〜U6 NFR Requirements
  の先例を踏襲）。PBT-01（Property Identification）はFunctional Design段階で適用済み
  （`business-logic-model.md` P1〜P10）。PBT-02以降はCode Generation Planning/Code
  Generation/Build and Testステージで適用される。