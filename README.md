# Отчёт по реализации RxJava

В этом документе подробно описана архитектура, принципы работы Schedulers, процесс тестирования и примеры использования нашей учебной библиотеки реактивных потоков.

---

## 1. Архитектура системы

### 1.1 Модуль `core`

- **Observable<T>**
  - Хранит `OnSubscribe<T>` — функцию-генератор, где пользователь описывает, как и какие данные эмитить.
  - Предоставляет методы:
    - `subscribe(Observer<? super T>)` — подключает подписчика, создаёт флаг `unsubscribed` и безопасного `Observer`, запускает эмиссию через `onSubscribe.call()`.
    - Операторы-цепочки: `.map()`, `.filter()`, `.flatMap()`, `.subscribeOn()`, `.observeOn()`.
  - Возвращает `Disposable` — объект для отмены подписки.

- **Observer<T>**
  - Интерфейс с тремя методами:
    - `onNext(T item)` — при получении очередного элемента.
    - `onError(Throwable t)` — при ошибке.
    - `onComplete()` — при завершении потока.

- **Disposable**
  - Функциональный интерфейс:
    - `dispose()` — устанавливает флаг `unsubscribed` и вызывает `dispose()` upstream, чтобы прекратить эмиссию.
    - `isDisposed()` — проверяет состояние.

### 1.2 Модуль `operators`

Каждый оператор создаёт новый `Observable` поверх исходного:

- **MapOperator.map**
  1. При подписке создаёт `Observer<T>`, который в `onNext(T item)` вызывает `mapper.apply(item)` → `R`.
  2. Отправляет `obs.onNext(mapped)` дальше.
  3. Пропускает `onError` и `onComplete` без изменений.

- **FilterOperator.filter**
  1. При `onNext` проверяет `predicate.test(item)`.
  2. Если `true` → `obs.onNext(item)`; иначе — отбрасывает.
  3. `onError` и `onComplete` прокидываются напрямую.

- **FlatMapOperator.flatMap**
  1. В `onNext(T item)` создаёт `Observable<? extends R> inner = mapper.apply(item)`.
  2. Подписывается на каждый `inner`, собирая `Disposable` во `List`.
  3. Любой `inner.onNext(r)` пересылает во внешний `obs.onNext(r)`.
  4. `onError` любого потока прерывает весь внешний поток.
  5. `onComplete` оригинального `source` завершает внешний поток.
  6. В `dispose()` вызывает `dispose()` всех накопленных подписок.

### 1.3 Модуль `schedulers`

- **Scheduler** — интерфейс:
  ```java
  public interface Scheduler {
      void execute(Runnable task);
  }
  ```

- **IOThreadScheduler** — `Executors.newCachedThreadPool()` для задач I/O.
- **ComputationScheduler** — `Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())` для CPU.
- **SingleThreadScheduler** — `Executors.newSingleThreadExecutor()` для строгого порядка.

Операторы:
- **subscribeOn(Scheduler s)**
  - Эмиссия внутри `OnSubscribe.call()` запускается через `s.execute()`, не блокируя вызывающий поток.
- **observeOn(Scheduler s)**
  - Каждый входящий `onNext/onError/onComplete` планируется через `s.execute()`, меняя контекст обработки.

---

## 2. Принципы работы Schedulers

| Scheduler                | Механизм                              | Где применять                            |
|--------------------------|----------------------------------------|------------------------------------------|
| IOThreadScheduler       | newCachedThreadPool()                  | Сетевые, файловые, БД-операции           |
| ComputationScheduler    | newFixedThreadPool(N)                  | Тяжёлые вычисления, параллелизация CPU   |
| SingleThreadScheduler   | newSingleThreadExecutor()              | Последовательная обработка, UI-активности|

**Как это работает:**
1. **subscribeOn**
   - Обёртывает `onSubscribe.call(obs)` в `scheduler.execute(...)`.
   - Вся генерация данных происходит в другом потоке.

2. **observeOn**
   - При каждом событии предыдущего уровня вместо прямого вызова `obs.onNext()` выполняет `scheduler.execute(() -> obs.onNext(item))`.
   - Меняет контекст для downstream-операторов и подписчика.

---

## 3. Процесс тестирования

### 3.1 Инструменты и окружение
- **JUnit 5** — основной фреймворк для написания и запуска юнит-тестов.
- **CountDownLatch** — синхронизирует основной тестовый поток и асинхронные операции.
- **AtomicInteger**, **AtomicBoolean** — позволяют безопасно проверять и изменять значения из разных потоков.
- **Maven/Gradle** — сборка и управление зависимостями, интеграция с CI.

### 3.2 Структура тестового набора
1. **Файл с тестами**: `ObservableTest.java` в пакете `com.rxjava.clone.tests`.
2. **Конфигурация**:
   - В `pom.xml` (Maven) или `build.gradle` (Gradle) подключены JUnit 5.
   - Тесты запускаются командой `mvn test` или `./gradlew test`.

