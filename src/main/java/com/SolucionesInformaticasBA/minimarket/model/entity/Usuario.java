package com.SolucionesInformaticasBA.minimarket.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

import com.SolucionesInformaticasBA.minimarket.model.enums.Rol;

@Entity
@Table(name = "usuario")
@Data
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;
}