# unit-of-work.md — ユニット定義・責務

`unit-of-work-plan.md`（Question 1〜5、すべて回答A = 提案どおり）に基づき確定した
7ユニットの定義。本プロジェクトは単一の Spring Boot WAR + 単一 React SPA として
デプロイされるモノリスであるため、ここでの「ユニット」は独立デプロイ可能な
「サービス」ではなく、CONSTRUCTION フェーズで1つずつ完結させる（Functional Design →
NFR Requirements → NFR Design → Code Generation）*開発順序と検証範囲を区切るための
論理単位*（"Module"/"Unit of Work"、`units-generation.md` 用語定義）である。

各ユニットの「主要コンポーネント」列は `components.md` の該当パッケージ節から抜粋。

---

## U1: Platform Foundation

- **バックエンドパッケージ**: `common`（`common/dialect` 含む）, `config`, `audit`, `mail`
- **フロントエンド`features/`外の共通基盤**: `components/`, `api/`, `hooks/`, `store/`, `routes/`
- **フロントエンド`features/`**: `auditLog/`
- **対応ストーリー**: ADM-6（監査ログの閲覧・絞り込み）
- **責務**: 他の全ユニットが横断的に利用する基盤機能を提供する。
  - 対象RDBMS方言差異の吸収（`DialectStrategy` / `DialectStrategyFactory`、Question 6 = B）
  - 共通DTO（`PageRequest`/`PageResult<T>`）・共通例外階層
  - Spring Security・JPA・メール・Web層の設定（`SecurityConfig`, `JpaConfig`, `MailConfig`, `WebConfig`）
  - 監査イベントの記録・検索（`AuditLogService.record(...)` / `.search(...)`、Question 5 = B、
    AOP不使用・各サービスからの明示呼び出し）
  - メール送信（登録確認・承認/却下通知、`MailService`）
  - フロントエンド共通基盤（APIクライアント、共通フック、状態管理、ルーティング）
- **主要コンポーネント**: `DialectStrategy`系, `PageRequest`/`PageResult<T>`, 共通例外群,
  `SecurityConfig`, `JpaConfig`, `MailConfig`, `WebConfig`, `AuditLogService`, `MailService`
- **他ユニットへの依存**: なし（最基盤ユニット）
- **備考（Question 5 = A で確定）**: `AuditLogService.record` は全ユニットが着手当初から
  利用するため本ユニットに含めるが、`search` + `auditLog/` 画面（ADM-6、Part 2ストーリー）を
  含めて U1 で一括して構築する（記録機能と閲覧UIを分離しない、提案どおり採用）。

---

## U2: Auth & User Registration

- **バックエンドパッケージ**: `auth`, `userregistration`
- **フロントエンド`features/`**: `auth/`, `userRegistration/`
- **対応ストーリー**: MVP-1, MVP-2, MVP-3, MVP-4, MVP-5, MVP-6
- **責務**: メールアドレス起点の2段階ユーザ登録（申請→確認→パスワード設定→管理者承認/却下）と、
  承認済みユーザの認証（JWT発行）を担う。
  - 登録申請受付（列挙攻撃対策込み）、確認トークンの発行・検証・有効期限管理
  - 管理者による登録完了ユーザ一覧確認・承認/却下
  - 承認/却下結果のメール通知（`mail` パッケージ経由）
  - メールアドレス+パスワードでの認証、JWT発行・検証、ログアウト
- **主要コンポーネント**: `UserRegistrationService`, `RegistrationTokenService`,
  `AuthenticationService`, `JwtTokenProvider`, `AuthenticatedPrincipal`
- **他ユニットへの依存**: U1（`common`, `audit`, `mail`）

---

## U3: RDBMS Connection & Schema Import

