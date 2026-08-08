# MTVehicles HorizonCity — guida operativa completa

Questa guida descrive la build HorizonCity `2.5.18` per una persona che non conosce il progetto. Comandi, permessi e placeholder sono ricavati direttamente dal codice della build.

## 1. Che cosa fa il plugin

MTVehicles gestisce veicoli basati su ArmorStand, modelli vanilla o ItemsAdder, carburante, salute, bagagliaio, proprietari, accessi, menu di consegna, acquisto tramite Vault, storage YAML/MariaDB, autovelox e sirene FDO.

Un veicolo possiede:

- una **targa** univoca, che identifica la singola istanza;
- un **UUID di modello** in `vehicles.yml`, che identifica la skin acquistabile o consegnabile;
- un **proprietario**;
- una lista **riders**, autorizzata a guidare;
- una lista **members**, autorizzata a usare i sedili passeggero;
- caratteristiche statiche in `vehicles.yml` e stato persistente in `vehicleData.yml` o MariaDB.

## 2. Requisiti e installazione

1. Usare Paper `1.21.11` e Java `21`.
2. Copiare `MTVehicles.jar` nella cartella `plugins`.
3. Installare il resource pack usato dai modelli. Se `vehicles.yml` contiene ID come `horizon_veicoli:nome`, installare e caricare anche ItemsAdder.
4. Avviare una volta il server per generare i file.
5. Configurare i file in `plugins/MTVehicles/` e riavviare completamente.

Dipendenze opzionali:

| Plugin | Funzione |
|---|---|
| ItemsAdder | Risolve skin e icone con namespace `namespace:item`. |
| PlaceholderAPI | Registra i placeholder `%mtv_...%`. |
| Vault + economia | Abilita acquisti, prezzi e distributori a pagamento. |
| WorldGuard | Abilita gas station e restrizioni regionali. |
| Skript | Abilita condizioni, effetti ed eventi dell'addon MTVehicles. |

Non tenere due JAR di MTVehicles nella cartella plugin. Dopo la sostituzione del JAR eseguire un riavvio completo, non PlugMan e non un semplice reload del server.

## 3. Comando principale e alias

Il comando principale è `/minetopiavehicles`. Tutti questi alias sono equivalenti:

`/mtv`, `/minetopiav`, `/mtvehicles`, `/mtvehicle`, `/vehicle`, `/veicolo`.

Negli esempi viene usato `/mtv`.

### Come il plugin individua il veicolo

I comandi relativi a un veicolo normalmente usano:

1. il veicolo sul quale il mittente è seduto, se ne è il proprietario; oppure
2. il veicolo che il mittente tiene nella mano principale.

Per evitare errori amministrativi, prendere il veicolo in mano prima di cambiare proprietario, accessi o dati.

## 4. Membri, guidatori e sedili

- **Proprietario**: controllo completo del veicolo.
- **Rider**: può guidare, quindi usare il sedile `1`.
- **Member**: può salire come passeggero, ma non guidare.
- **Accesso completo**: il giocatore è sia rider sia member.

Comandi tipici:

```text
/mtv addmember Nick       # solo passeggero
/mtv addrider Nick        # solo guidatore
/veicolo aggiungi Nick    # guidatore + passeggero con un solo salvataggio
/veicolo rimuovi Nick     # rimuove entrambi gli accessi
```

I sedili passeggero vengono creati quando il veicolo viene attivato da un guidatore. Il member fa clic destro sul sedile libero del veicolo già attivo. La build `2.5.18` esegue l'aggancio dopo l'evento di click e lo verifica nel tick successivo, senza teletrasportare chi è già vicino al veicolo; il messaggio di salita arriva soltanto dopo la conferma del mount. Non serve un ulteriore permesso: `mtvehicles.ride` è soltanto un bypass amministrativo delle liste, mentre `addmember` è sufficiente per un passeggero normale.

La numerazione dei sedili segue l'ordine di `seats` in `vehicles.yml`:

