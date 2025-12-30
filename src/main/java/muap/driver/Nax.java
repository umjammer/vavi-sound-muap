package muap.driver;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dotnet4j.util.compat.Tuple;
import muap.common.X86Register;
import musicDriverInterface.MmlDatum;


//
//	YM2608+3438 Music Integrated Driver NAX3 version 6.32
//	copyright(C)1987,1989-1995 by Packen Software[jan.5.1996]
//
public class Nax {

    private static final Logger logger = System.getLogger(Nax.class.getName());

    public static class NAXException extends Exception {

        public NAXException(String message) {
            super(message);
        }
    }

    private Work work;
    private final Map<Object, Object> envVars;
    public Pc98 pc98;
    public Ems ems;
    public X86Register reg;
    private final String arg;
    public Play4 play4;
    public Charset myEnc;
    private byte[] filebuf;
    public String objPath;

    // For self-modification detection
    private int port11 = 0x188;
    /** OPNA portB */
    private int port31 = 0x18c;
    /** OPNA portB */
    private int port32 = 0x18c;
    /** OPNA portB */
    private int port34 = 0x18c;
    /** Self-modifying mov wpr cyon,0ffb1h ; inc cl → mov cl,0ffh */
    private int cyon = 0xffb1;
    /** PLAY4 IMR operation */
    private int pt1 = 0x0a;
    /** PLAY4 IMR operation */
    private int pt2 = 0x0a;
    /** PLAY4 timer processing (whether to send to slave as well) */
    public int jmp1 = 0x0;
    /** PLAY4 IMR operation */
    private int setand = 0xef;
    /** 16kHz switch */
    private int intm3 = 0x0;
    /** 16kHz switch (if enabled, rewrite to NOPx2) */
    private int intm4 = 0x0;
    private int segad2 = 0;

    // From MUAP.INC
    public static int MAXPCM = 100;
    public static int MAXBUF = 18;
    public static int FIFO_SIZE = 128;

    // Address reference
    private int setnew_extpcm2 = 0;
    private byte[] toneBuffFromOutside;
    private int[] pcmtbl = new int[] {
            0x0100, 0x1010, 0x8010, 0x6000,
            0xc001, 0x0002, 0x0003, 0xff04, 0xff05,
            0xff0c, 0xff0d, 0xffff
    };
    public List<Tuple<Byte, Object>> functionList = new ArrayList<>();
    private Consumer<Object>[] jmptbl;

    public byte comlength;
    public String lyric = "";

    public Nax(Work work, X86Register regs, Map envVars, Pc98 pc98, Ems ems, String arg, byte[] toneBuffFromOutside, int[] labelPtr, String objPath) {
        myEnc = Charset.forName("Windows-31J");

        this.work = work;
        this.envVars = envVars;
        this.pc98 = pc98;
        this.ems = ems;
        this.arg = arg;
        this.reg = regs;
        this.objPath = objPath;
        this.play4 = new Play4(this, work, labelPtr);
        this.toneBuffFromOutside = toneBuffFromOutside;
        work.fifoBuf = new byte[FIFO_SIZE * MAXBUF * 2];
        for (int i = 0; i < FIFO_SIZE * MAXBUF * 2; i++) work.fifoBuf[i] = (byte) 0x80;
        work.int0bEnt = this::Int0bEntry;

        initParaJump();
        initJmpTbl();

        initCallMenu();
        inst();
    }

    public void Int08Entry() {
        play4.int08ent();
    }

    public void TimerEntry() {
        play4.timer_entry();
    }

    public void Int0bEntry() {
        play4.int0bent();
    }

    /** $108 Function vector number */
    private int funcoff = 0x180;
    private int funcseg = 0x182;
    /** $114 save interrupt vector(timer A) */
    private int tboff = 0x50;
    /** $116 */
    private int tbseg = 0x52;
    public byte[] m_mode = new byte[] {
            0x00,       // $118 b1 = SSGPCM
            0x00,       //      b2 = -V
            0x00,       //      b7 = PCM 1MB
            0x20        //      b0 = WSS, b1 = Alternative YM2608, b2 = ADPCM
                        //      b3 = Use EMS, b4 = 86PCM, b7 = Main memory PCM
    };

    /** $11c object buffer segment */
    public int[] _object = new int[] {0, 0, 0};
    /** Kuma: Performance data goes here */
    public MmlDatum[][] objBuf = new MmlDatum[3][];
    /** $122 tone data buffer segment */
    public int tone = 0;
    /** Kuma: Tone storage buffer */
    public byte[] toneBuff = null;
    /** $124 86/wss pcm segment */
    private int extseg = 0;
    /** $126 86/wss pcm length(*16) */
    private int extlen = 0x1000;
    /** $128 object buffer length */
    private int[] bufleno = new int[] {0, 0, 0};
    /** $12e object data length */
    private int obj_len = 0;
//    /** $132 Offset of the performance work */
//    point1 dw ofs:data_buff;
//    /** $134 Address of lyric data */
//    point2 dw ofs:comdata;
    /** $136 Segment of PCM storage buffer */
    public int pcmseg = 0;
    /** $138 */
    private int pcmlen = 0;
    /** Kuma: PCM storage buffer */
    public byte[] pcmBuff = null;
    /** $13a YM2203 recognized count */
    private byte ym2203 = 0;
    /** $13b YM2608 recognized count */
    private byte ym2608 = 0;
    /** $13c YM3438 recognized count */
    private byte ym3438 = 0;
    /** $13e FIFO segment */
    public int fifoseg = 0;
    /** $140 USRPCM used bytes */
    private int usrbyte = 0;
    /** $142 SSGPCM used bytes */
    private int pcmbyte = 0;
    /** $144 User PCM file name segment */
    private int pcmfile = 0;
    private byte[] pcmfileBuf = new byte[(20 + MAXPCM - 50) * 13 + 1];
    /** EMS handle for PCM */
    public int phandle = 0xffff;
    /** For EMS map info storage */
    private byte[] pemsbuf = new byte[32];

    /** Beginning of the file name (including \) // Kuma: Position of filename */
    private int path1 = 0;
    /** Beginning of the parent directory (including \) // Kuma: Position of parent directory */
    private int path2 = 0;
    /** Beginning of the extension (including .) // Kuma: Position of extension */
    private int path3 = 0;
    /** File name length */
    private int flength = 0;
    private byte usrpcm = 0;
    /** User PCM being registered number */
    private byte usrpcmn = 0;
    private int voldata = 256;
    private int deltax = 127;
    /**
     * Note (type is byte)
     */
    private byte[] xdata = new byte[4];
    private byte[] bufbuf = new byte[128];
    /** usrpcm search */
    private String pcm_path = "*.*" + new String(new char[61]).replace('\0', (char) 0);
    /** sub-usrpcm search */
    private String pcm_path1 = "*.*" + new String(new char[61]).replace('\0', (char) 0);
    /** Read buffer for PCM.TBL , TONE.DTA */
    private byte[] cusbuff;
    /** Number of bytes read */
    private static int MAXCUS = 512;

    //
    // Routines used only during installation
    //

    private String mess_2 = """
            
            -Ax   : Do not use EMS for PCM buffer. x is capacity (1~8)*32KB.
            -Bxx  : Specify SSGPCM buffer capacity.
            -Fx   : Fade-out speed setting.
            -I    : Disable external interrupts during performance routine.
            -Lxx  : Specify function vector number.
            -Mx   : Specify DMA channel.
            -Oxx  : Secure performance buffer.
            -P    : Also read PCM.DTA when resident (not possible with -A or without EMS).
            -Q    : Also read SSGPCM.DTA when resident.
            -T    : Do not display error if file is missing.
            -Vxx  : Specify Timer A interrupt vector number (0B, 10~17).
            -Yx,y : Set start addresses for YM2608 and YM3438.
            -2    : Use one YM2203 instead of YM3438.
            -3    : Prohibit use of 86B/WSS-PCM.
            -6    : Specify 16KHz for SSGPCM.
            -8    : Allocate 1MB for 86B/WSS-PCM buffer.
            -(x   : Specify frequency for 86B/WSS-PCM.
            -?    : Display parameter help.
            
            ★ This music driver can be freely incorporated into commercial software without application.
            ※ For development environment, please purchase "Muup 98/iv" from Soft Vendor Takeru (3000 yen).""";

    private String mess_3 = """
            
             NAXⅢ DSP  Version 6.34
             copyright (c)1990-96 by Packen Software.
             for YM2608/3438/2203, YMF288, PC-9801-86PCM, Windows Sound System PCM
             Public copy & use free.
            """;

    private String mess_5b = "Resident process cancelled due to invalid option settings";
    private String mes_e3 = "SSGPCM.DTA exceeded PCM buffer";
    private String mes_e10 = "WSS interrupt to INT0";
    private String mes_e11 = "DMA is";
    private String mess_f1 = "The sound board is not connected to the specified port, so BGM use will be stopped";
    private String mess_g2 = " not found";
    private String mess_p1 = "ADPCM data transfering";
    private String mess_p2 = "Cancelled due to timeout";
    private String mess_p3 = " User Aborted.";
    private String mess_p4 = "Insufficient PCM buffer capacity";
    private String mes_s0 = " Using device(s) = YM";
    private String mes_s4 = " + 2203";
    private String mes_s2 = "2203";
    private String mes_s9 = " + 2608";
    private String mes_s3 = "2608";
    private String mes_s5 = " + 3438";
    private String mes_s6 = " + 86B<17ch>DSP-";
    private String mes_s12 = " + WSS<17ch>DSP-";
    private String mes_s7 = " + ADPCM";
    private String mes_s13 = "PCM";
    private String mes_s8 = "";

    private byte sflag = 0x00;
    private byte sys_flg = 0x00;

    private int extpcmadr = 0; // Extended PCM address
    private byte oldmapadr = 0; // EMS page number
    private byte extmapflg = 0; // b0 = EMS map flag, b1 = usrpcm registration
    private byte pcmcnt1 = 0;
    private int pcmcnt2 = 0;

    private String[] dspdtop = new String[] {"o", "+", "*", "･"};
    private int dspcnt1 = 0;
    private byte dspcnt2 = 0;
    private byte pcm_flg = 0; // -P specification flag
    private String emsName2 = "MUAP_PCM";
    private byte[] sbufbuf = new byte[128];
    private String tone_path = "TONES.DTA";
    private String pcmt_path = "PCM.TBL";
    private String pcmd_path = "PCM.DTA";
    private String ssg1_path = "SSGPCM.DTA";
    private String ssg2_path = "SSGPCM.TBL";
    public byte[] dma_ch_data = new byte[] {
            0x01, 0x01, 0x03, 0x27,
            0x02, 0x05, 0x07, 0x21,
            0x07, 0x00, 0x00, 0x00,
            0x03, 0x0d, 0x0f, 0x25
    };

    public void initCallMenu() {
        //---------------------------------------
        // Set paths based on environment variables
        //---------------------------------------
        if (envVars != null) {
            if (envVars.containsKey("DTA"))
                tone_path = Paths.get(envVars.get("DTA").toString(), tone_path).toString();
            if (envVars.containsKey("PCM")) {
                pcmd_path = Paths.get(envVars.get("PCM").toString(), pcmd_path).toString();
                pcmt_path = Paths.get(envVars.get("PCM").toString(), pcmt_path).toString();
                ssg1_path = Paths.get(envVars.get("PCM").toString(), ssg1_path).toString();
                ssg2_path = Paths.get(envVars.get("PCM").toString(), ssg2_path).toString();
            }
            if (envVars.containsKey("UDP"))
                pcm_path = Paths.get(envVars.get("UDP").toString(), pcm_path).toString();
            if (envVars.containsKey("SUD"))
                pcm_path1 = Paths.get(envVars.get("SUD").toString(), pcm_path1).toString();
        }
    }

