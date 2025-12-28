package muap.compiler;

import java.io.Serializable;


/**
 * Internal exception class used to signal a recovery process within the compiler's main loop.
 */
class MusRecov8Exception extends RuntimeException implements Serializable {

    /**
     * Initializes a new instance of the MusRecov8Exception class.
     */
    public MusRecov8Exception() {
        super();
    }

    /**
     * Initializes a new instance of the MusRecov8Exception class with a specified error message.
     *
     * @param message The message that describes the error.
     */
    public MusRecov8Exception(String message) {
        super(message);
    }

    /**
     * Initializes a new instance of the MusRecov8Exception class with a specified error message
     * and a reference to the inner exception that is the cause of this exception.
     *
     * @param message        The error message that explains the reason for the exception.
     * @param innerException The exception that is the cause of the current exception.
     */
    public MusRecov8Exception(String message, Throwable innerException) {
        super(message, innerException);
    }
}
