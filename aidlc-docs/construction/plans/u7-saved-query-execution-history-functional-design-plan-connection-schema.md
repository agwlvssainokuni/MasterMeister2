# U7 Functional Design 改訂プラン（変更要求: 接続コンテキストのグローバル化 + クエリ実行時スキーマ指定）

## 対象
`requirements.md` §9・`stories.md`（CHG-3〜CHG-5、GEN-10/11/13/15/16改訂）・Application Design
（`queryexecution → permission`新規依存、`QueryExecutionService`のスキーマ検証・
`SET search_path`、`QueryHistoryService`のスキーマ記録）を受けた、U7のFunctional Design
（`domain-entities.md`・`business-rules.md`・`frontend-components.md`）への反映。
今回の変更要求で最も変更量が大きいユニット。

## 質問要否の判定
新たな確認質問は発生しないと判断した。スキーマ指定・検証・`SET search_path`の方式は
Requirements Analysis（Q1〜Q8）・Application Design（Q1〜Q3）で確定済みであるため。

## 実行チェックリスト
- [x] Step A: `domain-entities.md`の`QueryHistory`レコードに`schema: String`フィールドを追加した
- [x] Step B: `business-rules.md`に新規セクション「2.3 実行対象スキーマの指定・検証」を追加した。
      その過程で、フロントエンドのスキーマ選択UIには`queryexecution`側にスキーマ一覧を列挙する
      API（`listAccessibleSchemas`新規メソッド＋`GET /api/query-execution/{connectionId}/
      schemas`新規エンドポイント）が別途必要であることをFunctional Design時点で新規に識別し、
      2.3節に追記した（Application Designでは明示されていなかった付随的な必要事項。
      `EffectivePermissionResolver`への単純委譲のため`component-dependency.md`自体の追加変更は
      不要）
- [x] Step C: `business-rules.md` 6節（U6↔U7連携）の遷移表・「U6との連携」節にスキーマの
      引き継ぎを追加した
- [x] Step D: `frontend-components.md`の`SavedQueryListPage`/`SavedQuerySaveForm`/
      `QueryExecutionPage`/`QueryHistoryListPage`の責務記述を、グローバル接続コンテキスト参照に
      改訂した
- [x] Step E: `frontend-components.md`の`QueryExecutionPage`にスキーマ選択UIの責務・
      `listAccessibleSchemas`のapi.ts項目を追加した
- [x] Step F: `frontend-components.md`の`QueryHistoryListPage`にスキーマ列表示・遷移時の
      スキーマ引き継ぎを追加した
- [x] Step G: `frontend-components.md`の「U6との連携」節を、U6側が既に実装済みである旨も含めて
      更新した