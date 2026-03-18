package com.enkryptify.internal.model;

public record SecretValue(
        String environmentId,
        String value,
        boolean isPersonal,
        Reminder reminder
) {
}
