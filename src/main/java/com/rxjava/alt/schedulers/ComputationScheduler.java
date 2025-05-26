package com.rxjava.alt.schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ComputationScheduler создаёт фиксированное число потоков,
// равное количеству доступных ядер CPU
public class ComputationScheduler implements Scheduler {
    // Фиксированный пул размером Runtime.getRuntime().availableProcessors()
    private final ExecutorService pool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    @Override
    public void execute(Runnable task) {
        pool.submit(task);
    }
}

