package com.security.test.domain.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.test.domain.post.dto.request.PostCreateRequest;
import com.security.test.domain.post.entity.Post;
import com.security.test.domain.post.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(PostController.class)
class PostControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostService postService;

    @Test
    @DisplayName("GET/ post by id")
    public void postDetail() throws Exception {
        // given
        Long mockPostId = 1L;
        Post mockPost = getPost("test title", "test content");

        given(postService.findById(anyLong()))
                .willReturn(mockPost);


        // when
        ResultActions result = this.mockMvc.perform(
                get("/api/posts/{id}", mockPostId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
        );

        // then
        result.andExpect(status().isOk()).andDo(print());
    }

    @Test
    @DisplayName("GET/ get postList")
    public void postList() throws Exception {
        // given
        List<Post> mockPostList = List.of(
                getPost("test title1", "test content1"),
                getPost("test title2", "test content2"),
                getPost("test title3", "test content3"));

        given(postService.findAll())
                .willReturn(mockPostList);

        // when
        ResultActions result = this.mockMvc.perform(
                get("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
        );

        // then
        result.andExpect(status().isOk()).andDo(print());
    }

    @Test
    @DisplayName("")
    public void postAdd() throws Exception {
        // given
        PostCreateRequest postCreateRequest = getPostCreateRequest();
        Post mockPost = getPost("test title", "test content");

        given(postService.save(any()))
                .willReturn(mockPost);

        // when
        ResultActions result = this.mockMvc.perform(
                post("/api/posts")
                        .content(objectMapper.writeValueAsString(postCreateRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isOk()).andDo(print());
    }

    private Post getPost(String title, String content) {
        return new Post(title, content);
    }

    private PostCreateRequest getPostCreateRequest() {
        return PostCreateRequest.builder()
                .title("Test Title")
                .content("Test Content")
                .build();
    }
}