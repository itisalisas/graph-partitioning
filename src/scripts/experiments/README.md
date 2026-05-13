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

### 4. `compare_rif_dif.py` - Сравнение RIF и DIF

Генерирует HTML таблицу со сравнением метрик для алгоритмов RIF и DIF.
Значения RIF подсвечиваются:
- **Зеленым** если RIF лучше
- **Красным** если RIF хуже

**Параметры:**
- `--base-dir` - базовая директория с результатами (по умолчанию: `src/main/output`)
- `--output` - выходной HTML файл (по умолчанию: `comparison.html`)
- `--coef` - фильтр по коэффициенту (например, `0.1`, `0.25`, `0.4`). Если не указан, создается таблица со всеми коэффициентами и интерактивным выбором
- `--sizes` - фильтр по размерам графов (например, `1000 2000`). Если не указан, включаются все размеры
- `--version` - фильтр по версии эксперимента (например, `2`, `3`). Если не указан, автоматически определяется последняя версия

**Примеры:**

```bash
# Сравнение для конкретной версии и коэффициента
python src/scripts/experiments/compare_rif_dif.py --version 3 --coef 0.25 --sizes 1000 2000 --output comparison_0_25_v3.html

# Сравнение для всех коэффициентов с интерактивным dropdown (по умолчанию 0.25)
# Версия определяется автоматически
python src/scripts/experiments/compare_rif_dif.py --output comparison_all.html

# Только большие графы (1000 и 2000 вершин), версия 2
python src/scripts/experiments/compare_rif_dif.py --version 2 --sizes 1000 2000 --output comparison_large_v2.html

# Указать кастомную базовую директорию
python src/scripts/experiments/compare_rif_dif.py --base-dir src/main/output --output my_comparison.html
```

**Результат:**
- HTML файл с интерактивной таблицей сравнения всех метрик
- Интерактивный выбор коэффициента (dropdown-меню) если `--coef` не указан
- Автоматическая подсветка лучших/худших значений
- Процентная разница между алгоритмами

**Примечание:** Скрипт `run_all_experiments.py` автоматически генерирует таблицы сравнения после завершения всех экспериментов (только для графов размера 1000 и 2000 вершин). Версия определяется автоматически из имени первого запущенного эксперимента:
- `comparison_coef_0_1_v3.html` - только для коэффициента 0.1, версия 3
- `comparison_coef_0_25_v3.html` - только для коэффициента 0.25, версия 3
- `comparison_coef_0_4_v3.html` - только для коэффициента 0.4, версия 3
- `comparison_all_v3.html` - все коэффициенты с интерактивным выбором, версия 3

Если эксперименты запускаются без суффикса версии (например, `RIF_0_1` вместо `RIF_0_1_3`), то файлы будут называться без суффикса версии (`comparison_coef_0_1.html`).

**Важно:** Версия экспериментов задается в строке 26 файла `run_all_experiments.py` (по умолчанию `_3`).

**Информация о датасете (одинаковая для RIF и DIF, не сравнивается):**
- Общий вес графа (totalGraphWeight)
- Минимальное количество регионов (minRegionCountEstimate)
- Извлеченные большие вершины (bigVerticesPartitions, bigVerticesWeight)

**Сравниваемые метрики:**
- **Производительность (меньше = лучше):** время выполнения (partitionTime)
- **Количество регионов (меньше = лучше):** общее (regionCount), обычных (normalRegionCount)
- **Качество разбиения (ближе к 1 = лучше):** отклонение от оптимума (estimatorRegionNumber)
- **Балансировка:** дисперсия весов (меньше = лучше: weightVariance), средний вес (больше = лучше: averageWeight)
- **Границы (меньше = лучше):** общая длина (totalBoundaryLength)
- **Диапазон весов:** минимальный вес (больше = лучше: minRegionWeight), максимальный вес (больше = лучше: maxRegionWeight)

## Рекомендации по параллельности

