package muap.common;

import java.io.Serializable;
import java.util.ResourceBundle;


/**
 * Custom exception class for the muapDotNET environment.
 */
public class MusException extends Exception implements Serializable {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Initializes a new instance of the MusException class.
     */
    public MusException() {
    }

    /**
     * Initializes a new instance of the MusException class with a specified error message.
     * * @param message The message that describes the error.
     */
    public MusException(String message) {
        super(message);
    }

    /**
     * Initializes a new instance of the MusException class with a specified error message
     * and a reference to the inner exception that is the cause of this exception.
     *
     * @param message        The error message that explains the reason for the exception.
     * @param innerException The exception that is the cause of the current exception.
     */
    public MusException(String message, Throwable innerException) {
        super(message, innerException);
    }

    /**
     * Initializes a new instance of the MusException class with formatted row and column information.
     *
     * @param message The base error message.
     * @param row     The row number where the error occurred.
     * @param col     The column number where the error occurred.
     */
    public MusException(String message, int row, int col) {
        // Formats the message using a key-based lookup equivalent to the C# msg.get call.
        super(String.format(rb.getString("E0300"), row, col, message));
    }
}
