package me.taromati.afreecatv.exception;

import lombok.Getter;

@Getter
public class AfreecatvException extends RuntimeException {

    private final String code;
    private final String message;

    public AfreecatvException(ExceptionCode code) {
        super(code.getMessage());

        this.message = code.getMessage();
        this.code = code.getCode();
    }
}
