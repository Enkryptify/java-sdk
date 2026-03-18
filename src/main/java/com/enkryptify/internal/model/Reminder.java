package com.enkryptify.internal.model;

public record Reminder(
        String id,
        String type,
        String nextReminderDate
) {
}
