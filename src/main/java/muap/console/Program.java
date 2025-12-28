package muap.console;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ResourceBundle;

import dotnet4j.io.FileMode;
import dotnet4j.io.FileStream;
import groovy.lang.Tuple;
import muap.compiler.Compiler;
import musicDriverInterface.MmlDatum;


/**
 * Main entry point for the muapDotNET console application.
 */
public class Program {

    private static final Logger logger = System.getLogger(Program.class.getName());

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    private static String srcFile;
    private static boolean isSeli = false;

    public static void main(String[] args) {

        logger.log(Level.INFO, "Hello, muapDotNET!");

        int fnIndex = analyzeOption(args);

        if (args.length < 1 + fnIndex) {
            logger.log(Level.INFO, rb.getString("I0600"));
            return;
        }

        try {
            // Note: System.Text.Encoding.RegisterProvider equivalent is handled
            // by ensuring the JVM supports the required charsets (like Shift-JIS).

            compile(args[fnIndex], (args.length > fnIndex + 1 ? args[fnIndex + 1] : null));

        } catch (Exception ex) {
            logger.log(Level.ERROR, ex.getMessage());
            // Get stack trace as string for logging
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            logger.log(Level.ERROR, sw.toString());
        }
    }

    /**
     * Orchestrates the compilation process.
     *
     * @param srcFile  Path to the source MML file.
     * @param destFile Optional path for the output binary.
     */
    private static void compile(String srcFile, String destFile) {
        try {
            File file = new File(srcFile);
            if (!file.exists()) {
                logger.log(Level.ERROR, "File not found [%s]".formatted(srcFile));
                return;
            }

            // Implementation of the compilation logic based on compiler.java
            Compiler compiler = new Compiler();
            compiler.init();

            // Set source filename in compiler work context
            compiler.setCompileSwitch(new Tuple<>("SOURCEFILENAME", srcFile));

            try (FileStream fis = new FileStream(srcFile, FileMode.Create)) {

                MmlDatum[] results = compiler.compile(fis, (name) -> {
                    return new FileStream(name, FileMode.Create);
                });

                if (results != null && destFile != null) {
                    try (FileStream fos = new FileStream(destFile, FileMode.Create)) {
                        for (MmlDatum md : results) {
                            if (md == null) {
                                fos.writeByte((byte) 0);
                            } else {
                                fos.writeByte((byte) md.dat);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.log(Level.ERROR, "Compilation failed: " + ex.getMessage());
        }
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
            }

            i++;
        }

        return i;
    }
}
