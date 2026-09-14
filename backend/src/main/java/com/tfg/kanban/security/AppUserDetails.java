package com.tfg.kanban.security;

import com.tfg.kanban.domain.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Adaptador entre la entidad {@link User} y el modelo de usuario de Spring Security. */
@Getter
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getEmail();
        this.password = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.authorities = List.of(new SimpleGrantedAuthority(user.getRole().authority()));
    }

    public AppUserDetails(Long id, String username, String authority) {
        this.id = id;
        this.username = username;
        this.password = "";
        this.enabled = true;
        this.authorities = List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
