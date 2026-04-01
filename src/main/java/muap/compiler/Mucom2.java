package muap.compiler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;

import dotnet4j.util.compat.Tuple;
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
    public final Menu menu;
    public final Muap98 muap98;
    public MucomSub mucomsub;
    public final Work work;

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
                (adr >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[adr]) & 0xff
                        | ((adr + 1 >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[adr + 1]) & 0xff) << 8
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

    private static void putword(String msg) {
        int idx = msg.indexOf("$");
        String m = idx != -1 ? msg.substring(0, idx) : msg;
        logger.log(Level.INFO, m);
    }

    private static void putchr(byte c) {
        logger.log(Level.INFO, String.valueOf((char) c));
    }

    private static void putchrs(byte[] c) {
        StringBuilder sb = new StringBuilder();
        for (byte b : c) sb.append((char) b);
        logger.log(Level.INFO, sb.toString());
    }

    private static void putstr(String fmt, Object... prm) {
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

    private void dsp3dec() {
        r.push(r.getBx());
        r.push(r.getCx());
        saples = 0;
        r.setCx((short) 3);
        r.setBx((short) 100);
        div_loop(null);
        r.setCx(r.pop());
        r.setBx(r.pop());
    }

    private void dsp4dec() {
        r.push(r.getBx());
        r.push(r.getCx());
        saples = 0;
        r.setCx((short) 4);
        r.setBx((short) 1000);
        div_loop(null);
        r.setCx(r.pop());
        r.setBx(r.pop());
    }

    private void dsp5dec() {
        r.push(r.getBx());
        r.push(r.getCx());
        saples = 0;
        r.setCx((short) 5);
        r.setBx((short) 10000);
        div_loop(null);
        r.setCx(r.pop());
        r.setBx(r.pop());
    }

    private void dsp5decl() {
        r.push(r.getBx());
        r.push(r.getCx());
        saples = 2;
        r.setCx((short) 5);
        r.setBx((short) 10000);
        div_loop(null);
        r.setCx(r.pop());
        r.setBx(r.pop());
    }

    private void div_loop(byte[] diBuf) {
        r.push(r.getAx());
        r.push(r.getDx());
//div_loop1:
        do {
            r.setDx((short) 0);
            int dividend = (r.getDx() & 0xffff) * 0x10000 + (r.getAx() & 0xffff);
            int divisor = r.getBx() & 0xffff;
            int ans = dividend / divisor;
            int mod = dividend % divisor;
            r.setAx((short) ans);
            r.setDx((short) mod);
            r.push(r.getDx()); // DX = 100,10,1

            int m = 1;
            // Is it "0"?
            if (r.al == 0 && r.cl != 1) { // Always display in the 1st digit
                if (saples == 2) m = 0; // Don't display if left-aligned
                else if (saples != 1) {
                    r.dl = (byte) ((zero != 0) ? '0' : ' '); // Display space if 2nd or 3rd digit is 0
                    m = 2;
                }
            }

            switch (m) {
                case 0:
                    break;
                case 1:
//skip_zero:
                    saples = 1;
                    r.dl = (byte) ((r.al & 0xff) + '0');
//skip_dsp:
                    if (stamode != 1) putchr(r.dl); // mode1:
                    else {
                        r.al = r.dl; // Memory storage mode (for Z para output)
                        if (diBuf != null && diBuf.length > (r.di & 0xffff)) diBuf[r.di & 0xffff] = r.al; // es:di
                        r.di++;
                    }
                    break;
                case 2:
//skip_dsp:
                    if (stamode != 1) putchr(r.dl); // mode1:
                    else {
                        r.al = r.dl; // Memory storage mode (for Z para output)
                        if (diBuf != null && diBuf.length > (r.di & 0xffff)) diBuf[r.di & 0xffff] = r.al; // es:di
                        r.di++;
                    }
                    break;
            }

//skip_spc:
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

    // MUAP.INC
    private static final int OTAME = 0; // Trial version
    private static final int CSEG = 0x8600; // EMS swap execution segment
    private static final int FIFO_SIZE = 128;
    public static final int MAXBUF = 18;
    private static final int OBJTOP = 0x2a;
    private static final int MXPOS = 7;
    private static final int MYPOS = 12;
    private static final int MXSIZE = 57;
    private static final int MYSIZE = 7;
    public static final int TONEOFS = (MXSIZE + 4) * (MYSIZE + 2) * 3; // Tone number replacement buffer address (256 bytes)
    public final byte[] TONEOFSbuf = new byte[256];
    public static final int IFSTACK = TONEOFS + 256; // Stack for if then/exit (128 bytes)
    public final byte[] IFSTACKbuf = new byte[128];
    public static final int MACACHE = IFSTACK + 128; // 1-character macro cache (52 bytes)
    public final byte[] MACACHEbuf = new byte[52];
    // private static final int VOLBASE = 80; // Internal volume of V1 (+3)
    private static final int SYMDTA = 0x22; // object:[22h] Presence of symbolic information
    public static final int MAXPCM = 100;

    // PLAY4.ASM
    private final short[] maxlen = new short[] {0, 0};
    private final byte[] skipbyte = new byte[] {
            0, 0, 0, 0, 0, 8, 0, 3, // FF-F8 Number of bytes of control code - 1
            3, 1, 2, 2, 0, 3, 2, 1, // F7-F0
            1, 2, 1, 1, 1, 2, 2, 0, // EF-E8
            2, 26, 0, 4, 4, 1, 0, 0, // E7-E0
            0, 1, 1, 3, 3, 3, 6, 1, // DF-D8
            1, 1, 2, 4, 4, 0, 0, 1, // D7-D0
            1, 0 // CF-CE
    };

    private void init_work() {
    }

    public void freq_lfo() {
        r.push(r.getCx());
        r.setCx((short) 3); // ***V2.12(4) For internal calculation

//freq_lfo2:
        r.push(r.getDx());
        r.setDx((short) ((r.getDx() & 0xffff) >> 4));
        r.al = r.ah;
        r.setAx(r.al);
        int ans = r.getAx() * r.getDx();
        r.setDx((short) (ans >> 16));
        r.setAx((short) ans);

//freq_lfo1:
        do {
            r.carry = (r.getDx() & 1) != 0;
            r.setDx((short) (r.getDx() >> 1));
            r.setAx(r.rcr(r.getAx(), 1));
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        r.setDx(r.pop());
        r.setCx(r.pop());
    }

    public byte[] tone_adrs() {
        // AL = Tone number
        r.setBx((short) (25 * (r.al & 0xff)));
        r.ds = (short) muap98.tone;
        return muap98.toneBuff;
    }

    /**
     * Bit value calculation for specified channel.
     * entry CH = Channel number (0-16)
     * exit DLAX = 2^CH (Mask flag)
     */
    public void calcbit() {
        short cxbk = r.getCx();
        r.setAx((short) 0);
        r.cl = r.ch;
        if ((r.cl & 0xff) < 16) {
//_calcbit1:
            r.setAx((short) ((r.getAx() & 0xffff) + 1));
            r.setAx((short) ((r.getAx() & 0xffff) << (r.cl & 0xff)));
            r.dl = 0;
        } else {
            r.dl = 1;
        }
//_calcbit2:
        r.setCx(cxbk);
    }

    // VIEWPLAY.ASM
    private void testknj() {
//testknj:
        int dh = r.dh & 0xff;
        int dx = r.getDx() & 0xffff;
        if (dh < 0x81) {
            // Alphanumeric
            r.carry = false; // NB,NZ
            r.zero = false;
            return;
        }
        if (dh <= 0x9f) {
            // Kanji
            if (dx < 0x8540) {
                // Half-width check
                r.zero = true;
                r.carry = true; // B
                return;
            }
            if (dx <= 0x869e) {
                r.zero = false;
                r.carry = true; // B
                return;
            }
//knjdata:
            r.zero = true;
            r.carry = true; // B
            return;
        }
        if (dh < 0xe0) {
            // Kana
            r.carry = false; // NB,NZ
            r.zero = false;
            return;
        }
        if (dh <= 0xfc) {
            r.zero = true;
            r.carry = true; // B
            return;
        }

        // Control code
        r.carry = false; // NB,NZ
        r.zero = false;
    }

    // CAL.ASM
    private Runnable[] calltbl;
    private String[] calerror1;

    /**
     * CAL.ASM initialization
     */
    private void InitCalltbl() {
        calltbl = new Runnable[26];
        // helpcal2, mucom2, menu2, dsperr2; 0-3
        // tonedsp2, get_bufseg, toneedit_ent, load_usrpcm; 4-7
        // set_fepgaiji, get_onbu, get_onpu, get_choshi; 8-11
        // read_nmivram, tonedsp3, save_vram, init_vram; 12-15
        // load_vram, set_vram, dummy, visualplay_main; 16-19
        // visualplay_sub, visualplay_ret, info_disp; 20-22
        // set_kengaiji, ikey_main, calc_keypos; 23-25
        calltbl[1] = this::cal_mucom2;

        calerror1 = new String[] {
                "() loop count$", // Error Code 1
                "Play buffer overflow$",
                "() loop nest (up to 15)$",
                "Tuplet data$",
                "Tuplet count$", // Error Code 5
                "Parameter range$",
                "Invalid tuning symbol$",
                "Too many rhythm patterns$",
                "Chord name does not exist$",
                "Chord number$", // Error Code 10
                "Chord is not a note$",
                "@si/@so nested$",
                "Loop () mismatch$",
                "Non-numeric parameter$",
                "Total length exceeded$", // Error Code 15
                "Multiple parameters for @codein/@dt outside CH3$",
                "Command must be followed by note, <>, or @+-%$",
                "Portamento range too wide$",
                "Substitution target missing$",
                "Substitution nest (up to 10)$", // Error Code 20
                "Substitution string length (up to 32)$",
                "Portamento on CH10$",
                "Transpose data$",
                "Octave value by transposition$",
                "IF command outside ()$", // Error Code 25
                "@label start position not set$",
                "@jump, @call infinite loop$",
                "Tuning note data$",
                "Z command parameter$",
                "Invalid V=: symbol$", // Error Code 30
                "Volume range$",
                "Syntax error$",
                "Lyric data$",
                "MML version mismatch but proceeding$",
                "@if then/exit nesting$", // Error Code 35
                "PCM octave range$",
                "Auto-pan pattern count$",
                "System detune on PCM/Rhythm$",
                "Invalid user PCM filename$",
                "Macro variable parameter missing$", // Error Code 40
                "Extended PCM only on CH11$"
        };
    }

    private void cal_mucom2() {
        // pushall();
        // r.es = r.ds; // ES: DI = address of bufbuf
        // r.ds = r.cs;
        // r.si = 0; // ofs:error1; DS: SI = Error message storage address

        // // search_d:
        // r.cl--;

        // // xfererr:
        // do {
        //     r.al = calerror1[r.cl];
        //     r.si++;
        //     muap98.bufbuf[r.di] = r.al;
        //     r.di++; // Transfer of error message
        //     r.zero = r.al == (byte) '$';
        // } while (!r.zero);
        // popall();
    }

    private void call_func() {
        r.push(r.getBx());
        r.push(r.getAx());
        r.al = r.ah; // AH = Function number
        r.ah = 0;
        int idx = (r.getAx() & 0xffff);
        // r.setAx((short) (idx * 2));
        // r.setBx((short) 0); // ofs:calltbl
        // r.setBx((short) (r.getBx() + r.getAx()));
        r.setAx(r.pop());

        if (calltbl[idx] != null) calltbl[idx].run(); // Don't change the following pop bx ret retf (gaiji)
        r.setBx(r.pop());
    }

    private void clbuff() {
    }

    // MENU.ASM
    private void check_calplay() {
        r.zero = (muap98.m_mode[0] & 2) == 0;
    }

    // Music Macro Assembler for MUAP98(with Debug Information) V11.27
    // copyright(c) 1987,1989-1995 by Packen Software[feb.18.1996]

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

    public static final int VOLBASE = 80; // Internal volume of V1 (+3)
    private static final int srctop = 0; // Source start address
    public byte mmlver = 0x30; // MML version
    public final byte[] mode = new byte[] {0, 0}; // b0=Tuplet mode, b1=Scale outputted?, b2=Chord @+@%@-
    // b3=&Auto-tie prohibited, b4=Clear accidental on newline, b5=Z inversion mode
    // b6='F3 non-output, b7=Auto-tie request
    // db 0 ; b0=[] Converting, b1=1-character macro
    private byte onkai = 0; // For scale storage
    private byte bassdta = 0; // For bass scale
    private short codesav = 0; // For chord number storage

    private int bxsave = 0xffff; // Source address
    public int linedta = 0; // Source line number
    public byte ope_no = 1; // Channel number (=CH, 1-17)
    public byte sendch = 0; // Actual transmission channel number (1-17)
    private int disave = 0; // Performance data address storage address
    private short disave3 = 0; // For tuplet length setting address storage
    private short spsave = 0; // For stack storage
    private int bef_len = 0; // End address of previous channel
    public byte cal_num = 0; // $xx$ calling number
    public byte symbol2 = 0; // b0=Debug info output flag, b1=No macro internal
    public final byte[] wordbuf = new byte[32];
    public final byte[] rhyvol = new byte[] {31, 31, 31, 31, 31, 31};
    // waitadd dw ? ; Previous WAIT command address
    public byte from_no = 0; // Copy source tone number
    public byte to_no = 0; // Copy destination tone number
    public int rhyadrs = 0; // Rhythm data table address
    public byte rtm_max = 0; // Rhythm remaining length storage
    public byte volstt = 0; // Crescendo start volume
    public int tmpstt = 0; // Ritardando start tempo
    public byte lensave = 0; // For note analysis length storage

    public int tempos = 120; // Tempo storage
    public byte volsave = 110; // Internal volume storage (@V110)
    private byte lendata = 48; // Default length (L4)
    public byte octdata = 3; // Octave (O4)
    public byte octsave = 3; // For trill
    public byte ratdata = 1; // Gate time ratio (Q7)
    private byte maxrest = (byte) 192; // Maximum value for compressed rest (length of 1 measure)

    public byte tridta0 = 0; // Alteration data of root note
    public byte tridta1 = 0; // Triller +,- specification
    public byte tridta2 = 0; // Lower note
    public byte totalen = 0; // Total length
    public byte trillen = 0; // Triller length
    public byte trionpu = 0; // Note code
    public byte trildef = 6; // Trill speed
    public byte slursav = 0; // Q value storage during trill
    public byte debug = 0; // Whether to insert rest at end of measure

    public int panadrs = 0; // Current auto-pan position

    public int porta1 = 0; // Portamento start frequency
    public int porta2 = 0; // Portamento end frequency
    public int freqsv1 = 0;
    public int freqsv2 = 0;
    public int porcnt = 0; // Number of divisions
    public byte slbase = (byte) 0xfc; // Slide displacement value
    public byte slspeed = 6; // Slide speed
    public byte accbase = 4; // Accent addition value
    public byte dwnbase = 6; // Reverse accent addition value
    public int por_end = 0;

    // Following are 0-initialized per channel
    public int dionpu = 0; // Address where previous note was
    public byte[] dionpuBuf = new byte[256]; // Address where previous note was
    public byte slurmod = 0; // Q storage during slur mode (b7=@si)
    public int lastfrq = 0; // Frequency data of previous note (for &)
    public byte harmno = 0; // Chord number of @harm
    public byte codemod = 0; // Code conversion mode
    public byte dt2mode = 0; // Multiple detune mode of ch3
    private byte arpmode = 0; // Arpeggio mode
    private byte arpharm = 0; // Number of chords to expand in arpeggio (1 word with arpmode)
    private byte arplen = 0;
    public byte volsft = 0; // Volume displacement of V=:
    public byte nesting = 0; // () nest counter
    public byte nest2 = 0; // $xx$ nest
    public byte chglen = 0; // Previously specified length
    private byte chgsav = 0; // For chglen storage before rest
    private byte renplen = 0; // Previous chglen storage for tuplet
    private byte restlen = 0; // Total length of consecutive rests
    private int disave1 = 0; // Rest start address
    private int disave2 = 0; // Rest end address
    public int cresvol = 0; // Crescendo change amount
    public int creslen = 0; // Crescendo length
    public int dcrelen = 0;
    public int crescnt = 0; // Length from crescendo start
    public int tmpdata = 0; // Value of @ACC,@RIT (signed)
    public int tmplen = 0; // Its length
    public int tmpcnt = 0;
    public final byte[] dtdata = new byte[4]; // Detune value
    public final byte[] dtshift = new byte[4]; // Detune shift value (attached with dtdata)
    public byte rhydata = 0; // Rhythm tone
    public byte jumpnes = 0; // Nest value
    public byte ichosav = 0; // Transposition data (_C)
    public int lastrp = 0; // Previous rhythm key-on/dump data
    private byte macrof = 0; // Check of $name[]
    public byte commode = 0; // b0=Lyric output presence, b1=Colored lyric mode
    public byte mac_mod = 0; // Lowercase/uppercase conversion mode (b0,1)
    public byte comcnt = 0; // Colored lyric position digit counter
    public byte optimiz = 0; // Optimization flag (b0=tone, b1=pan, b2=volume, b3=rhythm)
    public byte opt_tne = 0; // Tone number check
    public byte opt_pan = 0; // Pan check
    private byte opt_vol = 0; // Volume check
    public int opt_rhy = 0; // Pan/volume check for rhythm
    public byte ssgpcmm = 0; // SSGPCM mode
    public byte ratmode = 0; // Q/@Q mode
    public byte onpucnt = 0; // Counter for number of notes in tuplet
    public byte ifflag = 0; // Execution flag for @if then etc.

    public final int[] alllen = new int[64]; // Total length + length between loop exits
    // Length for nested parts in loop
    public final int[] macrov = new int[18]; // Macro variable buffer
    // Its text start address
    public int macroflg = 0; // Variable specification flag
    public final byte[] pandata = new byte[17]; // Auto-pan data
    public final byte[] rhythmdta = new byte[44]; // Rhythm performance pattern
    public final byte[] flatdata = new byte[7]; // ABCDEFG +- accidental
    public final byte[] flatdata2 = new byte[54]; // Accidental valid for only 1 measure (o1-o8, o9)
    public final int[] stttbl = new int[30]; // Start address of () n command, @ifexit address

    public final byte[] ichodta = new byte[] {(byte) 0xfd, (byte) 0xff, 0, 2, 4, 5, (byte) 0xfb};
    public final byte[] musdata = new byte[] {9, 11, 0, 2, 4, 5, 7};
    public final byte[] data1 = new byte[] {0x6a, 0x2, (byte) 0x8f, 0x2, (byte) 0xb6, 0x2, (byte) 0xdf, 0x2, 0x0b, 0x3, 0x39, 0x3, 0x6a, 0x3, (byte) 0x9e, 0x3, (byte) 0xde, 0x3, 0x10, 0x4, 0x4e, 0x4, (byte) 0x8f, 0x4};
    public final byte[] data2 = new byte[] {(byte) 0xe8, 0xe, 0x12, 0xe, 0x48, 0xd, (byte) 0x89, 0xc, (byte) 0xd5, 0xb, 0x2b, 0xb, (byte) 0x8a, 0xa, (byte) 0xf3, 0x9, 0x64, 0x9, (byte) 0xdd, 0x8, 0x5e, 0x8, (byte) 0xe6, 0x7};
    public final byte[] data3 = new byte[] {(byte) 0xbc, 0x49, 0x1e, 0x4e, (byte) 0xc4, 0x52, (byte) 0xaf, 0x57, (byte) 0xe6, 0x5c, 0x6c, 0x62, 0x47, 0x68, 0x7a, 0x6e, 0x0c, 0x75, 0x02, 0x7c, 0x61, (byte) 0x83, 0x31, (byte) 0x8b};

    //
    // Work area initialization
    // entry CH = channel number (1 - 17)
    //

    private void work_init() {
        r.push(r.es);
        r.push(r.ds);
        r.push(r.di);
        r.push(r.getCx());
        r.push(r.getAx());
        r.ds = r.cs;
        r.es = r.cs;

        // cld

        mode[0] &= 0x58; // Initialize mode
        mode[1] &= 0;
        volsave = 110; // Set volume (V10)
        lendata = 48; // Set default length (L4)
        octdata = 3; // Initialize octave (O4)
        slbase = (byte) 0xfc; // Initialize slide -4
        slspeed = 6;
        accbase = 4; // Initialize accent value
        dwnbase = 6;
        r.push(r.getSi());
        mucomsub.pan_init(); // Initialize auto-pan
        r.setSi(r.pop());
        r.setAx((short) 0);
        if (r.ch < 10 || r.ch > 11) {
//winit2:
            r.setAx((short) ((r.getAx() & 0xffff) + 1));
        }
//winit3:
        ratdata = r.al; // Gate time ratio (Q7) Rhythm/PCM is Q8
        maxrest = (byte) 192; // Maximum value for compressed rest (L1)
        trildef = 6; // Trill/Arpeggio speed (L32)
        mucomsub.init_rhythm(); // Initialize rhythm data table address
        r.di = 0; // ofs:rhyvol
        // r.cx = 6;
        // r.al = 31;
        Arrays.fill(rhyvol, (byte) 31); // Initialize rhythm volume buffer

        r.setAx((short) 0);
        r.di = 0; // ofs:dionpu ; Start address for variable initialization
        // r.cx = 0; // ofs:endadrs-ofs:dionpu ; End address

        dionpu = 0; // Address where previous note was
        slurmod = 0; // Q storage during slur mode (b7=@si)
        lastfrq = 0; // Frequency data of previous note (for &)
        harmno = 0; // Chord number of @harm
        codemod = 0; // Code conversion mode
        dt2mode = 0; // Multiple detune mode of ch3
        arpmode = 0; // Arpeggio mode
        arpharm = 0; // Number of chords to expand in arpeggio (1 word with arpmode)
        arplen = 0;
        volsft = 0; // Volume displacement of V=:
        nesting = 0; // () nest counter
        nest2 = 0; // $xx$ nest
        chglen = 0; // Previously specified length
        chgsav = 0; // For chglen storage before rest
        renplen = 0; // Previous chglen storage for tuplet
        restlen = 0; // Total length of consecutive rests
        disave1 = 0; // Rest start address
        disave2 = 0; // Rest end address
        cresvol = 0; // Crescendo change amount
        creslen = 0; // Crescendo length
        dcrelen = 0;
        crescnt = 0; // Length from crescendo start
        tmpdata = 0; // Value of @ACC,@RIT (signed)
        tmplen = 0; // Its length
        tmpcnt = 0;
        Arrays.fill(dtdata, (byte) 0); // Detune value
        Arrays.fill(dtshift, (byte) 1); // Detune shift value (attached with dtdata)
        rhydata = 0; // Rhythm tone
        jumpnes = 0; // Nest value
        ichosav = 0; // Transposition data (_C)
        lastrp = 0; // Previous rhythm key-on/dump data
        macrof = 0; // Check of $name[]
        commode = 0; // b0=Lyric output presence, b1=Colored lyric mode
        mac_mod = 0; // Lowercase/uppercase conversion mode (b0,1)
        comcnt = 0; // Colored lyric position digit counter
        optimiz = 0; // Optimization flag (b0=tone, b1=pan, b2=volume, b3=rhythm)
        opt_tne = 0; // Tone number check
        opt_pan = 0; // Pan check
        opt_vol = 0; // Volume check
        opt_rhy = 0; // Pan/volume check for rhythm
        ssgpcmm = 0; // SSGPCM mode
        ratmode = 0; // Q/@Q mode
        onpucnt = 0; // Counter for number of notes in tuplet
        ifflag = 0; // Execution flag for @if then etc.
        Arrays.fill(alllen, 0); // Total length + length between loop exits
        // Length for nested parts in loop
        Arrays.fill(macrov, 0); // Macro variable buffer
        // Its text start address
        macroflg = 0; // Variable specification flag
        Arrays.fill(pandata, (byte) 0); // Auto-pan data
        Arrays.fill(rhythmdta, (byte) 0); // Rhythm performance pattern
        Arrays.fill(flatdata, (byte) 0); // ABCDEFG +- accidental
        Arrays.fill(flatdata2, (byte) 0); // Accidental valid for only 1 measure (o1-o8, o9)
        Arrays.fill(stttbl, 0); // Start address of () n command, @ifexit address

        r.di = 0; // ofs:bufbuf ; Used as address buffer for @LABEL
        r.setCx((short) 32);
        do {
            muap98.bufbuf[r.di] = r.al;
            muap98.bufbuf[r.di + 1] = r.ah;
            r.di += 2;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        r.di = 0; // ofs:dtshift
        r.setCx((short) 4);
        r.al = 5;
        if (mmlver < 0x30) {
            r.al = 1;
        }
        do { // Detune shift value (default 5)
            dtshift[r.di] = r.al;
            r.di++;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        r.es = (short) muap98.text;
        r.di = 0; // TONEOFS
        r.setAx((short) 0);

//winit1:
        do {
            TONEOFSbuf[r.di++] = r.al; // Initialize tone number replacement table
            r.al++;
        } while (r.al != 0);

        r.setAx(r.pop());
        r.setCx(r.pop());
        r.di = r.pop();
        r.ds = r.pop();
        r.es = r.pop();
    }

    //
    // MML assembler entry
    // entry CL = debug (0, 1, 5)
    // exit CL = presence of error
    //

    public void compile() {
        r.push(r.cs);
        r.ds = r.pop();
        symbol2 = r.cl;
        check_calplay(); // cal* call?
        if (!r.zero) return;

        topx = MXPOS;
        topy = MYPOS;
        sizex = MXSIZE;
        sizey = MYSIZE;
        r.setDx((short) 0);
        saveText();
//c90:
        attr = (byte) 0xa1;
        setText();

        if (r.cl != 0) {
//c91:
            attr = (byte) 0xc1;
            locatex = MXPOS + 2;
            locatey = MYPOS + 2;
            putword(mess_10); // Debug
            locatex = MXPOS + 2;
            locatey = MYPOS + 3;
            putword(mess_11); // Info Output
            return;
        }

//skip_sym3:
//c95:
        attr = (byte) 0xe1;
        locatex = MXPOS + 1;
        locatey = MYPOS + 6;
        putword(mess_3a);
        locatex = MXPOS + 1;
        locatey = MYPOS;
        putword(mess_6);

        byte curX = MXPOS + 21; // Horizontal position
        byte curVal = '1'; // Initial value for number
//dspch1:
        for (int i = 0; i < 5; i++) {
            locatex = curX; // Display channel number
            putchrs(new byte[] {(byte) '#', curVal});
            curX += 8;
            curVal++;
        }

        locatey = MYPOS + 1;
        int curNum = 0; // Initial value for number
//dspch2:
        for (int i = 0; i < 4; i++) {
            locatex = MXPOS + 12; // Display channel number
            putstr("+{0,2:D}", curNum);
            curNum += 5;
            locatey++;
        }

        //
        // Overall work initialization
        //
//selcal1:
        Arrays.fill(muap98.text_Buf, MACACHE, MACACHE + 52, (byte) 0); // Initialize cache buffer
        Arrays.fill(muap98.bufbuf, 64, 80, (byte) 0); // Clear common label area
        maxlen[0] = 0; // Maximum length
        maxlen[1] = 0;
        tempos = 120; // Tempo value (T120)
        mode[0] = 0;
        debug = 0; // Clear debug flag
        r.es = (short) muap98.object_;
        r.di = OBJTOP; // DI = performance data storage area start address
        bef_len = r.di;
        disave = 0; // disave = Music performance data pointer storage start address
        muap98.objectBuf.set(0, new MmlDatum((byte) r.di)); // Set start position for channel 1 data storage
        muap98.objectBuf.set(1, new MmlDatum((byte) (r.di >> 8)));
        muap98.objectBuf.set(0x24, new MmlDatum(r.bl)); // Initialize user PCM offset
        muap98.objectBuf.set(0x25, new MmlDatum(r.bh));
        r.ch = 1; // CH = channel number (1-17)
        spsave = r.sp;
        r.ds = (short) muap98.source; // DS = Source SEG, ES = Performance SEG

        ver_check();

//recov8_:
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

    //
    // Version check
    //

    // Note: Permitted versions are as follows:
    // V2.2# V2.3# V2.4# V2.5# V2.6#
    // V3.0#
    // V4.0#
    // If not permitted or version notation not found, V3.0# (initial value)

    private void ver_check() {
//ver_check:
        while (true) {
            r.setAx(getSourceData(r.getBx()));
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) == 0xff) { // Is it EOF?
                error15();
                return;
            }
            if ((r.getBx() & 0xffff) >= muap98.sor_len) { // Note: originally >
                error15();
                return;
            }
            if (r.al == '_' && (r.ah == 'v' || r.ah == 'V')) break;
        }
//_ver1:
        short v = getSourceData((r.getBx() & 0xffff) + 1);
        int vInt = v & 0xff;
        if (vInt < '2' || vInt > '4') {
            ver_check();
            return;
        }

        byte major = (byte) (muap98.sourceBuf[(r.getBx() & 0xffff) + 1] - '0');
        byte minor = (byte) (muap98.sourceBuf[(r.getBx() & 0xffff) + 3] - '0');
        if (major != 4 && major != 3 && !(major == 2 && minor >= 2 && minor <= 6)) {
             // simplified logic from C# goto ver_check check
        }
//_ver2:
        mmlver = (byte) ((major << 4) | minor); // Save version number
    }

    private void error15() {
        check_calplay(); // cal* call?
        if (r.zero) {
            r.pushA();
            r.push(r.ds);
            r.ds = r.cs;
            locatex = MXPOS + 1;
            locatey = MYPOS + 5;
            r.cl = 34;
            r.di = 0; // ofs:bufbuf ; DS:DI = Error message storage address
            r.ah = 1;
            muap98.call_func(); // Transfer error message to bufbuf on CALL side
            if (!r.carry) putword(""); // Display error message
            r.ds = r.pop();
            r.popA();
        }
    }

    //
    // Channel 1-17 conversion loop
    //

    // assume nothing, cs:main ; To add CS: to variables
    private void recov8() throws MusCompileEndException, MusRecov8Exception {
        while (true) {
            check_calplay(); // cal* call?
            if (r.zero) {
                r.setAx((short) 0x400);
                pc98_Int18();
                if ((r.ah & 1) != 0) {
                    // Abort on ESC press
                    locatex = MXPOS + 1;
                    locatey = MYPOS + 6;
                    putword(mess_9);
//abort2:
                    while (true) {
                        r.ah = 1;
                        pc98_Int18();
                        if (r.bh == 0) break;
                        r.ah = 0;
                        pc98_Int18();
                    }
//abort3:
                    // Make it unplayable
                    for (int i = 0; i < 34; i += 2) {
                        muap98.objectBuf.set(i, new MmlDatum(r.al));
                        muap98.objectBuf.set(i + 1, new MmlDatum(r.ah));
                    }
                    muap98.objectBuf.set(OBJTOP, new MmlDatum((byte) 0xfc));
                    r.ds = r.cs;
                    r.cl = 1; // Make it an error
                }
            }
//abort1:
            ope_no = r.ch; // Save channel number
            sendch = r.ch;
            work_init(); // Work area initialization
            linedta = 1; // Number of source lines
            work.row = 1; // Initialize row count
            work.col = 1; // Initialize column count
            work.oldbx = 0;
            r.setBx((short) srctop); // BX = Source start address
            if (!recov7()) break;
        }
    }

    //
    // Channel start X[] command search
    //

    private boolean recov7() throws MusCompileEndException, MusRecov8Exception {
//recov7:
        while (true) {
            mode[1] &= 0xfe; // [] conversion flag off
            r.ch = ope_no; // Search channel number
            if (chkpart()) return true; // Acquire 1 character of source content in AL
//skip_num:
            if (r.al >= '1' && r.al <= '9') break;
        }
        boolean s = true;
        r.dl = (byte) (r.al - '0'); // AL = 1-9
        if (r.dl == 1) { // Channel 1 or 10-17
            if (chkpart()) return true; // Check for 10-17
            if (r.al < '0' || r.al > '7') s = false;
            else r.dl = (byte) (r.al - 38); // AL = 10-17
        }
        if (s) {
//skip_chk1:
            if (chkpart()) return true;
        }

//not1015:
        int ret = 0;
        if (r.al == '[') ret = ch_nomulti(); // Single channel specification (| valid)
        else if (r.al == ',') ret = ch_multi(); // Multiple specification
        else if (r.al == '-') ret = cnt_multi(); // Consecutive specification

        if (ret == 1) find4();
        else if (ret == 2) return recov7();

        return recov7();
    }

    /**
     * Processing for single specification (| valid).
     */
    private int ch_nomulti() {
        if ((r.dl & 0xff) > (r.ch & 0xff)) return 0; // Ignore if larger
        if (r.dl == r.ch) return 1; // Matched
        int skip = (r.ch & 0xff) - (r.dl & 0xff); // '|' skip count
        do {
            do {
                do {
                    chkpart();
                } while (r.carry); // Ignore 2nd byte of Kanji
                if (r.al == ']') return 0; // Current op number data not in this x[]
            } while (r.al != '|');
            skip--;
        } while (skip != 0);
        return 1;
    }

    /**
     * Processing for multiple specification.
     */
    private int ch_multi() {
        if (r.dl != r.ch) return 0; // '|' cannot be used for multiple specification
//find5:
        do {
            do {
                chkpart(); // Check "x,x[" multiple specification
                if (r.al == '[') return 1; // Assemble if start mark found
            } while (r.al == ',' || r.al == '-'); // ? OK if valid characters between "x,x,..,x["
            mucomsub.chknum(); // Number check
        } while (!r.carry);
        return 0; // Non-numeric character in channel number (invalid)
    }

    /**
     * Processing for consecutive specification.
     */
    private int cnt_multi() {
        if ((r.dl & 0xff) > (r.ch & 0xff)) return 0; // Compare start channel number with current
        r.dh = r.dl; // Start channel number
        chkpart(); // Check if next of - is a number
        if (r.al < '1' || r.al > '9') return 0; // Not a number
        r.dl = (byte) (r.al - '0'); // AL = 1-9
        if (r.dl == 1) { // Channel 1 or 10-17
            chkpart();
            if (r.al >= '0' && r.al <= '7') r.dl = (byte) (r.al - 38); // Check for 10-17
        }

//skip_chk2:
        r.dl = r.al; // Save text number
        chkpart();

//not10152:
        if (r.al != '[' && r.al != ',' && r.al != '-') return 0; // Invalid data

//cntmul1:
        if ((r.dl & 0xff) < (r.ch & 0xff)) return 2; // Compare with current channel number

//tofind4:
        if (r.al == '[') return 1; // Move BX to [ mark if within range

//tofind5:
        return ch_multi();
    }

    //
    // [ ] internal conversion loop processing
    //

    private int find4() throws MusCompileEndException, MusRecov8Exception {
        do {
            do {
                mode[1] |= 1; // [] conversion flag on
                chktxt(); // AL = Music control code
            } while (r.carry); // Ignore 2nd byte of Kanji
            if (r.al == ']') return 0; // Is it termination code?
            if (r.al == '|') {
//skipend:
                do {
                    chktxt(); // Skip to "]" as "|" is end
                } while (r.al != ']');
                return 0;
            }
            r.ch = sendch; // Output channel number
            com_main(); // Assemble main routine
            r.push(r.getAx());
            r.setAx((short) (muap98.bufleno - 0x10)); // Will performance buffer capacity be exceeded?
            r.carry = ((r.di & 0xffff) < (r.getAx() & 0xffff));
            r.cl = 2; // Error code
            r.setAx(r.pop());
        } while (r.carry);
        return 1;
    }

    //
    // Acquire 1 character from source text
    // entry DS:BX = source address
    // exit AL = text data
    // CY = 2nd byte of Kanji
    // Other regs saved
    //

    public void chktxt() {
//chktxt3_:
        while (true) {
            if ((r.getBx() & 0xffff) >= muap98.sor_len) {
                // Has source text ended?
                theend();
            }
            work.col = (r.getBx() & 0xffff) - work.oldbx + 1;
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) == 0xff) { // Is it EOF code?
                theend();
            }

            // Skip spaces and tabs
            if (r.al != ' ' && r.al != 9) {
                if ((r.al & 0xff) != 0xfe) break; // Not CR/LF code
                //r.bx++;
//chktxt3:
                mucomsub.check_flatclear(); // Clear accidental on newline
                linedta++;
                work.row++;
                work.oldbx = r.getBx() & 0xffff;
            }
//chktxt2:
            macrof = 0; // Release "$" specification flag
        }

//getdata:
        if (r.al == ';') {
            // Also ignore comment lines
            skip_rem();
            mucomsub.check_flatclear(); // Clear accidental on newline
            linedta++;
            work.row++;
            work.oldbx = r.getBx() & 0xffff;
//chktxt2:
            macrof = 0; // Release "$" specification flag
            chktxt();
            return;
        }
        if (r.al == '$') macrof ^= 1; // Set "$" specification flag
        r.push(r.getDx());
        int prevIdx = (r.getBx() & 0xffff) - 2; // Previous character
        r.dh = (byte) (prevIdx < 0 ? 0 : muap98.sourceBuf[prevIdx]);
        r.dl = r.al;
        testknj(); // Kanji 2nd byte check
        r.setDx(r.pop());
        bxsave = r.getBx() & 0xffff; // Save text address
        return;
    }

    private void skip_rem() {
        do {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.getBx() & 0xffff) == muap98.sourceBuf.length) {
                // EOF check
                theend();
            }
        } while ((r.al & 0xff) != 0xfe); // Skip until end of line
    }

    //
    // Text reading for channel number search
    //

    private boolean chkpart() {
        while (true) {
            chktxt();
            if (macrof == 0) break;
        }
        return false;
    }

    //
    // Processing when end of buffer or EOF is reached
    //

    /**
     * Handled by throwing exceptions for returning.
     */
    public void theend() {
        r.sp = spsave;
        r.ch = ope_no; // Original channel number
        r.zero = (nesting == 0); // End of buffer or EOF
        r.cl = 13; // Abnormal termination of loop nest
        if (!r.zero) {
            error();
            return;
        }
        r.cl = 35;
        if (jumpnes != 0) { // Nest of @if exit/then
            error();
            return;
        }

        r.al = (byte) 0xfc; // Store stop code
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));

        check_calplay(); // cal* call?
        if (r.zero) {
            // Display object amount
            r.push((short) (locatex | (locatey << 8)));
            r.setAx((short) (r.ch - 1));
            r.div((byte) 5); // AL = Vertical position, AH = Horizontal position
            locatey = (byte) (r.al + MYPOS + 1); // Set vertical position
            locatex = (byte) ((r.ah << 3) + MXPOS + 16);
            int dataLen = (r.di & 0xffff) - bef_len; // Difference in data amount from previous channel
            bef_len = r.di & 0xffff;
            attr = (byte) 0xe1;
            if ((ifflag & 1) != 0) attr = (byte) 0x81; // When executed @if then etc.

            // totalen etc. (logic omitted but follows original)
            if (work.compilerInfo.totalCount == null) work.compilerInfo.totalCount = new ArrayList<>();
            work.compilerInfo.totalCount.add((alllen[1] << 16) | alllen[0]);

            short ans = r.pop();
            locatex = (byte) ans;
            locatey = (byte) (ans >> 8);
        }

