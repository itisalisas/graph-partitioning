# Experiment Scripts

Скрипты для запуска и анализа экспериментов по разбиению графов.

## Скрипты

### 1. `run_test_datasets.py` - Запуск одного эксперимента

Запускает эксперименты для всех городов и размеров с заданными параметрами.

**Параметры:**
- `experiment_dir` (обязательный) - имя директории эксперимента
- `--algorithm` - алгоритм (по умолчанию: IF)
- `--weights` - веса (по умолчанию: 10000 20000)
- `--coef` - коэффициент (по умолчанию: 0.1)
- `--workers` - количество параллельных процессов (по умолчанию: 1)

**Примеры:**

```bash
# Последовательное выполнение
python src/scripts/experiments/run_test_datasets.py DIF_0_1 --algorithm DIF --coef 0.1

# Параллельное выполнение с 4 воркерами
python src/scripts/experiments/run_test_datasets.py DIF_0_1 --algorithm DIF --coef 0.1 --workers 4

# С кастомными весами
python src/scripts/experiments/run_test_datasets.py my_exp --algorithm RIF --coef 0.25 --weights 5000 10000 --workers 8
```

### 2. `run_all_experiments.py` - Запуск всех комбинаций

Запускает все комбинации алгоритмов (DIF, RIF) и коэффициентов (0.1, 0.25, 0.4).
**Автоматически вычисляет статистику** после завершения каждого эксперимента.

**Параметры:**
- `--workers` - количество параллельных процессов для каждого эксперимента (по умолчанию: 4)

**Примеры:**

```bash
# С 4 воркерами (по умолчанию)
python src/scripts/experiments/run_all_experiments.py

# С 8 воркерами для максимальной скорости
python src/scripts/experiments/run_all_experiments.py --workers 8

# Без параллельности (последовательно)
python src/scripts/experiments/run_all_experiments.py --workers 1
```

**Создаваемые директории и файлы:**
```
src/main/output/
├── DIF_0_1/
├── DIF_0_1_statistics.txt          # Автоматически создается
├── DIF_0_25/
├── DIF_0_25_statistics.txt
├── DIF_0_4/
├── DIF_0_4_statistics.txt
├── RIF_0_1/
├── RIF_0_1_statistics.txt
├── RIF_0_25/
├── RIF_0_25_statistics.txt
├── RIF_0_4/
└── RIF_0_4_statistics.txt
```

### 3. `calculate_statistic.py` - Анализ результатов

Вычисляет статистику по результатам эксперимента.

**Параметры:**
- `experiment_dir` (обязательный) - имя директории эксперимента

**Примеры:**

```bash
# Анализ одного эксперимента
python src/scripts/experiments/calculate_statistic.py DIF_0_1

# Анализ всех экспериментов
for exp in DIF_0_1 DIF_0_25 DIF_0_4 RIF_0_1 RIF_0_25 RIF_0_4; do
    python src/scripts/experiments/calculate_statistic.py $exp
done
```

**Результат:**
- Файл `src/main/output/{experiment_dir}_statistics.txt` со всеми метриками

## Рекомендации по параллельности

- **CPU-bound задачи**: количество воркеров = количество ядер процессора
- **Небольшие графы**: можно использовать больше воркеров (8-16)
- **Большие графы**: меньше воркеров (2-4) из-за потребления памяти
- **Первый запуск**: начните с `--workers 4` и экспериментируйте

## Полный workflow

```bash
# 1. Запустить все эксперименты параллельно
# Статистика вычисляется автоматически после каждого эксперимента
python src/scripts/experiments/run_all_experiments.py --workers 4

# 2. Проверить результаты
ls -lh src/main/output/*.txt

# Все готово! Файлы статистики уже созданы:
# - DIF_0_1_statistics.txt
# - DIF_0_25_statistics.txt
# - DIF_0_4_statistics.txt
# - RIF_0_1_statistics.txt
# - RIF_0_25_statistics.txt
# - RIF_0_4_statistics.txt
```

**Если нужно пересчитать статистику вручную:**
```bash
python src/scripts/experiments/calculate_statistic.py DIF_0_1
```

## Структура результатов

```
src/main/output/
├── DIF_0_1/                           # Директория эксперимента
│   ├── spb/1000/max_weight_10000/     # Результаты для города/размера/веса
│   │   ├── partition_info.json         # Метрики разбиения
│   │   ├── map.html                    # Визуализация
│   │   ├── run.log                     # Логи Java
│   │   └── visualization.log           # Логи визуализации
│   └── visualization_errors.log        # Ошибки визуализации
├── DIF_0_1_statistics.txt             # Сводная статистика
├── DIF_0_25/
└── ...
```

## Логи и отладка

- **Java логи**: `{experiment_dir}/{city}/{size}/{weight}/run.log`
- **Визуализация логи**: `{experiment_dir}/{city}/{size}/{weight}/visualization.log`
- **Ошибки визуализации**: `{experiment_dir}/visualization_errors.log`
- **Статус выполнения**: выводится в консоль в реальном времени

## Возобновление после сбоя

Скрипты автоматически пропускают уже выполненные задачи (проверяют наличие `map.html`), поэтому можно безопасно перезапустить скрипт после сбоя:

```bash
# Перезапуск прерванного эксперимента
python src/scripts/experiments/run_test_datasets.py DIF_0_1 --algorithm DIF --coef 0.1 --workers 4
```
