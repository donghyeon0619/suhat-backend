package com.coders.boardgame.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class CookieConfig {
    private final String domain;

    public CookieConfig(@Value("${server.servlet.session.cookie.domain:suhat.kr}") String domain) {
        this.domain = domain;
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setUseHttpOnlyCookie(true);
        if (!domain.isBlank()) {
            serializer.setDomainName(domain);
        }
        serializer.setSameSite("None"); // CORS 요청 허용
        serializer.setUseSecureCookie(true);
        serializer.setCookiePath("/");
        return serializer;
    }
}
