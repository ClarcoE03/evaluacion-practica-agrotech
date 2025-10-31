# Agrotech – CSV ➜ SQLite ➜ JSON + RPC simulado (Apache Camel)

Automatiza un flujo E2E con **Apache Camel 4.14.1** y **SQLite** para:

1. **Inicializar** la BD (`lecturas`)
2. **Ingerir** un CSV de sensores, **insertar** en BD, **generar** JSON y **archivar** el CSV
3. **Simular RPC** (cliente/servidor) para obtener la **última lectura** por `sensorId`

---

## 🚀 Características

* **INIT-DB**: crea/verifica tabla `lecturas` al iniciar
* **Ingesta CSV**: parseo con `CsvDataFormat`, inserts por fila, JSON consolidado
* **Archivado**: mueve el CSV procesado a `SensData/Archived/<timestamp>-sensores.csv`
* **RPC simulado**: `FieldControl/requests/*.req` ➜ responde JSON en `FieldControl/responses/*.json`
* **Logs por FASE**: mensajes claros para evidencias y diagnóstico

---

## 📦 Requisitos

* **Java** 17 o superior
* **JARs** de Apache Camel y **SQLite JDBC** en `lib/`
* **Windows/PowerShell** (comandos incluidos). *Nota*: en Linux/macOS cambia `;` por `:` en `-cp`

---

## 🗂️ Estructura del proyecto

```
evaluacion-practica-agrotech/
├─ src/
│  └─ FileTransferRoute.java
├─ lib/                       # dependencias Camel + SQLite JDBC
├─ database/
│  └─ lecturas.db            # se crea en runtime
├─ SensData/
│  ├─ sensores.csv
│  └─ Archived/              # CSVs archivados con timestamp
├─ AgroAnalyzer/
│  └─ sensores.json          # salida consolidada
└─ FieldControl/
   ├─ requests/              # S001.req, S002.req, ...
   └─ responses/             # S001.json, S002.json, ...
```

---

## ⚙️ Configuración

En `FileTransferRoute.java` ajusta la ruta base si cambias de carpeta:

```java
private static final String ROOT = "C:/Cursos/IntegracionSistemas/evaluacion-practica-agrotech";
```

---

## ▶️ Ejecución (Windows / PowerShell)

```powershell
cd C:\Cursos\IntegracionSistemas\evaluacion-practica-agrotech

# (opcional) limpiar clase previa
Remove-Item -ErrorAction SilentlyContinue .\FileTransferRoute.class

# compilar
javac -encoding UTF-8 -cp "lib/*" -d . .\src\FileTransferRoute.java

# ejecutar
java -cp ".;lib/*" FileTransferRoute
```

> **Detener**: `Ctrl + C` en la consola

---

## 🧪 Datos de prueba

**CSV** `SensData/sensores.csv`

```
id_sensor,fecha,humedad,temperatura
S001,2025-05-22,45,26.4
S002,2025-05-22,50,25.1
S003,2025-05-22,47,27.3
```

**Requests RPC** en `FieldControl/requests/`

* `S001.req` con contenido:

  ```
  S001
  ```

**Respuestas** en `FieldControl/responses/`

* `S001.json` (ejemplo):

  ```json
  {"id_sensor":"S001","fecha":"2025-05-22","humedad":45.0,"temperatura":26.4}
  ```
* Si no hay datos:

  ```json
  {"error":"sin datos"}
  ```

---

## 🧭 Flujo por FASES (logs esperados)

### FASE 0 — INIT-DB

* Crea/verifica la tabla `lecturas`

```
=== [FASE 0/3] INIT-DB -> verificando/creando tabla 'lecturas' ===
=== [FASE 0/3] INIT-DB -> OK: tabla 'lecturas' lista ===
```

### FASE 1 — CSV ➜ BD ➜ JSON + Archive

* Detecta CSV, cuenta filas, inserta en BD, genera JSON y archiva CSV

```
=== [FASE 1/3] CSV->JSON: detectado 'sensores.csv' en 'SensData' ===
    Se archivará como 'Archived/<timestamp>-sensores.csv'
=== [FASE 1/3] CSV->JSON: filas leídas = 3 ===
=== [FASE 2/3] DB: iniciando inserts a tabla 'lecturas' ===
    [DB] Insert -> {id_sensor=S001, fecha=2025-05-22, humedad=45, temperatura=26.4 }
    [DB] Insert -> {id_sensor=S002, fecha=2025-05-22, humedad=50, temperatura=25.1 }
    [DB] Insert -> {id_sensor=S003, fecha=2025-05-22, humedad=47, temperatura=27.3 }
=== [FASE 2/3] DB: inserts finalizados ===
=== [FASE 1/3] CSV->JSON: generando 'AgroAnalyzer/sensores.json' y snapshot en 'database' ===
=== [FASE 1/3] CSV->JSON: COMPLETA (JSON escrito y CSV archivado) ===
```

### FASE 3 — RPC simulado (Cliente/Servidor)

* Atiende solicitudes `.req` y responde JSON

```
=== [FASE 3/3][RPC][CLIENT] -> solicitud 'S002.req' (sensor=S002) ===
=== [RPC][SERVER] atendiendo sensor=S002 ===
=== [RPC][SERVER] listo -> JSON enviado al cliente ===
=== [FASE 3/3][RPC][CLIENT] <- respuesta JSON: {"id_sensor":"S002","fecha":"2025-05-22","humedad":50.0,"temperatura":25.1} ===
=== [FASE 3/3][RPC][CLIENT] guardando respuesta en 'FieldControl/responses/S002.json' ===
=== [FASE 3/3][RPC][CLIENT] COMPLETA: 'S002.json' escrito ===
```

---

## ✅ Checklist de verificación rápida

* [ ] BD creada: `database/lecturas.db`
* [ ] Tabla `lecturas` con datos del CSV
* [ ] `AgroAnalyzer/sensores.json` generado
* [ ] CSV movido a `SensData/Archived/<timestamp>-sensores.csv`
* [ ] Requests `.req` producen `.json` en `FieldControl/responses/`

---

## 🛠️ Troubleshooting

**`no such table: lecturas`**

* Ocurre si FASE 1/3 corre antes que INIT-DB.
* Solución: deja correr el proceso; el siguiente ciclo insertará bien. En corridas limpias, elimina `database/lecturas.db` y vuelve a ejecutar.

**Rutas no detectan archivos**

* Verifica `ROOT`, nombres de carpetas y permisos.

**Placeholders SQL/DDL**

* Se usa DDL literal con `usePlaceholder=false` para evitar `sql:{{body}}`.

**Respuestas “sin datos”**

* Significa que no hay registros para ese `sensorId`. Carga CSV o verifica inserts.

---

## 🧩 Reflexión 

* **Patrones usados**: Timer + SQL Gateway (INIT), File Inbound + Pipes & Filters + Splitter + JSON Marshal (Ingesta), Request/Reply con `direct:` (RPC).
* **Riesgos BD compartida**: contención, acoplamiento y fallos con impacto global.
* **RPC simulado**: demuestra dependencia temporal y respuesta inmediata como en un flujo síncrono real.
* **Límites de patrones clásicos**: menor resiliencia/escalabilidad vs. enfoques event-driven y microservicios.

---

## 👤 Autor

Estudiante – Carlos Larco