    private void dsp_end(Level llv, String msg) throws NAXException {
        logger.log(llv, msg);
        throw new NAXException("Resident processing failure");
    }

    private void err_param() {
        String dx = mess_5b; // Display invalid parameters
        try {
            dsp_end(Level.ERROR, dx);
        } catch (NAXException e) {
            // Error handled inside dsp_end
        }
    }

    private void dsp_help(String arg) throws NAXException {
        // Display parameter help
        logger.log(Level.INFO, mess_2);
        throw new NAXException("Exit without becoming resident");
    }

    private void putasciz(String mes, int sw) {
        if (sw == 0)
            logger.log(Level.INFO, mes + ".");
        else if (sw == 1)
            logger.log(Level.INFO, mes);
    }

    //
    // Parameter check (BX, DX : destruction prohibited)
    // The following programs for residency
    //
    private void inst() {
        logger.log(Level.INFO, mess_3); // Initial message

        check_port(); // Identification of YM2608 port
        check_extend(); // YM3438

        // Kuma: Command line check starts here?

        para3();
    }

    private void para3() {
        reg.setBx((short) 0);
        while (true) {
            if (skip_bl(arg)) break; // separator skip

            int bx = reg.getBx() & 0xffff;
            char al = arg.length() > bx ? arg.charAt(bx) : (char) 0;
            char ah = arg.length() > bx + 1 ? arg.charAt(bx + 1) : (char) 0;
            reg.setBx((short) (bx + 2));

            if (al != '-' && al != '/') { // Separator check
                err_param();
                return;
            }
//paras:
            ah = xsmall(ah);
            int cx = paradta.indexOf(ah); // check data number
            if (cx < 0) {
                err_param(); // Only invalid parameters existed
                return;
            }
            parajmp[cx].accept(arg);
        }

        para0(); // parameter end
    }

    private String paradta = "FLV?YIOPTMB6Q23(A8";
    private Consumer<String>[] parajmp;

    private void initParaJump() {
        parajmp = List.<Consumer<String>>of(
                this::fade_time, this::funvct,
                this::vector, (String a) -> {
                    try {
                        dsp_help(a);
                    } catch (NAXException e) {
                    }
                },
                this::outport, this::disint,
                this::memget, this::pcm_load,
                this::undisp, this::set_dmach,
                this::ssgpcm_buff, this::ssgpcm16,
                this::read_ssgpcm, this::ext_2203,
                this::dis9821pcm, this::pcmfreq,
                this::disems, this::pcm1mb
        ).toArray(Consumer[]::new);
    }

    private void undisp(String x) {
        sflag |= 1;
    }

    private void patherr(String path) {
        if ((sflag & 1) != 0) return;
        logger.log(Level.ERROR, "%s%s".formatted(path, mess_g2));
    }

    private char xsmall(char al) {
        if ((byte) al >= 0x61 && (byte) al <= 0x7a)
            return (char) ((byte) al & 0xdf);
        return al;
    }

    private void memget(String arg) {
        Set_buff(arg); // AL = performance buffer capacity/100h
        reg.ah = reg.al;
        reg.al = 0;
        bufleno[0] = reg.getAx();
    }

    private void ssgpcm_buff(String arg) {
        Set_buff(arg);
        reg.ah = reg.al;
        reg.al = 0;
        pcmlen = reg.getAx();
    }

    private void ssgpcm16(String x) {
        m_mode[0] |= 0x80; // Set flag
        play4.intm1 = (byte) 0x9a; // Change timer frequency
        play4.intm2 = (byte) 0x7d; //
        reg.setAx((short) 0x9090);
        intm3 = reg.al;
        intm4 = reg.getAx();
    }

    private void read_ssgpcm(String arg) {
        short bxbk = reg.getBx();
        reg.setAx((short) pcmlen);
        if (reg.getAx() == 0) pcmlen = 0x8000;
        m_mode[0] |= 2;

        try {
            play4.ssgtable = Files.readAllBytes(Paths.get(ssg2_path));
            logger.log(Level.INFO, "[{0}] File found.", ssg2_path);
        } catch (IOException e) {
            logger.log(Level.ERROR, "File not found. {0}", ssg2_path);
        } finally {
            reg.setBx(bxbk);
        }
    }

    private void vector(String arg) {
        m_mode[1] |= 4; // User setting for Timer A vector
        Set_buff(arg); // AL = vector number
        if ((reg.al & 0xff) != 0xb // Specifying expansion bus INT0?
                || ((reg.al & 0xff) < 0x10 && (reg.al & 0xff) > 0x17) // 10-17
        )
            err_param();
        int00();
    }

    private void int00() {
        reg.ah = 0;
        short ax_bk = reg.getAx();
        reg.setAx((short) ((reg.getAx() & 0xffff) << 2)); // AX = vector address
        tboff = reg.getAx();
        reg.setAx((short) ((reg.getAx() & 0xffff) + 2));
        tbseg = reg.getAx();
        reg.setAx(ax_bk);

        if ((reg.al & 0xff) == 0x0b) {
            pt1 = 2; // Change IMR operation port (for -V0B)
            pt2 = 2; //
            jmp1 = 0x0ceb; // Set to jmp short +12h (ignore slave processing)
            reg.al = 0x13;
        }

        reg.al -= 0x10; // AL = 0-7 (corresponds to 10-17)
        reg.cl = reg.al;
        reg.setAx((short) 0xfe01); // AH = AND data, AL = OR data
        reg.al = reg.rol(reg.al, reg.cl);
        reg.ah = reg.rol(reg.ah, reg.cl);
        setand = reg.ah; // Write directly to PLAY2 code
    }

    private void funvct(String arg) {
        Set_buff(arg); // AL = vector number
        reg.ah = 0;
        reg.setAx((short) ((reg.getAx() & 0xffff) << 2));
        funcoff = reg.getAx();
        reg.setAx((short) ((reg.getAx() & 0xffff) + 2));
        funcseg = reg.getAx();
    }

    private void autovct() {
        short dxbk = reg.getDx();
        short cxbk = reg.getCx();
        short axbk = reg.getAx();

        if ((m_mode[1] & 4) != 0)
            return;

        reg.setCx((short) 0x40);
        reg.setCx((short) 0); // loop getv1 // Kuma: Hey

        reg.setDx((short) port11);
        reg.al = 0xe;
        pc98.outportB(reg.getDx() & 0xffff, reg.al);
        pc98.outportB(0x5f, reg.al); // for H98

        reg.setCx((short) 0x40);
        reg.setCx((short) 0); // loop getv2 // Kuma: Hey 2

        reg.setDx((short) (reg.getDx() + 2));
        reg.al = pc98.inportB(reg.getDx() & 0xffff); // B7 = IRST0 , B6 = IRST1
        reg.al &= 0xc0;
        if (reg.al == 0) reg.al = 0xb; // INT0
        else if (reg.al == (byte) 0x80) reg.al = 0x13; // INT4
        else if (reg.al == (byte) 0xc0) reg.al = 0x14; // INT5
        else if (reg.al == 0x40) reg.al = 0x15; // INT6
        int00(); // Interrupt vector settings

        reg.setAx(axbk);
        reg.setCx(cxbk);
        reg.setDx(dxbk);
    }

    private void outport(String arg) {
        sys_flg |= 4; // -Y specification
        reg.al = (byte) xsmall(arg.charAt(reg.getBx() & 0xffff));

        getax(arg);
        setport1(reg.getAx() & 0xffff); // Store port address
        reg.setAx((short) ((reg.getAx() & 0xffff) + 4));
        setport3(reg.getAx() & 0xffff);

        if (arg.charAt(reg.getBx() & 0xffff) != ',') return;

        reg.setBx((short) (reg.getBx() + 1));
        getax(arg);
        play4.port5 = reg.getAx() & 0xffff; // Write directly to PLAY4 port
        reg.setAx((short) ((reg.getAx() & 0xffff) + 4));
        play4.port7 = reg.getAx() & 0xffff;
    }

    private void setport1(int ax) {
        // Kuma: Note: Original is self-modifying, so here it's just held as variables
        play4.port1 = ax; // Port number storage address
        play4.port11 = ax;
        play4.port12 = ax;
        play4.port18 = ax;
        play4.port19 = ax;
    }

    private void setport3(int ax) {
        // Kuma: Note: Original is self-modifying, so here it's just held as variables
        play4.port3 = ax;
        play4.port31 = ax;
        play4.port32 = ax;
        play4.port33 = ax;
        play4.port34 = ax;
        play4.port35 = ax;
        play4.port36 = ax;
        play4.port37 = ax;
    }

    private void getax(String arg) {
        // Acquired 4-digit hexadecimal
        Set_buff(arg);
        byte dl = reg.al;
        Set_buff(arg);
        reg.ah = dl;
    }

    private void Set_buff(String arg) {
        int bx = reg.getBx() & 0xffff;
        reg.al = arg.length() > bx ? (byte) arg.charAt(bx) : (byte) 0;
        reg.ah = arg.length() > bx + 1 ? (byte) arg.charAt(bx + 1) : (byte) 0;
        reg.setBx((short) (bx + 2));

        if (!Chkparam()) err_param();
        byte a = reg.al;
        reg.al = reg.ah;
        reg.ah = a;
        if (!Chkparam()) err_param();
        reg.ah <<= 4;

        reg.al = (byte) ((reg.al & 0xf) | (reg.ah & 0xf0));
    }

    private boolean Chkparam() {
        if (reg.al >= (byte) '0' && reg.al <= (byte) '9') {
            reg.al -= (byte) '0';
            return true;
        }

        reg.al &= 0xdf;

        if (reg.al >= (byte) 'A' && reg.al <= (byte) 'F') {
            reg.al -= (byte) 'A';
            reg.al += 10;
            return true;
        }

        return false;
    }

    private void fade_time(String arg) {
        int bx = reg.getBx() & 0xffff;
        reg.al = arg.length() > bx ? (byte) arg.charAt(bx) : (byte) 0;
        reg.setBx((short) (bx + 1));
        reg.al -= (byte) '0';
        int alInt = reg.al & 0xff;
        if ((alInt < 0 || alInt > 9) && (alInt < 0x11 || alInt > 0x2a))
            err_param();
        if (alInt >= 0x11 && alInt <= 0x2a)
            reg.al -= 7;

        reg.al++;
        reg.ah = 0;
        reg.setAx((short) ((reg.getAx() & 0xffff) << 3));
        play4.fadesave = reg.getAx() & 0xffff;
    }

    private void dis9821pcm(String x) {
        sys_flg |= 2;
    }

    private void pcm1mb(String x) {
        m_mode[2] |= 0x80;
    }

    private void disems(String arg) {
        m_mode[3] |= 0x80;
        setup_mainpcm();
        int bx = reg.getBx() & 0xffff;
        reg.al = arg.length() > bx ? (byte) arg.charAt(bx) : (byte) 0;
        reg.setBx((short) (bx + 1));

        if ((reg.al & 0xff) < (byte) '1' || (reg.al & 0xff) > (byte) '8') err_param();
        reg.al -= (byte) '1';
        reg.ah = 0;
        reg.al++;
        reg.setAx((short) ((reg.getAx() & 0xffff) << 11));

        extlen = reg.getAx() & 0xffff; // Specify the length of main memory PCM
    }

    private void pcm_load(String x) {
        pcm_flg = 1;
    }

    private void disint(String x) {
        reg.al = (byte) 0x90;
        play4.stiof1 = reg.al; // Disable interrupts during performance
    }

    private boolean check_ems() {
        // Assuming EMS exists
        return true;
    }

