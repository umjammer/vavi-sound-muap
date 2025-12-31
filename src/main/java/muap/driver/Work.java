package muap.driver;

import musicDriverInterface.MmlDatum;


/**
 * Port of the Work class for the muapDotNET Driver.
 * This class maintains the runtime state of the music driver, including timers,
 * audio buffers, and synchronization objects.
 */
public class Work {

    // Synchronization objects for thread-safe operations and system interrupts
    public final Object lockObj = new Object();
    public final Object systemInterrupt = new Object();

    // Flag to synchronize reset operations
    public boolean resetPlaySync = false;

    private int status = 0;

    /**
     * Thread-safe access to the driver status.
     * 0: Stopped, 1: Playing, etc.
     */
    public int getStatus() {
        synchronized (lockObj) {
            return status;
        }
    }

    public void setStatus(int value) {
        synchronized (lockObj) {
            this.status = value;
        }
    }

    // Audio and timing buffers
    public byte[] fifoBuf;

    /**
     * Action delegate for the system interval interrupt (0bh).
     */
    public Runnable int0bEnt;

    // Hardware emulation components
    public OPNATimer timerOPNA1 = null;
    public long timeCounter = 0L;
    public int currentTimer = 0;

    /**
     * PCM audio buffer (16-bit stereo/mono).
     */
    public short[] sound;

    /**
     * PIT (Programmable Interval Timer) emulation.
     */
    public _8253Timer _8253timer = null;

    /**
     * The MML command currently being processed.
     */
    public MmlDatum crntMmlDatum = null;
    //public DMA dma=null;
    //public CS4231 cs4231 = null;

    /** Full compiled MML data set */
    public MmlDatum[] mData = null;

    /**
     * Initializes a new instance of the Work class.
     */
    public Work() {
        init();
    }

    /**
     * Performs initialization of the driver state.
     */
    private void init() {
        // Initialization logic as per the original source
    }
}
