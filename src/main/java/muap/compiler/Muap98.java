package muap.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import muap.common.MusException;
import muap.common.X86Register;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.MmlDatum;
import musicDriverInterface.common.AutoExtendList;
import vavi.util.compat.Tuple3;


/** */
class Muap98 {

    final byte[] m_mode = {0, 0, 0, 8};
    static final int object_ = 0xc000;
    final AutoExtendList<MmlDatum> objectBuf = new AutoExtendList<>(MmlDatum.class);
    public static final int source = 0xb000;
    byte[] sourceBuf = null;
    /** Kuma: TONEOFS buffer is defined separately */
    public static final int text = 0xa800;
    final byte[] text_Buf = new byte[0x8000];
    /** $124 tone data buffer segment */
    public static final int tone = 0;
    /** Kuma: Buffer for storing tone data */
    public byte[] toneBuff;
    /** $126 buffer length (source) */
    public static final int buflens = 0x8000;
    /** $128 buffer length (object) */
    public static final int bufleno = 0x8000;
    /** $12c source data length */
    public int sor_len = 0;
    /** $12e object data length */
    public int obj_len = 0;
    public final byte[] bufbuf = new byte[128];
    private final X86Register r;

    /** */
    public Muap98(byte[] srcBuf, X86Register reg, String tone_path, Work work) throws MusException {
        for (int i = 0; i < 0x10000; i++) {
            objectBuf.set(i, new MmlDatum());
        }

        this.sourceBuf = srcBuf;
        this.sor_len = (srcBuf != null) ? (srcBuf.length & 0xffff) : 0;
        this.r = reg;

        File toneFile = new File(tone_path);
        if (!toneFile.exists()) {
            String errorMsg = "%s was not found".formatted(tone_path);
            if (work.compilerInfo == null) work.compilerInfo = new CompilerInfo();
            work.compilerInfo.errorList.add(new Tuple3<>(-1, -1, errorMsg));
            throw new MusException(errorMsg);
        }

        try {
            byte[] buf = Files.readAllBytes(toneFile.toPath());
            toneBuff = new byte[6400];
            System.arraycopy(buf, 0, toneBuff, 0, Math.min(buf.length, 6400));
        } catch (IOException e) {
            throw new MusException("Error reading tone file: " + e.getMessage());
        }
    }

    /** */
    public void call_func() {
        if ((m_mode[1] & 1) == 0) {
            //je skip_call
            r.carry = true;
            return;
        }
        //call_add(); // Kuma: TBD
        r.carry = false;
    }
}
