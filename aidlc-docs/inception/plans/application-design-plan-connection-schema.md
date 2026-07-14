# Application Design Plan（変更要求: 接続コンテキストのグローバル化 + クエリ実行時スキーマ指定）

## 方針
`requirements.md` §9・`stories.md` Part 4（CHG-1〜CHG-5）+ 既存ストーリー改訂を踏まえ、
`aidlc-docs/inception/application-design/`配下の既存4ドキュメント
（components.md, component-methods.md, services.md, component-dependency.md,
application-design.md）を、新規セクション追記・該当箇所の改訂という形で更新する
（全面再生成はしない）。

## 実行チェックリスト

- [ ] Step A: `rdbmsconnection`パッケージに「アクセス可能な接続一覧」取得コンポーネント/
      メソッドを追加する設計を確定する
- [ ] Step B: `queryexecution`パッケージのスキーマ許可リスト検証の依存先を確定する
- [ ] Step C: 新規/変更エンドポイントのパスを確定する（`components.md`/`services.md`に反映）
- [ ] Step D: `component-dependency.md`のマトリクスを更新する（新規依存エッジの追加、
      `masterdata`/`querybuilder`の既存重複エンドポイント記載の整理）
- [ ] Step E: `components.md`/`component-methods.md`/`services.md`/`application-design.md`を
      更新する

## 確認事項（[Answer]タグに回答してください）

### Question 1
「アクセス可能な接続一覧」を取得するロジック（現状`masterdata`と`querybuilder`が個別に
重複実装しているもの）を`rdbmsconnection`パッケージに一本化する際、どこに配置しますか？

A) 既存の`RdbmsConnectionService`（現在は管理者向けCRUD・接続テストが責務）に
`listAccessibleConnections(userId)`メソッドを追加する

B) 新規コンポーネント`ConnectionAccessService`として切り出す（`RdbmsConnectionService`は
管理者向けCRUD専任のまま維持し、責務を分離する）

C) Other (please describe after [Answer]: tag below)

[Answer]: 

### Question 2
`queryexecution`がスキーマ許可リスト（`SET search_path`検証用）を取得する依存先について。
Requirements Analysis Q4では「`schema`パッケージへの新規依存」としていましたが、
実際に権限フィルタ済みのアクセス可能スキーマ一覧を提供するコンポーネントは
`permission.EffectivePermissionResolver.listAccessibleSchemas`（`schema.SchemaQueryService`は
権限フィルタなしの生一覧のみ）です。依存先を`permission`パッケージに訂正してよいですか？

A) はい。`queryexecution → permission`という依存エッジで`EffectivePermissionResolver.listAccessibleSchemas`を呼び出す

B) いいえ。別の設計にする（詳細を[Answer]:タグ後に記述してください）

[Answer]: 

### Question 3
新規「アクセス可能な接続一覧」取得エンドポイントのパスをどうしますか？

A) 既存の管理者向けCRUD群と同じベースパスに追加する: `GET /api/rdbms-connections/accessible`
（既存の`GET /api/rdbms-connections`は管理者向け全件取得のまま維持、新設パスは
ログインユーザー向けの権限フィルタ済み一覧）。あわせて`masterdata`の
`/api/master-data/connections`と`querybuilder`の`/api/query-builder/connections`は廃止する

B) 独立した新規ベースパス`GET /api/connections/accessible`を新設する

C) Other (please describe after [Answer]: tag below)

[Answer]: 