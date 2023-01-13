package com.security.test.domain.post.controller;

import com.security.test.domain.post.dto.request.PostCreateRequest;
import com.security.test.domain.post.dto.response.PostResponse;
import com.security.test.domain.post.entity.Post;
import com.security.test.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> postDetail(@PathVariable Long id) {
        return ResponseEntity.ok(postService.findById(id).toPostResponse());
    }

    @GetMapping
    public ResponseEntity<List<String>> postList() {
        return ResponseEntity.ok(postService.findAll().stream()
                .map(Post::getTitle)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<PostResponse> postAdd(@RequestBody PostCreateRequest postCreateRequest) {
        return ResponseEntity.ok(postService.save(postCreateRequest).toPostResponse());
    }
}
