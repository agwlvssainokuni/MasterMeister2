# 変更要求: 接続コンテキストのグローバル化 + クエリ実行時スキーマ指定 — Requirements Clarification Questions

本ドキュメントは、以下の変更要求に関する残存する曖昧点を確認するための質問です。
各質問の[Answer]タグに回答してください。

**背景**: `/saved-queries`等へのナビゲーション直接アクセス時に接続を指定する手段がない不具合を
発端に、(1) 接続選択のグローバルコンテキスト化、(2) クエリ実行時のスキーマ指定と
実行履歴へのスキーマ記録、という2つの変更を行うことで合意しています。以下は実装方針を
確定するための残論点です。

## Question 1
グローバル接続選択（`AppLayout`に常設するセレクタ）の値をどこに永続化しますか？

A) sessionStorage（`authStore`と同様、ブラウザタブを閉じたら消える）

B) localStorage（ブラウザを閉じても次回起動時に前回の接続を復元する）

C) メモリのみ（ページリロードでリセットされる、最も単純）

D) Other (please describe after [Answer]: tag below)

[Answer]: 

## Question 2
グローバル接続を切り替えた際、詳細な作業状態（例: マスタデータのテーブル詳細画面
`/master-data/:connectionId/:schema/:table`、クエリビルダーの編集中モデル）を持つ画面に
いる場合の挙動はどうしますか？

A) 各機能のトップページ（例: `/master-data`, `/query-builder`）へ自動的に戻す

B) 何もせず現在のURLに留まる（各ページ側で「無効な接続/スキーマ」を検知したら個別にエラー表示する）

C) 確認ダイアログを出し、ユーザーが同意したら遷移する

D) Other (please describe after [Answer]: tag below)

[Answer]: 

## Question 3
接続一覧取得APIの実装方針はどうしますか？（現状`masterdata`と`querybuilder`が同一ロジックを
それぞれ専用エンドポイントとして重複実装している）

A) 既存の重複パターンを踏襲し、`savedquery`/`queryexecution`/`queryhistory`にも専用エンドポイントを追加する

B) `rdbmsconnection`パッケージに共通サービス/エンドポイントを1本化し、既存の`masterdata`/`querybuilder`を含む全機能をそこに寄せる（重複解消、`component-dependency.md`更新、影響範囲は広がる）

C) 新規3機能のみ共通の新エンドポイントを使い、`masterdata`/`querybuilder`の既存エンドポイントはそのまま残す（部分的統一）

D) Other (please describe after [Answer]: tag below)

[Answer]: 

## Question 4
`queryexecution`が実行時に指定されたスキーマを許可リスト（アクセス可能なスキーマ一覧）と
突き合わせて検証する必要があります（`SET search_path`にそのまま連結できないため）。
この検証ロジックの実装方針は？

A) `schema`パッケージの既存サービスに新規依存する（`component-dependency.md`のマトリクス更新: `queryexecution → schema`追加）

B) `queryexecution`内で許可リスト検証ロジックを独自実装する（`masterdata`/`querybuilder`の接続一覧取得と同じ重複方針を踏襲）

C) Other (please describe after [Answer]: tag below)

[Answer]: 

## Question 5
MySQL/MariaDB等、スキーマが実質1つしかない接続（`CATALOG_BASED`）の場合、スキーマ選択UIは
どう扱いますか？

A) 選択肢が1つのセレクタを表示する（他ダイアレクトと一貫したUI、操作は不要だが見た目は統一）

B) スキーマが`databaseName`で自動確定するため、UI自体を非表示にして自動セットする

C) Other (please describe after [Answer]: tag below)

[Answer]: 

## Question 6
クエリビルダーで選択中のスキーマを、実行画面（`/query-execution`）や保存フォーム
（`/saved-queries/new`）への遷移時にどう引き継ぎますか？

A) URLクエリパラメータでプリフィルし、遷移先で上書き可能にする（`connectionId`と同じ扱い）

B) 固定値として引き継ぎ、遷移先では変更不可にする

C) Other (please describe after [Answer]: tag below)

[Answer]: 

## Question 7
`QueryHistory`エンティティへの`schema`列追加に伴うDBマイグレーションの扱いは？

A) 本プロジェクトは未リリースで実データがないため、単純に`NOT NULL`列を追加する（`ddl-auto: update`前提、特別なマイグレーションスクリプトは不要）

B) 既存データとの互換のため`NULL`許容にし、アプリ側でNULL時は「不明」として扱う

C) Other (please describe after [Answer]: tag below)

[Answer]: 

## Question 8
既存の`/master-data`・`/query-builder`のページ内接続セレクタは、グローバル接続コンテキスト
導入後どう扱いますか？

A) ページ内セレクタは残しつつ、初期値をグローバル選択から補完する（ページ内で変更した場合はグローバル側にも反映する）

B) ページ内セレクタを廃止し、グローバルセレクタのみで操作する（ページ側は選択済みのconnectionIdを表示するのみ）

C) Other (please describe after [Answer]: tag below)

[Answer]: 