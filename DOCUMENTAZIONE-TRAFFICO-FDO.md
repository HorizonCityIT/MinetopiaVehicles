# MTVehicles — autovelox, velocità e sirene FDO

Questa estensione comprende autovelox statici persistenti, telelaser dinamico, placeholder della velocità in km/h, sirene FDO configurabili ed evento API per integrare multe o sistemi dispatch.

## Autovelox statici

Gli autovelox statici sono punti logici: non viene generata automaticamente una struttura visibile. Il tecnico può costruire il modello o posizionare un custom block nella stessa posizione, quindi eseguire:

```text
/vehicle autovelox add <limite-kmh> [raggio]
/vehicle autovelox remove [id]
/vehicle autovelox list
```

Esempio:

```text
/vehicle autovelox add 70 8
```

Crea un autovelox nella posizione del giocatore con limite di 70 km/h e raggio di 8 blocchi. Il raggio consentito è 1–64 blocchi. Senza ID, `remove` elimina quello più vicino entro 10 blocchi.

Gli autovelox vengono salvati automaticamente in `plugins/MTVehicles/speedcameras.yml` e ricaricati con `/vehicle reload`. Quando viene superato il limite:

- il conducente riceve velocità, limite e targa;
- viene emesso `VehicleSpeedCameraEvent`;
- lo stesso autovelox non segnala nuovamente la stessa targa per 10 secondi.

Il plugin non preleva automaticamente denaro: una multa può essere implementata ascoltando l’evento API.

Permesso: `mtvehicles.speedcamera.admin`.

## Telelaser dinamico

```text
/vehicle autovelox dynamic [limite-kmh]
```

Esempio: `/vehicle autovelox dynamic 90`. Dopo l’attivazione, guardare un componente del veicolo entro 80 blocchi. L’action bar mostra targa, velocità e limite; la velocità diventa rossa quando supera il limite. Ripetere il comando per disattivarlo.

Permesso: `mtvehicles.speedcamera.dynamic`.

## Placeholder velocità

L’identificatore PlaceholderAPI è `mtv`.

| Placeholder | Risultato |
|---|---|
| `%mtv_speed%` | Velocità numerica in km/h, con un decimale |
| `%mtv_vehicle_speed_kmh%` | Alias numerico in km/h |
| `%mtv_vehicle_speed_raw%` | Valore `double` non arrotondato, senza unità |
| `%mtv_vehicle_speed%` | Placeholder storico in blocchi/secondo con unità |
| `%mtv_vehicle_maxspeed%` | Velocità massima storica in blocchi/secondo |

Se il giocatore non è dentro un veicolo, i nuovi placeholder restituiscono `0`. La conversione è `velocità interna × 20 tick × 3.6`.

## Configurare un veicolo FDO

Le proprietà possono essere inserite sul veicolo principale e applicate a tutte le skin:

```yaml
- name: "Auto Polizia"
  vehicleType: CAR
  fdo: true
  sirenType: POLICE
  # resto della configurazione...
  cars:
    - name: "Auto Polizia Blu"
      SkinItem: "police:car_blue"
      itemDamage: 0
      uuid: POL001
      price: 25000.0
```

Oppure solamente su una skin specifica. I valori della skin hanno precedenza:

```yaml
- name: "Veicolo Servizi"
  vehicleType: CAR
  # resto della configurazione...
  cars:
    - name: "Versione Civile"
      SkinItem: "vehicles:service_civil"
      itemDamage: 0
      uuid: CIV001
      price: 15000.0

    - name: "Versione Ambulanza"
      SkinItem: "vehicles:ambulance"
      itemDamage: 0
      uuid: EMS001
      price: 25000.0
      fdo: true
      sirenType: AMBULANCE
```

Profili inclusi: `POLICE`, `AMBULANCE`, `FIRE_BRIGADE`, `CARABINIERI`.

Il conducente preme il tasto di scambio mano, normalmente `F`, per attivare o disattivare la sirena. L’evento viene annullato solamente per il conducente di un veicolo con `fdo: true`; sugli altri veicoli il tasto mantiene il comportamento normale. La sirena si spegne quando il conducente scende, il veicolo viene rimosso o il plugin viene disabilitato.

## Personalizzare le sirene

`plugins/MTVehicles/sirens.yml` viene creato automaticamente. Ogni profilo contiene intervallo, volume e sequenza di toni:

```yaml
sirens:
  POLICE:
    intervalTicks: 7
    volume: 2.0
    tones:
      - sound: "minecraft:block.note_block.bit"
        pitch: 1.65
      - sound: "minecraft:block.note_block.bit"
        pitch: 0.85
```

Si possono aggiungere profili senza modificare il codice, anche con suoni ItemsAdder/resource pack:

```yaml
  GUARDIA_DI_FINANZA:
    intervalTicks: 6
    volume: 2.2
    tones:
      - sound: "my_namespace:sirens.gdf_high"
        pitch: 1.0
      - sound: "my_namespace:sirens.gdf_low"
        pitch: 1.0
```

Nel veicolo usare `fdo: true` e `sirenType: GUARDIA_DI_FINANZA`, quindi `/vehicle reload`.

## API per multe o integrazioni

```java
import nl.mtvehicles.core.events.VehicleSpeedCameraEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class SpeedFineListener implements Listener {
    @EventHandler
    public void onSpeeding(VehicleSpeedCameraEvent event) {
        String cameraId = event.getCameraId();
        String plate = event.getLicensePlate();
        double speed = event.getSpeedKmh();
        double limit = event.getLimitKmh();

        // event.getPlayer() restituisce il conducente.
        // Qui si può salvare l'infrazione o applicare una multa con Vault.
    }
}
```

Metodi: `getCameraId()`, `getLicensePlate()`, `getSpeedKmh()`, `getLimitKmh()`, `getCameraLocation()`, `getPlayer()`.

## Prestazioni e note operative

- Il controllo statico gira ogni 10 tick e considera solo veicoli attivamente guidati.
- Gli autovelox usano un indice spaziale a celle da 64 blocchi.
- Il telelaser esegue ray trace solo per i giocatori che lo hanno attivato.
- Le sirene vengono processate solo mentre sono attive.
- Ogni tono viene emesso dall'entità principale del veicolo: la sorgente audio segue la macchina mentre si muove e non rimane fissata nel punto di emissione.
- Gli autovelox non caricano chunk e non generano entità.
- Dopo aver sostituito il JAR eseguire un riavvio completo del server.
