package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AuthResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.LoginRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetConfirmRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RefreshTokenRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RegisterRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.VerifyEmailRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.RefreshToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.enums.TokenType;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.security.JwtProvider;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.UnauthorizedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService implements AuthApi {

    private final UsuarioRepository userRepository;
    private final TokenService tokenService;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UsuarioResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new BadRequestException("El email ya está registrado");
        }

        var user = Usuario.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .email(request.getEmail())
                .username(request.getUsername())
                .hashPassword(passwordEncoder.encode(request.getPassword()))
                .rol(Rol.EMPLEADO)
                .estado(EstadoUsuario.PENDIENTE) // pasa a ACTIVO al confirmar la cuenta
                .build();

        user = userRepository.saveAndFlush(user);

        // El token en claro solo se puede entregar acá: en la base queda hasheado.
        String tokenVerificacion = tokenService.generateVerificationToken(user.getId());
        log.info("Usuario {} creado por autorregistro. Token de verificación pendiente de envío por email.",
                user.getEmail());
        log.debug("Token de verificación de {}: {}", user.getEmail(), tokenVerificacion);

        return toUserResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // El estado se exige en la consulta: una cuenta pendiente o bloqueada no distingue su
        // mensaje del de credenciales inválidas, para no filtrar qué cuentas existen.
        Usuario u = buscarParaLogin(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Credenciales inválidas o cuenta sin acceso"));

        if (!passwordEncoder.matches(request.getPassword(), u.getHashPassword())) {
            throw new UnauthorizedException("Credenciales inválidas");
        }

        var accessToken = jwtProvider.generateAccessToken(u.getId(), u.getRol());
        var refreshToken = tokenService.generateRefreshToken(u.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .usuario(toUserResponse(u))
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = tokenService.validateRefreshToken(request.getRefreshToken());

        tokenService.revokeRefreshToken(request.getRefreshToken());

        Usuario u = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        var accessToken = jwtProvider.generateAccessToken(u.getId(), u.getRol());
        var newRefreshToken = tokenService.generateRefreshToken(u.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .usuario(toUserResponse(u))
                .build();
    }

    @Override
    public void logout(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
    }

    @Override
    public void revokeAllSessions(java.util.UUID userId) {
        int revocadas = tokenService.revokeAllUserRefreshTokens(userId);
        log.info("Se revocaron {} sesiones del usuario {}", revocadas, userId);
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        AuthToken authToken = tokenService.validateAuthToken(request.getToken(), TokenType.VERIFICATION);

        Usuario u = userRepository.findById(authToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        u.setEstado(EstadoUsuario.ACTIVO);
        userRepository.save(u);
        tokenService.markAuthTokenAsUsed(authToken.getId());
    }

    /**
     * Resuelve al usuario por email o por nombre de usuario, indistinto.
     *
     * <p>Se busca primero por email y solo después por username, en dos consultas separadas en
     * lugar de un OR. Es a propósito: si alguien tuviera como username el email de otra persona,
     * un OR devolvería dos filas y la consulta reventaría. Así la precedencia queda explícita
     * —gana el email, que es la credencial principal— y el resultado nunca es ambiguo.
     */
    private Optional<Usuario> buscarParaLogin(String identificador) {
        String id = identificador == null ? "" : identificador.trim();

        return userRepository.findByEmailAndDeletedAtIsNullAndEstado(id, EstadoUsuario.ACTIVO)
                .or(() -> userRepository.findByUsernameAndDeletedAtIsNullAndEstado(id, EstadoUsuario.ACTIVO));
    }

    /**
     * Acepta email o username, igual que el login: quien entra con su nombre de usuario va a
     * escribir eso mismo acá. El mail de recuperación se manda de todos modos a su email.
     *
     * <p>Responde igual exista o no la cuenta, para no revelar qué emails están registrados.
     */
    @Override
    public void requestPasswordReset(PasswordResetRequest request) {
        String id = request.getUsername() == null ? "" : request.getUsername().trim();

        userRepository.findByEmailAndDeletedAtIsNull(id)
                .or(() -> userRepository.findByUsernameAndDeletedAtIsNull(id))
                .ifPresent(u -> tokenService.generatePasswordResetToken(u.getId()));
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        AuthToken authToken = tokenService.validateAuthToken(request.getToken(), TokenType.PASSWORD_RESET);

        Usuario u = userRepository.findById(authToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        u.setHashPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(u);
        tokenService.markAuthTokenAsUsed(authToken.getId());
        tokenService.revokeAllUserRefreshTokens(u.getId());
    }

    private UsuarioResponse toUserResponse(Usuario u) {
        return UsuarioResponse.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .email(u.getEmail())
                .username(u.getUsername())
                .rol(u.getRol())
                .estado(u.getEstado())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
