# components.md — コンポーネント定義・責務

`requirements.md`・`docs/REQUIREMENTS.md`・`stories.md`・`docs/PROJECT_STRUCTURE.md`、および
`application-design-plan.md` の確認質問（Question 1〜9）への回答に基づく、バックエンドの
コンポーネント（主にサービス層）の一覧。パッケージは `docs/PROJECT_STRUCTURE.md` の機能別
パッケージ構成（`cherry.mastermeister.*`）に従う。各パッケージ内部は
`controller / service / repository / entity / dto` の横割り構成を持つ（`PROJECT_STRUCTURE.md`
既定）ため、本書では Controller・Repository・Entity・DTO は自明なもの以外は個別列挙せず、
Service（および Service に準ずる中心的コンポーネント）を中心に記載する。

各カラムの意味:
- **責務**: そのコンポーネントが担う仕事
- **公開インターフェース**: 他コンポーネント・外部（Controller経由）から見た主な入口
  （詳細な入出力型は `component-methods.md` 参照）

---

## common パッケージ（横断的関心事）

| コンポーネント | 種別 | 責務 | 公開インターフェース |
|---|---|---|---|
| `DialectStrategy` | インターフェース | 対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）ごとの方言差異を吸収する単一の抽象化点（Question 6 = B）。識別子クォート、ページング句生成、NULLソート順、スキーマ/カタログ解釈の差異を吸収する。 | `common/dialect/DialectStrategy` |
| `MySqlDialectStrategy` / `MariaDbDialectStrategy` / `PostgreSqlDialectStrategy` / `H2DialectStrategy` | 実装クラス | `DialectStrategy` の DB種別ごとの実装（Strategyパターン）。 | 同上の実装 |
| `DialectStrategyFactory` | ファクトリ | 対象RDBMS接続の DB種別から対応する `DialectStrategy` 実装を解決する。 | `common/dialect/DialectStrategyFactory` |
| `PageRequest` / `PageResult<T>` | 汎用DTO | ページング要求・結果の共通型。全機能パッケージの一覧系APIで共用。 | `common/dto` |
| 共通例外群（`PermissionDeniedException`, `EntityNotFoundException`, `ValidationException` 等） | 例外 | 機能横断で使う例外階層。`@ControllerAdvice` で共通レスポンス形式に変換。 | `common/exception` |

**設計上の位置づけ**: `docs/PROJECT_STRUCTURE.md` はパッケージごとに `dialect/` サブパッケージを
置く案を提示していたが、Question 6 の回答により `common/dialect/` への一元化に変更する
（`docs/PROJECT_STRUCTURE.md` 側もこの決定に合わせて更新する）。

---

## config パッケージ

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `SecurityConfig` | 設定 | Spring Security のフィルタチェーン構成。JWT認証フィルタの組込、エンドポイントごとの認可（管理者専用API等）。 |
| `JpaConfig` | 設定 | 内部DB（H2, JPA）関連設定。 |
| `MailConfig` | 設定 | メール送信（開発: MailPit、本番: 環境変数経由SMTP）の設定。 |
| `WebConfig` | 設定 | CORS・共通レスポンス変換等のWeb層設定。 |

（対象RDBMSへの接続プール設定は `rdbmsconnection` パッケージの責務。内部DB用の
`DataSource` のみ `config` が扱う。）

---

## auth パッケージ（5.3 ユーザ認証）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `AuthenticationService` | サービス | メールアドレス+パスワードでの認証、JWT発行、ログアウト（監査記録目的の明示的APIコール）を担う。 |
| `JwtTokenProvider` | コンポーネント | JWTの生成・検証・パースを担う。設定可能な有効期限（`mm.app.auth.token-expiry-hours`、デフォルト提案8時間）を参照する。 |
| `AuthenticatedPrincipal` | 値オブジェクト | 認証済みユーザのコンテキスト（userId, role等）。Spring Securityの `Authentication` に格納される。 |

---

## userregistration パッケージ（5.1 ユーザ登録）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `UserRegistrationService` | サービス | メールアドレス登録申請の受付（列挙攻撃対策込み）、確認トークン検証とパスワード設定による登録完了、管理者による承認/却下を担う。 |
| `RegistrationTokenService` | サービス | 登録確認トークンの発行・検証・有効期限管理（`mm.app.user-registration.token-expiry-hours`）。 |

---

## rdbmsconnection パッケージ（5.2 対象RDBMS接続管理）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `RdbmsConnectionService` | サービス | 対象RDBMS接続情報（種別/ホスト/ポート/DB名/認証情報等）のCRUD、接続テストを担う。 |
| `ConnectionPoolRegistry` | コンポーネント | 接続（connectionId）ごとの `DataSource`（コネクションプール、`DriverManager.getConnection()` 不使用）を保持・供給する。`schema` / `masterdata` / `querybuilder` / `queryexecution` から利用される。 |

---

## schema パッケージ（5.2 スキーマ取り込み）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `SchemaImportService` | サービス | 対象RDBMS接続からテーブル/ビュー構造（物理名・コメント・型・制約・主キー構成）を取り込み、内部DBに接続単位で保持する。`DialectStrategy` を介してDB種別ごとのメタデータ取得差異を吸収する。 |
| `SchemaQueryService` | サービス | 取り込み済みスキーマ情報の参照（テーブル一覧、テーブル詳細=カラム一覧・主キー構成等）を提供する。`permission`・`masterdata`・`querybuilder` から参照される。 |

