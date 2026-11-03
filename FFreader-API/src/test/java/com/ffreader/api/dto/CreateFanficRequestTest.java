package com.ffreader.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CreateFanficRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.fanfiction.net/s/14585136/1/For-Her",
            "http://www.fanfiction.net/s/14585136/1/For-Her",
            "https://fanfiction.net/s/14585136",
            "https://www.fanfiction.net/s/14585136/",
            "https://www.fanfiction.net/s/14585136/3"
    })
    void acceptsStoryLinks(String url) {
        assertThat(validator.validate(new CreateFanficRequest(url))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "not a url",
            "ftp://www.fanfiction.net/s/123",
            "https://example.com/s/123",
            "https://www.fanfiction.net.evil.test/s/123",
            "https://www.fanfiction.net/u/123/Author",
            "https://www.fanfiction.net/s/abc",
            "https://www.fanfiction.net/s/123abc",
            "https://www.fanfiction.net/s/"
    })
    void rejectsAnythingElse(String url) {
        assertThat(validator.validate(new CreateFanficRequest(url))).isNotEmpty();
    }
}
