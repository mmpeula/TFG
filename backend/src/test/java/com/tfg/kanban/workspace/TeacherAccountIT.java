package com.tfg.kanban.workspace;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una cuenta docente tiene el mismo panel que un alumno: crea sus propios
 * espacios y, a la vez, sigue como docente los que otras personas le anaden.
 * Lo que puede hacer en cada uno lo decide su rol en ese espacio, no el de su cuenta.
 */
class TeacherAccountIT extends AbstractApiIT {

    private Long firstColumnOf(TestUser user, Long boardId) throws Exception {
        return json(mockMvc.perform(authorized(get("/api/boards/{id}", boardId), user))
                .andExpect(status().isOk()).andReturn()).path("columns").get(0).path("id").asLong();
    }

    @Test
    @DisplayName("Un docente crea su propio espacio y queda como su RESPONSABLE")
    void teacherCreatesItsOwnWorkspace() throws Exception {
        TestUser teacher = registerUser(Role.DOCENTE);

        mockMvc.perform(authorized(post("/api/workspaces"), teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mi asignatura\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("RESPONSABLE"));
    }

    @Test
    @DisplayName("Un administrador no crea espacios: no pertenece a ningun equipo")
    void adminCannotCreateWorkspaces() throws Exception {
        TestUser admin = registerUser(Role.ADMIN);

        mockMvc.perform(authorized(post("/api/workspaces"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"No deberia\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("El mismo docente es RESPONSABLE en el suyo y DOCENTE en el de un alumno, y cada uno se comporta como tal")
    void sameTeacherHasADifferentRoleInEachWorkspace() throws Exception {
        TestUser teacher = registerUser(Role.DOCENTE);
        TestUser student = registerUser(Role.MIEMBRO);
        Workspace own = createWorkspace(teacher, "Espacio del docente");
        Workspace students = createWorkspace(student, "Espacio del alumno");
        addMember(student, students.teamId(), teacher, Role.DOCENTE);

        mockMvc.perform(authorized(get("/api/workspaces"), teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==%d)].myRole".formatted(own.id())).value("RESPONSABLE"))
                .andExpect(jsonPath("$[?(@.id==%d)].myRole".formatted(students.id())).value("DOCENTE"));

        // En el suyo trabaja y administra...
        mockMvc.perform(authorized(post("/api/columns/{id}/tasks", firstColumnOf(teacher, own.boardId())), teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Preparar la practica\"}"))
                .andExpect(status().isCreated());
        // ...en el del alumno solo mira: no escribe ni invita a nadie.
        mockMvc.perform(authorized(post("/api/columns/{id}/tasks", firstColumnOf(student, students.boardId())), teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No deberia poder\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", students.teamId()), teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"alguien@uni.es\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una cuenta docente invitada como alumno entra a trabajar, y despues se le sube a RESPONSABLE")
    void teacherInvitedAsStudentCanBecomeResponsable() throws Exception {
        TestUser owner = registerUser(Role.MIEMBRO);
        TestUser teacher = registerUser(Role.DOCENTE);
        Workspace workspace = createWorkspace(owner, "Espacio compartido");

        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", workspace.teamId()), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"MIEMBRO\"}".formatted(teacher.email())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MIEMBRO"));
        String token = json(mockMvc.perform(authorized(get("/api/invitations/mine"), teacher))
                .andExpect(status().isOk()).andReturn()).get(0).path("token").asText();
        mockMvc.perform(authorized(post("/api/invitations/{token}/accept", token), teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MIEMBRO"));

        // Como alumno del equipo, trabaja: crea tareas, aunque su cuenta sea docente.
        mockMvc.perform(authorized(post("/api/columns/{id}/tasks", firstColumnOf(owner, workspace.boardId())), teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Mi parte\"}"))
                .andExpect(status().isCreated());

        // Y se le puede subir a responsable desde el propio equipo.
        mockMvc.perform(authorized(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/teams/{id}/members/{userId}", workspace.teamId(), teacher.id()), owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"RESPONSABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RESPONSABLE"));
    }

    @Test
    @DisplayName("Consultar un correo ofrece docente o alumno solo si la cuenta es docente, y solo lo ve el responsable")
    void candidateOffersTheChoiceOnlyForTeachers() throws Exception {
        TestUser owner = registerUser(Role.MIEMBRO);
        TestUser teacher = registerUser(Role.DOCENTE);
        TestUser student = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(owner, "Espacio");

        mockMvc.perform(authorized(get("/api/teams/{id}/invitations/candidate", workspace.teamId()).param("email", teacher.email()), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountRole").value("DOCENTE"))
                .andExpect(jsonPath("$.defaultRole").value("DOCENTE"))
                .andExpect(jsonPath("$.allowedRoles.length()").value(2));
        mockMvc.perform(authorized(get("/api/teams/{id}/invitations/candidate", workspace.teamId()).param("email", student.email()), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedRoles.length()").value(1))
                .andExpect(jsonPath("$.allowedRoles[0]").value("MIEMBRO"));
        mockMvc.perform(authorized(get("/api/teams/{id}/invitations/candidate", workspace.teamId()).param("email", "nadie@uni.es"), owner))
                .andExpect(status().isNotFound());
        // quien no es responsable del equipo no puede preguntar por cuentas
        mockMvc.perform(authorized(get("/api/teams/{id}/invitations/candidate", workspace.teamId()).param("email", teacher.email()), student))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una cuenta de alumno no se puede invitar como docente")
    void studentCannotBeInvitedAsTeacher() throws Exception {
        TestUser owner = registerUser(Role.MIEMBRO);
        TestUser student = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(owner, "Espacio");

        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", workspace.teamId()), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"DOCENTE\"}".formatted(student.email())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Un alumno que pasa a docente conserva sus espacios y su rol en cada uno")
    void promotedStudentKeepsItsWorkspaces() throws Exception {
        TestUser admin = registerUser(Role.ADMIN);
        TestUser student = registerUser(Role.MIEMBRO);
        Workspace own = createWorkspace(student, "Trabajo del alumno");

        mockMvc.perform(authorized(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/{id}/role", student.id()), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"DOCENTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCENTE"));

        mockMvc.perform(authorized(get("/api/workspaces"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==%d)].myRole".formatted(own.id())).value("RESPONSABLE"));
    }
}
