# U3 Functional Design 改訂プラン（変更要求: 接続コンテキストのグローバル化）

## 対象
`requirements.md` §9・Application Design（`ConnectionAccessService`新設、`GET
/api/rdbms-connections/accessible`新設）を受けた、U3のFunctional Design（`business-logic-
model.md`、`business-rules.md`、`frontend-components.md`）への反映。`domain-entities.md`は
新規エンティティ・エンティティ変更がないため対象外。

## 質問要否の判定
Application Designで以下が既に確定済みのため、新たな確認質問は発生しないと判断した:
- コンポーネント配置（`ConnectionAccessService`、`rdbmsconnection`パッケージ内）
- 依存関係（`rdbmsconnection → permission`、`EffectivePermissionResolver.listAccessibleSchemas`
  を利用）
- エンドポイントパス（`GET /api/rdbms-connections/accessible`）
- フィルタ基準（接続ごとに`listAccessibleSchemas`が1件以上返せば「アクセス可能」）

唯一の実装判断（Functional Designレベルの新規事項）は、この新規エンドポイントの認可要件が
既存のU3内エンドポイント（すべて`hasRole("ADMIN")`）と異なり、**認証済みの全ユーザー**
（管理者・一般ユーザーともに）がアクセスできる必要がある点。これは`requirements.md` §9・
`stories.md` CHG-1（「ユーザーとして」＝ペルソナ限定なし）から自明に導かれるため、
質問とせず本プランに直接記載する。

## 実行チェックリスト
- [ ] Step A: `business-rules.md`に「1.7 アクセス可能な接続一覧の取得」を追加し、
      フィルタ基準・認可要件（`hasRole("ADMIN")`ではなく認証済み全ユーザー）を明記する
- [ ] Step B: `business-rules.md` 4章のAPI認可表に新規エンドポイントを追加する
- [ ] Step C: `business-logic-model.md`に新規フロー6「アクセス可能な接続一覧の取得」を追加する
- [ ] Step D: `frontend-components.md`に`listAccessibleConnections()`のapi.ts定義を追加し、
      呼び出し元がU1の`AppLayout`（本ユニットの画面ではない）である旨を明記する