package muap.driver;

import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import muap.common.X86Register;
import muap.driver.Ems.EMS_AllocMemory;
import muap.driver.Ems.EMS_GetHandleName;
import muap.driver.Ems.EMS_Map;
import muap.driver.Ems.EMS_SetHandleName;
import musicDriverInterface.ChipAction;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.IDriver;
import musicDriverInterface.MetaData;
import musicDriverInterface.MmlDatum;
import vavi.util.compat.Tuple;


public class Driver implements IDriver {

    private static final Logger logger = System.getLogger(Driver.class.getName());

    public static final long cOPNAMasterClock = 7987200L;

    private final Object lockObjInt0BEnt = new Object();
    private final Object lockObjWriteReg = new Object();
    private Consumer<ChipDatum> writeOPNAP;
    private Consumer<ChipDatum> writeOPN2P;
    private Consumer<ChipDatum> writeC4231;
    private Function<Byte, Byte> readC4231;
    public Supplier<byte[]> cs4231EMS_GetCurrentMapBuf;
    public EMS_Map cs4231EMS_Map;
    public Supplier<Integer> cs4231EMS_GetPageMap;
    public EMS_GetHandleName cs4231EMS_GetHandleName;
    public EMS_SetHandleName cs4231EMS_SetHandleName;
    public EMS_AllocMemory cs4231EMS_AllocMemory;

    private BiFunction<Byte, Byte, Boolean> write8253;
    private final Map<String, String> envVars = Map.of(
            "DTA", System.getProperty("muap.dir.dta", System.getProperty("user.dir")),
            "PCM", System.getProperty("muap.dir.pcm", System.getProperty("user.dir")),
            "UDP", System.getProperty("muap.dir.udp", System.getProperty("user.dir")),
            "SUD", System.getProperty("muap.dir.sud", System.getProperty("user.dir"))
    ); // TODO env
    private Nax nax;
    public final Work work = new Work();
    private int renderingFreq = 44100;
    private int opnaMasterClock = (int) cOPNAMasterClock;
    private MmlDatum[] musicData;
    private byte[] toneBuffFromOutside = null;
    private int[] labelPtr = null;
    private String objPath = null;
    private int sdm = 0;

    public final short[] sound = new short[] {0, 0};

    public Driver() {
        work.sound = sound;
    }

    // interface from iDriver

