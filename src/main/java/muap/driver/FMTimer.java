package muap.driver;


/** */
public class FMTimer {

    /** Timer A overflow set value */
    public int timerA;
    /** Timer A counter value */
    protected double timerACounter;
    /** Timer B overflow set value */
    public int timerB;
    /** Timer B counter value */
    protected double timerBCounter;
    /** Timer control register (lower 4 bits + bit 7) */
    public int timerReg;
    public double step;

    /** Status register (lower 2 bits) */
    public int statReg;
    public Runnable csmKeyOn;

    /** */
    public FMTimer(int renderingFreq, int masterClock) {
    }

    /** */
    public void timer() {
        if ((timerReg & 0x01) != 0) {
            timerACounter += step;
            // Timer A is working
            if (timerACounter >= (1024 - timerA)) {
                statReg |= ((timerReg >> 2) & 0x01);
                timerACounter -= (1024 - timerA);
                //if ((timerReg & 0x80) != 0) if (csmKeyOn != null) csmKeyOn.run();
            }
        }

        if ((timerReg & 0x02) != 0) {
            // Timer B is working
            timerBCounter += step;
            if (timerBCounter >= timerB) {
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
