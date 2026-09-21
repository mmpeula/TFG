package com.tfg.kanban.domain.repository;

import com.tfg.kanban.domain.model.Invitation;
import com.tfg.kanban.domain.model.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);

    /** Las que siguen esperando respuesta en un equipo, para la pantalla de miembros. */
    List<Invitation> findByTeam_IdAndStatusOrderByCreatedAtAsc(Long teamId, InvitationStatus status);

    /** Las que esperan respuesta de esta persona, para avisarla al entrar. */
    List<Invitation> findByInvited_IdAndStatusOrderByCreatedAtDesc(Long userId, InvitationStatus status);

    boolean existsByTeam_IdAndInvited_IdAndStatus(Long teamId, Long userId, InvitationStatus status);
}
