package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

/**
 * Invitacion de alguien a un equipo, a la espera de respuesta.
 *
 * <p>Es una entidad aparte de {@link TeamMember} a proposito: mientras no se
 * acepta, la persona <b>no</b> pertenece al equipo y no debe ver nada suyo. Una
 * pertenencia «a medias», con una marca de pendiente, obligaria a acordarse de
 * excluirla en cada consulta de permisos, y bastaria un descuido para dar acceso
 * a quien todavia no ha dicho que si.</p>
 *
 * <p>Se invita a cuentas que ya existen, no a direcciones sueltas: el rol que se
 * le va a dar depende del tipo de cuenta (un docente solo puede seguir el equipo
 * como docente), asi que hace falta saber quien es antes de invitarle.</p>
 */
@Entity
@Table(name = "INVITATIONS", uniqueConstraints = @UniqueConstraint(
        name = "UK_INVITATIONS_TOKEN", columnNames = "TOKEN"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"team", "invited", "invitedBy"})
@EqualsAndHashCode(of = "id", callSuper = false)
public class Invitation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "invitations_seq")
    @SequenceGenerator(name = "invitations_seq", sequenceName = "SEQ_INVITATIONS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TEAM_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_INV_TEAM"))
    private Team team;

    /** A quien se invita. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "INVITED_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_INV_INVITED"))
    private User invited;

    /** Quien invito, para poder decirlo en el correo y en la pantalla. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "INVITED_BY_ID", foreignKey = @ForeignKey(name = "FK_INV_BY"))
    private User invitedBy;

    /** Rol que tendra en el equipo al aceptar. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.MIEMBRO;

    /**
     * Lo que viaja en el enlace del correo. Aleatorio y unico: es lo unico que
     * hace falta saber para responder, asi que no puede ser adivinable.
     */
    @NotNull
    @Column(name = "TOKEN", nullable = false, length = 64)
    private String token;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDIENTE;

    /** Cuando se respondio, si es que se respondio. */
    @Column(name = "RESPONDED_AT")
    private Instant respondedAt;

    /**
     * Si el aviso por correo llego a salir. Se guarda porque hace falta despues,
     * en la pantalla de miembros: si fallo, quien invito tiene que saberlo para
     * avisar por otro lado, y eso no se puede deducir mas tarde.
     */
    @Column(name = "EMAIL_SENT", nullable = false)
    @Builder.Default
    private boolean emailSent = false;

    public boolean isPending() {
        return status == InvitationStatus.PENDIENTE;
    }
}
