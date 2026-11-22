package com.example.travelsocialservice.client;

public interface MediaStorage {

    record StoredObject(String url, String objectName) {
    }

    class MediaStorageException extends RuntimeException {
        public MediaStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    StoredObject upload(String fileName, String mimeType, byte[] data);

    void delete(String objectName);
}
