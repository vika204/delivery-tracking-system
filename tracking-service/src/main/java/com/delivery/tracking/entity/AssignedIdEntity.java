package com.delivery.tracking.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Базовий клас для сутностей, чий ID задається вручну як ключ ідемпотентності.
 * <p>
 * Без {@link Persistable} Spring Data вважав би новий запис наявним (ID не null) і виконував би
 * {@code merge} (SELECT + UPDATE). Тоді паралельна спроба з тим самим ключем тихо перезаписала б
 * рядок замість порушення первинного ключа, і дані зберіглися б двічі.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<String> {

    @Transient
    private boolean persisted;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }
}
