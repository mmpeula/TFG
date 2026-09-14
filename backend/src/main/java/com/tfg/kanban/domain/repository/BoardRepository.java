package com.tfg.kanban.domain.repository;

import com.tfg.kanban.domain.model.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByProject_Id(Long projectId);
}
