# HBM Far View – souhrn projektu (stav 21. 11. 2026)

Tento soubor shrnuje všechno důležité z chatů o modu. Hodí se pro nový chat: stačí ho přiložit nebo říct, ať si ho Claude přečte.

---

## 1. Základní údaje

| Co | Hodnota |
|---|---|
| Název na CurseForge | HBM Far View - Continuum compat (plán: vypustit slovo „compat“, „Far View“ zůstává) |
| Mod id | `hbm_farview` (NIKDY neměnit – rozbil by se config a aktualizace) |
| Verze Minecraftu | 1.21.1, NeoForge 21.1.228+ (instance má 21.1.267), Java 21 |
| Poslední vydaná verze | 0.2.7 (CurseForge), 0.2.8 připravená/vydávaná |
| Licence | LGPL-3.0 (`LICENSE`, `LICENSE.LESSER`, `NOTICE.md`) |
| GitHub | https://github.com/davpec4-creator/HBM-FarView (účet `davpec4-creator`, musí být Public) |
| Závislosti (CurseForge) | NTM - Continuum, Golem Core, Patchouli = Required; Distant Horizons = Optional |

Mod drží hřib (torex) a velké efekty výbuchů NTM Continuum (Fleija, Solinium, Rainbow, Tom) a rakety NTM viditelné a animované i za render distance.

---

## 2. Cesty

**Projekt (zdrojáky):**
`C:\Users\info\OneDrive\Plocha\HBMNTM mod\HBM-FarView`

| Soubor / složka | K čemu |
|---|---|
| `gradle.properties` | `mod_version=` – číslo verze |
| `build.gradle` | sestavení; DH API z `libs/distanthorizons-api-3.3.3.jar` (compileOnly) |
| `src\main\java\com\hbmfarview\` | Java kód |
| `src\main\resources\hbm_farview.mixins.json` | seznam mixinů |
| `src\main\resources\META-INF\neoforge.mods.toml` | metadata modu (display name, závislosti) |
| `src\main\resources\assets\hbm_farview\lang\en_us.json` | názvy položek v okně configu |
| `src\main\resources\assets\hbm_farview\patchouli_books\guide\en_us\entries\changelog\changelog.json` | changelog v herní knize |
| `CurseForge popis.md` | text stránky na CurseForge (Markdown) |
| `README.md`, `NOTICE.md` | pro GitHub; NOTICE obsahuje odkaz na zdrojáky |
| `build\libs\hbm-farview-X.Y.Z.jar` | hotový jar po buildu |
| `build\moddev\artifacts\neoforge-21.1.228-sources.jar` | zdrojáky Minecraftu + NeoForge (Mojang názvy) – na zkoumání vanilly |

**Testovací instance (CurseForge):**
`C:\Users\info\curseforge\minecraft\Instances\HBM Mods test`
- `mods\` – sem jar modu. Je tam: hbm-neoforge-1.0.6, golemcore 1.0.6, DistantHorizons 3.3.3, sodium-neoforge **0.8.13**, iris 1.8.14-beta.1, Patchouli, gemrender
- `config\hbm_farview-common.toml` – `farTrackingRange` (teď 4096)
- `config\hbm_farview-client.toml` – klientské přepínače (od 0.2.8)
- `config\DistantHorizons.toml` – nastavení DH
- `logs\latest.log` – log hry (hledat `hbm_farview`, `error`, `mixin`)

**Knihovny pro kontrolní kompilaci:** `C:\Users\info\.gradle\caches\modules-2\files-2.1\...` (loader 4.0.42, mixinextras, fastutil…)

**Omezení Claudova přístupu:** soubory hlouběji než 7 složek pod připojenou složkou nejdou *číst* (zapisovat ano). Řešení: zkopírovat soubor do kořene projektu, Claude ho přečte, pak se kopie smaže.

---

## 3. Sestavení a test

1. IntelliJ: **File → Reload All from Disk** (vždy po změnách od Clauda!).
2. Gradle → Tasks → build → **build**.
3. Jar z `build\libs` do `mods` instance (starou verzi smazat).
4. Spustit hru, zkontrolovat `latest.log`.

**Pozor:** když má IntelliJ otevřený soubor, který Claude změní, může ho uložit zpátky ve staré verzi (stalo se s `CurseForge popis.md` a `changelog.json`). Před úpravami zavřít záložky nebo dát Reload All from Disk.

---

## 4. Verze a vydání

- Každé vydání = zvýšit `mod_version` v `gradle.properties` o jeden krok (0.2.7 → 0.2.8 → 0.2.9).
- Do herní knihy (`changelog.json`) přidat **novou stránku s číslem verze jako první**, starší stránky nechat.
- Na CurseForge: **Add File** → jar → hodnoty se předvyplní z minulé verze (Client+Server, NeoForge, Java 21, 1.21.1) → Release type R → vložit changelog → Distant Horizons jako **Optional Dependency**.
- Popis stránky: editor přepnout z **WYSIWYG na Markdown**, jinak jsou vidět znaky `##`, `**`.
- Záložka **Source** na CurseForge: GitHub, `davpec4-creator/HBM-FarView`.

