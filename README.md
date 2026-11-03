# ItemCopier

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-3d9c40?style=flat-square)
![Fabric](https://img.shields.io/badge/Fabric-client--only-3d5a99?style=flat-square)

Najedź na dowolny item w dowolnym GUI, wciśnij **`A`**, i masz go u siebie na zawsze.
Z nazwą, kolorami, lore, enchantami — wszystkim, co widzisz na tooltipie.

Potem wchodzisz na swój serwer, włączasz Creative i wyciągasz go z panelu.

Serwer źródłowy nic o tym nie wie. Nie klikasz, nie kupujesz, nic nie dotykasz.

---

## Instalacja

1. [Fabric Loader](https://fabricmc.net/use/) dla Minecrafta **26.2**
2. [Fabric API](https://modrinth.com/mod/fabric-api) → folder `mods`
3. `ItemCopier-1.0.0.jar` → folder `mods`

Tyle. Nic na serwerze, żaden REI ani JEI nie jest potrzebny.

---

## Jak się tego używa

### Kopiowanie

Otwórz cokolwiek z itemami — skrzynkę, sklep, menu z pluginu — najedź kursorem i wciśnij `A`.

```
[ItemCopier] ✔ Item copied: BUTY TANATOSA ★8
```

| Klawisz | Co robi |
|---|---|
| `A` | Kopiuje item pod kursorem |
| `Ctrl` + `A` | Kopiuje **bez lore** (przydatne przy sklepach) |
| `B` | Otwiera pełną bibliotekę |

Klawisze zmienisz w *Opcje → Sterowanie → ItemCopier*.

### Panel przy ekwipunku

Otwórz ekwipunek — **po prawej stronie** pojawi się Twoja biblioteka. Zawsze w tym samym
miejscu: nie przesuwa się, gdy przeskakujesz między zakładkami kreatywnego.

| | |
|---|---|
| Najedź | Zobaczysz normalny tooltip itemu |
| Kliknij | Wrzuca item do ekwipunku (Creative) |
| Przeciągnij | Wrzuca w konkretny slot |
| Prawy przycisk | Szczegóły, zmiana nazwy, edycja lore |
| `Q` | Usuwa item spod kursora z biblioteki |
| `Shift` + prawy | To samo, jeśli wolisz myszką |
| Scroll | Przewijanie |

W prawym górnym rogu panelu jest **zębatka** — klikasz i jesteś w ustawieniach. Stamtąd dojdziesz
też do klawiszy (*Key bindings*) i do filtrów lore.

Panel nigdy nie zasłania slotów. Jak okno jest wąskie, zmniejsza siatkę; jak miejsca nie ma
wcale — chowa się, zamiast wchodzić na ekwipunek.

Nie ma pod nim żadnej ciemnej ramki — itemy leżą wprost na ekranie, jak w EMI czy REI. Dzięki
temu, kiedy tooltip itemu z ekwipunku na niego wjedzie, nie zderzają się dwa prostokąty.
Tooltip gry zawsze rysuje się na wierzchu panelu.

### Wrzucanie na swój serwer

1. Wejdź na swój serwer, włącz sobie Creative
2. Otwórz ekwipunek
3. Kliknij item w panelu

Jak nie masz Creative, przycisk jest wyszarzony i mod pisze dlaczego. Nie próbuje tego obchodzić —
i tak by nie przeszło, serwer sprawdza to po swojej stronie.

---

## Sklepowe lore — jak się go pozbyć

Itemy ze sklepów mają zwykle doklejone na końcu rzeczy, których nie chcesz:

```
Niezniszczalność 16
Ochrona 16
─────────────
✕ Cena: $128 lub ★128              ← tego nie chcesz
Przedmiot możesz ulepszyć w poprzednim menu   ← i tego
```

Masz trzy sposoby:

**1. Wytnij ręcznie po skopiowaniu** — prawy przycisk na itemie → **Edit lore**.
Dostajesz listę linijek, klikasz te, których nie chcesz, i **Apply**. Przekreślone znikają,
reszta itemu zostaje bez zmian. Puste linijki są pokazane jako `(pusta linia)`, a przycisk
**Wytnij puste** odznacza je wszystkie naraz.

**2. Skopiuj od razu bez lore** — `Ctrl` + `A` zamiast samego `A`.

**3. Niech wycina samo** — jeśli kopiujesz dużo z tego samego sklepu, ustaw sobie filtry:
**zębatka → Lore filters**. Wpisujesz tekst, klikasz *Dodaj*, i każda linijka lore, która go
zawiera, leci do kosza już przy kopiowaniu. Wielkość liter nie ma znaczenia.

Najszybciej: wytnij raz ręcznie przez *Edit lore*, a potem kliknij tam
**Zawsze wycinaj takie linijki**. Mod sam wymyśli wzorzec — z `Cena: $128 lub ★128` zrobi
`Cena:`, żeby łapało też inne ceny.

Puste linijki, które zostają po wyciętym tekście, mod sprząta sam — nie zostaje po nich dziura
w tooltipie.

Da się to też wpisać wprost w `config/itemcopier/config.json`:

```json
"stripLorePatterns": ["Cena:", "ulepszyć", "Kliknij aby"]
```

**Wzorzec może być krótki — i lepiej, żeby był.** Dopasowanie to zwykłe „czy linijka zawiera
ten tekst", więc żeby wyciąć ozdobną kreskę `⎯‹⎯⎯«⎯⎯»⎯⎯›⎯`, wystarczy jeden znak: `«`.
Wklejony cały pasek zadziała tylko na kreski o dokładnie tej samej długości, a sklepy rysują
je różnie — przy innym itemie ta sama kreska może mieć więcej myślników i wzorzec przestanie
pasować.

**Filtry działają w momencie kopiowania.** Item, który już leży w bibliotece, zostaje taki,
jaki był — nowo dodany wzorzec go nie ruszy. Skopiuj go jeszcze raz albo popraw ręcznie przez
*Edit lore*.

**4. Utnij samą końcówkę** — kiedy sklep dokleja na dole stałą stopkę (cena, „kliknij aby
kupić", ozdobna kreska, puste linijki), nie musisz wymyślać wzorca na każdą z nich. Na dole
ekranu **Lore filters** jest **Wycinaj ostatnie linijki** z przyciskami `-` i `+`. Ustawiasz,
ile linijek ma zniknąć od dołu; `0` wyłącza. Ta opcja patrzy tylko na pozycję, nie na treść,
więc łapie też ceny i dopiski, których nie przewidziałeś.

Liczy od lore, które widzisz w tooltipie — po prostu policz linijki od dołu. Cięcie idzie
pierwsze, wzorce działają na tym, co zostanie, więc obie opcje można spokojnie łączyć. Puste
linijki odsłonięte przez cięcie też są sprzątane. W pliku:

```json
"stripLastLoreLines": 4
```

---

## Biblioteka (`B`)

Pełny ekran z całą kolekcją: wyszukiwarka (po nazwie, ID itemu albo serwerze), sortowanie,
**Import** / **Export**, i **Clear all** z potwierdzeniem.

Na pojedynczym itemie (**Details**) masz dodatkowo:

- **Edit lore** — opisane wyżej
- **Save label** — własna nazwa, żeby łatwiej szukać
- **Raw data** — pełne dane itemu do schowka
- **/give** — gotowa komenda do schowka
- **Export .nbt** — plik do przeniesienia na inny komputer

> **O `/give`:** pole czatu w Minecrafcie przyjmuje tylko 256 znaków. Item z lore i atrybutami
> łatwo to przebija, więc komenda zostałaby ucięta i item wyszedłby niekompletny. Mod to
> sprawdza i mówi wprost, kiedy tak jest. Do przenoszenia 1:1 używaj **Export .nbt**.

---

## Ustawienia (zębatka na panelu albo `B` → Settings)

| Opcja | Do czego |
|---|---|
| Panel | Włącz / wyłącz panel przy ekwipunku |
| Panel scale | Powiększenie samego panelu |
| Columns / Max rows | Rozmiar siatki |
| Notifications | Komunikaty na czacie |
| Duplicates | Co przy skopiowaniu tego samego drugi raz |
| Click action | Co robi lewy przycisk na itemie |
| Source server | Czy zapamiętywać, skąd item pochodzi |
| Lore filters | Teksty, po których lore jest wycinane przy kopiowaniu |
| Debug logging | Więcej w logu, gdy coś nie działa |

---

## Twoje rzeczy leżą w

```
.minecraft/config/itemcopier/
├── config.json     ← ustawienia
├── library.nbt     ← biblioteka
└── exports/        ← eksporty i importy
```

Biblioteka przeżywa zmianę serwera, restart gry i restart kompa. Zapis jest robiony tak, że
nawet ubicie gry w trakcie go nie uszkodzi. Gdyby plik jednak padł, mod odkłada go obok jako
`library.nbt.broken-<data>` zamiast kasować.

---

## Czego mod nie zrobi

**Nie skopiuje tego, czego serwer Ci nie wysłał.** Kopiuje dokładnie to, co dostał Twój klient —
czyli to, co widzisz na tooltipie. Jeśli plugin trzyma coś tylko u siebie, tego nie ma i nie da
się tego wyciągnąć z poziomu klienta.

**Item nie zacznie działać na nowym serwerze.** Przeniesiesz wygląd i statystyki. Logiki pluginu,
który go obsługiwał, nie — tam go po prostu nie ma.

**Nie obejdzie braku Creative.** Bez uprawnień wrzucanie jest wyłączone. Biblioteka działa dalej,
tylko do podglądu.

**Może nie wejść na innej wersji gry.** Item z enchantem albo komponentem, którego na serwerze
docelowym nie ma, się nie wczyta. Zobaczysz go jako czerwony slot z `?` i tooltip powie dlaczego —
ale oryginał zostaje w pliku nietknięty.

---

## Coś nie działa

| Co widzisz | Co z tym zrobić |
|---|---|
| `Hover an item slot first.` | Kursor musi być dokładnie na slocie |
| Czerwony slot z `?` | Tego itemu nie da się wczytać tutaj — tooltip powie czemu. Nic nie przepadło |
| `Creative Mode or appropriate permissions required` | Nie masz Creative na tym serwerze |
| `Server would reject this item` | Mod złapał problem i **nie wysłał** pakietu. Powód jest w komunikacie |
| Panel się nie pokazuje | Za wąskie okno — zmniejsz *Columns* albo *Max rows* |
| Nic z powyższych | *Settings → Debug logging → On*, potem zajrzyj do `logs/latest.log` |

---

## Budowanie ze źródeł

```bash
./gradlew build
```

Jar wyląduje w `build/libs/`. Potrzebujesz JDK 25 — tyle wymaga sam Minecraft 26.2.

---

Licencja: All Rights Reserved — [LICENSE.txt](LICENSE.txt)
