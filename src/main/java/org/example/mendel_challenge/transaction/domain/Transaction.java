package org.example.mendel_challenge.transaction.domain;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
public class Transaction {

    private long id;

    private Long parentId; // Optional

    private double amount;

    private String type;
}
