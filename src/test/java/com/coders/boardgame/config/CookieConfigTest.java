package com.coders.boardgame.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;

class CookieConfigTest {
    @Test
    void usesConfiguredTestDomain() {
        assertThat(cookieFor("test.example"))
                .contains("Domain=test.example")
                .doesNotContain("suhat.kr")
                .contains("Secure", "HttpOnly", "SameSite=None");
    }

    @Test
    void supportsHostOnlyCookie() {
        assertThat(cookieFor("")).doesNotContain("Domain=");
    }

    private String cookieFor(String domain) {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        new CookieConfig(domain)
                .cookieSerializer()
                .writeCookieValue(
                        new CookieSerializer.CookieValue(request, response, "test-session"));
        return response.getHeader("Set-Cookie");
    }
}
