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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;
    private final String mailFrom;

    public MailService(
            JavaMailSender javaMailSender,
            TemplateEngine templateEngine,
            @Value("${spring.mail.username}") String mailFrom
    ) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.mailFrom = mailFrom;
    }

    public void send(MailNotificationType type, String to, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            String body = templateEngine.process(resolveTemplateName(type), context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(mailFrom);
            helper.setSubject(resolveSubject(type));
            helper.setText(body, true);
            javaMailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send mail: type={}, to={}", type, to, e);
        }
    }

    private String resolveTemplateName(MailNotificationType type) {
        return switch (type) {
            case REGISTRATION_CONFIRMATION -> "mail/registration-confirmation";
            case REGISTRATION_APPROVED -> "mail/registration-approved";
            case REGISTRATION_REJECTED -> "mail/registration-rejected";
        };
    }

    private String resolveSubject(MailNotificationType type) {
        return switch (type) {
            case REGISTRATION_CONFIRMATION -> "【MasterMeister】メールアドレス確認のお願い";
            case REGISTRATION_APPROVED -> "【MasterMeister】登録承認のお知らせ";
            case REGISTRATION_REJECTED -> "【MasterMeister】登録却下のお知らせ";
        };
    }

}