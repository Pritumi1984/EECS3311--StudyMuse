package com.studymuse.agent;

/** The LLM reply could not be parsed into the expected JSON structure. */
public class ResponseFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ResponseFormatException(String message) {
        super(message);
    }
}
