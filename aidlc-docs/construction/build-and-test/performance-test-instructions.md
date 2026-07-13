# Performance Test Instructions

## Purpose

`docs/REQUIREMENTS.md`・各ユニットのNFR Requirementsは、本アプリケーションを「少数の管理者・
少数の対象RDBMS」を想定した内部マスタデータ管理システムと位置づけており（U3
`nfr-requirements.md`3節、U2`nfr-requirements.md`外部キャッシュ層見送りの根拠等）、
明示的なスループット/同時接続数SLA（例: "X req/秒"、"Y同時ユーザー"）は定義されていない。
そのため本ドキュメントは、大規模負荷テストよりも**各ユニットで確定したタイムアウト値・
上限値が実際の挙動として機能することの検証**を主目的とする。

## Performance Requirements（各ユニットのNFR確定値、SLAではなく設定境界値）

| ユニット | 設定キー | 既定値 | 意味 |
|---|---|---|---|
| U3 | `mm.app.rdbms-connection.pool.connection-timeout` | 5秒 | 対象RDBMSへの接続取得タイムアウト |
| U3 | `mm.app.rdbms-connection.pool.maximum-pool-size` | 5 | 接続あたりのプール上限 |
| U5 | `mm.app.master-data.query-timeout` | 30秒 | マスタデータ一覧取得のSQLタイムアウト |
| U5 | `mm.app.master-data.large-record-threshold` | 100件 | 監査ログ記録の閾値（`LARGE_RECORD_READ`） |
| U5 | `mm.app.master-data.max-mutation-batch-size` | 500件 | 統一ミューテーションAPIの1回あたり上限 |
| U6 | `mm.app.query-builder.parse-timeout` | 5秒 | SQL解析（逆解析）のタイムアウト |
| U6 | `mm.app.query-builder.parse-executor-pool-size` | 4 | 解析用`ExecutorService`のスレッド数 |
| U7 | `mm.app.query-execution.query-timeout` | 30秒 | アドホック/保存クエリ実行のSQLタイムアウト |
| U7 | `mm.app.query-execution.parse-timeout` | 5秒 | 読み取り専用SQL検証（JSqlParser）のタイムアウト |
| U7 | `mm.app.query-execution.max-result-rows` | 1000件 | ページングなし実行時の結果件数上限 |
| U7 | `mm.app.query-history.default-page-size` | 50件 | 履歴一覧の既定ページサイズ |

これらの多くは各ユニットのjqwikプロパティテストで境界値そのものは既に検証済み（例:
U5 `listRecordsRecordsLargeRecordAuditAtThresholdBoundary`、U7
`executeAdhocSqlWithoutPagingTruncatesAtMaxResultRowsBoundary`）。本パフォーマンステストの
価値は、それらの単体テスト（モック/H2組み込みDB）とは異なり、**実際の対象RDBMS
（MySQL/MariaDB/PostgreSQL）・実際のネットワーク越しの応答時間の中でも同じ境界値が
意図どおりに機能するか**を確認する点にある。

## Setup Performance Test Environment

### 1. Prepare Test Environment

```bash
cd devenv
docker compose up -d mysql   # 対象RDBMSとしてMySQLを使用する例
```

大量データでの挙動確認には、`mysql`コンテナに数万〜数十万行規模のテストテーブルを用意する
（`devenv/mysql/init/`にシード用SQLを追加するか、`INSERT ... SELECT`で複製する）。

### 2. Configure Test Parameters

推奨負荷テストツール: [k6](https://k6.io/)（軽量・スクリプトベース、CIに組み込みやすい）。
JMeterでも代替可能。

- **Test Duration**: 5分（クエリタイムアウト境界の確認が主目的のため長時間の負荷維持は不要）
- **Ramp-up Time**: 10秒
- **Virtual Users**: 5〜10（想定利用規模が「少数の管理者・少数の一般ユーザー」であるため、
  数百〜数千同時接続を想定した負荷テストは本アプリケーションの用途に対して過剰）

## Run Performance Tests

### 1. Execute Load Tests

```bash
# 例: k6スクリプト（未作成、以下は雛形）でマスタデータ一覧API・クエリ実行APIに対して
# 5〜10並列でリクエストを送る
k6 run --vus 10 --duration 5m perf/master-data-list.js
k6 run --vus 10 --duration 5m perf/query-execution-adhoc.js
```

（`perf/`ディレクトリ・k6スクリプトは本ステージ時点で未作成。実施する場合は上記の設定キー・
既定値表を参考にシナリオを作成する。）

### 2. Execute Stress Tests

タイムアウト境界の確認を優先する:

- `mm.app.query-execution.query-timeout`（30秒）を意図的に超える重いSQL（例: 大テーブルの
  カルテシアン積JOIN）を実行し、タイムアウトが実際に発火し`SQLTimeoutException`相当が
  適切にハンドリングされること（アプリケーションがハングしないこと）を確認する
- `mm.app.rdbms-connection.pool.maximum-pool-size`（既定5）を超える同時リクエストを送り、
  接続プール枯渇時に`connection-timeout`（既定5秒）で適切に待機・タイムアウトすることを確認する

### 3. Analyze Performance Results

- **Response Time**: 通常時（タイムアウト境界に達しないSQL）は数百ms〜数秒程度を想定
  （明示的な目標値は未定義、実測してベースラインとする）
- **Throughput**: 明示的な目標値なし（少数ユーザー想定のため優先度低）
- **Error Rate**: タイムアウト設定境界を超えた場合のみエラーが発生することを期待（境界内では
  0%）
- **Bottlenecks**: 対象RDBMS側の接続プール（`mm.app.rdbms-connection.pool.*`、既定
  maximum-pool-size=5）が最初に頭打ちになりやすい設計（少数対象RDBMS・少数管理者を想定した
  意図的な既定値、U3 `nfr-requirements.md`3節）。負荷試験で問題が出た場合はまずこの値の
  引き上げを検討する
- **Results Location**: k6実行時は`--out json=results.json`等で出力先を指定する（本プロジェクト
  では未整備）

## Performance Optimization

If performance doesn't meet requirements:
1. 上記「設定境界値」表のうちボトルネックとなっているキーを特定する
2. `application.yml`（または環境変数）で該当値を調整する（コード変更不要、12-factor設計）
3. 再度負荷テストを実行し改善を確認する

## 本ステージでの実施範囲

本ステージでは実際の負荷テスト実行（k6スクリプト作成・実行）は行っていない
（Docker自体は本環境で利用可能だが、専用スクリプト作成・大量テストデータ投入を伴う本格的な
負荷試験は本Build and Testステージの主眼——ビルド成功・全単体テストのグリーン確認——を超える
範囲と判断したため）。上記は実施時の具体的な手順・設定境界値のリファレンスとして整備した。