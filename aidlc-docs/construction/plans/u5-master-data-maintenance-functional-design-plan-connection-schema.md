# U5 Functional Design 改訂プラン（変更要求: 接続コンテキストのグローバル化）

## 対象
`requirements.md` §9・`stories.md`（MVP-10改訂）・Application Design（`ConnectionAccessService`
一本化、`GET /api/master-data/connections`廃止）を受けた、U5のFunctional Design
（`frontend-components.md`）への反映。`SchemaTableListPage`のページ内接続セレクタを廃止し、
グローバル接続コンテキスト（U1の`useConnection`）参照に切り替える。`business-rules.md`・
`domain-entities.md`・`business-logic-model.md`のバックエンド側業務ロジックは、
`listAccessibleConnections`（重複ロジック）の削除以外に変更がないため、該当箇所のみ改訂する。

## 質問要否の判定
新たな確認質問は発生しないと判断した。`ConnectionAccessService`への一本化・
`GET /api/master-data/connections`廃止はApplication Design（Q3=B）で確定済み、
ページ内セレクタ廃止はRequirements Analysis（Q8=B）で確定済みであるため。

## 実行チェックリスト
- [x] Step A: `frontend-components.md`の`SchemaTableListPage`の責務記述を改訂した
      （接続はグローバルコンテキストから取得、ページ内選択UIは持たない）
- [x] Step B: N/A —`frontend-components.md`のapi.ts表には`listAccessibleConnections`が
      元々記載されていなかった（U5 Code Generation時の実装時判断として追加されたのみ）ため、
      削除対象なし
- [x] Step C: `frontend-components.md`のルーティング表の`/master-data`の説明を更新した
- [x] Step D: N/A — `business-logic-model.md`に`listAccessibleConnections`への言及なし
      （調査済み、該当なし）
