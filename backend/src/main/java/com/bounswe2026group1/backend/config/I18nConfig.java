package com.bounswe2026group1.backend.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Wires Spring's message-source + locale-resolver so user-facing error
 * strings produced by services (e.g. {@code RegisteredUserService}) can be
 * resolved against the caller's locale.
 *
 * Locale resolution: read {@code Accept-Language} header, default to English
 * when missing or unsupported. Supported list is kept narrow (en, tr) so an
 * unrecognised header doesn't surprise the user with another fallback.
 *
 * Note: an authenticated user's persisted {@code preferredLanguage} (#505)
 * is currently honoured by clients sending the right {@code Accept-Language}
 * header. If we ever want server-side resolution against the DB column, this
 * is the spot to plug in a custom {@code LocaleResolver}.
 */
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        // Fall back through messages_xx -> messages.properties before failing,
        // so a missing TR key returns the EN string rather than raising.
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("tr")));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
