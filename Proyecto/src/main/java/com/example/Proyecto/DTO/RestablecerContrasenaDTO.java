package com.example.Proyecto.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RestablecerContrasenaDTO {
    private String correo;
    // Jackson sabrá convertir "1999-05-21" a LocalDate
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaNacimiento;
    private String nuevaContrasena;
    private String confirmacionContrasena;
}
