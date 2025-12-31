package muap.driver;


/**
 * Port of the 8253 Programmable Interval Timer (PIT).
 *
 * @see "http://www.webtech.co.jp/company/doc/undocumented_mem/io_tcu.txt"
 */
public class _8253Timer {

    private final int renderingFreq;
    private final int masterClock;

    private static class Ch {

        private int _stat = 0;

        /**
         * Gets the status and resets it to 0.
         */
        public int getStat() {
            int bk = _stat;
            _stat = 0;
            return bk;
        }

        public void setStat(int value) {
            _stat = value;
        }

        public int c = 3;
        public int b = 0;
        public boolean a = false;
        public int val = 0;
        public double counter = 0.0;
        public double step = 0.0;
    }

    private Ch[] ch = new Ch[] {new Ch(), new Ch(), new Ch()};

    public int getCh0Stat() {
        return ch[0].getStat();
    }

    public int getCh1Stat() {
        return ch[1].getStat();
    }

    public int getCh2Stat() {
        return ch[2].getStat();
    }

    /**
     * Initializes the timer with rendering frequency and master clock.
     * Default master clock for PC98 5/10MHz systems is 1,996,800Hz.
     */
    public _8253Timer(int renderingFreq, int masterClock) {
        this.renderingFreq = renderingFreq;
        // The 5/10MHz PC98 is 1996800Hz, and the 8MHz PC98 is 2457600Hz.
        this.masterClock = masterClock;
    }

    public _8253Timer(int renderingFreq) {
        this(renderingFreq, 1996800);
    }

    /**
     * Advances the timer based on the rendering frequency.
     */
    public void timer() {
        ch[0].counter += ch[0].step;
        if (ch[0].counter >= 1.0) {
            ch[0].setStat(1);
            ch[0].counter -= 1.0;
        }
    }

    /**
     * Writes data to the specified timer register address.
     * Addresses 0x71, 0x73, 0x75 are for Counter 0, 1, and 2 respectively.
     * Address 0x77 is the Control Word Register.
     */
    public boolean WriteReg(byte adr, byte data) {
        int sc;
        switch (adr & 0xff) {
            case 0x71:
            case 0x73:
            case 0x75:
                sc = ((adr & 0xff) - 0x71) / 2;
                if (ch[sc].c != 3) return false;
                if (!ch[sc].a) {
                    ch[sc].val = (ch[sc].val & 0xff00) | (data & 0xff);
                } else {
                    ch[sc].val = (ch[sc].val & 0x00ff) | ((data & 0xff) << 8);
                }
                ch[sc].a = !ch[sc].a;
                ch[sc].step = 0;
                if (ch[sc].val != 0 && renderingFreq != 0) {
                    ch[sc].step = (double) masterClock / ch[sc].val / renderingFreq;
                }
                return true;
            case 0x77:
                sc = (data & 0b1100_0000) >> 6;
                if (sc == 3) return false; // Multiple latch command not supported
                int c = (data & 0b0011_0000) >> 4;
                if (c == 0) return false; // Count latch command not supported
                int m = (data & 0b0000_1110) >> 1;
                if (m > 5) m -= 4;
                if (m != 3) return false; // Only mode 3 supported (Square Wave Generator)
                int b = (data & 1);
                if (b != 0) return false; // Only binary count supported
                ch[sc].c = c;
                ch[sc].b = b;
                return true;

            default:
                return false;
        }
    }
}