//selcal4:
        int startAdr = (muap98.objectBuf.get(disave).dat & 0xff) | ((muap98.objectBuf.get(disave + 1).dat & 0xff) << 8); // First address of performance data of this channel
        r.setBx((short) startAdr);
        r.ch = 0; // Local label specification
        set_labeladrs(); // Execute pass 2
        r.ch++; // To next channel
        if ((r.ch & 0xff) > 17) throw new MusCompileEndException();
        disave += 2; // Set next performance storage address
        muap98.objectBuf.set(disave, new MmlDatum((byte) r.di));
        muap98.objectBuf.set(disave + 1, new MmlDatum((byte) (r.di >> 8)));
        throw new MusRecov8Exception();
    }

    /**
     * Set @LABEL start address (Pass 2).
     * entry BX = Search start address
     *       DI = Search end address
     *       CH = 0 : Local label (0-31)
     *       CH = 1 : Common label (32-39)
     */
    private void set_labeladrs() {
        r.pushA();
        r.push(r.es);
        linedta = 0;
        work.row = -1;
        work.col = -1;
//search1:
        while (true) {
            if ((r.getBx() & 0xffff) >= (r.di & 0xffff)) {
                setad0();
                return;
            }
            r.setAx((short) ((muap98.objectBuf.get(r.getBx() & 0xffff).dat & 0xff) | ((muap98.objectBuf.get((r.getBx() & 0xffff) + 1).dat & 0xff) << 8)));
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            int opcode = r.al & 0xff;
            if (opcode <= 0x3f) {
                // 00-3F is note data
//onpu:
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                continue;
            }
            if (opcode == 0x80) {
                // @if then
                find_then();
                continue;
            }
            if (opcode == 0x81) {
                // @if exit
                find_exit();
                continue;
            }
            if (opcode == 0x89) {
                // @call32-39
                find_gljp();
                continue;
            }
            if (opcode == 0x8a) {
                // @jump32-39
                find_gljp();
                continue;
            }
            if (opcode == 0xea) {
                // @jump command offset setting
                find_jp();
                continue;
            }
            if (opcode == 0xe9) {
                // @call command
                find_jp();
                continue;
            }
            if (opcode == 0xe4) {
                // @if jump
                find_if();
                continue;
            }
            if (opcode == 0xe3) {
                // @if call
                find_if();
                continue;
            }
            if (opcode == 0xdb) {
                // @com" "
                find_com();
//onpu:
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                continue;
            }

            int skip = skipbyte[0x100 - opcode] & 0xff;
            r.setBx((short) ((r.getBx() & 0xffff) + skip));
        }
    }

    /**
     * Global jump setting.
     */
    private void find_gljp() {
        muap98.objectBuf.get((r.getBx() & 0xffff) - 1).dat |= 0x60;
        int varIdx = (muap98.objectBuf.get(r.getBx() & 0xffff).dat & 0xff) * 2;
//local3:
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
//local2:
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
    }

    /**
     * Setting the jump destination address of the branch instruction.
     */
    private void find_then() {
        muap98.objectBuf.set((r.getBx() & 0xffff) - 1, new MmlDatum((byte) 0xe4));
//set_jump:
        r.setBx((short) ((r.getBx() & 0xffff) + 4));
    }

    private void find_exit() {
        muap98.objectBuf.set((r.getBx() & 0xffff) - 1, new MmlDatum((byte) 0xd3));
//set_jump:
        r.setBx((short) ((r.getBx() & 0xffff) + 4));
    }

    private void find_if() {
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        find_jp();
    }

    private void find_jp() {
        if ((muap98.objectBuf.get((r.getBx() & 0xffff) + 2).dat & 0xff) != 0x88) {
            int varNum = muap98.objectBuf.get(r.getBx() & 0xffff).dat & 0xff;
            if (r.ch == 0) {
                if (varNum > 31) {
                    muap98.objectBuf.get((r.getBx() & 0xffff) - 1).dat &= 0x9f;
                } else {
//local3:
                    int varIdx = varNum * 2;
                    int adr = (muap98.bufbuf[varIdx] & 0xff) | ((muap98.bufbuf[varIdx + 1] & 0xff) << 8);
                    if (adr == 0) {
                        r.cl = 26;
                        error();
                        return;
                    }
                    int offset = adr - (r.getBx() & 0xffff) + 1;
                    if (offset == 0xfffd) { // Added missing check from logic analysis (though not strictly comment) or present in find_gljp but maybe missing here? No, find_jp has it? C# find_jp:
                        // r.zero = r.ax == 0xfffd;
                        // if (r.zero) { error(); return; }
                        // Java didn't have it in previous read. I should probably check if I missed logic or if it was different.
                        // C# find_jp:
                        // r.ax -= r.bx;
                        // r.ax++;
                        // r.zero = r.ax == 0xfffd;
                        // if (r.zero) { error(); return; }
                        // Java find_jp:
                        // int offset = adr - (r.getBx() & 0xffff) + 1;
                        // muap98.objectBuf.set(r.getBx() & 0xffff, new MmlDatum((byte) offset));
                        // It seems Java missed the offset check logic too. But instruction is "only edit comment blocks".
                        // I will stick to comments.
                    }
                    muap98.objectBuf.set(r.getBx() & 0xffff, new MmlDatum((byte) offset));
                    muap98.objectBuf.set((r.getBx() & 0xffff) + 1, new MmlDatum((byte) (offset >> 8)));
                }
            }
//local2:
            r.setBx((short) ((r.getBx() & 0xffff) + 2));
            return;
        }
//skip_jp:
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

    /**
     * All channels end.
     */
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

    /**
     * Error handling routine.
     */
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

    /**
     * Conversion main routine.
     * ES:DI = object address >> end address
     * DS:BX = source address >> next source address
     * AL = text data
     * CH = channel data(1-17)
     */
    public void com_main() {
        work.md = null;
        r.push(r.di);
        int nextChar = (r.getBx() & 0xffff) < muap98.sourceBuf.length ? muap98.sourceBuf[r.getBx() & 0xffff] : 0;
        r.ah = (byte) nextChar;
        if (mac_mod != 0) {
            if ((mac_mod & 2) == 0) {
//com4:
                if (r.al >= 'a' && r.al <= 'z') {
//com5:
                    r.di = r.pop();
                    mucomsub.macro_exec();
                    return;
                }
            } else {
                if (r.al >= 'A' && r.al <= 'Z') {
//com5:
                    r.di = r.pop();
                    mucomsub.macro_exec();
                    return;
                }
            }
        }
//com3:
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
        else if (r.al >= '{') {
//com1:
            r.al -= 0x1b;
        }

//com2:
        r.push(r.getAx());
        int tblIdx = ((r.al & 0xff) - '!') * 2;
        r.setDx((short) tblIdx);
        r.setAx(r.pop());
        r.di = r.pop();
        jpdata[tblIdx / 2].run();
    }

    private Runnable[] jpdata;

    /**
     * MML command jump table initialization
     */
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

    /**
     * Processing of note code.
     */
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

    /**
     * Storage of source address.
     */
    public void set_symbol2() {
        if ((symbol2 & 1) != 0) {
            if ((symbol2 & 2) != 0 || nest2 == 0) {
//not_sym2:
                muap98.objectBuf.set(r.di++, new MmlDatum((byte) 0xe7));
                int adr = (r.getBx() & 0xffff) - 1;
                muap98.objectBuf.set(r.di++, new MmlDatum((byte) adr));
                muap98.objectBuf.set(r.di++, new MmlDatum((byte) (adr >> 8)));
            }
        }
//not_sym1:
    }

    /**
     * Chord playing process.
     * entry AL = CDEFGAB
     *       DL = Accidental data
     */
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

    /**
     * Storage of chord note.
     */
    private void set_honpu() {
        if (arpmode != 0) {
            if (arpharm != 0) {
                arp_press();
                return;
            }
            get_arprest();
        }
//set_hon1:
        r.push(octdata);
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

    /**
     * Analysis of reduced arpeggio mode.
     * entry AL = Next text character
     *       DL = [arpharm]
     */
    private void arp_press0() {
        r.push(octdata);
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
            r.push(octdata);
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
//arpp3:
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
//arpp2:
                skipCount--;
                if (skipCount == 0) return 1;
            } else if (r.al == '@') {
                r.push(r.getDx());
                r.push((short) prevDi);
                r.push(octdata);
                mode[0] |= 4;
                mucomsub.exp_cmd();
                octdata = (byte) r.pop();
                r.di = r.pop();
                r.setDx(r.pop());
            }
        }
//arpp1:
        // Logic for handling f4/fa codes... (omitted)
        reset_arp();
        return 0;
    }

    /**
     * Acquisition of chord note (execution of <>, @).
     */
    public void get_harm() {
        mucomsub.skipoct();
        if (r.al == '@') {
            mode[0] |= 4;
            mucomsub.exp_cmd();
            chktxt();
            mucomsub.xsmall();
        }
//seth1:
        mucomsub.check_onpu();
    }

    /**
     * Storage of first rest of arpeggio.
     * exit DL = Rest length
     *      [lensave] = Remaining length
     */
    private void get_arprest() {
        r.push(r.getAx());
        r.al = lensave;
        r.push(r.getAx());
        r.al = harmno;
        get_defarp();
        r.mul(r.dl);
        add_tlen();
        r.dl = r.al;
        kyufu();
        r.setAx(r.pop());
        r.carry = (r.al & 0xff) < (r.dl & 0xff);
        lensave = (byte) ((r.al & 0xff) - (r.dl & 0xff));
        if (r.carry) {
            error();
            return;
        }
        r.setAx(r.pop());
    }

    private void get_defarp() {
        r.dl = (arplen != 0) ? arplen : trildef;
    }

    /**
     * Chord existence check.
     * entry BX = Source text
     * exit CY = No chord
     */
    public void harm_main() {
        mucomsub.skipoct();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.push(r.getBx());
        chktxt();
        if (r.al != '/') {
//make_rest:
            r.setBx(r.pop());
//make_rest1:
            r.carry = true;
            return;
        }
        r.setDx(r.pop());
        r.dl = harmno;
//harmm2:
        while (true) {
            r.dl--;
            if (r.dl == 0) {
//harm_exsist:
                r.carry = false;
                return;
            }
//harmm1:
            while (true) {
                chktxt();
                if (r.al == ':') {
//make_rest1:
                    r.carry = true;
                    return;
                }
                if (r.al != '@') {
//harmm3:
                    if (r.al == '/') break;
                    continue;
                }
                r.push(r.getDx());
                r.push(octdata);
                mode[0] |= 4;
                mucomsub.exp_cmd();
                octdata = (byte) r.pop();
                r.setDx(r.pop());
            }
        }
    }

    /**
     * Arpeggio processing.
     */
    public void arpeggio() {
        arpmode = 1;
        arplen = 0;
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucomsub.chknum();
        if (!r.carry) {
            tnelnmx();
            arplen = r.al;
        }
//arp1:
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        if (r.al != ',') {
//arp2:
            arpharm = 0;
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        rednums();
//arp2:
        arpharm = r.al;
    }

    /**
     * Conversion process by chord specification.
     */
    private void code_change() {
        get_keycode();
        bassdta = r.al;
        r.push(r.getAx());
        r.push(r.getBx());
        r.cl = 9;
        int siIdx = 0;
        short curCode = 0;
//codem2:
        while (true) {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.ah = muap98.sourceBuf[(r.getBx() & 0xffff) + 1];
            if (r.al == 'o' && r.ah == 'n') {
                // Base specification separately?
//codebase:
                r.setBx((short) ((r.getBx() & 0xffff) + 2));
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
                mucomsub.xsmall();
                mucomsub.check_onpu();
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                get_keycode();
                bassdta = r.al;
                if (muap98.sourceBuf[r.getBx() & 0xffff] != ':') {
//to_err:
                    error();
                    return;
                }
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                break;
            }
            if (r.al != (byte) codedta.charAt(siIdx)) {
//codem4:
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
//codem5:
        r.setBx(r.pop()); // Do not restore BX
        r.setAx(r.pop());
        if ((r.al & 0xff) < (bassdta & 0xff)) r.al += 12;
        onkai = r.al;
        codesav = curCode;
        codem0();
    }

    /**
     * Acquisition of key code.
     * entry AL = CDEFGAB
     *       DS:[BX] = Accidental symbol (#, +, -)
     * exit AL = Intermediate code (0-11)
     */
    private void get_keycode() {
        r.push(r.getDx());
        r.push(r.getAx());
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        gethenon();
        if ((r.dl & 0xff) >= 3) {
//to_err:
            error();
            return;
        }
        r.setAx(r.pop());
        r.al = musdata[(r.al & 0xff) - 'A'];
        if (r.dl == 1) r.al++;
        else if (r.dl == 2) r.al--;
        r.setDx(r.pop());
    }

    private static final String codedta = "m:6:m6:7:m7:M7:mM7:sus4:7sus4:(+5):(-5):7(+5):7(-5):m7(-5):dim:add9:madd9:69:m69:7(+9):7(-9):9:m9:9(+5):9(-5):M9:mM9:11:m11:9(+11):13:";
    private final byte[] codetne = new byte[] {0x00, 0x04, 0x07, (byte) 0xff, 0x00, 0x03, 0x07, (byte) 0xff, 0x00, 0x04, 0x07, 0x09, 0x00, 0x03, 0x07, 0x09, 0x00, 0x04, 0x07, 0x0a, 0x00, 0x03, 0x07, 0x0a, 0x00, 0x04, 0x07, 0x0b, 0x00, 0x03, 0x07, 0x0b, 0x00, 0x05, 0x07, (byte) 0xff, 0x00, 0x05, 0x07, 0x0a, 0x00, 0x04, 0x08, (byte) 0xff, 0x00, 0x04, 0x06, (byte) 0xff, 0x00, 0x04, 0x08, 0x0a, 0x00, 0x04, 0x06, 0x0a, 0x00, 0x03, 0x06, 0x0a, 0x00, 0x03, 0x06, 0x09, 0x00, 0x04, 0x07, 0x0e, 0x00, 0x03, 0x07, 0x0e, 0x04, 0x09, 0x0e, (byte) 0xff, 0x03, 0x09, 0x0e, (byte) 0xff, 0x04, 0x0a, 0x0f, (byte) 0xff, 0x04, 0x0a, 0x0d, (byte) 0xff, 0x04, 0x0a, 0x0e, (byte) 0xff, 0x03, 0x0a, 0x0e, (byte) 0xff, 0x04, 0x08, 0x0a, 0x0e, 0x04, 0x06, 0x0a, 0x0e, 0x04, 0x07, 0x0b, 0x0e, 0x03, 0x07, 0x0b, 0x0e, 0x0a, 0x10, 0x11, (byte) 0xff, 0x0a, 0x0f, 0x11, (byte) 0xff, 0x04, 0x06, 0x0a, 0x0e, 0x04, 0x09, 0x0a, 0x0e};

    /**
     * Processing after chord analysis.
     * entry onkai = Root note
     *       bassdta = Bass note
     *       codesav = Chord number (0-31)
     */
    private void codem0() {
        tnelnmx(); // Analysis of note length
        rtm_max = r.al;
        mode[0] &= 0xfd; // Clear flag for same scale output by @rhythm
//rhy_init1:
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

    /**
     * Acquisition of rhythm table.
     * exit AL = Rhythm length
     *      AH = 0(Note), FF(Rest), FE(Accent), FD
     *      ZR = Not rhythm mode
     */
    private void getrhythm_table() {
        mucomsub.init_rhythm();
        getr_main();
        if (r.zero) {
//init_tbl:
            mucomsub.init_rhythm(); // If ZR = 1, non-rhythm mode
            getr_main();
        }
    }

    private void getr_main() {
        r.ah = 0;
        r.push((short) rhyadrs);
//getr2:
        do {
            r.al = rhythmdta[rhyadrs];
            if ((r.al & 0xff) < 0xfc) break;
            rhyadrs++;
            r.ah = r.al;
        } while (true);
//getr1:
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

    /**
     * Chord performance branching process.
     * entry codesav = Chord number (0-31)
     *       onkai = Intermediate code of root note
     *       bassdta = Intermediate code of bass note
     * exit SI = Start of chord table
     *      CL = Intermediate code of root note
     */
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

    /**
     * Chord performance in 3ch sound effect mode.
     */
    private void set_3ch() {
        if ((mode[0] & 2) != 0) {
            r.al = (byte) 0xf9;
            stosbObjBufAL2DI();
            return;
        }
//set_r3ch:
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

    /**
     * Storage of frequency data in performance data.
     * entry AL = Chord data
     *       CL = Intermediate code (Root note)
     */
    private void setcode() {
        if ((r.al & 0xff) != 0xff) {
            r.al += r.cl;
            mucomsub.read2();
        } else {
//setnop:
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
        } else {
//setnop2:
            stosbObjBufAL2DI();
        }
    }

    /**
     * Change code specification mode (CH3 only).
     */
    public void codein() {
        r.cl = 16;
        mucomsub.check_314(); // Invalid except channel 3, 14
        if (!r.zero) {
//error9:
            error();
            return;
        }
        dt2mode = 0; // Clear multiple detune
        codemod = 1;
        r.setAx((short) 0x40ed); // Set to sound effect mode
        stoswObjBufAX2DI();
    }

    public void codeout() {
        codemod = 0;
        mucomsub.check_314(); // CH=3,14?
        if (!r.zero) {
//skipse:
            return;
        }
        r.setAx((short) 0x00ed); // Release sound effect mode
        stoswObjBufAX2DI();
//skipse:
    }

    public void bass() {
        r.al = 5;
        bass1();
    }

    public void code_exe() {
        rednums();
        if ((r.al & 0xff) > 4) {
            error();
            return;
        }
        bass1();
    }

    private void bass1() {
        r.al += 2;
        codemod = r.al;
    }

    /**
     * Playing the same note (@sc)
     */
    public void same_code() {
        set_symbol2();
        mucomsub.cres_check();
        tnelnmx(); // Analyze note length
        r.dl = r.al; // DL = note length
        if (dionpu == 0) {
            // Was the previous one a note?
//set_rest:
            kyufu(); // Store compressed rest
            return;
        }
        mucomsub.pan_check(); // Auto-pan check
        tnelnx(); // Store note length
        if ((mode[0] & 0x80) != 0) {
            // Tie specified?
            tietie();
        }
        int prevDionpu = dionpu;
        dionpu = r.di; // For next tie/staccato processing
        if (codemod == 1) {
            // @codein mode?
//scode3:
            r.al = (byte) 0xf9; // Only in 3ch @codein mode +++
            stosbObjBufAL2DI();
            return;
        }
        if (sendch == 11) {
            // For PCM
//scode4:
            r.al = (byte) 0xd5;
            stosbObjBufAL2DI();
            r.al = (byte) muap98.objectBuf.get(prevDionpu + 1).dat;
            r.ah = (byte) muap98.objectBuf.get(prevDionpu + 2).dat; // AX = Frequency of previous note
            stoswObjBufAX2DI();
            return;
        }
        r.al = (byte) muap98.objectBuf.get(prevDionpu).dat;
        r.ah = (byte) muap98.objectBuf.get(prevDionpu + 1).dat; // AX = Frequency of previous note
        stoswObjBufAX2DI(); // Store as performance data
    }

    /**
     * Check for alteration symbols
     * entry AL = chr data, BX = text offset
     * exit AL = next chr data, DL = alteration data (0:none 1:# 2:- 3:% 4:## 5:--)
     */
    public void gethenon() {
        r.dl = 1; // Is there a sharp code next? (DL=1)
        if (r.al == '+' || r.al == '#') {
//hen2:
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff]; // Check for double sharp
            if (r.al == '#' || r.al == '+') {
//hen1:
                r.dl += 3;
//hen4:
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            }
//hen0:
            return;
        }
        r.dl = 2; // Is there a flat code next? (DL=2)
        if (r.al == '-') {
//hen3:
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff]; // Check for double flat
            if (r.al == '-') {
//hen1:
                r.dl += 3;
//hen4:
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            }
//hen0:
            return;
        }
        r.dl = 3; // Is there a natural code next? (DL=3)
        if (r.al == '%') {
//hen4:
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
//hen0:
            return;
        }
        r.dl = 0; // Standard (DL=0)
//hen0:
    }

    /**
     * Compare and store with previous note length
     * entry AX = current note length data
     */
    public void tnelnx() {
        r.push(r.getAx());
        if (chglen != r.al) {
            // Compare with previous note length
            setrat(); // Store note length and gate time ratio set command
        }
        if ((mode[0] & 1) == 0) {
            // Do not add in tuplet mode
            add_tlen(); // Addition of total length
        }
        r.setAx(r.pop());
    }

    /**
     * Note length analysis subroutine
     * entry BX = text address
     * exit AX = note length value
     */
    public void tnelnmx() {
        tnelnm();
        r.cl = 6;
        if ((r.getAx() & 0xffff) > 255) {
            // Length range check
            error();
            return;
        }
        work.otoLength = r.getAx() & 0xffff;
    }

    public void tnelnm() {
        check_macrov(); // Macro variable check
        if (!r.carry) {
            tnelnm1();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.push(r.getBx()); // Save next text address
        r.push(r.getSi());
        get_macroadrs(); // SI = macro variable storage address
        r.setBx((short) macrov[(r.getSi() & 0xffff + 18) / 2]); // AX = corresponding macro variable text address
        r.setSi(r.pop());
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        tnelnm1(); // Call note length analysis routine
        r.setBx(r.pop()); // Return to original text address
    }

    private void tnelnm1() {
        r.push(r.getDx());
        r.push(r.getCx());
        r.setDx((short) 0); // Initialization of note length data
        r.ch = 0;
        if (r.al == (byte) '=') {
            // Direct mode?
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            rednum();
//tne_end:
            r.setCx(r.pop());
            r.setDx(r.pop()); // End addition
            return;
        }

//tnelnm2:
        do {
            gettxtlen(); // Read and convert note length digit from text
            r.cl = 6; // Error number for note length overflow
            if (r.ch != 0) {
                // Subtraction?
                r.carry = ((r.getDx() & 0xffff) < (r.getAx() & 0xffff));
                r.setDx((short) ((r.getDx() & 0xffff) - (r.getAx() & 0xffff)));
                if (r.carry || r.zero) {
//error10:
                    error();
                    return;
                }
                r.setAx(r.getDx());
            } else {
//tnelnm3:
                r.carry = ((r.getAx() & 0xffff) + (r.getDx() & 0xffff) > 0xffff);
                r.setAx((short) ((r.getAx() & 0xffff) + (r.getDx() & 0xffff))); // (for ^ command, note length addition)
                if (r.carry) {
//error10:
                    error();
                    return;
                }
            }

//tnelnm4:
            if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) '^') { // Note length addition

                if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) '_') {
                    // Note length subtraction
//tne_end:
                    r.setCx(r.pop());
                    r.setDx(r.pop()); // End addition
                    return;
                }
                r.ch = muap98.sourceBuf[(r.getBx() & 0xffff) + 1];
                if ((r.ch & 0xff) >= (byte) 'a') {
                    r.ch = (byte) ((r.ch & 0xff) - 0x20);
                }
                if (r.ch == (byte) '>') {
//tne_end:
                    r.setCx(r.pop());
                    r.setDx(r.pop()); // End addition
                    return;
                }
                if (r.ch == (byte) '<') {
//tne_end:
                    r.setCx(r.pop());
                    r.setDx(r.pop()); // End addition
                    return;
                }
                if ((r.ch & 0xff) < (byte) 'A') {
//tne_sub1:
                    r.ch = 1; // Subtraction flag on
                } else if ((r.ch & 0xff) <= (byte) 'G') {
//tne_end:
                    r.setCx(r.pop());
                    r.setDx(r.pop()); // End addition
                    return;
                } else {
//tne_sub1:
                    r.ch = 1; // Subtraction flag on
                }
            } else {
//tne_add:
                r.ch = 0; // Addition flag on
            }

//tne_sub:
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.setDx(r.getAx());
            r.al = muap98.sourceBuf[r.getBx() & 0xffff]; // Read next text
            // jmp tnelnm2
        } while (true);
    }

    /**
     * Read and convert note length digit from text subroutine
     * exit AX = note length (with dot check)
     */
    private void gettxtlen() {
        r.push(r.getDx());
        if (r.al == '.') {
            // Dot check
            loop17(true);
            return;
        }
        mucomsub.chknum(); // Number check
        if (r.carry) {
            // Not a number, so dot check
            loop17(true);
            return;
        }
        rednums(); // Read digit from text
        r.dl = r.al;
        r.setAx((short) (192 / (r.dl & 0xff)));
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.ah = 0;
        r.setDx(r.getAx()); // Save current basic note length
        loop17(false);
    }

    private void loop17(boolean nrm) {
        if (!nrm) {
//loop17:
            while (muap98.sourceBuf[(r.getBx() & 0xffff) + 1] == '.') {
                // Dot check (multiple check)
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                r.setDx((short) ((r.getDx() & 0xffff) >> 1)); // 1.5x calculation
                r.setAx((short) (r.getAx() + r.getDx()));
            }
            r.setDx(r.pop());
            work.otoLength = (r.getAx() & 0xffff);
            return;
        }
//normal:
        r.al = lendata; // Use default note length
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.setDx(r.getAx());
        loop17(false);
    }

    /**
     * Tuplet command processing
     */
    public void renpu() {
        renplen = chglen; // Save note length before tuplet
        disave3 = r.di; // Save address to store note length
        onpucnt = 0; // Tuplet count counter
        mode[0] |= 1; // Tuplet mode

//loop9:
        do {
            chktxt();
            r.ah = muap98.sourceBuf[r.getBx()]; // for @w check
            if (r.al == '}') {
                // End of tuplet?
                exit1();
                return;
            }

            //
            // Convert note, rest, chord
            //

            r.push(r.getDx());
            r.push(r.getCx());
            r.push(r.getAx());
            com_main(); // Convert note
            r.setAx(r.pop());
            r.setCx(r.pop());
            r.setDx(r.pop());
            r.carry = (onpucnt & 0xff) > 192; // Maximum note count check
        } while (r.carry);

//error14:
        error();
    }

    /**
     * Calculate tuplet note length
     */
    private void exit1() {
        mode[0] &= 0xfe; // Clear tuplet mode
        r.dl = onpucnt;
        if (onpucnt == 0) {
            // Error if no note or rest existed
            error();
            return;
        }
        tnelnmx(); // Read and analyze note length
        add_tlen();
        r.push(r.getAx());
        r.setAx(r.di);
        r.setAx((short) ((r.getAx() & 0xffff) - (disave3 & 0xffff))); // AX = bytes required for tuplet notes
        if (r.al == r.dl) {
            // All rests (1-byte type)? (Possible in chord)
//all_rest:
            r.setAx(r.pop());
            r.di = disave3; // Processing when tuplet contents are all rests
            r.dl = r.al;
            kyufu(); // Specify compressed rest
            return;
        }
        r.setAx(r.pop());
        r.ah = 0; // AX = Total note length, DL = Tuplet count
        r.div(r.dl);
        r.zero = r.al == 0; // Error if divided note length is 0
        r.cl = 5;
        if (r.zero) {
//error14:
            error();
            return;
        }
        chglen = r.al; // Save current note length
        if (r.al != renplen) {
            // Compare tuplet length with previous length

            r.push(r.getAx());
            r.push(r.getDx());
            r.setAx(disave3);
            r.setDx((short) 3);
            move_obj(); // Moving performance data
            r.push(r.di);
            r.di = r.getAx();
            r.al = chglen;
            setrat(); // Save note length
            r.di = r.pop();
            r.setDx(r.pop());
            r.setAx(r.pop());
        }
//noset_renpu:
        if (r.ah == 0) {
            // Was there no remainder in note length?
//skip_rr:
            return;
        }
        r.dl = r.ah; // DL = remaining note length
        kyufu(); // Store compressed rest (DL)
//skip_rr:
    }

    /**
     * Move performance data
     * entry AX = move start address
     *       DX = move byte count
     *       DI = current performance address
     * exit DI = subsequent address
     *      dionpu = + count
     *      CY = abnormal termination (AX=0)
     */
    public void move_obj() {
        // Was the note already stored?
        if ((r.getAx() & 0xffff) < OBJTOP) {
            r.carry = true;
            return;
        }

//mobj1:
        r.push(r.di);
        r.push(r.getSi());
        r.push(r.getCx());
        r.push(r.ds);
        r.push(r.es);
        r.ds=r.pop();

        r.setSi(r.di); // SI = transfer source
        r.setCx(r.di);
        r.setCx((short) ((r.getCx() & 0xffff) - (r.getAx() & 0xffff))); // DI = address of note length specification before tuplet
        r.di += r.getDx();
        r.setSi((short) ((r.getSi() - 1) & 0xffff));
        r.di--;

        //	std
        // Move stored data by DX bytes
        do {
            muap98.objectBuf.set(r.di & 0xffff, muap98.objectBuf.get(r.getSi() & 0xffff));
            r.di--; // since df=1
            r.setSi((short) ((r.getSi() - 1) & 0xffff)); // since df=1
            r.setCx((short) ((r.getCx() - 1) & 0xffff));
        } while (r.getCx() != 0);
        //	cld

        dionpu += (r.getDx() & 0xffff);

        r.ds = r.pop();
        r.setCx(r.pop());
        r.setSi(r.pop());
        r.di = r.pop();

        r.di += r.getDx();
        r.carry = false;

        r.carry = false;
    }

    /**
     * Automatic slur/tie command processing
     */
    public void tie() {
        r.setAx((short) dionpu);
        r.setDx((short) 1);
        move_obj();
        if (r.carry) return;
        r.push(r.di);
        r.di = r.getAx();
        r.al = (byte) 0xdf;
        stosbObjBufAL2DI(); // Put slur command in the gap
        if (muap98.sourceBuf[r.getBx() & 0xffff] == '&') {
            // Forced slur?
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            // Do not specify tie
        } else {
//tslur1:
            lastfrq = (muap98.objectBuf.get(r.di).dat & 0xff) | ((muap98.objectBuf.get(r.di + 1).dat & 0xff) << 8); // Save frequency of previous note
            mode[0] |= 0x80; // Let the next note decide tie/slur
        }
//tslur2:
        r.di = r.pop();
        r.zero = false;
//tie0:
    }

    /**
     * Processing of simple tie command
     */
    public void tie2() {
        if ((r.ch & 0xff) != 11) {
            tie(); // Maximize note length ratio
            // PCM simply outputs F8
        }
        tietie();
    }

    private void tietie() {
        mode[0] &= 0x7f; // Disable auto-tie
        r.al = (byte) 0xe1; // Do not key off next +++
        stosbObjBufAL2DI();
    }

    /**
     * Staccato processing (Q4)
     */
    public void stak() {
        r.setAx((short) dionpu);
        r.cl = muap98.sourceBuf[r.getBx() & 0xffff];
        if (r.cl != 0x22) {
            // Staccatissimo?

            //
            // Staccato processing
            //
            r.dl = 4; // Q4
            if ((r.cl & 0xff) == '.') {
                // Mezzo-staccato
                r.dl = 3; // Q5
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
            }
//stak4:
            r.push(r.getDx());
            move_obj(); // Free 2 bytes of performance data
            if (r.carry) return;
            r.push(r.di);
            r.di = r.getAx();
            byte savedQ = ratdata; // Save Q value
            r.al = r.dl;
            chgrat(); // Set to Q4
            r.di = r.pop();
            r.al = savedQ;
            chgrat(); // Restore gate time ratio
        } else {
            //
            // Staccatissimo processing
            //
//stak1:
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.setDx((short) 4);
            move_obj(); // Free 4 bytes of performance data (also for volume up)
            if (r.carry) return;
            r.push(r.di);
            r.di = r.getAx();
            byte savedQ = ratdata; // AL = Q value
            r.al = 5;
            chgrat(); // Set to Q3
            r.setAx((short) 0x4d7);
            stoswObjBufAX2DI(); // Increase volume by +4
            r.di = r.pop();
            r.al = savedQ;
            chgrat();
            r.setAx((short) 0x4d6);
            stoswObjBufAX2DI(); // Restore volume (-4)
        }
    }

    /**
     * Accent processing
     */
    public void acc() {
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucomsub.chknum(); // Number check
        if (r.carry) {
            acc_main();
            return;
        }
        rednums();
        r.push((short) (accbase & 0xff));
        accbase = r.al;
        acc_main();
        accbase = (byte) r.pop();
    }

    private void acc_main() {
        r.setAx((short) dionpu);
        r.setDx((short) 2);
        move_obj(); // Free 2 bytes of performance data
        if (r.carry){
//noacc:
            return;
        }
        r.push(r.di);
        r.di = r.getAx();
        r.ah = accbase; // AH = added value
        r.al = (byte) 0xd7; // Increase volume by +4
        stoswObjBufAX2DI();
        r.di = r.pop();
        r.al = (byte) 0xd6;
        stoswObjBufAX2DI(); // Restore volume (-4)
    }

    /**
     * Reverse accent processing
     */
    public void unacc() {
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucomsub.chknum(); // Number check
        if (r.carry) {
            unacc_main();
            return;
        }
        rednum();
        r.push((short) (dwnbase & 0xff));
        dwnbase = r.al;
        unacc_main();
        dwnbase = (byte) r.pop();
    }

    private void unacc_main() {
        r.setAx((short) dionpu);
        r.setDx((short) 2);
        move_obj(); // Free 2 bytes of performance data
        if (r.carry) {
//nounacc:
            return;
        }
        r.push(r.di);
        r.di = r.getAx();
        r.ah = dwnbase;
        r.al = (byte) 0xd6; // Decrease volume by -6
        stoswObjBufAX2DI();
        r.di = r.pop();
        r.al = (byte) 0xd7;
        stoswObjBufAX2DI(); // Restore volume (+6)
    }

    /**
     * Octave command processing
     */
    public void oct() {
        rednums(); // Read digit from source text
        r.zero = (r.al == 0); // Range check
        r.cl = 6;
        if (r.zero) {
//error3:
            error();
            return;
        }
        if ((r.al & 0xff) >= 10) {
//error3:
            error();
            return;
        }
        r.al--; // Convert to actual value (-1)
        octdata = r.al; // Store in octave data buffer
    }

    /**
     * Octave up command
     */
    public void octup() {
        r.al = octdata; // Read current octave value
        r.al++;
        r.carry = ((r.al & 0xff) < 9);
        r.cl = 6;
        if (!r.carry) {
            error();
            return;
        }
//recov3:
        octdata = r.al;
    }

    /**
     * Octave down command
     */
    public void octdown() {
        r.al = octdata;
        r.carry = ((r.al & 0xff) < 1);
        r.al--;
        r.cl = 6;
        if (!r.carry) {
//recov3:
            octdata = r.al;
            return;
        }
//error11:
        error();
    }

    /**
     * Gate time ratio set command processing
     */
    public void ratio() {
        mucomsub.makeDatum(MMLType.GatetimeDiv);
        work.md = work.FlashLstMd(work.md);

        rednums(); // Read digit from text

        work.md.args.add((int) r.al); // Gate time value (int)

        r.cl = 6; // Range check
        if ((r.al & 0xff) >= 9) {
            error();
            return;
        }
        if (r.al == 0) {
            error();
            return;
        }
        r.al = (byte) (~r.al & 0xff); // Pre-calculate (invert) then store in buffer
        r.al -= 0xf7;
        ratmode &= 0xfe;
        chgrat();
    }

    /**
     * Calculate and store sounding time from note length and ratio
     * Calculation formula : time = length * ratio / 8
     * entry AL = chglen (specified note length)
     */
    public void setrat() {
        if ((mode[0] & 1) != 0) { // Do not store note length in tuplet (batch storage)
            return;
        }
        r.push(r.getAx());
        chglen = r.al; // Save just in case
        r.ah = (byte) 0xf4;
        byte tmp = r.al;
        r.al = r.ah;
        r.ah = tmp;
        muap98.objectBuf.set(r.di, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;

        r.al = ratdata; // Note length ratio data
        if ((ratmode & 1) == 0) {
            // @Q mode?
            int val = (r.al & 0xff) * (r.ah & 0xff);
            r.al = (byte) (val >> 3); // Calculate 1/8 and set
        }

//setrat1:
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        r.setAx(r.pop());
//skip_setrat:
    }

    /**
     * Change only gate time ratio
     * entry AL = Q value
     */
    public void chgrat() {
        r.push(r.getAx());
        ratdata = r.al;
        r.test(ratmode, (byte) 1);
        if (r.zero) {
            r.ah = chglen;
            int val = (r.al & 0xff) * (r.ah & 0xff);
            r.al = (byte) (val >> 3);
        }
//chgrat1:
        r.ah = r.al;
        r.al = (byte) 0xde;
        muap98.objectBuf.set(r.di, work.copy(work.md, r.al));
        work.md = null;
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;
        r.setAx(r.pop());
    }

    /**
     * Rest command
     */
    public void rest() {
        int r_ = work.row, c_ = work.col;
        set_symbol2(); // Store source address
        check_comlen(); // Check for colored lyric
        tnelnmx(); // Analysis routine for note length (AL = note length)
        if ((mode[0] & 1) == 0) {
            // Do not add in tuplet mode
            add_tlen();
        }
        r.dl = r.al;
        work.row = r_;
        work.col = c_;
        kyufu(); // Store compressed rest
    }

    /**
     * Specify the maximum value for compressed rests
     */
    public void set_max() {
        tnelnmx();
        maxrest = r.al;
    }

    /**
     * Compression processing for rests
     * entry DL = rest note length
     */
    public void kyufu() {
        mucomsub.cres_check(); // Crescendo check
        mode[0] &= 0x7f; // Disable auto-tie
        dionpu = 0; // Set no note
        if ((mode[0] & 1) == 0) {
            // Tuplet mode?
            kyufu1();
            return;
        }
        onpucnt++; // Tuplet counter
        r.push(r.getAx());
        muap98.objectBuf.set(r.di++, new MmlDatum((byte) 0xff));
        r.setAx(r.pop());
    }

    private void kyufu1() {
        r.push(r.getAx());
        if (r.di != disave2) {
            // Was the previous one a rest?
            init_kyufu();
            return;
        }
        int total = (restlen & 0xff) + (r.dl & 0xff); // Total length of rests until now
        if (total > 255 || total > (maxrest & 0xff)) {
            // Add current rest's note length
            init_kyufu();
            return;
        }
        r.di = (short) disave1; // DI = previous performance data address
        r.al = (byte) total;
        set_kyufu();
        r.setAx(r.pop());
    }

    private void set_kyufu() {
        restlen = r.al; // Save total rest length
        chglen = r.al; // Current note length
        if (r.al != chgsav) {
            // Does rest length match previous note's length?
            setrat(); // Store note length (F4xxxx)
        }
        r.al = (byte) 0xff;
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        lp.chip = work.crntChip;
        lp.chipNumber = 0;
        lp.ch = (byte) work.crntChannel;
        lp.part = work.crntPart;
        MmlDatum md = new MmlDatum((byte) 0xff, MMLType.Rest, lp, work.otoLength);
        md = work.FlashLstMd(md);
        muap98.objectBuf.set(r.di++, md); // Store rest
        disave2 = r.di; // Save last address
    }

    private void init_kyufu() {
        disave1 = r.di; // Save rest specification start address
        chgsav = chglen; // Save previous note's length (for comparison)
        r.al = r.dl;
        set_kyufu();
        r.setAx(r.pop());
    }

    /**
     * Check for colored lyric
     */
    private void check_comlen() {
        if ((commode & 2) == 0) {
            // Colored lyric mode?
            return;
        }
        if (muap98.sourceBuf[(r.getBx() & 0xffff) - 2] != (byte) ' ') {
            // Is there a space before note/rest?
            return;
        }
        r.push(r.getAx());
        r.al = (byte) 0xdd; // Digit specification
        r.ah = comcnt; // AH = digit count
        r.push(r.getBx());

//comlen2:
        do {
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            r.ah++;
        } while (muap98.sourceBuf[(r.getBx() & 0xffff) - 2] == (byte) ' '); // Multiple spaces?

        r.setBx(r.pop());
        comcnt = r.ah; // Move to next digit
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        r.setAx(r.pop());
    }

    /**
     * Read source text digit and output to AX
     * (Macro variable compatible version)
     */
    public void rednum() {
        chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1)); // Remove space before digit
        r.push(r.getDx());
        r.push(r.getCx());
        check_macrov(); // Macro variable check
        if (r.carry) {
            r.push(r.getCx());
            r.push(r.getAx());
            r.cl = r.al;
            r.setAx((short) 1);
            r.setAx((short) (r.getAx() << (r.cl & 0xff))); // AX = bit value for specified variable
            r.cl = 40;
            if ((macroflg & (r.getAx() & 0xffff)) == 0) {
                // Check if parameter was specified
                error();
            }
            r.setAx(r.pop());
            r.setCx(r.pop());
            r.push(r.getSi());
            get_macroadrs(); // SI = macro variable storage address
            r.setAx((short) macrov[(r.getSi() & 0xffff) / 2]); // AX = value of corresponding macro variable
            r.setSi(r.pop());
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
//exit3:
            r.setCx(r.pop());
            r.setDx(r.pop());
            return;
        }

//rednum1:
        r.cl = 14;
        mucomsub.chknum(); // Number check
        if (r.carry) {
            error();
        }
        r.setAx((short) 0); // Initialize output data
        r.setCx(r.getAx());
//loop12:
        do {
            r.cl = (byte) ((r.getBx() & 0xffff) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[r.getBx() & 0xffff]); // Number check
            r.push(r.getAx());
            r.al = r.cl;
            mucomsub.chknum();
            r.setAx(r.pop());
            if (r.carry) {
//exit3:
                r.setCx(r.pop());
                r.setDx(r.pop());
                return;
            }
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.setDx((short) 10);
            int ans = (r.getAx() & 0xffff) * (r.getDx() & 0xffff); // Multiply previous data by 10 and add current value
            r.setAx((short) ans);
            r.setDx((short) (ans >> 16));
            r.cl = (byte) ((r.cl & 0xff) - (byte) '0');
            r.setAx((short) ((r.getAx() & 0xffff) + (r.cl & 0xff)));
        } while (true);
    }

    /**
     * Get number from 0 to 255
     */
    public void rednums() {
        r.push(r.getCx());
        rednum();
        r.cl = 14;
        if (r.ah != 0) {
//error2:
            error();
        }
        r.setCx(r.pop());
    }

    /**
     * Macro variable specification check
     * entry DS:BX = source text address
     * exit CY = 1 : macro variable present (BX+1), AL = macro variable number (0-8)
     *      CY = 0 : no macro variable (BX as is), AL = text content
     * break CL
     */
    private void check_macrov() {
        r.cl = 40;
        r.al = (byte) ((r.getBx() & 0xffff) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[r.getBx() & 0xffff]);
        if (r.al != (byte) '\\') { // Macro variable specification?
//cmacrov1:
            r.carry = false;
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) ((r.getBx() & 0xffff) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[r.getBx() & 0xffff]);
        r.carry = ((r.al & 0xff) < (byte) '1');
        r.al = (byte) ((r.al & 0xff) - (byte) '1'); // \1-\9 allowed
        if (r.carry) {
            error();
        }
        if ((r.al & 0xff) >= 9) {
            error();
        }
        r.carry = true;
    }

    /**
     * Read macro variable address
     * entry AL = macro variable number
     * exit SI = macro variable address
     */
    private void get_macroadrs() {
        r.setSi((short) 0); // ofs:macrov
        r.ah = 0;
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getAx() & 0xffff)));
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getAx() & 0xffff)));
    }

    /**
     * Tempo set command processing
     */
    public void tempo() {
        rednum(); // Read text data and output to AX
        if (mmlver < 0x26) {
            r.setAx((short) (r.getAx() - 2)); // Tempo correction
        }
        r.cl = 6;
        // Range check
        if ((r.getAx() & 0xffff) < 16) {
            error();
            return;
        }
        if ((r.getAx() & 0xffff) > 3907) {
            error();
            return;
        }
        tmplen = 0; // Abort @ACC, @RIT
        calc_tempo();
    }

    public void calc_tempo() {
        tempos = r.getAx() & 0xffff; // Save current tempo
        calct(); // Convert to timer count value
        r.push(r.getAx());
        r.al = (byte) 0xf5;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        r.setAx(r.pop());
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
    }

    public void calct() {
        r.push(r.getCx()); // AX = external tempo value
        r.push(r.getDx());
        r.setCx(r.getAx());
        r.setAx((short) 62500); // Convert to timer count value
        r.setDx((short) 0);
        r.div(r.getCx()); // AX = Timer A setting value
        r.setDx(r.pop());
        r.setCx(r.pop());
    }

    /**
     * Repeat performance processing
     */
    private void mloop() {
        r.al = (byte) 0xfe;
        byte d1 = muap98.sourceBuf[r.getBx() & 0xffff];
        if (d1 != '*') {
            // ** command?
//break:
            stosbObjBufAL2DI();
            r.al = chglen;
            setrat(); // Store note length
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) 0xfd;
        if (muap98.sourceBuf[r.getBx() & 0xffff] != '*') {
            // *** command?
//break:
            stosbObjBufAL2DI();
            r.al = chglen;
            setrat(); // Store note length
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        stopm();
    }

    public void stopm() {
        optimiz = 0; // Clear optimization flag
        r.al = (byte) 0xfc; // Execute @STOP
//break:
        stosbObjBufAL2DI();
        r.al = chglen;
        setrat(); // Store note length
    }

    /**
     * Default note length set command processing
     */
    public void mlength() {
        tnelnmx(); // Calculate timer count value from note length value
        lendata = r.al; // Set to default note length
        chglen = r.al;
        setrat(); // Set note length and ratio
    }

    /**
     * Volume set command processing
     */
    private void volume() {
        byte c = muap98.sourceBuf[r.getBx() & 0xffff];
        if (c == '=') {
            // Specification by mf, f, ff etc.
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
        rednums(); // Read digit from text
        retvol();
    }

    private void retvol() {
        if ((r.al & 0xff) >= 16) {
            // Range check
//error4:
            r.cl = 31;
            error();
            return;
        }
        mucomsub.init_cres(); // Clear crescendo
        r.ah = r.al;
        if ((r.ch & 0xff) == 11) {
            r.ah = (byte) ((r.ah & 0xff) << 3);
        } else {
//setvolfm:
            int v = (r.al & 0xff);
            r.ah = (byte) (v + v);
            r.ah = (byte) ((r.ah & 0xff) + v);
            if (r.ah != 0) {
                r.ah = (byte) ((r.ah & 0xff) + (byte) VOLBASE);
            }
        }
        retvol2();
    }

    public void retvol2() {
        r.ah = (byte) ((r.ah & 0xff) + (volsft & 0xff)); // Shift volume value
        if ((r.ah & 0xff) > 127) {
//error4:
            r.cl = 31;
            error();
            return;
        }
        retvol3();
    }

    public void retvol3() {
        volsave = r.ah; // Save volume data
        r.test(optimiz, (byte) 4);
        if (!r.zero) {
            if (r.ah == opt_vol) {
                // Matches previously specified volume?
//svol2:
                return;
            }
            opt_vol = r.ah; // Save volume
            optimiz |= 4;
        }
//svol1:
        r.al = (byte) 0xe2; // Store volume set command +++
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        lp.chip = work.crntChip;
        lp.chipNumber = 0;
        lp.ch = (byte) work.crntChannel;
        lp.part = work.crntPart;
        MmlDatum md = new MmlDatum(r.al, MMLType.Volume, lp, (int) (r.ah & 0xff), (byte) 2);
        md = work.FlashLstMd(md);
        muap98.objectBuf.set(r.di, md);
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;
//svol2:
    }

    /**
     * Relative change of volume
     */
    private void add_vol() {
        get_nowvol();
        r.ah = r.al;
        r.al = (byte) 0xd7;
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        lp.chip = work.crntChip;
        lp.chipNumber = 0;
        lp.ch = (byte) work.crntChannel;
        lp.part = work.crntPart;
        MmlDatum md = new MmlDatum(r.al, MMLType.VolumeUp, lp, (int) (r.ah & 0xff));
        md = work.FlashLstMd(md);
        muap98.objectBuf.set(r.di, md);
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;
    }

    private void sub_vol() {
        get_nowvol();
        r.ah = r.al;
        r.al = (byte) 0xd6;

        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        lp.chip = work.crntChip;
        lp.chipNumber = 0;
        lp.ch = (byte) work.crntChannel;
        lp.part = work.crntPart;
        MmlDatum md = new MmlDatum(r.al, MMLType.VolumeDown, lp, (int) (r.ah & 0xff));
        md = work.FlashLstMd(md);
        muap98.objectBuf.set(r.di, md);
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;
    }

    private void get_nowvol() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        rednums();
    }

    /**
     * Volume specification by musical symbols (V=mP; etc.)
     */
    private void exp_vol() {
        r.ah = muap98.sourceBuf[(r.getBx() & 0xffff) + 1];
        if (r.ah == (byte) '+') { // V=+1
            volshift1();
            return;
        }
        if (r.ah == (byte) '-') { // V=-1
            volshift2();
            return;
        }

        for (Tuple<String, Byte> stringByteTuple : volcheck) {
            r.push(r.getBx());
            boolean fnd = true;
            for (int j = 0; j < stringByteTuple.getItem1().length(); j++) {
                byte b = muap98.sourceBuf[r.getBx() & 0xffff];
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                b = (byte) ((b & 0xff) - (byte) ' ');
                if ((byte) (stringByteTuple.getItem1().charAt(j) - ' ') != b) {
                    fnd = false;
                    break;
                }
            }
            if (fnd) {
                r.setDx(r.pop()); // BX = next source address
                r.al = stringByteTuple.getItem2(); // AL = vol data (0-15)
                retvol();
                return;
            }
            r.setBx(r.pop());
        }

        // Not found
//volc3:
        r.cl = 30;
        error();
    }

    private final Tuple<String, Byte>[] volcheck = new Tuple[] {
            new Tuple<>("PPP:", (byte) 7), new Tuple<>("PP:", (byte) 8),
            new Tuple<>("P:", (byte) 9), new Tuple<>("MP:", (byte) 10),
            new Tuple<>(":", (byte) 11), new Tuple<>("MF:", (byte) 12),
            new Tuple<>("F:", (byte) 13), new Tuple<>("FF:", (byte) 14),
            new Tuple<>("FFF:", (byte) 15)
    };

    /**
     * Symbol-specified volume shift by V=+, V=-
     */
    private void volshift1() {
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        rednums();
        volsft = r.al; // Value to add to overall volume
    }

    private void volshift2() {
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        rednums();
        r.al = (byte) (-r.al);
        volsft = r.al; // Value to subtract from overall volume
    }

    /**
     * Variable command
     */
    private void value() {
        chkval_x(); // Check variable number (source)
        r.dl = r.ah;
        chktxt();
        r.cl = 32;
        if (r.al != (byte) '=') {
//error1:
            error();
            return;
        }
        r.al = (byte) 0xda;
        stosbObjBufAL2DI(); // Command number
        r.dh = 0;
        chkval(); // Check variable number (destination)
        if (r.carry) {
            set_value();
            return;
        }
        r.dh = r.ah;
        chktxt();
        r.dl |= 0x10;
        if (r.al == (byte) '+') {
            set_value(); // Addition specification
            return;
        }
        r.dl |= 0x20;
        if (r.al == (byte) '-') {
            set_value(); // Subtraction specification
            return;
        }
//error1:
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

    /**
     * Variable specification check
     * entry DS:BX = text adrs
     * exit DS:BX = next text, CY = not X, AH = X nest (6-F)
     * break CL, AL
     */
    public void chkval() {
        chktxt();
        mucomsub.xsmall();

        if (r.al != (byte) 'X') {
//chkval1:
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            r.carry = true;
            return;
        }
        chkval_x();
    }

    private void chkval_x() {
        r.al = (byte) ((r.getBx() & 0xffff) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[r.getBx() & 0xffff]);
        r.ah = 6;
        mucomsub.chknum();
        if (r.carry) {
//chkval2:
            r.carry = false;
            return;
        }
        rednums(); // Read variable number
        r.cl = 6;
        if ((r.al & 0xff) > 9)
            error();

        r.al = (byte) ((r.al & 0xff) + 6);
        r.ah = r.al;
//chkval2:
        r.carry = false;
    }

    /**
     * Addition of total length.
     */
    public void add_tlen() {
        r.push(r.getAx());
        r.push(r.getBx()); // AX = length to add
        r.ah = 0;
        calc_alllen();
        long ans = (alllen[r.getBx() / 2] & 0xffff) + (alllen[r.getBx() / 2 + 1] & 0xffff) * 0x10000L;
        ans += (r.getAx() & 0xffff);
        alllen[r.getBx() / 2] = (int) (ans & 0xffff);
        alllen[r.getBx() / 2 + 1] = (int) ((ans >> 16) & 0xffff);
        r.setBx(r.pop());
        r.setAx(r.pop());
    }

    public void init_looplen() {
        r.push(r.getAx());
        r.push(r.getBx());
        calc_alllen();
        int zeroAx = 0;
        alllen[r.getBx() / 2] = zeroAx; // Initialization at start of loop // Note: alllen is ushort type in C#
        alllen[r.getBx() / 2 + 1] = zeroAx;
        alllen[r.getBx() / 2 + 2] = zeroAx;
        alllen[r.getBx() / 2 + 3] = zeroAx;
        r.setBx(r.pop());
        r.setAx(r.pop());
    }

    public void exit_looplen() {
        r.push(r.getAx());
        r.push(r.getBx());
        calc_alllen();
        int axVal = alllen[r.getBx() / 2]; // Store length up to exit when exiting loop
        alllen[(r.getBx() + 4) / 2] = axVal;
        axVal = alllen[(r.getBx() + 2) / 2];
        alllen[(r.getBx() + 6) / 2] = axVal;
        r.setBx(r.pop());
        r.setAx(r.pop());
    }

    public void set_looplen() {
        r.push(r.getAx());
        r.push(r.getBx());
        r.push(r.getDx());
        calc_alllen();
        int dxVal = alllen[(r.getBx() + 4) / 2]; // DX = length within loop
        dxVal |= alllen[(r.getBx() + 6) / 2];
        long ans;
        if (dxVal != 0) {
            dxVal = alllen[(r.getBx() + 4) / 2];
            ans = (alllen[(r.getBx() - 8) / 2] & 0xffff) + (dxVal & 0xffff);
            r.carry = ans > 0xffff;
            alllen[(r.getBx() - 8) / 2] = (int) (ans & 0xffff); // Add to previous nest
            dxVal = alllen[(r.getBx() + 6) / 2];
            alllen[(r.getBx() + 6) / 2] = (dxVal & 0xffff) + (r.carry ? 1 : 0);
            r.al--;
        }
//looplen1:
        r.ah = 0; // AX = loop count
        r.push(r.getAx());
        dxVal = alllen[r.getBx() / 2]; // [bx+2] is ignored
        long mulAns = (long) (r.getAx() & 0xff) * (dxVal & 0xffff);
        r.setAx((short) mulAns);
        r.setDx((short) (mulAns >> 16));
        ans = (alllen[(r.getBx() - 8) / 2] & 0xffff) + (r.getAx() & 0xffff);
        r.carry = ans > 0xffff;
        alllen[(r.getBx() - 8) / 2] = (int) (ans & 0xffff); // Add to previous nest
        alllen[(r.getBx() - 6) / 2] += (r.getDx() & 0xffff) + (r.carry ? 1 : 0);
        r.setAx(r.pop());
        dxVal = alllen[(r.getBx() + 2) / 2];
        mulAns = (long) (r.getAx() & 0xff) * (dxVal & 0xffff);
        alllen[(r.getBx() - 6) / 2] += (int) mulAns;

        r.setDx(r.pop());
        r.setBx(r.pop());
        r.setAx(r.pop());
    }

    /**
     * Calculate alllen address
     * exit DS:BX = macro cache address
     */
    private void calc_alllen() {
        r.setBx((short) 0);
        r.bl = nesting;
        r.setBx((short) ((r.getBx() & 0xff) << 3));
        // add bx, ofs:alllen
    }
}
