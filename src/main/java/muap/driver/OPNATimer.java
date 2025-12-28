package muap.driver;

/**
 * Emulation of the OPNA (YM2608) timer.
 * Extends the FMTimer base class to provide specific register handling for the OPNA.
 */
public class OPNATimer extends FMTimer {

    /**
     * Initializes a new instance of the OPNATimer class.
     *
     * @param renderingFreq   The output sampling frequency.
     * @param opnaMasterClock The master clock frequency of the OPNA chip.
     */
    public OPNATimer(int renderingFreq, int opnaMasterClock) {
        super(renderingFreq, opnaMasterClock);
        // Step calculation based on the OPNA internal clock divider (72 * 2)
        step = opnaMasterClock / 72.0 / 2.0 / (double) renderingFreq;
    }

    /**
     * Writes data to the timer-related registers of the OPNA.
     *
     * @param adr  The register address.
     * @param data The byte data to write.
     * @return true if the address was a handled timer register; otherwise false.
     */
    @Override
    public boolean writeReg(byte adr, byte data) {
        switch (adr & 0xff) {
            // Timer A (MSB)
            case 0x24:
                timerA &= 0x3;
                timerA |= ((data & 0xff) << 2);
                return true;
            // Timer A (LSB)
            case 0x25:
                timerA &= 0x3fc;
                timerA |= (data & 3);
                return true;
            // Timer B
            case 0x26:
                // Timer B value is 8-bit, counting up from the written value to 256.
                // The resolution is shifted by 4.
                timerB = (256 - (data & 0xff)) << 4;
                return true;
            // Timer Control Register
            case 0x27:
                // Bit 7: Reset flags, Bits 0-3: Enable/Start timers
                timerReg = data & 0x8f;
                // Reset status flags based on written bits
                statReg &= 0xff - ((data >> 4) & 3);
                return true;
        }
        return false;
    }
}
