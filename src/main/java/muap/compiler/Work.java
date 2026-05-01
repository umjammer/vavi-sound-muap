package muap.compiler;

import java.util.ArrayList;
import java.util.List;

import musicDriverInterface.CompilerInfo;
import musicDriverInterface.MmlDatum;


/** */
public class Work {

    public CompilerInfo compilerInfo = null;
    public String sourceFileName = "";
    public int row = 0;
    public int col = 0;
    public int oldbx;
    public int ontei;
    public byte oct;
    public MmlDatum md;
    public int otoLength; // Note length
    public String crntChip;
    public int crntChannel;
    public String crntPart;
    public final List<MmlDatum> lstMd = new ArrayList<>();
    public int mdArgsStep = 0;

    /** */
    public MmlDatum copy(MmlDatum md, int dat) {
        MmlDatum md2;
        if (md == null) {
            md2 = new MmlDatum(dat);
            return md2;
        }

        md2 = new MmlDatum(md.type, md.args, md.linePos, dat);
        return md2;
    }

    /** */
    public MmlDatum FlashLstMd(MmlDatum md) {
        mdArgsStep = 0;
        if (md == null) return null;

        if (lstMd.size() <= 0) {
            return md;
        }

        List<Object> oldArgs = md.args;
        md.args = new ArrayList<>();

        md.args.add(new ArrayList<>(lstMd));
        if (oldArgs != null) {
            md.args.addAll(oldArgs);
        }

        mdArgsStep = 1;
        lstMd.clear();
        return md;
    }
}