- **CPU-bound задачи**: количество воркеров = количество ядер процессора
- **Небольшие графы**: можно использовать больше воркеров (8-16)
- **Большие графы**: меньше воркеров (2-4) из-за потребления памяти
- **Первый запуск**: начните с `--workers 4` и экспериментируйте

## Полный workflow

```bash
# 1. Запустить все эксперименты параллельно
# Статистика вычисляется автоматически после каждого эксперимента
# Таблицы сравнения генерируются автоматически после всех экспериментов
python src/scripts/experiments/run_all_experiments.py --workers 4

# 2. Проверить результаты
ls -lh src/main/output/*.txt
ls -lh src/main/output/*.html

# Все готово! Автоматически созданы:
# Файлы статистики:
# - DIF_0_1_statistics.txt
# - DIF_0_25_statistics.txt
# - DIF_0_4_statistics.txt
# - RIF_0_1_statistics.txt
# - RIF_0_25_statistics.txt
# - RIF_0_4_statistics.txt
#
# HTML таблицы сравнения (только для графов 1000 и 2000 вершин):
# - comparison_coef_0_1_v3.html (только коэффициент 0.1, версия 3)
# - comparison_coef_0_25_v3.html (только коэффициент 0.25, версия 3)
# - comparison_coef_0_4_v3.html (только коэффициент 0.4, версия 3)
# - comparison_all_v3.html (все коэффициенты с dropdown-меню, по умолчанию 0.25, версия 3)

# 3. Открыть таблицу сравнения в браузере
open src/main/output/comparison_all_v3.html
```

**Если нужно пересчитать статистику вручную:**
```bash
python src/scripts/experiments/calculate_statistic.py DIF_0_1
```

**Если нужно пересоздать таблицы сравнения:**
```bash
# Конкретный коэффициент, версия 3 (только большие графы 1000, 2000)
python src/scripts/experiments/compare_rif_dif.py --version 3 --coef 0.25 --sizes 1000 2000 --output comparison_0_25_v3.html

# Все коэффициенты, версия 3 (только большие графы 1000, 2000)
python src/scripts/experiments/compare_rif_dif.py --version 3 --sizes 1000 2000 --output comparison_all_v3.html

# Автоопределение последней версии, все размеры графов
python src/scripts/experiments/compare_rif_dif.py --output comparison_all_sizes.html

# Сравнить конкретно версию 2
python src/scripts/experiments/compare_rif_dif.py --version 2 --sizes 1000 2000 --output comparison_all_v2.html
```

## Структура результатов

```
src/main/output/
├── DIF_0_1/                           # Директория эксперимента
│   ├── moscow/                        # Город
│   │   ├── 1000/                      # Размер графа (количество вершин)
│   │   │   ├── max_weight_10000/      # Максимальный вес региона
│   │   │   │   ├── partition_info.json # Метрики разбиения
│   │   │   │   ├── map.html            # Визуализация
│   │   │   │   ├── run.log             # Логи Java
│   │   │   │   └── visualization.log   # Логи визуализации
│   │   │   └── max_weight_20000/
│   │   ├── 2000/
│   │   └── ...
│   ├── spb/
│   └── visualization_errors.log        # Ошибки визуализации
├── DIF_0_1_statistics.txt             # Сводная статистика
├── DIF_0_25/
└── ...
```

## Логи и отладка

- **Java логи**: `{experiment_dir}/{city}/{vertices}/max_weight_{weight}/run.log`
- **Визуализация логи**: `{experiment_dir}/{city}/{vertices}/max_weight_{weight}/visualization.log`
- **Ошибки визуализации**: `{experiment_dir}/visualization_errors.log`
- **Статус выполнения**: выводится в консоль в реальном времени

Пример: `RIF_0_1/moscow/1000/max_weight_10000/run.log`

## Возобновление после сбоя

Скрипты автоматически пропускают уже выполненные задачи (проверяют наличие `map.html`), поэтому можно безопасно перезапустить скрипт после сбоя:

```bash
# Перезапуск прерванного эксперимента
python src/scripts/experiments/run_test_datasets.py DIF_0_1 --algorithm DIF --coef 0.1 --workers 4
```
