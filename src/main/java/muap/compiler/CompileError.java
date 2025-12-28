package muap.compiler;

import java.io.Serializable;


/**
 * Internal exception class used by the compiler to signal errors.
 */
class CompileError extends Exception implements Serializable {

    /**
     * Initializes a new instance of the compileError class.
     */
    public CompileError() {
        super();
    }

    /**
     * Initializes a new instance of the compileError class with a specified error message.
     *
     * @param message The message that describes the error.
     */
    public CompileError(String message) {
        super(message);
    }

    /**
     * Initializes a new instance of the compileError class with a specified error message
     * and a reference to the inner exception that is the cause of this exception.
     *
     * @param message        The error message that explains the reason for the exception.
     * @param innerException The exception that is the cause of the current exception.
     */
    public CompileError(String message, Throwable innerException) {
        super(message, innerException);
    }
}
