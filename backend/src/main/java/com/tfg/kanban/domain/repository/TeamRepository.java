package com.tfg.kanban.domain.repository;

import com.tfg.kanban.domain.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /** Equipos en los que participa el usuario. */
    Page<Team> findByMembers_User_Id(Long userId, Pageable pageable);

    List<Team> findByMembers_User_Id(Long userId);

    List<Team> findByOwner_Id(Long ownerId);
}
