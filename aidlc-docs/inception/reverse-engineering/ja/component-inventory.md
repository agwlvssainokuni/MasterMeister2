# コンポーネント一覧

## アプリケーションパッケージ
- `backend`（`cherry.mastermeister`）- Spring Bootバックエンドアプリケーションの雛形。機能パッケージは未実装
- `frontend` - React + TypeScript SPAクライアント（未改変のViteテンプレート）

## インフラストラクチャパッケージ
- `devenv` - ローカル開発環境用Docker Compose（MailPit、MySQL、MariaDB、PostgreSQL）

## 共有パッケージ
- 現状なし（バックエンドに `common/`、`config/` などの共有ユーティリティなし。フロントエンドに `components/`、`hooks/`、`api/`、`store/`、`types/` ディレクトリなし）

## テストパッケージ
- `backend/src/test/java/cherry/mastermeister` - Springコンテキストロードテスト1つ（`MasterMeisterApplicationTests`）
- フロントエンドのテストパッケージなし（テストランナー未設定）

## 総数
- **総パッケージ数**: 3（backend、frontend、devenv）
- **アプリケーション**: 2（backend、frontend）
- **インフラストラクチャ**: 1（devenv）
- **共有**: 0
- **テスト**: 1（backendのテストソースセット。frontendはなし）