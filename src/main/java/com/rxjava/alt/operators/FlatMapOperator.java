package com.rxjava.alt.operators;

import com.rxjava.alt.core.Observable;
import com.rxjava.alt.core.Observer;
import com.rxjava.alt.core.Disposable;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;

// FlatMapOperator — оператор, позволяющий на каждый входящий элемент создавать,
// новый внутренний поток Observable и производить merge их выходных
// элементов в один последовательный внешний поток

public class FlatMapOperator {

    // Преобразует каждый элемент входного source через функцию mapper в новый Observable
    // и объединяет все элементы из этих внутренних потоков в единую последовательность

    public static <T, R> Observable<R> flatMap(
            Observable<T> source,
            Function<? super T, ? extends Observable<? extends R>> mapper
    ) {
        // Создаем новый Observable, где реализуем логику flatMap
        return com.rxjava.alt.core.Observable.create(obs -> {
            // Список всех подписок: основная + вложенные
            List<Disposable> disposables = new ArrayList<>();

            // Подписываемся на исходный поток source
            Disposable main = source.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    // Для каждого элемента item вызываем mapper, получая внутренний Observable
                    Observable<? extends R> inner = mapper.apply(item);

                    // Подписываемся на этот внутренний поток
                    Disposable d = inner.subscribe(new Observer<R>() {
                        @Override
                        public void onNext(R r) {
                            // Пересылаем каждое r в результирующий внешний поток
                            obs.onNext(r);
                        }

                        @Override
                        public void onError(Throwable t) {
                            // При ошибке из любого внутреннего потока сигнализируем об ошибке
                            obs.onError(t);
                        }

                        @Override
                        public void onComplete() {
                            // Игнорируем завершение каждого отдельного внутреннего потока,
                            // потому что общий поток завершится, когда основной source завершится.
                        }
                    });

                    // Сохраняем Disposable вложенной подписки для возможности отмены
                    disposables.add(d);
                }

                @Override
                public void onError(Throwable t) {
                    // Если исходный поток выдал ошибку, передаем её во внешний
                    obs.onError(t);
                }

                @Override
                public void onComplete() {
                    // Когда исходный поток полностью завершился, сигнализируем окончание
                    obs.onComplete();
                }
            });

            // Добавляем основную подписку в список disposables
            disposables.add(main);

            // Возвращаем Disposable, который при dispose() пройдется по всем подпискам
            // (основной + вложенные) и отменит их
            return new Disposable() {
                @Override
                public void dispose() {
                    // Отписываемся от всех потоков
                    disposables.forEach(Disposable::dispose);
                }

                @Override
                public boolean isDisposed() {
                    return false;
                }
            };
        });
    }
}