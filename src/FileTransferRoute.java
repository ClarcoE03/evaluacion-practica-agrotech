
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.dataformat.csv.CsvDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;

import javax.sql.DataSource;
import org.sqlite.SQLiteDataSource;

public class FileTransferRoute extends RouteBuilder {


    private static final String ROOT = "C:/Cursos/IntegracionSistemas/evaluacion-practica-agrotech";

    public static void main(String[] args) throws Exception {

        String dbPath = ROOT + "/database/lecturas.db";
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath);

        Main main = new Main();
        main.bind("sqlite", (DataSource) ds);      // -> dataSource=#sqlite
        main.configure().addRoutesBuilder(new FileTransferRoute());
        main.run();
    }

    @Override
    public void configure() throws Exception {

        //  0) CREAR/VERIFICAR TABLA 
        from("timer:init-db?repeatCount=1")
            .routeId("init-db")
            .log("=== [FASE 0/3] INIT-DB -> verificando/creando tabla 'lecturas' ===")
            .to("sql:CREATE TABLE IF NOT EXISTS lecturas ("
                    + "id_sensor VARCHAR(10),"
                    + "fecha TEXT,"
                    + "humedad DOUBLE,"
                    + "temperatura DOUBLE)"
                    + "?dataSource=#sqlite&usePlaceholder=false")
            .log("=== [FASE 0/3] INIT-DB -> OK: tabla 'lecturas' lista ===");

        // 1) FILE TRANSFER + JSON + ARCHIVE + DB 
        CsvDataFormat csv = new CsvDataFormat();
        csv.setUseMaps(true);
        csv.setDelimiter(',');
        csv.setHeader(new String[]{"id_sensor","fecha","humedad","temperatura"});
        csv.setSkipHeaderRecord(true);

        from("file:" + ROOT + "/SensData"
                + "?fileName=sensores.csv"
                + "&charset=UTF-8"
                + "&noop=false"
                + "&delay=1000"
                + "&move=../Archived/${date:now:yyyyMMddHHmmss}-${file:name}")
            .routeId("sensors-csv-to-db-and-json")
            // --- Fase 1A: detección/movimiento y parsing CSV ---
            .log("=== [FASE 1/3] CSV->JSON: detectado '${file:name}' en 'SensData' ===")
            .log("    Se archivará como 'Archived/${date:now:yyyyMMddHHmmss}-${file:name}'")
            .convertBodyTo(String.class)
            .unmarshal(csv) // -> List<Map<String,Object>>
            .log("=== [FASE 1/3] CSV->JSON: filas leídas = ${body.size} ===")

            // --- Fase 2: inserts a DB (una por fila) ---
            .log("=== [FASE 2/3] DB: iniciando inserts a tabla 'lecturas' ===")
            .split(body())
                .to("sql:INSERT INTO lecturas (id_sensor,fecha,humedad,temperatura) "
                        + "VALUES (:#id_sensor, :#fecha, :#humedad, :#temperatura)"
                        + "?dataSource=#sqlite")
                .log("    [DB] Insert -> ${body}")
            .end()
            .log("=== [FASE 2/3] DB: inserts finalizados ===")

            // --- Fase 1B: generación de JSON para AgroAnalyzer y snapshot histórico ---
            .marshal().json(JsonLibrary.Jackson, true)
            .log("=== [FASE 1/3] CSV->JSON: generando 'AgroAnalyzer/sensores.json' y snapshot en 'database' ===")
            .to("file:" + ROOT + "/AgroAnalyzer?fileName=sensores.json")
            .toD("file:" + ROOT + "/database?fileName=sensores-${date:now:yyyyMMddHHmmss}.json")
            .log("=== [FASE 1/3] CSV->JSON: COMPLETA (JSON escrito y CSV archivado) ===");

        // 2) RPC SIMULADO (CLIENTE) 
        from("file:" + ROOT + "/FieldControl/requests"
                + "?include=.*\\.req"
                + "&noop=false"
                + "&move=../responses/${file:name.noext}-${date:now:yyyyMMddHHmmss}.done"
                + "&charset=UTF-8"
                + "&delete=false")
            .routeId("rpc-cliente")
            .convertBodyTo(String.class)
            .transform().simple("${body.trim()}") // deja solo el ID, ej. S001
            .setHeader("sensorId", body())
            .log("=== [FASE 3/3][RPC][CLIENT] -> solicitud '${file:name}' (sensor=${header.sensorId}) ===")
            .to("direct:rpc-handle")
            .log("=== [FASE 3/3][RPC][CLIENT] <- respuesta JSON: ${body} ===")
            .setHeader("CamelFileName", simple("${header.sensorId}.json"))
            .log("=== [FASE 3/3][RPC][CLIENT] guardando respuesta en 'FieldControl/responses/${header.CamelFileName}' ===")
            .to("file:" + ROOT + "/FieldControl/responses")
            .log("=== [FASE 3/3][RPC][CLIENT] COMPLETA: '${header.CamelFileName}' escrito ===");

        // 3) RPC SIMULADO (SERVIDOR) 
        from("direct:rpc-handle")
            .routeId("rpc-agroanalyzer-server")
            .log("=== [RPC][SERVER] atendiendo sensor=${header.sensorId} ===")
            .to("sql:SELECT id_sensor, fecha, humedad, temperatura "
                    + "FROM lecturas WHERE id_sensor = :#sensorId "
                    + "ORDER BY fecha DESC LIMIT 1?dataSource=#sqlite")
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> rows =
                        exchange.getMessage().getBody(java.util.List.class);
                java.util.Map<String, Object> r = (rows != null && !rows.isEmpty()) ? rows.get(0) : null;

                if (r == null) {
                    exchange.getMessage().setBody("{\"error\":\"sin datos\"}");
                    return;
                }
                String json = String.format(
                    "{\"id_sensor\":\"%s\",\"fecha\":\"%s\",\"humedad\":%s,\"temperatura\":%s}",
                    r.get("id_sensor"), r.get("fecha"), r.get("humedad"), r.get("temperatura")
                );
                exchange.getMessage().setBody(json);
            })
            .log("=== [RPC][SERVER] listo -> JSON enviado al cliente ===");
    }
}
