# component-dependency.md — 依存関係・通信パターン

## 依存関係マトリクス

`✓` は「行のパッケージが列のパッケージのサービスに依存する（呼び出す）」ことを表す。
すべての依存は各パッケージの Facade的サービス経由（`services.md` の「サービス間依存の原則」参照）。

| 依存元 \ 依存先 | common | audit | mail | userregistration | rdbmsconnection | schema | permission | queryhistory | savedquery |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| auth | ✓ | ✓ | | ✓ | | | | | |
| userregistration | ✓ | ✓ | ✓ | | | | | | |
| rdbmsconnection | ✓ | ✓ | | | | | | | |
| schema | ✓ | ✓ | | | ✓ | | | | |
| permission | ✓ | ✓ | | ✓ | | ✓ | | | |
| masterdata | ✓ | ✓ | | | ✓ | ✓ | ✓ | | |
| querybuilder | ✓ | | | | ✓ | ✓ | ✓ | | |
| savedquery | ✓ | | | | | | | | |
| queryexecution | ✓ | ✓ | | | ✓ | | | ✓ | ✓ |
| queryhistory | ✓ | | | | | | | | |
| audit | ✓ | | | | | | | | |
| mail | ✓ | | | | | | | | |
| config | ✓ | | | | | | | | |

**注記（未確定として明記する設計上の留意点）**: `queryexecution`（手入力SQL・保存クエリの実行）は
`stories.md` GEN-13 のAC上「読み取り専用SQLのみ実行可能」という制約のみが明記されており、
`masterdata` のようなテーブル/カラム単位の読み取り権限フィルタは要件上明示されていない。
本設計では要件に忠実に `permission` パッケージへの依存を持たせていないが、これは対象RDBMSの
任意のテーブル/カラムを権限に関わらず読み取れることを意味する。セキュリティ上の論点として、
`security-baseline` 拡張のオプトイン時、または後続のNFR Requirements段階で改めて要否を確認する
ことを推奨する。

---

## 通信パターン

1. **Controller → Service**: 標準的なSpring MVC。Controllerは認証・入力検証・DTO変換のみを担い、
   業務ロジックはServiceに委譲する。
2. **Service → 他パッケージのService**: 直接のJavaメソッド呼び出し（プロセス内、リモート呼び出し
   ではない）。呼び出し先は依存関係マトリクスの列に示すFacade的サービスに限定する。
3. **Service → 対象RDBMS**: `ConnectionPoolRegistry.getJdbcTemplate(connectionId)` で取得した
   `NamedParameterJdbcTemplate` を介してのみアクセスする（`DriverManager.getConnection()` は
   使用しない）。
4. **Service → 内部DB**: Spring Data JPA のRepositoryを介してアクセスする。
5. **対象RDBMS接続の伝搬**: 全REST APIエンドポイントは `connectionId` をパスパラメータとして
   明示的に受け取る（例: `/api/connections/{connectionId}/tables`,
   `/api/connections/{connectionId}/masterdata/{tableId}/records`）。JWTやサーバ側セッションに
   接続情報を保持しない（Question 4 = A）。
6. **横断的関心事の呼び出し**: 監査ログ（`AuditLogService.record(...)`）は、対象操作を行う
   サービスの実装コード内で明示的に呼び出す（AOPは使用しない、Question 5 = B）。

---

## データフロー図1: パッケージ依存関係の全体像

