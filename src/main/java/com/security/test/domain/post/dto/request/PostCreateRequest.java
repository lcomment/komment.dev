package com.security.test.domain.post.dto.request;

import lombok.Builder;

public record PostCreateRequest(String title, String content) {
    @Builder
    public PostCreateRequest { }
}
