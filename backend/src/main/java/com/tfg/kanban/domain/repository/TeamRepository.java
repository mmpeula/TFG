package com.tfg.kanban.domain.repository;

import com.tfg.kanban.domain.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
