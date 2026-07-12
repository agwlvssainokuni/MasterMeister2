/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.queryexecution;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;

import cherry.mastermeister.common.exception.ValidationException;

/**
 * GEN-13の読み取り専用検証。U6の{@code SqlParsingService}（{@code QueryBuilderModel}で
 * 表現できる範囲に限定される）は経由せず、JSqlParserを直接利用して{@code Select}型か
 * どうかのみを判定する。
 */
@Component
public class ReadOnlySqlValidator {

    private final ExecutorService executor;
    private final int sqlMaxLength;
    private final Duration parseTimeout;

    public ReadOnlySqlValidator(
            @Value("${mm.app.query-execution.sql-max-length:10000}") int sqlMaxLength,
            @Value("${mm.app.query-execution.parse-timeout:5s}") Duration parseTimeout,
            @Value("${mm.app.query-execution.parse-executor-pool-size:4}") int parseExecutorPoolSize
    ) {
        this.sqlMaxLength = sqlMaxLength;
        this.parseTimeout = parseTimeout;
        this.executor = Executors.newFixedThreadPool(parseExecutorPoolSize);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    public void validate(String sql) {
        if (sql == null || sql.length() > sqlMaxLength) {
            throw new ValidationException("SQLが長すぎるため実行できません");
        }

        Statement statement;
        Future<Statement> future = executor.submit(() -> CCJSqlParserUtil.parse(sql));
        try {
            statement = future.get(parseTimeout.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ValidationException("SQLの解析がタイムアウトしたため実行できません");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("SQLを解析できないため実行できません");
        } catch (Exception e) {
            throw new ValidationException("SQLを解析できないため実行できません");
        }

        if (!(statement instanceof Select)) {
            throw new ValidationException("読み取り専用SQL（SELECT文）のみ実行できます");
        }
    }

}