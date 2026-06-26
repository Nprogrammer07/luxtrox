package com.luxtrox.backend.security;

import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador entre nuestra entidad User y el contrato UserDetails que
 * Spring Security exige. Se mantiene separado de la entidad a
 * proposito -- la entidad no deberia saber nada de Spring Security.
 *
 * El nombre del rol se extrae AQUI, en el constructor, en vez de
 * leerlo on-demand dentro de getAuthorities() -- user.getRole() es
 * FetchType.LAZY, y getAuthorities() se llama desde
 * JwtAuthenticationFilter, que corre FUERA de cualquier transaccion
 * (los filtros de servlet no son @Transactional). Para ese momento la
 * sesion de Hibernate que cargo al usuario ya cerro, y acceder a un
 * lazy ahi tira LazyInitializationException. Extraerlo aqui funciona
 * porque este constructor se llama dentro del .map() inmediatamente
 * despues del findByEmail en CustomUserDetailsService, que SI esta
 * dentro de la transaccion de esa consulta (ver @Transactional ahi).
 */
public class CustomUserPrincipal implements UserDetails {

    private final User user;
    private final String roleName;

    public CustomUserPrincipal(User user) {
        this.user = user;
        this.roleName = user.getRole().getName();
    }

    public UUID getId() {
        return user.getId();
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // ROLE_ADMIN / ROLE_USER -- el prefijo "ROLE_" es lo que
        // Spring Security espera para que hasRole("ADMIN") funcione.
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}