### 3.3 Основные сценарии тестирования
1. **Map + Filter**
   - **Цель**: убедиться, что комбинация операторов работает в нужном порядке.
   - **Шаги**:
     1. Создаём источник `Observable` с эмиссией `[1, 2, 3, 4]`.
     2. Применяем `filter(i -> i % 2 == 0)` и `map(i -> i * 10)`.
     3. Используем `CountDownLatch` для ожидания `onComplete()`.
     4. Собираем элементы в `List` и проверяем, что результат равен `[20, 40]`.

2. **FlatMap**
   - **Цель**: проверить развёртывание вложенных потоков.
   - **Шаги**:
     1. Источник даёт `"A"` и `"B"`.
     2. `flatMap` создаёт для каждого символа по два элемента: `A1, A2` и `B1, B2`.
     3. После `onComplete()` ожидаем завершения и сравниваем итоговый список из четырёх строк.

3. **subscribeOn + observeOn**
   - **Цель**: проверить, что эмиссия и обработка переключаются в нужные потоки.
   - **Шаги**:
     1. Внутри `onNext` сохраняем `Thread.currentThread().getName()` в `AtomicReference<String>`.
     2. Подписываемся с `subscribeOn(IOThreadScheduler)` и `observeOn(SingleThreadScheduler)`.
     3. После завершения проверяем, что:
        - Эмиссия шла не в основном (`main`), а в пуле IO (`pool-`).
        - Обработка (`onNext`) произошла в однопоточном пуле (`pool-1-thread-1`).

4. **Обработка ошибок (onError)**
   - **Цель**: убедиться, что любая исключительная ситуация корректно прокидывается в `onError()`.
   - **Шаги**:
     1. В `OnSubscribe` сразу бросаем `new RuntimeException("TestError")`.
     2. Подписываемся и проверяем, что `observer.onError` был вызван ровно один раз с сообщением `TestError`.

5. **Отмена подписки (Disposable)**
   - **Цель**: проверить, что после `dispose()` поток останавливается.
   - **Шаги**:
     1. Создаём бесконечный эмиттер:
        ```java
        Observable<Long> src = Observable.create(obs -> {
            long i = 0;
            while (true) {
                obs.onNext(i++);
            }
        });
        ```
     2. Подписываемся и сохраняем `Disposable d`.
     3. В `onNext` после получения, например, пяти элементов, вызываем `d.dispose()`.
     4. Проверяем, что после этого `onNext` больше не вызывается`.

### 3.4 Интеграция в CI
- **GitHub Actions / Jenkins**:
  - Шаг `build + test`: запускает `mvn clean test` или `./gradlew clean test`.
  - Артефакты тестирования собираются в отчёты JUnit.
- **Надёжность**:
  - Тесты детерминированы, без флейков, с разумными таймаутами (≤1 с). 

---

## 4. Примеры использования

### 4.1 Простой поток с фильтрацией и отображением
```java
Observable.<Integer>create(emitter -> {
    for (int i = 0; i < 5; i++) {
        emitter.onNext(i);
    }
    emitter.onComplete();
    return () -> {};
})
.filter(i -> i % 2 == 1)                          // отсекаем чётные
.map(i -> "Odd number: " + i)                  // форматируем строкой
.subscribe(
    System.out::println,                        // onNext
    Throwable::printStackTrace,                  // onError
    () -> System.out.println("Completed")      // onComplete
);
```

### 4.2 Асинхронная генерация с переключением потоков
```java
Observable.<String>create(obs -> {
    obs.onNext("Start");
    try {
        Thread.sleep(200);                       // имитация работы
    } catch (InterruptedException ignored) {}
    obs.onComplete();
    return () -> {};
})
.subscribeOn(new IOThreadScheduler())           // эмиссия в IO-пуле
.observeOn(new SingleThreadScheduler())         // обработка в одном потоке
.subscribe(
    msg -> System.out.println(msg +           
        " (thread " + Thread.currentThread().getName() + ")"),
    Throwable::printStackTrace,
    () -> System.out.println("Done")
);
```

### 4.3 FlatMap для параллельной обработки
```java
Observable.<Integer>create(obs -> {
    obs.onNext(1);
    obs.onNext(2);
    obs.onComplete();
    return () -> {};
})
.flatMap(i -> Observable.<String>create(inner -> {
    inner.onNext("Item " + i + "-A");
    inner.onNext("Item " + i + "-B");
    inner.onComplete();
    return () -> {};
}))
.subscribe(System.out::println, Throwable::printStackTrace, () -> System.out.println("All Items Emitted"));
```

### 4.4 Демонстрация ошибки
```java
Observable.<Integer>create(obs -> {
    obs.onNext(1);
    throw new RuntimeException("Emitter failure");  // исключение
})
.subscribe(
    System.out::println,
    err -> System.err.println("Caught: " + err.getMessage()),
    () -> System.out.println("No Errors")
);
```

### 4.5 Прерывание длинного потока
```java
Disposable d = Observable.<Long>create(obs -> {
    long i = 0;
    while (true) {
        obs.onNext(i++);
        Thread.sleep(50);
    }
}).subscribeOn(new IOThreadScheduler())
  .subscribe(
      num -> {
          System.out.println(num);
          if (num >= 5) {
              d.dispose();                        // останавливаем после 5
              System.out.println("Disposed after 5");
          }
      },
      Throwable::printStackTrace,
      () -> System.out.println("Finished")
  );
```
