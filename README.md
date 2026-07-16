# MasterMeister2

外部 RDBMS に格納されたマスタデータをメンテナンスするための Web アプリケーション（SPA）。
2 段階のユーザ登録（メール確認 + 管理者承認）→ 対象 RDBMS 接続設定・スキーマ取込 → テーブル/カラム単位のアクセス権限設定 → マスタデータの閲覧・編集、クエリビルダーによる SQL 生成・保存・実行、という一連のワークフローを提供する。

詳細な要件は [docs/REQUIREMENTS.md](./docs/REQUIREMENTS.md)、ディレクトリ構成の設計方針は [docs/PROJECT_STRUCTURE.md](./docs/PROJECT_STRUCTURE.md) を参照。

## 技術スタック

| 項目 | 内容 |
|---|---|
| Java | 25 |
| Node.js | 24 |
| バックエンド | Spring Boot 4.1（Gradle 9.6 / Kotlin DSL） |
| フロントエンド | React 19 + TypeScript（Vite） |
| 内部 DB（アプリ運用データ） | H2 Database（JPA でアクセス） |
| 対象 RDBMS（メンテナンス対象） | MySQL / MariaDB / PostgreSQL / H2（`NamedParameterJdbcTemplate` + コネクションプールでアクセス、JPA は使用しない） |
| 認証 | JWT |
| デプロイ形態 | 実行可能 WAR（12-factor / 環境変数設定）。Docker コンテナ化・Tomcat デプロイにも対応 |

対象 RDBMS への接続情報は管理者がアプリ上で設定し、スキーマ取込・権限設定を経てマスタメンテナンス画面から利用する構成のため、上表の「対象 RDBMS」はアプリ自身の実行に必須ではない（開発環境では `devenv/` の Docker Compose で用意する）。

## ディレクトリ構成

```
MasterMeister2/
├── backend/    # Spring Boot アプリケーション（機能別パッケージ: userregistration, rdbmsconnection,
│               #   schema, group, permission, masterdata, querybuilder, savedquery,
│               #   queryexecution, queryhistory, audit, auth, security, mail, common, config）
├── frontend/   # React アプリケーション（backend の機能に対応する features/ 単位で構成）
├── devenv/     # 開発用インフラ（Docker Compose: MailPit, MySQL, MariaDB, PostgreSQL）
├── docs/       # 要件定義・ディレクトリ構成ドキュメント
└── aidlc-docs/ # AI-DLC ワークフローの成果物（設計・監査ログ等）
```

各層の詳細な内訳は [docs/PROJECT_STRUCTURE.md](./docs/PROJECT_STRUCTURE.md) を参照。

## 開発環境のセットアップ

### 1. 開発用インフラの起動

MailPit（メール確認用）と MySQL / MariaDB / PostgreSQL を Docker Compose で起動する（H2 は組込のためコンテナ不要）。

```bash
cd devenv
docker compose up -d
```

- MailPit Web UI: http://localhost:8025
- MySQL: `localhost:3306` / MariaDB: `localhost:3307` / PostgreSQL: `localhost:5432`
- いずれも db/user/password は `mastermeister` で統一

### 2. バックエンド（`backend/`）

Gradle Wrapper を使用する（グローバルの `gradle` は使わない）。

```bash
cd backend
./gradlew bootRun     # アプリ起動
./gradlew build        # ビルド + テスト
./gradlew test          # テストのみ
./gradlew test --tests "cherry.mastermeister.MasterMeisterApplicationTests"  # 単一テストクラス
```

### 3. フロントエンド（`frontend/`）

```bash
cd frontend
npm install
npm run dev       # 開発サーバ起動
npm run build      # 型チェック（tsc -b）+ ビルド（vite build）
npm run lint         # oxlint
npm run test          # vitest run
```

## ライセンス

[Apache License 2.0](./LICENSE)