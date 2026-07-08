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

package cherry.mastermeister.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.common.exception.PermissionDeniedException;
import cherry.mastermeister.common.exception.ValidationException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P8（{@code business-rules.md} 3.1の例外→HTTPステータスマッピング、Oracle性質）を検証する
 * プロパティテスト。スタブController経由で4種の例外をそれぞれ投げさせ、
 * {@code GlobalExceptionHandler}が返すHTTPステータス・エラーコードがマッピング表と
 * 常に一致することを検証する。
 */
@JqwikSpringSupport
@WebMvcTest(controllers = GlobalExceptionHandlerTest.StubExceptionController.class)
@Import(GlobalExceptionHandlerTest.StubExceptionController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Property
    void exceptionMappingMatchesBusinessRules(@ForAll("exceptionKinds") ExceptionKind kind) throws Exception {
        mockMvc.perform(get("/test/exceptions/" + kind.name()))
                .andExpect(status().is(kind.expectedStatus()))
                .andExpect(jsonPath("$.error").value(kind.expectedErrorCode()));
    }

    @Provide
    Arbitrary<ExceptionKind> exceptionKinds() {
        return Arbitraries.of(ExceptionKind.class);
    }

    enum ExceptionKind {
        PERMISSION_DENIED(403, "PERMISSION_DENIED"),
        ENTITY_NOT_FOUND(404, "ENTITY_NOT_FOUND"),
        VALIDATION(400, "VALIDATION_ERROR"),
        UNEXPECTED(500, "INTERNAL_ERROR");

        private final int expectedStatus;
        private final String expectedErrorCode;

        ExceptionKind(int expectedStatus, String expectedErrorCode) {
            this.expectedStatus = expectedStatus;
            this.expectedErrorCode = expectedErrorCode;
        }

        int expectedStatus() {
            return expectedStatus;
        }

        String expectedErrorCode() {
            return expectedErrorCode;
        }
    }

    @RestController
    @RequestMapping("/test/exceptions")
    public static class StubExceptionController {

        @GetMapping("/{kind}")
        public void throwException(@PathVariable ExceptionKind kind) {
            switch (kind) {
                case PERMISSION_DENIED -> throw new PermissionDeniedException("denied");
                case ENTITY_NOT_FOUND -> throw new EntityNotFoundException("not found");
                case VALIDATION -> throw new ValidationException("invalid");
                case UNEXPECTED -> throw new IllegalStateException("boom");
            }
        }

    }

}