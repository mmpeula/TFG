package com.tfg.kanban.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Pertenencia de un usuario a un equipo, con el rol que tiene <b>en ese equipo</b>.
 *
 * Sustituye a la relacion N:M simple de la fase de scaffolding: el rol global de
 * {@link User} solo se usa como valor por defecto y para los recursos que no
 * cuelgan de un equipo; para todo lo demas manda el rol de esta entidad.
 */
@Entity
@Table(name = "TEAM_MEMBERS", uniqueConstraints = @UniqueConstraint(
        name = "UK_TEAM_MEMBERS", columnNames = {"TEAM_ID", "USER_ID"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"team", "user"})
@EqualsAndHashCode(of = "id")
public class TeamMember extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_members_seq")
    @SequenceGenerator(name = "team_members_seq", sequenceName = "SEQ_TEAM_MEMBERS", allocationSize = 50)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TEAM_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_TM_TEAM"))
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_TM_USER"))
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.MIEMBRO;

    /**
     * Lo que hay que saber de esta persona para repartirle trabajo al generar el plan:
     * en que se maneja, que prefiere, que conviene no darle. Texto libre, en
     * lenguaje corriente, porque quien lo escribe es el responsable del equipo y
     * quien lo lee es un modelo de lenguaje: una lista cerrada de habilidades
     * obligaria a traducir «se defiende con SQL pero nunca ha tocado React» a
     * unas etiquetas que pierden justo el matiz que hace util la frase.
     *
     * <p>Cuelga de la pertenencia al equipo y no de la cuenta a proposito. Lo
     * escribe el responsable del workspace, y solo debe valer ahi: la misma
     * persona puede ser la que lleva el backend en una practica y la que se
     * encarga de la memoria en otra. Colgarlo de {@code User} dejaria ademas que
     * el responsable de un equipo editara el perfil que esa persona arrastra a
     * equipos suyos en los que el no pinta nada.</p>
     *
     * <p>Se queda y se usa en todas las ejecuciones siguientes: no hace falta
     * volver a escribirlo cada vez que se genera un plan.</p>
     */
    @Size(max = MAX_PLAN_NOTES)
    @Column(name = "PLAN_NOTES", length = MAX_PLAN_NOTES)
    private String planNotes;

    /**
     * Tope de las indicaciones de una persona.
     *
     * <p>Son varias las que entran en el mismo prompt —una por miembro— y compiten
     * por el sitio con el enunciado de la practica, que es lo que de verdad hay
     * que planificar. Con un parrafo por persona sobra para decir en que se maneja
     * y que conviene evitarle; un texto largo no da mejor reparto, solo empuja al
     * enunciado fuera del contexto de un modelo pequeno.</p>
     */
    public static final int MAX_PLAN_NOTES = 600;
}
