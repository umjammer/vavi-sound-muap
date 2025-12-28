package muap.driver;

/**
 * Base class for FM sound source timer emulation (e.g., YM2608, YM2203).
 * Handles the logic for Timer A and Timer B overflow and status register updates.
 */
public class FMTimer {

    /** Timer A overflow setting value. */
    public int timerA;
    /** Current counter value for Timer A. */
    protected double timerACounter;
    /** Timer B overflow setting value. */
    public int timerB;
    /** Current counter value for Timer B. */
    protected double timerBCounter;
    /** Timer control register (lower 4 bits + bit 7). */
    public int timerReg;
    /** The amount to increment the counters per step. */
    public double step;

    /** Status register (lower 2 bits representing timer overflows). */
    public int statReg;
    /** Callback for Composite Sine Mode (CSM) Key On events. */
    public Runnable csmKeyOn;

    /**
     * Initializes a new instance of the FMTimer class.
     *
     * @param renderingFreq The sampling frequency of the output.
     * @param masterClock   The master clock frequency of the sound chip.
     */
    public FMTimer(int renderingFreq, int masterClock) {
        // Initialization logic is empty in source.
    }

    /**
     * Updates the timers based on the current step.
     */
    public void timer() {
        // Bit 0 of timerReg indicates Timer A is running.
        if ((timerReg & 0x01) != 0) {
            timerACounter += step;
            // Timer A is 10-bit; it overflows when reaching 1024.
            if (timerACounter >= (1024 - timerA)) {
                // Update status register based on the overflow mask.
                statReg |= ((timerReg >> 2) & 0x01);
                timerACounter -= (1024 - timerA);
                // Bit 7 indicates CSM mode; triggering Key On is handled if enabled.
                // if ((timerReg & 0x80) != 0) if (csmKeyOn != null) csmKeyOn.run();
            }
        }

        // Bit 1 of timerReg indicates Timer B is running.
        if ((timerReg & 0x02) != 0) {
            timerBCounter += step;
            if (timerBCounter >= timerB) {
                // Update status register based on the overflow mask.
                statReg |= ((timerReg >> 2) & 0x02);
                timerBCounter -= timerB;
            }
        }
    }

    /**
     * Virtual method for writing to timer registers, to be implemented by specific chip classes.
     *
     * @param adr  Register address.
     * @param data Byte data.
     * @return true if the register was handled; otherwise false.
     */
    public boolean writeReg(byte adr, byte data) {
        return false;
    }
}