- sedile `1`: guidatore;
- sedile `2`: primo passeggero;
- sedile `3`: secondo passeggero;
- e così via.

### Sedili pubblici

Per rendere un sedile pubblico su tutte le skin della categoria:

```yaml
seats:
  - x: -0.1
    y: -1.2
    z: 0.6
  - x: -0.1
    y: -1.2
    z: -0.6
  - x: -1.2
    y: -1.2
    z: 0.6
    public: true
```

Per rendere pubblici solamente i sedili posteriori di una skin specifica:

```yaml
cars:
  - name: "Volante Polizia"
    SkinItem: LEATHER_CHESTPLATE
    itemDamage: 35
    uuid: VOLPOL
    publicSeats: [3, 4]
```

Dopo la modifica eseguire `/mtv reload`. Non inserire numeri maggiori del numero di voci presenti in `seats`.

## 5. Tutti i comandi

### Informazione e utilità

| Comando | Permesso | Descrizione |
|---|---|---|
| `/mtv` | Nessuno | Apre l'help. |
| `/mtv help` | Nessuno | Mostra i comandi; con `mtvehicles.admin` mostra anche la sezione amministrativa. |
| `/mtv admin` | Nessuno | Alias dell'help. |
| `/mtv info` | Nessuno | Mostra dati del veicolo selezionato; UUID e prezzo richiedono `mtvehicles.admin`. |
| `/mtv language` | `mtvehicles.language` o `mtvehicles.admin` | Apre il menu della lingua. |
| `/mtv version` | `mtvehicles.admin` | Mostra versione e informazioni diagnostiche. |
| `/mtv about` | `mtvehicles.admin` | Alias di `version`. |

### Proprietario e accessi

| Comando | Permesso | Descrizione |
|---|---|---|
| `/mtv public` | Nessuno | Rende pubblico il veicolo selezionato. Usarlo con il veicolo in mano. |
| `/mtv private` | Nessuno | Ripristina l'accesso tramite proprietario/liste. Usarlo con il veicolo in mano. |
| `/mtv addmember <giocatore>` | Nessuno | Aggiunge accesso ai sedili passeggero. Il giocatore deve essere online. |
| `/mtv removemember <giocatore>` | Nessuno | Rimuove l'accesso passeggero. |
| `/mtv addrider <giocatore>` | Nessuno | Aggiunge accesso al posto guida. |
| `/mtv removerider <giocatore>` | Nessuno | Rimuove l'accesso al posto guida. |
| `/veicolo aggiungi <giocatore>` | Nessuno | Aggiunge contemporaneamente rider e member. |
| `/veicolo rimuovi <giocatore>` | Nessuno | Rimuove contemporaneamente rider e member. |
| `/veicolo sali <giocatore> [sedile]` | `mtvehicles.forcemount` | Forza un giocatore online sul primo posto passeggero libero o sul numero indicato. Il veicolo deve essere attivo per i passeggeri; il sedile `1` può attivarlo. |
| `/veicolo scendi <giocatore>` | `mtvehicles.forcemount` | Fa scendere il giocatore dal veicolo selezionato. |
| `/mtv trunk` | Proprietario oppure `mtvehicles.kofferbak` | Apre il bagagliaio del veicolo selezionato. |
| `/mtv baggage` | Come `trunk` | Alias di `trunk`. |

### Creazione, acquisto e recupero

| Comando | Permesso | Descrizione |
|---|---|---|
| `/mtv menu` | `mtvehicles.menu` | Apre il menu di consegna gratuita configurato in `vehicles.yml`. |
| `/mtv give <giocatore> <MODELLO> [--voucher:true]` | `mtvehicles.givecar` o `mtvehicles.givevoucher` | Consegna un veicolo o voucher. `MODELLO` è il nome del tab completer, normalmente maiuscolo con `_`. |
| `/mtv buy <MODELLO> [--voucher:true]` | `mtvehicles.buycar` o `mtvehicles.buyvoucher` | Acquista tramite Vault e il prezzo della skin. |
| `/mtv restore [giocatore]` | `mtvehicles.restore` | Apre il menu dei veicoli persistenti, opzionalmente filtrato per proprietario. |
| `/mtv setowner <giocatore>` | `mtvehicles.setowner`, salvo opzione `putOneselfAsOwner` | Cambia proprietario e azzera rider/member. Il giocatore deve essere online. |

