package muap.console;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ResourceBundle;

import muap.compiler.Compiler;
import musicDriverInterface.MmlDatum;
import vavi.util.serdes.Serdes;

import static vavi.util.compat.Util.changeExtension;
import static vavi.util.compat.Util.getExtension;


/**
 * Main entry point for the muap console application.
 */
public class Program {

    private static final Logger logger = System.getLogger(Program.class.getName());

    private static final ResourceBundle rb = ResourceBundle.getBundle("muap/message");

    private String srcFile;
    private boolean isSeli = false;
    public static boolean isTest = false;

    public static void main(String[] args) throws Exception {

        logger.log(Level.INFO, "Hello, muap!");

        Program app = new Program();

        int fnIndex = app.analyzeOption(args);

        if (args.length < 1 + fnIndex) {
            logger.log(Level.INFO, rb.getString("I0600"));
            return;
        }

        try {

            app.compile(args[fnIndex], (args.length > fnIndex + 1 ? args[fnIndex + 1] : null));

        } catch (Exception ex) {
            logger.log(Level.ERROR, ex.getMessage(), ex);
            if (isTest) throw ex;
        }
    }

    /** */
    private void compile(String srcFile, String destFile) throws IOException {
        if (getExtension(srcFile).isEmpty() && !Files.exists(Path.of(srcFile)))
            srcFile = changeExtension(srcFile, ".mus");
        srcFile = Path.of(srcFile).toAbsolutePath().toString();

        this.srcFile = srcFile;
        Compiler compiler = new Compiler();
        compiler.init();

        //compiler.setCompileSwitch("IDE");
        //compiler.setCompileSwitch("SkipPoint=R19:C30");

        File srcIo = new File(srcFile);
        String srcStem = srcIo.getName();
        int dotIdx = srcStem.lastIndexOf('.');
        if (dotIdx >= 0) srcStem = srcStem.substring(0, dotIdx);
        String destFileName = new File(srcIo.getParentFile(), srcStem + ".o").getAbsolutePath();
        if (destFile != null) {
            destFileName = destFile;
        }

        if (!Files.exists(Path.of(srcFile))) {
            logger.log(Level.ERROR, rb.getString("E0601").formatted(srcFile));
            return;
        }

        boolean isSuccess = false;
        try (
                InputStream sourceMML = Files.newInputStream(Path.of(srcFile));
                ByteArrayOutputStream destCompiledBin = new ByteArrayOutputStream()
        ) {

            if (isSeli) {
                // T.B.D: Serialize Mode
                logger.log(Level.INFO, "Serialize Mode");
                MmlDatum[] mmlData = compiler.compile(sourceMML, this::appendFileReaderCallback);
                if (mmlData != null) {
                    try (InputStream fos = Files.newInputStream(Path.of(destFileName + ".seli"))) {
                        Serdes.Util.serialize(mmlData, fos);
                    }
                } else {
                    if (isTest) throw new IllegalStateException("compile error");
                }
            } else
                isSuccess = compiler.compile(sourceMML, destCompiledBin, this::appendFileReaderCallback);

            if (isSuccess) {
                destCompiledBin.flush();
                byte[] destbuf = destCompiledBin.toByteArray();
                Files.write(Path.of(destFileName), destbuf);
            } else
                throw new IllegalStateException("compile error");
        }
    }

    private InputStream appendFileReaderCallback(String arg) {

        Path fn = Path.of(srcFile).getParent().resolve(arg);

        if (!Files.exists(fn)) {
logger.log(Level.INFO, "file not found: " + fn);
            return null;
        }

        InputStream strm;
        try {
            strm = Files.newInputStream(fn);
        } catch (java.io.IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            strm = null;
        }

        return strm;
    }

    /**
     * Parses command line arguments for options.
     *
     * @param args Command line arguments.
     * @return The index of the first non-option argument (the source file).
     */
    private int analyzeOption(String[] args) {
        int i = 0;
        while (i < args.length && args[i] != null && !args[i].isEmpty() && args[i].charAt(0) == '-') {
            String op = args[i].substring(1).toUpperCase();

            if (op.equals("S")) {
                // Request output via serialization
                isSeli = true;
                break;
            }

            i++;
        }

        return i;
    }
}