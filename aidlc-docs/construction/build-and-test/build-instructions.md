# Build Instructions

MasterMeister2は`backend/`（Spring Boot、Gradle Kotlin DSL）・`frontend/`（React + TypeScript、
Vite）・`devenv/`（Docker Compose、開発用インフラ）の3モジュール構成。バックエンドとフロント
エンドは独立してビルドされ、実行時はバックエンドが`frontend/dist`のビルド成果物を配信する
構成ではなく、開発時はVite devサーバ、本番はフロントエンドを別途配信する構成を想定する
（`docs/PROJECT_STRUCTURE.md`参照）。

## Prerequisites

- **Build Tool（バックエンド）**: Gradle 9.6（Kotlin DSL、Gradle Wrapper同梱——`./gradlew`を
  使用し、グローバル`gradle`コマンドは使用しない）
- **Build Tool（フロントエンド）**: npm（`package-lock.json`同梱）
- **Java**: 25（LTS）。本環境での実測: OpenJDK 25.0.3（Temurin）
- **Node.js**: 24（LTS）。本環境での実測: v24.14.1
- **依存関係**: `backend/build.gradle.kts`（Spring Boot 4.1 BOMを`dependencyManagement`経由で
  明示インポート）・`frontend/package.json`にすべて記載済み。追加のグローバルインストールは
  不要（Gradle WrapperがGradle本体を自動取得、npmが`node_modules`にローカルインストール）
- **環境変数**: 実行時（`bootRun`/デプロイ）には`MM_APP_JWT_SECRET`・
  `MM_APP_RDBMS_CONNECTION_ENCRYPTION_KEY`等が必須（`application.yml`参照）だが、
  ビルド・単体テストの実行自体には不要（H2組み込みDB・デフォルト値で完結する）
- **システム要件**: 特別な要件なし（開発機で動作確認済み、メモリ・ディスク容量の明示的な
  下限は設定していない）

## Build Steps

### 1. Install Dependencies

```bash
# バックエンド: Gradle Wrapperが初回実行時に自動でGradle本体・依存ライブラリを取得するため、
# 明示的な依存解決コマンドは不要（./gradlew build 実行時に自動で行われる）

# フロントエンド
cd frontend
npm install
```

### 2. Configure Environment

ビルド・単体テストの実行には環境変数の設定は不要（H2組み込みDB・全プロパティのデフォルト値で
完結する）。実行時（`bootRun`やデプロイ）にのみ`application.yml`記載の環境変数を設定する。

### 3. Build All Units

```bash
# バックエンド（コンパイル・単体テスト・実行可能WAR生成を一括実行）
cd backend
./gradlew clean build

# フロントエンド（型チェック・本番ビルド）
cd frontend
npm run build
```

### 4. Verify Build Success

- **Expected Output（バックエンド）**: `BUILD SUCCESSFUL` — `:compileJava`・`:test`・
  `:bootWar`・`:war`・`:check`・`:build`の全タスクが成功する
- **Expected Output（フロントエンド）**: `tsc -b`（型チェック）・`vite build`がいずれも
  エラーなく完了し、`✓ built in ...`が出力される
- **Build Artifacts**:
  - `backend/build/libs/mastermeister.war` — 実行可能WAR（12-factor、環境変数設定）。
    本環境での実測サイズ: 約72MB
  - `backend/build/libs/mastermeister-plain.war` — Spring Boot Loaderを含まないプレーンWAR
    （外部Tomcat等へのデプロイ用、`providedRuntime`構成の副産物）
  - `frontend/dist/` — 静的アセット一式（`index.html`・`assets/*.js`・`assets/*.css`等）
- **Common Warnings**: `PermissionAssignmentServiceTest.java`のunchecked/unsafe操作に関する
  `javac`ノート（`-Xlint:unchecked`指定時のみ詳細表示、ジェネリクス配列生成に起因する既知の
  無害な警告、U4 Code Generation時点から存在）。`Consider enabling configuration cache`は
  Gradleのパフォーマンス提案であり無視してよい

## Docker Compose（開発用インフラ、ビルドには不要）

`devenv/docker-compose.yml`はMailPit・MySQL・MariaDB・PostgreSQLの開発用インスタンスを提供する
（アプリケーション自体のビルドには関与しない）。統合テスト（`integration-test-instructions.md`）
や、対象RDBMSとして手動検証する際に使用する。

```bash
cd devenv
docker compose up -d
# 起動確認
docker compose ps
```

## Troubleshooting

### Build Fails with Dependency Errors

- **Cause**: Gradle/npmのキャッシュ破損、オフライン環境でのネットワーク到達性の問題
- **Solution**: `./gradlew build --refresh-dependencies`（Gradle）、`rm -rf node_modules
  package-lock.json && npm install`（フロントエンド、通常は不要——`package-lock.json`が
  同梱されているため`npm install`のみで再現可能なはず）

### Build Fails with Compilation Errors

- **Cause（バックエンド）**: Java 25構文の使用漏れ、UTF-8以外のソースエンコーディング
  （`JavaCompile`はUTF-8前提で構成済み）
- **Cause（フロントエンド）**: TypeScript型エラー（`tsc -b`が`vite build`より先に実行され、
  型エラーがあればビルド全体が失敗する設計）
- **Solution**: エラーメッセージのファイル・行番号を特定し、該当箇所を修正後に再実行。
  `oxlint`（`npm run lint`）は`vite build`の一部ではないため、型エラーとは別に個別実行が必要

### `MasterMeisterApplicationTests`（Spring Boot ApplicationContext起動テスト）が失敗する

- **Cause**: バックグラウンドで起動中の`./gradlew bootRun`プロセスがファイルベースH2DB
  （`backend/data/mastermeister.mv.db`）をロックしている場合がある（U5 Code Generation時に
  一度発生した事象、コード変更に起因するものではない）
- **Solution**: 稼働中の`bootRun`プロセスを停止してから`./gradlew test`を再実行する