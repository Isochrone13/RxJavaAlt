package com.rxjava.alt.operators;

import com.rxjava.alt.core.Observable;
import com.rxjava.alt.core.Observer;
import java.util.function.Function;

// Оператор, преобразующий каждый элемент входящего Observable<T>
// в новый элемент типа R с помощью переданной функции mapper.

public class MapOperator {

    // Создаёт новый Observable<R>, который подписывается на исходный source,
    // получает элементы T, применяет функцию mapper, и передаёт результат R дальше.
    public static <T, R> Observable<R> map(
            Observable<T> source,
            Function<? super T, ? extends R> mapper
    ) {
        // Здесь мы создаем «обёрточный» Observable:
        return Observable.create(obs ->
                // Подписываемся на source — теперь все события source будут перехвачены
                source.subscribe(new Observer<T>() {
                    @Override
                    public void onNext(T item) {
                        // Для каждого пришедшего item вызываем mapper.apply(item)
                        R mapped = mapper.apply(item);
                        // И пересылаем полученное значение в следующий Observable (obs)
                        obs.onNext(mapped);
                    }

                    @Override
                    public void onError(Throwable t) {
                        // Если в source произошла ошибка — передаём её дальше
                        obs.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        // Когда source завершился, завершает и «mapped» поток
                        obs.onComplete();
                    }
                })
        );
    }
}