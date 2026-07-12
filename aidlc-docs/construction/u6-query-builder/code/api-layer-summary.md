# U6 Query Builder - APIレイヤサマリ

Step 5（APIレイヤ生成）・Step 6（APIレイヤ単体テスト）で生成したエンドポイントの一覧。

## エンドポイント一覧

| パス | メソッド | 認可要件 | Controller |
|---|---|---|---|
| `/api/query-builder/{connectionId}/schemas` | GET | 認証必須（ロール制約なし） | `QueryBuilderController#listSelectableSchemas` |
| `/api/query-builder/{connectionId}/schemas/{schema}/tables` | GET | 認証必須（ロール制約なし） | `QueryBuilderController#listSelectableTables` |
| `/api/query-builder/{connectionId}/schemas/{schema}/tables/{table}/columns` | GET | 認証必須（ロール制約なし） | `QueryBuilderController#listSelectableColumns` |
| `/api/query-builder/{connectionId}/generate` | POST | 認証必須（ロール制約なし） | `QueryBuilderController#generate` |
| `/api/query-builder/{connectionId}/parse` | POST | 認証必須（ロール制約なし） | `QueryBuilderController#parse` |

いずれも認可の強制は`SecurityConfig`の`authorizeHttpRequests`に委ね、Controller側での二重チェックはしない。`U1`〜`U4`の管理系エンドポイント（`hasRole("ADMIN")`）と異なり、本ユニットは全ユーザ向け機能のため`.requestMatchers("/api/query-builder/**").authenticated()`（item 5-3で`/api/master-data/**`の直後に追加）を用いる。実際にはこの明示ルールがなくとも末尾の`.anyRequest().authenticated()`で同じ結果になるが、`U5`同様、他の一般ユーザ向けルールと構成を揃えるため明示的に追加した。

## `QueryBuilderController`（`/api/query-builder/{connectionId}`）

### `GET /schemas`（選択可能スキーマ一覧）

ボディなし。`QueryBuilderMetadataService.listSelectableSchemas(userId, connectionId)`（`EffectivePermissionResolver.listAccessibleSchemas`へそのまま委譲）を呼び出す。成功時`200 OK`で`List<String>`:
```json
["public", "sales"]
```

### `GET /schemas/{schema}/tables`（選択可能テーブル一覧）

ボディなし。`QueryBuilderMetadataService.listSelectableTables(userId, connectionId, schema)`を呼び出す。成功時`200 OK`で`List<TableRef>`:
```json
[{"schema": "public", "table": "employees", "comment": "従業員マスタ"}]
```

### `GET /schemas/{schema}/tables/{table}/columns`（選択可能カラム一覧、P1）

ボディなし。`QueryBuilderMetadataService.listSelectableColumns(userId, connectionId, schema, table)`（実効カラム権限が`NONE`のカラムを除外、P1）を呼び出す。成功時`200 OK`で`List<ColumnRef>`:
```json
[{"columnName": "id", "dataType": "INTEGER", "nullable": false}]
```

### `POST /generate`（SQL生成、P2〜P6・P10）

リクエスト（`QueryBuilderModel`、`business-logic-model.md`確定のドメインDTO、`generate`呼び出しにあたり本Controllerでは`userId`を用いず`connectionId`のみ渡す——生成SQLの構文組み立てに権限は関与しないため、`business-rules.md` 8節の設計）:
```json
{"selectItems": [{"tableAlias": "t0", "columnName": "id",
                   "aggregateFunction": "NONE", "outputAlias": null}],
 "fromItem": {"schema": "public", "table": "employees", "alias": "t0"},
 "joinItems": [], "whereConditions": [], "groupByColumns": [],
 "havingConditions": [], "orderByItems": [], "limit": null, "offset": null}
```
`SqlGenerationService.generate(connectionId, model)`を呼び出す。成功時`200 OK`で`GeneratedSql`（`:paramN`プレースホルダとパラメータのマップ、`NamedParameterJdbcTemplate`実行用——実行自体はU7の範囲）:
```json
{"sql": "SELECT \"t0\".\"id\" FROM \"public\".\"employees\" AS \"t0\" WHERE \"t0\".\"id\" = :param1",
 "params": {"param1": 42}}
```
各項目件数が`mm.app.query-builder.max-*`上限を超える場合、またはGROUP BY未指定の非集計SELECT/ORDER BY列がある場合は`ValidationException`（400、P3）。`connectionId`が存在しない場合は`EntityNotFoundException`（404）。

