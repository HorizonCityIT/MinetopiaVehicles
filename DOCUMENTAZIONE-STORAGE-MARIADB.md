# MTVehicles - storage MariaDB production

## Cosa viene salvato dove

Le definizioni statiche restano volutamente su file:

- `vehicles.yml`: modelli, prestazioni di base, namespace ItemsAdder, flag FDO;
- `sirens.yml`: sequenze audio delle sirene;
- `config.yml` e `storage.yml`: configurazione del plugin;
- `speedcameras.yml`: posizioni statiche degli autovelox, un file piccolo modificato solo dai comandi amministrativi.

MariaDB contiene invece lo stato persistente e variabile di ogni veicolo: targa, proprietario, riders, membri, carburante, salute, stato pubblico, bagagliaio, skin e namespace, NBT e personalizzazioni delle prestazioni. Lo schema mantiene anche colonne indicizzate per proprietario e tipo, più un payload versionato che preserva eventuali campi legacy o aggiunti da altre versioni.

Velocità corrente, entità/ArmorStand attive, occupanti online, telelaser attivi, cooldown autovelox e stato corrente delle sirene restano esclusivamente in RAM. Non avrebbe senso scriverli nel database e causerebbe carico inutile.

## Preparazione di MariaDB

Creare un database e un utente dedicati. Sostituire la password d'esempio:

```sql
CREATE DATABASE mtvehicles
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'mtvehicles'@'IP_DEL_SERVER_MINECRAFT'
  IDENTIFIED BY 'PASSWORD_FORTE';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX
  ON mtvehicles.*
  TO 'mtvehicles'@'IP_DEL_SERVER_MINECRAFT';

FLUSH PRIVILEGES;
```

Non usare `root` e non assegnare privilegi globali. Se MariaDB è sulla stessa macchina, usare `127.0.0.1` sia nella configurazione sia nel vincolo dell'utente.

## Configurazione del plugin

Avviare una volta il nuovo JAR per far creare `plugins/MTVehicles/storage.yml`, quindi spegnere il server e impostare:

```yaml
storage:
  type: MARIADB
  mariadb:
    host: 127.0.0.1
    port: 3306
    database: mtvehicles
    username: mtvehicles
    password: "PASSWORD_FORTE"
    tablePrefix: "mtv_"
    sslMode: DISABLE
    flushIntervalSeconds: 5
    pool:
      maximumPoolSize: 6
      minimumIdle: 1
      connectionTimeoutMs: 5000
      maxLifetimeMs: 1800000
```

Con un database remoto è consigliato `VERIFY_FULL` con certificato attendibile. Non eseguire `/vehicle reload` per cambiare backend: il cambio tra YAML e MariaDB viene applicato solo a un riavvio completo, così non si invalidano scritture già in coda.

### Aggiornamento automatico dei config

Ad ogni avvio e ad ogni `/vehicle reload`, il plugin confronta i file YAML presenti sul server con quelli inclusi nella versione installata. Le nuove chiavi vengono aggiunte automaticamente insieme ai relativi commenti, mentre valori già configurati come password, host, namespace, veicoli e sirene non vengono sovrascritti. La scrittura avviene tramite sostituzione atomica del file.

`vehicleData.yml` è escluso da questa sincronizzazione perché contiene dati dinamici. Le modifiche strutturali non additive dei normali config dovranno continuare a essere accompagnate da un convertitore esplicito.

## Avvio MariaDB senza migratori file

Quando `storage.type` è `MARIADB`, il database è l'unica fonte autorevole. Il plugin:

1. crea le tabelle InnoDB `mtv_metadata` e `mtv_vehicles`;
2. carica direttamente le targhe MariaDB nella cache RAM;
3. ignora completamente eventuali dati rimasti in `vehicleData.yml`;
4. non crea backup `pre-mariadb` e non importa dati dal file.

Il file di emergenza creato soltanto quando MariaDB non riesce a ricevere le ultime scritture durante lo shutdown resta una protezione di crash recovery, non un migratore ordinario di `vehicleData.yml`.

## Integrità delle nuove targhe

Le nuove targhe generate automaticamente seguono il formato Horizon Roleplay `hz001rp`–`hz999rp`. L’allocatore usa sempre il numero libero più basso: se `hz001rp`–`hz007rp` e `hz009rp`–`hz010rp` sono occupate, ma `hz008rp` è stata eliminata dal database, il veicolo successivo riceve precisamente `hz008rp`. Dopo `hz999rp` la sequenza continua con `hz001rq`, poi `hz001rr` e così via fino a `hz999zz`.

