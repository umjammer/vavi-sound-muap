package muap.compiler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;

import dotnet4j.io.FileStream;
import dotnet4j.io.Stream;
import dotnet4j.io.compat.StreamInputStream;
import dotnet4j.util.compat.Tuple;
import dotnet4j.util.compat.Tuple3;
import muap.common.MusException;
import muap.common.X86Register;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.GD3Tag;
import musicDriverInterface.ICompiler;
import musicDriverInterface.MmlDatum;
import musicDriverInterface.common.AutoExtendList;


/**
 * Main compiler class for muapDotNET implementing the iCompiler interface.
 */
public class Compiler implements ICompiler {

    private static final Logger logger = System.getLogger(Compiler.class.getName());

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    private byte[] srcBuf = null;
    private Work work = null;
    private String tone_path = "TONES.DTA"; // + new string((char)0, 55);

    public Compiler() {
    }

    @Override
    public MmlDatum[] compile(Stream sourceMML, Function<String, Stream> appendFileReaderCallback) {
        Muap98 muap98 = null;
        try {
            if (work == null) work = new Work();
            work.compilerInfo = new CompilerInfo();

            srcBuf = readAllBytesFromText(sourceMML);
            X86Register r = new X86Register();

            // Get environment variables to check for DTA path
            Map<String, String> envVars = System.getenv();
            if (envVars.containsKey("DTA")) {
                tone_path = new File(envVars.get("DTA"), tone_path).getPath();
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
            logger.log(Level.ERROR, me.getMessage());
        } catch (Exception e) {
            if (work.compilerInfo == null) work.compilerInfo = new CompilerInfo();
            work.compilerInfo.errorList.add(new Tuple3<>(-1, -1, e.getMessage()));
            logger.log(Level.ERROR, String.format(rb.getString("E0000"), e.getMessage()), e);
        }

        // Removed Debug block containing hex dump for brevity

        return null;
    }

    public boolean compile(FileStream sourceMML, Stream destCompiledBin, Function<String, Stream> appendFileReaderCallback) {
        MmlDatum[] dat = compile(sourceMML, appendFileReaderCallback);
        if (dat == null) {
            return false;
        }
        try {
            for (MmlDatum md : dat) {
                if (md == null) {
                    destCompiledBin.writeByte((byte) 0);
                } else {
                    destCompiledBin.writeByte((byte) md.dat);
                }
            }
        } catch (dotnet4j.io.IOException e) {
            return false;
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
    public GD3Tag getGD3TagInfo(byte[] srcBuf) {
        if (work == null
                || work.compilerInfo == null
                || !(work.compilerInfo.additionalInfo instanceof GD3Tag)) {
            return null;
        }

        return (GD3Tag) work.compilerInfo.additionalInfo;
    }

    @Override
    public void init() {
        // Method body is empty in source
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

    /**
     * Read binary data in bulk from a stream.
     */
    private static byte[] ReadAllBytes(Stream stream) {
        if (stream == null) return null;

        byte[] buf = new byte[8192];
        try (ByteArrayOutputStream ms = new ByteArrayOutputStream()) {
            while (true) {
                int r = stream.read(buf, 0, buf.length);
                if (r < 1) {
                    break;
                }
                ms.write(buf, 0, r);
            }
            return ms.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Read text from stream and convert to Shift-JIS array format.
     */
    private static byte[] readAllBytesFromText(Stream stream) {
        if (stream == null) return null;

        try (BufferedReader sr = new BufferedReader(new InputStreamReader(new StreamInputStream(stream), Charset.forName("Windows-31J")))) {
            try (ByteArrayOutputStream ms = new ByteArrayOutputStream()) {
                String line;
                while ((line = sr.readLine()) != null) {
                    ms.write(line.getBytes(Charset.forName("Windows-31J")));
                    ms.write(new byte[] {(byte) 0xfe});
                }
                ms.write(new byte[] {(byte) 0xff});
                return ms.toByteArray();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