### `POST /parse`（SQL逆解析、P7〜P9）

リクエスト（`SqlParseRequest`、item 5-1で新規生成）:
```json
{"rawSql": "SELECT t0.id FROM public.employees t0 WHERE t0.id = 42"}
```
`SqlParsingService.parse(userId, connectionId, request.rawSql())`を呼び出す。手入力SQLがJSqlParserで解析可能な単純SELECT文（サブクエリ・UNION・CTE・ウィンドウ関数・OR結合・括弧グルーピング不可、P7）であり、かつ参照する全テーブル/カラムに実効READ権限がある場合（P9）のみ`fullyParsed = true`。成功時`200 OK`で`ParseResult`:
```json
{"fullyParsed": true,
 "model": {"selectItems": [...], "fromItem": {...}, "joinItems": [], "whereConditions": [...],
           "groupByColumns": [], "havingConditions": [], "orderByItems": [], "limit": null, "offset": null},
 "notice": null}
```
非対応構文または権限不足の場合は`fullyParsed = false`（`200 OK`のまま、HTTPエラーにはしない設計）:
```json
{"fullyParsed": false, "model": null, "notice": "対応していない構文です（UNION/サブクエリ等）"}
```
`Optional<QueryBuilderModel>`/`Optional<String>`はJacksonのJDK8モジュール（Spring Boot標準搭載）により`present`ラッパーなしで直接値または`null`にシリアライズされる。解析処理はタイムアウト（`mm.app.query-builder.parse-timeout`）付きの共有`ExecutorService`上で実行され、タイムアウト・入力長超過（`mm.app.query-builder.parse-max-length`）時も同様に`fullyParsed = false`で返す（例外を投げない）。

## エラーレスポンス

`GlobalExceptionHandler`（`@RestControllerAdvice`、U1既存＋U2〜U5で追記済み）が全コントローラ共通で以下にマッピングする。本ユニットは新規の例外クラスを追加していない。

| 例外 | HTTPステータス | `error` |
|---|---|---|
| 未認証（`SecurityConfig`のstateless認証チェーンで拒否） | `401 Unauthorized` | （`RestAuthenticationEntryPoint`が`ErrorResponse`を返却） |
| `ValidationException`（`generate`の件数上限超過・GROUP BY制約違反） | `400 Bad Request` | `VALIDATION_ERROR` |
| `EntityNotFoundException`（`connectionId`が存在しない） | `404 Not Found` | `ENTITY_NOT_FOUND` |
| その他未捕捉の`Exception` | `500 Internal Server Error` | `INTERNAL_ERROR`（メッセージは固定文言、詳細はサーバーログにのみ出力） |

エラーボディ共通形状（`ErrorResponse`）:
```json
{"error": "VALIDATION_ERROR", "message": "Too many select items: 150"}
```

**既知の課題**: なし。`U5`の`MutationResult(success=false, ...)`のような「実行時失敗をボディで表現する」設計は、本ユニットでは`parse`の`ParseResult(fullyParsed=false, ...)`が同じ役割を担う（非対応構文・権限不足・タイムアウトいずれも例外を投げず`200 OK`のまま返す、Step 2〜3で確立した設計）。`PermissionDeniedException`（403）は本ユニットでは使用しない——`listSelectableColumns`はP1のとおり権限に基づき選択肢を静かに絞り込むのみで例外を投げず、`parse`の権限不足も同様に`ParseResult`のボディで表現するため（`business-rules.md` 8節）。

## テストカバレッジ（Step 6）

| テストクラス | 検証内容 |
|---|---|
| `QueryBuilderControllerTest` | 5エンドポイント（`listSelectableSchemas`/`listSelectableTables`/`listSelectableColumns`/`generate`/`parse`）それぞれについて認証済みユーザ成功系・未認証401を検証（本ユニットは管理者ロール制約を持たないため403系テストは対象外、example-based、10件） |

P1〜P10（業務ロジックの性質）はController層では再検証せず、`business-logic-summary.md`記載のjqwik `@Property`テスト（`QueryBuilderMetadataServiceTest`/`SqlGenerationServiceTest`/`SqlParsingServiceTest`/`QueryBuilderRoundTripTest`）に一元化している。