### Modifica e manutenzione

| Comando | Permesso | Descrizione |
|---|---|---|
| `/mtv edit` | `mtvehicles.edit` | Apre il menu di modifica del veicolo tenuto in mano. |
| `/mtv edit <parametro> <valore>` | `mtvehicles.edit` | Modifica il veicolo tenuto dal mittente. |
| `/mtv edit <giocatore> <parametro> <valore>` | `mtvehicles.edit` e `mtvehicles.admin` per altri giocatori | Modifica il veicolo tenuto dal bersaglio online. |
| `/mtv repair` | `mtvehicles.repair` | Ripristina la salute del veicolo tenuto in mano. |
| `/mtv refill` | `mtvehicles.refill` | Porta il carburante del veicolo al 100%. |
| `/mtv refuel` | `mtvehicles.refill` | Alias di `refill`. |
| `/mtv fuel` | `mtvehicles.benzine` | Apre il menu delle taniche. |
| `/mtv benzine` | `mtvehicles.benzine` | Alias di `fuel`. |
| `/mtv givefuel <giocatore> <litri>` | `mtvehicles.givefuel` | Consegna una tanica piena. |
| `/mtv delete` | `mtvehicles.delete` | Elimina definitivamente record e item del veicolo tenuto. Non usarlo come semplice despawn. |

Parametri supportati da `/mtv edit`:

`licenseplate`, `name`, `fuel`, `fuelusage`, `trunkrows`, `accelerationspeed`, `maxspeed`, `brakingspeed`, `frictionspeed`, `maxspeedbackwards`, `rotationspeed`, `glowing`, `fuelenabled`, `trunkenabled`.

I parametri booleani accettano `true` o `false`. Per nome e targa senza spazi usare direttamente il valore; per modifiche più complesse è preferibile il menu GUI.

### Amministrazione e diagnostica

| Comando | Permesso | Descrizione |
|---|---|---|
| `/mtv reload` | `mtvehicles.reload` | Ricarica YAML, menu, profili sirena e autovelox. Non cambia backend storage a caldo. |
| `/mtv despawn <targa>` | `mtvehicles.despawn` | Rimuove le entità del veicolo senza cancellare il record. |
| `/mtv despawnall` | `mtvehicles.despawnall` | Rimuove tutti i veicoli realmente spawnati nei mondi caricati, senza cancellare i record. |
| `/mtv update` | `mtvehicles.update` | Usa l'updater soltanto se `autoUpdate: true`. Per questa build personalizzata è preferibile aggiornare manualmente. |
| `/mtv vault` | `mtvehicles.admin` | Mostra lo stato di Vault/economia. |
| `/mtv vault setup` | `mtvehicles.admin` | Ritenta l'aggancio al provider economico. |

### Autovelox e telelaser

`/mtv speedcamera` è alias di `/mtv autovelox`. Sono disponibili anche gli alias italiani delle operazioni.

| Comando | Permesso | Descrizione |
|---|---|---|
| `/mtv autovelox add <limite-kmh> [raggio]` | `mtvehicles.speedcamera.admin` | Crea un autovelox statico nella posizione del giocatore. Alias operazione: `aggiungi`. |
| `/mtv autovelox remove [id]` | `mtvehicles.speedcamera.admin` | Rimuove l'ID indicato o il più vicino entro 10 blocchi. Alias: `rimuovi`. |
| `/mtv autovelox list` | `mtvehicles.speedcamera.admin` | Elenca gli autovelox. Alias: `lista`. |
| `/mtv autovelox dynamic [limite-kmh]` | `mtvehicles.speedcamera.dynamic` | Attiva/disattiva il telelaser. Alias: `dinamico`, `telelaser`. |

