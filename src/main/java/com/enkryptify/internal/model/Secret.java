package com.enkryptify.internal.model;

import java.util.List;

public record Secret(
        String id,
        String name,
        String note,
        String type,
        String dataType,
        List<SecretValue> values,
        String createdAt,
        String updatedAt
) {
}
