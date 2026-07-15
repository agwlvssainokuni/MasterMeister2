# U7 Code Generation Plan（変更要求: 接続コンテキストのグローバル化 + クエリ実行時スキーマ指定）

## 対象
`frontend-components.md`・`business-rules.md`・`domain-entities.md`（U7 Functional Design改訂、
訂正版）に基づく実装。今回の変更要求で最大規模。バックエンド（スキーマ検証・`SET
search_path`・新規エンドポイント・`QueryHistory.schema`列）とフロントエンド（5画面の改修）の
両方を含む。

## ステップ

### バックエンド

- [x] Step 1: ドメイン型へのschema追加 — `QueryHistory.java`（エンティティ、`schema`列
      `NOT NULL`）、`ExecutionRecord.java`、`HistoryEntry.java`に`schema`フィールドを追加する。
      `AdhocExecutionRequest.java`・`SavedExecutionRequest.java`に`schema`フィールドを追加する。
- [x] Step 2: `QueryExecutionService.java`改修 — `EffectivePermissionResolver`を新規注入する。
      `executeAdhocSql`/`executeSavedQuery`/`execute`/`runQuery`に`schema`パラメータを追加する。
      実行前に`effectivePermissionResolver.listAccessibleSchemas(userId, connectionId)`で
      `schema`を検証し（含まれない場合`PermissionDeniedException`）、`SCHEMA_BASED`方言のみ
      同一コネクション上で`SET search_path`を発行してからクエリを実行する（`ConnectionCallback`
      + `SingleConnectionDataSource`で同一コネクションを保証）。`ExecutionRecord`に`schema`を
      渡す。新規メソッド`listAccessibleSchemas(userId, connectionId)`を追加する
      （`EffectivePermissionResolver`への単純委譲）。
- [x] Step 3: `QueryExecutionController.java`改修 — `executeAdhocSql`/`executeSavedQuery`で
      `request.schema()`を渡す。新規エンドポイント`GET /{connectionId}/schemas`を追加する。
- [x] Step 4: `QueryHistoryService.java`改修 — `recordExecution`で`QueryHistory`に`schema`を渡す。
      `toEntry`で`HistoryEntry`に`schema`を渡す。
- [x] Step 5: バックエンドテスト改修・追加 — `QueryExecutionServiceTest.java`（既存テストへの
      `schema`パラメータ追加、スキーマ検証失敗時の`PermissionDeniedException`、`SET
      search_path`が実際に効くこと——H2 TCPサーバでの実接続検証、CATALOG_BASED方言では
      `SET`が発行されないこと）。`QueryExecutionControllerTest.java`（既存テストへの`schema`
      追加、新規`GET /{connectionId}/schemas`のテスト）。`QueryHistoryServiceTest.java`・
      `QueryHistoryControllerTest.java`（`schema`フィールドの伝播確認）。

### フロントエンド

- [x] Step 6: `queryExecution/api.ts`・`types.ts`改修 — `listAccessibleSchemas(connectionId)`を
      新規追加する。`executeAdhocSql`/`executeSavedQuery`に`schema`パラメータを追加する。
- [x] Step 7: `QueryExecutionPage.tsx`改修 — `connectionId`の取得元を`savedQueryId`有無で分岐
      （`savedQueryId`指定時は`getQuery(savedQueryId)`のレスポンス、未指定時は
      `useConnection()`）。スキーマ選択`<select>`を追加（`listAccessibleSchemas`呼び出し、
      選択肢1件でも自動選択しない、URLの`schema`パラメータでプリフィル）。`schema`未選択時は
      実行不可にする。`executeAdhocSql`/`executeSavedQuery`呼び出しに`schema`を追加する。
- [x] Step 8: `savedQuery/SavedQueryListPage.tsx`改修 — `connectionId`を`useConnection()`から
      取得する（URL受け取りを廃止）。「実行」リンクは`savedQueryId`のみを付与する
      （`connectionId`は付与しない）。
- [x] Step 9: `savedQuery/SavedQuerySaveForm.tsx`改修 — `connectionId`を`useConnection()`から
      取得する（URL受け取りを廃止）。
- [x] Step 10: `savedQuery/SavedQueryDetailPage.tsx`改修 — 「実行」ボタンの遷移先URLから
      `connectionId`パラメータを削除する（`savedQueryId`のみ）。
- [x] Step 11: `queryHistory/types.ts`改修 — `HistoryEntry`に`schema`フィールドを追加する。
- [x] Step 12: `queryHistory/QueryHistoryListPage.tsx`改修 — `connectionId`を`useConnection()`
      から取得する（URL受け取りを廃止）。スキーマ列を追加する。「再実行」「ビルダーで編集」の
      遷移に`schema`パラメータを追加する（「保存」は`schema`を引き継がない）。いずれのボタンも
      `connectionId`パラメータは付与しない（実行/保存/ビルダー各画面がグローバルコンテキスト
      またはURLの`schema`から解決するため）。
- [x] Step 13: フロントエンドテスト改修 — `SavedQueryListPage.test.tsx`・
      `SavedQuerySaveForm.test.tsx`・`SavedQueryDetailPage.test.tsx`・
      `QueryExecutionPage.test.tsx`（大幅改訂）・`QueryHistoryListPage.test.tsx`を
      `useConnectionStore.setState`ベースに改訂し、スキーマ関連の新規テストケースを追加する。

### ドキュメント

- [x] Step 14: Documentation Generation — `aidlc-docs/construction/u7-saved-query-execution-
      history/code/`配下の該当ドキュメントに本変更を追記する。
