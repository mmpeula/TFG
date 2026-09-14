package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Carga que le toca a cada persona con el reparto propuesto",
        example = "{\"memberId\":2,\"memberName\":\"Luis Perez\",\"tasks\":3,\"hours\":12.5}")
public record MemberWorkload(Long memberId, String memberName, int tasks, BigDecimal hours) {
}
