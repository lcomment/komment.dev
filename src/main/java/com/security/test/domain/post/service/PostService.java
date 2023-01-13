package com.security.test.domain.post.service;

import com.security.test.domain.post.dto.request.PostCreateRequest;
import com.security.test.domain.post.entity.Post;
import com.security.test.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;

    @Transactional
    public Post save(PostCreateRequest postCreateRequest) {
        return postRepository.save(new Post(postCreateRequest.title(), postCreateRequest.content()));
    }

    public Post findById(Long id) {
        return postRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    }

    public List<Post> findAll() {
        return postRepository.findAll();
    }
}
