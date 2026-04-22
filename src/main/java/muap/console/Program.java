package muap.console;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ResourceBundle;

import dotnet4j.io.BufferedStream;
import dotnet4j.io.File;
import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileShare;
import dotnet4j.io.FileStream;
import dotnet4j.io.IOException;
import dotnet4j.io.MemoryStream;
import dotnet4j.io.Path;
import dotnet4j.io.Stream;
import muap.compiler.Compiler;
import musicDriverInterface.MmlDatum;
import vavi.util.serdes.Serdes;


/**
 * Main entry point for the muap console application.
 */
public class Program {

    private static final Logger logger = System.getLogger(Program.class.getName());

    private static final ResourceBundle rb = ResourceBundle.getBundle("muap/message");

    private static String srcFile;
    private static boolean isSeli = false;
    public static boolean isTest = false;

    public static void main(String[] args) throws Exception {

        logger.log(Level.INFO, "Hello, muap!");

        int fnIndex = analyzeOption(args);

        if (args.length < 1 + fnIndex) {
            logger.log(Level.INFO, rb.getString("I0600"));
            return;
        }

        try {
            compile(args[fnIndex], (args.length > fnIndex + 1 ? args[fnIndex + 1] : null));

        } catch (IOException ex) {
            logger.log(Level.ERROR, ex.getMessage(), ex);
            if (isTest) throw ex;
        }
    }

    /**
     * Orchestrates the compilation process.
     *
     * @param srcFile  Path to the source MML file.
     * @param destFile Optional path for the output binary.
     */
    private static void compile(String srcFile, String destFile) throws java.io.IOException {
        if (Path.getExtension(srcFile).isEmpty() && !File.exists(srcFile))
            srcFile = "%s.mus".formatted(Path.getFileNameWithoutExtension(srcFile));

        // Use java.io.File to resolve the source path against the current
        // working directory. dotnet4j's Path uses Windows-style separators,
        // so Path.combine() produces malformed UNC-style paths on Unix.
        srcFile = new java.io.File(srcFile).getAbsolutePath();

        Program.srcFile = srcFile;
        Compiler compiler = new Compiler();
        compiler.init();

        //compiler.setCompileSwitch("IDE");
        //compiler.setCompileSwitch("SkipPoint=R19:C30");

        java.io.File srcIo = new java.io.File(srcFile);
        String srcStem = srcIo.getName();
        int dotIdx = srcStem.lastIndexOf('.');
        if (dotIdx >= 0) srcStem = srcStem.substring(0, dotIdx);
        String destFileName = new java.io.File(srcIo.getParentFile(), srcStem + ".o").getAbsolutePath();
        if (destFile != null) {
            destFileName = destFile;
        }

        if (!File.exists(srcFile)) {
            logger.log(Level.ERROR, rb.getString("E0601").formatted(srcFile));
            return;
        }

        boolean isSuccess = false;
        try (
                FileStream sourceMML = new FileStream(srcFile, FileMode.Open);
                MemoryStream destCompiledBin = new MemoryStream();
                BufferedStream bufferedDestStream = new BufferedStream(destCompiledBin)
        ) {

            if (isSeli) {
                // T.B.D: Serialize Mode
                logger.log(Level.INFO, "Serialize Mode");
                MmlDatum[] mmlData = compiler.compile(sourceMML, Program::appendFileReaderCallback);
                if (mmlData != null && destFileName != null) {
                    try (FileStream fos = new FileStream(destFileName + ".seli", FileMode.Create)) {
                        Serdes.Util.serialize(mmlData, fos);
                    }
                } else {
                    if (isTest) throw new IllegalStateException("compile error");
                }
            } else
                isSuccess = compiler.compile(sourceMML, bufferedDestStream, Program::appendFileReaderCallback);

            if (isSuccess) {
                bufferedDestStream.flush();
                byte[] destbuf = destCompiledBin.toArray();
                File.writeAllBytes(destFileName, destbuf);
            } else
                throw new IllegalStateException("compile error");
        }
    }

    private static Stream appendFileReaderCallback(String arg) {

        String fn = Path.combine(Path.getDirectoryName(srcFile), arg);

        if (!File.exists(fn)) return null;

        FileStream strm;
        try {
            strm = new FileStream(fn, FileMode.Open, FileAccess.Read, FileShare.Read);
        } catch (IOException e) {
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
    private static int analyzeOption(String[] args) {
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