- **バックエンドパッケージ**: `rdbmsconnection`, `schema`
- **フロントエンド`features/`**: `rdbmsConnection/`, `schema/`
- **対応ストーリー**: MVP-7, MVP-8, ADM-3
- **責務**: 対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）への接続情報管理と、接続先からの
  スキーマ（テーブル/ビュー構造）取り込み・参照を担う。
  - 接続情報CRUD・接続テスト、複数接続の独立管理（ADM-3）
  - 接続ごとのコネクションプール（`DriverManager.getConnection()` 不使用）の保持・供給
  - `DialectStrategy` を介したDB種別ごとのメタデータ取得差異吸収を伴うスキーマ取り込み
  - 取り込み済みスキーマ情報の参照（権限フィルタなしの生の一覧）
- **主要コンポーネント**: `RdbmsConnectionService`, `ConnectionPoolRegistry`,
  `SchemaImportService`, `SchemaQueryService`
- **他ユニットへの依存**: U1（`common`, `audit`）

---

## U4: Permission Management

- **バックエンドパッケージ**: `group`, `permission`
- **フロントエンド`features/`**: `group/`, `permission/`
- **対応ストーリー**: MVP-9, ADM-1, ADM-2, ADM-4, ADM-5
- **責務**: テーブル/カラム単位のアクセス権限（ユーザ個別・グループ）の設定と、実効権限の解決を担う。
  - ユーザグループの作成・所属管理（ADM-1、`group`パッケージ）
  - 主権限（なし/R/RU、スキーマ/テーブル/カラムの3階層）・補助権限（作成C/削除D、
    スキーマ/テーブルの2階層）の設定・変更、YAMLエクスポート/インポート（MVP-9, ADM-2, ADM-4,
    ADM-5、`permission`パッケージ）
  - 実効権限解決（`EffectivePermissionResolver`）— グループ合成・個別上書き・階層継承・
    補助権限と主キー権限の組合せ判定、アクセス可能スキーマ/テーブル一覧フィルタ。
    U5/U6/U7から共通利用される中心的Facade（Question 3 = A、`permission`パッケージ）
  - `permission`は`group`に依存する一方向の依存関係（principalId=GROUPの実在チェック用、
    Functional Design Q3で確定）。U2/U3（`auth`/`userregistration`, `rdbmsconnection`/`schema`）
    と同様、1ユニット内で対応ストーリーの毛色に応じてパッケージを分割する構成
    （U4 Functional Designで訂正、当初のApplication Designでは単一パッケージだった）。
- **主要コンポーネント**: `GroupService`（`group`）, `PermissionAssignmentService`,
  `EffectivePermissionResolver`（`permission`）
- **他ユニットへの依存**: U1（`common`, `audit`）, U2（`userregistration`）, U3（`schema`）

---

## U5: Master Data Maintenance

- **バックエンドパッケージ**: `masterdata`
- **フロントエンド`features/`**: `masterData/`
- **対応ストーリー**: MVP-10, MVP-11, GEN-1, GEN-2, GEN-3, GEN-4, GEN-5
- **責務**: 一般ユーザ向けのマスタデータ閲覧・編集を担う。
  - アクセス可能スキーマ/テーブル一覧、レコード一覧（絞り込み・ソート・ページング、
    UI操作/手入力WHERE・ORDER BY両対応、手入力時はカラム権限フィルタ対象外＝GEN-2の
    設計上の意図的例外）
  - 作成・更新・削除を単一トランザクションで処理する統一API（GEN-4、`MasterDataMutationService`）
  - 実行前の権限検証（更新: 対象カラムRU以上、作成: 補助権限C+主キー列RU、
    削除: 補助権限D+主キー列R以上）、失敗時ロールバック・概要メッセージのみ返却
- **主要コンポーネント**: `MasterDataQueryService`, `MasterDataMutationService`
- **他ユニットへの依存**: U1（`common`, `audit`）, U3（`rdbmsconnection`, `schema`）,
  U4（`permission`）

---

## U6: Query Builder

