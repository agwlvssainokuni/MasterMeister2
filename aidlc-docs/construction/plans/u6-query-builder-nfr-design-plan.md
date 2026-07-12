# NFR Design Plan — U6: Query Builder

## ユニット適用可否の判定

`construction/nfr-design.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に基づき、
U6は **実行（EXECUTE）** と判定。

- **Logical Components**: `QueryBuilderMetadataService`/`SqlGenerationService`/
  `SqlParsingService`のパッケージ配置・クラス構成が未確定（U4 Q3/U5 Q1と同種の論点）。
- **Performance/Tech Stack**: NFR Requirements Q2で「JSqlParserの`CCJSqlParserUtil`に
  パース処理時間の上限を設定し、`ExecutorService`によるタイムアウト制御を行う」と方針のみ
  確定済みだが、具体的な実装パターン（Executor構成、タイムアウト超過時の扱い——例外化するか
  `ParseResult.fullyParsed = false`として通常応答するか）が未確定。
- **Logical Components/Maintainability**: `SqlParsingService`によるJSqlParser ASTから
  `QueryBuilderModel`への変換方式（Visitorパターン vs 型チェック分岐）が未確定。
- **Scalability/Performance（インデックス）**: 該当なし。U6は内部DBエンティティを一切持たない
  （Functional Design Q1 = A確定）ため、新規インデックス設計は対象外。
- **Security**: 該当なし。SQLインジェクション対策（`:paramN`パラメータ化）・権限フィルタは
  Functional Design段階で確定済み（`business-rules.md` 1, 5.2）。`security-baseline`拡張は
  無効。
- **Resilience**: 該当なし。U6はGEN-8/GEN-9いずれも対象RDBMSへの実行を伴わない
  （`business-rules.md` 7、NFR Requirements 5節で確認済み）ため、U3/U5で論点となった
  「対象RDBMS接続失敗時の障害分離パターン」はU6にはそもそも存在しない。`resiliency-baseline`
  拡張も無効。

→ 上記の中から3問を構成する。

---

## Step 1: NFR Requirements成果物の分析

- [x] `nfr-requirements.md`（1.1 JSqlParser、2.1 入力サイズ/処理時間ガード、
      3.1 メタデータキャッシュ非新設、4.1 リスト件数上限、5. 他NFR領域は新規論点なし）確認
- [x] `tech-stack-decisions.md`（決定事項6件、依存関係追加はJSqlParserのみ）確認
- [x] `functional-design/domain-entities.md`（内部DBエンティティなし、`QueryBuilderModel`
      一式/`GeneratedSql`/`ParseResult`）確認
- [x] `functional-design/business-rules.md`（1.1-1.2 権限フィルタ、2.1 AND結合のみ、
      3.1 JOIN種別、4.1-4.2 集計関数/GROUP BY制約、5.1-5.2 SQL生成方式、
      6.1-6.2 SQL解析方式、7 GEN-8連携範囲、8 API認可）確認
- [x] `functional-design/business-logic-model.md`（フロー1-3、P1〜P10）確認
- [x] `functional-design/frontend-components.md`（SelectTabの全カラム一括追加機能を含む）確認
- [x] U4 `nfr-design-patterns.md`（Question 3のパッケージ配置判断基準）、U5
      `nfr-design-patterns.md`（Question 1のパッケージ配置判断基準・Question 3の
      Resilience判断基準）を前提として参照し、判断基準を揃える

---

## 成果物生成タスク（`construction/nfr-design.md` Step 6）

U1〜U5の先例（`nfr-design-patterns.md`／`logical-components.md`の2ファイルを常に生成）に
倣い、本ユニットも同様とする。

- [x] `aidlc-docs/construction/u6-query-builder/nfr-design/nfr-design-patterns.md`
- [x] `aidlc-docs/construction/u6-query-builder/nfr-design/logical-components.md`

---

## Question 1: Logical Components — 主要サービスのパッケージ配置・クラス構成

`component-dependency.md`で`querybuilder → common, permission, schema, rdbmsconnection`の
依存方向は確定済みだが、`querybuilder`パッケージ内部のクラス構成（サービス分割）が未確定。

A. `QueryBuilderMetadataService`（フロー1: スキーマ/テーブル/カラム選択）・
   `SqlGenerationService`（フロー2: SQL生成）・`SqlParsingService`（フロー3: SQL解析）の
   3サービスに分割し、いずれも`cherry.mastermeister.querybuilder`パッケージに配置する。
   `domain-entities.md`確定済みのDTO群（`TableRef`/`ColumnRef`/`QueryBuilderModel`一式/
   `GeneratedSql`/`ParseResult`等）も同パッケージに配置する。`common`への切り出しは行わない
   （U4 Q3/U5 Q1と同じ判断基準：`querybuilder`が所有する単一実装のサービスであり、複数
   ユニットが実装を追加する拡張ポイントではない。U7からの`querybuilder`パッケージへの依存も
   `unit-of-work-dependency.md`上想定されていない）（推奨）
B. `SqlGenerationService`と`SqlParsingService`を1つの`SqlConversionService`に統合する
   （生成・解析が対になる処理のため）
C. その他（具体的な構成を指定）

[Answer]: A

---

## Question 2: Performance/Tech Stack — SQL解析パーサのタイムアウト制御の実装パターン

NFR Requirements Q2で「JSqlParserにパース処理時間の上限（既定5秒）を設定し、
`ExecutorService`によるタイムアウト制御を行う」と方針決定済みだが、具体的な実装
（Executor構成、タイムアウト超過時の扱い）が未確定。タイムアウト超過を例外として扱うと
GEN-9の「解析できない複雑なSQLの場合は、その旨を表示する」（`business-rules.md` 6.2の
非対応構文検出と同じ扱い）という設計方針と整合しなくなる懸念がある。

A. `SqlParsingService`が保持する固定サイズの共有`ExecutorService`
   （`Executors.newFixedThreadPool(n)`、設定キー`mm.app.query-builder.parse-executor-pool-size`
   既定値`4`）に、`CCJSqlParserUtil.parse(rawSql)`を呼び出す`Callable<Statement>`を
   `submit`する。`Future.get(parseTimeoutSeconds, TimeUnit.SECONDS)`でタイムアウトを待ち、
   `TimeoutException`発生時は`future.cancel(true)`で該当タスクの中断を試みたうえで、
   例外化せず`business-rules.md` 6.2の非対応構文検出と同じ扱いとする——
   `ParseResult(fullyParsed = false, model = Optional.empty(), notice = "解析に時間が
   かかりすぎたため中断しました")`を返す。理由: パースタイムアウトは「悪意ある/病的な入力」に
   起因する構造的な限界であり、構文的な非対応（サブクエリ等）と本質的に同じ「解析不能」
   カテゴリとしてユーザに一貫した体験（エラー画面ではなく通常のnotice表示）を提供できる
   （推奨）
B. タイムアウト超過時は`ValidationException`（またはタイムアウト専用の新規例外）を送出し、
   HTTPエラー応答として扱う（フロントエンド`SqlReverseParsePanel`はエラーメッセージとして
   表示する）
C. `ExecutorService`は`SqlParsingService`のインスタンスフィールドではなく、リクエストの
   たびに`Executors.newSingleThreadExecutor()`で使い捨て生成する
D. その他（具体的な実装パターンを指定）

[Answer]: A

---

## Question 3: Logical Components/Maintainability — JSqlParser ASTから`QueryBuilderModel`への変換方式

`business-rules.md` 6.1で「ASTの`QueryBuilderModel`へのマッピングは自前の変換ロジック」と
確定済みだが、実装パターン（Visitorパターン vs 型チェック分岐）が未確定。JSqlParserは
`SelectVisitor`/`ExpressionVisitorAdapter`等のVisitor基盤クラスを提供している。

A. JSqlParserが提供するVisitor基盤クラス（`ExpressionVisitorAdapter`等）を継承した専用
   Visitorクラス（`WhereConditionVisitor`等、クラス名はCode Generationで確定）を実装し、
   WHERE/HAVING句の式ツリー・SELECT項目・JOIN句をそれぞれ`QueryBuilderModel`のフィールドへ
   変換する。非対応構文（サブクエリ・OR条件・括弧グルーピング等、`business-rules.md` 6.2）に
   遭遇した場合はVisitorメソッド内で変換を中断し、`fullyParsed = false`の判定に反映する
   フラグを立てる。JSqlParserの設計思想（Visitor基盤）に沿い、将来の対応構文拡張時も
   Visitorメソッドの追加で対応できる（推奨）
B. `PlainSelect`等のASTノードに対する`instanceof`による型チェック分岐で変換ロジックを直接
   実装する（Visitor基盤クラスは使用しない）
C. その他（具体的な変換方式を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。