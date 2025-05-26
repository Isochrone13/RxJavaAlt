package org.example;

import com.rxjava.alt.core.Observable;
import com.rxjava.alt.core.Observer;
import com.rxjava.alt.schedulers.IOThreadScheduler;
import com.rxjava.alt.schedulers.SingleThreadScheduler;

// Пример работы реактивного потока
public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Создание «холодного» источника:
        // Observable.create() принимает логику эмиссии через OnSubscribe.
        Observable<Integer> source = Observable.<Integer>create(obs -> {
            // Здесь мы вручную генерируем значения от 1 до 5
            for (int i = 1; i <= 5; i++) {
                obs.onNext(i);        // Эмитируем число i
            }
            obs.onComplete();        // Сигнализируем об окончании потока
            // Возвращаем Disposable — в данном случае пустая реализация
            return () -> {};
        });

        // Построение конвейера операторов:
        source
                // Умножаем каждое число на 10 (map)
                .map(i -> i * 10)
                // Фильтруем только значения > 30 (filter)
                .filter(i -> i > 30)
                // Для каждого значения создаём внутренний Observable,
                // который эмитирует два элемента (flatMap)
                .flatMap(i -> Observable.<Integer>create(inner -> {
                    inner.onNext(i);
                    inner.onNext(i + 1);
                    inner.onComplete();
                    return () -> {};
                }))
                // Переносим эмиссию данных в пул IO (subscribeOn)
                .subscribeOn(new IOThreadScheduler())
                // Доставку результатов — в однопоточный пул (observeOn)
                .observeOn(new SingleThreadScheduler())
                // Подписываемся на готовый поток
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        // Получаем элемент и выводим его вместе с именем текущего потока
                        System.out.printf("Получено: %d (поток %s)%n",
                                item, Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable t) {
                        // В случае ошибки печатаем стектрейс
                        t.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        // Когда все данные доставлены, выводим сообщение
                        System.out.println("Done");
                    }
                });

        // Задерживаем основной поток, чтобы фоновая работа успела завершиться
        Thread.sleep(1000);
    }
}
