package com.delivery.dispatch.dto;

import jakarta.validation.constraints.Size;

public record WithdrawAssignmentRequest(@Size(max = 500) String reason) {
}