## 6. Tutti i permessi

I permessi amministrativi sono dichiarati con default `op`. Il nodo `mtvehicles.admin` eredita tutti i permessi amministrativi elencati nel suo blocco `children` in `plugin.yml`. I comandi proprietario per liste e stato pubblico non richiedono un nodo separato.

| Permesso | Effetto reale nel codice |
|---|---|
| `mtvehicles.admin` | Help amministrativo, dettagli avanzati di `info`, `version`, `vault`, modifica del veicolo tenuto da un altro giocatore e tutti i children amministrativi. |
| `mtvehicles.anwb` | Ignora il blocco di recupero dei veicoli dall'acqua. |
| `mtvehicles.benzine` | Apre il menu taniche. |
| `mtvehicles.buycar` | Compra veicoli con Vault. |
| `mtvehicles.buyvoucher` | Compra voucher con Vault. |
| `mtvehicles.delete` | Cancella definitivamente un veicolo. |
| `mtvehicles.despawn` | Despawna per targa. |
| `mtvehicles.despawnall` | Despawna tutti i veicoli attivi. |
| `mtvehicles.edit` | Modifica dati veicolo. |
| `mtvehicles.filljerrycans` | Consente il riempimento quando `gasStations.fillJerryCans.needPermission` è `true`. |
| `mtvehicles.forcemount` | Usa `/veicolo sali` e `/veicolo scendi`. |
| `mtvehicles.givecar` | Consegna veicoli. |
| `mtvehicles.givefuel` | Consegna taniche. |
| `mtvehicles.givevoucher` | Consegna voucher. |
| `mtvehicles.kofferbak` | Apre bagagliai senza esserne proprietario. |
| `mtvehicles.language` | Apre il menu lingua. |
| `mtvehicles.menu` | Apre il menu di consegna veicoli. |
| `mtvehicles.nolimit` | Ignora il limite del menu veicoli. |
| `mtvehicles.limit.X` | Limita a `X` i veicoli ottenibili dal menu; esempio `mtvehicles.limit.6`. Va assegnato come nodo numerico esatto. |
| `mtvehicles.oppakken` | Ignora i normali vincoli di proprietà/configurazione nel recupero del veicolo. |
| `mtvehicles.refill` | Rifornisce completamente un veicolo. |
| `mtvehicles.reload` | Ricarica le configurazioni. |
| `mtvehicles.repair` | Ripara un veicolo. |
| `mtvehicles.restore` | Apre il menu di recupero. |
| `mtvehicles.ride` | Bypass completo delle liste rider/member; permette di guidare o sedersi su veicoli privati. Assegnare solo a staff fidato. |
| `mtvehicles.setowner` | Cambia proprietario. |
| `mtvehicles.speedcamera.admin` | Gestisce autovelox statici. |
| `mtvehicles.speedcamera.dynamic` | Usa il telelaser. |
| `mtvehicles.update` | Usa updater e riceve avvisi di aggiornamento quando abilitati. |

`mtvehicles.filljerrycansforfree` compare in vecchia documentazione upstream, ma non viene letto dal codice di questa build: non va considerato un permesso operativo.

## 7. Tutti i placeholder PlaceholderAPI

Richiedono PlaceholderAPI. L'identificatore dell'espansione è `mtv`.

