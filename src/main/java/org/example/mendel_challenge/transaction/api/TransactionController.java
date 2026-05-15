package org.example.mendel_challenge.transaction.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mendel_challenge.transaction.domain.Transaction;
import org.example.mendel_challenge.transaction.dto.StatusResponse;
import org.example.mendel_challenge.transaction.dto.TransactionRequestDTO;
import org.example.mendel_challenge.transaction.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PutMapping("/{transaction_id}")
    public ResponseEntity<StatusResponse> createTransaction(
            @PathVariable("transaction_id") long transactionId,
            @Valid @RequestBody TransactionRequestDTO request) {

        log.info("Creating transaction with id: {}", transactionId);

        Transaction transaction = Transaction.builder()
                .id(transactionId)
                .amount(request.getAmount())
                .type(request.getType())
                .parentId(request.getParentId())
                .build();

        transactionService.save(transaction);

        return ResponseEntity.status(HttpStatus.CREATED).body(StatusResponse.ok());
    }

    @GetMapping("/types/{type}")
    public ResponseEntity<List<Long>> getTransactionsByType(
            @PathVariable String type
    ) {
        log.info("Getting transactions with type: {}", type);

        Set<Long> transactionsByType = transactionService.getTransactionsByType(type);

        return ResponseEntity.ok(new ArrayList<>(transactionsByType));

    }
}
