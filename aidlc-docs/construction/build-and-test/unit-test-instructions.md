# Unit Test Execution

各ユニット（U1〜U7）のCode Generationステージで、性質ベース（jqwik `@Property`、
`property-based-testing`拡張のPBT-02〜PBT-08準拠）とexample-based（JUnit 5 / Vitest +
React Testing Library）の単体テストが既に生成済み。本ステージでは、全ユニットを横断して
実際に実行しグリーンであることを最終確認する。

## Run Unit Tests

### 1. Execute All Unit Tests

```bash
# バックエンド（JUnit 5 + jqwik、H2組み込みDB or H2 TCPサーバで完結、外部DB不要）
cd backend
./gradlew test

# フロントエンド（Vitest + React Testing Library）
cd frontend
npx vitest run
```

### 2. Review Test Results

- **Expected**: バックエンド・フロントエンドとも全件成功、0失敗・0エラー
- **本ステージでの実測結果**（`./gradlew clean build`・`npx vitest run`実行時点）:

  | 項目 | 件数 |
  |---|---|
  | バックエンドテストクラス数 | 58 |
  | バックエンドテスト件数 | 299 / 299 成功（0失敗・0エラー） |
  | フロントエンドテストファイル数 | 57 |
  | フロントエンドテスト件数 | 254 / 254 成功 |

- **Test Coverage**: 明示的なカバレッジ計測ツール（JaCoCo等）は導入していない
  （`build.gradle.kts`にプラグイン未追加）。各ユニットのCode Generationステージで
  business-logic-model.mdのP1〜P10全性質にjqwik `@Property`テストが対応していることを
  ユニットごとに確認済みで、これがカバレッジの代替指標となっている
  （詳細は各ユニットの`code/testing-summary.md`）
- **Test Report Location**:
  - バックエンド: `backend/build/reports/tests/test/index.html`（HTML）、
    `backend/build/test-results/test/*.xml`（JUnit XML、CI集計用）
  - フロントエンド: コンソール出力のみ（`npx vitest run --reporter=verbose`で詳細表示、
    HTMLレポートは未導入）

### 3. Fix Failing Tests

If tests fail:
1. バックエンド: `backend/build/reports/tests/test/index.html`または
   `backend/build/test-results/test/*.xml`で失敗したテストクラス・メソッドを特定
2. フロントエンド: `npx vitest run`のコンソール出力（失敗行のスタックトレース）を確認
3. jqwik `@Property`が失敗した場合、出力される`Original Sample`（縮小前）と
   `Shrunk Sample`（縮小後の最小反例）を確認し、プロダクションコードのバグかテスト条件の
   誤りかを切り分ける（U7 Step 3ではこの方式で3件の実装バグを検出・修正した実績がある——
   詳細は`aidlc-docs/construction/u7-saved-query-execution-history/code/
   business-logic-summary.md`）
4. 該当コードを修正し、`./gradlew test --tests "<FQCN>"`または
   `npx vitest run <path>`で対象のみ再実行してから全体を再実行する

## ユニット別内訳（Code Generation時点の記録、参考情報）

各ユニット完了時点の`code/testing-summary.md`に記録された、そのユニットで新規追加された
テスト件数の推移。U1のみCode Generation完了時点では`./gradlew test`全体実行を本ステージまで
据え置いた（コンパイル可能であることのみ確認済み）ため、本ステージの実測値（上表）が
U1分を含む初回の全体実行確認となる。

| ユニット | バックエンド新規 | フロントエンド新規/拡張 | フロントエンド累計 |
|---|---|---|---|
| U1: Platform Foundation | （本ステージで初回実行確認） | （同左） | — |
| U2: Auth & User Registration | 18クラス・68件 | 42件 | 20ファイル・71件 |
| U3: RDBMS Connection & Schema Import | 11クラス・65件 | 43件 | 30ファイル・114件 |
| U4: Permission Management | 11クラス・81件 | 42件 | 40ファイル・156件 |
| U5: Master Data Maintenance | 3クラス・19件 | 27件 | 44ファイル・183件 |
| U6: Query Builder | 5クラス・22件 | 41件 | 52ファイル・224件 |
| U7: Saved Query / Execution / History | 10クラス・43件 | 30件 | 57ファイル・254件 |
| **合計（本ステージ実測）** | **58クラス・299件** | — | **57ファイル・254件** |

（バックエンドのユニット別新規クラス数はU2〜U7各`testing-summary.md`の記載値。U1分は
`GlobalExceptionHandlerTest`等を含む差分として合計値との突き合わせで算出できるが、
本表は各ユニット完了時点の一次記録をそのまま転記したものであり、合計欄は本ステージでの
実測値を正とする。）
