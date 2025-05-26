package com.rxjava.alt.schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// SingleThreadScheduler использует единственный поток для упорядоченной последовательной обработки
public class SingleThreadScheduler implements Scheduler {
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    public void execute(Runnable task) {
        pool.submit(task);
    }
}
