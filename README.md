# Incurable — Global Medical Engine (Fabric 26.3)

Серверная, data-driven система болезней. Болезнь **никогда не проходит сама**: ни по таймеру,
ни после смерти (если это разрешено конфигом). Убрать её может только
`MedicalProfile#cure(...)` — лекарство, сыворотка, процедура или команда.

> Проект собирается против **официальных маппингов Mojang**, которые Fabric использует для
> Minecraft 26.3 (`net.minecraft.server.level.ServerPlayer`, `net.minecraft.resources.Identifier`,
> `EntityTypes.CREEPER`, …). Yarn-имён (`ServerPlayerEntity`, `Text`) в этой версии нет.
>
> Сборка проверена: `./gradlew build` → `build/libs/incurable-1.0.0.jar`
> Запуск проверен: `./gradlew runServer` → `[Incurable] Registered 27 diseases`.

## Структура

```
src/main/java/com/incurable/
├── Incurable.java                     # ModInitializer: attachment (+sync), события, id()
├── config/IncurableConfig.java        # config/incurable.json
├── medical/
│   ├── DiseaseRegistry.java           # кастомный Registry<Disease> + каталог из 27 болезней
│   ├── DiseaseInstance.java           # объект болезни на игроке: стадия, таймеры, флаг permanent
│   ├── MedicalProfile.java            # контейнер на игроке (persistent + sync), cure()
│   ├── Disease.java / DiseaseStage.java / StatusEffectSpec.java / DiseaseCategory.java
│   ├── DiseaseBehaviors.java          # поведение всех болезней, КРОМЕ Кордицепса и Бешенства
│   └── MedicalEngine.java             # иммунитет, ролл заражения, cure-API, галлюцинации
├── command/IncurableCommand.java      # /incurable diagnose|infect|cure|immunity|list
└── mixin/DiseaseTickMixin.java        # цикл 1200 тиков + Кордицепс + Бешенство

src/client/java/com/incurable/client/
├── IncurableClient.java               # регистрирует HUD
└── DiseaseHudElement.java             # HUD слева сверху: активные болезни
```

## Правило перманентности

* `DiseaseInstance.permanent` — `final boolean = true`, сеттера нет.
* `advance(delta)` принимает только положительные тики; вычитания/обнуления нет.
* `escalate()` только повышает стадию и сбрасывает `ticksInStage` **лишь при ухудшении**.
* Единственный путь удаления — `MedicalProfile#cure(Identifier)`; из тика он не вызывается.

## Цикл 1 минута (1200 тиков)

`DiseaseTickMixin` инжектится в `ServerPlayer#tick()` (HEAD) и по **глобальным часам мира**
(`level.getGameTime() % interval == 0`) запускает пульс одновременно для всех игроков:

1. **Игрок болен** → `advance()` + гарантированный `escalate()`. На терминальной стадии —
   некупируемый острый приступ.
2. **Игрок здоров** → считается иммунитет (сытость, сон, биом/температура, свет, измерение,
   здоровье). Если он подорван — гарантированно подхватывается новая случайная болезнь.

Между пульсами, раз в ~4 тика на игрока (со сдвигом по `entityId`), обновляются «дешёвые»
непрерывные симптомы. После каждого пульса состояние отправляется владельцу
(`Incurable.sync`) — по нему рисуется HUD.

## HUD

`DiseaseHudElement` рисуется слева сверху и показывает активные болезни, их стадию
(`I/II`) и цвет по категории; терминальные стадии выделяются красным. Данные приходят
через `syncWith(..., targetOnly())` — attachment синхронизируется только владельцу.
Клиент без мода (ванильный) просто ничего не получает и играет как обычно.

Отключить HUD: `showHud: false` в `config/incurable.json`.

## Каталог (27 болезней)

| Категория | Болезни |
|---|---|
| Инфекционные | Грипп, Холера, Бубонная чума, Туберкулёз, Столбняк, Гангрена |
| Экологические | Лучевая болезнь, Силикоз |
| Хронические | Диабет, Сердечный приступ, Анемия, Цирроз, Цинга |
| Паразитарные | Кордицепс, Бешенство |
| Психические | Шизофрения, Амнезия, Болезнь Альцгеймера, Паранойя, ПТСР, Бессонница |
| Синдромы | ДЦП, Синдром Туретта |
| Неврологические | Нарколепсия, Мигрень, Эпилепсия |

## Конфиг (`config/incurable.json`)

| Ключ | По умолчанию | Смысл |
|---|---|---|
| `persistAcrossDeath` | `true` | сохранять болезни после смерти |
| `pulseIntervalTicks` | `1200` | цикл прогрессии/заражения (1 минута) |
| `immunityThreshold` | `35` | ниже этого иммунитета происходит заражение |
| `infectionChance` | `1.0` | шанс заражения на пульсе (1.0 = гарантированно) |
| `contagionChance` | `0.35` | шанс заразить другого при контакте |
| `minimumMaxHealth` | `4.0` | предел потери сердец от радиации |
| `showHud` | `true` | рисовать HUD (клиент) |
| `debugLogging` | `false` | подробный лог пульсов |

## Команды (OP level)

```
/incurable diagnose <player>
/incurable infect  <player> <disease>
/incurable cure    <player> all|<disease>
/incurable immunity <player>
/incurable list
```
