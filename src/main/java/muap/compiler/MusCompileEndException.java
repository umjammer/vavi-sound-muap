package muap.compiler;

import java.io.Serializable;


/**
 * Internal exception class used to signal the end of the compilation process.
 */
class MusCompileEndException extends RuntimeException implements Serializable {

    /**
     * Initializes a new instance of the MusCompileEndException class.
     */
    public MusCompileEndException() {
        super();
    }

    /**
     * Initializes a new instance of the MusCompileEndException class with a specified error message.
     *
     * @param message The message that describes the error.
     */
    public MusCompileEndException(String message) {
        super(message);
    }

    /**
     * Initializes a new instance of the MusCompileEndException class with a specified error message
     * and a reference to the inner exception that is the cause of this exception.
     *
     * @param message        The error message that explains the reason for the exception.
     * @param innerException The exception that is the cause of the current exception.
     */
    public MusCompileEndException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
