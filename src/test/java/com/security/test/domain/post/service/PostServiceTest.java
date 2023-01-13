package com.security.test.domain.post.service;

import com.security.test.domain.post.dto.request.PostCreateRequest;
import com.security.test.domain.post.entity.Post;
import com.security.test.domain.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {
    @InjectMocks
    PostService postService;

    @Mock
    PostRepository postRepository;

    @Test
    @DisplayName("save post")
    public void save() {
        // given
        Long mockPostId = 1L;
        Post mockPost = getPost();

        ReflectionTestUtils.setField(mockPost, "id", mockPostId);
        given(postRepository.save(any()))
                .willReturn(mockPost);

        // when
        PostCreateRequest postCreateRequest = PostCreateRequest.builder()
                .title("Test Title")
                .content("Test Content")
                .build();
        Post post = postService.save(postCreateRequest);

        // then
        assertThat(post.getId()).isEqualTo(mockPostId);
    }

    @Test
    @DisplayName("get post by id")
    public void findById() {
        // given
        Long mockPostId = 1L;
        Post mockPost = getPost();

        ReflectionTestUtils.setField(mockPost, "id", mockPostId);
        given(postRepository.save(any()))
                .willReturn(mockPost);
        given(postRepository.findById(any(Long.class)))
                .willReturn(Optional.of(mockPost));

        // when
        PostCreateRequest postCreateRequest = PostCreateRequest.builder().build();
        postService.save(postCreateRequest);
        Post post = postService.findById(mockPostId);

        // then
        assertThat(post.getId()).isEqualTo(mockPostId);
    }

    private Post getPost() {
        String title = "Test Title";
        String content = "Test Content";
        return new Post(title, content);
    }
}