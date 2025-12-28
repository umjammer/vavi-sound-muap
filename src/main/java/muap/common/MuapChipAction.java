package muap.common;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dotnet4j.util.compat.TriConsumer;
import musicDriverInterface.ChipAction;
import musicDriverInterface.ChipDatum;


/**
 * Implementation of ChipAction for the muapDotNET environment.
 */
public class MuapChipAction implements ChipAction {

    /** Functional interface equivalent to Action<ChipDatum> */
    private Consumer<ChipDatum> write;
    /** Functional interface for PCM data writing logic */
    private TriConsumer<byte[], Integer, Integer> writePcmData;
    /** Functional interface for wait/send logic */
    private BiConsumer<Long, Integer> waitSend;

    /**
     * Constructor for muapChipAction.
     *
     * @param write        Action to perform when writing a register.
     * @param writePcmData Action to perform when writing PCM data.
     * @param waitSend     Action to perform when waiting.
     */
    public MuapChipAction(Consumer<ChipDatum> write, TriConsumer<byte[], Integer, Integer> writePcmData, BiConsumer<Long, Integer> waitSend) {
        this.write = write;
        this.writePcmData = writePcmData;
        this.waitSend = waitSend;
    }

    @Override
    public String getChipName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void waitSend(long t1, int t2) {
    }

    @Override
    public void writePCMData(byte[] data, int startAddress, int endAddress) {
        if (writePcmData != null) {
            writePcmData.accept(data, startAddress, endAddress);
        }
    }

    @Override
    public void writeRegister(ChipDatum cd) {
        if (write != null) {
            write.accept(cd);
        }
    }
}
