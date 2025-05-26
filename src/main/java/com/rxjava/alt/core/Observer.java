package com.rxjava.alt.core;

// Слушатель потока
public interface Observer<T> {
    void onNext(T item);
    void onError(Throwable t);
    void onComplete();
}