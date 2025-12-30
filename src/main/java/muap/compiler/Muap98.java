package muap.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import dotnet4j.util.compat.Tuple3;
import muap.common.MusException;
import muap.common.X86Register;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.MmlDatum;
import musicDriverInterface.common.AutoExtendList;


/**
 * Port of muap98. Provides buffer management and tone data initialization for the compiler.
 */
public class Muap98 {

    /** Current mode flags */
    public byte[] m_mode = new byte[] {0, 0, 0, 8};
    /** Segment address for object data */
    public int object_ = 0xc000;
    /** Buffer for compiled object data */
    public AutoExtendList<MmlDatum> objectBuf = new AutoExtendList<>(MmlDatum.class);
    /** Segment address for source data */
    public int source = 0xb000;
    /** Buffer for source MML data */
    public byte[] sourceBuf = null;
    /** Buffer for text data; note: TONEOFS buffer is defined separately */
    public int text = 0xa800;
    public byte[] text_Buf = new byte[0x8000];
    /** Segment for tone data buffer ($124) */
    public int tone = 0;
    /** Buffer for storing tone data */
    public byte[] toneBuff = null;
    /** Buffer length for source data ($126) */
    public int buflens = 0x8000;
    /** Buffer length for object data ($128) */
    public int bufleno = 0x8000;
    /** Source data length ($12c) */
    public int sor_len = 0;
    /** Object data length ($12e) */
    public int obj_len = 0;
    public byte[] bufbuf = new byte[128];
    private X86Register r;

    /**
     * Initializes the muap98 environment, loads source buffer, and reads tone data.
     */
    public Muap98(byte[] srcBuf, X86Register reg, String tone_path, Work work) throws MusException {
        // Initialize object buffer with MmlDatum instances
        for (int i = 0; i < 0x10000; i++) {
            objectBuf.set(i, new MmlDatum());
        }

        this.sourceBuf = srcBuf;
        this.sor_len = (srcBuf != null) ? (srcBuf.length & 0xffff) : 0;
        this.r = reg;

        File toneFile = new File(tone_path);
        if (!toneFile.exists()) {
            // Error: Tone file not found
            String errorMsg = "%s was not found".formatted(tone_path);
            if (work.compilerInfo == null) work.compilerInfo = new CompilerInfo();
            work.compilerInfo.errorList.add(new Tuple3<>(-1, -1, errorMsg));
            throw new MusException(errorMsg);
        }

        try {
            byte[] buf = Files.readAllBytes(toneFile.toPath());
            toneBuff = new byte[6400];
            // Copy tone data, capping at 6400 bytes
            System.arraycopy(buf, 0, toneBuff, 0, Math.min(buf.length, 6400));
        } catch (IOException e) {
            throw new MusException("Error reading tone file: " + e.getMessage());
        }
    }

    /**
     * Executes external function calls if enabled in m_mode.
     */
    public void call_func() {
        if ((m_mode[1] & 1) == 0) {
            // Equivalent to je skip_call
            r.carry = true;
            return;
        }
        // call_add(); // TBD
        r.carry = false;
    }
}
