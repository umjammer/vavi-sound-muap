package muap.common;

import java.io.Serializable;
import java.util.ResourceBundle;


/** */
public class MusException extends RuntimeException implements Serializable {

    private static final ResourceBundle rb = ResourceBundle.getBundle("muap/message");

    /** */
    public MusException() {
    }

    /** */
    public MusException(String message) {
        super(message);
    }

    /** */
    public MusException(String message, Throwable innerException) {
        super(message, innerException);
    }

    /** */
    public MusException(String message, int row, int col) {
        super(String.format(rb.getString("E0300"), row, col, message));
    }
}
