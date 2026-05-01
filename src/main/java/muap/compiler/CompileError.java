package muap.compiler;

import java.io.Serializable;


/** */
class CompileError extends Exception implements Serializable {

    /** */
    public CompileError() {
        super();
    }

    /** */
    public CompileError(String message) {
        super(message);
    }

    /** */
    public CompileError(String message, Throwable innerException) {
        super(message, innerException);
    }
}
