package com.tfg.kanban.team;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.InvitationRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.TeamRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.team.dto.InvitationResponse;
import com.tfg.kanban.team.dto.InviteRequest;
import com.tfg.kanban.team.dto.MyInvitationResponse;
import com.tfg.kanban.team.dto.TeamMemberResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Invitaciones a un equipo: se manda, se espera y solo al aceptarla la persona
 * entra.
 *
 * <p>Nadie entra en un equipo sin decir que si. Hasta entonces la invitacion no
 * da ningun acceso —no es una pertenencia con una marca, es otra cosa (vease
 * {@link Invitation})— y en la pantalla de miembros se ve como pendiente.</p>
 *
 * <p>Se invita a cuentas registradas. Invitar a una direccion cualquiera
 * obligaria a inventarse que rol darle antes de saber que tipo de cuenta es, y
 * a guardar invitaciones que quiza no reclame nadie nunca; es mas claro decir
 * que esa persona todavia no esta en PlanifIA.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    /** Invita a alguien. Solo lo llama el RESPONSABLE (lo comprueba el controlador). */
    @Transactional
    public InvitationResponse invite(Long teamId, InviteRequest request) {
        Team team = getTeam(teamId);
        String email = request.email().trim().toLowerCase();

        User invited = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay ninguna cuenta en PlanifIA con el email " + email
                                + ". Dile que se registre y vuelve a invitarle."));

        if (teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, invited.getId())) {
            throw new BusinessRuleException("Esa persona ya esta en el equipo");
        }
        if (invitationRepository.existsByTeam_IdAndInvited_IdAndStatus(
                teamId, invited.getId(), InvitationStatus.PENDIENTE)) {
            throw new BusinessRuleException("Ya tiene una invitacion pendiente de responder");
        }

        // El rol no lo elige quien invita: es el que le corresponde a la cuenta del
        // invitado. Llegar a RESPONSABLE es un paso aparte, posterior a entrar. Se
        // comprueba igual porque hay cuentas que no caben en ningun equipo (ADMIN):
        // sin esto se mandaria una invitacion que nunca se podria aceptar.
        Role role = TeamRoles.entryRoleFor(invited);
        TeamRoles.ensureRoleMatchesAccount(invited, role);

        User quienInvita = SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null);
        Invitation invitation = invitationRepository.save(Invitation.builder()
                .team(team)
                .invited(invited)
                .invitedBy(quienInvita)
                .role(role)
                .token(UUID.randomUUID().toString().replace("-", ""))
                .status(InvitationStatus.PENDIENTE)
                .build());

        invitation.setEmailSent(avisar(invitation, quienInvita));
        return InvitationResponse.from(invitation);
    }

    /** Las que siguen esperando respuesta, para la pantalla de miembros. */
    public List<InvitationResponse> pendingOf(Long teamId) {
        return invitationRepository
                .findByTeam_IdAndStatusOrderByCreatedAtAsc(teamId, InvitationStatus.PENDIENTE).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    /** Las que esperan respuesta de quien esta mirando. */
    public List<MyInvitationResponse> mine() {
        return invitationRepository
                .findByInvited_IdAndStatusOrderByCreatedAtDesc(SecurityUtils.requireCurrentUserId(), InvitationStatus.PENDIENTE).stream()
                .map(MyInvitationResponse::from)
                .toList();
    }

    /**
     * Acepta la invitacion y entra en el equipo.
     *
     * <p>El rol se vuelve a comprobar aqui: entre que se mando y se responde, un
     * administrador ha podido cambiarle el tipo de cuenta, y entonces el rol
     * prometido ya no valdria.</p>
     */
    @Transactional
    public TeamMemberResponse accept(String token) {
        Invitation invitation = pendingFor(token);
        Team team = invitation.getTeam();
        User invited = invitation.getInvited();

        if (teamMemberRepository.existsByTeam_IdAndUser_Id(team.getId(), invited.getId())) {
            // Alguien le anadio a mano mientras tanto: la invitacion sobra, no falla.
            resolver(invitation, InvitationStatus.ACEPTADA);
            return TeamMemberResponse.from(
                    teamMemberRepository.findByTeam_IdAndUser_Id(team.getId(), invited.getId()).orElseThrow());
        }

        TeamRoles.ensureRoleMatchesAccount(invited, invitation.getRole());
        TeamMember member = teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .user(invited)
                .role(invitation.getRole())
                .build());

        resolver(invitation, InvitationStatus.ACEPTADA);
        log.info("Invitacion aceptada: {} entra en el equipo {}", invited.getEmail(), team.getId());
        return TeamMemberResponse.from(member);
    }

    @Transactional
    public void reject(String token) {
        resolver(pendingFor(token), InvitationStatus.RECHAZADA);
    }

    /** El responsable se arrepiente antes de que respondan. */
    @Transactional
    public void cancel(Long teamId, Long invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitacion", invitationId));
        if (!invitation.getTeam().getId().equals(teamId)) {
            throw new ResourceNotFoundException("Invitacion", invitationId);
        }
        if (!invitation.isPending()) {
            throw new BusinessRuleException("Esa invitacion ya esta respondida");
        }
        resolver(invitation, InvitationStatus.CANCELADA);
    }

    /** La invitacion del token, si sigue abierta y si es de quien esta respondiendo. */
    private Invitation pendingFor(String token) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Esa invitacion no existe o ya no es valida"));

        if (!invitation.getInvited().getId().equals(SecurityUtils.requireCurrentUserId())) {
            // Mismo mensaje que si no existiera: quien tiene un token ajeno no debe
            // poder averiguar si es valido ni de quien es.
            throw new ResourceNotFoundException("Esa invitacion no existe o ya no es valida");
        }
        if (!invitation.isPending()) {
            throw new BusinessRuleException("Esa invitacion ya esta respondida");
        }
        return invitation;
    }

    private void resolver(Invitation invitation, InvitationStatus estado) {
        invitation.setStatus(estado);
        invitation.setRespondedAt(Instant.now());
    }

    private boolean avisar(Invitation invitation, User quienInvita) {
        String quien = quienInvita == null ? "Alguien" : quienInvita.getFullName();
        String enlace = mailService.getBaseUrl() + "/invitacion/" + invitation.getToken();
        String cuerpo = """
                Hola %s:

                %s te ha invitado a unirte a «%s» en PlanifIA, como %s.

                Para aceptar o rechazar la invitacion, entra aqui:
                %s

                Si no esperabas esta invitacion, puedes ignorar este correo: no entraras
                en ningun equipo mientras no la aceptes.
                """.formatted(
                invitation.getInvited().getFullName(),
                quien,
                invitation.getTeam().getName(),
                invitation.getRole().name().toLowerCase(),
                enlace);

        return mailService.send(invitation.getInvited().getEmail(),
                "%s te ha invitado a «%s»".formatted(quien, invitation.getTeam().getName()), cuerpo);
    }

    private Team getTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", teamId));
    }

}
