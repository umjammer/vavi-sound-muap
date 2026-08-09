package muap.compiler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.function.Function;

import muap.common.MusException;
import muap.common.X86Register;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.ICompiler;
import musicDriverInterface.MetaData;
import musicDriverInterface.MmlDatum;
import musicDriverInterface.common.AutoExtendList;
import vavi.util.compat.Tuple;
import vavi.util.compat.Tuple3;


/**
 * Main compiler class for muap implementing the iCompiler interface.
 * <p></p>
 * system. property
 * <li>{@code muap.dir.dta} ... {@code tones.dta} file path</li>
 */
public class Compiler implements ICompiler {

    private static final Logger logger = System.getLogger(Compiler.class.getName());

    private static final ResourceBundle rb = ResourceBundle.getBundle("muap/message");
    private static final Charset encoding = Charset.forName("ms932");

    private byte[] srcBuf = null;
    private Work work = null;
    private String tone_path = "TONES.DTA"; // + new string((char)0, 55);

    public Compiler() {
    }

    /**
     * @return null compile error
     */
    @Override
    public MmlDatum[] compile(InputStream sourceMML, Function<String, InputStream> appendFileReaderCallback) {
        Muap98 muap98;
        try {
            if (work == null) work = new Work();
            work.compilerInfo = new CompilerInfo();

            srcBuf = readAllBytesFromText(sourceMML);
            X86Register r = new X86Register();
            String dta = System.getProperty("muap.dir.dta");
            if (dta != null) {
                tone_path = new File(dta, tone_path).getPath();
            }
            muap98 = new Muap98(srcBuf, r, tone_path, work);
            Menu menu = new Menu(r, muap98);
            Mucom2 mc2 = new Mucom2(r, menu, muap98, null, work);
            MucomSub mucomsub = new MucomSub(r, mc2, muap98, work);
            mc2.mucomsub = mucomsub;
            mc2.init();

            mc2.compile();

            AutoExtendList<MmlDatum> obj = muap98.objectBuf;
            if (!obj.isEmpty() && obj.getFirst() != null) {
                // Check labels
                boolean fnd = false;
                for (int i = 0; i < mc2.mucomsub.labelAdrs.length; i++) {
                    if (mc2.mucomsub.labelAdrs[i] != 0) {
                        fnd = true;
                        break;
                    }
                }
                // Add tone information
                if (obj.getFirst().args == null) obj.getFirst().args = new ArrayList<>();
                obj.getFirst().args.add(muap98.toneBuff);
                obj.getFirst().args.add(fnd ? mc2.mucomsub.labelAdrs : null);
            }

            return obj.toArray(new MmlDatum[0]);
        } catch (MusException me) {
            // Log known compiler exceptions
            logger.log(Level.TRACE, me.getMessage(), me);
            logger.log(Level.ERROR, me.getMessage());
        } catch (Exception e) {
            if (work.compilerInfo == null) work.compilerInfo = new CompilerInfo();
            work.compilerInfo.errorList.add(new Tuple3<>(-1, -1, e.getMessage()));
            logger.log(Level.ERROR, e.getMessage(), e);
        }

//#if DEBUG
//        if (muap98 != null) {
//            for (int j = 0; j < 16 * 16; j++) {
//                StringBuilder hex = new StringBuilder(String.format("%02X: ", j * 16));
//                for (int i = 0; i < 16; i++) {
//                    hex.append(String.format("%02X ", muap98.objectBuf.get(i + j * 16).dat));
//                }
//                logger.log(Level.TRACE, hex.toString());
//            }
//        }
//#endif

        return null;
    }

    public boolean compile(InputStream sourceMML, ByteArrayOutputStream destCompiledBin, Function<String, InputStream> appendFileReaderCallback) {
        MmlDatum[] dat = compile(sourceMML, appendFileReaderCallback);
        if (dat == null) {
            return false;
        }
        for (MmlDatum md : dat) {
            if (md == null) {
                destCompiledBin.write((byte) 0);
            } else {
                destCompiledBin.write((byte) md.dat);
            }
        }
        return true;
    }

    @Override
    public CompilerInfo getCompilerInfo() {
        if (work.compilerInfo == null) {
            work.compilerInfo = new CompilerInfo();
        }
        return work.compilerInfo;
    }

    @Override
    public MetaData getMetaData(byte[] srcBuf) {
        if (work == null
                || work.compilerInfo == null
                || !(work.compilerInfo.additionalInfo instanceof MetaData)) {
            return null;
        }

        return (MetaData) work.compilerInfo.additionalInfo;
    }

    @Override
    public void init() {
        //throw new UnsupportedOperationException();
    }

    @Override
    public void setCompileSwitch(Object... param) {
        if (work == null) work = new Work();

        for (Object p : param) {
            if (p instanceof Tuple kvp) {
                if (((String) kvp.getKey()).equalsIgnoreCase("SOURCEFILENAME")) {
                    work.sourceFileName = (String) kvp.getValue();
                }
            }
        }
    }

    /** */
    private static byte[] readAllBytesFromText(InputStream stream) {
        if (stream == null) return null;

        try (BufferedReader sr = new BufferedReader(new InputStreamReader(stream, encoding));
             ByteArrayOutputStream ms = new ByteArrayOutputStream()) {
            String line;
            while ((line = sr.readLine()) != null) {
                ms.write(line.getBytes(encoding));
                ms.write(new byte[] {(byte) 0xfe});
            }
            ms.write(new byte[] {(byte) 0xff});
            return ms.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
