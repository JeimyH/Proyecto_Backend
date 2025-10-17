package com.example.Proyecto.Controller;

import com.example.Proyecto.DTO.EstadisticaPorDiaDTO;
import com.example.Proyecto.DTO.EstadisticaPorMesDTO;
import com.example.Proyecto.DTO.NutrientesRecomendadosDTO;
import com.example.Proyecto.DTO.NutrientesTotalesDTO;
import com.example.Proyecto.Service.EstadisticasNutricionalesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/Estadisticas")
public class EstadisticasNutricionalesController {
    @Autowired
    public EstadisticasNutricionalesService estadisticasService;

    //Usando
    @GetMapping("/totales")
    public ResponseEntity<NutrientesTotalesDTO> obtenerTotales(
            @RequestParam Long idUsuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        NutrientesTotalesDTO resultado = estadisticasService.obtenerTotalesPorFecha(idUsuario, fecha);
        return ResponseEntity.ok(resultado);
    }

    //Usando
    @GetMapping("/recomendados")
    public ResponseEntity<NutrientesRecomendadosDTO> obtenerRecomendaciones(@RequestParam Long idUsuario) {
        try {
            NutrientesRecomendadosDTO dto = estadisticasService.calcularRecomendacionesDiarias(idUsuario);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // Usuario no encontrado
        }
    }

    //Usando
    @GetMapping("/recomendados/mensual/{idUsuario}/{anio}/{mes}")
    public ResponseEntity<NutrientesRecomendadosDTO> getRecomendacionesMensuales(
            @PathVariable Long idUsuario,
            @PathVariable int anio,
            @PathVariable int mes) {

        NutrientesRecomendadosDTO dto = estadisticasService.calcularRecomendacionesMensuales(idUsuario, anio, mes);
        return ResponseEntity.ok(dto);
    }


    @GetMapping("/totalDiaria/{idUsuario}/mes")
    public ResponseEntity<List<EstadisticaPorDiaDTO>> getConsumoPorDia(
            @PathVariable Long idUsuario,
            @RequestParam int anio,
            @RequestParam int mes
    ) {
        YearMonth yearMonth = YearMonth.of(anio, mes);
        List<EstadisticaPorDiaDTO> datos = estadisticasService.obtenerConsumoPorDiaDelMes(idUsuario, yearMonth);
        return ResponseEntity.ok(datos);
    }

    //Usando
    @GetMapping("/totalDiariaOpt/{idUsuario}/{anio}/{mes}")
    public ResponseEntity<List<EstadisticaPorDiaDTO>> obtenerTotalesDiariosOptimizado(
            @PathVariable Long idUsuario,
            @PathVariable int anio,
            @PathVariable int mes) {

        System.out.println("⚡ [Controller] Ejecutando versión OPTIMIZADA para usuario " + idUsuario);

        YearMonth yearMonth = YearMonth.of(anio, mes);
        List<EstadisticaPorDiaDTO> resultado = estadisticasService.obtenerConsumoPorDiaDelMes(idUsuario, yearMonth);

        if (resultado.isEmpty()) {
            System.out.println("🔍 [Controller] No se encontraron registros en el mes " + yearMonth);
        } else {
            System.out.println("📊 [Controller] Datos devueltos para " + resultado.size() + " días.");
        }

        return ResponseEntity.ok(resultado);
    }

    //Usando
    @GetMapping("/totalMes/{idUsuario}/anio")
    public ResponseEntity<List<EstadisticaPorMesDTO>> getConsumoPorMes(
            @PathVariable Long idUsuario,
            @RequestParam int anio
    ) {
        List<EstadisticaPorMesDTO> datos = estadisticasService.obtenerConsumoPorMesDelAnio(idUsuario, anio);
        return ResponseEntity.ok(datos);
    }

    /**
     * Guardar o actualizar estadísticas diarias para un usuario en una fecha específica
     */
    @PostMapping("/diaria/{idUsuario}")
    public ResponseEntity<String> guardarEstadisticaDiaria(
            @PathVariable Long idUsuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        estadisticasService.guardarEstadisticaDiaria(idUsuario, fecha);
        return ResponseEntity.ok("✅ Estadística diaria guardada correctamente para el usuario " + idUsuario);
    }

    /**
     * Guardar o actualizar estadísticas mensuales para un usuario
     */
    @PostMapping("/mensual/{idUsuario}")
    public ResponseEntity<String> guardarEstadisticaMensual(
            @PathVariable Long idUsuario,
            @RequestParam int anio,
            @RequestParam int mes) {

        estadisticasService.guardarEstadisticaMensual(idUsuario, anio, mes);
        return ResponseEntity.ok(" Estadística mensual guardada correctamente para el usuario " + idUsuario);
    }

    /**
     * Forzar el procesamiento automático de todas las estadísticas diarias (como el cron)
     */
    @PostMapping("/procesar-diarias")
    public ResponseEntity<String> procesarDiarias() {
        estadisticasService.procesarEstadisticasDiarias();
        return ResponseEntity.ok("⏳ Procesamiento de estadísticas diarias ejecutado manualmente");
    }

    /**
     * Forzar el procesamiento automático de todas las estadísticas mensuales (como el cron)
     */
    @PostMapping("/procesar-mensuales")
    public ResponseEntity<String> procesarMensuales() {
        estadisticasService.procesarEstadisticasMensuales();
        return ResponseEntity.ok("⏳ Procesamiento de estadísticas mensuales ejecutado manualmente");
    }

}
