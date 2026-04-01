package muap.player;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.instrument.Cs4231Inst;
import mdsound.instrument.Ym2608Inst;
import mdsound.instrument.Ym3438Inst;
import muap.common.MuapChipAction;
import muap.driver.Driver;
import muap.driver.Ems.EMS_AllocMemory;
import muap.driver.Ems.EMS_GetHandleName;
import muap.driver.Ems.EMS_Map;
import muap.driver.Ems.EMS_SetHandleName;
import musicDriverInterface.ChipAction;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.MmlDatum;
import org.apache.tools.ant.types.LogLevel;


public class Program {

    private static final Logger logger = System.getLogger(Program.class.getName());

    private static int device = 0;
    private static int loop = 0;
    private static final int latency = 1000;
    private static MDSound mds = null;
    private static final short[] emuRenderBuf = new short[2];
    private static final int SamplingRate = 55467;
    private static final int samplingBuffer = 1024;
    private static SourceDataLine audioOutput = null;
    private static final long opnaMasterClock = 7987200;

    private static final int MAXBUF = 18;
    private static final int FIFO_SIZE = 128;
    private static byte[] fifoBuf = new byte[FIFO_SIZE * MAXBUF * 2];

    private static Driver drv;
    private static Cs4231Inst cS4231;

    public static void main(String[] args) throws Exception {

        logger.log(Level.INFO, "Hello, muap!");

        int fnIndex = analyzeOption(args);
        if (args == null || args.length != fnIndex + 1) {
            logger.log(Level.ERROR, "I need one argument (.o/.oy file)."); // I need one argument (.o/.oy file).
            System.exit(-1);
        }
        if (!Files.exists(Paths.get(args[fnIndex]))) {
            logger.log(Level.ERROR, "File not found.");
            System.exit(-1);
        }

        List<MmlDatum> bl = new ArrayList<>();
        byte[] srcBuf = Files.readAllBytes(Paths.get(args[fnIndex]));
        String objPath = Paths.get(args[fnIndex]).toAbsolutePath().getParent().toString();
        for (byte b : srcBuf) bl.add(new MmlDatum(b & 0xff));
        MmlDatum[] blary = bl.toArray(new MmlDatum[0]);

        // Audio setup (using standard Java Sound API as a replacement for DirectSoundOut)
        AudioFormat format = new AudioFormat(SamplingRate, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        audioOutput = (SourceDataLine) AudioSystem.getLine(info);
        audioOutput.open(format, (int) (SamplingRate * (latency / 1000.0) * 4));

        List<MDSound.Chip> lstChips = new ArrayList<>();
        MDSound.Chip chip;

        Ym2608Inst ym2608 = Instrument.getInstrument(Ym2608Inst.class);
        for (int i = 0; i < 1; i++) {
            chip = new MDSound.Chip();
            chip.id = i;
            chip.instrument = ym2608;
            chip.samplingRate = SamplingRate;
            chip.clock = (int) opnaMasterClock;
            chip.volume = 0;
            chip.option = null;
            lstChips.add(chip);
        }

        Ym3438Inst ym3438 = Instrument.getInstrument(Ym3438Inst.class);
        for (int i = 0; i < 1; i++) {
            chip = new MDSound.Chip();
            chip.id = i;
            chip.instrument = ym3438;
            chip.samplingRate = SamplingRate;
            chip.clock = (int) opnaMasterClock;
            chip.volume = 0;
            chip.option = null;
            lstChips.add(chip);
        }

        cS4231 = Instrument.getInstrument(Cs4231Inst.class);
        for (int i = 0; i < 1; i++) {
            chip = new MDSound.Chip();
            chip.id = i;
            chip.instrument = cS4231;
            chip.samplingRate = SamplingRate;
            chip.clock = 0;
            chip.volume = 0;
            chip.option = null;
            lstChips.add(chip);
        }

        mds = new MDSound(SamplingRate, samplingBuffer, lstChips);

        mds.inst(Ym2608Inst.class).setVolume("PSG", -10, 0);
        mds.inst(Ym2608Inst.class).setVolume("Rhythm", 5, 0);

        List<ChipAction> lca = new ArrayList<>();
        lca.add(new MuapChipAction(Program::opnaWriteP, null, null));
        lca.add(new MuapChipAction(Program::opn2WriteP, null, null));
        lca.add(new MuapChipAction(Program::cs4231Write, null, null));

        drv = new Driver();
        drv.init(lca, blary, null, (Function<Byte, Byte>) Program::cs4231Read,
                (Supplier<byte[]>) Program::cs4231EMS_GetCrntMapBuf,
                (EMS_Map) Program::cs4231EMS_Map,
                (Supplier<Integer>) Program::cs4231EMS_GetPageMap,
                (EMS_GetHandleName) Program::cs4231EMS_GetHandleName,
                (EMS_SetHandleName) Program::cs4231EMS_SetHandleName,
                (EMS_AllocMemory) Program::cs4231EMS_AllocMemory,
                null,
                0,
                null,
                objPath);

        logger.log(Level.INFO, "Press any key to exit.");

        drv.startRendering(SamplingRate, new Tuple<>("YM2608", (int) opnaMasterClock));
        drv.startMusic(0);
        cS4231.setFifoBuf(0, drv.work.fifoBuf);

        audioOutput.start();

        // Audio rendering thread
        Thread audioThread = new Thread(() -> {
            byte[] byteBuffer = new byte[samplingBuffer * 4];
            short[] shortBuffer = new short[samplingBuffer * 2];
            while (audioOutput.isOpen()) {
                emuCallback(shortBuffer, 0, shortBuffer.length);
                ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shortBuffer);
                audioOutput.write(byteBuffer, 0, byteBuffer.length);
            }
        });
        audioThread.start();

        while (true) {
            Thread.sleep(1);
            // Simulating Console.KeyAvailable in Java
            if (System.in.available() > 0) break;

            // If status is 0 (end) or less than 0 (error), exit the loop and finish
            if (drv.getStatus() <= 0) {
                if (drv.getStatus() == 0) {
                    Thread.sleep((long) (latency * 2.0)); // Wait for latency * 2 until the actual sound is fully produced
                }
                break;
            }
            if (drv.getNowLoopCounter() > 0) {
                Thread.sleep((long) (latency * 2.0)); // Wait for latency * 2 until the actual sound is fully produced
                break;
            }
        }

        Thread.sleep(2000);
        drv.stopMusic();
        drv.stopRendering();
        audioOutput.stop();
        audioOutput.close();
    }