- **バックエンドパッケージ**: `querybuilder`
- **フロントエンド`features/`**: `queryBuilder/`
- **対応ストーリー**: GEN-6, GEN-7, GEN-8, GEN-9
- **責務**: GUIによるSQL組み立てと、既存SQLのクエリビルダーへの逆変換を担う。
  - スキーマ選択、FROM/JOINタブでのテーブル・エイリアス選択、他タブでのアクセス可能
    カラム一覧提供（権限フィルタ適用）
  - SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSET指定からのSQL生成
  - 手入力SQLの解析とクエリビルダー各タブモデルへの逆変換（GEN-9、解析不能な複雑SQLの検知）
- **主要コンポーネント**: `QueryBuilderMetadataService`, `SqlGenerationService`
  （実装方式は Functional Design/NFR Design で決定）, `SqlParsingService`（同上）
- **他ユニットへの依存**: U1（`common`）, U3（`rdbmsconnection`, `schema`）, U4（`permission`）

---

## U7: Saved Query / Execution / History

- **バックエンドパッケージ**: `savedquery`, `queryexecution`, `queryhistory`
- **フロントエンド`features/`**: `savedQuery/`, `queryExecution/`, `queryHistory/`
- **対応ストーリー**: GEN-10, GEN-11, GEN-12, GEN-13, GEN-14, GEN-15, GEN-16
- **責務**: SQL（手入力またはクエリビルダー生成）の保存・実行・履歴管理を担う。
  - SQLへの名前付け保存、公開/非公開設定、一覧・詳細取得、作成者限定編集（GEN-10〜12）
  - 手入力SQL・保存クエリの実行。読み取り専用SQLのみ許可する簡易検証、`:param` 形式
    パラメータの自動検出・バインド、ページング制御（GEN-13, GEN-14）
  - 実行のたびに履歴記録（`QueryHistoryService.recordExecution`）と監査記録
    （`AuditLogService.record`）を明示的に呼び出す
  - 実行履歴の記録・一覧・絞り込み（GEN-15）、履歴からの画面遷移（GEN-16）
- **主要コンポーネント**: `SavedQueryService`, `QueryExecutionService`, `QueryHistoryService`
- **他ユニットへの依存**: U1（`common`, `audit`）, U3（`rdbmsconnection`）
- **設計上の留意点（`component-dependency.md` 注記を継承）**: `queryexecution` は
  `permission` パッケージに依存しない設計（要件上、読み取り専用SQL制約のみが明記され、
  テーブル/カラム単位の読み取り権限フィルタは課されていない）。したがって技術的な
  パッケージ依存としては U4（Permission）は必須ではないが、`savedquery`/`queryhistory`
  が保存・参照するSQL（クエリビルダー生成SQL等）がU5/U6の成果物であるという
  ワークフロー上の関係から、承認済みビルド順序（Question 2 = A）では最終段に置く。

---

## ユニット構成サマリ表

| ユニット | バックエンドパッケージ | フロントエンド`features/` | ストーリー数 |
|---|---|---|---|
| U1: Platform Foundation | common, config, audit, mail | (共通基盤) + auditLog/ | 1（+全ストーリー横断の監査記録AC） |
| U2: Auth & User Registration | auth, userregistration | auth/, userRegistration/ | 6 |
| U3: RDBMS Connection & Schema Import | rdbmsconnection, schema | rdbmsConnection/, schema/ | 3 |
| U4: Permission Management | group, permission | group/, permission/ | 5 |
| U5: Master Data Maintenance | masterdata | masterData/ | 7 |
| U6: Query Builder | querybuilder | queryBuilder/ | 4 |
| U7: Saved Query / Execution / History | savedquery, queryexecution, queryhistory | savedQuery/, queryExecution/, queryHistory/ | 7 |

合計: 1 + 6 + 3 + 5 + 7 + 4 + 7 = 33 ストーリー（全ストーリー割当済み、詳細は
`unit-of-work-story-map.md` 参照）。