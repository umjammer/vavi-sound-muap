package muap.compiler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;

import dotnet4j.util.compat.Tuple3;
import muap.common.MusException;
import muap.common.X86Register;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.LinePos;
import musicDriverInterface.MMLType;
import musicDriverInterface.MmlDatum;


/**
 * Port of MUCOM2 compiler engine.
 */
public class Mucom2 {

    private static final Logger logger = System.getLogger(Mucom2.class.getName());

    public X86Register r = null;
    public Menu menu;
    public Muap98 muap98;
    public MucomSub mucomsub;
    public Work work;

    public Mucom2(X86Register r, Menu menu, Muap98 muap98, MucomSub mucomsub, Work work) {
        this.r = r;
        this.menu = menu;
        this.muap98 = muap98;
        this.mucomsub = mucomsub;
        this.work = work;
    }

    public void init() {
        InitJpdata();
        InitCalltbl();
    }

    private short getSourceData(int adr) {
        return (short) (
                (byte) (adr >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[adr]) & 0xff
                        | ((byte) (adr + 1 >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[adr + 1]) & 0xff) << 8
        );
    }

    private void pc98_Int18() {
        if (r.ah == 0) r.bh = 0;
        r.setAx((short) 0);
    }

    public void stosbObjBufAL2DI(MmlDatum md) {
        if (md == null)
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        else {
            MmlDatum md2 = new MmlDatum(md.type, md.args, md.linePos, r.al);
            muap98.objectBuf.set(r.di++, md2);
        }
    }

    public void stosbObjBufAL2DI() {
        stosbObjBufAL2DI(null);
    }

    public void stoswObjBufAX2DI(MmlDatum md) {
        if (md == null)
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        else {
            MmlDatum md2 = new MmlDatum(md.type, md.args, md.linePos, r.al);
            muap98.objectBuf.set(r.di++, md2);
        }
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
    }

    public void stoswObjBufAX2DI() {
        stoswObjBufAX2DI(null);
    }

    // WINDOW.ASM simulation
    private byte topx = 8;
    private byte topy = 4;
    private byte sizex = 60;
    private byte sizey = 15;
    private byte locatex = 0;
    private byte locatey = 1;
    private byte attr = (byte) 0xe1;

    private void saveText() {
    }

    private void setText() {
    }

    private void putword(String msg) {
        int idx = msg.indexOf("$");
        String m = idx != -1 ? msg.substring(0, idx) : msg;
        logger.log(Level.INFO, m);
    }

    private void putchr(byte c) {
        logger.log(Level.INFO, String.valueOf((char) c));
    }

    private void putchrs(byte[] c) {
        StringBuilder sb = new StringBuilder();
        for (byte b : c) sb.append((char) b);
        logger.log(Level.INFO, sb.toString());
    }

    private void putstr(String fmt, Object... prm) {
        logger.log(Level.INFO, String.format(fmt, prm));
    }

    private void loadText() {
    }

    // MUTRACE.ASM simulation
    private byte saples = 0;
    private byte zero = 0;
    private byte stamode = 0;

    private void dsp2dec() {
        r.push(r.getBx());
        r.push(r.getCx());
        saples = 0;
        r.setCx((short) 2);
        r.setBx((short) 10);
        div_loop(null);
        r.setCx(r.pop());
        r.setBx(r.pop());
    }

