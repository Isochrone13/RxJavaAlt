package com.rxjava.alt.schedulers;

// Общий интерфейс Scheduler для запуска задач в разных потоках/пулях
public interface Scheduler {
    void execute(Runnable task);
}