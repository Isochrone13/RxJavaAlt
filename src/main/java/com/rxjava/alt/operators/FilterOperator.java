package com.rxjava.alt.operators;

import com.rxjava.alt.core.Observable;
import com.rxjava.alt.core.Observer;
import java.util.function.Predicate;

public class FilterOperator {
    // Создаёт новый Observable, который принимает элементы из исходного source,
    // проверяет их условием predicate и пересылает дальше только те, что проходят тест
    public static <T> Observable<T> filter(Observable<T> source, Predicate<? super T> predicate) {
        return Observable.create(obs ->
                // Подписываемся на source — получаем все события от исходного потока
                source.subscribe(new Observer<T>() {

            @Override public void onNext(T item) {
                // Если проверка прошла (true), пересылаем элемент дальше через obs.onNext(item)
                if (predicate.test(item)) obs.onNext(item);
            }

            // В случае ошибки в исходном потоке передаём её в выходной поток
            @Override
            public void onError(Throwable t) {
                obs.onError(t);
            }

            // Когда исходный поток завершился, завершаем и фильтрующий Observable
            @Override
            public void onComplete() {
                obs.onComplete();
            }
        }));
    }
}