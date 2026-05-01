package muap.driver;


/**
 * Emulation of the OPNA (YM2608) timer.
 */
public class OPNATimer extends FMTimer {

    /** */
    public OPNATimer(int renderingFreq, int opnaMasterClock) {
        super(renderingFreq, opnaMasterClock);
        step = opnaMasterClock / 72.0 / 2.0 / (double) renderingFreq;
    }

    @Override
    public boolean writeReg(byte adr, byte data) {
        switch (adr & 0xff) {
            // Timer A (MSB)
            case 0x24:
                timerA &= 0x3;
                timerA |= ((data & 0xff) << 2);
                return true;
            case 0x25:
                timerA &= 0x3fc;
                timerA |= (data & 3);
                return true;
            // Timer B
            case 0x26:
                // Timer B
                timerB = (256 - (data & 0xff)) << 4;
                return true;
            case 0x27:
                // Timer Control Register
                timerReg = data & 0x8f;
                statReg &= 0xff - ((data >> 4) & 3);
                return true;
        }
        return false;
    }
}
