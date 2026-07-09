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

package cherry.mastermeister.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * P6, P7（business-logic-model.md）を検証するプロパティテスト。
 */
class MailServiceTest {

    // P6: メール送信失敗時も例外を伝播しない
    @Property
    void sendDoesNotPropagateExceptionOnMailSendFailure(
            @ForAll("notificationTypes") MailNotificationType type,
            @ForAll("exceptions") RuntimeException thrown
    ) throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        when(javaMailSender.createMimeMessage()).thenReturn(newMimeMessage());
        doThrow(thrown).when(javaMailSender).send(any(MimeMessage.class));
        MailService mailService = new MailService(javaMailSender, templateEngine(), "noreply@example.com");

        assertThatCode(() -> mailService.send(type, "user@example.com", variablesFor(type)))
                .doesNotThrowAnyException();
    }

    // P7: テンプレート変数が本文に反映され、未解決プレースホルダが残らない
    // 変数値は英小文字のみに限定し、Thymeleafのth:textによるHTMLエスケープが
    // containsチェックを乱さないようにする（意図的な単純化）
    @Property
    void sendRendersAllVariablesWithoutUnresolvedPlaceholders(
            @ForAll("recipientNames") String recipientName,
            @ForAll("linkUrls") String linkUrl
    ) throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = newMimeMessage();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        MailService mailService = new MailService(javaMailSender, templateEngine(), "noreply@example.com");

        mailService.send(
                MailNotificationType.REGISTRATION_APPROVED, "user@example.com",
                Map.of("recipientName", recipientName, "linkUrl", linkUrl));

        String body = (String) mimeMessage.getContent();
        assertThat(body).contains(recipientName);
        assertThat(body).contains(linkUrl);
        assertThat(body).doesNotContain("${");
    }

    // メールテンプレートのライセンス表記（Thymeleafパーサーレベルコメントブロック
    // <!--/* ... */--> で囲んである）が配信メール本文に残らないことを検証する
    @Property
    void sendDoesNotLeakLicenseHeaderIntoMailBody(
            @ForAll("notificationTypes") MailNotificationType type
    ) throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = newMimeMessage();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        MailService mailService = new MailService(javaMailSender, templateEngine(), "noreply@example.com");

        mailService.send(type, "user@example.com", variablesFor(type));

        String body = (String) mimeMessage.getContent();
        assertThat(body).doesNotContain("Copyright");
        assertThat(body).doesNotContain("Apache License");
    }

    private static MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getDefaultInstance(new Properties()));
    }

    // spring-boot-starter-thymeleafの自動設定はSpringTemplateEngine（SpringStandardDialect,
    // SpringEL）を用いるため、テストでも素のTemplateEngine（OGNLベースのStandardDialect、
    // ognl依存が必要）ではなくSpringTemplateEngineを使用し、本番の挙動と一致させる
    private static TemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static Map<String, Object> variablesFor(MailNotificationType type) {
        return switch (type) {
            case REGISTRATION_CONFIRMATION -> Map.of(
                    "recipientName", "taro", "linkUrl", "https://example.com/confirm",
                    "expiryDateTime", "2026-01-01 00:00");
            case REGISTRATION_APPROVED -> Map.of(
                    "recipientName", "taro", "linkUrl", "https://example.com/login");
            case REGISTRATION_REJECTED -> Map.of("recipientName", "taro");
        };
    }

    @Provide
    Arbitrary<MailNotificationType> notificationTypes() {
        return Arbitraries.of(MailNotificationType.class);
    }

    @Provide
    Arbitrary<RuntimeException> exceptions() {
        return Arbitraries.of(
                new RuntimeException("mail server error"),
                new IllegalStateException("illegal state"),
                new MailSendException("send failure"));
    }

    @Provide
    Arbitrary<String> recipientNames() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> linkUrls() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20)
                .map(s -> "https://example.com/" + s);
    }

}