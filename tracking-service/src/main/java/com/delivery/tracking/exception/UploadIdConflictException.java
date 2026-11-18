package com.delivery.tracking.exception;

public class UploadIdConflictException extends RuntimeException {

    public UploadIdConflictException(String uploadId) {
        super("uploadId " + uploadId + " is already used for another shipment");
    }
}
