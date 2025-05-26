package com.rxjava.alt.tests;

import com.rxjava.alt.core.Observable;
import com.rxjava.alt.core.Observer;
import com.rxjava.alt.core.Disposable;
import com.rxjava.alt.schedulers.IOThreadScheduler;
import com.rxjava.alt.schedulers.SingleThreadScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class ObservableTest {

    @Test
    void testMapFilterFlatMap() throws InterruptedException {
        List<Integer> result = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Observable<Integer> source = Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
            return () -> {};
        });

        source
                .map(i -> i * 2)
                .filter(i -> i % 3 != 0)
                .flatMap(i -> Observable.<Integer>create(inner -> {
                    inner.onNext(i);
                    inner.onNext(i + 10);
                    inner.onComplete();
                    return () -> {};
                }))
                .subscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) {
                        result.add(item);
                    }
                    @Override public void onError(Throwable t) {
                        Assertions.fail("Ошибка в тесте: " + t.getMessage());
                    }
                    @Override public void onComplete() {
                        latch.countDown();
                    }
                });

        Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS), "Поток не завершился вовремя");
        // Исход: [1,2,3]*2 -> [2,4,6]; фильтр %3!=0 -> [2,4]; flatMap -> [2,12,4,14]
        List<Integer> expected = Arrays.asList(2, 12, 4, 14);
        Assertions.assertEquals(expected, result);
    }

    @Test
    void testErrorPropagation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean errorCalled = new AtomicBoolean(false);

        Observable<Object> errorSource = Observable.create(emitter -> {
            emitter.onNext("ok");
            emitter.onError(new RuntimeException("Test error"));
            return () -> {};
        });

        errorSource.subscribe(new Observer<Object>() {
            @Override public void onNext(Object item) {}
            @Override public void onError(Throwable t) {
                errorCalled.set("Test error".equals(t.getMessage()));
                latch.countDown();
            }
            @Override public void onComplete() {}
        });

        Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS), "onError не был вызван");
        Assertions.assertTrue(errorCalled.get(), "Сообщение об ошибке неверно");
    }

    @Test
    void testDisposeStopsEmission() throws InterruptedException {
        CountDownLatch disposeLatch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();

        Observable<Integer> infinite = Observable.<Integer>create(emitter -> {
            Thread t = new Thread(() -> {
                int i = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    emitter.onNext(i++);
                }
            });
            t.start();
            return () -> {
                t.interrupt();
                disposeLatch.countDown();
            };
        });

        disposableRef.set(infinite.subscribe(new Observer<Integer>() {
            @Override public void onNext(Integer item) {
                if (count.incrementAndGet() >= 5) {
                    disposableRef.get().dispose();
                }
            }
            @Override public void onError(Throwable t) {}
            @Override public void onComplete() {}
        }));

        Assertions.assertTrue(disposeLatch.await(1, TimeUnit.SECONDS), "dispose() не был вызван");
        Assertions.assertTrue(count.get() >= 5, "Элементы не эмитятся или dispose сработал слишком рано");
    }

    @Test
    void testSubscribeOnObserveOn() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> threadRef = new AtomicReference<>();

        Observable<Long> src = Observable.<Long>create(emitter -> {
            emitter.onNext(Thread.currentThread().getId());
            emitter.onComplete();
            return () -> {};
        });

        src
                .subscribeOn(new IOThreadScheduler())
                .observeOn(new SingleThreadScheduler())
                .subscribe(new Observer<Long>() {
                    @Override public void onNext(Long id) {
                        threadRef.set(Thread.currentThread());
                    }
                    @Override public void onError(Throwable t) {}
                    @Override public void onComplete() {
                        latch.countDown();
                    }
                });

        Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS), "Stream did not complete in time");
        Assertions.assertNotEquals(Thread.currentThread(), threadRef.get(), "Ожидался другой поток");
    }
}
