package com.rxjava.alt.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import com.rxjava.alt.schedulers.Scheduler;
import com.rxjava.alt.operators.MapOperator;
import com.rxjava.alt.operators.FilterOperator;
import com.rxjava.alt.operators.FlatMapOperator;

// Основной класс реактивного потока, который будем "слушать"
public class Observable<T> {
    // Функция, которая при подписке запустит выпуск элементов
    private final OnSubscribe<T> onSubscribe;

    private Observable(OnSubscribe<T> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    // Метод для создания Observable
    public static <T> Observable<T> create(OnSubscribe<T> onSubscribe) {
        return new Observable<>(onSubscribe);
    }

    // Интерфейс-обёртка для выпуска объектов
    // при вызове call() передаем Observer, через который вызываем onNext/onError/onComplete
    @FunctionalInterface
    public interface OnSubscribe<T> {
        Disposable call(Observer<? super T> observer);
    }

    // Подписка на Observable
    public Disposable subscribe(Observer<? super T> observer) {
        // Создаём флаг отмены подписки, пока он false, мы будем передавать события observer
        AtomicBoolean unsubscribed = new AtomicBoolean(false);

        // Будем проверять флаг перед каждым вызовом, чтобы не вызывать методы после dispose()
        Observer<T> safeObserver = wrapObserver(observer, unsubscribed);

        // Запускаем выпуск из OnSubscribe, передавая ей safeObserver и вызывая onNext/onError/onComplete
        Disposable upstream = onSubscribe.call(safeObserver);

        // Возвращаем пользователю новый Disposable — он меняет флаг и говорит источнику остановиться
        return new Disposable() {
            @Override
            public void dispose() {
                // Помечаем отписку
                unsubscribed.set(true);
                // Передаём команду остановиться источнику
                upstream.dispose();
            }
            @Override
            public boolean isDisposed() {
                // Смотрим, отписался ли уже пользователь
                return unsubscribed.get();
            }
        };
    }


    /** Ниже — операторы, которые возвращают новый Observable,
    обёрнутый над существующим. */

    // оператор map берёт каждый элемент потока типа T и трансформирует его в новый элемент типа R при помощи переданной функции mapper
    public <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        // Делегируем в MapOperator который создаст новый Observable
        return MapOperator.map(this, mapper);
    }

    // Оператор фильтрации: пропускает только те T, которые прошли predicate.test(item).
    public Observable<T> filter(Predicate<? super T> predicate) {
        return FilterOperator.filter(this, predicate);
    }

    // оператор flatMap преобразовывает каждый элемент потока T в новый поток (Observable) элементов типа R, а затем соединяет вложенные потоки в один общий
    public <R> Observable<R> flatMap(Function<? super T, ? extends Observable<? extends R>> mapper) {
        return FlatMapOperator.flatMap(this, mapper);
    }

    // Оператор subscribeOn говорит, что OnSubscribe.call нужно запускать в указанном Scheduler (пуле потоков)
    public Observable<T> subscribeOn(Scheduler scheduler) {
        // Создаём новый Observable, чтобы не ломать исходный.
        return Observable.create(observer -> {
            AtomicBoolean unsubscribed = new AtomicBoolean(false);
            AtomicReference<Disposable> ref = new AtomicReference<>();
            /**
            Пользователь, вызвав subscribeOn, ожидает сразу получить Disposable,
             чтобы в любой момент отписаться. Но реальная подписка у нас запускается
             асинхронно через scheduler.execute. Латч нужен, чтобы дождаться момента,
             когда ref.set(...) отработал, и мы возвращаем уже корректный Disposable
             с настоящим upstream внутри.
             */
            CountDownLatch latch = new CountDownLatch(1);

            // Запускаем onSubscribe.call(...) в другом потоке.
            scheduler.execute(() -> {
                // В этом пуле потоков запускается наша логика выпуска.
                ref.set(onSubscribe.call(wrapObserver(observer, unsubscribed)));
                // Обозначаем, что upstream Disposable уже сохранили и мы можем сразу вернуть его пользователю.
                latch.countDown();
            });

            // Ждём, пока ref заполнится, чтобы сразу вернуть корректный Disposable
            try {
                latch.await();
            } catch (InterruptedException ignored) {
            }

            // Возвращаем Disposable, который отменяет upstream и выставляет флаг
            return new Disposable() {
                @Override
                public void dispose() {
                    unsubscribed.set(true);
                    ref.get().dispose();
                }

                @Override
                public boolean isDisposed() {
                    return unsubscribed.get();
                }
            };
        });
    }

    // Оператор observeOn переключает поток, в котором будут вызваны onNext/onError/onComplete
    public Observable<T> observeOn(Scheduler scheduler) {
        // создаём новый Observable, но теперь оборачиваем передаваемый observer так,
        // чтобы все onNext/onError/onComplete шли через scheduler.
        return Observable.create(observer ->
                subscribe(wrapObserverWithScheduler(observer, scheduler))
        );
    }

    // Проверяем флаг unsubscribed перед каждым событием
    private Observer<T> wrapObserver(Observer<? super T> observer, AtomicBoolean unsubscribed) {
        return new Observer<T>() {
            @Override
            public void onNext(T item) {
                if (!unsubscribed.get()) {
                    observer.onNext(item);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (!unsubscribed.get()) {
                    observer.onError(t);
                }
            }

            @Override
            public void onComplete() {
                if (!unsubscribed.get()) {
                    observer.onComplete();
                }
            }
        };
    }

    // Переключаем контекст выполнения каждого метода через указанный Scheduler.
    private Observer<T> wrapObserverWithScheduler(Observer<? super T> observer, Scheduler scheduler) {
        return new Observer<T>() {
            @Override
            public void onNext(T item) {
                // Вызов observer.onNext(item) внутри scheduler
                scheduler.execute(() -> observer.onNext(item));
            }

            @Override
            public void onError(Throwable t) {
                scheduler.execute(() -> observer.onError(t));
            }

            @Override
            public void onComplete() {
                scheduler.execute(observer::onComplete);
            }
        };
    }
}