    @Override
    public void fadeOut() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MmlDatum[] getData() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MetaData getMetaData(byte[] srcBuf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getNowLoopCounter() {
        if (nax == null || nax.play4 == null) return 0;

        int aLoopCnt = nax.play4.init_cnt & 0xFF;

        int lookUpLabel = 0;
        int max0 = 0;
        for (int i = 0; i < 17; i++) {
            int dmy = nax.play4.labelPassCnt[i * 40 + lookUpLabel];
            if (dmy >= 0) {
                max0 = Math.max(max0, dmy);
            }
        }

        return Math.max(aLoopCnt, max0);
    }

    @Override
    public byte[] getPCMFromSrcBuf() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChipDatum[] getPCMSendData() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Tuple<String, short[]>[] getPCMTable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getStatus() {
        // n>0: Performance in progress
        // n=0: Performance finished
        // n<0: Error
        return work.getStatus();
    }

    @Override
    public List<Tuple<String, String>> getTags() {
        if (nax == null) return null;
        try {
            List<Tuple<String, String>> tags = new ArrayList<>();
            tags.add(new Tuple<>("", nax.lyric));
            tags.add(new Tuple<>("", String.valueOf(nax.comlength & 0xFF)));
            return tags;
        } catch (Exception e) {
            return null;
        }
    }

    /** @return "work": work, "omt0bEnt": runnable? */
    @Override
    public Map<String, Object> getWork() {
        return Map.of(
                "work", work.fifoBuf,
                "int0bEnt", (Runnable) this::int0BEnt
        );
    }

    @Override
    public void init(List<ChipAction> chipsAction, MmlDatum[] srcBuf, Function<String, InputStream> appendFileReaderCallback, Object... additionalOption) {
        writeOPNAP = chipsAction.get(0)::writeRegister;
        writeOPN2P = chipsAction.get(1)::writeRegister;
        writeC4231 = chipsAction.get(2)::writeRegister;
        if (additionalOption != null) {
            if (additionalOption.length > 0) readC4231 = (Function<Byte, Byte>) additionalOption[0];
            if (additionalOption.length > 1) cs4231EMS_GetCurrentMapBuf = (Supplier<byte[]>) additionalOption[1];
            if (additionalOption.length > 2) cs4231EMS_Map = (EMS_Map) additionalOption[2];
            if (additionalOption.length > 3) cs4231EMS_GetPageMap = (Supplier<Integer>) additionalOption[3];
            if (additionalOption.length > 4) cs4231EMS_GetHandleName = (EMS_GetHandleName) additionalOption[4];
            if (additionalOption.length > 5) cs4231EMS_SetHandleName = (EMS_SetHandleName) additionalOption[5];
            if (additionalOption.length > 6) cs4231EMS_AllocMemory = (EMS_AllocMemory) additionalOption[6];
            if (additionalOption.length > 7) toneBuffFromOutside = (byte[]) additionalOption[7];
            if (additionalOption.length > 8) sdm = (Integer) additionalOption[8];
            if (additionalOption.length > 9) labelPtr = (int[]) additionalOption[9];
            if (additionalOption.length > 10) objPath = (String) additionalOption[10];
        }

        musicData = srcBuf;
    }

    @Override
    public void startMusic(int musicNumber) {
        //String opt = "/f0 /L1A /V0b /Y0288,0388 /I /OFF /P /T /M2 /BFF /6 /Q /2 /3 /(A /A8 /8";
        String opt = "/F0 /L60 /V0B             /I /OFF /P    /M2 /BFF /Q       /(A     /8 ";
        logger.log(Level.INFO, "Regist NAX3 (option:%s)".formatted(opt));
        X86Register reg = new X86Register();
        Pc98 pc98 = new Pc98(work, reg, this::writeOPNAPRegister, this::writeOPN2PRegister, this::writeC4231Register, this::readC4231Register, this::write8253Register, sdm);
        Ems ems = new Ems(
                cs4231EMS_GetCurrentMapBuf,
                cs4231EMS_Map,
                cs4231EMS_GetPageMap,
                cs4231EMS_GetHandleName,
                cs4231EMS_SetHandleName,
                cs4231EMS_AllocMemory);
        nax = new Nax(work, reg, envVars, pc98, ems, opt, toneBuffFromOutside, labelPtr, objPath);
        nax.function((byte) 10, musicData);
        work.setStatus(1);
    }

    @Override
    public void stopMusic() {
        if (nax == null) return;
        nax.function((byte) 2, null);
    }

    @Override
    public void render() {
        if (work.getStatus() < 0) return;

        try {
            if (!nax.functionList.isEmpty()) {
                nax.functionF(nax.functionList.getFirst());
                nax.functionList.removeFirst();
            }

            synchronized (work.systemInterrupt) {
                if ((nax != null && (nax.pc98.inportB(2) & 1) == 0))
                    work._8253timer.timer();

                work.timerOPNA1.timer();
                work.timeCounter++;

                boolean flg = false;
                if (work._8253timer.getCh0Stat() != 0) {
                    if (nax != null) nax.Int08Entry();
                }

                flg = switch (work.currentTimer) {
                    case 0 -> (work.timerOPNA1.statReg & 3) != 0;
                    default -> flg;
                };
                if (flg) {
                    if (nax != null) nax.TimerEntry();
                }
            }
        } catch (Exception ex) {
            logger.log(Level.ERROR, "Fatal error Message: " + ex.getMessage(), ex);
            work.setStatus(-1);
            throw ex;
        }
    }

    @Override
    public void setDriverSwitch(Object... param) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setLoopCount(int loopCounter) {
        return nax.play4.init_cnt & 0xFF;
    }

    @Override
    public void shotEffect() {
        throw new UnsupportedOperationException();
    }

    @SafeVarargs
    @Override
    public final void startRendering(int renderingFreq, Tuple<String, Integer>... chipMasterClocks) {
        synchronized (work.systemInterrupt) {
            work.timeCounter = 0L;
            this.renderingFreq = renderingFreq <= 0 ? 44100 : renderingFreq;
            this.opnaMasterClock = 7987200;
            if (chipMasterClocks != null && chipMasterClocks.length != 0) {
                this.opnaMasterClock = chipMasterClocks[0].getValue() <= 0 ? 7987200 : chipMasterClocks[0].getValue();
            }

            work.timerOPNA1 = new OPNATimer(this.renderingFreq, this.opnaMasterClock);
            work._8253timer = new _8253Timer(this.renderingFreq);
            logger.log(Level.TRACE, String.format("OPNA MasterClock %d".formatted(opnaMasterClock)));
            logger.log(Level.TRACE, "Start rendering.");
        }
    }

    @Override
    public void stopRendering() {
        synchronized (work.systemInterrupt) {
            if (work.getStatus() > 0) work.setStatus(0);
            logger.log(Level.TRACE, "Stop rendering.");
        }
    }

    @Override
    public void writeRegister(ChipDatum reg) {
        throw new UnsupportedOperationException();
    }

    public void writeOPNAPRegister(ChipDatum reg) {
        synchronized (lockObjWriteReg) {
            if (reg.port == 0) {
                Boolean ret = work.timerOPNA1 != null ? work.timerOPNA1.writeReg((byte) reg.address, (byte) reg.data) : null;
                if (ret != null && ret)
                    work.currentTimer = 0;
            }
            if (writeOPNAP != null) writeOPNAP.accept(reg);
        }
    }

    public void writeOPN2PRegister(ChipDatum reg) {
        synchronized (lockObjWriteReg) {
            if (writeOPN2P != null) writeOPN2P.accept(reg);
        }
    }

    public void writeC4231Register(ChipDatum reg) {
        synchronized (lockObjWriteReg) {
            if (writeC4231 != null) writeC4231.accept(reg);
        }
    }

    public byte readC4231Register(byte regAddr) {
        synchronized (lockObjWriteReg) {
            return readC4231 != null ? readC4231.apply(regAddr) : (byte) 0;
        }
    }

    private boolean write8253Register(byte arg1, byte arg2) {
        synchronized (lockObjWriteReg) {
            return work._8253timer.WriteReg(arg1, arg2);
        }
    }

    public void int0BEnt() {
        nax.Int0bEntry();
    }
}
