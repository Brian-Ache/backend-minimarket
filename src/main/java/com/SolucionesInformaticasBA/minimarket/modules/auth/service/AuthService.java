package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

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
                .enabled(false) // se habilita al verificar el email
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
        Usuario u = userRepository.findByEmailAndDeletedAtIsNullAndEnabledTrue(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Credenciales inválidas o cuenta no verificada"));

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

        u.setEnabled(true);
        userRepository.save(u);
        tokenService.markAuthTokenAsUsed(authToken.getId());
    }

    @Override
    public void requestPasswordReset(PasswordResetRequest request) {
        Usuario u = userRepository.findByEmailAndDeletedAtIsNull(request.getUsername())
                .orElse(null);

        if (u != null) {
            tokenService.generatePasswordResetToken(u.getId());
        }
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
                .enabled(u.isEnabled())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
