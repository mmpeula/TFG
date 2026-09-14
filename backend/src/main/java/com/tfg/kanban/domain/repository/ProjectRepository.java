package com.tfg.kanban.domain.repository;

import com.tfg.kanban.domain.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    List<Project> findByTeam_Id(Long teamId);

    boolean existsByTeam_IdAndNameIgnoreCase(Long teamId, String name);
}