    private void ena_9821() {
        short dxbk = reg.getDx();
        short bxbk = reg.getBx();
        short esbk = reg.es;

        if (!chk_sound()) { // Check extended sound source
            m_mode[3] &= 0xee; // Disable 86B/WSS-PCM
            reg.es = esbk;
            reg.setBx(bxbk);
            reg.setDx(dxbk);
            return;
        }

        check_extend(); // YM3438 side again
        if ((m_mode[3] & 0x80) != 0) {
            reg.es = esbk;
            reg.setBx(bxbk);
            reg.setDx(dxbk);
            return;
        }

        if (!check_ems()) { // Check for EMS existence
            m_mode[3] |= 0x80; // Main memory PCM
            setup_mainpcm();
            reg.es = esbk;
            reg.setBx(bxbk);
            reg.setDx(dxbk);
            return;
        }

        if (check_phandle()) { // Check if MUAP_PCM exists
            m_mode[3] |= 0x08; // EMS mapping
            reg.es = esbk;
            reg.setBx(bxbk);
            reg.setDx(dxbk);
            return;
        }

        reg.setBx((short) 32); // Kuma:32 * 16Kbyte/page = 512Kbyte
        if ((m_mode[2] & 0x80) != 0) reg.setBx((short) 64); // 1MB allocation?
        reg.ah = 0x43;
        int[] dx = {reg.getDx() & 0xffff};
        ems.cS4231EMS_AllocMemory.accept(reg.toByteArray(), dx, reg.getBx() & 0xffff); // Allocate 512KB of EMS
        reg.setDx((short) dx[0]);

        if (reg.ah != 0) { // If insufficient, do not support PCM
            m_mode[3] |= 0x80; // Main memory PCM
            setup_mainpcm();
            reg.es = esbk;
            reg.setBx(bxbk);
            reg.setDx(dxbk);
            return;
        }

        phandle = reg.getDx() & 0xffff; // Store handle
        reg.setAx((short) 0x5301);
        reg.di = 0; // emsName2;
        ems.cS4231EMS_SetHandleName.accept(reg.toByteArray(), reg.getDx() & 0xffff, emsName2); // Set handle name

        m_mode[3] |= 0x08; // EMS mapping
        reg.es = esbk;
        reg.setBx(bxbk);
        reg.setDx(dxbk);
    }

    private void setup_mainpcm() {
        short dib = reg.di;
        short axb = reg.getAx();

        reg.di = 0;
        set_naxadrs(play4.naxad1);
        reg.di = 0;
        set_naxadrs(play4.naxad2);
        reg.di = dib;
        reg.setAx(axb);
    }

    private void set_naxadrs(byte[] buf) {
        buf[reg.di] = (byte) 0xe8;
        reg.setAx((short) setnew_extpcm2);
        reg.setAx((short) (reg.getAx() - reg.di));
        reg.setAx((short) (reg.getAx() - 3));

        buf[reg.di + 1] = (byte) reg.getAx();
        buf[reg.di + 2] = (byte) ((reg.getAx() & 0xffff) >> 8);
        reg.setAx((short) 0x3e3e);
        buf[reg.di + 3] = (byte) reg.getAx();
        buf[reg.di + 4] = (byte) ((reg.getAx() & 0xffff) >> 8);
        buf[reg.di + 5] = (byte) reg.getAx();
        buf[reg.di + 6] = (byte) ((reg.getAx() & 0xffff) >> 8);
        buf[reg.di + 7] = (byte) reg.getAx();
        buf[reg.di + 8] = (byte) ((reg.getAx() & 0xffff) >> 8);
    }

    private boolean check_phandle() {
        reg.setDx((short) 0);
        reg.es = reg.cs;

        String[] sbuf = {""};
        do {
            reg.di = 0; // Read handle name
            reg.setAx((short) 0x5300);
            ems.cS4231EMS_GetHandleName.accept(reg.toByteArray(), reg.getDx() & 0xffff, sbuf);
            if (reg.ah != 0) return false;

            reg.setDx((short) (reg.getDx() + 1)); // To the next handle
            if (reg.getDx() == 0) return false;

        } while (!sbuf.equals(emsName2));
        reg.setDx((short) (reg.getDx() - 1));
        phandle = reg.getDx() & 0xffff;
        m_mode[2] &= 0x7f;
        if ((reg.getBx() & 0xffff) == 64) // PCM 1MB?
        {
            m_mode[2] |= 0x80;
        }
        sys_flg |= 0x10; // Registered
        return true;
    }

    private void pcmfreq(String arg) {
        int bx = reg.getBx() & 0xffff;
        reg.al = arg.length() > bx ? (byte) arg.charAt(bx) : (byte) 0;
        reg.setBx((short) (bx + 1));
        reg.al = (byte) xsmall((char) reg.al);

        if ((reg.al & 0xff) < (byte) 'A' || (reg.al & 0xff) > (byte) 'P') err_param();

        reg.setAx((short) ((reg.al & 0xff) - 'A'));

        play4.freq1 = freq86b[reg.getAx() & 0xffff];
        play4.freq3 = freqwss[reg.getAx() & 0xffff];
        play4.freq2 = freqtbl[reg.getAx() & 0xffff];
        pc98.OutportC4231_Freq2(play4.freq2);
    }

    // PCM frequency table
    private byte[] freq86b = new byte[] {
            0b000, 0b000, 0b001, 0b001,
            0b001, 0b010, 0b010, 0b010,
            0b011, 0b011, 0b100, 0b100,
            0b101, 0b101, 0b110, 0b110
    };
    private byte[] freqwss = new byte[] {
            0b1100, 0b1011, 0b1001, 0b1101,
            0b0110, 0b0100, 0b0111, 0b0101,
            0b0010, 0b0010, 0b0011, 0b1110,
            0b0010, 0b0000, 0b1111, 0b0001
    };
    private int[] freqtbl = new int[] {
            7078, 6503, 5574, 4877,
            4719, 4043, 3251, 2787,
            2439, 2359, 1626, 1416,
            1220, 1180, 976, 813
    };

    private boolean chk_sound() {
        reg.setDx((short) 0xa460);
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0b1111_1101;
        reg.al |= 1;
        pc98.outportB(reg.getDx() & 0xffff, reg.al);
        reg.al &= 0b1111_0000;
        int alInt = reg.al & 0xff;
        if (alInt == 0x10           // PC-98GS
                || alInt == 0x20    // PC-9801-73(0188)
                || alInt == 0x40) { // PC-9801-86(0188)
            reg.setAx((short) 0x188);
            ex_sound_set();
        } else if (alInt == 0x30    // PC-9801-73(0288)
                || alInt == 0x50) { // PC-9801-86(0288)
            reg.setAx((short) 0x288);
            ex_sound_set();
        } else if (alInt == 0x60    // PC-9821Np
                || alInt == 0x70    // PC-9821X*
                || alInt == 0x80) { // PC-9821C*
            ex_wss_exist();
        } else {
            if (!check_srn()) return false; // Check SRN-F PCM sound source permission
            ex_wss_exist();
        }
        return true;
    }

    private void ex_sound_set() {
        m_mode[3] |= 0x10; // 86B-PCM provisional activation
        if ((sys_flg & 4) != 0)
            return;
        setport1(reg.getAx() & 0xffff);
        reg.setAx((short) ((reg.getAx() & 0xffff) + 4));
        setport3(reg.getAx() & 0xffff);
    }

    private void ex_wss_exist() {
        m_mode[3] |= 0x01; // WSS-PCM provisional activation
        if (play4.freq2 != 2439) {
            reg.setAx((short) 0x188);
            ex_sound_set();
            return;
        }

        play4.freq2 = 2360; // Only if frequency is not set
        reg.setAx((short) 0x188);
        ex_sound_set();
    }

    private boolean check_srn() {
        reg.setDx((short) 0x51e1);
        reg.setCx((short) 8);

        boolean fnd = false;
        for (; reg.getCx() > 0; reg.setCx((short) (reg.getCx() - 1))) {
            reg.al = pc98.inportB(reg.getDx() & 0xffff);
            if ((reg.al & 0xff) == 0xc2) {
                fnd = true;
                break;
            }
            reg.setDx((short) (reg.getDx() + 2)); // Search port address
        }
        if (!fnd) return false; // Keyword C2 was missing

//portchk1:
        int dx = reg.getDx() & 0xffff;
        dx &= 0xf;
        reg.di = (short) dx; // Address where C2 was found, lower 4 bits odd
        dx--;
        reg.setSi((short) dx); // Address where C2 was found, lower 4 bits even

        reg.setDx((short) (0x57e0 + (reg.di & 0xffff)));
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0xbf; // Initialize SRN-F
        pc98.outportB(reg.getDx() & 0xffff, reg.al);

        reg.setDx((short) (0x56e0 + (reg.di & 0xffff)));
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al |= 0x51;
        pc98.outportB(reg.getDx() & 0xffff, reg.al);

        reg.setDx((short) (0x57e0 + (reg.di & 0xffff)));
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al |= 0x40; // a460 ID = 71h
        pc98.outportB(reg.getDx() & 0xffff, reg.al);

        reg.setDx((short) (0x5be0 + (reg.di & 0xffff)));
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al |= 0x06;
        pc98.outportB(reg.getDx() & 0xffff, reg.al);

        reg.setDx((short) (0x51e0 + (reg.getSi() & 0xffff)));
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0xfc; // b0 = PCM permit?, b1 = FM sound permit
        pc98.outportB(reg.getDx() & 0xffff, reg.al);

        return true;
    }

    private void set_dmach(String arg) {
        int bx = reg.getBx() & 0xffff;
        reg.al = arg.length() > bx ? (byte) arg.charAt(bx) : (byte) 0;
        reg.setBx((short) (bx + 1));

        if ((reg.al & 0xff) < (byte) '0') err_param();
        reg.al -= (byte) '0';
        if ((reg.al & 0xff) < 2 || (reg.al & 0xff) > 3) err_param();
        reg.ah = reg.al;
        reg.setDx((short) 0xf40);
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0xf8; // Set CS4231 DMA
        reg.al |= reg.ah;
        pc98.outportB(reg.getDx() & 0xffff, reg.al);
    }

    private void setup_int_dma() {
        reg.setDx((short) 0x0f40);
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0b1100_0111;
        reg.al |= 0b0000_1000; // Set CS4231 interrupt to INT0
        pc98.outportB(reg.getDx() & 0xffff, reg.al);
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0b0011_1000;
        if (((reg.al & 0xff) - 0b0000_1000) != 0) {
            // Set to INT0?
            cs4231_error();
            return;
        }

        reg.setDx((short) 0x0f40);
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0b0000_0111;
        reg.dl = 0;
        if (((reg.al & 0xff) - 1) == 0) {
            dmachset1();
            return;
        }

        reg.dl = 1;
        if (((reg.al & 0xff) - 2) == 0) {
            dmachset1();
            return;
        }
        reg.dl = 3;
        if (((reg.al & 0xff) - 3) == 0) {
            dmachset1();
            return;
        }

        reg.setDx((short) 0x0f40); // DMA channel undefined
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0b1111_1000; // Forcibly specify DMA#3
        reg.al |= 0b0000_0011;
        pc98.outportB(reg.getDx() & 0xffff, reg.al);
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        reg.al &= 0b0000_0111;
        if (((reg.al & 0xff) - 0b0000_0011) != 0) {
            dma_error();
            return;
        }
        reg.dl = 3;
        dmachset1();
        return;
    }