```mermaid
graph TD
    Controller["Controller層 各パッケージ"] --> Service["Service層 各パッケージ"]
    Service --> Permission["permission: EffectivePermissionResolver"]
    Service --> Audit["audit: AuditLogService"]
    Service --> Common["common: DialectStrategy等"]
    MasterData["masterdata"] --> Permission
    MasterData --> Schema["schema: SchemaQueryService"]
    MasterData --> ConnRegistry["rdbmsconnection: ConnectionPoolRegistry"]
    QueryBuilder["querybuilder"] --> Permission
    QueryBuilder --> Schema
    QueryBuilder --> ConnRegistry
    QueryExecution["queryexecution"] --> ConnRegistry
    QueryExecution --> QueryHistory["queryhistory"]
    QueryExecution --> SavedQuery["savedquery"]
    QueryExecution --> Audit
    Schema --> ConnRegistry
    Permission --> Schema
    ConnRegistry --> TargetDB[("対象RDBMS MySQL/MariaDB/PostgreSQL/H2")]
    Schema --> DialectFactory["common/dialect: DialectStrategyFactory"]
```

### Text Alternative（content-validation.md により常時併記）

- Controller層は各パッケージのService層を呼び出す。
- Service層のうち、業務系パッケージ（masterdata, querybuilder, queryexecution 等）は
  横断的関心事として `permission.EffectivePermissionResolver` と `audit.AuditLogService`
  および `common` のユーティリティ（`DialectStrategy` 等）を利用する。
- `masterdata` は権限判定（`permission`）、スキーマ参照（`schema`）、対象RDBMS接続
  （`rdbmsconnection.ConnectionPoolRegistry`）に依存する。
- `querybuilder` も同様に `permission`・`schema`・`rdbmsconnection` に依存する。
- `queryexecution` は `rdbmsconnection`（実行）、`queryhistory`（履歴記録）、`savedquery`
  （保存クエリ取得）、`audit`（監査記録）に依存する。
- `schema` は `rdbmsconnection`（対象RDBMSへの接続）と `common/dialect`（メタデータ取得の
  方言差異吸収）に依存する。
- `permission` は `schema`（テーブル/カラムの参照整合性検証）に依存する。
- `rdbmsconnection.ConnectionPoolRegistry` が最終的に対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）
  へ接続する唯一の経路である。

---

## データフロー図2: 統一マスタ更新API（GEN-4）の処理フロー

```mermaid
sequenceDiagram
    participant FE as フロントエンド
    participant MC as masterdata Controller
    participant MM as MasterDataMutationService
    participant PR as EffectivePermissionResolver
    participant DB as 対象RDBMS
    participant AL as AuditLogService

    FE->>MC: POST /connections/id/masterdata/tableId/mutations
    MC->>MM: applyChanges(connectionId, tableId, userId, request)
    MM->>PR: canCreate / canDelete / resolveEffectiveColumnLevels
    PR-->>MM: 判定結果
    alt 権限NG
        MM-->>MC: PermissionDeniedException
        MC-->>FE: 403エラー応答
        MM->>AL: record(失敗)
    else 権限OK
        MM->>DB: 単一トランザクションでINSERT/UPDATE/DELETE
        alt 実行失敗
            DB-->>MM: SQLException
            MM->>DB: ロールバック
            MM->>AL: record(失敗, 概要メッセージ)
            MM-->>MC: MutationResult(失敗, 概要メッセージ)
        else 実行成功
            DB-->>MM: コミット
            MM->>AL: record(成功)
            MM-->>MC: MutationResult(成功)
        end
        MC-->>FE: 応答
    end
```

### Text Alternative（content-validation.md により常時併記）

1. フロントエンドが `MasterDataMutationService.applyChanges` を単一APIエンドポイントに
   まとめて送信する。
2. `MasterDataMutationService` は `EffectivePermissionResolver` に対して作成・削除・更新の
   各操作の可否を問い合わせる。
3. 権限NGの場合は対象RDBMSへの実行を行わず、`PermissionDeniedException` を返し、
   `AuditLogService` に失敗を記録する。
4. 権限OKの場合、対象RDBMSに対して単一トランザクションで作成・更新・削除を実行する。
5. 実行中に `SQLException` が発生した場合はトランザクション全体をロールバックし、
   概要メッセージのみを含む失敗結果を返す（行・カラム単位の詳細は含めない）。
6. 成功・失敗いずれの場合も `AuditLogService.record(...)` を呼び出して監査ログに記録する。