| Placeholder | Risultato |
|---|---|
| `%mtv_fuel_pricePerLitre%` | Prezzo per litro configurato per le gas station. Funziona senza un giocatore dentro un veicolo. |
| `%mtv_vehicle_licensePlate%` | Targa del veicolo occupato. |
| `%mtv_vehicle_name%` | Nome persistente del veicolo occupato. |
| `%mtv_vehicle_type%` | Tipo leggibile: auto, barca, elicottero, ecc. |
| `%mtv_vehicle_fuel%` | Carburante con massimo due decimali e `%`; vuoto se disabilitato. |
| `%mtv_vehicle_speed%` | Velocità in `blocks/sec`. |
| `%mtv_speed%` | Velocità numerica in km/h con un decimale, adatta a HUD e scoreboard. |
| `%mtv_vehicle_speed_kmh%` | Alias di `%mtv_speed%`. |
| `%mtv_vehicle_speed_raw%` | Velocità km/h non arrotondata. |
| `%mtv_vehicle_maxspeed%` | Velocità massima in `blocks/sec`. |
| `%mtv_vehicle_place%` | `DRIVER` oppure `PASSENGER`. |
| `%mtv_vehicle_seats%` | Numero totale di sedili configurati. |
| `%mtv_vehicle_uuid%` | UUID del modello/skin in `vehicles.yml`, non UUID dell'entità Minecraft. |
| `%mtv_vehicle_owner%` | Nome del proprietario. |

Quando il giocatore è offline o non è realmente montato su un sedile MTVehicles, i placeholder testuali restituiscono una stringa vuota; quelli numerici della velocità restituiscono `0`.

## 8. File di configurazione

| File | Contenuto |
|---|---|
| `config.yml` | Regole globali: carburante, danni, guida, mondi, WorldGuard, gas station e menu. |
| `vehicles.yml` | Categorie, skin, UUID, ItemsAdder, prestazioni, sedili, FDO e prezzi. |
| `sirens.yml` | Profili, volume, intervallo e toni delle sirene. |
| `storage.yml` | Backend YAML/MariaDB e pool database. |
| `vehicleData.yml` | Stato persistente quando il backend è YAML. Non modificarlo a server acceso. |
| `speedcameras.yml` | Autovelox statici creati dai comandi. |
| `supersecretsettings.yml` | Lingua e versioni interne dello schema config. |
| `messages/messages_*.yml` | Testi tradotti. |

Le nuove chiavi mancanti vengono aggiunte senza sovrascrivere i valori esistenti. Liste personalizzate come `voertuigen` non vengono sostituite con quelle incluse nel JAR.

## 9. Sirene FDO

Un mezzo deve avere `fdo: true` e un profilo valido:

```yaml
- name: "FDO - Ambulanza"
  vehicleType: CAR
  fdo: true
  sirenType: AMBULANCE
```

Oppure l'override può essere applicato alla singola skin. Profili inclusi: `POLICE`, `AMBULANCE`, `FIRE_BRIGADE`, `CARABINIERI`. Il guidatore preme `F` per attivare o disattivare la sirena. I toni sono riprodotti dalla posizione corrente del veicolo con categoria audio `MASTER`.

## 10. Procedura di verifica dopo un aggiornamento

1. Rimuovere il vecchio JAR e installare una sola copia della nuova build.
2. Riavviare completamente Paper.
3. Controllare `/mtv version` e il log di startup.
4. Entrare come proprietario e guidare.
5. Eseguire `/mtv addmember Nick` con il veicolo selezionato.
6. Tenere il guidatore dentro il veicolo e far fare clic destro al member su un sedile passeggero libero.
7. Verificare che il messaggio arrivi solo quando il giocatore risulta realmente seduto.
8. Provare `/veicolo aggiungi Nick`, verificando una sola notifica.
9. Aprire `/mtv menu` e controllare categoria e skin selezionata.
10. Su un mezzo `fdo: true`, premere `F` e verificare il suono a distanza.

Se il member riceve ancora un errore di aggancio, controllare nel log eventuali plugin che cancellano `PlayerTeleportEvent`, rimuovono passeggeri o gestiscono mount custom. Verificare inoltre che il veicolo sia attivo e che il sedile cliccato sia libero.

## 11. Build e supporto tecnico

Compilazione locale:

```bash
mvn clean package
```

Artefatto risultante:

```text
target/MTVehicles.jar
```

Per dettagli specifici su MariaDB e traffico consultare anche `DOCUMENTAZIONE-STORAGE-MARIADB.md` e `DOCUMENTAZIONE-TRAFFICO-FDO.md`.
