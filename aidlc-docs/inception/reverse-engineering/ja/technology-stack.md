# 技術スタック

## プログラミング言語
- Java - 25（最新LTS、Gradleツールチェーン経由）- バックエンドアプリケーションコード
- Kotlin - （Gradle DSLのみ、`.gradle.kts` ビルドスクリプト）- ビルドスクリプティングのみ、アプリケーションロジックではない
- TypeScript - ~6.0.2 - フロントエンドアプリケーションコード

## フレームワーク
- Spring Boot - 4.1.0（Spring Framework 7ベース）- バックエンドアプリケーションフレームワーク（現状 `spring-boot-starter-web` + `spring-boot-starter-test` のみ使用）
- React - ^19.2.7 - フロントエンドUIフレームワーク（テンプレートのデフォルト。アプリ固有コードはまだなし）

## インフラストラクチャ
- H2 Database - 計画中の内部運用DB（JPA）。まだアプリに組み込まれていない
- MySQL - devenvの `mysql:lts` コンテナ - 対象RDBMS方言の1つ
- MariaDB - devenvの `mariadb:lts` コンテナ（ポート3307→3306）- 対象RDBMS方言
- PostgreSQL - devenvの `postgres:18` コンテナ - 対象RDBMS方言
- MailPit - devenvの `axllent/mailpit:latest` コンテナ - 開発時のメール確認用SMTPキャッチャー＋WebUI

## ビルドツール
- Gradle - 9.6.1（Kotlin DSL、Wrapperコミット済み）- バックエンドビルド
- `io.spring.dependency-management` - 1.1.7 - 明示的なSpring Boot BOMインポート
- Vite - ^8.1.1 - フロントエンドビルド/開発サーバ
- npm - （リポジトリ内でバージョン固定なし）- フロントエンドパッケージ管理
- Docker Compose - （バージョン固定なし。ローカルではCLI v29.6.1 / compose v5.3.0を確認）- devenvのオーケストレーション

## テストツール
- JUnit 5（`spring-boot-starter-test` 経由、JUnit Platform）- バックエンドテスト。コンテキストロードのスモークテストのみ存在
- oxlint - ^1.71.0 - フロントエンドのリントのみ（テストランナーではない。フロントエンドのテストランナーは未設定）