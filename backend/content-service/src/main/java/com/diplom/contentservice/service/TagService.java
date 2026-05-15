package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.TagCreateRequest;
import com.diplom.contentservice.dto.TagResponse;
import com.diplom.contentservice.entity.Tag;
import com.diplom.contentservice.exception.TagAlreadyExistsException;
import com.diplom.contentservice.exception.TagInUseException;
import com.diplom.contentservice.exception.TagNotFoundException;
import com.diplom.contentservice.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public Page<TagResponse> listTags(Pageable pageable) {
        return tagRepository.findAllByOrderByNameAsc(pageable)
                .map(tag -> new TagResponse(tag.getId(), tag.getName()));
    }

    @Transactional(readOnly = true)
    public Page<TagResponse> searchByPrefix(String prefix, Pageable pageable) {
        String trimmed = prefix.trim();
        if (trimmed.isEmpty()) {
            return listTags(pageable);
        }
        return tagRepository.findAllByNameStartingWithIgnoreCaseOrderByNameAsc(trimmed, pageable)
                .map(tag -> new TagResponse(tag.getId(), tag.getName()));
    }

    @Transactional
    public TagResponse createTag(TagCreateRequest request) {
        String trimmedName = request.name().trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Tag name must not be blank after trimming");
        }
        if (tagRepository.existsByName(trimmedName)) {
            throw new TagAlreadyExistsException("Tag with name '" + trimmedName + "' already exists");
        }
        Tag tag = Tag.builder()
                .name(trimmedName)
                .build();
        tag = tagRepository.save(tag);
        return new TagResponse(tag.getId(), tag.getName());
    }

    @Transactional
    public void deleteTag(UUID tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new TagNotFoundException("Tag not found: " + tagId));
        try {
            tagRepository.delete(tag);
            tagRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new TagInUseException("Tag is referenced by existing posts and cannot be deleted");
        }
    }
}
