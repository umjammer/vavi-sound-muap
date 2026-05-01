package muap.compiler;

import java.io.Serializable;


/** */
class MusCompileEndException extends RuntimeException implements Serializable {

    /** */
    public MusCompileEndException() {
        super();
    }

    /** */
    public MusCompileEndException(String message) {
        super(message);
    }

    /** */
    public MusCompileEndException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