    private void dmachset1() {
        play4.dma_chan = reg.dl;
        reg.bh = 0;
        reg.bl = reg.dl;
        reg.setBx((short) ((reg.getBx() & 0xffff) << 2));
        reg.setBx((short) (reg.getBx() & 0xffff)); // ofs:dma_ch_data
        reg.ah = 0;
        reg.al = dma_ch_data[(reg.getBx() & 0xffff) + 1];
        play4.dma_adr = reg.getAx() & 0xffff;
        reg.al = dma_ch_data[(reg.getBx() & 0xffff) + 2];
        play4.dma_count = reg.getAx() & 0xffff;
        reg.al = dma_ch_data[(reg.getBx() & 0xffff) + 3];
        play4.dma_bank = reg.getAx() & 0xffff;
    }

    private void cs4231_error() {
        reg.setDx((short) 0); // ofs:mes_e10
        putasciz(mes_e10, 0);
        m_mode[3] &= 0xee; // WSS-PCM prohibited
    }

    private void dma_error() {
        reg.setDx((short) 0); // ofs:mes_e11
        putasciz(mes_e11, 0);
        m_mode[3] &= 0xee; // WSS-PCM prohibited
    }

    private void init_86pcm() {
        reg.setAx((short) 0x3e3e);
        play4.sign1 = reg.getAx() & 0xffff; // Change PCM routine for 86-PCM
        play4.sign1_2 = reg.getAx() & 0xffff;
        play4.sign1_4 = reg.al;
        play4.sign2 = reg.getAx() & 0xffff;
        reg.setAx((short) 0);
        play4.sign3_1 = reg.getAx() & 0xffff;
        play4.sign4_1 = reg.getAx() & 0xffff;
    }

    private void para0() {
        // Omit processing for calculating resident range (result in dx)

        //
        // Processing after buffer address specification
        //

        short dxbk = reg.getDx();

        if ((sys_flg & 2) == 0) ena_9821(); // Check PC9821PCM
        if ((sys_flg & 0x20) != 0) check_adpcm(); // Check extended sound function
        if (play4.check_86pcm()) {
            if ((m_mode[3] & 0x80) != 0) {
                // Main memory PCM
                reg.setDx(dxbk);
                extseg = reg.getDx() & 0xffff;
                reg.setDx((short) ((reg.getDx() & 0xffff) + extlen));
                dxbk = reg.getDx();
            } else {
                if (!play4.check_wsspcm())
                    init_86pcm(); // Change PCM routine for 86-PCM
                else
                    setup_int_dma(); // Initial setting for DMA interrupt
            }
        }

        chkfm(); // Check extended sound function
        wait_port(); // Forced weight setting
        autovct(); // Automatic assignment of Timer A interrupt number
        set_pcmtable(); // Store PCM management table
        reg.setDx(dxbk); // Final segment value
        if (!play4.check_86pcm()) {
            fifoseg = reg.getDx() & 0xffff; // FIFO segment
        }

        check_fopen();
        load_pcm(); // Load PCM data
        set_vct(); // Setting routines for each interrupt vector
        xtone(); // Transfer SSGPCM.DTA
    }

    private void wait_port() {
        reg.setAx((short) 0);
        play4.hadr11 = reg.al & 0xffff;
        reg.al = (byte) 0x90;
        play4.hadr7 = reg.al & 0xffff;
        play4.hadr8 = reg.al & 0xffff;
        play4.hadr9 = reg.al & 0xffff;
        play4.hadr10 = reg.al & 0xffff;
    }

    private void check_fopen() {
        reg.setDx((short) 0); // ofs:tone_path
        chksns(tone_path);
        if (reg.carry) patherr(tone_path);

        if ((m_mode[0] & 2) == 0) return;

        reg.setDx((short) 0); // ofs:ssg1_path
        chksns(ssg1_path);
        if (reg.carry) patherr(ssg1_path);

        reg.setBx(reg.getAx()); // Set fileHandle
        reg.setCx((short) 0);
        reg.setDx((short) 0);
        byte[] buf = new byte[1];
        if (Files.exists(Paths.get(ssg1_path))) {
            try {
                buf = Files.readAllBytes(Paths.get(ssg1_path));
            } catch (IOException e) {
            }
        } else logger.log(Level.ERROR, "File not found. {0}", ssg1_path);

        reg.setDx((short) (buf.length >> 16));
        if (reg.getDx() != 0 || buf.length >= pcmlen) {
            reg.setDx((short) 0); // ofs:mes_e3 ; PCM buffer overflowed
            putasciz(mes_e3, 0);
        }
    }

    private void ext_2203(String x) {
        play4.outdata4_ = (byte) 0xc3;
        cyon = 0xffb1; // inc cl → mov cl,0ffh
    }

    private void check_port() {
        int dx = 0x88;
        boolean fnd = false;
        for (int cx = 4; cx >= 0; cx--) {
            if (fnd = get_port(dx)) break; // Identification of YM2608 port
            dx += 0x100;
        }
        if (!fnd) return;

        int ax = dx;
        setport1(ax);
        ax += 4;
        setport3(ax);
        sys_flg |= 0x20; // YM2608 port exists
    }

    private boolean get_port(int dx) {
        byte al = (byte) 0xff;
        pc98.outportB(dx, al);
        play4.check_busy(dx);
        dx += 2;
        al = pc98.inportB(dx);
        if (al == 1) return true; // Identification of YM2608 port
        return false;
    }

    private void check_extend() {
        int dx = 0x788;
        for (int cx = 8; cx >= 0; cx--) {
            int f = get_3438(dx);
            if (f == 1) // YM3438 identified
            {
                play4.port5 = dx;
                play4.port7 = (dx + 4);
                return;
            }

            if (f == 0) {
                dx -= 0x100;
                continue;
            }

            if (play4.port1 != dx) {
                if (get_port(dx)) // YM2608 identified
                {
                    m_mode[3] |= 2;
                    play4.port5 = dx;
                    play4.port7 = (dx + 4);
                    return;
                }

                ext_2203(""); // Specify extended 2203
                play4.port5 = dx;
                play4.port7 = (dx + 4);
                return;
            }

            dx -= 0x100;
        }
    }

    private int get_3438(int dx) {
        byte al = pc98.inportB(dx);
        if (al == 0x00) return 0;

        al = 6;
        pc98.outportB(dx, al);
        wait_fm();
        dx += 2;
        al = 0x15;
        pc98.outportB(dx, al);
        wait_fm();
        al = pc98.inportB(dx);
        if (al == 0x15) return 2;
        return 1;
    }

    private void wait_fm() {
    }

    private void chkfm() {
        short axbk = reg.getAx();
        short dxbk = reg.getDx();

        reg.setDx((short) play4.port1);
        reg.al = pc98.inportB(reg.getDx() & 0xffff);
        boolean cf = (reg.al & 0x80) != 0;
        reg.al = (byte) ((reg.al & 0xff) << 1);
        if (!cf) {
            if ((sflag & 1) != 0)
                putasciz(mess_f1, 0);
            reg.al = (byte) 0xc3;
            play4.outdata1_ = reg.al;
            play4.outdata2_ = reg.al;
            play4.outdata3_ = reg.al;
            play4.outdata4_ = reg.al;
            reg.setDx(dxbk);
            reg.setAx(axbk);
            return;
        }

        String msg;
        msg = mes_s0;

        if (!get_port(reg.getDx() & 0xffff)) {
            msg += mes_s2;
            ym2203++;

            reg.al = (byte) 0xc3;
            play4.outdata2_ = reg.al;
            play4.outdata3_ = reg.al;
            play4.outdata4_ = reg.al;
            reg.setDx(dxbk);
            reg.setAx(axbk);

            logger.log(Level.INFO, msg);
            return;
        }

        msg += mes_s3;
        ym2608++;

        reg.setAx((short) 0x002d);
        play4.outdata1();
        reg.setAx((short) 0x8129);
        play4.outdata1();

        if ((m_mode[3] & 0x02) != 0) {
            reg.setAx((short) 0x002d);
            play4.outdata3();
            reg.setAx((short) 0x8129);
            play4.outdata3();

            msg += mes_s9;
            ym2608++;
        } else {
            reg.setDx((short) play4.port5);
            reg.al = pc98.inportB(reg.getDx() & 0xffff);
            cf = (reg.al & 0x80) != 0;
            reg.al = (byte) ((reg.al & 0xff) << 1);
            if (cf) {
                reg.al = play4.outdata4_;
                if ((reg.al & 0xff) == 0xc3) {
                    msg += mes_s4;
                    ext_2203("");
                    ym2203++;
                } else {
                    reg.setDx((short) (play4.port7 + 1));
                    reg.al = pc98.inportB(reg.getDx() & 0xffff);
                    cf = (reg.al & 0x80) != 0;
                    reg.al = (byte) ((reg.al & 0xff) << 1);
                    if (!cf) {
                        msg += mes_s5;
                        ym3438++;
                    } else {
                        msg += mes_s4;
                        ext_2203("");
                        ym2203++;
                    }
                }
            } else {
                reg.al = (byte) 0xc3;
                play4.outdata3_ = reg.al;
                play4.outdata4_ = reg.al;
            }
        }

        msg += mes_s8;
        if ((play4.outdata2_ & 0xff) != 0xc3) {
            reg.al = m_mode[3];
            if ((reg.al & 0x10) == 0) {
                msg += ((reg.al & 4) == 0) ? mes_s8 : mes_s7;
            } else {
                msg += ((reg.al & 0x1) == 0) ? mes_s6 : mes_s12;
                msg += mes_s13;
            }
        }

        logger.log(Level.INFO, msg);
        reg.setAx(axbk);
        reg.setDx(dxbk);
    }

