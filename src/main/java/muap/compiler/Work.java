package muap.compiler;

import java.util.ArrayList;
import java.util.List;

import musicDriverInterface.CompilerInfo;
import musicDriverInterface.MmlDatum;


/**
 * Port of the Work class, which holds global state and context for the muapDotNET compiler.
 */
public class Work {

    // Basic compiler and file information
    public CompilerInfo compilerInfo = null;
    public String sourceFileName = "";

    // Position tracking in the source MML
    public int row = 0;
    public int col = 0;
    public int oldbx;

    // Musical note context
    public int ontei;
    public byte oct;
    public MmlDatum md;
    public int otoLength; // Note length

    // Target hardware and part information
    public String crntChip;
    public int crntChannel;
    public String crntPart;

    // Data structures for building compiled output
    public final List<MmlDatum> lstMd = new ArrayList<>();
    public int mdArgsStep = 0;

    /**
     * Creates a copy of an MmlDatum with a new data value.
     *
     * @param md  The original MmlDatum to copy.
     * @param dat The new data value to assign.
     * @return A new MmlDatum instance.
     */
    public MmlDatum copy(MmlDatum md, int dat) {
        MmlDatum md2;
        if (md == null) {
            md2 = new MmlDatum(dat);
            return md2;
        }

        md2 = new MmlDatum(md.type, md.args, md.linePos, dat);
        return md2;
    }

    /**
     * Consolidates a list of collected data into a single MmlDatum's arguments.
     *
     * @param md The target MmlDatum.
     * @return The updated MmlDatum with processed arguments.
     */
    public MmlDatum FlashLstMd(MmlDatum md) {
        mdArgsStep = 0;
        if (md == null) return null;

        if (lstMd.size() <= 0) {
            return md;
        }

        List<Object> oldArgs = md.args;
        md.args = new ArrayList<>();

        // Add the accumulated list and then add back the original arguments
        md.args.add(new ArrayList<>(lstMd));
        if (oldArgs != null) {
            md.args.addAll(oldArgs);
        }

        mdArgsStep = 1;
        lstMd.clear();
        return md;
    }
}
