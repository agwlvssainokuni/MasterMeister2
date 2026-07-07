# application-design.md — Application Design 統合サマリ

`components.md`・`component-methods.md`・`services.md`・`component-dependency.md` の
統合サマリ。`application-design-plan.md` の確認質問（Question 1〜9）で確定した設計判断を
軸に、コンポーネント・サービス層の高レベル設計を完了する。

## 対象範囲

- コンポーネント（主にサービス層）の責務境界 → `components.md`
- メソッドシグネチャ（入出力の型レベル） → `component-methods.md`
- サービス間のオーケストレーション（主要業務フロー） → `services.md`
- 依存関係・通信パターン・データフロー図 → `component-dependency.md`

詳細な業務ロジック（バリデーション項目の網羅、例外階層の細分化等）は、CONSTRUCTION フェーズの
ユニット単位 Functional Design で確定する。

## 確定した設計判断（Question 1〜9 の回答サマリ）

| # |論点 | 決定 |
|---|---|---|
| Q1/Q2 | 権限モデル・グループ合成・個別上書き | 主権限（なし/R/RU）をスキーマ/テーブル/カラムの3階層で継承・下位優先上書き。補助権限（作成C/削除D）はスキーマ/テーブルの2階層で同様。複数グループ所属時は最も緩い権限を採用。個別ユーザ設定は同じ機構でグループ結果を上書き。 |
| Q9 | 複合主キー・主キー無しテーブル | 複合主キーはAND条件（全構成カラムが条件を満たす必要がある）。主キー無しテーブルは例外的に「作成のみ」補助権限Cのみで許可、削除は常に不可。 |
| Q3 | 権限判定ロジックの配置 | `permission` パッケージの `EffectivePermissionResolver` に一元化（Facade）。他パッケージは直接参照しない。 |
| Q4 | 対象RDBMS接続の伝搬 | 全APIで `connectionId` をパスパラメータとして明示（ステートレス）。JWTには含めない。 |
| Q5 | 監査ログの実装パターン | AOPではなく、各サービス実装内で `AuditLogService.record(...)` を明示的に呼び出す。 |
| Q6 | 方言差異の吸収方法 | `common/dialect/DialectStrategy`（Strategyパターン）に一元化（当初案のパッケージごと `dialect/` から変更）。 |
| Q7 | クエリビルダーの実装方針 | `SqlGenerationService` / `SqlParsingService` の抽象境界のみを定義。実装方式（外部ライブラリ/自前実装）はFunctional Design/NFR Designで決定。 |
| Q8 | 統一マスタ更新APIのエラー粒度 | 全体ロールバック後、`SQLException` 由来の概要メッセージのみを返す（行・カラム単位の詳細は含めない）。 |

## コンポーネント構成の要点

- バックエンドは `docs/PROJECT_STRUCTURE.md` の機能別パッケージ構成
  （`config, common, auth, userregistration, rdbmsconnection, schema, permission, masterdata,
  querybuilder, savedquery, queryexecution, queryhistory, audit, mail`）をそのまま踏襲する。
- 各業務パッケージは、横断的関心事（権限判定・監査ログ・方言吸収）について
  それぞれ単一のFacade的サービス（`EffectivePermissionResolver` / `AuditLogService` /
  `DialectStrategy`）を経由し、内部実装（Repository/Entity）には直接アクセスしない
  （`services.md` 「サービス間依存の原則」）。
- 対象RDBMSへのアクセスはすべて `rdbmsconnection.ConnectionPoolRegistry` が提供する
  接続プール経由の `NamedParameterJdbcTemplate` を用いる（`DriverManager.getConnection()`
  不使用、`docs/REQUIREMENTS.md` 2章の制約に合致）。

## ドキュメント同期（doc-sync）

Question 1/2/9 の回答は、以下の既存ドキュメントに記載されていたモデルを置き換える／具体化する
決定であるため、Application Design完了と合わせて次の更新を行う:

- `aidlc-docs/inception/requirements/requirements.md` 5.2: 権限モデルの記述を
  「テーブルレベル Allow/Deny、カラムレベル なし/R/RU/CRUD」から、確定した3階層モデル
  （主権限 なし/R/RU × スキーマ/テーブル/カラム + 補助権限C/D）に更新する。
- `docs/PROJECT_STRUCTURE.md`: 方言吸収方式を「パッケージごとに `dialect/` サブパッケージ」から
  「`common/dialect/` への一元化（Strategyパターン）」に更新する。

（両ファイルとも本ステージの完了処理の一部として更新する。）

## 未解決として持ち越す論点

- `queryexecution`（手入力SQL・保存クエリ実行）が `permission` パッケージの読み取り権限
  フィルタに依存しない設計となっている点（`component-dependency.md` 注記参照）。
  `stories.md` GEN-13 の受け入れ基準に忠実な設計だが、対象RDBMSの任意テーブル/カラムを
  権限に関わらず読み取れることを意味するため、`security-baseline` 拡張のオプトイン時、
  または後続のNFR Requirements段階で改めて要否を確認することを推奨する。
- 認証トークン設定キー: `mm.app.auth.token-expiry-hours`（デフォルト値は8時間を提案、
  最終値はNFR Designで確定）。

## Units Generation への引き継ぎ

本設計で洗い出したパッケージ（14機能パッケージ）が、後続の Units Generation ステージにおける
ユニット分割の主要な入力となる。特に `permission`（`EffectivePermissionResolver`）は
`masterdata` / `querybuilder` / `queryexecution` の複数ユニットから参照される共有コンポーネント
であるため、ユニット間の実装順序（`permission` を先行させる等）を Units Generation で検討する。