package com.swiftpay.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Page shape from API_SPEC.md (no Spring Data internals leak into the API). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