    private void load_pcm() {
        short axbk = reg.getAx();
        short bxbk = reg.getBx();
        short cxbk = reg.getCx();
        short dxbk = reg.getDx();
        short sibk = reg.getSi();

        if (pcm_flg == 0) return;
        if ((m_mode[3] & 0x80) != 0) return; // Main memory PCM
        if ((m_mode[3] & 0x10) == 0)
            if ((m_mode[3] & 0x04) == 0) return; // ADPCM not ready
        if ((sys_flg & 0x10) != 0) return; // Registered
        reg.al = m_mode[3];
        reg.setDx((short) 0); // ofs:pcmd_path
        chksns(pcmd_path);
        if (reg.carry) {
            patherr(pcmd_path);
            reg.setAx(axbk);
            reg.setDx(dxbk);
            return;
        }

        reg.setBx(reg.getAx());
        String msg = mess_p1;
        reg.setDx((short) 0); // ofs:mess_p1
        boolean ret = play4.check_86pcm();
        if (ret) msg = msg.substring(2);

        pcm_init();

        byte[] buf = null;
        try {
            buf = Files.readAllBytes(Paths.get(pcmd_path));
        } catch (IOException e) {
            logger.log(Level.ERROR, "File not found.{0}", pcmd_path);
        }
        int bufPtr = 0;

        while (true) {
            dspcnt2++;
            dspcnt2 &= 3;
            reg.al = dspcnt2;
            if (dspcnt2 == 0) {
                reg.setDx((short) dspcnt1);
                reg.setDx((short) (reg.getDx() + 1));
                if ((reg.getDx() & 0xffff) == dspdtop.length) {
                    msg += dspdtop[(reg.getDx() & 0xffff) - 1];
                    reg.setDx((short) 0);
                }
                dspcnt1 = reg.getDx() & 0xffff;
            }
            reg.setDx((short) 0); // ofs:cusbuff
            reg.setSi(reg.getDx());
            reg.setCx((short) MAXCUS);

            int n = (buf.length - bufPtr);
            n = n > (reg.getCx() & 0xffff) ? (reg.getCx() & 0xffff) : n;
            if (n == 0) break;
            System.arraycopy(buf, bufPtr, cusbuff, 0, n);
            bufPtr += n;

            reg.setCx((short) n);
            if (play4.check_86pcm()) {
                boolean cf = extpcm_write(cusbuff);
                if (cf) {
                    pcm_finish();
                    putasciz(mess_p4, 0);
                    reg.setAx(axbk);
                    reg.setBx(bxbk);
                    reg.setCx(cxbk);
                    reg.setDx(dxbk);
                    reg.setSi(sibk);
                    return;
                }
            } else {
                do {
                    reg.setAx((short) 0x1810);
                    outdata2pcm();
                    reg.setAx((short) 0x1010);
                    outdata2pcm();
                    reg.ah = cusbuff[reg.getSi() & 0xffff];
                    reg.setSi((short) ((reg.getSi() & 0xffff) + 1));
                    reg.al = 8;
                    outdata2pcm();

                    if (pcm_flg != 2) {
                        short cxbk2 = reg.getCx();
                        reg.setCx((short) 0x200);
                        do {
                            reg.setDx((short) port31);
                            reg.al = pc98.inportB(reg.getDx() & 0xffff);
                            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
                            if ((reg.getCx() & 0xffff) == 0) {
                                reg.setCx(cxbk2);
                                reg.setDx((short) 0); // ofs:mess_p2
                                putasciz(mess_p2, 0);
                                reg.setAx(axbk);
                                reg.setBx(bxbk);
                                reg.setCx(cxbk);
                                reg.setDx(dxbk);
                                reg.setSi(sibk);
                                return;
                            }
                        } while ((reg.al & 8) == 0);
                        reg.setCx(cxbk2);
                        reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
                        if ((reg.getCx() & 0xffff) > 0) continue;
                        break;
                    }

                    short cxbk3 = reg.getCx();
                    reg.setDx((short) port31);
                    play4.check_busy(reg.getDx() & 0xffff);
                    reg.setCx(cxbk3);
                    reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
                } while ((reg.getCx() & 0xffff) > 0);
            }
        }

        logger.log(Level.INFO, msg + dspdtop[dspdtop.length - 1]);
        pcm_finish();

        if (play4.check_86pcm()) {
            reg.setAx(axbk);
            reg.setBx(bxbk);
            reg.setCx(cxbk);
            reg.setDx(dxbk);
            reg.setSi(sibk);
            return;
        }
        if (check_mp23()) {
            reg.setAx(axbk);
            reg.setBx(bxbk);
            reg.setCx(cxbk);
            reg.setDx(dxbk);
            reg.setSi(sibk);
            return;
        }

        reg.setSi((short) 0); // ofs:cusbuff
        reg.setAx((short) ((cusbuff[reg.getSi() & 0xffff] & 0xff) + (cusbuff[(reg.getSi() & 0xffff) + 1] & 0xff) * 0x100));
        if (cusbuff[reg.getSi() & 0xffff] == (byte) 'M'
                && cusbuff[(reg.getSi() & 0xffff) + 1] == (byte) 'P'
                && cusbuff[(reg.getSi() & 0xffff) + 2] == (byte) '2'
                && cusbuff[(reg.getSi() & 0xffff) + 3] == (byte) '3'
        )
            m_mode[3] |= 4;

        reg.setAx(axbk);
        reg.setBx(bxbk);
        reg.setCx(cxbk);
        reg.setDx(dxbk);
        reg.setSi(sibk);
    }

    private void pcm_finish() {
        if (play4.check_86pcm()) {
            remove_extpcm();
            return;
        }

        reg.setAx((short) 0);
        play4.outdata2();
        reg.setAx((short) 0x8010);
        play4.outdata2();
        reg.ah = 0x1c;
        play4.outdata2();
        reg.ah = (byte) 0x80;
        play4.outdata2();
    }

    private boolean extpcm_write(byte[] siBuf) {
        short esbk = reg.es;
        short csbk = reg.cs;
        reg.es = csbk; // Swap for memory write
        extpcm_write_main(siBuf);
        reg.es = esbk;
        reg.setDx((short) 0); // ofs:mess_p4
        return reg.carry; // insufficient buffer
    }

    private boolean check_mp23() {
        cusbuff = pc98.ReadOpnaPCMMemory(port34, 0, 4);
        return true;
    }

    private void check_adpcm() {
        short sibk = reg.getSi();
        short dxbk = reg.getDx();
        short cxbk = reg.getCx();
        short axbk = reg.getAx();

        if (play4.check_86pcm()) {
            reg.setDx((short) port31);
            play4.check_busy(reg.getDx() & 0xffff);
            reg.setAx(axbk);
            reg.setCx(cxbk);
            reg.setDx(dxbk);
            reg.setSi(sibk);
            return;
        }

        if (!check_mp23()) {
            reg.setDx((short) port31);
            play4.check_busy(reg.getDx() & 0xffff);
            reg.setAx(axbk);
            reg.setCx(cxbk);
            reg.setDx(dxbk);
            reg.setSi(sibk);
            return;
        }

        if (cusbuff[0] == 'M' && cusbuff[1] == 'P' && cusbuff[2] == '2' && cusbuff[3] == '3') {
            m_mode[3] |= 4;
            reg.setDx((short) port31);
            play4.check_busy(reg.getDx() & 0xffff);
            reg.setAx(axbk);
            reg.setCx(cxbk);
            reg.setDx(dxbk);
            reg.setSi(sibk);
            return;
        }

        pcm_init();
        reg.setCx((short) 4);
        while ((reg.getCx() & 0xffff) > 0) {
            reg.setAx((short) 0x1810);
            outdata2pcm();
            reg.setAx((short) 0x1010);
            outdata2pcm();
            reg.setAx((short) 0x5a08);
            outdata2pcm();
            adpcm_wait();
            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
        }

        pcm_finish();
        if (!check_mp23()) {
            reg.setDx((short) port31);
            play4.check_busy(reg.getDx() & 0xffff);
            reg.setAx(axbk);
            reg.setCx(cxbk);
            reg.setDx(dxbk);
            reg.setSi(sibk);
            return;
        }
        if (cusbuff[0] == 0x5a) {
            m_mode[3] |= 4;
        }

        reg.setDx((short) port31);
        play4.check_busy(reg.getDx() & 0xffff);

        reg.setAx(axbk);
        reg.setCx(cxbk);
        reg.setDx(dxbk);
        reg.setSi(sibk);
    }

    private void adpcm_wait() {
        short cxbk = reg.getCx();
        reg.setCx((short) 0x200);
        while (true) {
            reg.setDx((short) port32);
            reg.al = pc98.inportB(reg.getDx() & 0xffff);
            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
            if ((short) reg.getCx() >= 0) break;
            if ((reg.al & 0x8) == 0) break;
        }
        reg.setCx(cxbk);
    }

    private void set_pcmtable() {
        short dxbk = reg.getDx();
        short bxbk = reg.getBx();

        if (pcm_flg == 0) return;

        if ((m_mode[3] & 0x80) != 0) {
            return;
        }

        reg.setDx((short) 0);

        if (!Files.exists(Paths.get(pcmt_path))) {
            reg.setDx((short) 0);
            patherr(pcmt_path);
            reg.setDx(dxbk);
            reg.setBx(bxbk);
            return;
        }

        try {
            byte[] bin = Files.readAllBytes(Paths.get(pcmt_path));
            cusbuff = new byte[4000];
            System.arraycopy(bin, 0, cusbuff, 0, bin.length < 4000 ? bin.length : 4000);

            reg.setSi((short) 0);
            reg.di = 0;
            reg.setCx((short) MAXPCM);

            do {
                reg.carry = gettxt(cusbuff);
                if (reg.carry) break;
                gethex();
                int adr = (reg.al & 0xff) << 8;

                reg.carry = gettxt(cusbuff);
                if (reg.carry) break;
                gethex();
                adr |= (reg.al & 0xff);

                adr >>= 1;
                play4.pcmtable[reg.di] = (byte) adr;
                play4.pcmtable[reg.di + 1] = (byte) (adr >> 8);
                reg.di += 2;
                reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
            } while ((reg.getCx() & 0xffff) > 0);

            while ((reg.getCx() & 0xffff) > 0) {
                for (int i = 0; i < 2; i++) {
                    play4.pcmtable[reg.di] = play4.pcmtable[reg.di - 2];
                    reg.di++;
                }
                reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
            }
        } catch (IOException e) {
        }

        reg.setBx(bxbk);
        reg.setDx(dxbk);
    }

    private boolean gettxt(byte[] siBuf) {
        while (true) {
            int si = reg.getSi() & 0xffff;
            reg.setAx((short) ((siBuf[si] & 0xff) + (siBuf[si + 1] & 0xff) * 0x100));
            reg.setSi((short) (si + 1));
            if (reg.al == 0x1a) return true;
            if (reg.ah == 0x1a) return true;

            if (reg.al == (byte) ' ' || reg.al == 9 || reg.al == 13 || reg.al == 10)
                continue;

            if (reg.al != (byte) ';') {
                reg.setSi((short) ((reg.getSi() & 0xffff) + 1));
                return false;
            }

            do {
                reg.al = siBuf[(reg.getSi() & 0xffff)];
                reg.setSi((short) ((reg.getSi() & 0xffff) + 1));
                if (reg.al == 0x1a) return true;
            } while (reg.al != 13);
        }
    }

    private void gethex() {
        reg.al -= (byte) '0';
        if ((reg.al & 0xff) - 10 > 0) reg.al -= 7;
        if ((reg.al & 0xff) - 16 > 0) reg.al -= (byte) ' ';
        byte temp = reg.al;
        reg.al = reg.ah;
        reg.ah = temp;
        reg.al -= (byte) '0';
        if ((reg.al & 0xff) - 10 > 0) reg.al -= 7;
        if ((reg.al & 0xff) - 16 > 0) reg.al -= (byte) ' ';

        reg.ah <<= 4;
        reg.al |= reg.ah;
    }

    private boolean chksns(String path) {
        short dxbk = reg.getDx();
        short bxbk = reg.getBx();
        reg.setBx(reg.getDx());
        reg.dl = (byte) path.charAt(reg.getBx() & 0xffff);
        reg.carry = ((reg.dl & 0xff) < (byte) 'a');
        reg.dl -= (byte) 'a';
        if (!reg.carry) reg.dl += (byte) ' ';
        drv_sense();
        reg.setBx(bxbk);
        reg.setDx(dxbk);
        reg.setAx((short) 0x3d00);
        if (!reg.carry) pc98.Int21_3d(path);

        return !reg.carry;
    }

    private boolean skip_bl(String str) {
        if ((reg.getBx() & 0xffff) >= str.length()) return true;

        while (true) {
            int bx = reg.getBx() & 0xffff;
            if (bx >= str.length()) return true;
            char al = str.charAt(bx);
            reg.setBx((short) (bx + 1));
            if (al == 0x09) continue;
            if (al == ' ') continue;
            if (al == 13) continue;
            reg.setBx((short) ((reg.getBx() & 0xffff) - 1));
            return false;
        }
    }

    private void set_vct() {
        short esbk = reg.es;
        reg.setAx((short) 0);
        reg.es = reg.getAx();
        reg.setAx((short) 0);
        reg.setBx((short) tboff);
        reg.setAx(reg.cs);
        reg.setBx((short) tbseg);

        reg.setAx((short) 0);
        reg.setBx((short) funcoff);
        reg.setAx(reg.cs);
        reg.setBx((short) funcseg);

        if ((m_mode[0] & 2) != 0) {
            reg.setAx((short) 0);
            reg.setAx(reg.cs);
        }
        if ((m_mode[3] & 1) != 0) {
            reg.setAx((short) 0);
            reg.setAx(reg.cs);
        }

        reg.es = esbk;
    }

