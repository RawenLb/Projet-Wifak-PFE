package com.wifak.validationservice.exceptions;

public class DeclarationNotFoundException extends RuntimeException {
    public DeclarationNotFoundException(Long id) {
        super("Déclaration introuvable : ID = " + id);
    }
}