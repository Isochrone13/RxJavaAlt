package com.rxjava.alt.schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// IOThreadScheduler использует Executors.newCachedThreadPool(), который создаёт новые потоки по необходимости и кэширует их
public class IOThreadScheduler implements Scheduler {
    // ExecutorService с динамически создаваемыми и удаляемыми потоками
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @Override
    public void execute(Runnable task) {
        // Отправляем задачу в пул на выполнение
        pool.submit(task);
    }
}
