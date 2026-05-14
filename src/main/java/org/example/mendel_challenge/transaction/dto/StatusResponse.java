package org.example.mendel_challenge.transaction.dto;

public record StatusResponse(String status) {
    public static StatusResponse ok() {
        return new StatusResponse("ok");
    }
}