---

## permission パッケージ（5.2 アクセス権限）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `GroupService` | サービス | ユーザグループの作成、ユーザの追加/削除、所属確認を担う（ADM-1）。 |
| `PermissionAssignmentService` | サービス | 主権限（なし/R/RU、スキーマ/テーブル/カラムの3階層）・補助権限（作成C/削除D、スキーマ/テーブルの2階層）の設定・変更、YAMLエクスポート/インポートを担う（MVP-9, ADM-2, ADM-4, ADM-5）。 |
| `EffectivePermissionResolver` | サービス（Facade） | ユーザ・接続・テーブル/カラムを与えて実効権限を解決する中心コンポーネント（Question 3 = A）。グループ合成（最も緩い権限を採用）・個別上書き（同じ階層上書き機構）・主権限の階層継承・補助権限と主キー権限の組合せ判定（作成/削除可否、複合主キーAND条件、主キー無しテーブルの作成のみ例外）を担う。`masterdata` / `querybuilder` / `queryexecution` は本サービスのAPIのみを呼び出し、`permission` の内部エンティティには直接アクセスしない。 |

---

## masterdata パッケージ（5.4 マスタメンテナンス）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `MasterDataQueryService` | サービス | アクセス可能テーブル一覧、レコード一覧（絞り込み・ソート・ページング）を提供する。`EffectivePermissionResolver` によるテーブル/カラム読み取り権限フィルタを適用する。手入力WHERE/ORDER BYはカラム権限フィルタの対象外（GEN-2、設計上の意図的例外）。 |
| `MasterDataMutationService` | サービス | 作成・更新・削除を単一トランザクションで処理する統一APIの実体（GEN-4）。実行前に `EffectivePermissionResolver` で各操作の可否（更新: 対象カラムがRU以上か、作成: 補助権限C+主キー列RU、削除: 補助権限D+主キー列R以上）を検証する。失敗時は全体ロールバックし、`SQLException` 由来の概要メッセージのみを返す（Question 8 = B）。 |

---

## querybuilder パッケージ（5.5 クエリビルダー）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `QueryBuilderMetadataService` | サービス | FROM/JOINタブでのテーブル選択、他タブでのアクセス可能カラム一覧提供（`EffectivePermissionResolver` 経由の読み取り権限フィルタ適用）。 |
| `SqlGenerationService` | サービス（抽象境界のみ、Question 7 = C） | クエリビルダーの指定内容（SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSET）からSQLを生成する。実装方式（外部パーサライブラリ利用か自前実装か）はFunctional Design/NFR Designで決定する。 |
| `SqlParsingService` | サービス（抽象境界のみ、Question 7 = C） | 手入力SQLを解析し、クエリビルダーの各タブのモデルへ逆変換する（GEN-9）。解析不能な複雑SQLの検知・通知を含む。実装方式は同上、後続段階で決定する。 |

---

## savedquery パッケージ（5.6 クエリ保存）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `SavedQueryService` | サービス | SQL（手入力またはクエリビルダー生成）への名前付け保存、公開/非公開設定、一覧・詳細取得（可視性チェック）、作成者のみに限定した編集を担う（GEN-10, GEN-11, GEN-12）。 |

---

## queryexecution パッケージ（5.7 クエリ実行）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `QueryExecutionService` | サービス | 手入力SQL・保存クエリの実行を担う。読み取り専用SQLのみ許可する簡易検証（DML/DDLキーワード検知等）、`:param` 形式パラメータの自動検出と `NamedParameterJdbcTemplate` へのバインド、ページング制御を行う。実行のたびに `QueryHistoryService.recordExecution` と `AuditLogService.record` の両方を明示的に呼び出す。 |

---

## queryhistory パッケージ（5.8 クエリ履歴）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `QueryHistoryService` | サービス | クエリ実行履歴の記録（SQL・パラメータ・結果件数・実行時間・実行回数・実行日時・実行者）、一覧・絞り込み（実行日時範囲・実行者・SQLテキスト検索）を担う。`queryexecution` から呼び出される。 |

---

## audit パッケージ（6章 監査ログ）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `AuditLogService` | サービス | 監査イベントの記録（`record(...)` の明示呼び出し、Question 5 = B）、管理者向けの監査ログ検索・絞り込みを担う。閲覧は管理者ロールに限定（アプリケーションレベルのRBAC。対象RDBMSの権限モデルとは別軸）。 |

---

## mail パッケージ

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `MailService` | サービス | 登録確認メール、承認/却下結果通知メールの送信を担う。開発環境はMailPit、本番はSMTP環境変数設定を使用。 |

---

## パッケージ横断の設計方針まとめ

- **対象RDBMS接続の伝搬（Question 4 = A）**: 全APIは `connectionId` をパス（例:
  `/api/connections/{connectionId}/...`）で明示的に受け取るステートレス設計とする。JWTには
  接続情報を含めない。
- **権限判定の一元化（Question 3 = A）**: `EffectivePermissionResolver` のみが権限判定ロジックを
  持ち、他の機能パッケージは同サービスを呼び出すのみとする。
- **監査ログの実装パターン（Question 5 = B）**: AOPは使わず、各サービスの実装コード内で
  `AuditLogService.record(...)` を明示的に呼び出す。
- **方言吸収（Question 6 = B）**: `common/dialect/DialectStrategy` に一元化する。
