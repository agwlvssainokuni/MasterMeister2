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

package cherry.mastermeister.queryhistory;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@code params}はREST APIのJSONリクエストボディ由来（String/Number/Boolean/null/List/Mapのみ）で
 * あり外部設定（暗号鍵等）に依存しないため、{@code EncryptedStringConverter}と異なりSpring管理Bean
 * にはしない。{@code @DataJpaTest}スライスにはJacksonの{@code ObjectMapper}Beanが含まれず、Spring
 * 管理Beanにすると全エンティティを走査する{@code @DataJpaTest}系テストのコンテキスト起動が失敗する
 * ため。
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize params", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize params", e);
        }
    }

}