# End-to-End (E2E) Test Instructions

## Purpose

Complete user workflow testing across the full stack (frontend UI → backend API → internal DB /
target RDBMS), covering the acceptance criteria recorded in
`aidlc-docs/inception/user-stories/stories.md` (MVP-1〜MVP-5, GEN-1〜GEN-16). No E2E framework
is currently installed in this project (`frontend/package.json`has no test runner beyond Vitest,
per `CLAUDE.md`: "No test runner is configured yet" referred to E2E, not the Vitest unit tests
added during Code Generation).

## Recommended Tooling

**Playwright** (`@playwright/test`) is recommended over Cypress for this project:
- First-class TypeScript support, matching the frontend stack
- Can drive the Vite dev server directly (`webServer` config) or a built `dist/` served statically
- Built-in test runner + assertions, no additional framework needed
- Not yet added to `frontend/package.json` — installing it is a scope decision for the user
  (`npm install -D @playwright/test && npx playwright install`)

## Key User Journeys to Script (traceable to user stories)

| # | ジャーニー | 対応ストーリー | ユニット |
|---|---|---|---|
| E2E-1 | メール登録 → 確認メール受信（MailPit）→ パスワード設定 → 管理者承認 → ログイン | MVP-1〜MVP-3 | U1, U2 |
| E2E-2 | 管理者によるRDBMS接続登録 → スキーマ取り込み → グループ作成 → 権限付与 | MVP-4, MVP-5相当 | U1, U3, U4 |
| E2E-3 | 一般ユーザーによるマスタデータ閲覧・フィルタ・編集・新規行追加・削除・一括反映 | 5.5節 マスタデータ | U4, U5 |
| E2E-4 | クエリビルダーでのSQL組み立て → 生成 → 逆解析（貼り付けSQLの反映） | 5.6節 クエリビルダー | U6 |
| E2E-5 | 生成SQLの保存（Public/Private） → 保存クエリ一覧・詳細・編集・廃止 | GEN-10, GEN-12 | U7 |
| E2E-6 | 保存クエリ実行・手入力SQL実行（パラメータ自動検出・ページング） | GEN-11, GEN-13, GEN-14 | U7 |
| E2E-7 | クエリ実行履歴の一覧・絞り込み・再実行/保存/ビルダーで編集への遷移 | GEN-15, GEN-16 | U6, U7 |
| E2E-8 | 監査ログ一覧・絞り込み（管理者専用） | 5.9節 監査ログ | U1 |

E2E-4〜E2E-7は本ユニット（U6・U7）横断のシナリオであり、
`integration-test-instructions.md`のScenario 3・4と対象は重なるが、E2Eテストはブラウザ自動化
（実際のDOM操作・ネットワーク往復）を通す点で異なるレイヤの検証となる。

## Setup

```bash
cd frontend
npm install -D @playwright/test
npx playwright install --with-deps chromium

cd ../devenv
docker compose up -d

cd ../backend
export MM_APP_JWT_SECRET="e2e-test-secret-please-change-32bytes-min"
export MM_APP_RDBMS_CONNECTION_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export MM_APP_ADMIN_BOOTSTRAP_EMAIL="admin@example.com"
export MM_APP_ADMIN_BOOTSTRAP_PASSWORD="ChangeMe123!"
./gradlew bootRun &

cd ../frontend
npm run dev &
```

## Example Playwright Config (未作成、導入時の雛形)

```ts
// frontend/playwright.config.ts
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  use: { baseURL: 'http://localhost:5173' },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
})
```

## Run E2E Tests

```bash
npx playwright test
```

## 本ステージでの実施範囲

Playwright未導入のため、本ステージでは実際のE2Eテストコード生成・実行は行っていない
（フレームワーク導入はこのアプリケーションのテスト戦略における新規の技術選定であり、
`tech-stack-decisions.md`（各ユニットのNFR Requirementsステージ）で扱うべき決定を、
Build and Testステージ内で暗黙に行うべきではないと判断した）。E2E導入を希望する場合は、
上記ジャーニー一覧・設定雛形を出発点として、別途スコープを切って対応することを推奨する。

---

## Contract Tests — N/A

本プロジェクトはSpring Bootバックエンド1つ・Reactフロントエンド1つのモノリシック構成であり、
マイクロサービス間のAPI契約は存在しない（`docs/PROJECT_STRUCTURE.md`）。フロントエンド↔
バックエンド間のAPI契約は、各ユニットのAPIレイヤテスト（`*ControllerTest`、リクエスト/
レスポンスDTOをexample-basedで検証）とフロントエンド側`api.ts`（バックエンドDTOと1:1対応する
TypeScript型定義）の組み合わせで実質的にカバーされているため、Consumer-Driven Contract等の
専用テストは不要と判断した。

## Security Tests — N/A（本ステージでは対象外）

`aidlc-docs/aidlc-state.md`のExtension Configurationにおいて、`security-baseline`拡張は
「コア機能実装後にオプトインする」方針で明示的に無効化されたまま2026-07-06T20:26:00Zから
変更されていない（U1〜U7のいずれのCode Generationステージでも有効化されていない）。
このためBuild and Testステージにおいても`security-baseline`のルール（脆弱性スキャン・
依存関係セキュリティチェック等）は適用対象外とする。認証・認可の基本的な正常系/異常系
（未認証401、権限外403、`ProtectedRoute`によるリダイレクト等）は各ユニットのAPIレイヤ・
フロントエンドテストで個別にexample-basedカバレッジ済みであり、汎用的なセキュリティ観点の
専用テストとは別のレイヤで扱われている。