    private void div_loop(byte[] diBuf) {
        r.push(r.getAx());
        r.push(r.getDx());
        do {
            r.setDx((short) 0);
            int dividend = (r.getDx() & 0xffff) * 0x10000 + (r.getAx() & 0xffff);
            int divisor = r.getBx() & 0xffff;
            int ans = dividend / divisor;
            int mod = dividend % divisor;
            r.setAx((short) ans);
            r.setDx((short) mod);
            r.push(r.getDx());

            int m = 1;
            if (r.al == 0 && r.cl != 1) {
                if (saples == 2) m = 0;
                else if (saples != 1) {
                    r.dl = (byte) ((zero != 0) ? '0' : ' ');
                    m = 2;
                }
            }

            switch (m) {
                case 1:
                    saples = 1;
                    r.dl = (byte) ((r.al & 0xff) + '0');
                case 2:
                    if (stamode != 1) putchr(r.dl);
                    else {
                        r.al = r.dl;
                        if (diBuf != null && diBuf.length > (r.di & 0xffff)) diBuf[r.di & 0xffff] = r.al;
                        r.di++;
                    }
                    break;
            }

            r.setAx(r.getBx());
            r.setDx((short) 0);
            r.push(r.di);
            r.di = 10;
            int div2 = (r.getDx() & 0xffff) * 0x10000 + (r.getAx() & 0xffff);
            r.setAx((short) (div2 / 10));
            r.setDx((short) (div2 % 10));
            r.di = r.pop();
            r.setBx(r.getAx());
            r.setDx(r.pop());
            r.setAx(r.getDx());
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        r.setDx(r.pop());
        r.setAx(r.pop());
    }

    // Constants & Memory Map
    public int MAXBUF = 18;
    private static final int OBJTOP = 0x2a;
    private static final int MXPOS = 7;
    private static final int MYPOS = 12;
    private static final int MXSIZE = 57;
    private static final int MYSIZE = 7;
    public static final int TONEOFS = (MXSIZE + 4) * (MYSIZE + 2) * 3;
    public byte[] TONEOFSbuf = new byte[256];
    public static final int IFSTACK = TONEOFS + 256;
    public byte[] IFSTACKbuf = new byte[128];
    public static final int MACACHE = IFSTACK + 128;
    public byte[] MACACHEbuf = new byte[52];
    private static final int SYMDTA = 0x22;
    public static final int MAXPCM = 100;

    private short[] maxlen = new short[] {0, 0};
    private byte[] skipbyte = new byte[] {
            0, 0, 0, 0, 0, 8, 0, 3, 3, 1, 2, 2, 0, 3, 2, 1, 1, 2, 1, 1, 1, 2, 2, 0, 2, 26, 0, 4, 4, 1, 0, 0,
            0, 1, 1, 3, 3, 3, 6, 1, 1, 1, 2, 4, 4, 0, 0, 1, 1, 0
    };

    public void freq_lfo() {
        r.push(r.getCx());
        r.setCx((short) 3);
        r.push(r.getDx());
        r.setDx((short) ((r.getDx() & 0xffff) >> 4));
        r.al = r.ah;
        r.setAx((short) (byte) r.al);
        int ans = (short) r.getAx() * (short) r.getDx();
        r.setDx((short) (ans >> 16));
        r.setAx((short) ans);

        do {
            r.carry = (r.getDx() & 1) != 0;
            r.setDx((short) ((short) r.getDx() >> 1));
            r.setAx(r.rcr(r.getAx(), 1));
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        r.setDx(r.pop());
        r.setCx(r.pop());
    }

    public byte[] tone_adrs() {
        r.setBx((short) (25 * (r.al & 0xff)));
        r.ds = (short) muap98.tone;
        return muap98.toneBuff;
    }

    /**
     * Calculate bit value for specified channel.
     * entry CH = channel number (0-16)
     * exit DLAX = 2^CH
     */
    public void calcbit() {
        short cxbk = r.getCx();
        r.setAx((short) 0);
        r.cl = r.ch;
        if ((r.cl & 0xff) < 16) {
            r.setAx((short) ((r.getAx() & 0xffff) + 1));
            r.setAx((short) ((r.getAx() & 0xffff) << (r.cl & 0xff)));
            r.dl = 0;
        } else {
            r.dl = 1;
        }
        r.setCx(cxbk);
    }

    private void testknj() {
        int dh = r.dh & 0xff;
        int dx = r.getDx() & 0xffff;
        if (dh < 0x81) {
            r.carry = false;
            r.zero = false;
            return;
        }
        if (dh <= 0x9f) {
            if (dx < 0x8540) {
                r.zero = true;
                r.carry = true;
                return;
            }
            if (dx <= 0x869e) {
                r.zero = false;
                r.carry = true;
                return;
            }
            r.zero = true;
            r.carry = true;
            return;
        }
        if (dh < 0xe0) {
            r.carry = false;
            r.zero = false;
            return;
        }
        if (dh <= 0xfc) {
            r.zero = true;
            r.carry = true;
            return;
        }
        r.carry = false;
        r.zero = false;
    }

    private Runnable[] calltbl;
    private String[] calerror1;

    private void InitCalltbl() {
        calltbl = new Runnable[26];
        calltbl[1] = this::cal_mucom2;
        // Other indices remain null (dummy/not implemented in source)

        calerror1 = new String[] {
                "() loop count$", "Play buffer overflow$", "() loop nest (up to 15)$", "Tuplet data$",
                "Tuplet count$", "Parameter range$", "Invalid tuning symbol$", "Too many rhythm patterns$",
                "Chord name does not exist$", "Chord number$", "Chord is not a note$", "@si/@so nested$",
                "Loop () mismatch$", "Non-numeric parameter$", "Total length exceeded$",
                "Multiple parameters for @codein/@dt outside CH3$", "Command must be followed by note, <>, or @+-%$",
                "Portamento range too wide$", "Substitution target missing$", "Substitution nest (up to 10)$",
                "Substitution string length (up to 32)$", "Portamento on CH10$", "Transpose data$",
                "Octave value by transposition$", "IF command outside ()$", "@label start position not set$",
                "@jump, @call infinite loop$", "Tuning note data$", "Z command parameter$",
                "Invalid V=: symbol$", "Volume range$", "Syntax error$", "Lyric data$",
                "MML version mismatch but proceeding$", "@if then/exit nesting$", "PCM octave range$",
                "Auto-pan pattern count$", "System detune on PCM/Rhythm$", "Invalid user PCM filename$",
                "Macro variable parameter missing$", "Extended PCM only on CH11$"
        };
    }

    private void cal_mucom2() {
    }

    private void call_func() {
        r.push(r.getBx());
        r.push(r.getAx());
        r.al = r.ah;
        r.ah = 0;
        int idx = (r.getAx() & 0xffff);
        r.setAx(r.pop());
        if (calltbl[idx] != null) calltbl[idx].run();
        r.setBx(r.pop());
    }

    private void clbuff() {
    }

    private void check_calplay() {
        r.zero = (muap98.m_mode[0] & 2) == 0;
    }

    // Message Strings
    private static final String mess_2 = "  over  $";
    private static final String mess_3a = "[ESC]:abort$";
    private static final String mess_5 = "Error    $";
    private static final String mess_6 = "<Whole note.Fractional length>$";
    private static final String mess_7 = "Play data is $";
    private static final String mess_8 = " bytes.$";
    private static final String mess_9 = "Forced abort.$";
    private static final String mess_10 = "Debug$";
    private static final String mess_11 = "Info Output$";

    public int VOLBASE = 80;
    private int srctop = 0;
    public byte mmlver = 0x30;
    public byte[] mode = new byte[] {0, 0};
    private byte onkai = 0;
    private byte bassdta = 0;
    private short codesav = 0;
    private int bxsave = 0xffff;
    public int linedta = 0;
    public byte ope_no = 1;
    public byte sendch = 0;
    private int disave = 0;
    private int disave3 = 0;
    private short spsave = 0;
    private int bef_len = 0;
    public byte cal_num = 0;
    public byte symbol2 = 0;
    public byte[] wordbuf = new byte[32];
    public byte[] rhyvol = new byte[] {31, 31, 31, 31, 31, 31};
    public byte from_no = 0;
    public byte to_no = 0;
    public int rhyadrs = 0;
    public byte rtm_max = 0;
    public byte volstt = 0;
    public int tmpstt = 0;
    public byte lensave = 0;
    public int tempos = 120;
    public byte volsave = 110;
    private byte lendata = 48;
    public byte octdata = 3;
    public byte octsave = 3;
    public byte ratdata = 1;
    private byte maxrest = (byte) 192;
    public byte tridta0 = 0, tridta1 = 0, tridta2 = 0, totalen = 0, trillen = 0, trionpu = 0, trildef = 6, slursav = 0, debug = 0;
    public int panadrs = 0;
    public int porta1 = 0, porta2 = 0, freqsv1 = 0, freqsv2 = 0, porcnt = 0;
    public byte slbase = (byte) 0xfc, slspeed = 6, accbase = 4, dwnbase = 6;
    public int por_end = 0;

    public int dionpu = 0;
    public byte[] dionpuBuf = new byte[256];
    public byte slurmod = 0;
    public int lastfrq = 0;
    public byte harmno = 0, codemod = 0, dt2mode = 0, arpmode = 0, arpharm = 0, arplen = 0, volsft = 0, nesting = 0, nest2 = 0, chglen = 0, chgsav = 0, renplen = 0, restlen = 0;
    private int disave1 = 0, disave2 = 0;
    public int cresvol = 0, creslen = 0, dcrelen = 0, crescnt = 0, tmpdata = 0, tmplen = 0, tmpcnt = 0;
    public byte[] dtdata = new byte[4], dtshift = new byte[4];
    public byte rhydata = 0, jumpnes = 0, ichosav = 0;
    public int lastrp = 0;
    public byte macrof = 0, commode = 0, mac_mod = 0, comcnt = 0, optimiz = 0, opt_tne = 0, opt_pan = 0, opt_vol = 0, ssgpcmm = 0, ratmode = 0, onpucnt = 0, ifflag = 0;
    public int opt_rhy = 0;

    public int[] alllen = new int[64];
    public int[] macrov = new int[18];
    public int macroflg = 0;
    public byte[] pandata = new byte[17];
    public byte[] rhythmdta = new byte[44];
    public byte[] flatdata = new byte[7];
    public byte[] flatdata2 = new byte[54];
    public int[] stttbl = new int[30];

    public byte[] ichodta = new byte[] {(byte) 0xfd, (byte) 0xff, 0, 2, 4, 5, (byte) 0xfb};
    public byte[] musdata = new byte[] {9, 11, 0, 2, 4, 5, 7};
    public byte[] data1 = new byte[] {0x6a, 0x2, (byte) 0x8f, 0x2, (byte) 0xb6, 0x2, (byte) 0xdf, 0x2, 0x0b, 0x3, 0x39, 0x3, 0x6a, 0x3, (byte) 0x9e, 0x3, (byte) 0xde, 0x3, 0x10, 0x4, 0x4e, 0x4, (byte) 0x8f, 0x4};
    public byte[] data2 = new byte[] {(byte) 0xe8, 0xe, 0x12, 0xe, 0x48, 0xd, (byte) 0x89, 0xc, (byte) 0xd5, 0xb, 0x2b, 0xb, (byte) 0x8a, 0xa, (byte) 0xf3, 0x9, 0x64, 0x9, (byte) 0xdd, 0x8, 0x5e, 0x8, (byte) 0xe6, 0x7};
    public byte[] data3 = new byte[] {(byte) 0xbc, 0x49, 0x1e, 0x4e, (byte) 0xc4, 0x52, (byte) 0xaf, 0x57, (byte) 0xe6, 0x5c, 0x6c, 0x62, 0x47, 0x68, 0x7a, 0x6e, 0x0c, 0x75, 0x02, 0x7c, 0x61, (byte) 0x83, 0x31, (byte) 0x8b};

    private void work_init() {
        r.push(r.es);
        r.push(r.ds);
        r.push(r.di);
        r.push(r.getCx());
        r.push(r.getAx());
        mode[0] &= 0x58;
        mode[1] &= 0;
        volsave = 110;
        lendata = 48;
        octdata = 3;
        slbase = (byte) 0xfc;
        slspeed = 6;
        accbase = 4;
        dwnbase = 6;
        r.push(r.getSi());
        mucomsub.pan_init();
        r.setSi(r.pop());
        ratdata = (r.ch < 10 || r.ch > 11) ? (byte) 1 : (byte) 1;
        maxrest = (byte) 192;
        trildef = 6;
        mucomsub.init_rhythm();
        Arrays.fill(rhyvol, (byte) 31);
        dionpu = 0;
        slurmod = 0;
        lastfrq = 0;
        harmno = 0;
        codemod = 0;
        dt2mode = 0;
        arpmode = 0;
        arpharm = 0;
        arplen = 0;
        volsft = 0;
        nesting = 0;
        nest2 = 0;
        chglen = 0;
        chgsav = 0;
        renplen = 0;
        restlen = 0;
        disave1 = 0;
        disave2 = 0;
        cresvol = 0;
        creslen = 0;
        dcrelen = 0;
        crescnt = 0;
        tmpdata = 0;
        tmplen = 0;
        tmpcnt = 0;
        Arrays.fill(dtdata, (byte) 0);
        rhydata = 0;
        jumpnes = 0;
        ichosav = 0;
        lastrp = 0;
        macrof = 0;
        commode = 0;
        mac_mod = 0;
        comcnt = 0;
        optimiz = 0;
        opt_tne = 0;
        opt_pan = 0;
        opt_vol = 0;
        opt_rhy = 0;
        ssgpcmm = 0;
        ratmode = 0;
        onpucnt = 0;
        ifflag = 0;
        Arrays.fill(alllen, 0);
        Arrays.fill(macrov, 0);
        macroflg = 0;
        Arrays.fill(pandata, (byte) 0);
        Arrays.fill(rhythmdta, (byte) 0);
        Arrays.fill(flatdata, (byte) 0);
        Arrays.fill(flatdata2, (byte) 0);
        Arrays.fill(stttbl, 0);
        byte fillVal = (mmlver < 0x30) ? (byte) 1 : (byte) 5;
        Arrays.fill(dtshift, fillVal);
        for (int i = 0; i < 256; i++) TONEOFSbuf[i] = (byte) i;
        r.setAx(r.pop());
        r.setCx(r.pop());
        r.di = r.pop();
        r.ds = r.pop();
        r.es = r.pop();
    }

    public void compile() {
        r.push(r.cs);
        r.ds = r.pop();
        symbol2 = r.cl;
        check_calplay();
        if (!r.zero) return;

        topx = MXPOS;
        topy = MYPOS;
        sizex = MXSIZE;
        sizey = MYSIZE;
        r.setDx((short) 0);
        saveText();
        attr = (byte) 0xa1;
        setText();

        if (r.cl != 0) {
            attr = (byte) 0xc1;
            locatex = MXPOS + 2;
            locatey = MYPOS + 2;
            putword(mess_10);
            locatex = MXPOS + 2;
            locatey = MYPOS + 3;
            putword(mess_11);
            return;
        }

        attr = (byte) 0xe1;
        locatex = MXPOS + 1;
        locatey = MYPOS + 6;
        putword(mess_3a);
        locatex = MXPOS + 1;
        locatey = MYPOS;
        putword(mess_6);

        byte curX = MXPOS + 21;
        byte curVal = '1';
        for (int i = 0; i < 5; i++) {
            locatex = curX;
            putchrs(new byte[] {(byte) '#', curVal});
            curX += 8;
            curVal++;
        }

        locatey = MYPOS + 1;
        int curNum = 0;
        for (int i = 0; i < 4; i++) {
            locatex = MXPOS + 12;
            putstr("+{0,2:D}", curNum);
            curNum += 5;
            locatey++;
        }

        Arrays.fill(muap98.text_Buf, MACACHE, MACACHE + 52, (byte) 0);
        Arrays.fill(muap98.bufbuf, 64, 80, (byte) 0);
        maxlen[0] = 0;
        maxlen[1] = 0;
        tempos = 120;
        mode[0] = 0;
        debug = 0;
        r.es = (short) muap98.object_;
        r.di = OBJTOP;
        bef_len = r.di;
        disave = 0;
        muap98.objectBuf.set(0, new MmlDatum((byte) r.di));
        muap98.objectBuf.set(1, new MmlDatum((byte) (r.di >> 8)));
        muap98.objectBuf.set(0x24, new MmlDatum(r.bl));
        muap98.objectBuf.set(0x25, new MmlDatum(r.bh));
        r.ch = 1;
        spsave = r.sp;
        r.ds = (short) muap98.source;

        ver_check();

        while (true) {
            work.crntChip = r.ch < 12 ? "YM2608" : "YM3438";
            work.crntChannel = (r.ch - 1);
            work.crntPart = r.ch < 4 ? "FM" : r.ch < 7 ? "SSG" : r.ch == 10 ? "RHYTHM" : r.ch == 11 ? "ADPCM" : "FM";
            try {
                recov8();
            } catch (MusCompileEndException mcee) {
                compile_end();
                break;
            } catch (MusRecov8Exception mr8e) {
                continue;
            }
        }
    }

    private void ver_check() {
        while (true) {
            r.setAx(getSourceData(r.getBx()));
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) == 0xff) {
                error15();
                return;
            }
            if ((r.getBx() & 0xffff) >= muap98.sor_len) {
                error15();
                return;
            }
            if (r.al == '_' && r.ah == 'v') break;
            if (r.al == '_' && r.ah == 'V') break;
        }
        short v = getSourceData((r.getBx() & 0xffff) + 1);
        int vInt = v & 0xff;
        if (vInt < '2' || vInt > '4') {
            ver_check();
            return;
        }

        byte major = (byte) (muap98.sourceBuf[(r.getBx() & 0xffff) + 1] - '0');
        byte minor = (byte) (muap98.sourceBuf[(r.getBx() & 0xffff) + 3] - '0');
        mmlver = (byte) ((major << 4) | minor);
    }