La disponibilità viene ricostruita dalla cache caricata dal database a ogni avvio e aggiornata immediatamente a ogni registrazione, rinomina o cancellazione. Menu e anteprime non prenotano targhe. Una prenotazione avviene solo quando inizia una consegna reale e viene liberata automaticamente se l’inventario è pieno o la registrazione fallisce. Le targhe create dalle versioni precedenti restano valide e non vengono rinominate.

La registrazione di una nuova identità segue ora obbligatoriamente questa sequenza:

1. prepara item e dati in memoria senza creare righe;
2. consegna realmente l'item all'inventario del proprietario;
3. verifica targa e proprietario sull'item consegnato;
4. registra la targa nella cache e quindi nella coda MariaDB.

Se l'inventario è pieno, ItemsAdder non produce l'item, la targa è già occupata o la registrazione fallisce, l'item viene annullato e non viene creata alcuna riga. `VehicleDataConfig.set()` non può più creare implicitamente targhe mancanti. Il vecchio doppio inserimento del riscatto voucher e i comandi legacy `givecar`, `givevoucher`, `buycar` e `buyvoucher` sono stati rimossi.

## Despawn amministrativo sicuro

Il comando `/vehicle despawnall` (alias principale `/mtv despawnall`) richiede il permesso `mtvehicles.despawnall`, assegnato agli operatori per impostazione predefinita. Il comando esamina una sola volta le entità dei mondi caricati e rimuove esclusivamente i veicoli che:

- hanno una targa presente nello storage attivo, YAML o MariaDB;
- possiedono realmente l'entità principale `MTVEHICLES_MAIN_<targa>` in gioco.

Le vecchie righe presenti nello storage ma prive di un veicolo spawnato vengono contate e ignorate. Il comando non cancella righe dal database e non modifica proprietario, inventario, carburante o altri dati persistenti; rimuove soltanto le entità attive e pulisce lo stato runtime associato.

Di conseguenza un normale `/vehicle despawn` o `/vehicle despawnall` non libera una targa: il veicolo continua a esistere ed è ripristinabile. Lo slot numerico torna disponibile solo quando la relativa identità viene eliminata realmente dal database, per esempio con `/vehicle delete`.

Se MariaDB è configurato ma non disponibile, il plugin non si avvia: usare automaticamente un file YAML ormai vuoto o obsoleto potrebbe duplicare o perdere veicoli. Se MariaDB cade durante il gioco, le modifiche rimangono accorpate in RAM e vengono ritentate senza bloccare il thread Minecraft. Se non è ancora possibile svuotare la coda allo shutdown, viene creato `vehicleData-emergency.yml`; al successivo collegamento riuscito quel file viene ripristinato automaticamente e rinominato come recuperato.

## Prestazioni e consistenza

- Tutte le letture di gameplay arrivano dalla cache già esistente, non da SQL.
- `set()` marca soltanto la targa come modificata: operazione O(1).
- Più cambi sulla stessa targa vengono fusi; ogni intervallo produce al massimo un record aggiornato per targa.
- Le scritture vengono eseguite da un solo writer asincrono e in transazione batch.
- HikariCP gestisce un pool piccolo e limitato; aumentarlo non migliora il tick perché il gameplay non interroga il database.
- Gli errori ripetuti sono limitati nei log e i dati restano in coda.
- Lo schema e il payload hanno una versione. Una versione del plugin più vecchia rifiuta uno schema database più nuovo invece di danneggiarlo.

La cache non è un sistema di sincronizzazione live tra più server Minecraft. Due server che modificano contemporaneamente la stessa targa usano la regola “ultima scrittura completata”; per una rete multi-server va aggiunto un livello di ownership/messaging prima di condividere le stesse targhe.

## Verifica dopo il deploy

Nel log deve apparire una riga simile a:

```text
Persistent vehicle storage: MariaDB (N vehicles cached in memory, asynchronous write-behind enabled).
```

Controlli SQL:

```sql
SELECT meta_key, meta_value FROM mtv_metadata;
SELECT COUNT(*) AS vehicles FROM mtv_vehicles;
SELECT license_plate, owner_uuid, vehicle_type, updated_at
FROM mtv_vehicles
ORDER BY updated_at DESC
LIMIT 20;
```

Test operativo consigliato su una copia del server:

1. creare un veicolo con spazio disponibile e verificare la nuova riga SQL;
2. tentare una consegna con inventario pieno e verificare che il conteggio SQL non aumenti;
3. riscattare un voucher e verificare che venga creata una sola targa;
4. modificare carburante, proprietario e contenuto di un bagagliaio;
5. attendere almeno `flushIntervalSeconds` e verificare `updated_at`;
6. riavviare e controllare gli stessi veicoli in gioco.

## Backup

In produzione eseguire backup MariaDB consistenti (dump o backup fisico) insieme al resto dei dati del server.
