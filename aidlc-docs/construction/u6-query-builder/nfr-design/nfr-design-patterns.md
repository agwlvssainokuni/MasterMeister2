# nfr-design-patterns.md — U6: Query Builder

`u6-query-builder-nfr-design-plan.md`（Question 1〜3、全回答A）に基づく設計パターン。

---

## 1. Logical Components Patterns

### 1.1 主要サービスのパッケージ配置・クラス構成（Question 1）

- `QueryBuilderMetadataService`（フロー1: スキーマ/テーブル/カラム選択）・
  `SqlGenerationService`（フロー2: SQL生成）・`SqlParsingService`（フロー3: SQL解析）の
  3サービスに分割し、いずれも`cherry.mastermeister.querybuilder`パッケージに配置する。
  `domain-entities.md`確定済みのDTO群（`TableRef`/`ColumnRef`/`QueryBuilderModel`一式/
  `GeneratedSql`/`ParseResult`等）も同パッケージに配置する。
- **依存方向**: `querybuilder → common, permission, schema, rdbmsconnection`
  （`component-dependency.md`確定済み）の一方向のみ。`permission`（`EffectivePermissionResolver`）・
  `schema`（`SchemaQueryService`）側への新規実装追加は行わず、`querybuilder`側から既存サービスを
  呼び出すだけとする。
- **`common`への切り出しは行わない**。理由: U4 Q3・U5 Q1と同じ判断基準——`querybuilder`が所有する
  単一実装のサービスであり、複数ユニットが対等に実装を追加する拡張ポイントではない。
  `unit-of-work-dependency.md`上、U7からの`querybuilder`パッケージへの依存も想定されていない。

### 1.2 JSqlParser ASTから`QueryBuilderModel`への変換方式（Question 3）

- JSqlParserが提供するVisitor基盤クラス（`ExpressionVisitorAdapter`等）を継承した専用Visitor
  クラス群（`querybuilder`パッケージ内、クラス名はCode Generationで確定）を実装し、WHERE/HAVING句
  の式ツリー・SELECT項目・JOIN句をそれぞれ`QueryBuilderModel`のフィールドへ変換する。
- 非対応構文（サブクエリ・OR条件・括弧グルーピング等、`business-rules.md` 6.2）に遭遇した場合は
  Visitorメソッド内で変換を中断し、`fullyParsed = false`の判定に反映するフラグ（`SqlParsingService`
  内部の変換コンテキストが保持する）を立てる。以降のVisitorメソッド呼び出しは中断済みの構造を
  上書きしないよう、フラグが立った時点で残りの変換処理を打ち切る。
- JSqlParserの設計思想（Visitor基盤）に沿うことで、将来の対応構文拡張時もVisitorメソッドの追加
  のみで対応でき、`instanceof`分岐の保守負担（分岐漏れ・型キャストミス）を避けられる。

---

## 2. Performance/Tech Stack Patterns

### 2.1 SQL解析パーサのタイムアウト制御の実装パターン（Question 2）

- `SqlParsingService`が保持する固定サイズの共有`ExecutorService`
  （`Executors.newFixedThreadPool(n)`、設定キー`mm.app.query-builder.parse-executor-pool-size`、
  既定値`4`）に、`CCJSqlParserUtil.parse(rawSql)`を呼び出す`Callable<Statement>`を`submit`する。
- `Future.get(parseTimeoutSeconds, TimeUnit.SECONDS)`（`mm.app.query-builder.parse-timeout`、
  既定値`5`秒、NFR Requirements確定済み）でタイムアウトを待つ。JSqlParserの`parse()`自体は
  同期・ブロッキング呼び出しでありタイムアウト機構を持たないため、別スレッドへの委譲が
  タイムアウト制御実現の前提となる。
- `TimeoutException`発生時は`future.cancel(true)`で該当タスクへの割り込みを試みたうえで、例外化
  せず`business-rules.md` 6.2の非対応構文検出と同じ扱いとする——
  `ParseResult(fullyParsed = false, model = Optional.empty(), notice = "解析に時間がかかりすぎた
  ため中断しました")`を返す。
  - 補足: `cancel(true)`はパース処理スレッドへの割り込み要求であり、JSqlParser内部が割り込みを
    検知するポイントを持つとは限らないため、バックグラウンドのパース処理自体が即座に停止する
    保証はない。呼び出し元の待ち時間を確実に打ち切ることが本パターンの主目的である。
  - 理由: パースタイムアウトは「悪意ある/病的な入力」に起因する構造的な限界であり、構文的な
    非対応（サブクエリ等）と本質的に同じ「解析不能」カテゴリとしてユーザに一貫した体験
    （エラー画面ではなく通常のnotice表示）を提供できる。
- `ExecutorService`は`SqlParsingService`のインスタンスフィールドとしてBean生成時に1回だけ生成し、
  リクエストのたびに使い捨て生成は行わない（スレッド生成コストの回避）。

---

## 3. Scalability/Performance Patterns（インデックス）

- 該当なし。`domain-entities.md`確定済みのとおり、本ユニットは内部DBエンティティを一切持たない
  （`QueryBuilderMetadataService`/`SqlGenerationService`/`SqlParsingService`はいずれも
  `SchemaQueryService`/`EffectivePermissionResolver`への都度委譲、またはJSqlParserによる
  インメモリ処理のみで完結する）ため、新規インデックス設計は対象外
  （`u6-query-builder-nfr-design-plan.md`のユニット適用可否判定を参照）。

---

## 4. Security Patterns

- 該当なし。SQLインジェクション対策（`:paramN`パラメータ化）・権限フィルタは`business-rules.md`
  1、5.2でFunctional Design段階において既に確定済みであり、NFR Requirements・NFR Design
  いずれの段階でも新たな論点は生じていない（`security-baseline`拡張は無効、`aidlc-state.md`）。

---

## 5. Resilience Patterns

- 該当なし。U6はGEN-8/GEN-9いずれも対象RDBMSへの実行を伴わない（`business-rules.md` 7、
  NFR Requirements 5節で確認済み）ため、U3/U5で論点となった「対象RDBMS接続失敗時の障害分離
  パターン」はU6にはそもそも存在しない（`resiliency-baseline`拡張は無効、`aidlc-state.md`）。

---

## 6. PBT適用性（property-based-testing拡張）

- 本ステージ（NFR Design）ではPBT-09（フレームワーク選定）を含むいずれのPBTルールも対象外
  （`property-based-testing.md`のEnforcement Integration表: NFR Designは対象外ステージ）。
  U1〜U5のNFR Design承認時と同様、N/Aとして扱う。