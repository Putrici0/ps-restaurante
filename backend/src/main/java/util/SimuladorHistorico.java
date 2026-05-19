package util;

import com.google.cloud.firestore.Firestore;
import config.FirebaseConfig;
import config.FirestoreClientProvider;
import model.*;
import repository.firestore.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class SimuladorHistorico {

    public static void main(String[] args) {
        System.out.println("Iniciando simulación histórica de datos...");
        
        // Cargar variables de entorno si existen
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure().ignoreIfMissing().load();
        if (dotenv.get("GOOGLE_APPLICATION_CREDENTIALS") == null) {
            dotenv = io.github.cdimascio.dotenv.Dotenv.configure().directory("..").ignoreIfMissing().load();
        }
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        FirebaseConfig.init();
        Firestore db = FirestoreClientProvider.getFirestore();

        FirestoreMesaRepository mesaRepository = new FirestoreMesaRepository(db);
        FirestorePlatoRepository platoRepository = new FirestorePlatoRepository(db);
        FirestoreCuentaRepository cuentaRepository = new FirestoreCuentaRepository(db);
        FirestorePedidoRepository pedidoRepository = new FirestorePedidoRepository(db);
        FirestoreOrdenRepository ordenRepository = new FirestoreOrdenRepository(db);
        FirestoreNotificacionRepository notificacionRepository = new FirestoreNotificacionRepository(db);

        List<Mesa> mesas = mesaRepository.findAll();
        List<Plato> platos = platoRepository.findAll();

        if (mesas.isEmpty() || platos.isEmpty()) {
            System.err.println("No hay mesas o platos suficientes para simular.");
            return;
        }

        System.out.println("Mesas cargadas: " + mesas.size());
        System.out.println("Platos cargados: " + platos.size());

        Random random = new Random();
        ZoneId zone = ZoneId.of("Europe/Madrid");
        ZonedDateTime hoy = ZonedDateTime.now(zone);
        
        // Simular últimos 60 días
        int diasASimular = 60;
        int cuentasGeneradas = 0;
        int ordenesGeneradas = 0;

        for (int i = diasASimular; i >= 0; i--) {
            ZonedDateTime dia = hoy.minusDays(i);
            
            java.time.DayOfWeek diaSemana = dia.getDayOfWeek();
            boolean esFinde = diaSemana == java.time.DayOfWeek.FRIDAY || 
                              diaSemana == java.time.DayOfWeek.SATURDAY || 
                              diaSemana == java.time.DayOfWeek.SUNDAY;
            
            // Simular carga de trabajo por día (fines de semana más concurridos)
            int cuentasPorDia = esFinde ? (35 + random.nextInt(26)) : (15 + random.nextInt(16));
            
            for (int c = 0; c < cuentasPorDia; c++) {
                int hora;
                int minuto;
                
                // 70% de probabilidad de que sea en hora punta (13:30-15:30 o 20:30-22:30)
                double probHoraPunta = random.nextDouble();
                if (probHoraPunta < 0.70) {
                    boolean mediodia = random.nextBoolean();
                    if (mediodia) {
                        hora = 13 + random.nextInt(3); // 13, 14, 15
                        if (hora == 13) minuto = 30 + random.nextInt(30);
                        else if (hora == 15) minuto = random.nextInt(31);
                        else minuto = random.nextInt(60);
                    } else {
                        hora = 20 + random.nextInt(3); // 20, 21, 22
                        if (hora == 20) minuto = 30 + random.nextInt(30);
                        else if (hora == 22) minuto = random.nextInt(31);
                        else minuto = random.nextInt(60);
                    }
                } else {
                    // Hora valle (resto del horario operativo: 11:00 a 23:30)
                    int[] horasValle = {11, 12, 13, 15, 16, 17, 18, 19, 20, 22, 23};
                    hora = horasValle[random.nextInt(horasValle.length)];
                    if (hora == 13) minuto = random.nextInt(30);
                    else if (hora == 15) minuto = 30 + random.nextInt(30);
                    else if (hora == 20) minuto = random.nextInt(30);
                    else if (hora == 22) minuto = 30 + random.nextInt(30);
                    else if (hora == 23) minuto = random.nextInt(31); // Hasta las 23:30
                    else minuto = random.nextInt(60);
                }

                ZonedDateTime horaCreacion = dia.withHour(hora).withMinute(minuto).withSecond(0).withNano(0);
                Instant instantCreacion = horaCreacion.toInstant();

                // Seleccionar mesa(s) aleatoria(s)
                Mesa mesaAleatoria = mesas.get(random.nextInt(mesas.size()));
                List<Mesa> mesasCuenta = List.of(mesaAleatoria);
                
                // Si la mesa está unida a otra, es posible simularlo, pero para simplicidad 
                // usaremos la mesa seleccionada, asegurándonos de limpiar dependencias raras.
                
                // Simular pago
                int duracionMinutos = 40 + random.nextInt(60); // 40 a 100 minutos
                Instant instantPago = instantCreacion.plus(duracionMinutos, ChronoUnit.MINUTES);
                MetodoPago metodoPago = random.nextBoolean() ? MetodoPago.EFECTIVO : MetodoPago.TARJETA;

                Cuenta cuenta = new Cuenta(
                        UUID.randomUUID().toString(),
                        mesasCuenta,
                        true, // Pagada
                        Optional.empty(),
                        instantCreacion,
                        Optional.of(instantPago),
                        String.format("%04d", random.nextInt(10000)), // Password aleatorio
                        Optional.of(metodoPago)
                );

                cuentaRepository.save(cuenta);
                cuentasGeneradas++;

                // Generar Pedidos para la cuenta (1 a 3 pedidos)
                int numPedidos = 1 + random.nextInt(3);
                Instant currentPedidoTime = instantCreacion;
                
                for (int p = 0; p < numPedidos; p++) {
                    Pedido pedido = new Pedido(
                            UUID.randomUUID().toString(),
                            cuenta,
                            PedidoEstado.Listo,
                            currentPedidoTime
                    );
                    pedidoRepository.save(pedido);
                    
                    // Generar Ordenes para el pedido (1 a 5 ordenes)
                    int numOrdenes = 1 + random.nextInt(5);
                    for (int o = 0; o < numOrdenes; o++) {
                        Plato plato = platos.get(random.nextInt(platos.size()));
                        
                        // La orden se pagó al final junto con la cuenta
                        Orden orden = new Orden(
                                UUID.randomUUID().toString(),
                                pedido,
                                plato,
                                plato.precio(),
                                OrdenEstado.Entregado,
                                currentPedidoTime.plusSeconds(random.nextInt(120)),
                                "", // detalles
                                false, // urgente
                                true, // pagada
                                Optional.of(instantPago),
                                Optional.of(metodoPago)
                        );
                        ordenRepository.save(orden);
                        ordenesGeneradas++;
                    }
                    
                    // Cada nuevo pedido en la cuenta se hace unos 15-30 minutos después
                    currentPedidoTime = currentPedidoTime.plus(15 + random.nextInt(15), ChronoUnit.MINUTES);
                }

                // Generar Notificaciones aleatorias para esta cuenta
                if (random.nextDouble() > 0.7) {
                    Notificacion noti = new Notificacion(
                            UUID.randomUUID().toString(),
                            cuenta,
                            TipoNotificacion.Atencion,
                            true, // leida
                            instantPago.minusSeconds(120), // pidió la cuenta 2 mins antes de pagar
                            null,
                            null,
                            null,
                            false,
                            null,
                            null,
                            null
                    );
                    notificacionRepository.save(noti);
                }
            }
            System.out.println("Día " + dia.toLocalDate() + " simulado con éxito (" + cuentasPorDia + " cuentas).");
        }

        System.out.println("========== SIMULACIÓN COMPLETADA ==========");
        System.out.println("Cuentas generadas: " + cuentasGeneradas);
        System.out.println("Órdenes generadas: " + ordenesGeneradas);
    }
}
