package com.rxjava.alt.core;

// Интерфейс для отмены подписки.
@FunctionalInterface
public interface Disposable {
    void dispose();

    // Метод для проверки состояния подписки (по умолчанию false)
    default boolean isDisposed() {
        return false;
    }
}