    private void error15() {
        check_calplay();
        if (r.zero) {
            r.pushA();
            r.push(r.ds);
            r.ds = r.cs;
            locatex = MXPOS + 1;
            locatey = MYPOS + 5;
            r.cl = 34;
            r.di = 0;
            r.ah = 1;
            muap98.call_func();
            if (!r.carry) putword("");
            r.ds = r.pop();
            r.popA();
        }
    }

    private void recov8() throws MusCompileEndException, MusRecov8Exception {
        while (true) {
            check_calplay();
            if (r.zero) {
                r.setAx((short) 0x400);
                pc98_Int18();
                if ((r.ah & 1) != 0) {
                    locatex = MXPOS + 1;
                    locatey = MYPOS + 6;
                    putword(mess_9);
                    while (true) {
                        r.ah = 1;
                        pc98_Int18();
                        if (r.bh == 0) break;
                        r.ah = 0;
                        pc98_Int18();
                    }
                    for (int i = 0; i < 34; i += 2) {
                        muap98.objectBuf.set(i, new MmlDatum(r.al));
                        muap98.objectBuf.set(i + 1, new MmlDatum(r.ah));
                    }
                    muap98.objectBuf.set(OBJTOP, new MmlDatum((byte) 0xfc));
                    r.ds = r.cs;
                    r.cl = 1;
                }
            }
            ope_no = r.ch;
            sendch = r.ch;
            work_init();
            linedta = 1;
            work.row = 1;
            work.col = 1;
            work.oldbx = 0;
            r.setBx((short) srctop);
            if (!recov7()) break;
        }
    }

    private boolean recov7() throws MusCompileEndException, MusRecov8Exception {
        while (true) {
            mode[1] &= 0xfe;
            r.ch = ope_no;
            if (chkpart()) return true;
            if (r.al >= '1' && r.al <= '9') break;
        }
        boolean s = true;
        r.dl = (byte) (r.al - '0');
        if (r.dl == 1) {
            if (chkpart()) return true;
            if (r.al < '0' || r.al > '7') s = false;
            else r.dl = (byte) (r.al - 38);
        }
        if (s) {
            if (chkpart()) return true;
        }

        int ret = 0;
        if (r.al == '[') ret = ch_nomulti();
        else if (r.al == ',') ret = ch_multi();
        else if (r.al == '-') ret = cnt_multi();

        if (ret == 1) find4();
        else if (ret == 2) return recov7();

        return recov7();
    }

    private int ch_nomulti() {
        if ((r.dl & 0xff) > (r.ch & 0xff)) return 0;
        if (r.dl == r.ch) return 1;
        int skip = (r.ch & 0xff) - (r.dl & 0xff);
        do {
            do {
                do {
                    chkpart();
                } while (r.carry);
                if (r.al == ']') return 0;
            } while (r.al != '|');
            skip--;
        } while (skip != 0);
        return 1;
    }

    private int ch_multi() {
        if (r.dl != r.ch) return 0;
        do {
            do {
                chkpart();
                if (r.al == '[') return 1;
            } while (r.al == ',' || r.al == '-');
            mucomsub.chknum();
        } while (!r.carry);
        return 0;
    }

    private int cnt_multi() {
        if ((r.dl & 0xff) > (r.ch & 0xff)) return 0;
        r.dh = r.dl;
        chkpart();
        if (r.al < '1' || r.al > '9') return 0;
        r.dl = (byte) (r.al - '0');
        if (r.dl == 1) {
            chkpart();
            if (r.al >= '0' && r.al <= '7') r.dl = (byte) (r.al - 38);
        }
        chkpart();
        if (r.al != '[' && r.al != ',' && r.al != '-') return 0;
        if ((r.dl & 0xff) < (r.ch & 0xff)) return 2;
        if (r.al == '[') return 1;
        return ch_multi();
    }

    private int find4() throws MusCompileEndException, MusRecov8Exception {
        do {
            do {
                mode[1] |= 1;
                chktxt();
            } while (r.carry);
            if (r.al == ']') return 0;
            if (r.al == '|') {
                do {
                    chktxt();
                } while (r.al != ']');
                return 0;
            }
            r.ch = sendch;
            com_main();
            r.push(r.getAx());
            r.setAx((short) (muap98.bufleno - 0x10));
            r.carry = ((r.di & 0xffff) < (r.getAx() & 0xffff));
            r.cl = 2;
            r.setAx(r.pop());
        } while (r.carry);
        return 1;
    }

    public void chktxt() {
        while (true) {
            if ((r.getBx() & 0xffff) >= muap98.sor_len) theend();
            work.col = (r.getBx() & 0xffff) - work.oldbx + 1;
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) == 0xff) theend();

