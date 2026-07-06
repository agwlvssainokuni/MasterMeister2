# システムアーキテクチャ

## システム概要

MasterMeisterは、単一のSpring BootバックエンドがReact SPAを配信する構成（実行可能WAR化のため、フロントエンドのビルド成果物を静的リソースとして組み込む）として計画されている。バックエンドは2種類の異なるデータベースと通信する：自身の運用データ用の内部H2データベース（JPA経由）と、メンテナンス対象のマスタデータを持つ対象RDBMS（コネクションプール上の `NamedParameterJdbcTemplate` 経由）である。本分析時点では、各要素の雛形のみが存在し、コントローラ・エンティティ・フロントエンドの機能コードは一切書かれていない。

## アーキテクチャ図

```mermaid
flowchart TB
    subgraph Client["ブラウザ"]
        SPA["React SPA<br/>(Viteビルド成果物、計画中)"]
    end

    subgraph Backend["backend/ (Spring Boot 4.1、計画中)"]
        Ctrl["コントローラ<br/>(未実装)"]
        Svc["サービス<br/>(未実装)"]
        JPA["JPAリポジトリ<br/>(未実装)"]
        Jdbc["NamedParameterJdbcTemplate<br/>(未実装)"]
    end

    InternalDB[("内部DB: H2<br/>ユーザ、権限、<br/>保存クエリ、監査ログ")]
    TargetDB[("対象RDBMS<br/>MySQL / MariaDB / PostgreSQL / H2")]
    Mail[("SMTP<br/>devenv/のMailPit")]

    SPA -->|"HTTP/JSON (計画中)"| Ctrl
    Ctrl --> Svc
    Svc --> JPA
    Svc --> Jdbc
    JPA --> InternalDB
    Jdbc --> TargetDB
    Svc -.->|"登録・承認メール"| Mail
```

## コンポーネント説明

### backend/
- **目的**: サーバサイドアプリケーション。`docs/REQUIREMENTS.md` に定義された全業務トランザクションを実装する予定
- **責務**: 現状はSpringコンテキストの起動のみ
- **依存関係**: `spring-boot-starter-web`、`spring-boot-starter-test`（テストスコープ）、Java 25 ツールチェーン、Gradle 9.6（Kotlin DSL）で明示的な `dependencyManagement` によるSpring Boot BOMインポート
- **種別**: アプリケーション

### frontend/
- **目的**: SPAクライアント。全機能のUIを実装する予定
- **責務**: 現状はなし — 未改変のVite `react-ts` テンプレート
- **依存関係**: React 19、ReactDOM 19、TypeScript ~6.0、Vite ^8.1、oxlint ^1.71（リントのみ、テストランナー未設定）
- **種別**: アプリケーション（クライアント）

### devenv/
- **目的**: Docker Composeによるローカル開発インフラ
- **責務**: MailPit（SMTP＋WebUI）、MySQL、MariaDB、PostgreSQLコンテナを、`mastermeister` のdb/ユーザ/パスワードでシードして起動する。H2は組み込みのためコンテナ不要
- **依存関係**: アプリケーションコードからの依存はなし。手動／開発時の動作確認のみをサポート
- **種別**: インフラストラクチャ（この環境ではDockerデーモンが利用できず、動作未検証）

## データフロー

コントローラや永続化コードが存在しないため、実装済みの業務フローは図示できない。以下は、`docs/REQUIREMENTS.md` §5.4（マスタデータ編集）を例にした**計画中**のシーケンスであり、参考情報として提示するのみで、未実装である。

```mermaid
sequenceDiagram
    participant U as ユーザ（ブラウザ）
    participant C as コントローラ（計画中）
    participant S as サービス（計画中）
    participant J as NamedParameterJdbcTemplate（計画中）
    participant T as 対象RDBMS

    U->>C: 編集済みレコードを送信（「反映」ボタン1回の操作）
    C->>S: 作成・更新・削除の統一リクエスト
    S->>S: テーブル/カラム権限を確認
    S->>J: すべての変更を単一トランザクションで実行
    J->>T: INSERT/UPDATE/DELETE
    T-->>J: 結果
    J-->>S: 結果
    S-->>C: 処理結果
    C-->>U: 成功/失敗レスポンス
```

## 連携ポイント

- **外部API**: 現状なし
- **データベース**:
  - 内部: H2（JPA）— 未設定（`application.yml` にデータソース設定なし、エンティティなし）
  - 対象: MySQL / MariaDB / PostgreSQL / H2 — 未設定。開発用インスタンスは `devenv/docker-compose.yml` に定義済み
- **サードパーティサービス**: 開発時はMailPit経由のSMTP（`devenv/docker-compose.yml`）。本番環境のSMTPは `docs/REQUIREMENTS.md` §7.3 のとおり環境変数で設定予定

## インフラストラクチャ構成

- **CDKスタック**: なし（AWS CDKプロジェクトではない）
- **デプロイモデル**: `docs/REQUIREMENTS.md` §7.2 に従い、実行可能WAR（Twelve-Factor、環境変数設定）を計画。Dockerパッケージ化を副次的手段とし、将来的にTomcatへのWARデプロイにも対応予定。いずれも未実装
- **ネットワーキング**: 未定義。`devenv/docker-compose.yml` は開発用にMailPit（1025/8025）、MySQL（3306）、MariaDB（3307→3306）、PostgreSQL（5432）をlocalhostにマッピングしている