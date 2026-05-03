package muap.driver;

import musicDriverInterface.MmlDatum;


/** */
public class Work {

    public final Object systemInterrupt = new Object();
    public boolean resetPlaySync = false;
    private int status = 0;

    /** 0: Stopped, 1: Playing, */
    public synchronized int getStatus() {
        return status;
    }

    public synchronized void setStatus(int value) {
        this.status = value;
    }

    public byte[] fifoBuf;

    public Runnable int0bEnt;

    public OPNATimer timerOPNA1 = null;
    public long timeCounter = 0L;
    public int currentTimer = 0;

    public short[] sound;

    public _8253Timer _8253timer = null;

    public MmlDatum crntMmlDatum = null;
    //public DMA dma=null;
    //public CS4231 cs4231 = null;

    public MmlDatum[] mData = null;

    public Work() {
        init();
    }

    private void init() {
    }
}