**Historie:**
- 0.2.3 – první verze (hřib z dálky)
- 0.2.5 / 0.2.6 – podpora Distant Horizons 3.3.3 (kopce schovávají hřib), hláška Fade Mode
- 0.2.7 – rakety NTM z dálky, kouř raket na dálku (řidší a větší), `farTrackingRange` default i max 4096
- 0.2.8 – okno configu (tlačítko Config v Mods + `/farview config`), klientské přepínače

**Changelog 0.2.8 (CurseForge):**
```
- New config screen. Open it with the Config button in the mod list or with the command /farview config.
- New client settings: far blast effects, far missiles and far missile smoke can each be turned off.
- Changes in the config screen apply at once, without a restart.
```

---

## 5. Ovládání modu (config)

Okno: **Mods → HBM Far View → Config**, nebo ve hře **`/farview config`**. Změny platí hned.

| Soubor | Klíč | Výchozí | Význam |
|---|---|---|---|
| client | `blastEffects` | true | hřib a velké výbuchy z dálky |
| client | `missiles` | true | rakety z dálky |
| client | `missileSmoke` | true | kouř raket z dálky (za 350 bloky řidší a větší) |
| common | `farTrackingRange` | 4096 (max 4096) | do jaké vzdálenosti server posílá efekty hráči; 0 = vypnuto; na serveru mění jen majitel |

`farTrackingRange` neurčuje sílu výbuchu, jen jak daleko je efekt vidět.

**Záhada `.bak` souborů:** když NeoForge nedokáže config přečíst, přejmenuje ho na `...-1.toml.bak` a vytvoří nový s výchozí hodnotou. Proto se ruční úprava kdysi „ztratila“. Upravovat jen `hbm_farview-common.toml`, ne `.bak`.

---

## 6. Jak mod funguje uvnitř (pro opravy chyb)

Řetěz viditelnosti efektu:
1. **Server posílá entitu** dál než normálně – `MixinTrackedEntity` (dosah = `farTrackingRange`).
2. **Klient ji nechá tikat** (animace) – `MixinEntity` (`isAlwaysTicking`).
3. **Kreslí se i mimo obrazovku/vzdálenost** – `MixinEntityRenderer`, `MixinEntityRenderDispatcher`.
4. **Vzdálená ořezová rovina** se zvětší, když je efekt vidět – `MixinGameRenderer`.
5. **Bez mlhy** – `NoFogRenderTypes`, `MixinTorexRenderer`, `MixinCloudRenderTypes`, `MixinFleijaRenderDistance`.

Klíčové třídy:
- `FarView.java` – seznam entit (5 efektů + 19 raket `hbm:entity_missile_*`), `isFarVisible` (server), `isShownFar` (klient, bere v úvahu config).
- `FarViewConfig.java` – common config; `FarViewClientConfig.java` – klientský config.
- `client/HbmFarViewClient.java` – registrace klientského configu a tlačítka Config.
- `client/FarViewCommand.java` – příkaz `/farview config`.
- `mixin/client/MixinHbmParticleHandlers.java` – NTM vypíná kouř raket nad 350 bloků; mixin limit zvedá na `farTrackingRange` a kouř s dálkou řídne (méně, větší obláčky).
- `mixin/client/MixinCompatParticle.java` + `client/NoFogParticles.java` – kouř raket bez mlhy.
- `client/DhCompat.java`, `DhDepthPass.java`, `DhOcclusion.java`, `DhRenderHook.java` – Distant Horizons: hloubka DH kopců (bez shaderů, Fade = None), jinak raycast.
- `client/DhFadeHint.java` – hláška v chatu, když je Vanilla Fade Mode zapnutý (kontrola každých 40 ticků).
- `FarViewLog.java` – logování.

Mixiny do cizích modů mají `require = 0` → když se cíl po aktualizaci NTM/DH/Sodia změní, hra nespadne, jen ta funkce přestane fungovat.

**Distant Horizons:** Vanilla Fade Mode musí být **None** (Advanced Options > Graphics > Quality). Shadery s DH nefungují správně.

---

## 7. MRAKY – plán a výzkum (další velký krok)

### Rozhodnutí
- **Vlastní mraky na celé obloze** (2. nápad), ne jen v oblasti výbuchu.
- Mraky jako **3D mřížka buněk s patry** – proto jde:
  - **tunel po raketě**, která proletí mrakem (buňky se vyříznou, okraje roztrhané, pak se fade efektem zatáhne),
  - **díra a rozfoukání od hřibu**, po zmizení hřibu se mraky vrátí (fade).
- Styl „mezi vanillou a Simple Clouds“: kostičky menší než vanilla (návrh 8×4 bloky), 1–3 patra, stupňovitý tvar, vršek bílý, spodek šedomodrý, lehký pixelový šum; **bez černého obrysu**.
- Ne skutečné entity – jen data v paměti klienta, vlastní render (lehčí, bez serveru a sítě).
- Shadery zatím nepodporujeme.
- V configu přepínač „vlastní mraky“ (vypnuto → vrátí se původní mraky).
- Když má hráč Simple Clouds / WeatherPhysics / jiný mod na mraky → naše se samy vypnou.
- Kód mraků držet odděleně od NTM části (přehlednost, případný port).