    private static int analyzeOption(String[] args) {
        int i = 0;

        device = 0;
        loop = 0;

        while (i < args.length && args[i] != null && !args[i].isEmpty() && args[i].charAt(0) == '-') {
            String op = args[i].substring(1).toUpperCase();
            if (op.equals("D=EMU")) device = 0;
            if (op.equals("D=GIMIC")) device = 1;
            if (op.equals("D=SCCI")) device = 2;
            if (op.equals("D=WAVE")) device = 3;

            i++;
        }

        if (device == 3 && loop == 0) loop = 1;

        return i;
    }

    private static void writeLine(LogLevel level, String msg) {
        System.out.printf("[%s] %s%n", String.format("%-7s", level), msg);
    }

    private static void writeLineF(LogLevel level, String msg) {
        try (FileWriter fw = new FileWriter("C:\\Users\\kuma\\Desktop\\new.log", true)) {
            fw.write(String.format("[%s] %s%n", String.format("%-7s", level), msg));
        } catch (IOException e) {
        }
    }

    private static int emuCallback(short[] buffer, int offset, int count) {
        try {
            int bufCnt = count / 2;
            for (int i = 0; i < bufCnt; i++) {
                mds.update(emuRenderBuf, 0, 2, Program::oneFrame);

                emuRenderBuf[0] = (short) Math.clamp(emuRenderBuf[0] + drv.sound[0], Short.MIN_VALUE, Short.MAX_VALUE);
                emuRenderBuf[1] = (short) Math.clamp(emuRenderBuf[1] + drv.sound[1], Short.MIN_VALUE, Short.MAX_VALUE);

                buffer[offset + i * 2 + 0] = emuRenderBuf[0];
                buffer[offset + i * 2 + 1] = emuRenderBuf[1];
            }
        } catch (Exception e) {
        }
        return count;
    }

    private static void oneFrame() {
        drv.render();
    }

    private static void opnaWriteP(ChipDatum dat) {
        opnaWrite(0, dat);
    }

    private static void opn2WriteP(ChipDatum dat) {
        opn2Write(0, dat);
    }

    private static void opnaWrite(int chipId, ChipDatum dat) {
        if (dat.port == -1) return;
        mds.inst(Ym2608Inst.class).write(chipId, dat.port, dat.address, dat.data);
    }

    private static void opn2Write(int chipId, ChipDatum dat) {
        if (dat.port == -1) return;
        mds.inst(Ym3438Inst.class).write(chipId, dat.port, dat.address, dat.data);
    }

    private static void cs4231Write(ChipDatum dat) {
        mds.inst(Cs4231Inst.class).write(0, dat.port, dat.address, dat.data);
    }

    private static byte cs4231Read(byte adr) {
        return (byte) mds.inst(Cs4231Inst.class).read(0, adr & 0xff);
    }

    private static byte[] cs4231EMS_GetCrntMapBuf() {
        return cS4231.EMS_GetCurrentMapBuf(0);
    }

    private static void cs4231EMS_Map(int al, byte[] ah, int bx, int dx) {
        cS4231.EMS_Map(0, al, ah, bx, dx);
    }

    private static int cs4231EMS_GetPageMap() {
        return cS4231.EMS_GetPageMap(0);
    }

    private static void cs4231EMS_GetHandleName(byte[] ah, int dx, String[] sbuf) {
        cS4231.EMS_GetHandleName(0, ah, dx, sbuf);
    }

    private static void cs4231EMS_SetHandleName(byte[] ah, int dx, String emsname2) {
        cS4231.EMS_SetHandleName(0, ah, dx, emsname2);
    }

    private static void cs4231EMS_AllocMemory(byte[] ah, int[] dx, int bx) {
        cS4231.EMS_AllocMemory(0, ah, dx, bx);
    }

//    public static class SineWaveProvider16 {
//
//        public naudioCallBack callback;
//
//        public int read(short[] buffer, int offset, int sampleCount) {
//            return callback.apply(buffer, offset, sampleCount);
//        }
//    }

//    public interface naudioCallBack extends TriFunction<short[], Integer, Integer, Integer> {
//    }
}
