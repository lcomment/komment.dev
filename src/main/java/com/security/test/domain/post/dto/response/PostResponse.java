package com.security.test.domain.post.dto.response;

import lombok.Builder;

public record PostResponse(String title, String content) {
    @Builder
    public PostResponse { }
}