### Zjištění z výzkumu (z jarů v instanci)
- **Vanilla 1.21.1:** mraky kreslí `LevelRenderer.renderClouds` → `buildClouds` → `cloudBuffer` (přestavba při `generateClouds`). Tvar z textury `clouds.png` čte **grafická karta**. Buňka 12×4 bloky, výška z `level.effects().getCloudHeight()` (+0.33), posun 0,03 bloku/tick, odstíny stěn: vršek 1.0, boky 0.9/0.8, spodek 0.7, průhlednost 0.8. Třída `CloudRenderer` existuje až od 1.21.2.
- **Sodium 0.8.13:** na začátku `LevelRenderer.renderClouds` zruší vanillu a kreslí přes `net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer`. Texturu načte **jednou do paměti procesoru**, geometrii přestaví jen při změně parametrů.
- **Vypnutí obou najednou:** mixin na **volání** `renderClouds` v `LevelRenderer.renderLevel` (např. MixinExtras `@WrapWithCondition`) – nezáleží na pořadí mixinů se Sodiem; naše mraky se nakreslí na stejném místě.
- **Distant Horizons 3.3.3 má vlastní mraky:** `CloudRenderHandler`, 3 vrstvy velkých kvádrů (128×32 bloků), volba `enableCloudRendering`; s `overrideVanillaGraphicsSettings` vypíná vanilla mraky. → S DH hráč nejspíš vidí DH mraky. Musíme je vypnout (API?) nebo upozornit v chatu. Zjistit.
- DH má API na vlastní kvádry (box groups), které se správně schovávají za DH kopce – možná použití pro vzdálené mraky.
- **Iris** má vlastní mixin do Sodium `CloudRenderer` → konflikt se shadery.

### Návrh generátoru (k rozhodnutí)
1. Tvar ze **šumu** (noise) – doporučeno (nekonečné, neopakuje se), alternativa vanilla `clouds.png` (opakuje se po ~3 km).
2. Patra 1–3 podle šumu + jemnější šum na hrboly.
3. Vítr – pomalý posun jedním směrem.
4. Počasí – déšť víc a tmavší mraky, bouřka nejtmavší.
5. Barva podle denní doby.
6. Výkon – obloha po čtvercových dílech kolem hráče, každý díl postavený jednou v grafice, přestavba jen při změně.
7. Dosah – nastavitelný, i za render distance.

**Otevřené otázky:** šum vs. textura; max 2 nebo 3 patra; výška (vanilla ~Y 192 nebo výš); co do prvního testu.
**Návrh prvního testu:** vypnout vanilla/Sodium mraky + šum + patra + vítr. Počasí, barvy dne a díry až potom.

### Pořadí práce
1. ~~Config~~ (hotovo v 0.2.8)
2. Vlastní 3D mraky (vypnout původní, generátor, vzhled, výkon)
3. Tunely po raketách a díra/rozfoukání od hřibu
4. Později: vylepšená tlaková vlna / lepší výbuch, případně kompatibilita se Simple Clouds

---

## 8. Další nápady a otevřené věci

- Přejmenovat na CurseForge (bez „compat“) + `display name` v `neoforge.mods.toml` + nadpis popisu – udělat spolu s některým updatem.
- Komentář Terri1237: „Is compatible with voxy neo ports?“ – odpověď: netestováno; hlavní funkce by měla fungovat, schovávání za kopci jen s DH.
- Možné prosby o port na 1.20.1 – nic neslibovat; Continuum na 1.20.1 není (jiné verze HBM mají jiný kód).
- Dřívější nápady: Create compat, Long Range Designator přes DH raycast, další rakety (Lambda, Soyuz, artilerie).
- `Jak sestavit.txt` je zastaralý (0.2.3, 1024) – README ho nahrazuje, lze smazat.
- V kategorii HBM je Far View podle hledání nejspíš první doplněk, který řeší vzhled výbuchů a raket na obloze (neověřeno úplně – na stránku nepsat „první“).

---

## 9. GitHub (GitHub Desktop)

- Repozitář: `HBM-FarView`, lokálně ve složce projektu, publikovaný veřejně.
- Po změnách: GitHub Desktop → napsat Summary → **Commit to main** → **Push origin**.
- Hláška „LF line endings… CRLF“ je neškodná.
- Licence při vytváření repozitáře: **None** (vlastní LICENSE už existuje).

---

## 10. Jak se mnou (uživatelem) pracovat

- Píšu česky; anglické texty (komentáře, popisy) překládat do češtiny.
- Do modu přidávat jen to, o co požádám (na extra věci se nejdřív zeptat); styl jako vanilla Minecraft; popisy krátké, věcné, bez emoji a hypu.
- Kód čistý, bez komentáře na každém řádku, ale vysvětlit, co dělá.
- Logo, screenshoty a stránku na CurseForge dělám sám.
- Neověřená tvrzení označit jako neověřená.
- Když je chat moc dlouhý, upozornit hned na začátku odpovědi.
