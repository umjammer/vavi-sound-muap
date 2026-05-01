package muap.compiler;

import java.io.Serializable;


/** */
class MusRecov8Exception extends RuntimeException implements Serializable {

    /** */
    public MusRecov8Exception() {
        super();
    }

    /** */
    public MusRecov8Exception(String message) {
        super(message);
    }

    /** */
    public MusRecov8Exception(String message, Throwable innerException) {
        super(message, innerException);
    }
}