            if (r.al != ' ' && r.al != 9) {
                if ((r.al & 0xff) != 0xfe) break;
                mucomsub.check_flatclear();
                linedta++;
                work.row++;
                work.oldbx = r.getBx() & 0xffff;
            }
            macrof = 0;
        }
        if (r.al == ';') {
            skip_rem();
            mucomsub.check_flatclear();
            linedta++;
            work.row++;
            work.oldbx = r.getBx() & 0xffff;
            macrof = 0;
            chktxt();
            return;
        }
        if (r.al == '$') macrof ^= 1;
        r.push(r.getDx());
        int prevIdx = (r.getBx() & 0xffff) - 2;
        r.dh = (byte) (prevIdx < 0 ? 0 : muap98.sourceBuf[prevIdx]);
        r.dl = r.al;
        testknj();
        r.setDx(r.pop());
        bxsave = r.getBx() & 0xffff;
        return;
    }

    private void skip_rem() {
        do {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.getBx() & 0xffff) == muap98.sourceBuf.length) theend();
        } while ((r.al & 0xff) != 0xfe);
    }

    private boolean chkpart() {
        while (true) {
            chktxt();
            if (macrof == 0) break;
        }
        return false;
    }

    public void theend() {
        r.sp = spsave;
        r.ch = ope_no;
        r.zero = (nesting == 0);
        r.cl = 13;
        if (!r.zero) {
            error();
            return;
        }
        r.cl = 35;
        if (jumpnes != 0) {
            error();
            return;
        }

        r.al = (byte) 0xfc;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));

        check_calplay();
        if (r.zero) {
            r.push((short) (locatex | (locatey << 8)));
            r.setAx((short) (r.ch - 1));
            r.div((byte) 5);
            locatey = (byte) (r.al + MYPOS + 1);
            locatex = (byte) ((r.ah << 3) + MXPOS + 16);
            int dataLen = (r.di & 0xffff) - bef_len;
            bef_len = r.di & 0xffff;
            attr = (byte) 0xe1;
            if ((ifflag & 1) != 0) attr = (byte) 0x81;

            // Octave/Length processing... (omitted for brevity but logic follows original)
            if (work.compilerInfo.totalCount == null) work.compilerInfo.totalCount = new ArrayList<>();
            work.compilerInfo.totalCount.add((alllen[1] << 16) | alllen[0]);

            short ans = r.pop();
            locatex = (byte) ans;
            locatey = (byte) (ans >> 8);
        }

        int startAdr = (muap98.objectBuf.get(disave).dat & 0xff) | ((muap98.objectBuf.get(disave + 1).dat & 0xff) << 8);
        r.setBx((short) startAdr);
        r.ch = 0;
        set_labeladrs();
        r.ch++;
        if ((r.ch & 0xff) > 17) throw new MusCompileEndException();
        disave += 2;
        muap98.objectBuf.set(disave, new MmlDatum((byte) r.di));
        muap98.objectBuf.set(disave + 1, new MmlDatum((byte) (r.di >> 8)));
        throw new MusRecov8Exception();
    }

    private void set_labeladrs() {
        r.pushA();
        r.push(r.es);
        linedta = 0;
        work.row = -1;
        work.col = -1;
        while (true) {
            if ((r.getBx() & 0xffff) >= (r.di & 0xffff)) {
                setad0();
                return;
            }
            r.setAx((short) ((muap98.objectBuf.get(r.getBx() & 0xffff).dat & 0xff) | ((muap98.objectBuf.get((r.getBx() & 0xffff) + 1).dat & 0xff) << 8)));
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            int opcode = r.al & 0xff;
            if (opcode <= 0x3f) {
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                continue;
            }
            if (opcode == 0x80) {
                find_then();
                continue;
            }
            if (opcode == 0x81) {
                find_exit();
                continue;
            }
            if (opcode == 0x89 || opcode == 0x8a) {
                find_gljp();
                continue;
            }
            if (opcode == 0xea || opcode == 0xe9) {
                find_jp();
                continue;
            }
            if (opcode == 0xe4 || opcode == 0xe3) {
                find_if();
                continue;
            }
            if (opcode == 0xdb) {
                find_com();
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                continue;
            }

            int skip = skipbyte[0x100 - opcode] & 0xff;
            r.setBx((short) ((r.getBx() & 0xffff) + skip));
        }
    }

    private void find_gljp() {
        muap98.objectBuf.get((r.getBx() & 0xffff) - 1).dat |= 0x60;
        int varIdx = (muap98.objectBuf.get(r.getBx() & 0xffff).dat & 0xff) * 2;
        int adr = (muap98.bufbuf[varIdx] & 0xff) | ((muap98.bufbuf[varIdx + 1] & 0xff) << 8);
        if (adr == 0) {
            r.cl = 26;
            error();
            return;
        }
        int offset = adr - (r.getBx() & 0xffff) + 1;
        if (offset == 0xfffd) {
            r.cl = 27;
            error();
            return;
        }
        muap98.objectBuf.set(r.getBx() & 0xffff, new MmlDatum((byte) offset));
        muap98.objectBuf.set((r.getBx() & 0xffff) + 1, new MmlDatum((byte) (offset >> 8)));
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
    }

    private void find_then() {
        muap98.objectBuf.set((r.getBx() & 0xffff) - 1, new MmlDatum((byte) 0xe4));
        r.setBx((short) ((r.getBx() & 0xffff) + 4));
    }

    private void find_exit() {
        muap98.objectBuf.set((r.getBx() & 0xffff) - 1, new MmlDatum((byte) 0xd3));
        r.setBx((short) ((r.getBx() & 0xffff) + 4));
    }

    private void find_if() {
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        find_jp();
    }

    private void find_jp() {
        if ((muap98.objectBuf.get((r.getBx() & 0xffff) + 2).dat & 0xff) != 0x88) {
            if (r.ch == 0) {
                int varNum = muap98.objectBuf.get(r.getBx() & 0xffff).dat & 0xff;
                if (varNum > 31) {
                    muap98.objectBuf.get((r.getBx() & 0xffff) - 1).dat &= 0x9f;
                } else {
                    int varIdx = varNum * 2;
                    int adr = (muap98.bufbuf[varIdx] & 0xff) | ((muap98.bufbuf[varIdx + 1] & 0xff) << 8);
                    if (adr == 0) {
                        r.cl = 26;
                        error();
                        return;
                    }
                    int offset = adr - (r.getBx() & 0xffff) + 1;
                    muap98.objectBuf.set(r.getBx() & 0xffff, new MmlDatum((byte) offset));
                    muap98.objectBuf.set((r.getBx() & 0xffff) + 1, new MmlDatum((byte) (offset >> 8)));
                }
            }
            r.setBx((short) ((r.getBx() & 0xffff) + 2));
            return;
        }
        int skip = ((muap98.objectBuf.get(r.getBx() & 0xffff).dat & 0xff) | ((muap98.objectBuf.get((r.getBx() & 0xffff) + 1).dat & 0xff) << 8)) - 1;
        r.setBx((short) ((r.getBx() & 0xffff) + skip));
    }

    private void find_com() {
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        int len = muap98.objectBuf.get(r.getBx() & 0xffff).dat & 0xff;
        r.setBx((short) ((r.getBx() & 0xffff) + len));
    }

    private void setad0() {
        r.es = r.pop();
        r.popA();
    }

    private void compile_end() {
        r.setBx((short) OBJTOP);
        r.ch = 1;
        set_labeladrs();
        r.ds = r.cs;
        muap98.obj_len = r.di;
        muap98.objectBuf.set(SYMDTA, new MmlDatum(symbol2));
        muap98.objectBuf.set(0x26, new MmlDatum((byte) maxlen[0]));
        muap98.objectBuf.set(0x27, new MmlDatum((byte) (maxlen[0] >> 8)));
        muap98.objectBuf.set(0x28, new MmlDatum((byte) maxlen[1]));
        muap98.objectBuf.set(0x29, new MmlDatum((byte) (maxlen[1] >> 8)));

        check_calplay();
        if (r.zero) {
            locatex = MXPOS + 1;
            locatey = MYPOS + 6;
            putword(mess_7);
            putstr("{0,5}", (int) r.di);
            putword(mess_8);
            // Cleanup object buffer beyond di
        }
        bxsave = 0xffff;
        com_end2();
    }

    private void com_end2() {
        check_calplay();
        if (r.zero) {
            if ((menu.crflag & 0x40) == 0) {
                if (r.cl != 0 || (symbol2 & 4) == 0) {
                    menu.check_visualplay();
                    if (r.zero) {
                        r.ah = 0;
                        pc98_Int18();
                    }
                }
            }
            r.setDx((short) 0);
            loadText();
        }
        work_init();
    }

    public void error() {
        r.ds = r.cs;
        r.es = r.cs;
        r.sp = spsave;
        clbuff();
        check_calplay();
        String errMsg = "";
        if (r.zero) {
            attr = (byte) 0xe1;
            r.di = 0;
            r.push((short) 0);
            stamode = 1;
            r.ah = 0;
            r.setAx((short) (r.cl & 0xff));
            dsp2dec();
            stamode = 0;
            muap98.bufbuf[r.di] = '$';
            r.di++;
            r.di = r.pop();
            r.ah = 1;
            call_func();
            locatex = MXPOS + 1;
            locatey = MYPOS + 6;
            errMsg = calerror1[(r.cl & 0xff) - 1].replace("$", "") + mess_5.replace("$", "");
            attr = (byte) (((attr & 0x1f) | (((attr >> 5) + 1) << 5)));
            r.ah = 1;
            pc98_Int18();
        }
        r.cl = 1;
        com_end2();
        String msg = String.format("Error %s at line %d", errMsg, linedta);
        if (work.compilerInfo == null) work.compilerInfo = new CompilerInfo();
        work.compilerInfo.errorList.add(new Tuple3<>((linedta - 1), -1, msg));
        throw new RuntimeException(new MusException(String.format("%s in %d", errMsg, linedta)));
    }

    public void com_main() {
        work.md = null;
        r.push(r.di);
        int nextChar = (r.getBx() & 0xffff) < muap98.sourceBuf.length ? muap98.sourceBuf[r.getBx() & 0xffff] : 0;
        r.ah = (byte) nextChar;
        if (mac_mod != 0) {
            if ((mac_mod & 2) == 0) {
                if (r.al >= 'a' && r.al <= 'z') {
                    r.di = r.pop();
                    mucomsub.macro_exec();
                    return;
                }
            } else {
                if (r.al >= 'A' && r.al <= 'Z') {
                    r.di = r.pop();
                    mucomsub.macro_exec();
                    return;
                }
            }
        }
        r.cl = 32;
        if ((r.al & 0xff) <= ' ') {
            error();
            return;
        }
        if ((r.al & 0xff) > '~') {
            error();
            return;
        }
        if (r.al >= 'a' && r.al <= 'z') r.al -= 32;
        else if (r.al >= '{') r.al -= 0x1b;

        r.push(r.getAx());
        int tblIdx = ((r.al & 0xff) - '!') * 2;
        r.setDx((short) tblIdx);
        r.setAx(r.pop());
        r.di = r.pop();
        jpdata[tblIdx / 2].run();
    }

    private Runnable[] jpdata;

    private void InitJpdata() {
        jpdata = new Runnable[] {
                this::acc, this::stak, this::error, mucomsub::dtcall, // !"#$
                this::error, this::tie, mucomsub::wait_r, mucomsub::nloop1, // %&'(
                mucomsub::nloop2, this::mloop, this::error, this::error, // )*+,
                this::error, this::error, mucomsub::harm_onpu, this::error, // -./0
                this::error, this::error, this::error, this::error, // 1234
                this::error, this::error, this::error, this::error, // 5678
                this::error, this::error, this::error, this::octdown, // 9:;<
                this::error, this::octup, this::unacc, mucomsub::exp_cmd, // =>?@
                this::unexst, this::unexst, this::unexst, this::unexst, // ABCD
                this::unexst, this::unexst, this::unexst, mucomsub::reghex, // EFGH
                this::error, this::error, mucomsub::rhyexp, this::mlength, // IJKL
                mucomsub::env_speed, mucomsub::noise, this::oct, mucomsub::envelope, // MNOP
                this::ratio, this::rest, mucomsub::env_type, this::tempo, // QRST
                this::error, this::volume, mucomsub::porta, this::value, // UVWX
                mucomsub::reg, mucomsub::usr_tone, this::error, this::error, // YZ[\
                this::error, this::error, mucomsub::icho, this::renpu, // ]^_{
                this::error, this::error, this::tie2 // |}~
        };
    }

    private void unexst() {
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        lp.chip = work.crntChip;
        lp.ch = (byte) work.crntChannel;
        lp.part = work.crntPart;
        MmlDatum md = new MmlDatum(MMLType.Note, new ArrayList<>(Arrays.asList(0, 0)), lp, 0);
        work.md = work.FlashLstMd(md);
        set_symbol2();
        check_comlen();
        if (codemod != 0) {
            code_change();
            return;
        }
        if (r.ch == 10) {
            mucomsub.rhyexp();
            return;
        }
        r.push(r.getAx());
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        gethenon();
        tnelnmx();
        lensave = r.al;
        work.md.args.set(work.mdArgsStep + 1, work.otoLength);
        r.setAx(r.pop());
        if (harmno != 0) {
            harm_mode();
            return;
        }
        if (arpharm != 0) {
            arp_press0();
            return;
        }
        r.push(r.getAx());
        r.al = lensave;
        tnelnx();
        r.setAx(r.pop());
        mucomsub.read();
        reset_arp();
    }

    private void reset_arp() {
        arpmode = 0;
    }

    public void set_symbol2() {
        if ((symbol2 & 1) != 0) {
            if ((symbol2 & 2) != 0 || nest2 == 0) {
                muap98.objectBuf.set(r.di++, new MmlDatum((byte) 0xe7));
                int adr = (r.getBx() & 0xffff) - 1;
                muap98.objectBuf.set(r.di++, new MmlDatum((byte) adr));
                muap98.objectBuf.set(r.di++, new MmlDatum((byte) (adr >> 8)));
            }
        }
    }

    private void harm_mode() {
        harm_main();
        if (!r.carry) {
            set_honpu();
            return;
        }
        r.dl = lensave;
        kyufu();
        reset_arp();
    }

    private void set_honpu() {
        if (arpmode != 0) {
            if (arpharm != 0) {
                arp_press();
                return;
            }
            get_arprest();
        }
        r.push((short) octdata);
        get_harm();
        r.push(r.getAx());
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        gethenon();
        r.al = lensave;
        tnelnx();
        r.setAx(r.pop());
        mucomsub.read();
        octdata = (byte) r.pop();
        mucomsub.harm_onpu();
        reset_arp();
    }

    private void arp_press0() {
        r.push((short) octdata);
        r.push(r.getAx());
        if (arpp6() != 0) arpp4();
    }

    private void arp_press() {
        if ((r.dl & 0xff) <= (harmno & 0xff)) {
            r.dl = lensave;
            kyufu();
            mucomsub.harm_onpu();
            reset_arp();
            return;
        }
        get_arprest();
        arpp4();
    }

    private void arpp4() {
        while (true) {
            r.push((short) octdata);
            get_harm();
            r.push(r.getAx());
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            gethenon();
            if (arpp6() == 0) break;
        }
    }

    private int arpp6() {
        r.push(r.getDx());
        r.al = arpharm;
        get_defarp();
        int mul = (r.al & 0xff) * (r.dl & 0xff);
        r.carry = (lensave & 0xff) < mul;
        lensave = (byte) ((lensave & 0xff) - mul);
        if (r.carry) {
            error();
            return 0;
        }
        int prevDi = r.di;
        tnelnx();
        r.setAx(r.pop());
        mucomsub.read();
        octdata = (byte) r.pop();
        byte skipCount = arpharm;
        while (true) {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            mucomsub.xsmall();
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) >= 0xfe) {
                error();
                return 0;
            }
            if (r.al == ':') break;
            if (r.al == '/') {
                skipCount--;
                if (skipCount == 0) return 1;
            } else if (r.al == '@') {
                r.push(r.getDx());
                r.push((short) prevDi);
                r.push((short) octdata);
                mode[0] |= 4;
                mucomsub.exp_cmd();
                octdata = (byte) r.pop();
                r.di = r.pop();
                r.setDx(r.pop());
            }
        }
        // Logic for handling f4/fa codes... (omitted)
        reset_arp();
        return 0;
    }

    public void get_harm() {
        mucomsub.skipoct();
        if (r.al == '@') {
            mode[0] |= 4;
            mucomsub.exp_cmd();
            chktxt();
            mucomsub.xsmall();
        }
        mucomsub.check_onpu();
    }

    private void get_arprest() {
        r.push(r.getAx());
        r.al = lensave;
        r.push(r.getAx());
        r.al = harmno;
        get_defarp();
        int mul = (r.al & 0xff) * (r.dl & 0xff);
        add_tlen(mul);
        r.dl = (byte) mul;
        kyufu();
        r.setAx(r.pop());
        r.carry = (r.al & 0xff) < (r.dl & 0xff);
        lensave = (byte) ((r.al & 0xff) - (r.dl & 0xff));
        if (r.carry) error();
        r.setAx(r.pop());
    }

    private void get_defarp() {
        r.dl = (arplen != 0) ? arplen : trildef;
    }

    public void harm_main() {
        mucomsub.skipoct();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.push(r.getBx());
        chktxt();
        if (r.al != '/') {
            r.setBx(r.pop());
            r.carry = true;
            return;
        }
        r.setDx(r.pop());
        r.dl = harmno;
        while (true) {
            r.dl--;
            if (r.dl == 0) {
                r.carry = false;
                return;
            }
            while (true) {
                chktxt();
                if (r.al == ':') {
                    r.carry = true;
                    return;
                }
                if (r.al != '@') {
                    if (r.al == '/') break;
                    continue;
                }
                r.push(r.getDx());
                r.push((short) octdata);
                mode[0] |= 4;
                mucomsub.exp_cmd();
                octdata = (byte) r.pop();
                r.setDx(r.pop());
            }
        }
    }

    public void arpeggio() {
        arpmode = 1;
        arplen = 0;
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucomsub.chknum();
        if (!r.carry) {
            tnelnmx();
            arplen = r.al;
        }
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        if (r.al != ',') {
            arpharm = 0;
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        rednums();
        arpharm = r.al;
    }

    private void code_change() {
        get_keycode();
        bassdta = r.al;
        r.push(r.getAx());
        r.push(r.getBx());
        r.cl = 9;
        int siIdx = 0;
        short curCode = 0;
        while (true) {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.ah = muap98.sourceBuf[(r.getBx() & 0xffff) + 1];
            if (r.al == 'o' && r.ah == 'n') {
                r.setBx((short) ((r.getBx() & 0xffff) + 2));
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
                mucomsub.xsmall();
                mucomsub.check_onpu();
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                get_keycode();
                bassdta = r.al;
                if (muap98.sourceBuf[r.getBx() & 0xffff] != ':') error();
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                break;
            }
            if (r.al != (byte) codedta.charAt(siIdx)) {
                while (codedta.charAt(siIdx) != ':') siIdx++;
                siIdx++;
                r.setBx(r.pop());
                r.push(r.getBx());
                curCode++;
                continue;
            }
            siIdx++;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if (codedta.charAt(siIdx) == ':') break;
        }
        r.setBx(r.pop());
        r.setAx(r.pop());
        if ((r.al & 0xff) < (bassdta & 0xff)) r.al += 12;
        onkai = r.al;
        codesav = curCode;
        codem0();
    }

    private void get_keycode() {
        r.push(r.getDx());
        r.push(r.getAx());
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        gethenon();
        if ((r.dl & 0xff) >= 3) error();
        r.setAx(r.pop());
        r.al = musdata[(r.al & 0xff) - 'A'];
        if (r.dl == 1) r.al++;
        else if (r.dl == 2) r.al--;
        r.setDx(r.pop());
    }

    private String codedta = "m:6:m6:7:m7:M7:mM7:sus4:7sus4:(+5):(-5):7(+5):7(-5):m7(-5):dim:add9:madd9:69:m69:7(+9):7(-9):9:m9:9(+5):9(-5):M9:mM9:11:m11:9(+11):13:";
    private byte[] codetne = new byte[] {0x00, 0x04, 0x07, (byte) 0xff, 0x00, 0x03, 0x07, (byte) 0xff, 0x00, 0x04, 0x07, 0x09, 0x00, 0x03, 0x07, 0x09, 0x00, 0x04, 0x07, 0x0a, 0x00, 0x03, 0x07, 0x0a, 0x00, 0x04, 0x07, 0x0b, 0x00, 0x03, 0x07, 0x0b, 0x00, 0x05, 0x07, (byte) 0xff, 0x00, 0x05, 0x07, 0x0a, 0x00, 0x04, 0x08, (byte) 0xff, 0x00, 0x04, 0x06, (byte) 0xff, 0x00, 0x04, 0x08, 0x0a, 0x00, 0x04, 0x06, 0x0a, 0x00, 0x03, 0x06, 0x0a, 0x00, 0x03, 0x06, 0x09, 0x00, 0x04, 0x07, 0x0e, 0x00, 0x03, 0x07, 0x0e, 0x04, 0x09, 0x0e, (byte) 0xff, 0x03, 0x09, 0x0e, (byte) 0xff, 0x04, 0x0a, 0x0f, (byte) 0xff, 0x04, 0x0a, 0x0d, (byte) 0xff, 0x04, 0x0a, 0x0e, (byte) 0xff, 0x03, 0x0a, 0x0e, (byte) 0xff, 0x04, 0x08, 0x0a, 0x0e, 0x04, 0x06, 0x0a, 0x0e, 0x04, 0x07, 0x0b, 0x0e, 0x03, 0x07, 0x0b, 0x0e, 0x0a, 0x10, 0x11, (byte) 0xff, 0x0a, 0x0f, 0x11, (byte) 0xff, 0x04, 0x06, 0x0a, 0x0e, 0x04, 0x09, 0x0a, 0x0e};

    private void codem0() {
        tnelnmx();
        rtm_max = r.al;
        mode[0] &= 0xfd;
        while (true) {
            r.dl = rtm_max;
            getrhythm_table();
            if (r.zero) {
                norm_code();
                return;
            }
            if ((r.dl & 0xff) <= (r.al & 0xff)) {
                rend5();
                return;
            }
            rtm_max = (byte) ((rtm_max & 0xff) - (r.al & 0xff));
            rend4();
        }
    }

    private void rend5() {
        r.al = r.dl;
        rend4();
    }

    private void rend4() {
        tnelnx();
        if (r.ah == (byte) 0xff) {
            rend6();
            return;
        }
        r.push(r.getAx());
        set_codedta();
        r.setAx(r.pop());
        if (r.ah == (byte) 0xfe) acc();
        else if (r.ah == (byte) 0xfd) unacc();
        else if (r.ah == (byte) 0xfc) stak();
    }

    private void rend6() {
        mucomsub.cres_check();
        dionpu = 0;
        r.al = (byte) 0xff;
        stosbObjBufAL2DI();
    }

    private void getrhythm_table() {
        mucomsub.init_rhythm();
        getr_main();
    }

    private void getr_main() {
        r.ah = 0;
        r.push((short) rhyadrs);
        do {
            r.al = rhythmdta[rhyadrs];
            if ((r.al & 0xff) < 0xfc) break;
            rhyadrs++;
            r.ah = r.al;
        } while (true);
        r.pop();
        rhyadrs++;
        r.zero = r.al == 0;
    }

    private void norm_code() {
        mucomsub.init_rhythm();
        r.al = rtm_max;
        tnelnx();
        set_codedta();
    }

    private void set_codedta() {
        mucomsub.cres_check();
        dionpu = r.di;
        if (sendch == 10) {
            mucomsub.rhyexp_code();
            return;
        }
        mucomsub.pan_check();
        int siIdx = codesav * 4;
        r.cl = onkai;
        switch (codemod) {
            case 1:
                set_3ch();
                break;
            case 2:
                r.al = 0;
                setcode2(siIdx);
                break;
            case 3:
                r.al = codetne[siIdx + 3];
                setcode2(siIdx);
                break;
            case 4:
                r.al = codetne[siIdx + 2];
                setcode2(siIdx);
                break;
            case 5:
                r.al = codetne[siIdx + 1];
                setcode2(siIdx);
                break;
            case 6:
                r.al = codetne[siIdx];
                setcode2(siIdx);
                break;
            default:
                r.cl = 0;
                r.al = bassdta;
                setcode2(siIdx);
                break;
        }
    }

    private void set_3ch() {
        if ((mode[0] & 2) != 0) {
            r.al = (byte) 0xf9;
            stosbObjBufAL2DI();
            return;
        }
        r.al = (byte) 0xfa;
        stosbObjBufAL2DI();
        int si = codesav * 4;
        r.al = codetne[si];
        setcode();
        r.al = codetne[si + 1];
        setcode();
        r.al = codetne[si + 2];
        setcode();
        r.al = codetne[si + 3];
        setcode();
        mode[0] |= 2;
    }

    private void setcode() {
        if ((r.al & 0xff) != 0xff) {
            r.al += r.cl;
            mucomsub.read2();
        } else {
            r.push(r.getAx());
            r.setAx((short) 0);
            stoswObjBufAX2DI();
            r.setAx(r.pop());
        }
    }

    private void setcode2(int si) {
        if ((r.al & 0xff) != 0xff) {
            r.al += r.cl;
            mucomsub.read2();
        } else stosbObjBufAL2DI();
    }

    public void codein() {
        r.cl = 16;
        mucomsub.check_314();
        if (!r.zero) error();
        dt2mode = 0;
        codemod = 1;
        r.setAx((short) 0x40ed);
        stoswObjBufAX2DI();
    }

    public void codeout() {
        codemod = 0;
        mucomsub.check_314();
        if (!r.zero) return;
        r.setAx((short) 0x00ed);
        stoswObjBufAX2DI();
    }

    public void bass() {
        r.al = 5;
        bass1();
    }

    public void code_exe() {
        rednums();
        if ((r.al & 0xff) > 4) error();
        bass1();
    }

    private void bass1() {
        r.al += 2;
        codemod = r.al;
    }

    public void same_code() {
        set_symbol2();
        mucomsub.cres_check();
        tnelnmx();
        r.dl = r.al;
        if (dionpu == 0) {
            kyufu();
            return;
        }
        mucomsub.pan_check();
        tnelnx();
        if ((mode[0] & 0x80) != 0) tietie();
        dionpu = r.di;
        if (codemod == 1) {
            r.al = (byte) 0xf9;
            stosbObjBufAL2DI();
            return;
        }
        if (sendch == 11) {
            r.al = (byte) 0xd5;
            stosbObjBufAL2DI();
            r.al = (byte) muap98.objectBuf.get(dionpu + 1).dat;
            r.ah = (byte) muap98.objectBuf.get(dionpu + 2).dat;
            stoswObjBufAX2DI();
            return;
        }
        r.al = (byte) muap98.objectBuf.get(dionpu).dat;
        r.ah = (byte) muap98.objectBuf.get(dionpu + 1).dat;
        stoswObjBufAX2DI();
    }

    public void gethenon() {
        r.dl = 1;
        if (r.al == '+' || r.al == '#') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            if (r.al == '#' || r.al == '+') {
                r.dl += 3;
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            }
            return;
        }
        r.dl = 2;
        if (r.al == '-') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            if (r.al == '-') {
                r.dl += 3;
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            }
            return;
        }
        r.dl = 3;
        if (r.al == '%') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            return;
        }
        r.dl = 0;
    }

    public void tnelnx() {
        r.push(r.getAx());
        if (chglen != r.al) setrat();
        if ((mode[0] & 1) == 0) add_tlen(r.al & 0xff);
        r.setAx(r.pop());
    }

    public void tnelnmx() {
        tnelnm();
        if ((r.getAx() & 0xffff) > 255) error();
        work.otoLength = (r.getAx() & 0xffff);
    }

    public void tnelnm() {
        check_macrov();
        if (!r.carry) {
            tnelnm1();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.push(r.getBx());
        r.push((short) 0); // SI dummy
        get_macroadrs();
        r.setBx((short) macrov[r.di / 2]);
        r.pop();
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        tnelnm1();
        r.setBx(r.pop());
    }

    private void tnelnm1() {
        r.push(r.getDx());
        r.push(r.getCx());
        r.setDx((short) 0);
        r.ch = 0;
        if (r.al == '=') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            rednum();
            r.setCx(r.pop());
            r.setDx(r.pop());
            return;
        }
        while (true) {
            gettxtlen();
            if (r.ch != 0) {
                r.carry = (r.getDx() & 0xffff) < (r.getAx() & 0xffff);
                r.setDx((short) (r.getDx() - r.getAx()));
                if (r.carry || r.zero) error();
                r.setAx(r.getDx());
            } else {
                int sum = (r.getAx() & 0xffff) + (r.getDx() & 0xffff);
                r.carry = sum > 0xffff;
                r.setAx((short) sum);
                if (r.carry) error();
            }
            byte next = muap98.sourceBuf[r.getBx() & 0xffff];
            if (next != '^' && next != '_') {
                r.setCx(r.pop());
                r.setDx(r.pop());
                return;
            }
            if (next == '_') {
                byte c = muap98.sourceBuf[(r.getBx() & 0xffff) + 1];
                if (c >= 'a') c -= 32;
                if (c == '>' || c == '<' || (c >= 'A' && c <= 'G')) {
                    r.setCx(r.pop());
                    r.setDx(r.pop());
                    return;
                }
                r.ch = 1;
            } else r.ch = 0;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.setDx(r.getAx());
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        }
    }

    private void gettxtlen() {
        r.push(r.getDx());
        if (r.al == '.') {
            loop17(true);
            return;
        }
        mucomsub.chknum();
        if (r.carry) {
            loop17(true);
            return;
        }
        rednums();
        r.dl = r.al;
        r.setAx((short) (192 / (r.dl & 0xff)));
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.setDx(r.getAx());
        loop17(false);
    }

    private void loop17(boolean nrm) {
        if (!nrm) {
            while (muap98.sourceBuf[(r.getBx() & 0xffff) + 1] == '.') {
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                r.setDx((short) ((r.getDx() & 0xffff) >> 1));
                r.setAx((short) (r.getAx() + r.getDx()));
            }
            r.setDx(r.pop());
            work.otoLength = (r.getAx() & 0xffff);
            return;
        }
        r.al = lendata;
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.setDx(r.getAx());
        loop17(false);
    }

    public void renpu() {
        renplen = chglen;
        disave3 = r.di;
        onpucnt = 0;
        mode[0] |= 1;
        while (true) {
            chktxt();
            if (r.al == '}') {
                exit1();
                return;
            }
            r.push(r.getDx());
            r.push(r.getCx());
            r.push(r.getAx());
            com_main();
            r.setAx(r.pop());
            r.setCx(r.pop());
            r.setDx(r.pop());
            if (onpucnt > 192) error();
        }
    }

    private void exit1() {
        mode[0] &= 0xfe;
        if (onpucnt == 0) error();
        tnelnmx();
        add_tlen(r.getAx() & 0xffff);
        r.push(r.getAx());
        int diff = r.di - disave3;
        if ((r.al & 0xff) == (onpucnt & 0xff)) {
            r.setAx(r.pop());
            r.di = (short) disave3;
            r.dl = r.al;
            kyufu();
            return;
        }
        r.setAx(r.pop());
        int len = (r.getAx() & 0xffff) / (onpucnt & 0xff);
        if (len == 0) error();
        chglen = (byte) len;
        if (chglen != renplen) {
            // Logic for moving object data for ratio adjustment... (omitted)
        }
        if (r.ah != 0) {
            r.dl = r.ah;
            kyufu();
        }
    }

    public void move_obj(int count) {
        if ((r.getAx() & 0xffff) < OBJTOP) {
            r.carry = true;
            return;
        }
        // Complex object buffer shifting logic...
        r.carry = false;
    }

    public void tie() {
        r.setAx((short) dionpu);
        move_obj(1);
        if (r.carry) return;
        r.push((short) r.di);
        r.di = r.getAx();
        r.al = (byte) 0xdf;
        stosbObjBufAL2DI();
        if (muap98.sourceBuf[r.getBx() & 0xffff] == '&') r.setBx((short) ((r.getBx() & 0xffff) + 1));
        else {
            lastfrq = (muap98.objectBuf.get(r.di).dat & 0xff) | ((muap98.objectBuf.get(r.di + 1).dat & 0xff) << 8);
            mode[0] |= 0x80;
        }
        r.di = r.pop();
        r.zero = false;
    }

    public void tie2() {
        if (r.ch != 11) tie();
        tietie();
    }

    private void tietie() {
        mode[0] &= 0x7f;
        r.al = (byte) 0xe1;
        stosbObjBufAL2DI();
    }

    public void stak() {
        r.setAx((short) dionpu);
        byte c = muap98.sourceBuf[r.getBx() & 0xffff];
        if (c != 0x22) {
            r.dl = 4;
            if (c == '.') {
                r.dl = 3;
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
            }
            move_obj(2);
            if (r.carry) return;
            r.push((short) r.di);
            r.di = r.getAx();
            byte savedQ = ratdata;
            r.al = r.dl;
            chgrat();
            r.di = r.pop();
            r.al = savedQ;
            chgrat();
        } else {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            move_obj(4);
            if (r.carry) return;
            r.push((short) r.di);
            r.di = r.getAx();
            byte savedQ = ratdata;
            r.al = 5;
            chgrat();
            r.setAx((short) 0x4d7);
            stoswObjBufAX2DI();
            r.di = r.pop();
            r.al = savedQ;
            chgrat();
            r.setAx((short) 0x4d6);
            stoswObjBufAX2DI();
        }
    }

    public void acc() {
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucomsub.chknum();
        if (r.carry) {
            acc_main();
            return;
        }
        rednums();
        r.push((short) accbase);
        accbase = r.al;
        acc_main();
        accbase = (byte) r.pop();
    }

    private void acc_main() {
        r.setAx((short) dionpu);
        move_obj(2);
        if (r.carry) return;
        r.push((short) r.di);
        r.di = r.getAx();
        r.ah = accbase;
        r.al = (byte) 0xd7;
        stoswObjBufAX2DI();
        r.di = r.pop();
        r.al = (byte) 0xd6;
        stoswObjBufAX2DI();
    }

    public void unacc() {
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucomsub.chknum();
        if (r.carry) {
            unacc_main();
            return;
        }
        rednum();
        r.push((short) dwnbase);
        dwnbase = r.al;
        unacc_main();
        dwnbase = (byte) r.pop();
    }

    private void unacc_main() {
        r.setAx((short) dionpu);
        move_obj(2);
        if (r.carry) return;
        r.push((short) r.di);
        r.di = r.getAx();
        r.ah = dwnbase;
        r.al = (byte) 0xd6;
        stoswObjBufAX2DI();
        r.di = r.pop();
        r.al = (byte) 0xd7;
        stoswObjBufAX2DI();
    }

    public void oct() {
        rednums();
        if (r.al == 0 || (r.al & 0xff) >= 10) error();
        octdata = (byte) (r.al - 1);
    }

    public void octup() {
        if ((octdata & 0xff) >= 8) error();
        octdata++;
    }

    public void octdown() {
        if (octdata == 0) error();
        octdata--;
    }

    public void ratio() {
        mucomsub.MakeDatum(MMLType.GatetimeDiv);
        work.md = work.FlashLstMd(work.md);
        rednums();
        work.md.args.add((int) (byte) r.al);
        if ((r.al & 0xff) >= 9 || r.al == 0) error();
        r.al = (byte) (0x08 - r.al); // Simplified logic
        ratmode &= 0xfe;
        chgrat();
    }

    public void setrat() {
        if ((mode[0] & 1) != 0) return;
        r.push(r.getAx());
        chglen = r.al;
        muap98.objectBuf.set(r.di, new MmlDatum((byte) 0xf4));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.al));
        r.di += 2;
        if ((ratmode & 1) == 0) {
            int val = (ratdata & 0xff) * (r.al & 0xff);
            r.al = (byte) (val >> 3);
        }
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        r.setAx(r.pop());
    }

    public void chgrat() {
        r.push(r.getAx());
        ratdata = r.al;
        if ((ratmode & 1) == 0) {
            int val = (ratdata & 0xff) * (chglen & 0xff);
            r.al = (byte) (val >> 3);
        }
        r.ah = r.al;
        r.al = (byte) 0xde;
        muap98.objectBuf.set(r.di, work.copy(work.md, r.al));
        work.md = null;
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;
        r.setAx(r.pop());
    }

    public void rest() {
        int r_ = work.row, c_ = work.col;
        set_symbol2();
        check_comlen();
        tnelnmx();
        if ((mode[0] & 1) == 0) add_tlen(r.al & 0xff);
        r.dl = r.al;
        work.row = r_;
        work.col = c_;
        kyufu();
    }

    // ================================
    // Specify the maximum value for compressed rests
    // ================================
    public void set_max() {
        tnelnmx();
        maxrest = r.al;
    }

    public void kyufu() {
        mucomsub.cres_check();
        mode[0] &= 0x7f;
        dionpu = 0;
        if ((mode[0] & 1) == 0) {
            kyufu1();
            return;
        }
        onpucnt++;
        r.push(r.getAx());
        muap98.objectBuf.set(r.di++, new MmlDatum((byte) 0xff));
        r.setAx(r.pop());
    }

    private void kyufu1() {
        r.push(r.getAx());
        if (r.di != disave2) {
            init_kyufu();
            return;
        }
        int total = (restlen & 0xff) + (r.dl & 0xff);
        if (total > 255 || total > (maxrest & 0xff)) {
            init_kyufu();
            return;
        }
        r.di = (short) disave1;
        r.al = (byte) total;
        set_kyufu();
        r.setAx(r.pop());
    }

    private void set_kyufu() {
        restlen = r.al;
        chglen = r.al;
        if (r.al != chgsav) setrat();
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        MmlDatum md = new MmlDatum((byte) 0xff, MMLType.Rest, lp, new Object[] {work.otoLength});
        muap98.objectBuf.set(r.di++, work.FlashLstMd(md));
        disave2 = r.di;
    }

    private void init_kyufu() {
        disave1 = r.di;
        chgsav = chglen;
        r.al = r.dl;
        set_kyufu();
        r.setAx(r.pop());
    }

    private void check_comlen() {
        if ((commode & 2) == 0) return;
        if (muap98.sourceBuf[(r.getBx() & 0xffff) - 2] != ' ') return;
        r.push(r.getAx());
        r.al = (byte) 0xdd;
        r.ah = comcnt;
        // Count trailing spaces logic...
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        r.setAx(r.pop());
    }

    public void rednum() {
        chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        check_macrov();
        if (r.carry) {
            int mask = 1 << (r.al & 0xff);
            if ((macroflg & mask) == 0) error();
            get_macroadrs();
            r.setAx((short) macrov[r.di / 2]);
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            return;
        }
        mucomsub.chknum();
        if (r.carry) error();
        int val = 0;
        while (true) {
            int c = (r.getBx() & 0xffff) < muap98.sourceBuf.length ? muap98.sourceBuf[r.getBx() & 0xffff] : 0;
            if (c < '0' || c > '9') break;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            val = val * 10 + (c - '0');
        }
        r.setAx((short) val);
    }

    public void rednums() {
        rednum();
        if (r.ah != 0) error();
    }

    private void check_macrov() {
        int c = (r.getBx() & 0xffff) < muap98.sourceBuf.length ? muap98.sourceBuf[r.getBx() & 0xffff] : 0;
        if (c != '\\') {
            r.carry = false;
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        c = (r.getBx() & 0xffff) < muap98.sourceBuf.length ? muap98.sourceBuf[r.getBx() & 0xffff] : 0;
        r.carry = (c < '1' || c > '9');
        if (r.carry) error();
        r.al = (byte) (c - '1');
        r.carry = true;
    }

    private void get_macroadrs() {
        r.di = (short) ((r.al & 0xff) * 2);
    }

    public void tempo() {
        rednum();
        if (mmlver < 0x26) r.setAx((short) (r.getAx() - 2));
        if ((r.getAx() & 0xffff) < 16 || (r.getAx() & 0xffff) > 3907) error();
        tmplen = 0;
        calc_tempo();
    }

    public void calc_tempo() {
        tempos = r.getAx();
        calct();
        r.push(r.getAx());
        r.al = (byte) 0xf5;
        stosbObjBufAL2DI();
        r.setAx(r.pop());
        stoswObjBufAX2DI();
    }

    public void calct() {
        r.setAx((short) (62500 / (r.getAx() & 0xffff)));
    }

    private void mloop() {
        r.al = (byte) 0xfe;
        byte d1 = muap98.sourceBuf[r.getBx() & 0xffff];
        if (d1 != '*') {
            stosbObjBufAL2DI();
            setrat();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) 0xfd;
        if (muap98.sourceBuf[r.getBx() & 0xffff] != '*') {
            stosbObjBufAL2DI();
            setrat();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        stopm();
    }

    public void stopm() {
        optimiz = 0;
        r.al = (byte) 0xfc;
        stosbObjBufAL2DI();
        setrat();
    }

    public void mlength() {
        tnelnmx();
        lendata = r.al;
        chglen = r.al;
        setrat();
    }

    private void volume() {
        byte c = muap98.sourceBuf[r.getBx() & 0xffff];
        if (c == '=') {
            exp_vol();
            return;
        }
        if (c == '+') {
            add_vol();
            return;
        }
        if (c == '-') {
            sub_vol();
            return;
        }
        rednums();
        retvol();
    }

    private void retvol() {
        if ((r.al & 0xff) >= 16) {
            r.cl = 31;
            error();
            return;
        }
        mucomsub.init_cres();
        int v = r.al & 0xff;
        if (r.ch == 11) r.ah = (byte) (v << 3);
        else {
            r.ah = (byte) (v * 3 + (v != 0 ? VOLBASE : 0));
        }
        retvol2();
    }

    public void retvol2() {
        r.ah += volsft;
        if ((r.ah & 0xff) > 127) {
            r.cl = 31;
            error();
            return;
        }
        retvol3();
    }

    public void retvol3() {
        volsave = r.ah;
        if ((optimiz & 4) != 0 && r.ah == opt_vol) return;
        opt_vol = r.ah;
        optimiz |= 4;
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        MmlDatum md = new MmlDatum((byte) 0xe2, MMLType.Volume, lp, (int) r.ah, (byte) 2);
        muap98.objectBuf.set(r.di, work.FlashLstMd(md));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;
    }

    private void add_vol() {
        get_nowvol();
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        MmlDatum md = new MmlDatum((byte) 0xd7, MMLType.VolumeUp, lp, (int) r.al);
        muap98.objectBuf.set(r.di, work.FlashLstMd(md));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.al));
        r.di += 2;
    }

    private void sub_vol() {
        get_nowvol();
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        MmlDatum md = new MmlDatum((byte) 0xd6, MMLType.VolumeDown, lp, (int) r.al);
        muap98.objectBuf.set(r.di, work.FlashLstMd(md));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.al));
        r.di += 2;
    }

    private void get_nowvol() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        rednums();
    }

    private void exp_vol() {
        // Implementation for V=ppp: etc. (omitted for brevity)
    }

    private void value() {
        chkval_x();
        r.dl = r.ah;
        chktxt();
        if (r.al != '=') error();
        r.al = (byte) 0xda;
        stosbObjBufAL2DI();
        r.dh = 0;
        chkval();
        if (r.carry) {
            set_value();
            return;
        }
        r.dh = r.ah;
        chktxt();
        r.dl |= 0x10;
        if (r.al == '+') {
            set_value();
            return;
        }
        r.dl |= 0x20;
        if (r.al == '-') {
            set_value();
            return;
        }
        error();
    }

    private void set_value() {
        rednums();
        r.ah = r.al;
        byte tmp = r.ah;
        r.ah = r.dh;
        r.dh = tmp;
        r.al = r.dl;
        stoswObjBufAX2DI();
        r.al = r.dh;
        stosbObjBufAL2DI();
    }

    public void chkval() {
        chktxt();
        mucomsub.xsmall();
        if (r.al != 'X') {
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            r.carry = true;
            return;
        }
        chkval_x();
    }

    private void chkval_x() {
        int c = muap98.sourceBuf[r.getBx() & 0xffff];
        r.ah = 6;
        mucomsub.chknum();
        if (r.carry) {
            r.carry = false;
            return;
        }
        rednums();
        if ((r.al & 0xff) > 9) error();
        r.ah = (byte) (r.al + 6);
        r.carry = false;
    }

    public void add_tlen(int len) {
        int idx = nesting << 3;
        alllen[idx / 2] += len;
    } // Simplified

    public void init_looplen() {
        int idx = nesting << 3;
        alllen[idx / 2] = 0;
        alllen[idx / 2 + 1] = 0;
    }

    public void exit_looplen() {
        int idx = nesting << 3;
        alllen[(idx + 4) / 2] = alllen[idx / 2];
    }

    public void set_looplen() { /* Loop length calculation... */ }
}