    private void xtone() {
        short dxbk = reg.getDx();
        if ((m_mode[0] & 2) != 0) {
            reg.setDx((short) 0);
            chksns(ssg1_path);
            if (!reg.carry) {
                reg.setBx(reg.getAx());
                reg.setCx((short) pcmlen);
                reg.ah = 0x3f;
                reg.setDx((short) 0);
                short dsbk = reg.ds;
                reg.ds = (short) pcmseg;
                byte[] buf = null;
                try {
                    buf = Files.readAllBytes(Paths.get(ssg1_path));
                } catch (IOException e) {
                    logger.log(Level.ERROR, "File not found.{0}", ssg1_path);
                }
                pcmBuff = new byte[pcmlen];
                System.arraycopy(buf, 0, pcmBuff, 0, buf.length > pcmlen ? pcmlen : buf.length);
                reg.ds = dsbk;
            }
        }

        reg.setDx((short) 0);
        chksns(tone_path);
        if (!reg.carry) {
            reg.setBx(reg.getAx());
            reg.setCx((short) 6400);
            reg.ah = 0x3f;
            reg.setDx((short) 0);
            short dsbk = reg.ds;
            reg.ds = (short) tone;
            byte[] buf = null;
            if (toneBuffFromOutside != null) {
                buf = toneBuffFromOutside;
            } else {
                try {
                    buf = Files.readAllBytes(Paths.get(tone_path));
                } catch (IOException e) {
                    logger.log(Level.ERROR, "File not found.{0}", tone_path);
                }
            }
            toneBuff = new byte[6400];
            System.arraycopy(buf, 0, toneBuff, 0, buf.length > 6400 ? 6400 : buf.length);
            reg.ds = dsbk;
        }

        reg.setDx(dxbk);
    }

    public void pushems() {
    }

    public void popems() {
    }

    public void save_extpcm(int[] bx) {
        if ((m_mode[3] & 0x80) != 0)
            return;
        bx[0] = ems.cS4231EMS_GetPageMap.get();
    }

    private void setnew_extpcm() {
        reg.carry = false;
        if ((m_mode[3] & 0x80) != 0) {
            emsmain1();
            return;
        }
        short sibk = reg.getSi();
        short dxbk = reg.getDx();
        short bxbk = reg.getBx();
        short axbk = reg.getAx();
        reg.setDx((short) phandle);
        reg.setAx((short) 0x4400);
        ems.cS4231EMS_Map.accept(reg.al & 0xff, reg.toByteArray(), reg.getBx() & 0xffff, reg.getDx() & 0xffff);
        reg.setAx(axbk);
        reg.setBx(bxbk);
        reg.setDx(dxbk);
        reg.setSi(sibk);
    }

    private boolean emsmain1() {
        short bxbk = reg.getBx();
        reg.setBx((short) ((reg.getBx() & 0xffff) << 10));
        if ((reg.getBx() & 0xffff) > extlen) {
            reg.carry = true;
            reg.setBx(bxbk);
            return true;
        }
        reg.setBx((short) ((reg.getBx() & 0xffff) + extseg));
        segad2 = reg.getBx() & 0xffff;
        reg.carry = false;
        reg.setBx(bxbk);
        return false;
    }

    public void remove_extpcm() {
        if ((m_mode[3] & 0x80) != 0) return;
    }

    public void function(byte ah, Object obj) {
        functionList.add(new Tuple<>(ah, obj));
    }

    public void functionF(Tuple<Byte, Object> tp) {
        jmptbl[tp.getItem1() & 0xff].accept(tp.getItem2());
    }

    private void initJmpTbl() {
        jmptbl = new Consumer[] {
                this::play0, this::play1,
                play4::music_stop, null,
                play4::music_again, null, null, null,
                null, null, this::play10
        };
    }

    private void play0(Object x) {
        byte al = 0;

        if (play4.outdata1_ == (byte) 0xc3) al = 0;
        else if (play4.outdata2_ == (byte) 0xc3) al = 1;
        else if (play4.outdata3_ == (byte) 0xc3) al = 2;
        else if (play4.outdata4_ == (byte) 0xc3) al = 3;
        else al = 4;

        if (play4.check_86pcm()) al += 10;
        else if ((m_mode[3] & 4) != 0) al += 5;

        reg.setAx((short) (al & 0xff));
    }

    private void play1(Object obj) {
        objBuf[0] = (MmlDatum[]) obj;
        if (bufleno[0] == 0) {
            _object[0] = reg.getDx() & 0xffff;
        }
        play4.music_start();
        work.setStatus(1);
    }

    private void play10(Object o) {
        short bxbk = reg.getBx();
        short cxbk = reg.getCx();
        short dxbk = reg.getDx();
        MmlDatum[] oo = null;
        if (o != null && o instanceof MmlDatum[]) {
            oo = (MmlDatum[]) o;
        } else {
            reg.al = 2;
            return;
        }
        if (bufleno[0] == 0) {
            reg.al = 1;
            return;
        }
        play4.music_stop(null);

        objBuf[0] = oo;
        obj_len = (short) oo.length;

        pcm_check();
        if (reg.carry) {
            reg.al = 4;
            reg.setDx(dxbk);
            reg.setCx(cxbk);
            reg.setBx(bxbk);
            return;
        }

        play4.music_start();

        reg.al = 0;
        reg.setDx(dxbk);
        reg.setCx(cxbk);
        reg.setBx(bxbk);
        return;
    }

    private void pcm_check() {
        reg.push(reg.getAx());
        reg.push(reg.getBx());
        reg.push(reg.getCx());
        reg.push(reg.getSi());
        reg.push(reg.di);
        reg.push(reg.ds);
        reg.push(reg.es);

        try {
            reg.ds = reg.cs;

            usrpcm = 0;
            reg.es = 0;
            int di = 0x24;
            di = (objBuf[0][di].dat & 0xff) + (objBuf[0][di + 1].dat & 0xff) * 0x100;
            reg.al = (byte) objBuf[0][di].dat;
            if (reg.al != (byte) 0x88) {
                reg.setAx((short) 0);
                pcmbyte = reg.getAx() & 0xffff;
                reg.setAx((short) ((play4.pcmtable[50 * 2] & 0xff) + (play4.pcmtable[50 * 2 + 1] & 0xff) * 0x100));
                usrbyte = reg.getAx() & 0xffff;
                reg.carry = false;
                pcmload15();
                return;
            }

            usrpcm |= 1;
            di++;
            int bx = 0;
            byte[] bxbuf = play4.ssgtable;
            int si = 0;
            reg.setCx((short) (20 + MAXPCM - 50));
            reg.ah = 0;

            do {
                usrpcmn = reg.ah;

                reg.push(reg.getAx());
                reg.push(reg.getCx());
                reg.push((short) si);
                reg.push((short) di);

                boolean skipPcmload2 = false;
                int continuePcmload5 = 0;
                while (true) {
                    reg.al = (byte) objBuf[0][di].dat;

                    int alInt = reg.al & 0xff;
                    if ((alInt >= 20 && alInt < 50) || alInt > MAXPCM - 1) break;

                    if (reg.al == reg.ah) {
                        if (!pcmload3(bxbuf, di, si, bx)) {
                            skipPcmload2 = true;
                            break;
                        }

                        continuePcmload5 = pcmload6(bxbuf, di, si, bx);
                        if (continuePcmload5 != 0) break;
                        reg.carry = false;
                        return;
                    }
                    di += 14;
                }

                if (continuePcmload5 != 2) {
                    if (continuePcmload5 == 0) {
                        if (!skipPcmload2) {
                            reg.setCx((short) 13);
                            do {
                                pcmfileBuf[si] = 0;
                                si++;
                                reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
                            } while ((reg.getCx() & 0xffff) > 0);

                            reg.setAx((short) ((bxbuf[bx] & 0xff) + (bxbuf[bx + 1] & 0xff) * 0x100));
                            bxbuf[bx + 2] = reg.al;
                            bxbuf[bx + 3] = reg.ah;
                        }
                    }
                }
                bx += 2;

                di = reg.pop();
                si = reg.pop();
                reg.setCx(reg.pop());
                reg.setAx(reg.pop());

                si += 13;
                reg.ah = (byte) ((reg.ah & 0xff) + 1);
                if ((reg.ah & 0xff) == 20) {
                    reg.ah = 50;
                    bx = 50 * 2;
                    bxbuf = play4.pcmtable;
                }

                reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
            } while ((reg.getCx() & 0xffff) > 0);

            reg.carry = false;
        } catch (Exception e) {

        }

        pcmload15();
        return;
    }

    private void pcmload15() {
        reg.es = reg.pop();
        reg.ds = reg.pop();
        reg.di = reg.pop();
        reg.setSi(reg.pop());
        reg.setCx(reg.pop());
        reg.setBx(reg.pop());
        reg.setAx(reg.pop());
    }

    private boolean pcmload3(byte[] bxbuf, int di, int si, int bx) {
        if ((reg.al & 0xff) < 20) usrpcm |= 2;

        di++;

        reg.setCx((short) 11);
        reg.zero = true;
        for (int i = 0; i < 11; i++) {
            if (objBuf[0][di + i].dat == pcmfileBuf[si + i]) continue;
            reg.zero = false;
            break;
        }
        if (!reg.zero) return true;

        reg.setAx((short) ((pcmfileBuf[si + 11] & 0xff) + (pcmfileBuf[si + 12] & 0xff) * 0x100));
        reg.setAx((short) ((reg.getAx() & 0xffff) ^ ((objBuf[0][di + 11].dat & 0xff) + (objBuf[0][di + 12].dat & 0xff) * 0x100)));

        if ((reg.getAx() & 0xffff) != ((bxbuf[bx] & 0xff) + (bxbuf[bx + 1] & 0xff) * 0x100)) {
            return true;
        }
        di = (bxbuf[bx + 2] & 0xff) + (bxbuf[bx + 3] & 0xff) * 0x100;

        if (bxbuf != play4.ssgtable) {
            pcmbyte = di;
        }

        return false;
    }

    private int pcmload6(byte[] bxbuf, int di, int si, int bx) {
        int sibk = si;
        int dsbk = reg.ds;
        reg.setCx((short) 11);
        reg.ds = (short) pcmfile;
        do {
            reg.al = (byte) objBuf[0][di].dat;
            pcmfileBuf[si] = reg.al;
            si++;
            di++;
            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
        } while ((reg.getCx() & 0xffff) > 0);
        reg.ds = (short) dsbk;
        si = sibk;
        reg.setAx((short) ((objBuf[0][di].dat & 0xff) + (objBuf[0][di + 1].dat & 0xff) * 0x100));
        voldata = reg.getAx() & 0xffff;
        reg.setAx((short) ((bxbuf[bx] & 0xff) + (bxbuf[bx + 1] & 0xff) * 0x100));

        reg.pushA();

        reg.push(reg.es);
        pcmtbl[11 / 2] = (pcmtbl[11 / 2] & 0x00ff) + (reg.al & 0xff) * 0x200;
        pcmtbl[13 / 2] = (pcmtbl[13 / 2] & 0x00ff) + (reg.ah & 0xff) * 0x200;
        reg.push(reg.getAx());

        reg.es = reg.cs;
        reg.setBx((short) 0);
        pcm_path1 = "*.*" + new String(new char[61]).replace('\0', (char) 0);
        String[] tmp = {pcm_path1};
        set_usrpcmfile(tmp);
        pcm_path1 = tmp[0];

        reg.setDx(reg.getBx());
        reg.carry = false;

        if (objPath != null && !objPath.isEmpty()) {
            String objPathFn = Paths.get(objPath, pcm_path1).toString();
            if (Files.exists(Paths.get(objPathFn))) {
                try {
                    filebuf = Files.readAllBytes(Paths.get(objPathFn));
                    logger.log(Level.INFO, "[{0}] File found.", objPathFn);
                } catch (IOException e) {
                    reg.carry = true;
                }
            } else reg.carry = true;
        }

        if (reg.carry) {
            reg.carry = false;
            if (!Files.exists(Paths.get(pcm_path1))) {
                logger.log(Level.ERROR, "File not found. {0}", pcm_path1);
                reg.carry = true;
            } else {
                try {
                    filebuf = Files.readAllBytes(Paths.get(pcm_path1));
                    logger.log(Level.INFO, "[{0}] File found.", pcm_path1);
                } catch (IOException e) {
                    logger.log(Level.ERROR, "File not found. {0}", pcm_path1);
                    reg.carry = true;
                }
            }
        }
        if (reg.carry) {
            if (!fdd_notready()) {
                pcmload9s();
                return 0;
            }
        } else {
            reg.di = reg.pop();
            reg.es = reg.pop();
        }

        return subdir1(bxbuf, bx);
    }

