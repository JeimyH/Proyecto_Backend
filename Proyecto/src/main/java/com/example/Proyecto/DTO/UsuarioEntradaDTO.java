package com.example.Proyecto.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class UsuarioEntradaDTO {

    @NotBlank(message = "El correo no puede estar vacío")
    @Email(message = "El correo debe tener un formato válido")
    private String correo;
    @NotBlank(message = "La contraseña no puede estar vacía")
    private String contrasena;
    @NotBlank(message = "El nombre no puede estar vacío")
    private String nombre;
    @NotBlank(message = "La fecha de nacimiento no puede estar vacía")
    private LocalDate fechaNacimiento;
    private Float altura;
    private Float peso;
    private String sexo;
    private String restriccionesDieta;
    private String objetivosSalud;
    private Float pesoObjetivo;
    private String nivelActividad;
}
