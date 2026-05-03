package muap.common;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import musicDriverInterface.ChipAction;
import musicDriverInterface.ChipDatum;
import vavi.util.compat.TriConsumer;


/** */
public class MuapChipAction implements ChipAction {

    private final Consumer<ChipDatum> write;
    private final TriConsumer<byte[], Integer, Integer> writePcmData;
    private final BiConsumer<Long, Integer> waitSend;

    /** */
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