    private boolean fdd_notready() {
        reg.setBx((short) 0);
        pcm_path = "*.*" + new String(new char[61]).replace('\0', (char) 0);
        String[] tmp = {pcm_path};
        set_usrpcmfile(tmp);
        pcm_path = tmp[0];
        reg.di = reg.pop();
        reg.es = reg.pop();

        reg.carry = false;
        if (!Files.exists(Paths.get(pcm_path))) reg.carry = true;
        else {
            try {
                filebuf = Files.readAllBytes(Paths.get(pcm_path));
                logger.log(Level.INFO, "[{0}] File found.", pcm_path);
            } catch (IOException e) {
                logger.log(Level.ERROR, "File not found.{0}", pcm_path);
                reg.carry = true;
            }
        }
        if (!reg.carry) {
            return true;
        }

        reg.push(reg.es);
        reg.push(reg.di);
        reg.push(reg.getSi());
        reg.push(reg.getBx());
        reg.es = reg.cs;

        String fn = Paths.get(pcm_path).getFileName().toString();
        String parentPath = Paths.get(pcm_path).getParent() != null ? Paths.get(pcm_path).getParent().toString() : ".";

        String testPath = searchFile(parentPath, fn);
        reg.carry = false;
        if (testPath == null) reg.carry = true;
        else {
            try {
                filebuf = Files.readAllBytes(Paths.get(testPath));
                logger.log(Level.INFO, "[{0}] File found.", testPath);
            } catch (IOException e) {
                logger.log(Level.ERROR, "File not found.{0}", testPath);
                reg.carry = true;
            }
        }
        if (reg.carry) {
            reg.setBx(reg.pop());
            reg.setSi(reg.pop());
            reg.di = reg.pop();
            reg.es = reg.pop();
            reg.dl = 25;
            return false;
        }

        reg.setBx(reg.pop());
        reg.setSi(reg.pop());
        reg.di = reg.pop();
        reg.es = reg.pop();

        return true;
    }

    private String searchFile(String path, String fn) {
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null) return null;

