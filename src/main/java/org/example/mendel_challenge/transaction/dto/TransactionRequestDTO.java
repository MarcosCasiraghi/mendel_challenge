package org.example.mendel_challenge.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransactionRequestDTO {

    @NotNull(message = "amount is required")
    private Double amount;

    @NotBlank(message = "type is required")
    private String type;

    @JsonProperty("parent_id")
    private Long parentId; // Optional
}
