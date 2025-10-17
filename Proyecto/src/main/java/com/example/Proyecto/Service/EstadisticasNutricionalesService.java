package com.example.Proyecto.Service;

import com.example.Proyecto.DTO.EstadisticaPorDiaDTO;
import com.example.Proyecto.DTO.EstadisticaPorMesDTO;
import com.example.Proyecto.DTO.NutrientesRecomendadosDTO;
import com.example.Proyecto.DTO.NutrientesTotalesDTO;
import com.example.Proyecto.Model.*;
import com.example.Proyecto.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EstadisticasNutricionalesService {

    @Autowired
    public EstadisticaDiariaRepository estadisticaDiariaRepository;

    @Autowired
    public EstadisticaMensualRepository estadisticaMensualRepository;

    @Autowired
    public RegistroAlimentoRepository registroAlimentoRepository;

    @Autowired
    public UnidadEquivalenciaRepository unidadEquivalenciaRepository;

    @Autowired
    public UsuarioRepository usuarioRepository;

    private static final Logger log = LoggerFactory.getLogger(EstadisticasNutricionalesService.class);

    // justo después de crear el DTO
    private void inicializarTotales(NutrientesTotalesDTO t) {
        if (t.getCalorias() == null ) t.setCalorias(0f);
        if (t.getProteinas() == null) t.setProteinas(0f);
        if (t.getCarbohidratos() == null) t.setCarbohidratos(0f);
        if (t.getGrasas() == null) t.setGrasas(0f);
        if (t.getAzucares() == null) t.setAzucares(0f);
        if (t.getFibra() == null) t.setFibra(0f);
        if (t.getSodio() == null) t.setSodio(0f);
        if (t.getGrasasSaturadas() == null) t.setGrasasSaturadas(0f);
    }

    public NutrientesTotalesDTO obtenerTotalesPorFecha(Long idUsuario, LocalDate fecha) {
        NutrientesTotalesDTO totales = new NutrientesTotalesDTO();
        inicializarTotales(totales);

        // Obtener todos los registros del usuario para la fecha
        List<RegistroAlimento> registros = registroAlimentoRepository.findByUsuarioAndFecha(
                idUsuario,
                fecha.atStartOfDay(),
                fecha.atTime(LocalTime.MAX)
        );

        if (registros.isEmpty()) {
            System.out.println("🔍 No se encontraron registros para el usuario " + idUsuario + " el " + fecha);
        }

        for (RegistroAlimento registro : registros) {
            Alimento alimento = registro.getAlimento();
            if (alimento == null) {
                System.out.println("⚠️ Registro sin alimento asociado. ID Registro: " + registro.getIdRegistroAlimento());
                continue;
            }

            // Tomar tamaño de porción y unidad: prioriza tamanoPorcion/unidadMedida, fallback a tamanoOriginal/unidadOriginal
            Float cantidadConsumida = registro.getTamanoPorcion() != null && registro.getTamanoPorcion() > 0f
                    ? registro.getTamanoPorcion() : registro.getTamanoOriginal();
            String unidadConsumida = registro.getTamanoPorcion() != null && registro.getTamanoPorcion() > 0f
                    ? registro.getUnidadMedida() : registro.getUnidadOriginal();

            if (cantidadConsumida == null || unidadConsumida == null) {
                System.out.println("⚠️ Registro ID=" + registro.getIdRegistroAlimento() + " sin cantidad válida");
                continue;
            }

            // Asumimos que la unidad base de todos los alimentos es gramos
            float cantidadBase = alimento.getCantidadBase() != null ? alimento.getCantidadBase() : 100f;

            // Calcular proporción respecto a la base (100 g)
            float proporcion = cantidadConsumida / cantidadBase;

            System.out.println("--------------------------------------------------");
            System.out.println("Alimento: " + alimento.getNombreAlimento());
            System.out.println("Cantidad consumida: " + cantidadConsumida + " " + unidadConsumida);
            System.out.println("Cantidad base (para 100g): " + cantidadBase + " " + alimento.getUnidadBase());
            System.out.println("Proporción: " + proporcion);

            // Sumar nutrientes escalados por la proporción
            totales.setCalorias(totales.getCalorias() + safeFloat(alimento.getCalorias()) * proporcion);
            totales.setProteinas(totales.getProteinas() + safeFloat(alimento.getProteinas()) * proporcion);
            totales.setCarbohidratos(totales.getCarbohidratos() + safeFloat(alimento.getCarbohidratos()) * proporcion);
            totales.setGrasas(totales.getGrasas() + safeFloat(alimento.getGrasas()) * proporcion);
            totales.setAzucares(totales.getAzucares() + safeFloat(alimento.getAzucares()) * proporcion);
            totales.setFibra(totales.getFibra() + safeFloat(alimento.getFibra()) * proporcion);
            totales.setSodio(totales.getSodio() + safeFloat(alimento.getSodio()) * proporcion);
            totales.setGrasasSaturadas(totales.getGrasasSaturadas() + safeFloat(alimento.getGrasasSaturadas()) * proporcion);

            System.out.println("Totales acumulados: " + totalesToString(totales));
        }

        return totales;
    }

    private float safeFloat(Float f) {
        return f == null ? 0f : f;
    }

    /*
    private String totalesToString(NutrientesTotalesDTO t) {
        return String.format(Locale.US,
                "cal=%.2f | prot=%.2f | carb=%.2f | gras=%.2f | azu=%.2f | fib=%.2f | sod=%.2f | grasSat=%.2f",
                t.getCalorias(), t.getProteinas(), t.getCarbohidratos(), t.getGrasas(),
                t.getAzucares(), t.getFibra(), t.getSodio(), t.getGrasasSaturadas());
    }
    */

    public NutrientesRecomendadosDTO calcularRecomendacionesDiarias(Long idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        float peso = usuario.getPeso();
        float altura = usuario.getAltura();
        float pesoObjetivo = usuario.getPesoObjetivo();
        int edad = Period.between(usuario.getFechaNacimiento(), LocalDate.now()).getYears();
        String sexo = usuario.getSexo().toLowerCase();
        String objetivo = usuario.getObjetivosSalud();
        String restriccion = usuario.getRestriccionesDieta();
        String nivelActividad = usuario.getNivelActividad(); // Nuevo campo

        System.out.println("📌 Usuario ID: " + idUsuario);
        System.out.println("→ Sexo: " + sexo + ", Edad: " + edad + ", Peso: " + peso + ", Altura: " + altura);
        System.out.println("→ Objetivo: " + objetivo);
        System.out.println("→ Restricción dietética: " + restriccion);
        System.out.println("→ Nivel actividad física: " + nivelActividad);

        // ✔ Fórmula Mifflin-St Jeor
        double tmb;
        if (sexo.equals("masculino")) {
            tmb = (10 * peso) + (6.25 * altura) - (5 * edad) + 5;
        } else {
            tmb = (10 * peso) + (6.25 * altura) - (5 * edad) - 161;
        }
        System.out.println("✅ TMB (Mifflin-St Jeor): " + tmb);

        // 👉 Determinar factor de actividad física
        double factorActividad = 1.2; // Por defecto sedentario
        switch (nivelActividad.toLowerCase()) {
            case "sedentario":
                factorActividad = 1.2;
                break;
            case "ligera actividad":
                factorActividad = 1.375;
                break;
            case "actividad moderada":
                factorActividad = 1.55;
                break;
            case "alta actividad":
                factorActividad = 1.725;
                break;
            case "actividad extrema":
                factorActividad = 1.9;
                break;
            default:
                System.out.println("⚠ Nivel de actividad no reconocido. Se usará sedentario por defecto.");
        }
        System.out.println("⚙️ Factor Actividad aplicado: " + factorActividad);

        double gct = tmb * factorActividad;
        System.out.println("✅ GCT (TMB x Actividad): " + gct);

        // Ajuste calórico por objetivo
        double caloriasObjetivo = gct;
        switch (objetivo.toLowerCase()) {
            case "pérdida de peso":
                caloriasObjetivo -= 500;
                System.out.println("📉 Déficit aplicado (-500 cal)");
                break;
            case "ganancia de masa muscular":
                caloriasObjetivo += 500;
                System.out.println("📈 Superávit aplicado (+500 cal)");
                break;
            case "mantener peso":
                System.out.println("🟰 Sin ajuste calórico");
                break;
            case "desarrollo de hábitos alimenticios saludables":
                System.out.println("🍏 Enfoque balanceado, sin ajuste calórico");
                break;
        }

        float calorias = (float) caloriasObjetivo;

        // Distribución estándar
        float porcProte = 0.20f, porcCarb = 0.50f, porcGrasa = 0.30f;

        // Ajustes por restricciones
        switch (restriccion.toLowerCase()) {
            case "alta en proteínas":
                porcProte = 0.30f; porcCarb = 0.40f; porcGrasa = 0.30f;
                break;
            case "baja en carbohidratos":
                porcProte = 0.30f; porcCarb = 0.20f; porcGrasa = 0.50f;
                break;
            case "keto":
                porcProte = 0.25f; porcCarb = 0.10f; porcGrasa = 0.65f;
                break;
            case "baja en grasas":
                porcProte = 0.30f; porcCarb = 0.60f; porcGrasa = 0.10f;
                break;
            // Otros valores se manejan como estándar
        }

        // Macronutrientes (proteínas, carbohidratos, grasas) se ajustan a la energía total y objetivos individuales (ej. ganancia muscular, pérdida de peso, keto).
        float proteinas = (calorias * porcProte) / 4f;
        float carbohidratos = (calorias * porcCarb) / 4f;
        float grasas = (calorias * porcGrasa) / 9f;

        float azucares = (calorias * 0.10f) / 4f;
        float fibra = calorias / 1000f * 14f;
        float grasasSaturadas = (calorias * 0.10f) / 9f;

        // El sodio se personaliza al mismo nivel que macronutrientes (proteínas, carbohidratos, grasas)
        // porque las recomendaciones de sodio se basan más en salud cardiovascular y límites de seguridad que en gasto energético o peso corporal.
        float sodio = 1500f;

        System.out.println("🔢 Calorías: " + calorias);
        System.out.println("🍗 Proteínas (g): " + proteinas);
        System.out.println("🍞 Carbohidratos (g): " + carbohidratos);
        System.out.println("🥑 Grasas (g): " + grasas);
        System.out.println("🍬 Azúcares (g): " + azucares);
        System.out.println("🌾 Fibra (g): " + fibra);
        System.out.println("🧂 Sodio (mg): " + sodio);
        System.out.println("🥓 Grasas Saturadas (g): " + grasasSaturadas);

        return new NutrientesRecomendadosDTO(
                calorias, proteinas, carbohidratos, grasas, azucares, fibra, sodio, grasasSaturadas
        );
    }

    public NutrientesRecomendadosDTO calcularRecomendacionesMensuales(Long idUsuario, int anio, int mes) {
        // Primero calculamos las recomendaciones diarias
        NutrientesRecomendadosDTO diario = calcularRecomendacionesDiarias(idUsuario);

        // Obtenemos el número de días del mes usando YearMonth
        YearMonth yearMonth = YearMonth.of(anio, mes);
        int diasEnMes = yearMonth.lengthOfMonth();

        System.out.println("📅 [INFO] Año: " + anio + " | Mes: " + mes + " → Días en mes: " + diasEnMes);

        // Multiplicamos cada valor por la cantidad de días del mes
        float caloriasMensual = diario.getCalorias() * diasEnMes;
        float proteinasMensual = diario.getProteinas() * diasEnMes;
        float carbohidratosMensual = diario.getCarbohidratos() * diasEnMes;
        float grasasMensual = diario.getGrasas() * diasEnMes;
        float azucaresMensual = diario.getAzucares() * diasEnMes;
        float fibraMensual = diario.getFibra() * diasEnMes;
        float sodioMensual = diario.getSodio() * diasEnMes;
        float grasasSaturadasMensual = diario.getGrasasSaturadas() * diasEnMes;

        System.out.println("📊 [DEBUG] Recomendaciones mensuales calculadas:");
        System.out.println("   🔢 Calorías: " + caloriasMensual);
        System.out.println("   🍗 Proteínas: " + proteinasMensual);
        System.out.println("   🍞 Carbohidratos: " + carbohidratosMensual);
        System.out.println("   🥑 Grasas: " + grasasMensual);
        System.out.println("   🍬 Azúcares: " + azucaresMensual);
        System.out.println("   🌾 Fibra: " + fibraMensual);
        System.out.println("   🧂 Sodio: " + sodioMensual);
        System.out.println("   🥓 Grasas Saturadas: " + grasasSaturadasMensual);

        return new NutrientesRecomendadosDTO(
                caloriasMensual,
                proteinasMensual,
                carbohidratosMensual,
                grasasMensual,
                azucaresMensual,
                fibraMensual,
                sodioMensual,
                grasasSaturadasMensual
        );
    }

    /*
    // Obtener consumo por día en un mes
    @Transactional(readOnly = true)
    public List<EstadisticaPorDiaDTO> obtenerConsumoPorDiaDelMes(Long idUsuario, YearMonth mes) {
        List<EstadisticaPorDiaDTO> resultados = new ArrayList<>();

        for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
            LocalDate fecha = mes.atDay(dia);
            NutrientesTotalesDTO nutrientes = obtenerTotalesPorFecha(idUsuario, fecha);
            resultados.add(new EstadisticaPorDiaDTO(dia, nutrientes));
        }

        return resultados;
    }
    */

    @Transactional(readOnly = true)
    public List<EstadisticaPorDiaDTO> obtenerConsumoPorDiaDelMes(Long idUsuario, YearMonth mes) {
        System.out.println("🚀 [OPTIMIZADO] Consultando registros del mes completo en una sola llamada...");

        LocalDateTime inicioMes = mes.atDay(1).atStartOfDay();
        LocalDateTime finMes = mes.atEndOfMonth().atTime(LocalTime.MAX);

        // 🔹 1️⃣ Traer todos los registros del mes
        List<RegistroAlimento> registros = registroAlimentoRepository.findByUsuarioAndMes(idUsuario, inicioMes, finMes);

        System.out.println("📦 Total de registros encontrados: " + registros.size());

        // 🔹 2️⃣ Agrupar por día
        Map<LocalDate, List<RegistroAlimento>> registrosPorDia = registros.stream()
                .collect(Collectors.groupingBy(r -> r.getConsumidoEn().toLocalDate()));

        List<EstadisticaPorDiaDTO> resultados = new ArrayList<>();

        // 🔹 3️⃣ Recorrer cada día del mes y calcular totales
        for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
            LocalDate fecha = mes.atDay(dia);
            List<RegistroAlimento> registrosDia = registrosPorDia.getOrDefault(fecha, Collections.emptyList());

            NutrientesTotalesDTO totales = calcularTotales(registrosDia);
            resultados.add(new EstadisticaPorDiaDTO(dia, totales));
        }

        System.out.println("✅ [OK] Totales diarios calculados correctamente para el mes " + mes);
        return resultados;
    }

    private NutrientesTotalesDTO calcularTotales(List<RegistroAlimento> registros) {
        NutrientesTotalesDTO totales = new NutrientesTotalesDTO();
        inicializarTotales(totales);

        for (RegistroAlimento registro : registros) {
            Alimento alimento = registro.getAlimento();
            if (alimento == null) {
                System.out.println("⚠️ Registro sin alimento asociado: " + registro.getIdRegistroAlimento());
                continue;
            }

            Float cantidadBase = alimento.getCantidadBase();
            String unidadBase = alimento.getUnidadBase();
            Float tamanoPorcion = registro.getTamanoPorcion();
            String unidadMedida = registro.getUnidadMedida();

            if (cantidadBase == null || unidadBase == null || tamanoPorcion == null || unidadMedida == null) {
                System.out.println("⚠️ Datos incompletos para alimento: " + alimento.getNombreAlimento());
                continue;
            }

            float factor = 1f;
            if (!unidadBase.equalsIgnoreCase(unidadMedida)) {
                Optional<UnidadEquivalencia> eqOpt =
                        unidadEquivalenciaRepository.findByAlimentoAndUnidadOrigenAndUnidadDestino(
                                alimento, unidadMedida.toLowerCase(), unidadBase.toLowerCase());
                if (eqOpt.isPresent()) {
                    factor = eqOpt.get().getFactorConversion();
                } else {
                    System.out.println("❌ No se encontró equivalencia para " + alimento.getNombreAlimento());
                    continue;
                }
            }

            float proporcion = (tamanoPorcion * factor) / cantidadBase;
            if (proporcion <= 0) continue;

            System.out.println("--------------------------------------------------");
            System.out.println("🍽️ Alimento: " + alimento.getNombreAlimento());
            System.out.println("Porción consumida: " + tamanoPorcion + " " + unidadMedida);
            System.out.println("Unidad base: " + cantidadBase + " " + unidadBase);
            System.out.println("Factor conversión: " + factor);
            System.out.println("Proporción respecto base: " + proporcion);

            System.out.println("➡️ Nutrientes antes de sumar: " + totalesToString(totales));

            totales.setCalorias(totales.getCalorias() + safeFloat(alimento.getCalorias()) * proporcion);
            totales.setProteinas(totales.getProteinas() + safeFloat(alimento.getProteinas()) * proporcion);
            totales.setCarbohidratos(totales.getCarbohidratos() + safeFloat(alimento.getCarbohidratos()) * proporcion);
            totales.setGrasas(totales.getGrasas() + safeFloat(alimento.getGrasas()) * proporcion);
            totales.setAzucares(totales.getAzucares() + safeFloat(alimento.getAzucares()) * proporcion);
            totales.setFibra(totales.getFibra() + safeFloat(alimento.getFibra()) * proporcion);
            totales.setSodio(totales.getSodio() + safeFloat(alimento.getSodio()) * proporcion);
            totales.setGrasasSaturadas(totales.getGrasasSaturadas() + safeFloat(alimento.getGrasasSaturadas()) * proporcion);

            System.out.println("✅ Nutrientes después de sumar: " + totalesToString(totales));
        }

        return totales;
    }

    private String totalesToString(NutrientesTotalesDTO n) {
        return String.format("Calorías=%.2f, Prot=%.2f, Carb=%.2f, Grasas=%.2f, Azúcar=%.2f, Fibra=%.2f, Sodio=%.2f, GrasasSat=%.2f",
                n.getCalorias(), n.getProteinas(), n.getCarbohidratos(), n.getGrasas(),
                n.getAzucares(), n.getFibra(), n.getSodio(), n.getGrasasSaturadas());
    }


    public List<EstadisticaPorMesDTO> obtenerConsumoPorMesDelAnio(Long idUsuario, int anio) {
        System.out.println("📊 [DEBUG] Iniciando obtención de consumo mensual para Usuario ID: " + idUsuario + " | Año: " + anio);

        // Traemos solo los registros existentes
        List<EstadisticaMensual> registrosMensuales =
                estadisticaMensualRepository.findByUsuarioIdUsuarioAndAnio(idUsuario, anio);

        System.out.println("📊 [DEBUG] Registros mensuales encontrados en BD: " + registrosMensuales.size());

        // Mapeamos por número de mes para búsqueda rápida
        Map<Integer, EstadisticaMensual> mapaPorMes = registrosMensuales.stream()
                .collect(Collectors.toMap(EstadisticaMensual::getMes, r -> r));

        System.out.println("📊 [DEBUG] Meses con registros: " + mapaPorMes.keySet());

        List<EstadisticaPorMesDTO> resultado = new ArrayList<>();

        // Recorremos todos los meses del 1 al 12
        for (int mes = 1; mes <= 12; mes++) {
            EstadisticaMensual registro = mapaPorMes.get(mes);

            if (registro != null) {
                // Si existe el mes, usamos sus valores
                System.out.println("✅ [INFO] Datos encontrados para mes " + mes +
                        ": Calorías=" + registro.getCalorias() +
                        ", Proteínas=" + registro.getProteinas() +
                        ", Carbohidratos=" + registro.getCarbohidratos() +
                        ", Grasas=" + registro.getGrasas()+
                        ", Azucares=" + registro.getAzucares() +
                        ", Fibra=" + registro.getFibra() +
                        ", Sodio=" + registro.getSodio() +
                        ", Grasas Saturadas=" + registro.getGrasasSaturadas()
                );

                resultado.add(new EstadisticaPorMesDTO(
                        mes,
                        new NutrientesTotalesDTO(
                                registro.getCalorias(),
                                registro.getProteinas(),
                                registro.getCarbohidratos(),
                                registro.getGrasas(),
                                registro.getAzucares(),
                                registro.getFibra(),
                                registro.getSodio(),
                                registro.getGrasasSaturadas()
                        )
                ));
            } else {
                // Si no existe, devolvemos valores en 0
                System.out.println("⚠️ [WARN] No se encontró registro para mes " + mes + ". Asignando valores en 0.");

                resultado.add(new EstadisticaPorMesDTO(
                        mes,
                        new NutrientesTotalesDTO(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                ));
            }
        }

        System.out.println("📊 [DEBUG] Resultado final: " + resultado.size() + " registros preparados para el año " + anio);
        return resultado;
    }


    /**
     * Guarda o actualiza las estadísticas diarias de un usuario para una fecha específica
     */
    @Transactional
    public void guardarEstadisticaDiaria(Long idUsuario, LocalDate fecha) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Obtener nutrientes del día
        NutrientesTotalesDTO nutrientes = obtenerNutrientesPorUsuarioYFecha(idUsuario, fecha);

        // Buscar si ya existe la estadística de ese día
        EstadisticaDiaria estadistica = estadisticaDiariaRepository
                .findByUsuarioAndFecha(usuario, fecha)
                .orElse(new EstadisticaDiaria());

        estadistica.setUsuario(usuario);
        estadistica.setFecha(fecha);
        estadistica.setCalorias(nutrientes.getCalorias());
        estadistica.setProteinas(nutrientes.getProteinas());
        estadistica.setCarbohidratos(nutrientes.getCarbohidratos());
        estadistica.setGrasas(nutrientes.getGrasas());
        estadistica.setAzucares(nutrientes.getAzucares());
        estadistica.setFibra(nutrientes.getFibra());
        estadistica.setSodio(nutrientes.getSodio());
        estadistica.setGrasasSaturadas(nutrientes.getGrasasSaturadas());

        estadisticaDiariaRepository.save(estadistica);
    }

    /**
     * Guarda o actualiza las estadísticas mensuales de un usuario para un mes/año específico
     */
    @Transactional
    public void guardarEstadisticaMensual(Long idUsuario, int anio, int mes) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Obtener todas las estadísticas diarias del mes
        List<EstadisticaDiaria> diasDelMes = estadisticaDiariaRepository
                .findByUsuarioAndMesAndAnio(usuario, mes, anio);

        // Sumar los valores
        float calorias = 0f, proteinas = 0f, carbohidratos = 0f, grasas = 0f, azucares = 0f, fibra = 0f, sodio = 0f, grasasSaturadas = 0f;

        for (EstadisticaDiaria dia : diasDelMes) {
            calorias += dia.getCalorias() != null ? dia.getCalorias() : 0f;
            proteinas += dia.getProteinas() != null ? dia.getProteinas() : 0f;
            carbohidratos += dia.getCarbohidratos() != null ? dia.getCarbohidratos() : 0f;
            grasas += dia.getGrasas() != null ? dia.getGrasas() : 0f;
            azucares += dia.getAzucares() != null ? dia.getAzucares() : 0f;
            fibra += dia.getFibra() != null ? dia.getFibra() : 0f;
            sodio += dia.getSodio() != null ? dia.getSodio() : 0f;
            grasasSaturadas += dia.getGrasasSaturadas() != null ? dia.getGrasasSaturadas() : 0f;
        }

        // Buscar si ya existe la estadística mensual
        EstadisticaMensual estadisticaMensual = estadisticaMensualRepository
                .findByUsuarioAndAnioAndMes(usuario, anio, mes)
                .orElse(new EstadisticaMensual());

        estadisticaMensual.setUsuario(usuario);
        estadisticaMensual.setAnio(anio);
        estadisticaMensual.setMes(mes);
        estadisticaMensual.setCalorias(calorias);
        estadisticaMensual.setProteinas(proteinas);
        estadisticaMensual.setCarbohidratos(carbohidratos);
        estadisticaMensual.setGrasas(grasas);
        estadisticaMensual.setAzucares(azucares);
        estadisticaMensual.setFibra(fibra);
        estadisticaMensual.setSodio(sodio);
        estadisticaMensual.setGrasasSaturadas(grasasSaturadas);

        estadisticaMensualRepository.save(estadisticaMensual);
    }

    /**
     * Metodo que calcula nutrientes totales del usuario en una fecha específica.
     * Este es tu metodo original adaptado.
     */
    public NutrientesTotalesDTO obtenerNutrientesPorUsuarioYFecha(Long idUsuario, LocalDate fecha) {
        NutrientesTotalesDTO totales = new NutrientesTotalesDTO();
        inicializarTotales(totales);

        // 1. Obtener registros del usuario para ese día completo
        List<RegistroAlimento> registros = registroAlimentoRepository.findByUsuarioAndFecha(
                idUsuario,
                fecha.atStartOfDay(),
                fecha.atTime(LocalTime.MAX)
        );

        if (registros.isEmpty()) {
            System.out.println("🔍 No se encontraron registros para el usuario " + idUsuario + " el " + fecha);
        }

        for (RegistroAlimento registro : registros) {
            Alimento alimento = registro.getAlimento();
            if (alimento == null) {
                System.out.println("⚠️ Registro sin alimento asociado. ID Registro: " + registro.getIdRegistroAlimento());
                continue;
            }

            Float cantidadBase = alimento.getCantidadBase();
            String unidadBase = alimento.getUnidadBase();
            Float tamanoPorcion = registro.getTamanoPorcion();
            String unidadMedida = registro.getUnidadMedida();

            if (cantidadBase == null || unidadBase == null || tamanoPorcion == null || unidadMedida == null) {
                System.out.println("⚠️ Datos incompletos en registro con ID " + registro.getIdRegistroAlimento());
                continue;
            }

            // 2. Calcular factor de conversión
            float factor = 1f;
            if (!unidadBase.equalsIgnoreCase(unidadMedida)) {
                Optional<UnidadEquivalencia> equivalenciaOpt =
                        unidadEquivalenciaRepository.findByAlimentoAndUnidadOrigenAndUnidadDestino(
                                alimento, unidadMedida.toLowerCase(), unidadBase.toLowerCase());

                if (equivalenciaOpt.isPresent()) {
                    factor = equivalenciaOpt.get().getFactorConversion();
                } else {
                    System.out.println("❌ No se encontró equivalencia para el alimento " +
                            alimento.getNombreAlimento() + " de " + unidadMedida + " a " + unidadBase +
                            " (registro ID " + registro.getIdRegistroAlimento() + ")");
                    continue;
                }
            }

            float proporcion = (tamanoPorcion * factor) / cantidadBase;
            if (proporcion <= 0) {
                System.out.println("❌ Proporción inválida para el alimento " + alimento.getNombreAlimento());
                continue;
            }

            // 3. Sumar nutrientes aplicando la proporción
            totales.setCalorias(totales.getCalorias() + alimento.getCalorias() * proporcion);
            totales.setProteinas(totales.getProteinas() + alimento.getProteinas() * proporcion);
            totales.setCarbohidratos(totales.getCarbohidratos() + alimento.getCarbohidratos() * proporcion);
            totales.setGrasas(totales.getGrasas() + alimento.getGrasas() * proporcion);
            totales.setAzucares(totales.getAzucares() + alimento.getAzucares() * proporcion);
            totales.setFibra(totales.getFibra() + alimento.getFibra() * proporcion);
            totales.setSodio(totales.getSodio() + alimento.getSodio() * proporcion);
            totales.setGrasasSaturadas(totales.getGrasasSaturadas() + alimento.getGrasasSaturadas() * proporcion);
        }

        return totales;
    }

    @Scheduled(cron = "59 59 23 * * *") // Cada día a las 23:59:59
    public void procesarEstadisticasDiarias() {
        List<Usuario> usuarios = usuarioRepository.findAll();
        LocalDate hoy = LocalDate.now();
        for (Usuario usuario : usuarios) {
            guardarEstadisticaDiaria(usuario.getIdUsuario(), hoy);
        }
    }

    @Scheduled(cron = "0 0 0 1 * *") // Cada primer día del mes a medianoche
    public void procesarEstadisticasMensuales() {
        List<Usuario> usuarios = usuarioRepository.findAll();
        YearMonth mesAnterior = YearMonth.now().minusMonths(1);
        for (Usuario usuario : usuarios) {
            guardarEstadisticaMensual(usuario.getIdUsuario(), mesAnterior.getYear(), mesAnterior.getMonthValue());
        }
    }

}