        for (File file : listOfFiles) {
            if (file.isDirectory()) {
                String ans = searchFile(file.getAbsolutePath(), fn);
                if (ans != null) return ans;
            } else if (file.getName().equalsIgnoreCase(fn)) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    private int subdir1(byte[] bxbuf, int bx) {
        reg.setBx(reg.getAx());
        if ((usrpcmn & 0xff) > 20) {
            pcm_init();
        }

        deltax = 127;
        reg.setAx((short) 0);
        xdata[0] = reg.al;
        xdata[1] = reg.ah;
        xdata[2] = reg.al;
        xdata[3] = reg.ah;

        int fileptr = 0;
        do {
            reg.setCx((short) bufleno[0]);
            reg.setDx((short) 0);
            if ((reg.getCx() & 0xffff) < 0x8000 || (reg.getDx() & 0xffff) >= 0x6000) {
                reg.setSi(reg.getDx());
                reg.zero = (reg.getCx() == reg.getDx());
                reg.setCx((short) ((reg.getCx() & 0xffff) - (reg.getDx() & 0xffff)));
                if (reg.zero) {
                    pcmload16();
                    return 0;
                }
            } else {
                reg.setDx((short) 0x0);
                reg.setSi(reg.getDx());
                reg.setCx((short) 0x1000);
            }

            short dsbk = reg.ds;
            reg.ds = reg.es;

            cusbuff = new byte[reg.getCx() & 0xffff];
            reg.setAx((short) Math.min((int) (reg.getCx() & 0xffff), filebuf.length - fileptr));
            System.arraycopy(filebuf, fileptr, cusbuff, 0, reg.getAx() & 0xffff);
            fileptr += (reg.getAx() & 0xffff);

            reg.ds = dsbk;
            if (reg.getAx() == 0) {
                pcmload13(bxbuf, bx);
                return 2;
            }
            reg.setCx(reg.getAx());
            if ((usrpcmn & 0xff) > 20) {
                int n = load_userpcm();
                if (n == 0) return 0;
                else if (n == 14) continue;
                break;
            }
            reg.dl = 32;
            if (pcmBuff == null) pcmBuff = new byte[pcmlen];
            xferpcm(cusbuff, pcmBuff);
            if (reg.carry) {
                pcmload9();
                return 0;
            }
        } while (!reg.carry);

        return 0;
    }

    private void set_usrpcmfile(String[] bxpath) {
        short bxbk = reg.getBx();
        short sibk = reg.getSi();
        reg.setDx(reg.getBx());
        getpath_main(bxpath[0]);
        reg.di = 0;
        reg.setCx((short) 8);
        short dsbk = reg.ds;
        reg.ds = (short) pcmfile;

        byte[] buf = new byte[255];
        do {
            buf[reg.di] = pcmfileBuf[reg.getSi() & 0xffff];
            reg.di++;
            reg.setSi((short) ((reg.getSi() & 0xffff) + 1));
            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
        } while ((reg.getCx() & 0xffff) > 0);

        reg.al = (byte) '.';
        buf[reg.di] = reg.al;
        reg.di++;

        reg.setCx((short) 3);
        do {
            buf[reg.di] = pcmfileBuf[reg.getSi() & 0xffff];
            reg.di++;
            reg.setSi((short) ((reg.getSi() & 0xffff) + 1));
            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
        } while ((reg.getCx() & 0xffff) > 0);

        reg.al = 0;
        buf[reg.di] = reg.al;
        reg.di++;

        String text = new String(buf, myEnc);
        String sub = bxpath[0].substring(0, path1 + (path1 == 0 ? 0 : 1)) + text;
        int limit = (reg.di & 0xffff) + path1 - (path1 == 0 ? 1 : 0);
        if (sub.length() > limit) sub = sub.substring(0, limit);
        bxpath[0] = sub.replace(" ", "").replace("\0", "");
        reg.ds = dsbk;
        reg.setSi(sibk);
        reg.setBx(bxbk);
    }

    private void xferpcm(byte[] siBuf, byte[] diBuf) {
        reg.setAx((short) 0);
        if ((reg.getAx() & 0xffff) == pcmlen) {
            reg.carry = true;
            return;
        }
        reg.setAx(reg.di);
        long ans = (long) (reg.getAx() & 0xffff) + (long) (reg.getCx() & 0xffff);
        reg.setAx((short) ans);
        if (ans > 0xffff) {
            reg.carry = true;
            return;
        }
        if (intm4 != 0) {
            ans = ans + (long) (reg.getCx() & 0xffff);
            reg.setAx((short) ans);
            if (ans > 0xffff) {
                reg.carry = true;
                return;
            }
        }
        if ((reg.getAx() & 0xffff) >= pcmlen) {
            reg.carry = true;
            return;
        }

        reg.push(reg.getDx());
        reg.push(reg.getCx());
        reg.push(reg.getBx());
        reg.push(reg.getAx());

        do {
            reg.push(reg.getCx());
            int si = reg.getSi() & 0xffff;
            siBuf[si] = reg.ror(siBuf[si], 4);
            calc_nextxy(siBuf, diBuf);
            if (intm3 == 0) {
                reg.di = (short) ((reg.di & 0xffff) - 1);
            }
            si = reg.getSi() & 0xffff;
            siBuf[si] = reg.ror(siBuf[si], 4);
            calc_nextxy(siBuf, diBuf);
            reg.setSi((short) (si + 1));
            reg.setCx(reg.pop());
            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
        } while ((reg.getCx() & 0xffff) > 0);

        reg.setAx(reg.pop());
        reg.setBx(reg.pop());
        reg.setCx(reg.pop());
        reg.setDx(reg.pop());

        reg.carry = false;
    }

    private int load_userpcm() {
        reg.setAx((short) ((reg.getAx() & 0xffff) >> 3));
        reg.carry = ((reg.di & 0xffff) + (reg.getAx() & 0xffff)) > 0xffff;
        reg.di = (short) ((reg.di & 0xffff) + (reg.getAx() & 0xffff));
        usrbyte = reg.di & 0xffff;
        reg.dl = 27;

        if (reg.carry
                || (((m_mode[2] & 0x80) == 0) && ((reg.di & 0xffff) > 0x8000))
        ) {
            pcmload9();
            return 0;
        }

        if (play4.check_86pcm()) {
            return extpcm_write1();
        }

        return pcmload10();
    }

    private int pcmload10() {
        do {
            reg.setAx((short) 0x1810);
            outdata2pcm();
            reg.setAx((short) 0x1010);
            outdata2pcm();
            reg.ah = cusbuff[reg.getSi() & 0xffff];
            reg.setSi((short) ((reg.getSi() & 0xffff) + 1));
            reg.al = 8;
            outdata2pcm();

            reg.push(reg.getCx());
            reg.setCx((short) 0x200);
            while (true) {
                reg.setDx((short) play4.port37);
                reg.al = pc98.inportB(reg.getDx() & 0xffff);
                reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
                if ((reg.getCx() & 0xffff) != 0) {
                    reg.test(reg.al, (byte) 8);
                    if (reg.zero) continue;
                }
                break;
            }
            reg.setCx(reg.pop());
            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
        } while ((reg.getCx() & 0xffff) != 0);

        return 14;
    }

    private void pcmload13(byte[] bxbuf, int bx) {
        reg.ah = 0x3e;
        reg.setAx((short) 0);
        play4.outdata2();
        reg.setAx((short) 0x8010);
        play4.outdata2();
        if (play4.check_86pcm()) {
            remove_extpcm();
        }
        extmapflg &= 0xfe;
        reg.setAx(reg.pop());
        reg.push(reg.di);
        reg.popA();
        reg.setAx((short) ((bxbuf[bx] & 0xff) + (bxbuf[bx + 1] & 0xff) * 0x100));
        bxbuf[bx + 2] = (byte) reg.di;
        bxbuf[bx + 3] = (byte) ((reg.di & 0xffff) >> 8);
        reg.setAx((short) ((reg.getAx() & 0xffff) ^ voldata));
        reg.push(reg.ds);
        reg.ds = (short) pcmfile;
        int si = reg.getSi() & 0xffff;
        pcmfileBuf[si + 11] = reg.al;
        pcmfileBuf[si + 12] = reg.ah;
        reg.ds = reg.pop();
        if (bxbuf == play4.ssgtable) {
            pcmbyte = reg.di & 0xffff;
        }
    }

    private void pcmload16() {
        reg.dl = 26;
        pcmload9();
    }

    private void pcmload9() {
        pcmload9s();
    }

    private void pcmload9s() {
        if ((extmapflg & 1) != 0) {
            remove_extpcm();
        }
        extmapflg &= 0xfe;

        reg.pop();
        reg.pop();
        reg.pop();
        reg.pop();
        reg.pop();
        reg.pop();
        reg.pop();
        reg.pop();
        reg.pop();
        reg.pop();
        reg.pop();

        reg.carry = true;
        pcmload15();
    }

    private int extpcm_write1() {
        reg.push(reg.di);
        reg.push(reg.getCx());
        reg.push(reg.getBx());
        reg.push(reg.getAx());

        reg.di = (short) ((reg.di & 0xffff) - (reg.getAx() & 0xffff));
        reg.setAx(reg.di);
        pcmcnt2 = reg.getAx() & 0xffff;
        pcmcnt1 = 0;

        play4.calc_extpcmadr();

        extpcmadr = reg.di & 0xffff;
        oldmapadr = reg.cl;

        reg.setBx(reg.getCx());
        setnew_extpcm();

        reg.setAx(reg.pop());
        reg.setBx(reg.pop());
        reg.setCx(reg.pop());
        reg.di = reg.pop();

        if (reg.carry) {
            pcmload16();
            return 0;
        }
        extmapflg |= 2;
        extpcm_write_main(cusbuff);
        extmapflg &= 0xfd;
        if (reg.carry) {
            pcmload16();
            return 0;
        }

        return 14;
    }

    private void getpath_main(String bxpath) {
        char dxChar;
        int bx = reg.getBx() & 0xffff;
        do {
            dxChar = bxpath.charAt(bx);
            if (dxChar == '\\') {
                path2 = path1;
                path1 = bx;
            }
            if (dxChar == '.') {
                path3 = bx;
            }
            bx++;
        } while (dxChar != '\0' && bx < bxpath.length());
        reg.setBx((short) bx);
        flength = (reg.getBx() & 0xffff) - path1;
    }

    private void extpcm_write_main(byte[] siBuf) {
        short dibk = reg.di;
        short dxbk = reg.getDx();
        short bxbk = reg.getBx();
        reg.setAx((short) pcmseg);
        short axbk = reg.getAx();

        reg.setAx((short) 0xc000);
        pcmseg = reg.getAx() & 0xffff;
        reg.di = (short) extpcmadr;

        do {
            short cxbk = reg.getCx();
            if ((reg.di & 0xffff) >= 16384) {
                reg.di = 0;
                reg.setAx((short) 0);
                reg.al = oldmapadr;
                reg.al++;
                oldmapadr = reg.al;
                reg.setBx(reg.getAx());
                setnew_extpcm();
                if (reg.carry) {
                    reg.setCx(cxbk);
                    reg.setAx(axbk);
                    pcmseg = reg.getAx() & 0xffff;
                    reg.setBx(bxbk);
                    reg.setDx(dxbk);
                    reg.di = dibk;
                    return;
                }
            }
            byte[] diBuf = ems.cS4231EMS_GetCurrentMapBuf.get();
            int si = reg.getSi() & 0xffff;
            byte a = siBuf[si];
            siBuf[si] = (byte) (((a & 0xf0) >> 4) | ((a & 0x0f) << 4));
            calc_nextxy(siBuf, diBuf);
            a = siBuf[si];
            siBuf[si] = (byte) (((a & 0xf0) >> 4) | ((a & 0x0f) << 4));
            calc_nextxy(siBuf, diBuf);
            reg.al = pcmcnt1;

            if ((reg.al & 0xff) >= 4) {
                reg.al = 0;
                short axbk2 = reg.getAx();
                short dxbk2 = reg.getDx();
                short sibk2 = reg.getSi();
                reg.setAx((short) pcmcnt2);
                reg.al++;
                pcmcnt2 = reg.getAx() & 0xffff;
                if ((extmapflg & 2) == 0) {
                    reg.setSi((short) 0);

                    do {
                        int currentSi = reg.getSi() & 0xffff;
                        reg.setDx((short) ((play4.pcmtable[currentSi] & 0xff) + (play4.pcmtable[currentSi + 1] & 0xff) * 0x100));
                        reg.setDx((short) ((reg.getDx() & 0xffff) << 1));
                        if ((reg.getAx() & 0xffff) == (reg.getDx() & 0xffff)) {
                            deltax = 127;
                            reg.setAx((short) 0);
                            xdata[0] = reg.al;
                            xdata[1] = reg.ah;
                            xdata[2] = reg.al;
                            xdata[3] = reg.ah;
                            break;
                        } else if ((reg.getAx() & 0xffff) - (reg.getDx() & 0xffff) < 0) break;
                        reg.setSi((short) (currentSi + 2));
                    } while ((reg.getSi() & 0xffff) - (MAXPCM * 2) <= 0);
                }
                reg.setSi(sibk2);
                reg.setDx(dxbk2);
                reg.setAx(axbk2);
            }
            pcmcnt1 = reg.al;
            reg.setSi((short) ((reg.getSi() & 0xffff) + 1));
            reg.setCx((short) ((reg.getCx() & 0xffff) - 1));
        } while ((reg.getCx() & 0xffff) > 0);

        extpcmadr = reg.di & 0xffff;
        reg.carry = false;

        reg.setAx(axbk);
        pcmseg = reg.getAx() & 0xffff;
        reg.setBx(bxbk);
        reg.setDx(dxbk);
        reg.di = dibk;
    }

    private void calc_nextxy(byte[] siBuf, byte[] diBuf) {
        reg.al = siBuf[reg.getSi() & 0xffff];
        reg.setDx((short) deltax);

        calcgx();

        int xd = ((xdata[0] & 0xff)
                + ((xdata[1] << 8) & 0xff)
                + ((xdata[2] << 16) & 0xff)
                + ((xdata[3] << 24) & 0xff));
        xd += (reg.getAx() & 0xffff) + ((reg.getDx() & 0xffff) << 16);
        xd = xd > 32767 ? 32767 : (xd < -32768 ? -32768 : xd);
        xdata[0] = (byte) xd;
        xdata[1] = (byte) (xd >> 8);
        xdata[2] = (byte) (xd >> 16);
        xdata[3] = (byte) (xd >> 24);

        if ((xdata[3] & 0x80) != 0) {
            if (((xdata[2] & 0xff) + (xdata[3] & 0xff) * 0x100 != 0xffff) ||
                    (((xdata[0] & 0xff) + (xdata[1] & 0xff) * 0x100) < 0x8000)) {
                xdata[0] = 0x00;
                xdata[1] = (byte) 0x80;
                xdata[2] = (byte) 0xff;
                xdata[3] = (byte) 0xff;
            }
        } else if (((xdata[2] & 0xff) + (xdata[3] & 0xff) * 0x100 != 0) ||
                (((xdata[0] & 0xff) + (xdata[1] & 0xff) * 0x100) > 0x7fff)) {
            xdata[0] = (byte) 0xff;
            xdata[1] = 0x7f;
            xdata[2] = 0x00;
            xdata[3] = 0x00;
        }

        reg.setAx((short) ((xdata[0] & 0xff) + ((xdata[1] & 0xff) << 8)));
        reg.setDx((short) voldata);
        int ans = (short) (reg.getAx() & 0xffff) * (short) (reg.getDx() & 0xffff);
        reg.setAx((short) (ans >> 16));
        reg.setDx(reg.getAx());
        reg.setAx((short) ((short) (reg.getAx() & 0xffff) >> 2));
        if ((reg.ah & 0x80) != 0) {
            if ((reg.getAx() & 0xffff) < 0xff80)
                reg.al = (byte) 0x80;
        } else if ((reg.getAx() & 0xffff) > 127) {
            reg.al = 127;
        }

        diBuf[reg.di & 0xffff] = reg.al;
        reg.di = (short) ((reg.di & 0xffff) + 1);

        reg.al = siBuf[reg.getSi() & 0xffff];
        reg.setDx((short) deltax);

        calcdn();

        deltax = reg.getAx() & 0xffff;
    }

    private void calcdn() {
        reg.al &= 7;
        reg.cl = reg.al;

        int clInt = reg.cl & 0xff;
        if (clInt < 4) reg.setAx((short) 57);
        else if (clInt == 4) reg.setAx((short) 77);
        else if (clInt < 6) reg.setAx((short) 102);
        else if (clInt == 6) reg.setAx((short) 128);
        else reg.setAx((short) 153);

        long ans = (long) (reg.getAx() & 0xffff) * (reg.getDx() & 0xffff);
        reg.setAx((short) ans);
        reg.setDx((short) (ans >> 16));

        reg.setCx((short) 64);
        long dans = ans / (reg.getCx() & 0xffff);
        reg.setAx((short) dans);
        reg.setDx((short) (ans % (reg.getCx() & 0xffff)));
        if ((reg.getAx() & 0xffff) < 127) reg.setAx((short) 127);
        else if ((reg.getAx() & 0xffff) >= 24576) reg.setAx((short) 24576);
    }

    private void calcgx() {
        if ((reg.al & 8) == 0) {
            calcgx_main();
            return;
        }
        calcgx_main();
        reg.setCx((short) 0);
        reg.setBx((short) 0);
        reg.carry = (reg.getBx() & 0xffff) < (reg.getAx() & 0xffff);
        reg.setBx((short) ((reg.getBx() & 0xffff) - (reg.getAx() & 0xffff)));
        reg.setCx((short) ((reg.getCx() & 0xffff) - ((reg.getDx() & 0xffff) + (reg.carry ? 1 : 0))));
        reg.setAx(reg.getBx());
        reg.setDx(reg.getCx());
    }

    private void calcgx_main() {
        reg.setAx((short) (reg.getAx() & 7));
        reg.setAx((short) ((reg.getAx() & 0xffff) << 1));
        reg.setAx((short) ((reg.getAx() & 0xffff) + 1));
        long ans = (long) (reg.getAx() & 0xffff) * (reg.getDx() & 0xffff);
        reg.setAx((short) ans);
        reg.setDx((short) (ans >> 16));

        int n = (int) (((reg.getDx() & 0xffff) & 7) << 13);
        reg.setDx((short) ((reg.getDx() & 0xffff) >> 3));
        reg.setAx((short) (((reg.getAx() & 0xffff) >> 3) | n));
    }

    private boolean pcm_init() {
        if (play4.check_86pcm()) {
            extpcm_init();
            return false;
        }

        int si = 0;
        short axbk = reg.getAx();
        while (true) {
            reg.setAx((short) pcmtbl[si]);
            si++;
            if ((reg.getAx() & 0xffff) == 0xffff) break;
            play4.outdata2();
        }
        reg.setAx(axbk);

        return true;
    }

    private void extpcm_init() {
        short bxbk = reg.getBx();
        voldata = 256;
        reg.setBx((short) 0);

        int[] bx = new int[] {reg.getBx() & 0xffff};
        save_extpcm(bx);
        reg.setBx((short) bx[0]);
        setnew_extpcm();
        extmapflg |= 1;
        reg.setAx((short) 0);
        extpcmadr = reg.getAx() & 0xffff;
        oldmapadr = reg.al;
        pcmcnt1 = reg.al;
        reg.setAx((short) 0);
        pcmcnt2 = reg.getAx() & 0xffff;
        reg.setBx(bxbk);
    }

    private void drv_sense() {
        reg.carry = false;
    }

    private void outdata2pcm() {
        short dxbk = reg.getDx();
        short axbk = reg.getAx();

        reg.setDx((short) 0x18c);
        pc98.outportB(reg.getDx() & 0xffff, reg.al);
        reg.setDx((short) ((reg.getDx() & 0xffff) + 2));
        reg.al = reg.ah;
        pc98.outportB(reg.getDx() & 0xffff, reg.al);

        reg.setAx(axbk);
        reg.setDx(dxbk);
    }
}
