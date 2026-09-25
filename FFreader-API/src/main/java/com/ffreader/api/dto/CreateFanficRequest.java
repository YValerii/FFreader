package com.ffreader.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateFanficRequest(
        @NotBlank(message = "url must not be blank")
        @Pattern(
                regexp = "^https?://(www\\.)?fanfiction\\.net/s/\\d+(/.*)?$",
                message = "url must be a valid fanfiction.net story link, e.g. https://www.fanfiction.net/s/14585136/1/For-Her"
        )
        String url
) {
}
