package muap.driver;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;

import muap.common.X86Register;
import musicDriverInterface.MMLType;
import musicDriverInterface.MmlDatum;


public class Play4 {

    private static final Logger logger = System.getLogger(Play4.class.getName());

    public int fadesave = 160;

    public Nax nax = null;
    public X86Register r = null;
    public Work work = null;
    public int[] labelPtr = null; // new ushort[40 * 17];
    public int[] labelPassCnt = new int[40 * 17];

    public Play4(Nax nax, Work work, int[] labelPtr) {
        this.nax = nax;
        r = nax.reg;
        setJumpTable1();
        SetJumptable2();
        setJumpTable3();
        this.work = work;
        this.labelPtr = labelPtr;
        for (int i = 0; i < labelPassCnt.length; i++) {
            labelPassCnt[i] = -1;
        }
    }

    // Self-modifying
    public byte stiof1 = 0;
    public byte intm1 = 0;
    public byte intm2 = 0;
    public byte freq1 = 0b011;
    public int freq2 = 0x987;
    public byte freq3 = 0b0010;
    public byte[] naxad1 = new byte[9];
    public byte[] naxad2 = new byte[9];
    public int port1 = 0x88; // OPNA portA 
    public int port11 = 0x88; // OPNA portA 
    public int port12 = 0x88; // OPNA portA 
    public int port18 = 0x88; // OPNA portA 
    public int port19 = 0x88; // OPNA portA 
    public int port3 = 0x18c;
    public int port31 = 0x18c;
    public int port32 = 0x18c;
    public int port33 = 0x18c;
    public int port34 = 0x18c;
    public int port35 = 0x18c;
    public int port36 = 0x18c;
    public int port37 = 0x18c;
    public int port5 = 0x788; // OPN2 portA 
    public int port7 = 0x78c; // OPN2 portB
    public int hadr7 = 0;
    public int hadr8 = 0;
    public int hadr9 = 0;
    public int hadr10 = 0;
    public int hadr11 = 0;
    public int sign1 = 0;
    public int sign1_2 = 0;
    public byte sign1_4 = 0;
    public int sign2 = 0;
    public int sign3_1 = 0;
    public int sign4_1 = 0;
    public byte outdata1_ = 0;
    public byte outdata2_ = 0;
    public byte outdata3_ = 0;
    public byte outdata4_ = 0;
    public int jump1_ = 0;
    public int jump2_ = 0;
    public int dsp_exit_ = 0;
    // public int test_lop1_ = 0;
    // public int test_lop2_ = 0;
    // public int test_entry3_ = 0;
    public long farjmp1_ = 0;
    public long farjmp2_ = 0;
    public int panl1_ = 0xc008; // or al,al
    public int panl2_ = 0xc008; // or al,al
    private int level1_ = 0x007f;
    private byte level2_ = 0x7f;
    private byte level3_ = 0x7f;

    // Flags for countermeasures against return destination changes via stack
    private boolean rechannelFlg = false;
    private boolean recovFlg = false;
    private boolean recovwFlg = false;
    private boolean initia0Flg = false;
    private boolean another1Flg = false;
    private boolean another_ssg1Flg = false;

    public void check_busy(int dx) {
        // Do nothing
    }

    //
    //	The Packen Software YM2608/3438 music player module V9.32
    //  copyright(c) 1987,1989-1995 by Packen Software[mar.24.1996]
    //			and Special thanks to S.Tabata(PCM Table)
    //

    // include MUAP.INC
    private static final byte OBJTOP = 0x2a;

    private static final byte TRD = 0x20;
    private static final byte SAMPLE_BIT = 0b0010;
    private static final int O4CDATA = 0x987;
    // SAMPLE_DATA = 011b
    // ;  44.10  33.08  22.05  16.54  11.03  8.27  5.52  4.13
    // ;   000b   001b   010b   011b   100b  101b  110b  111b
    // ;                    987h   65ah  4c3h

    // ; Performance work area

    private byte[] chtbl2 = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}; // Channels to be changed by @ch command (1-17)
    private int fadedata = 320; // Fade-out addition value
    private short spsave1 = 0;
    private short sssave1 = 0;
    // ; VisualPlay Work 3
    private int fade_count = 0; // Fade-out level counter

    /**
     * b0 = EMS map, b1 = Performance in progress, b2 = @dataDisp
     * b3 = TracePlay in progress, b4 = FMintIn, b5 = PCMintIn
     * b7 = Timer re-entry
     */
    private byte mapflag = 0;
    private byte[] maxlen = new byte[] {0, 0, 0, 0}; // Max length
    private byte[] con_data = new byte[] {8, 8, 8, 8, 10, 14, 14, 15}; // Bit values of output operators by FM connection
    private byte[] out_data = new byte[] {0, 8, 4, 12}; // FM address and operator order
    private byte realch = 0; // Actual channel number
    private byte fifo_exec = 0;
    // even
    public byte[] pcmtable = new byte[(Nax.MAXPCM + 1) * 2]; // PCM tone management table (@0-99)
    public byte[] ssgtable = new byte[21 * 2]; // SSGPCM tone management table (@0-19)

    // fadesave dw 160 ; For saving fade-out counter
    // skipbyte ... ; Omitted in source
    public byte init_cnt = 0; // Loop count

    // ; DMA, FIFO buffers

    public byte dma_chan = 3;
    public int dma_adr = 0;
    public int dma_bank = 0;
    public int dma_count = 0;
    public int dma_data = 0;
    public int fifoptr1 = 0;
    public int fifoend1 = Nax.FIFO_SIZE * 2;
    public int fifoptr2 = Nax.FIFO_SIZE * 2;
    public int fifoend2 = Nax.FIFO_SIZE * 4;
    public int fifofin = Nax.FIFO_SIZE * 2 * Nax.MAXBUF;

    // Data buffer during performance
    // VisualPlay Work 1

    // even
    private byte[] data_buff = { // Buffer for work area, initialized in logic
            48, 0, 48, 0, 48, 0, 48, 0,
            48, 0, 48, 0, 48, 0, 48, 0,
            48, 0, 48, 0, 48, 0, 48, 0,
            48, 0, 48, 0, 48, 0, 48, 0,
            48, 0, // Length counter, ratio data

            110, 48, 110, 48, 110, 48, 110, 48,
            110, 48, 110, 48, 110, 48, 110, 48,
            110, 48, 110, 48, 110, 48, 110, 48,
            110, 48, 110, 48, 110, 48, 110, 48,
            110, 48, // Volume data, length data

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Current performance address. Kuma: Originally dup(?)

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: TS, DY, TI

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Output frequency data. Kuma: FD

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: RA, LK

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: SC, DC, PC, SD

            (byte) 0xc0, 0, (byte) 0xc0, 0, (byte) 0xc0, 0,
            0, 0, 0, 0, 0, 0,
            (byte) 0xc0, 0, (byte) 0xc0, 0, (byte) 0xc0, 0, (byte) 0xc0, 0,
            (byte) 0xc0, 0, (byte) 0xc0, 0, (byte) 0xc0, 0, (byte) 0xc0, 0,
            (byte) 0xc0, 0, (byte) 0xc0, 0, (byte) 0xc0, 0, // Kuma: PN, DV, LV, PT

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: SY

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: LF, LL

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: FC

            0, (byte) 0x80, 0, (byte) 0x80, 0, (byte) 0x80, 0, (byte) 0x80,
            0, (byte) 0x80, 0, (byte) 0x80, 0, (byte) 0x80, 0, (byte) 0x80,
            0, (byte) 0x80, 0, (byte) 0x80, 0, (byte) 0x80, 0, (byte) 0x80,
            0, (byte) 0x80, 0, (byte) 0x80, 0, (byte) 0x80, 0, (byte) 0x80,
            0, (byte) 0x80,// Kuma: LD, LI

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: SV, AR, AC, FB

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: SE, LB

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: LR, RC

            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, // Kuma: LA, PM
    };

    private static final int LC = 0;
    private static final int RS = 1;
    private static final int VS = 34;
    private static final int LS = 35;
    private static final int AD = 68;
    /** Tone number (CH1-3, 7-17) */
    private static final int TS = 102;
    /** Decay data (CH4-6) */
    private static final int DY = 102;
    // b0=slur, b1=tie, b2=sawtooth LFO, b3=oneshot
    // b4=adding/subtracting, b5=Amd/Pmd, b6=LFO stop/run
    // b7=SYNC off/on
    private static final int TI = 103;
    private static final int FD = 136;

    /** Stack offset */
    private static final int RA = 170;
    /** Final LFO increment value */
    private static final int LK = 171;

    /** Start decay counter (CH4-6) */
    private static final int SC = 204 - 6;
    /** Decay counter (CH4-6) */
    private static final int DC = 204;
    /** Envelope mode [0 / 10h] (CH4-6) */
    private static final int PC = 204 + 6;
    /** Start decay data (CH4-6) */
    private static final int SD = 205 + 6;

    /** PAN & Hard LFO (CH1-3, 7-9, 11, 12-17) */
    private static final int PN = 238;
    /** Decay level (CH4-6) */
    private static final int DV = 239 - 6;
    /** Final volume (CH4-6) */
    private static final int LV = 238;
    /** Envelope shape (CH4-6) */
    private static final int PT = 239;

    /** Debug info */
    private static final int SY = 272;

    /** LFO speed (-128 to 127) */
    private static final int LF = 306;
    /** LFO level (0 to 127) */
    private static final int LL = 307;

    /** LFO counter */
    private static final int FC = 340;

    /** LFO delay (0 to 255) */
    private static final int LD = 374;
    // b0 to b3 = LFO increment
    // b4 to b6 = LFO level base
    // b7 = rest bit
    private static final int LI = 375;

    /** Start volume (CH4-6) */
    private static final int SV = 408 - 6;
    /** Attack rate (CH4-6) */
    private static final int AR = 408;
    /** Attack counter (CH4-6) */
    private static final int AC = 408 + 6;
    /** Frequency data decimal part */
    private static final int FB = 409;

    /** System detune */
    private static final int SE = 442;
    /** LFO speed base (b0, 1), keyboard color (b5 to 7) */
    private static final int LB = 443;

    /** LFO repeat count */
    private static final int LR = 476;
    /** LFO repeat counter */
    private static final int RC = 477;

    /** LFO delay continuous value */
    private static final int LA = 510;
    // b0 to 4 : used in PCM mode
    // b7 : key-on flag
    private static final int PM = 511;

    // VisualPlay Work 2
    // bit_status label byte
    /** #0 periodic circulation counter */
    private byte timercnt = 0;
    /** #1 SSG/PCM mode */
    private byte ssgpcm = 0;
    /** #2 SSG MIXER DATA */
    private byte mixsave = (byte) 0xb8;
    /** #3 noise frequency */
    private byte noisef = 0;
    /** #4 tempo timer value */
    private byte[] tempo = new byte[] {0x00, 0x02, 0x00, 0x02};
    //dw 200h ; Dummy

    /** #8 rhythm table */
    private byte[] rhytbl = new byte[] {0, 0, 0, 0, 0, 0};
    /** #14 played length */
    private int[] playlen = new int[] {0, 0};
    /** #18 total loop count */
    private byte playcont = 0;
    /** #19 channel stop flag (b0 to b16) */
    private int skip_data1 = 0;
    private byte skip_data2 = 0;
    // init_skip1 dw 0 ; #22 skip_data for comparison
    // init_skip2 db 0
    /** #25 for channel performance stop */
    private int ch_mask1 = 0;
    private byte ch_mask2 = 0;
    /** #28 for alignment (F3 command) */
    private int wait_flg1 = 0;
    private byte wait_flg2 = 0;
    /** #31 flag for not displaying keys */
    private int keymask1 = 0;
    private byte keymask2 = 0;
    /** #34 AH=8 usage flag (for NA) */
    private int song_flg1 = 0;
    private byte song_flg2 = 0;
    /** #37 sound effect in-use flag */
    private int shflag1 = 0;
    private int shflag2 = 0;
    /** #40 lyric data */
    private byte[] comdataBuf = new byte[73];
    private String comdata = "";
    /** #113 number of digits for lyric color change */
    private byte comlength = (byte) 0xff;
    /** #114 DSP mode */
    private byte dsp_mode = 0;
    /** #115 DSP level */
    private byte dsp_level = 0;
    /** b1 = @fo */
    private byte init_flg = 0;
    /** replay flag */
    private byte end_flug = 0;
    /** chord performance mode (ch3) */
    private byte codem1 = 0;
    /** chord performance mode (ch14) */
    private byte codem2 = 0;
    private byte exit_flg = 0;
    /** Timer-A frequency divider counter */
    private byte tacount = 0;
    // b0 = 9821PCM record mode, b1 = record buffer exceed
    // b2 = DacSample performance, b3 = play end
    // b4 = play interrupt entry, b5 = true address storage
    private byte pcmrecmode = 0;

    private static final int PWORKE = 1; // 18;

    // align 4
    private static class Pcm0work {

        /** Extended PCM start address, EMS page */
        public int[] pcm0adrs = new int[] {0, 0};
        /** Decr counter for performance * 4 */
        public int[] pcm0cnt = new int[] {0, 0};
        /** Frequency */
        public int[] pcm0freq = new int[] {0, 0};
        /** right+left pan and data (0/FFFF) */
        public int[] pcm0pan = new int[] {0, 0};
        /** Volume */
        public int[] pcm0vol = new int[] {0, 0};
    }

    private Pcm0work[] pcm0work = new Pcm0work[] {
            new Pcm0work(), new Pcm0work(), new Pcm0work(), new Pcm0work(),
            new Pcm0work(), new Pcm0work(), new Pcm0work(), new Pcm0work(),
            new Pcm0work(), new Pcm0work(), new Pcm0work(), new Pcm0work(),
            new Pcm0work(), new Pcm0work(), new Pcm0work(), new Pcm0work(),
            new Pcm0work()
    };

    // SSGPCM play start address, end address, frequency
    private int[] pcmNadrs = new int[] {
            0, 0, 0, // ch1
            0, 0, 0, // ch2
            0, 0, 0  // ch3
    };
    /** X value*10nest + 17ch 6nest. */
    public byte[] loopcnt = new byte[17 * 16 + 0xff];

    //
    // Performance start routine
    //
    public void music_start() {
        music_stop(null);

        short axbk = r.getAx();
        short bxbk = r.getBx();
        short cxbk = r.getCx();
        short dxbk = r.getDx();

        short esbk = r.es;

        r.setAx((short) 0xbf10); // Rhythm sound source full dump
        outdata1();
        r.al = 0;
        mode_change(); // DSP off
        r.al = 8;
        set_buffer_num(); // DSP time constant setting
        init_work();
        init_cnt = 0;

        r.es = esbk;

        r.setDx(dxbk);
        r.setCx(cxbk);
        r.setBx(bxbk);
        r.setAx(axbk);

        music_again(null);
    }

    //
    // Re-play routine
    //
    public void music_again(Object o) {
        short axbk = r.getAx();
        short bxbk = r.getBx();
        short cxbk = r.getCx();
        short dxbk = r.getDx();
        short sibk = r.getSi();
        short dibk = r.di;

        r.setAx((short) 0);
        fade_count = 0;

        r.ch = 0; // r.ch in ASM style
        r.setSi((short) 0); // ofs:data_buff
//magain1:
        do {
            r.al = data_buff[(r.getSi() + TS) & 0xffff];
            tone2608(data_buff); // Tone resetting (ch1 to 3)

            r.setSi((short) ((r.getSi() + 2) & 0xffff));
            r.ch = (byte) ((r.ch & 0xff) + 1);
        } while ((r.ch & 0xff) < 3);

        r.ch = 6;
        r.setSi((short) (6 * 2)); // ofs:data_buff+6*2
//magain2:
        do {
            r.al = data_buff[(r.getSi() + TS) & 0xffff];
            tone2608(data_buff); // Tone resetting (ch6 to 9)
            r.setSi((short) ((r.getSi() + 2) & 0xffff));
            r.ch = (byte) ((r.ch & 0xff) + 1);
        } while ((r.ch & 0xff) < 9);

        r.ch = 11;
        r.setSi((short) (11 * 2)); // ofs:data_buff+11*2
//magain3:
        do {
            // Check extended PCM
            if (!check_86pcm()) {
//magain4:
                r.al = data_buff[(r.getSi() + TS) & 0xffff];
                tone2608(data_buff); // Tone resetting (ch12 to 17)
            } else {
                r.al = data_buff[(r.getSi() + VS) & 0xffff];
                volchgr(data_buff); // Volume resetting
            }
//magain5:
            r.setSi((short) ((r.getSi() + 2) & 0xffff));
            r.ch = (byte) ((r.ch & 0xff) + 1);
        } while ((r.ch & 0xff) < 17);

        r.ch = 9;
        r.setSi((short) (9 * 2)); // ofs:data_buff+9*2
        r.al = data_buff[(r.getSi() & 0xffff) + VS];
        volchgr(data_buff); // Rhythm volume restoration

        r.setSi((short) ((r.getSi() & 0xffff) + 2));
        r.ch = (byte) ((r.ch & 0xff) + 1);
        r.al = data_buff[(r.getSi() & 0xffff) + VS];
        volchgr(data_buff); // PCM volume restoration

        short esbk = r.es;
        r.es = 0; // ofs:object
        r.setAx((short) (nax.objBuf[nax._object[0]][r.es].dat & 0xff));
        r.es = esbk;

        // Performance file before V6
        // Is the performance data header normal?
        if ((r.getAx() & 0xff) == 0x26 || (r.getAx() & 0xff) == OBJTOP) {
//objdata_ready:
            if (check_86pcm()) // Extended 86PCM?
            {
                fifo_start();
            }
//pt1:
            r.al = (byte) nax.pc98.inportB(0xa); // read IMR
//setand:
            r.al &= 0xef; // clear IR12 mask
//pt2:
            nax.pc98.outportB(0xa, r.al); // write IMR
            set_intimer(); // Internal timer initialization
            mapflag |= 2; // Performance in-progress flag
            set_timer();
        }

//objdata_err:
        r.setAx(axbk);
        r.setBx(bxbk);
        r.setCx(cxbk);
        r.setDx(dxbk);
        r.setSi(sibk);
        r.di = dibk;

        // jumpcommand valid check
        jumpMode = false;
        if (nax.objBuf[0][0].args != null && nax.objBuf[0][0].args.size() > 0 && nax.objBuf[0][0].args.get(0) instanceof MmlDatum) {
            MmlDatum md = (MmlDatum) nax.objBuf[0][0].args.get(0);
            if (md.type == MMLType.SkipPlay) {
                jumpMode = true;
            }
        }
    }

    // 
    // Start 86B-PCM performance
    // 

    private void fifo_start() {
        short axbk = r.getAx();
        short bxbk = r.getBx();
        short cxbk = r.getCx();
        short dxbk = r.getDx();
        short dibk = r.di;
        short sibk = r.getSi();

        short esbk = r.es;

        r.es = (short) nax.fifoseg;
        r.di = 0;

//sign4:
        r.setAx((short) 0x8080);
        r.setCx((short) (Nax.FIFO_SIZE * Nax.MAXBUF));

        do {
            work.fifoBuf[(r.di & 0xffff) + 0] = r.al;
            work.fifoBuf[(r.di & 0xffff) + 1] = r.ah;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0); // DMA buffer initialization

        pcm_stop(); // Stop PCM
        fifo_int_off(); // Prohibit FIFO interrupts
        if (check_wsspcm()) // true indicates wsspcm
        {
            fifo_start_wss();
        } else {
            // throw new UnsupportedOperationException();
            put_fifo_data();
            change_buffer();
            r.ah = (byte) (Nax.FIFO_SIZE / 64 - 1);
            // set_fifo_size(); // FIFO interrupt timing setting
            clear_fint();
            fifo_int_on();
            set_volume();
        }

//exit_start:
        r.es = esbk;

        r.setAx(axbk);
        r.setBx(bxbk);
        r.setCx(cxbk);
        r.setDx(dxbk);
        r.di = dibk;
        r.setSi(sibk);
    }

    private void fifo_start_wss() {
        setup_wss(); // WSS initialization
        r.setBx((short) (Nax.FIFO_SIZE - 1));
        set_dmabase();
        put_fifo_data();
        change_buffer();
        put_fifo_data();
        change_buffer();
        clear_fint();
        fifo_int_on();
        set_volume();

        dma_data = (Nax.FIFO_SIZE + 16) * 2;
        program_dma();
        dma_data = Nax.FIFO_SIZE * 2;
        change_buffer();
        r.al = (byte) nax.pc98.inportB(2);
        r.al &= 0xf7; // INT0B interrupt permitted
        nax.pc98.outportB(2, r.al); // write IMR
        pcm_start();
    }

    // WSS Initialization

    private void setup_wss() {
        r.setAx((short) 0x0cc0);
        put_wss(); // Set MODE2 flag
        r.setAx((short) 0x1080);
        put_wss(); // DAC enable, Timer disable, 0db mode
        r.setAx((short) 0x1100);
        put_wss(); // HPF disable

        r.setAx((short) 0x0406);
        put_wss(); // YMF288 left output
        r.setAx((short) 0x0506);
        put_wss(); // YMF288 right output
        r.setAx((short) 0x0dfd);
        put_wss(); // Loopback disabled

        r.setAx((short) 0x4810); // stereo
//freq3:
        r.al |= freq3; // SAMPLE_BIT;

        put_wss(); // Fs and Playback Data Format
        wait_wss(); // Wait for sync on rate change
        put_wss(); // Fs and Playback Data Format
        wait_wss(); // Wait for sync on rate change
    }

    // Wait until CS4231 INIT flag drops
    private void wait_wss() {
        int cx = 0;
        int dx = 0x0f44;

//read_stat_lop:
        do {
            byte al = (byte) nax.pc98.inportB(dx);
            if ((al & 0x80) == 0) break;
            cx--;
        } while (cx > 0);

//wait_wss_end:
    }

    // Setting interrupt generation unit

    private void set_dmabase() {
        r.ah = 0x0e;
        r.al = r.bh;
        put_wss(); // DMA base register high 8bit
        r.ah = 0x0f;
        r.al = r.bl;
        put_wss(); // DMA base register low 8bit
    }

    //
    //	Internal timer frequency setting
    //
    private void set_intimer() {
//intm1:
        byte dl = 0x33; // dx = 0x0133;
        byte dh = 0x01;
        if (intm1 != 0) {
            dl = (byte) 0x9a;
        }
        byte al = (byte) nax.pc98.inportB(0x42);
        if ((al & 0x20) != 0) // System clock is 10MHz?
        {
//intm2:
            dl = (byte) 0xf9; // dx = 0x00f9;
            dh = 0x00;
            if (intm2 != 0) {
                dl = 0x7d;
            }
        }
//clock10m:
        nax.pc98.outportB(0x77, (byte) 0x36);
        // jmp $+2
        nax.pc98.outportB(0x71, dl); // Set 8253#0 counter to 8KHz
        // jmp $+2
        nax.pc98.outportB(0x71, dh);
    }

    //
    // Internal timer interrupt entry
    //
    public void int08ent() {
        short axbk = r.getAx();
        short dxbk = r.getDx();
        short sibk = r.getSi();
        short dsbk = r.ds;
        short esbk = r.es;

        r.setDx((short) 0);
        r.setAx(r.cs);
        r.ds = r.getAx();
        r.es = (short) nax.pcmseg;
        r.setAx((short) 0x8000); // AH = non-play flag for each channel

        r.setSi((short) pcmNadrs[0]); // SI = PCM play pointer
        if ((r.getSi() & 0xffff) < (pcmNadrs[1] & 0xffff)) {
            r.setAx((short) pcmNadrs[2]); // AX = PCM counter
            r.setAx((short) (r.getAx() + 0x1dd)); // Add SSG O4C value
            if ((short) r.getAx() >= 0) {
                r.setDx((short) ((data_buff[FD + 6] & 0xff) + (data_buff[FD + 7] & 0xff) * 0x100));
                if (r.getDx() != 0) {
//pcm1inc:
                    int ans = (short) r.getAx();
                    int dx = r.getDx() & 0xffff;
                    do {
                        r.setSi((short) ((r.getSi() & 0xffff) + 1));
                        ans -= dx;
                    } while (ans >= 0);
                    pcmNadrs[0] = r.getSi() & 0xffff;
                    r.setAx((short) ans);
                }
            }
//pcm1skip:
            pcmNadrs[2] = r.getAx() & 0xffff; // Save PCM counter
            r.al = nax.pcmBuff[r.getSi() & 0xffff]; // AL = PCM data
            r.setAx((short) (byte) r.al);

            r.setDx(r.getAx()); // DX = PCM data
        }

//pcm1end:
        r.setSi((short) pcmNadrs[3 + 0]);
        if ((r.getSi() & 0xffff) < (pcmNadrs[3 + 1] & 0xffff)) {
            r.al = nax.pcmBuff[r.getSi() & 0xffff];
            r.setSi((short) ((r.getSi() & 0xffff) + 1));
            pcmNadrs[3 + 0] = r.getSi() & 0xffff;
            r.setAx((short) (byte) r.al);
            r.setDx((short) (r.getDx() + r.getAx()));
        }
//pcm2end:

        r.setSi((short) pcmNadrs[6 + 0]);
        if ((r.getSi() & 0xffff) < (pcmNadrs[6 + 1] & 0xffff)) {
            r.al = nax.pcmBuff[r.getSi() & 0xffff];
            r.setSi((short) ((r.getSi() & 0xffff) + 1));
            pcmNadrs[6 + 0] = r.getSi() & 0xffff;
            r.setAx((short) (byte) r.al);
            r.setDx((short) (r.getDx() + r.getAx()));
        } else {
//pcm3end:
            if (r.ah == (byte) 0x80) {
                // Not playing
//pcm_nouse:
                stop_intimer(); // Stop interrupt since PCM not used
                gotoPcmExit();
                return;
            }
        }

//pcm_using:
        r.setAx(r.getDx());
        if ((r.getAx() & 0x8000) != 0) {
//negpcm1:
            if (r.getAx() < (short) 0xff80)
                r.setAx((short) 0xff80);
        } else if (r.getAx() > 0x7f)
            r.setAx((short) 0x7f);

//negpcm2:
        r.setAx((short) ((r.getAx() & 0xffff) + 0x80));
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getAx() & 0xffff)));
        r.setSi(r.getAx());
        r.setDx((short) port12);

        // ch1 set volume register
        r.al = 8;
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
        r.setAx((short) pcmtbl[(r.getSi() & 0xffff) / 2]);
        r.setDx((short) ((r.getDx() & 0xffff) + 2));
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);

        // ch2 set volume register
        r.setDx((short) ((r.getDx() & 0xffff) - 2));
        r.al = 9;
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
        r.setDx((short) ((r.getDx() & 0xffff) + 2));
        r.al = (byte) (r.ah & 15);
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);

        // ch3 set volume register
        r.setDx((short) ((r.getDx() & 0xffff) - 2));
        r.al = 10;
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
        r.setDx((short) ((r.getDx() & 0xffff) + 2));
        r.al = (byte) ((r.ah & 0xff) >> 4);
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);

        gotoPcmExit();
    }

    private void gotoPcmExit() {
        r.al = 0x20;
        nax.pc98.outportB(0, r.al);

        // Logic handled by caller in int08ent
    }

    //
    // 8bit linear PCM → 4bit non-linear PCM × 3 conversion table
    //
    private final int[] pcmtbl = new int[] {
            1, 2, 3, 4, 5, 6, 7, 263, 775, 8, 264, 776, 9, 265, 777, 1033, 1289, 10, 266, 778, 1034, 1290, 1546, 11, 267, 523, 779, 1035, 1291, 1547, 1803, 1803, 1803, 12, 268, 524, 780, 1036, 1292, 1548, 1804, 1804, 2060, 2060, 13, 269, 525, 781, 1037, 1293, 1549, 1805, 5901, 14093, 2061, 6157, 14349, 2317, 6413, 10509, 14605, 18701, 22797, 14, 270, 526, 782, 1038, 1294, 1550, 1806, 5902, 14094, 2062, 6158, 14350, 2318, 6414, 10510, 14606, 18702, 22798, 26894, 30990, 15, 271, 527, 783, 1039, 1295, 1551, 1807, 5903, 14095, 2063, 6159, 14351, 2319, 6415, 14607, 18703, 22799, 2575, 6671, 14863, 18959, 23055, 27151, 2831, 6927, 11023, 15119, 19215, 23311, 27407, 31503, 31503, 3087, 3087, 7183, 11279, 15375, 19471, 23567, 27663, 31759, 31759, 35855, 35855, 35855, 3343, 7439, 11535, 15631, 19727, 23823, 27919, 32015, 32015, 32015, 36111, 36111, 36111, 40207, 40207, 40207, 3599, 3599, 3599, 3599, 7695, 11791, 15887, 19983, 24079, 28175, 32271, 32271, 32271, 36367, 36367, 36367, 40463, 40463, 40463, 40463, 44559, 44559, 44559, 44559, 3855, 7951, 12047, 16143, 20239, 24335, 28431, 32527, 32527, 32527, 36623, 36623, 36623, 40719, 40719, 40719, 40719, 40719, 44815, 44815, 44815, 44815, 44815, 44815, 48911, 48911, 48911, 48911, 48911, 48911, 48911, 48911, 48911, 48911, 53007, 53007, 53007, 53007, 53007, 53007, 53007, 53007, 53007, 53007, 53007, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 57103, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 61199, 65295
    };

    //
    // Built-in timer settings
    //
    private void reset_intimer() {
        short axbk = r.getAx();
        short dxbk = r.getDx();

        stop_intimer(); // built-in timer interrupt prohibit
        r.setDx((short) 0x6000); // return to 10ms unit
        r.al = (byte) nax.pc98.inportB(0x42);
        if ((r.al & 0x20) != 0) // System clock is 10MHz?
        {
            r.setDx((short) 0x4e00);
        }

        // Kuma: 0x77 8253 (timer controller) mode setting
        // Kuma: 0x36 #0 counter (interval timer) LSB, MSB R/W Square wave generator
        r.al = 0x36;
        nax.pc98.outportB(0x77, r.al);

        r.al = r.dl;
        nax.pc98.outportB(0x71, r.al); // Set 8253#0 counter to 100Hz

        r.al = r.dh;
        nax.pc98.outportB(0x71, r.al); // Set 8253#0 counter to 100Hz

        r.setDx(dxbk);
        r.setAx(axbk);
    }

    private void start_intimer() {
        r.al = (byte) nax.pc98.inportB(2);
        r.al &= 0xfe; // built-in timer interrupt permit
        nax.pc98.outportB(2, r.al);
    }

    private void stop_intimer() {
        r.al = (byte) nax.pc98.inportB(2);
        r.al |= 1; // built-in timer interrupt prohibit
        nax.pc98.outportB(2, r.al);
    }

    // 
    // Fade-out start instruction
    // 

    private void fade_out() {
        init_flg |= 2; // fade-out from menu
        fade_outm();
    }

    private void fade_outs() {
        if ((init_flg & 2) != 0) {
            // Already fading out
            return;
        }
        fade_outm();
    }

    private void fade_outm() {
        r.push(r.getCx());
        r.setAx((short) ((tempo[0] & 0xff) + (tempo[1] & 0xff) * 0x100)); // acquire current tempo
        long mans = (long) (fadesave & 0xffff) * (r.getAx() & 0xffff);
        r.setDx((short) (mans >> 16));
        r.setAx((short) (mans & 0xffff));

        int cx = 49 * 16;

        long val = ((long) (r.getDx() & 0xffff) << 16) + (r.getAx() & 0xffff);
        long ans = val / cx;
        long mod = val % cx;
        r.setAx((short) ans);
        r.setDx((short) mod);

        fadedata = r.getAx() & 0xffff; // Store value corresponding to tempo
        fade_count = 0x100; // Start fade-out counter
        r.setCx(r.pop());

//fade_oute:
        return;
    }

    //
    // Performance data initialization
    // Set next song address according to playoff value
    //
    private void init_work() {
        short esbk = r.es;
        r.setAx((short) 0x1010); // Timer-A, B Enable
        outdata2();

        r.ah = (byte) 0x80; // PCM Flag Reset
        outdata2();
        r.al = 0;
        tonepcmm(); // set PCM to @0

        r.setCx((short) 3);
        r.setAx((short) 0xc0b4); // PAN to center (OPNA, OPN2C)
//init_pan1:
        do {
            outdata1(); // for YM2608
            outdata2();
            outdata3(); // for YM3438
            outdata4();
            r.al++;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.setAx((short) 0x27);
        outdata3(); // set YM3438 to normal mode
        r.setAx((short) 0x0df18);
        r.setCx((short) 6);
//init_rhythm:
        do {
            outdata1(); // set rhythm pan and volume
            r.al++;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.setAx((short) 0x0c001);
        outdata2(); // init PCM pan

        r.setAx((short) 0x22);
        outdata1(); // init hard LFO (2608)

        outdata3(); // for YM3438
        vol_off(); // Cut all volume

        r.es = r.cs;
        r.di = 0; // ofs:data_buff
        r.setAx((short) 0);
        r.setCx((short) data_buff.length);
        do {
            data_buff[r.di++] = (byte) r.getAx();
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0); // Init all buffers
        timercnt = 0;
        ssgpcm = 0;
        // mixsave = 0;
        noisef = 0;
        playlen[0] = 0;
        playlen[1] = 0;
        playcont = 0;
        skip_data1 = 0;
        skip_data2 = 0;
        ch_mask1 = 0;
        ch_mask2 = 0;
        wait_flg1 = 0;
        wait_flg2 = 0;
        song_flg1 = 0;
        song_flg2 = 0;
        shflag1 = 0;
        shflag2 = 0;
        comdataBuf = new byte[73];
        comdata = "";
        dsp_mode = 0;
        dsp_level = 0;
        init_flg = 0;
        end_flug = 0;
        codem1 = 0;
        codem2 = 0;
        exit_flg = 0;
        tacount = 0;
        pcmrecmode = 0;

        r.di = (short) PN; // ofs:data_buff+PN
        r.setAx((short) 0xc0); // Pan center
        r.setCx((short) 3);
        do {
            data_buff[r.di++] = (byte) r.getAx();
            data_buff[r.di++] = (byte) (r.getAx() >> 8);
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.di += 6; // Ignore SSG part
        r.setCx((short) (5 + 6));
        do {
            data_buff[r.di++] = (byte) r.getAx();
            data_buff[r.di++] = (byte) (r.getAx() >> 8);
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.di = (short) (SV + 6); // ofs:data_buff+SV+6
        r.setAx((short) 0xff);
        r.setCx((short) 3);
        do {
            data_buff[r.di++] = (byte) r.getAx();
            data_buff[r.di++] = (byte) (r.getAx() >> 8);
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0); // Start volume setting

        comlength = r.al;
        nax.comlength = r.al;
        r.setSi((short) 0); // SI = performance data offset
        r.di = 0; // ofs:data_buff
        r.setCx((short) 17);
        r.es = 0; // nax._object;

//setwork:
        do {
//setw1:
            data_buff[r.di + LC] = 46; // Length counter (48)
            data_buff[r.di + LC + 1] = 0; // (short)
            if (check_86pcm()) // Extended 86PCM?
                if ((r.cl & 0xff) != 7) // ch11?
                    data_buff[r.di + LC] = 47;

//setw2:
            data_buff[r.di + VS + 0] = 110; // Volume/Length
            data_buff[r.di + VS + 1] = 48;
            data_buff[r.di + LI] = (byte) 0xb0; // Rest bit on + LFO level base
            data_buff[r.di + LB] = 1; // LFO speed base

            r.al = (byte) nax.objBuf[r.es][(r.getSi() & 0xffff) + 0].dat;
            r.ah = (byte) nax.objBuf[r.es][(r.getSi() & 0xffff) + 1].dat;
            data_buff[r.di + AD + 0] = r.al; // Transfer performance address buffer
            data_buff[r.di + AD + 1] = r.ah;
            r.setSi((short) ((r.getSi() & 0xffff) + 2));
            r.di += 2;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.di = 0x26; // ES:DI = Max length storage address
        r.al = (byte) nax.objBuf[r.es][r.di + 0].dat;
        r.ah = (byte) nax.objBuf[r.es][r.di + 1].dat;
        maxlen[0] = r.al;
        maxlen[1] = r.ah;
        r.al = (byte) nax.objBuf[r.es][r.di + 2].dat;
        r.ah = (byte) nax.objBuf[r.es][r.di + 3].dat;
        maxlen[2] = r.al;
        maxlen[3] = r.ah;

        r.setAx((short) 0xb807);
        mixsave = r.ah; // Set SSG PM2
        outdata1();

        r.setAx((short) 0x200);
        tempo[0] = r.al; // Tempo 120
        tempo[1] = r.ah;
        tempo[2] = r.al;
        tempo[3] = r.ah;

        r.di = 0; // ofs:chtbl2
        r.al = 0;
//chset1:
        do {
            chtbl2[r.di] = r.al;
            r.di++;
            r.al++;
        } while ((r.al & 0xff) < 17);

        r.setCx((short) 17);
        r.di = 0; // ofs:pcm0work
//pcminit1:
        do {
            nax.pc98.OutportC4231_Volume((byte) r.di, (byte) 0, (byte) 110); // PCM volume
            r.setAx((short) 0xc008); // or al, al
            nax.pc98.OutportC4231_Pan((byte) r.di, (byte) 0, r.getAx() & 0xffff); // PCM pan
            nax.pc98.OutportC4231_Pan((byte) r.di, (byte) 1, r.getAx() & 0xffff);
            r.di++;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.di = 0; // ofs:rhytbl ; rhythm table init
        r.al = (byte) (0xc0 + 0x1f);
        r.setCx((short) 6);
        do {
            rhytbl[r.di++] = r.al;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.setAx((short) 0xc3);

        // AH=0 : No sound source
        //    1 : ch1 to 6
        //    2 : ch1 to 10(11)
        //    3 : ch1 to 13
        //    4 : ch1 to 17
        if (outdata1_ == r.al) r.ah = 0;
        else if (outdata2_ == r.al) r.ah = 1;
        else if (outdata3_ == r.al) r.ah = 2;
        else if (outdata4_ == r.al) r.ah = 3;
        else r.ah = 4;

//syscon1:
        r.al = nax.m_mode[3];
        r.al &= 0b10100; // AL=0 : no PCM, AL=1 : ADPCM
        r.al >>= 2; // AL=4 : 86/WSS-PCM
        r.setCx((short) 17);
        r.di = 0; // ofs:loopcnt+8

//syscon2:
        do {
            loopcnt[r.di + 8 + 0] = r.al;
            loopcnt[r.di + 8 + 1] = r.ah;
            r.di += 2; // Store in X8, X9 variables
            r.di += 16 - 2;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.es = esbk;

    }

    // 
    // Performance stop routine
    // 
    public void music_stop() {
        music_stop(null);
    }

    public void music_stop(Object obj) {
        short axbk = r.getAx();
        short dxbk = r.getDx(); // interrupt mask

        // r.ax = 0x3027; // Stop Timer A
        r.setAx((short) 0x3827); // Stop Timer A (v6.25)
        outdata1();

        vol_off();

        reset_intimer(); // Stop internal timer
        r.setAx((short) 8);
        outdata1(); // Lower volume in SSGPCM
        r.setAx((short) (r.getAx() + 1));
        outdata1();
        r.setAx((short) (r.getAx() + 1));
        outdata1();

        mapflag &= 0xfd; // Performance in-progress flag
        if (check_86pcm()) // Extended 86PCM?
        {
            fifo_int_off(); // FIFO stop
            pcm_stop();
        }

        r.setDx(dxbk);
        r.setAx(axbk);
    }

    // 
    // FM/SSG sound cut routine
    // 
    private void vol_off() {
        short sibk = r.getSi();
        short dxbk = r.getDx();
        short cxbk = r.getCx();

        r.setCx((short) 12);

//off1:
        do {
            key_off(); // key-off FM
            r.ch = (byte) ((r.ch & 0xff) + 1);
            if ((r.ch & 0xff) == 3) {
                r.ch = 6;
            }
            if ((r.ch & 0xff) == 9) {
                r.ch = 11;
            }
            r.cl = (byte) ((r.cl & 0xff) - 1);
        } while ((r.cl & 0xff) > 0);

        r.setSi((short) 0); // ofs:data_buff+6
        r.setCx((short) 0x303);

//off2:
        do {
            volssg0(); // SSG volume to 0

            r.ch = (byte) ((r.ch & 0xff) + 1);
            r.cl = (byte) ((r.cl & 0xff) - 1);
        } while ((r.cl & 0xff) > 0);

        r.setAx((short) 0xb);
        outdata2(); // PCM volume to 0
        r.setAx((short) 0x100);
        outdata2a(); // PCM Play Reset

        r.setCx(cxbk);
        r.setDx(dxbk);
        r.setSi(sibk);
    }

    // 
    // YM2203/2608/3438 Data/Address output routine
    // entry AL = adrs
    //       AH = data
    // 	  CH = channel No. (0 to 16)
    // ;
    // outdata ch0 to 5 to 0188h
    // 	       ch6 to 10 subtract AL-6 and to 018Ch
    // 	       ch11 to 13 subtract AL-11 and to 0488h
    // 	       ch14 to 16 subtract AL-14 and to 048Ch
    // outdata0 ch0 to 5 to 0188h
    // 	       ch6 to 10 to 018Ch
    // 	       ch11 to 13 to 0488h
    // 	       ch14 to 16 to 048Ch
    // outdata1 everything to 0188h
    // outdata1s everything to 0188h (ignore in SSGPCM mode)
    // outdata2 everything to 018Ch
    // outdata3 everything to 0488h
    // outdata4 everything to 048Ch
    // outdata6 ch0 to 11 to 0188h
    //          ch11 to 16 to 0488h
    // 

    private void outdataa() {
        if (!checksh()) return;

//outdata:
        if ((r.ch & 0xff) < 6) {
            outdata1();
            return;
        }

        if ((r.ch & 0xff) >= 11) {
            outdata5();
            return;
        }

        r.al -= 6; // subtract address before output
        outdata2();
    }

    public void outdata2() {
        if (outdata2_ == (byte) 0xc3) return;

        short dxbk = r.getDx();
        short axbk = r.getAx();

        r.setDx((short) port3); // self-modifying Initial: 18ch
        bwait(); // busy wait processing
        r.setDx((short) ((r.getDx() & 0xffff) + 2));
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
        check_busy();
        r.setAx(axbk);
        r.setDx(dxbk);
        if (hadr7 == 0) return;
        nax.pc98.outportB(0x5f, r.al);
//skip_outdata:
        return;
    }

    public void outdata0a() {
        if (!checksh()) return;

//outdata0:
        if ((r.ch & 0xff) >= 14) {
            outdata4();
            return;
        }

        if ((r.ch & 0xff) >= 11) {
            outdata3(); // for YM3438
            return;
        }

        if ((r.ch & 0xff) >= 6) {
            outdata2();
            return;
        }

        outdata1();
        return;
    }

    public void outdata1() {
        if (outdata1_ == (byte) 0xc3) return;
        short dxbk = r.getDx();
        short axbk = r.getAx();

        r.setDx((short) port1);
        bwait(); // busy wait processing
        r.setDx((short) ((r.getDx() & 0xffff) + 2));
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
        check_busy();
        r.setAx(axbk);
        r.setDx(dxbk);

//hadr8: ret
    }

    private void outdata6a() {
        if (!checksh()) return;
        outdata6();
    }

    private void outdata6() {
        if ((r.ch & 0xff) < 11) {
            outdata1();
            return;
        }
        outdata3();
    }

    public void outdata3() {
        if (outdata3_ == (byte) 0xc3) return;
        short dxbk = r.getDx();
        short axbk = r.getAx();

        r.setDx((short) port5);
        bwait(); // busy wait processing
        r.setDx((short) ((r.getDx() & 0xffff) + 2));
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
        check_busy();
        r.setAx(axbk);
        r.setDx(dxbk);

        // hadr9: ret
    }

    private void outdata5() {
        r.al -= 11;
        if ((r.ch & 0xff) < 14) {
            outdata3();
            return;
        }
        r.al -= 3;
        outdata4();
    }

    public void outdata4() {
        if (outdata4_ == (byte) 0xc3) return;
        short dxbk = r.getDx();
        short axbk = r.getAx();

//port7:
        r.setDx((short) port7); // 0x58c;
        bwait(); // busy wait processing
        r.setDx((short) ((r.getDx() & 0xffff) + 2));
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
        check_busy();
        r.setAx(axbk);
        r.setDx(dxbk);
//hadr10: ret
    }

    private void outdata1a() {
        if (checksh()) outdata1();
    }

    private void outdata2a() {
        if (checksh()) outdata2();
    }

    private void outdata1sa() {
        if (!checksh()) return;
        if (ssgpcm == 0) outdata1();
//outret:
    }

    /**
     *
     * Check if sound effect is in use
     * exit NZ = in use
     *
     */
    private boolean checksh() {
        short dxbk = r.getDx();
        short cxbk = r.getCx();
        short axbk = r.getAx();

        boolean ret;
        r.ch = realch; // CH in ASM style
        calcbit();
        if (ret = (((r.getAx() & 0xffff) & shflag1) == 0))
            ret = (((r.dl & 0xff) & shflag2) == 0);

//chksh1:
        r.setAx(axbk);
        r.setCx(cxbk);
        r.setDx(dxbk);

        return ret;
    }

    //
    //	Busy wait processing main routine
    //

    private void bwait() {
        short axbk = r.getAx();
        check_busy();
        r.setAx(axbk);
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
        if (hadr11 != 0)
            nax.pc98.outportB(0x5f, r.al);
        check_busy();
        r.al = r.ah;
    }

    //
    //	Busy wait subroutine
    //	entry DX = system port
    //	break CX, AL
    //

    private void check_busy() {
        int cnt = 100; // time-over limit loop
//wait1:
        do {
            cnt--;
            if (cnt == 0) break;
            r.al = (byte) nax.pc98.inportB(r.getDx() & 0xffff); // Check BUSY
        } while ((r.al & 0x80) == 0);
    }

    //
    //	Timer operation
    //

    private void set_timer() {
        r.ds = r.cs;
        r.setAx((short) ((tempo[0] & 0xff) + (tempo[1] & 0xff) * 0x100)); // AX = tempo data
        r.setAx((short) ((r.getAx() & 0xffff) >> 2));
        r.dl = tacount;

        if ((r.dl & 0xff) == 0xff) {
//set_tac1:
            tacount = r.ah; // divide frequency for T61 and below
            // Correction because setting below 1.28ms is difficult
            if (r.ah != 0 && (r.al & 0xff) < 0x40) r.al += 0x40;
//set_tac5:
            r.al = (byte) ~(r.al & 0xff);
            r.ah = r.al;
            r.al = 0x24;
            outdata1();
            r.ah = tempo[0];
            r.ah = (byte) ~(r.ah & 0xff);
            r.ah &= 3;
        } else {
            r.ah = 0;
//set_tac3:
            // If the initially set value was corrected, fix it this time
            if (r.dl == 0 && (r.al & 0xff) < 0x40) r.ah = 0x40;

//set_tac4:
            r.al = 0x24;
            outdata1(); // Set slowest period while dividing frequency
            r.setAx((short) 0);
        }

//set_tac2:
        r.al = 0x25;
        outdata1(); // tempo setting
        r.setAx((short) 0x3d27); // use Timer-A
        r.ah |= codem1;
        if ((mapflag & 2) == 0) // only start when playing
        {
            r.ah = 0x30;
        }
        outdata1();
    }

    //
    // EMS map/unmap operation
    // entry AH = contents of m_mode+3
    //

    private void mapplay_ems() {
        short axbk = r.getAx();
        if ((mapflag & 1) != 0) {
            if (check_emsuse()) {
                // using EMS?
                mapflag |= 0x01;
                nax.pushems();
            }
        }
//notmap:
        r.setAx(axbk);
    }

    private void unmapplay_ems() {
        short axbk = r.getAx();
        if ((mapflag & 1) != 0) {
            if (check_emsuse()) {
                // using EMS?
                mapflag &= 0xfe;
                nax.popems();
            }
        }
//notunmap:
        r.setAx(axbk);
    }

    // 
    //	WSS-PCM DMA interrupt processing routine
    // 

    public void int0bent() {
        synchronized (work.systemInterrupt) {
            byte al = pcmrecmode;
            if ((al & 1) != 0) // Record mode?
            {
                record3();
            } else {
                program_dma();
                clear_fint();
                change_buffer();
                put_fifo_data();
            }

            nax.pc98.outportB(0, (byte) 0x20);
        }
    }

    private void record3() {
        // (Omitted in source)
    }

    // ====
    // Timer A interrupt processing routine
    // ====

    public void timer_entry() {
        short axbk = r.getAx();
        short dxbk = r.getDx();
        short dsbk = r.ds;
        short esbk;

        r.setAx(r.cs);
        r.ds = r.getAx();
        r.ah = 0;

//int_lop:
        boolean skipEOI = false;
        while (true) {
            boolean skipfifoint = timer_entry_CheckSkipFIFO();

//skip_fifo_int:
            r.setDx((short) port19);
            if (!skipfifoint) {
                timer_entry_FIFO();
                r.setDx((short) port18); // check for FM interrupt
            }

            r.al = (byte) nax.pc98.inportB(r.getDx() & 0xffff);
            if ((r.al & 0b0000_0001) == 0) {
                skipEOI = false;
                break;
            }

//fmint_exec:
            if ((mapflag & 0x10) != 0) {
                skipEOI = true;
                break;
            }
            mapflag |= 0x10;

            spsave1 = r.sp;
            sssave1 = r.ss;
            r.ss = r.cs; // dedicated stack (reusing PSP)
            r.sp = 0; // start - 0x20;

            esbk = r.es;
            put_eoi();
            r.sign = false;
            if (tacount == 0) r.sign = true;
            tacount--; // Timer-A divider counter
            set_timer();
            // stiof1: sti
            if (r.sign) {
                do {
                    play_exec();
                } while (jumpMode);
            }
            r.es = esbk;
            r.ah = 1;
            r.ss = sssave1;
            r.sp = spsave1;
            mapflag &= 0xef;
        }

//exit_int1:
        if (!skipEOI) if (r.ah == 0) put_eoi();

//exit_int:
        r.ds = dsbk;
        r.setDx(dxbk);
        r.setAx(axbk);
    }

    private boolean timer_entry_CheckSkipFIFO() {
        if (check_wsspcm()) return true; // WSS-PCM?

        r.setDx((short) 0xa468);
        r.al = (byte) nax.pc98.inportB(r.getDx() & 0xffff);
        if ((r.al & 0b0001_0000) == 0) return true;
        else if (!check_86pcm()) return true; // Extended 86PCM?
        return false;
    }

    private void timer_entry_FIFO() {
        short esbk = r.es;
        if ((pcmrecmode & 1) != 0) // Record mode?
        {
//record1:
            // get_fifo_data(); // get data from FIFO (record)
        } else {
            put_fifo_data(); // send data to FIFO
            change_buffer();
        }
//record2:
        clear_fint();
        r.es = esbk;
    }

    private void put_eoi() {
//jmp1:
        if (nax.jmp1 == 0) {
            r.al = 0x20; // EOI to slave
            nax.pc98.outportB(8, r.al);
            r.al = 0x0b; // read slave ISR
            nax.pc98.outportB(8, r.al);
            r.al = (byte) nax.pc98.inportB(8);
            if (r.al == 0) // more processing?
            {
                r.al = 0x20; // EOI to master
                nax.pc98.outportB(0, r.al); // end processing
            }
        } else {
            r.al = 0x20; // EOI to master
            nax.pc98.outportB(0, r.al);
        }
//exit2:
    }

    //
    //	Write data to FIFO
    //

    private void put_fifo_data() {
        int[] bx = new int[] {0};
        nax.save_extpcm(bx); // save EMS map
        int ax = nax.fifoseg; // ES, FS = FIFO segment
        int es = ax;
        int fs = ax;
//sign3:
        ax = 0x8080;
        int cx = Nax.FIFO_SIZE;
        int di = fifoptr1;

        // FIFO transfer buffer init
        do {
            work.fifoBuf[di++] = (byte) ax;
            work.fifoBuf[di++] = (byte) (ax >> 8);
            cx--;
        } while (cx > 0);

//segad3:
        ax = 0xc000;
        es = ax;

        // EMS mapping

        cx = 17;
        int si = 0; // ofs:pcm0work
        int dx, bp;
//fifo_map1:
        do {
            if (pcm0work[si].pcm0cnt[0] == 0 && pcm0work[si].pcm0cnt[1] == 0) {
                si += PWORKE; // next channel
                cx--;
                continue;
            }

            int cxbk = cx;
            bx[0] = pcm0work[si].pcm0adrs[1]; // logical page
            dx = nax.phandle;
            // r.ax = 0x4400; // EMS mapping
            byte[] ah = {0x44};
            nax.ems.cS4231EMS_Map.accept(r.al & 0xff, ah, bx[0], dx);
            byte[] emsMem = nax.ems.cS4231EMS_GetCurrentMapBuf.get();

            // FIFO write to pre-transfer buffer
            ax = pcm0work[si].pcm0pan[0]; // Pan command (L)
            panl1_ = ax;
            ax = pcm0work[si].pcm0pan[1]; // Pan command (R)
            panl2_ = ax;

            bp = pcm0work[si].pcm0adrs[0]; // BP = PCM data address
            cx = pcm0work[si].pcm0freq[0]; // CX = PCM freq counter
            int cur_bx = pcm0work[si].pcm0freq[1]; // BX = add frequency data
            di = fifoptr1; // FS:DI = pre-transfer FIFO buffer
            long edx = (long) (pcm0work[si].pcm0cnt[0] & 0xffff)
                    + (long) pcm0work[si].pcm0cnt[1] * 0x10000;

//fifo_lop1:
            do {
                byte al = emsMem[bp];
                int tmp_ax = (byte) al * (byte) pcm0work[si].pcm0vol[0];
                tmp_ax <<= 2;
                byte cur_al = (byte) (tmp_ax >> 8);
                byte ah_val = (byte) (tmp_ax >> 8);

                // panl1: self-modifying switch
                switch (panl1_) {
                    case 0xc008: // or al,al
                        cur_al |= cur_al;
                        break;
                    case 0xc030: // xor al,al
                        cur_al ^= cur_al;
                        break;
                }
                // panl2: mask pan
                switch (panl2_) {
                    case 0xc008: // or al,al
                        cur_al |= cur_al;
                        break;
                    case 0xe430: // xor ah,ah
                        ah_val ^= ah_val;
                        break;
                }

//fifo_lop2:
                work.fifoBuf[di++] += cur_al; // add L,R values and store
                work.fifoBuf[di++] += ah_val;
                cx += cur_bx;

                if ((cx & 0x8000) != 0) {
//fifo_freq1:
                    if (di >= fifoend1) {
                        // handle same value output at low freq
                        si = di; // fake jump destination logic
                        break; // fifo_end1 equivalent
                    }
                    continue; // jmp fifo_lop2
                }

//fifo_freq2:
                do {
                    bp++;
                    if (bp >= 16384) // EMS next page?
                    {
//fifo_freq3:
                        bp = 0;
                        bx[0] = pcm0work[si].pcm0adrs[1];
                        bx[0]++;
                        pcm0work[si].pcm0adrs[1] = bx[0];
                        dx = nax.phandle;
                        // ax = 0x4400; mapping
                        ah[0] = 0x44;
                        nax.ems.cS4231EMS_Map.accept(r.al & 0xff, ah, bx[0], dx);
                        emsMem = nax.ems.cS4231EMS_GetCurrentMapBuf.get();
                    }
//fifo_freq5:
                    edx--;
                    if (edx == 0) {
                        pcm0work[si].pcm0cnt[0] = 0;
                        pcm0work[si].pcm0cnt[1] = 0;
                        // jmp fifo_skip1_ logic
                        break;
                    }
//freq2:
                    cx -= (short) freq2;
                } while ((cx & 0x8000) == 0);

                if (edx == 0) break;

            } while (di < fifoend1); // loop until FIFO bytes processed

//fifo_end1:
            if (edx > 0) {
                pcm0work[si].pcm0cnt[0] = (short) edx;
                pcm0work[si].pcm0cnt[1] = (short) (edx >> 16);
                pcm0work[si].pcm0freq[0] = cx;
                pcm0work[si].pcm0adrs[0] = bp;
            }

//fifo_skip1_:
            si += PWORKE; // next channel
            cx = cxbk;
            cx--;

        } while (cx > 0);

        nax.remove_extpcm();

        // DSP processing

//jump1:
        if (jump1_ == 0x3e3e) {
            si = fifoptr2;
            di = fifoptr1;
            cx = (short) Nax.FIFO_SIZE;
//jump2:
            switch (jump2_) {
                case 0:
//test_lop1:
                    do {
                        int tmp_ax = (work.fifoBuf[si] & 0xff) + (work.fifoBuf[si + 1] & 0xff) * 0x100;
                        si += 2;
//sign1:
                        byte al = (byte) tmp_ax;
                        byte ah = (byte) (tmp_ax >> 8);
                        al -= 0x80;
                        ah -= 0x80;
                        int val_dx = ah;
                        int val_ax = (byte) al;
                        int tmp = val_ax;
                        val_ax = val_dx;
                        val_dx = tmp;
                        val_ax = (byte) val_ax;
                        val_ax += val_dx;
//level1:
                        int dx_level = level1_;
                        int ans = (short) val_ax * (short) dx_level;
                        val_dx = (short) (ans >> 16);
                        val_ax = (short) ans;
                        work.fifoBuf[di] += (byte) (val_ax >> 8);
                        work.fifoBuf[di + 1] -= (byte) (val_ax >> 8);
                        di += 2;
                        cx--;
                    } while (cx > 0);
                    break;
                case 1:
//test_lop2:
                    do {
                        int tmp_ax = (work.fifoBuf[si] & 0xff) + (work.fifoBuf[si + 1] & 0xff) * 0x100;
                        si += 2;
                        byte al = (byte) ((byte) tmp_ax - (byte) (tmp_ax >> 8));
//level2:
                        byte ah = level2_;
                        int val_ax = (byte) al * (byte) ah;
                        work.fifoBuf[di + 1] += (byte) (val_ax >> 8);
                        work.fifoBuf[di] -= (byte) (val_ax >> 8);
                        di += 2;
                        cx--;
                    } while (cx > 0);
                    break;
                case 2:
//test_entry3:
                    cx <<= 1;
//test_lop3:
                    do {
                        byte al = work.fifoBuf[si++];
//sign2:
                        al -= 0x80;
//level3:
                        byte ah = level3_;
                        int val_ax = (byte) al * (byte) ah;
                        val_ax <<= 1;
                        work.fifoBuf[di] -= (byte) (val_ax >> 8);
                        di++;
                        cx--;
                    } while (cx > 0);
                    break;
            }
        }

//dsp_exit:
        if (!check_wsspcm()) {
            si = fifoptr1;
            cx = (short) (Nax.FIFO_SIZE * 2);
            dx = 0xa46c;
            // transfer from DS:SI
            do {
                nax.pc98.outportB(dx, work.fifoBuf[si++]);
                cx--;
            } while (cx > 0);
        }
    }

    //
    //	Program DMA
    //
    private void program_dma() {
        byte al = (byte) 0b0000_0100; // set DMA mask bit
        al |= dma_chan;
        nax.pc98.outportB(0x15, al); // SingleMaskSet
        al = (byte) 0b0100_1000; // Set DMA mode
        al |= dma_chan;
        nax.pc98.outportB(0x17, al); // ModeReg.

        int ax = nax.fifoseg; // DMA segment
        long eax = (long) ((ax << 4) + fifoptr1);

//progdma_sub:
        nax.pc98.outportB(0x19, (byte) eax); // ClearByteF/F
        // set DMA address
        nax.pc98.outportB(dma_adr, (byte) eax);
        nax.pc98.outportB(dma_adr, (byte) (eax >> 8));

        // set DMA bank register
        long bank_eax = eax >> 16;
        nax.pc98.outportB(dma_bank, (byte) bank_eax);

        // set DMA counter
        nax.pc98.outportB(dma_count, (byte) dma_data);
        nax.pc98.outportB(dma_count, (byte) (dma_data >> 8));

        al = 0; // clear DMA mask bit
        al |= dma_chan;
        nax.pc98.outportB(0x15, al); // SingleMaskClear
        nax.pc98.outportB(0x5f, al);
    }

    //
    //	Double buffer switch
    //

    private void change_buffer() {
        int ax = fifoptr1;
        ax = change_ptr(ax);
        fifoptr1 = ax;
        fifoend1 = ax + Nax.FIFO_SIZE * 2;

        ax = fifoptr2;
        ax = change_ptr(ax);
        fifoptr2 = ax;
        fifoend2 = ax + Nax.FIFO_SIZE * 2;
    }

    private int change_ptr(int ptr) {
        ptr += Nax.FIFO_SIZE * 2;
        if (ptr >= (fifofin & 0xffff)) {
            ptr = 0;
        }
        return ptr;
    }

    //
    //	Get data from FIFO
    //

    private void get_fifo_data() {
        // (Omitted in source)
    }

    // ; FIFO interrupt clear

    private void clear_fint() {
        if (check_wsspcm()) {
//clear_fintwss:
            nax.pc98.outportB(0x0f46, (byte) 0xfe); // Write to R2
            return;
        }
        byte al = (byte) nax.pc98.inportB(0xa468);
        al &= 0b1110_1111;
        nax.pc98.outportB(0xa468, al);
        al = (byte) nax.pc98.inportB(0xa468);
        al |= 0b0001_0000;
        nax.pc98.outportB(0xa468, al);
    }

    // ; Prohibit FIFO interrupt

    private void fifo_int_off() {
        if (!check_wsspcm()) {
            r.setDx((short) 0xa468);
            r.al = (byte) nax.pc98.inportB(r.getDx() & 0xffff);
            r.al &= 0b1101_1111;
            nax.pc98.outportB(r.getDx() & 0xffff, r.al);
            return;
        }
//fifo_int_offwss:
        r.setAx((short) 0x0a00);
        put_wss();
    }

    // ; Permit FIFO interrupt

    private void fifo_int_on() {
        if (!check_wsspcm()) {
            r.setDx((short) 0xa468);
            r.al = (byte) nax.pc98.inportB(r.getDx() & 0xffff);
            r.al |= 0b0010_0000;
            nax.pc98.outportB(r.getDx() & 0xffff, r.al);
            return;
        }
//fifo_int_onwss:
        r.setAx((short) 0x0a02);
        put_wss();
    }

    // Start PCM playback

    private void pcm_start() {
        fifo_exec |= 1;
        if (check_wsspcm()) {
//pcm_startwss:
            r.setAx((short) 0x4902);
            put_wss(); // PIO
            r.setAx((short) 0x4905); // DMA
            put_wss();
            r.al = TRD;
            r.setDx((short) 0xf44);
            nax.pc98.outportB(r.getDx() & 0xffff, r.al); // MCE off
            return;
        }
        r.setDx((short) 0xa468);
        r.al = (byte) nax.pc98.inportB(r.getDx() & 0xffff);
        r.al |= 0b1000_0000;
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
    }

    // ; Stop PCM playback

    private void pcm_stop() {
        if (!check_wsspcm()) {
            r.setDx((short) 0xa468);
            r.al = (byte) nax.pc98.inportB(r.getDx() & 0xffff);
            r.al &= 0b0111_1111;
            nax.pc98.outportB(r.getDx() & 0xffff, r.al);
            fifo_exec &= 0xfe;
            return;
        }

//pcm_stopwss:
        r.al = (byte) 0b0000_0100;
        r.al |= dma_chan;
        nax.pc98.outportB(0x15, r.al); // SetSingleMask
        r.setAx((short) 0x4903); // Stop PCM playback
        put_wss();
        r.al = TRD;
        r.setDx((short) 0xf44);
        nax.pc98.outportB(r.getDx() & 0xffff, r.al); // MCE off
        r.setDx((short) 0xf46);
        nax.pc98.outportB(r.getDx() & 0xffff, r.al); // Clear interrupt flag
    }

    // ; Set volume

    private void set_volume() {
        if (check_wsspcm()) {
//set_volumewss:
            r.setAx((short) 0x0600);
            put_wss();
            r.setAx((short) 0x0700);
            put_wss();
            return;
        }

        r.setDx((short) 0xa466);
        r.al = (byte) 0b1010_0000;
        nax.pc98.outportB(r.getDx() & 0xffff, r.al);
    }

    //
    //	Set CS4231 register
    //	entry AH = Register number
    //		  AL = Data
    //
    private void put_wss() {
        byte al = r.al;
        byte ah = r.ah;
        int dx = 0xf44;

        ah |= TRD;
        nax.pc98.outportB(dx, ah);
        nax.pc98.outportB(0x5f, ah);

        dx++;
        nax.pc98.outportB(dx, al);
        nax.pc98.outportB(0x5f, al);
    }

    //
    //	Performance main routine
    //

    private void play_exec() {
        work.crntMmlDatum = null;
        r.ds = r.cs;
        nax.m_mode[1] |= 8; // playing flag
        if ((skip_data1 != 0xffff) || (skip_data2 != 1)) { // All channels stopped?
//lenchk2:
            timercnt++;
            r.carry = (playlen[0] == 0xffff);
            playlen[0] += 1; // Count performance length
            playlen[1] += (0 + (r.carry ? 1 : 0));
            int ax_val = playlen[0];
            int dx_val = (maxlen[0] & 0xff) + (maxlen[1] & 0xff) * 0x100;
            int cx_val = (maxlen[2] & 0xff) + (maxlen[3] & 0xff) * 0x100;
            r.carry = (dx_val + 48 > 0xffff);
            dx_val += 48; // add L4
            cx_val += (0 + (r.carry ? 1 : 0));
            if (ax_val == dx_val) {
                ax_val = playlen[1];
                if (ax_val == cx_val) {
                    ax_val = 0;
                    playlen[0] = ax_val;
                    playlen[1] = ax_val;
                    playcont++;
                }
            }
        } else {
            work.setStatus(0);
        }

//lenchk1:
        r.es = (short) nax._object[0];
        replay();
    }

    //
    //	Channel 1-17 performance pre/post processing
    //

    private void replay() {
        r.setSi((short) 0); // ofs:data_buff
        r.setCx((short) 17); // CH-No. & loop time
        r.setAx((short) skip_data1); // channel skip data (17bit)
        r.dl = skip_data2;
        r.setAx((short) ((r.getAx() & 0xffff) | wait_flg1)); // wait until all data ready
        r.dl |= wait_flg2;

//play_main:
        do {
            work.crntMmlDatum = null;
            r.carry = (r.dl & 1) != 0;
            r.dl >>= 1;
            r.setAx(r.rcr(r.getAx(), (byte) 1)); // Is channel stopped?

            short sibk = r.getSi();
            short dxbk = r.getDx();
            short cxbk = r.getCx();
            short axbk = r.getAx();

            if (!r.carry) {
                r.al = r.ch; // ch from ASM style
                realch = r.ch;
                int bx_ptr = 0; // ofs:chtbl2
                r.al = chtbl2[bx_ptr + (r.al & 0xff)];
                r.ch = r.al; // ch from ASM style

                // Rechannel: label logic
                boolean loopRechannel;
                do {
                    loopRechannel = false;
                    rechannelFlg = false;
                    r.al = data_buff[(r.getSi() & 0xffff) + LC]; // Read length counter
                    if ((r.ch & 0xff) == 10) {
                        // ADPCM
                        play_pcm();
                        if (initia0Flg) return;
                    } else if (check_extpcm(data_buff)) {
                        // Extended PCM Check
                        play_pcm();
                        if (initia0Flg) return;
                    } else if ((r.ch & 0xff) == 9) {
                        // RHYTHM
                        play_rhythm();
                    } else if ((r.ch & 0xff) >= 6) {
                        // FM
                        play_fm();
                        if (rechannelFlg) loopRechannel = true;
                    } else if ((r.ch & 0xff) >= 3) {
                        // SSG
                        play_ssg();
                    } else {
                        play_fm();
                        if (rechannelFlg) loopRechannel = true;
                    }
                } while (loopRechannel);
            }

//ch_skip:
            r.setAx(axbk);
            r.setCx(cxbk);
            r.setDx(dxbk);
            r.setSi(sibk);
            r.ch = (byte) ((r.ch & 0xff) + 1); // Next channel
            r.setSi((short) ((r.getSi() & 0xffff) + 2)); // Next data buffer
            r.cl = (byte) ((r.cl & 0xff) - 1);
        } while ((r.cl & 0xff) > 0);

        // Fade-out processing
        int dx_f = fade_count;
        if ((dx_f >> 8) != 0) {
            r.overflow = (dx_f + fadedata) > 0xffff;
            dx_f += fadedata;
            if (r.overflow) {
                music_stop();
                dx_f = 0;
            }
//skip_op3:
            fade_count = dx_f;
        }

        if (end_flug != 0) {
            if ((init_flg & 2) == 0) {
                fade_count = 0;
            }
            mapplay_ems();
            init_work();
        }

        unmapplay_ems();
        nax.m_mode[1] &= 0xf7;
    }

    private void play_fm() {
        r.al--;
        if (r.al == 0) {
            mapplay_ems();
            main_fm();
            if (rechannelFlg) return;
        }
//nextstep1:
        data_buff[(r.getSi() & 0xffff) + LC] = r.al;

        // LFO processing
        short axbk = r.getAx();
        calc_lfo();
        if (!r.carry) {
            if (r.zero) {
                mapplay_ems();
                vol_lfo();
                int sivsbk = (data_buff[(r.getSi() & 0xffff) + VS] & 0xff)
                        + (data_buff[(r.getSi() & 0xffff) + VS + 1] & 0xff) * 0x100;
                data_buff[(r.getSi() & 0xffff) + VS] = r.al;
                volchgm(data_buff);
                data_buff[(r.getSi() & 0xffff) + VS] = (byte) sivsbk;
                data_buff[(r.getSi() & 0xffff) + VS + 1] = (byte) (sivsbk >> 8);
            } else {
//pmd_fm1:
                int dx_val = (data_buff[(r.getSi() & 0xffff) + FD] & 0xff)
                        + (data_buff[(r.getSi() & 0xffff) + FD + 1] & 0xff) * 0x100;
                int dxbk = dx_val;
                dx_val = (dx_val & 0xff) | ((dx_val & 0x700) << 0); // dh &= 7
                r.setDx((short) dx_val);
                freq_master_lfo();
                r.setAx((short) (r.getAx() + r.getDx()));
                r.setDx((short) (dxbk & 0xf800));
                r.setAx((short) (r.getAx() + r.getDx()));
                setfreq1();
            }
        }

//exit_fm1:
        r.setAx(axbk);

        // Ratio processing
        if ((data_buff[(r.getSi() & 0xffff) + TI] & 1) == 0) {
            if (r.al == data_buff[(r.getSi() & 0xffff) + RS]) {
                key_off();
            }
        }
    }

    private void play_ssg() {
        r.al--;
        if (r.al == 0) {
            mapplay_ems();
            main_ssg();
        }

//nextstep2:
        data_buff[(r.getSi() & 0xffff) + LC] = r.al;
        if (data_buff[(r.getSi() & 0xffff) + PC] != 0) {
            lfo_ssg();
            return;
        }
        if (data_buff[(r.getSi() & 0xffff) + LV] == 0) {
            return;
        }

        if ((data_buff[(r.getSi() & 0xffff) + TI] & 1) != 0) {
            skip_ratio();
            return;
        }

        if (r.al != data_buff[(r.getSi() & 0xffff) + RS]) {
            skip_ratio();
            return;
        }
        r.al = 0;
        volssg();
    }

    private void skip_ratio() {
        r.bl = data_buff[(r.getSi() & 0xffff) + VS];
        if (data_buff[(r.getSi() & 0xffff) + SV] == (byte) 0xff) {
            decay_ssg();
            return;
        }
        r.al = data_buff[(r.getSi() & 0xffff) + AC];
        if (r.al == (byte) 0xff) {
            decay_ssg();
            return;
        }
        r.al--;
        data_buff[(r.getSi() & 0xffff) + AC] = r.al;
        if (r.al != 0) {
            lfo_ssg();
            return;
        }
        set_attackrate();
        r.al = 31;
        boolean beflg = ((r.al & 0xff) - (data_buff[(r.getSi() & 0xffff) + AR] & 0xff)) <= 0;
        r.al = (byte) ((r.al & 0xff) - (data_buff[(r.getSi() & 0xffff) + AR] & 0xff));
        if (beflg) {
            r.al = 1;
        }
        r.al = (byte) ((r.al & 0xff) + (data_buff[(r.getSi() & 0xffff) + LV] & 0xff));
        if ((r.al & 0xff) > (r.bl & 0xff)) {
            r.al = r.bl;
            data_buff[(r.getSi() & 0xffff) + AC] = (byte) 255;
        }
        decays1();
    }

    private void decay_ssg() {
        r.al = (byte) (data_buff[(r.getSi() & 0xffff) + SD] | data_buff[(r.getSi() & 0xffff) + DY]);
        if (r.al == 0) {
            lfo_ssg();
            return;
        }

        r.cl = 4;
        r.al = data_buff[(r.getSi() & 0xffff) + SC];
        r.ah = data_buff[(r.getSi() & 0xffff) + SC + 1];
        if ((r.ah & 0xff) < (data_buff[(r.getSi() & 0xffff) + DV] & 0xff)) {
            r.dl = data_buff[(r.getSi() & 0xffff) + SD];
            calc_decay();
            data_buff[(r.getSi() & 0xffff) + SC] = r.al;
            data_buff[(r.getSi() & 0xffff) + SC + 1] = r.ah;
        }

//decay2:
        r.al = data_buff[(r.getSi() & 0xffff) + DC];
        r.ah = data_buff[(r.getSi() & 0xffff) + DC + 1];
        r.dl = data_buff[(r.getSi() & 0xffff) + DY];
        calc_decay();
        data_buff[(r.getSi() & 0xffff) + DC] = r.al;
        data_buff[(r.getSi() & 0xffff) + DC + 1] = r.ah;
        r.setAx((short) ((r.getAx() & 0xffff) + (data_buff[(r.getSi() & 0xffff) + SC] & 0xff) + (data_buff[(r.getSi() & 0xffff) + SC + 1] & 0xff) * 0x100));
        r.al = r.bl;
        r.carry = (r.al & 0xff) < (r.ah & 0xff);
        r.al = (byte) ((r.al & 0xff) - (r.ah & 0xff));
        if (r.carry) {
            r.al = 0;
        }
        decays1();
    }

    private void decays1() {
        r.bl = r.al;
        volssg_set();
        lfo_ssg();
    }

    private void lfo_ssg() {
        calc_lfo();
        if (r.carry) {
            return;
        }
        if (!r.zero) {
            pmd_ssg();
            return;
        }

        r.al = r.bl;
        vol_lfo_ssg();
        volssg1();
    }

    private void pmd_ssg() {
        r.dl = data_buff[(r.getSi() & 0xffff) + FD];
        r.dh = data_buff[(r.getSi() & 0xffff) + FD + 1];
        freq_master_lfo();
        r.setDx((short) ((r.getDx() & 0xffff) - (r.getAx() & 0xffff)));
        r.setAx(r.getDx());
        setfreqs();
    }

    private void calc_decay() {
        r.dh = 0;
        int dx = (r.getDx() & 0xffff) << (r.cl & 0xff);
        int val = (r.getAx() & 0xffff) + dx;
        r.overflow = val > 0x7fff;
        r.setAx((short) val);
        if (r.overflow) r.setAx((short) 0x7fff);
    }

    private void set_attackrate() {
        short axbk = r.getAx();
        r.al = data_buff[(r.getSi() & 0xffff) + AR];
        boolean beflg = (r.al & 0xff) - 30 <= 0;
        r.al = (byte) ((r.al & 0xff) - 30);
        if (beflg) {
            r.al = 1;
        }
        data_buff[(r.getSi() & 0xffff) + AC] = r.al;
        r.setAx(axbk);
    }

    private void play_rhythm() {
        r.al--;
        if (r.al == 0) {
            mapplay_ems();
            main_rhythm();
        }

//nextstep3:
        data_buff[(r.getSi() & 0xffff) + LC] = r.al;
    }

    private void play_pcm() {
        r.al--;
        if (r.al == 0) {
            mapplay_ems();
            main_pcm();
            if (initia0Flg) return;
        }
//nextstep4:
        data_buff[(r.getSi() & 0xffff) + LC] = r.al;

        // PCM LFO
        short axbk = r.getAx();
        calc_lfo();
        if (!r.carry) {
            if (r.zero) {
                vol_lfo();
                int sivsbk = (data_buff[(r.getSi() & 0xffff) + VS] & 0xff)
                        + (data_buff[(r.getSi() & 0xffff) + VS + 1] & 0xff) * 0x100;
                data_buff[(r.getSi() & 0xffff) + VS] = r.al;
                volchgr(data_buff);
                data_buff[(r.getSi() & 0xffff) + VS] = (byte) sivsbk;
                data_buff[(r.getSi() & 0xffff) + VS + 1] = (byte) (sivsbk >> 8);
            }
//pmd_pcm:
            r.setDx((short) ((data_buff[(r.getSi() & 0xffff) + FD] & 0xff)
                    + (data_buff[(r.getSi() & 0xffff) + FD + 1] & 0xff) * 0x100));
            freq_master_lfo();
            r.setDx((short) ((r.getDx() & 0xffff) + (r.getAx() & 0xffff)));
            if (!check_86pcm()) {
                r.setDx((short) ((r.getDx() & 0xffff) << 3));
                r.ah = r.dl;
                r.al = 9;
                outdata2a();
                r.ah = r.dh;
                r.al = 0xa;
                outdata2a();
            } else {
//pmd_pcm86:
                calc_pcmwork(data_buff);
                nax.pc98.OutportC4231_Freq((byte) (r.di & 0xffff), (byte) 1, r.getDx() & 0xffff);
            }
        }
//exit_pcm:
        r.setAx(axbk);

        // PCM Ratio
        if ((data_buff[(r.getSi() & 0xffff) + TI] & 2) != 0) {
            return;
        }
        if (r.al != data_buff[(r.getSi() & 0xffff) + RS]) {
            return;
        }
        rest2_main();
    }

    private void check_mask() {
        short dxbk = r.getDx();
        short cxbk = r.getCx();
        short axbk = r.getAx();

        r.ch = realch; // ch in ASM style
        calcbit();
        r.zero = ((ch_mask1 & (r.getAx() & 0xffff)) == 0);
        if (r.zero) {
            r.zero = ((ch_mask2 & (r.dl & 0xff)) == 0);
        }

        r.setAx(axbk);
        r.setCx(cxbk);
        r.setDx(dxbk);
    }

    private void calc_lfo() {
        r.carry = false;
        r.ah = data_buff[(r.getSi() & 0xffff) + TI];
        if ((r.ah & 0x40) == 0) {
            r.carry = true;
            return;
        }
        r.al = data_buff[(r.getSi() & 0xffff) + LS];
        r.al = (byte) ((r.al & 0xff) - (data_buff[(r.getSi() & 0xffff) + LC] & 0xff));
        int flg = 0;
        if ((r.ah & 2) != 0) {
            r.al = data_buff[(r.getSi() & 0xffff) + LA];
            r.al++;
            if (r.al == 0) {
                r.al = data_buff[(r.getSi() & 0xffff) + LK];
                flg = 1;
            } else {
                data_buff[(r.getSi() & 0xffff) + LA] = r.al;
                flg = 2;
            }
        }
        if (flg == 0) {
            data_buff[(r.getSi() & 0xffff) + LA] = r.al;
        }
        if (flg == 0 || flg == 2) {
            r.carry = (r.al & 0xff) < (data_buff[(r.getSi() & 0xffff) + LD] & 0xff);
            if (r.carry) {
                return;
            }
            r.al = (byte) ((r.al & 0xff) - (data_buff[(r.getSi() & 0xffff) + LD] & 0xff));
            data_buff[(r.getSi() & 0xffff) + LK] = r.al;
        }

        short dxbk = r.getDx();
        short cxbk = r.getCx();
        r.ah = (byte) (data_buff[(r.getSi() & 0xffff) + LI] & 0xf);
        int val_ax = (r.al & 0xff) * (r.ah & 0xff);
        val_ax >>= 5;
        r.ch = 0; // r.ch clear
        r.cl = data_buff[(r.getSi() & 0xffff) + LL];
        val_ax += (r.cl & 0xff);
        if (val_ax > 127) val_ax = 127;
        r.al = (byte) val_ax;
        r.cl = r.al;
        val_ax = (short) ((byte) r.al * (byte) data_buff[(r.getSi() & 0xffff) + LF]);
        short cxbk2 = r.getCx();
        r.cl = (byte) (data_buff[(r.getSi() & 0xffff) + LB] & 3);
        r.sign = (r.cl - 1) < 0;
        r.cl--;
        if (!r.sign) {
            val_ax <<= (r.cl & 0xff);
        } else {
            val_ax >>= 1;
        }
        if ((data_buff[(r.getSi() & 0xffff) + TI] & 4) != 0) {
            val_ax >>= 2;
        }
        r.setCx(cxbk2);
        r.setDx((short) ((data_buff[(r.getSi() & 0xffff) + FC] & 0xff) + (data_buff[(r.getSi() & 0xffff) + FC + 1] & 0xff) * 0x100));
        if ((data_buff[(r.getSi() & 0xffff) + TI] & 0x10) == 0) {
            val_ax += (short) r.getDx();
        } else {
            val_ax = (short) r.getDx() - (short) val_ax;
        }
        r.setAx((short) val_ax);

        r.dl = r.ah;
        if ((r.dl & 0x80) != 0) {
            r.dl = (byte) -r.dl;
        }

        while (true) {
            r.carry = (r.dl & 0xff) < (r.cl & 0xff);
            if (!r.carry) {
                r.setDx((short) ((data_buff[(r.getSi() & 0xffff) + LR] & 0xff) + (data_buff[(r.getSi() & 0xffff) + RC] & 0xff) * 0x100));
                if (r.getDx() != 0) {
                    r.dh++;
                    if ((r.dh & 0xff) >= (r.dl & 0xff)) break;
                    data_buff[(r.getSi() & 0xffff) + RC] = r.dh;
                }
                r.dl = data_buff[(r.getSi() & 0xffff) + TI];
                if ((r.dl & 8) != 0) break;
                if ((r.dl & 4) == 0) {
                    data_buff[(r.getSi() & 0xffff) + TI] ^= 0x10;
                } else {
                    r.setAx((short) 0);
                }
            }
            data_buff[(r.getSi() & 0xffff) + FC] = r.al;
            data_buff[(r.getSi() & 0xffff) + FC + 1] = r.ah;
            break;
        }

        r.setCx(cxbk);
        r.setDx(dxbk);
        r.carry = false;
        r.zero = ((data_buff[(r.getSi() & 0xffff) + TI] & 0x20) == 0);
    }

    private void freq_master_lfo() {
        r.push(r.getCx());
        r.setCx((short) 0);
        r.cl = data_buff[(r.getSi() & 0xffff) + LI];
        r.setCx((short) ((r.getCx() & 0xffff) >> 4));
        r.cl &= 7;
        r.setCx((short) ((r.getCx() & 0xffff) + 1));
        freq_lfo2();
    }

    private void freq_lfo() {
        r.push(r.getCx());
        r.setCx((short) 3);
        freq_lfo2();
    }

    private void freq_lfo2() {
        r.push(r.getDx());
        r.setDx((short) ((r.getDx() & 0xffff) >> 4));
        r.al = r.ah;
        r.setAx((short) (byte) r.al);
        int ans = (short) r.getAx() * (short) r.getDx();
        r.setDx((short) (ans >> 16));
        r.setAx((short) (ans & 0xffff));
        do {
            r.carry = (r.getDx() & 1) != 0;
            r.setDx((short) ((short) r.getDx() >> 1));
            r.setAx(r.rcr(r.getAx(), (byte) 1));
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.setDx(r.pop());
        r.setCx(r.pop());
    }

    private void freq_detune_lfo() {
        r.ah = data_buff[(r.getSi() & 0xffff) + SE];
        r.push(r.getCx());
        r.setCx((short) 7);
        freq_lfo2();
    }

    private void vol_lfo() {
        r.al = data_buff[(r.getSi() & 0xffff) + VS];
        vol_lfo_ssg();
    }

    private void vol_lfo_ssg() {
        int val = (r.al & 0xff) + /* signed */ r.ah;
        if (val > 127 || val < 0) {
            if ((r.ah & 0x80) != 0) {
                r.al = 0;
            } else r.al = 127;
        } else {
            r.al = (byte) val;
        }
    }

    private void main_fm() {
        getentry();
        if (!r.zero) {
            r.al = data_buff[(r.getSi() & 0xffff) + VS];
            volchgm(data_buff);
        }

//back_fm:
        int dx_ret;
        do {
            recovFlg = false;
            recovwFlg = false;
            another1Flg = false;
            dx_ret = 0;

            work.crntMmlDatum = nax.objBuf[0][r.getBx() & 0xffff];
            checkJumpMode(work.crntMmlDatum);
            r.setAx((short) ((nax.objBuf[0][r.getBx() & 0xffff].dat & 0xff) | (((r.getBx() & 0xffff) + 1 >= nax.objBuf[0].length ? 0 : (nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat & 0xff)) << 8)));
            if ((r.al & 0xff) < 0xce) {
                another();
                if (recovFlg) {
                    dx_ret = 1;
                    break;
                }
            }

            r.di = 0; // ofs:jump_table1
            getadrs();
            check_ret();
            if (r.carry) {
                r.setDx((short) 2); // ofs:back_fm
            }
            dx_ret = r.getDx() & 0xffff;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = r.ah;
            jump_table1[(r.di & 0xffff) / 2].run();
            if (another1Flg) {
                another1();
                if (recovFlg) {
                    dx_ret = 1;
                    break;
                }
            }
            if (rechannelFlg) return;
        } while (dx_ret == 2);

        if (dx_ret == 1) recov();
    }

    private void checkJumpMode(MmlDatum md) {
        if (md == null
                || md.args == null
                || md.args.isEmpty()
                || !(md.args.getFirst() instanceof List)
                || ((List<MmlDatum>) md.args.getFirst()).isEmpty()
            || ((MmlDatum)((List<MmlDatum>)md.args.getFirst()).getFirst()).type != MMLType.SkipPlay
                ) return;

        jumpMode = false;
    }

    private Runnable[] jump_table1;

    private void setJumpTable1() {
        jump_table1 = new Runnable[] {
                this::rest, this::quit,
                this::quit2, this::stopm,
                this::kwait, this::code_play,
                this::same_play, this::addfreq,
                this::n_loop, this::pan,
                this::tempoa, this::mlength,
                this::wait_r, this::continue_,
                this::set_reg, this::sysdetune,
                this::hlfo_speed, this::hlfo_data,
                this::codeset, this::key_mask,
                this::tone2608, this::jump_to,
                this::call_to, this::ret_to,
                this::symbol, this::continue_,
                this::sinit, this::if_jump,
                this::if_call, this::vol_change,
                this::tie2, this::c_loop,
                this::tie_tone, this::ratio_change,
                this::setcomlen, this::initia,
                this::mcomment, this::value,
                this::lfopara, this::lfoset,
                this::voladd1, this::volsub1,
                this::continue_, this::continue_,
                this::if_exit, this::pops,
                this::fade_outs, this::pcm_change,
                this::channel, this::last_set
        };
    }

    private void getadrs() {
        short axbk = r.getAx();
        r.al = (byte) ~(r.al & 0xff);
        r.al = (byte) ((r.al & 0xff) * 2);
        r.setAx((short) (byte) r.al);
        r.di += r.getAx();
        r.setAx(axbk);
    }

    private void check_ret() {
        r.setDx((short) 1); // default return address
        if ((r.al & 0xff) >= 0xf8) {
            r.carry = false;
            return;
        }
        exit_flg++;
        if (exit_flg == 0) {
            r.carry = false;
            return;
        }
        r.carry = true;
        return;
    }

    private void getentry() {
        r.setBx((short) ((data_buff[(r.getSi() & 0xffff) + AD] & 0xff) + (data_buff[(r.getSi() & 0xffff) + AD + 1] & 0xff) * 0x100));
        data_buff[(r.getSi() & 0xffff) + TI] &= 0xfc;
        r.zero = (fade_count == 0);
    }

    // ; COMMANDS

    private void another() {
        check_tie();
        byte tmp = r.ah;
        r.ah = r.al;
        r.al = tmp;
        data_buff[(r.getSi() & 0xffff) + FB] = 0;
        r.setDx(r.getAx());
        short dxbk = r.getDx();
        r.dh &= 7;
        freq_detune_lfo();
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getDx() & 0xffff)));
        r.setDx((short) (dxbk & 0xf800));
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getDx() & 0xffff)));
        setkeyon();
        another1();
    }

    private void another1() {
        data_buff[(r.getSi() & 0xffff) + FD] = r.al;
        data_buff[(r.getSi() & 0xffff) + FD + 1] = r.ah;
        setfreq1();
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        sync();
        keyon();
    }

    private void keyon() {
        check_mask();
        if (!r.zero) {
            recovFlg = true;
            return;
        }
        if ((data_buff[(r.getSi() & 0xffff) + TI] & 3) == 0) {
            r.al = data_buff[(r.getSi() & 0xffff) + LS];
            if ((r.al & 0xff) - (data_buff[(r.getSi() & 0xffff) + RS] & 0xff) <= 0) {
                recovFlg = true;
                return;
            }
        }
        r.setAx((short) 0xf028);
        calc_keych();
        if (r.carry) {
            recovFlg = true;
            return;
        }
        outdata6a();
        recovFlg = true;
    }

    private void recov() {
        r.al = data_buff[(r.getSi() & 0xffff) + LS];
        recovw();
    }

    private void recovw() {
        data_buff[(r.getSi() & 0xffff) + AD] = r.bl;
        data_buff[(r.getSi() & 0xffff) + AD + 1] = r.bh;
        exit_flg = 0;
    }

    private void calc_keych() {
        byte cl;
        byte ch = r.ch; // ch in ASM style
        cl = ch;

        if ((ch & 0xff) < 6) {
            r.carry = ((r.ah & 0xff) + (cl & 0xff) > 255);
            r.ah = (byte) ((r.ah & 0xff) + (cl & 0xff));
            return;
        }

        cl -= 2;

        if ((ch & 0xff) < 11) {
            r.carry = ((r.ah & 0xff) + (cl & 0xff) > 255);
            r.ah = (byte) ((r.ah & 0xff) + (cl & 0xff));
            return;
        }

        cl -= 9;

        if ((ch & 0xff) >= 14)
            cl++;

        r.carry = ((r.ah & 0xff) + (cl & 0xff) > 255);
        r.ah = (byte) ((r.ah & 0xff) + (cl & 0xff));
    }

    private void setfreq1() {
        short axbk = r.getAx();
        r.al = (byte) 0xa4; // f-number 2 + block
        r.al = (byte) (r.al + r.ch);
        outdataa();
        r.setAx(axbk);
        r.ah = r.al;
        r.al = (byte) 0xa0; // f-number 1
        r.al = (byte) (r.al + r.ch);
        outdataa();
    }

    private void same_play() {
        key_off();
        keyon();
    }

    private void addfreq() {
        r.push(r.getCx());
        r.setAx((short) ((data_buff[(r.getSi() & 0xffff) + FD] & 0xff) + (data_buff[(r.getSi() & 0xffff) + FD + 1] & 0xff) * 0x100));
        r.setCx(r.getAx());
        r.setAx((short) (r.getAx() & 0x7ff));
        r.setCx((short) ((short) (r.getCx() & 0xffff) >> 11));
        r.setDx((short) 0);
        r.dl = r.ah;
        r.ah = r.al;
        r.al = data_buff[(r.getSi() & 0xffff) + FB];
        if ((r.getCx() & 0xffff) != 0) {
            int loop = r.getCx() & 0xffff;
            do {
                r.carry = (r.getAx() & 0x8000) != 0;
                r.setAx((short) ((short) (r.getAx() & 0xffff) << 1));
                r.setDx(r.rcl(r.getDx(), (byte) 1));
                loop--;
            } while (loop != 0);
        }

        r.setCx((short) 0);
        r.cl = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 2].dat;
        if ((r.cl & 0xff) > 0x7f) {
            r.ah = (byte) ((r.ah & 0xff) - 1); // signed extension
        }
        long sum = (r.getAx() & 0xffff) + (nax.objBuf[0][(r.getBx() & 0xffff)].dat & 0xff) + (nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat & 0xff) * 0x100;
        r.carry = (sum > 0xffff);
        r.setAx((short) sum);
        r.setDx((short) ((r.getDx() & 0xffff) + (r.getCx() & 0xffff) + (r.carry ? 1 : 0)));
        r.setCx((short) 0);

        while (r.dh != 0) {
            r.carry = (r.getDx() & 0x0001) != 0;
            r.setDx((short) ((r.getDx() & 0xffff) >> 1));
            r.setAx(r.rcr(r.getAx(), (byte) 1));
            r.setCx((short) ((r.getCx() & 0xffff) + 1));
        }

        while ((r.dl & 0xff) >= 4) {
            if (r.dl == 0) {
                if ((r.getAx() & 0xffff) < 0xd400)
                    break;
            }
            r.carry = (r.dl & 0x01) != 0;
            r.dl = (byte) ((r.dl & 0xff) >> 1);
            r.setAx(r.rcr(r.getAx(), (byte) 1));
            r.setCx((short) ((r.getCx() & 0xffff) + 1));
        }

        data_buff[(r.getSi() & 0xffff) + FB] = r.al;
        r.al = r.ah;
        r.ah = r.dl;
        r.setCx((short) ((r.getCx() & 0xffff) << 11));
        r.setAx((short) ((r.getAx() & 0xffff) | (r.getCx() & 0xffff)));
        r.setCx(r.pop());
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        data_buff[(r.getSi() & 0xffff) + TI] |= 1;
        clearrest();
        another1Flg = true;
    }

    private void rest() {
        key_off();
        rest0();
    }

    private void rest0() {
        data_buff[(r.getSi() & 0xffff) + LI] |= 0x80;
    }

    private void continue_() {
    }

    private void check_tie() {
        if ((data_buff[(r.getSi() & 0xffff) + TI] & 2) == 0) {
            key_off();
        }
    }

    private void code_play() {
        setkeyon();
        check_tie();
        sync();
        r.setAx((short) ((nax.objBuf[0][(r.getBx() & 0xffff) + 6].dat & 0xff) + (nax.objBuf[0][(r.getBx() & 0xffff) + 7].dat & 0xff) * 0x100));
        byte tmp = r.ah;
        r.ah = r.al;
        r.al = tmp;
        data_buff[(r.getSi() & 0xffff) + FD] = r.al;
        data_buff[(r.getSi() & 0xffff) + FD + 1] = r.ah;
        data_buff[(r.getSi() & 0xffff) + FB] = 0;

        r.setDx((short) 0xa2a6); // op4
        set_freq();
        r.setDx((short) 0xa8ac); // op3
        set_freq();
        r.setDx((short) 0xaaae); // op2
        set_freq();
        r.setDx((short) 0xa9ad); // op1
        set_freq();
        keyon();
    }

    private void codeset() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if ((r.ah & 0xff) == 2) {
            codem1 = r.al;
            return;
        }
        codem2 = r.al;
        r.al = 0x27;
        r.ah |= 0xc;
        outdata0a();
    }

    private void set_freq() {
        r.ah = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.al = r.dl;
        outdata0a();
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.ah = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.al = r.dh;
        outdata0a();
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void key_off() {
        short axbk = r.getAx();
        r.setAx((short) 0x28);
        calc_keych();
        if (!r.carry) outdata6a();
        r.setAx(axbk);
    }

    private void tie2() {
        data_buff[(r.getSi() & 0xffff) + TI] |= 2;
    }

    private void tie_tone() {
        data_buff[(r.getSi() & 0xffff) + TI] |= 1;
    }

    private void kwait() {
    }

    private void quit2() {
        music_stop();
        quit();
    }

    private void quit() {
        end_flug = 1;
        sinit();
    }

    private void sinit() {
        data_buff[(r.getSi() & 0xffff) + RA] = 0;
        init_cnt++;
    }

    private void mlength() {
        data_buff[(r.getSi() & 0xffff) + LS] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        ratio_change();
    }

    private void ratio_change() {
        nax.pc98.outportBDummy(port1);
        data_buff[(r.getSi() & 0xffff) + RS] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void tempoa() {
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.ah = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        tempo[0] = r.al;
        tempo[1] = r.ah;
        tempo[2] = r.al;
        tempo[3] = r.ah;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void n_loop() {
        r.dl = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.dh = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        pops();
        if (r.al == (byte) nax.objBuf[0][r.getBx() & 0xffff].dat) {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            return;
        }
        r.al++;
        pushs();
        r.setBx((short) ((r.getBx() & 0xffff) - 3));
        r.setBx((short) ((r.getBx() & 0xffff) - (r.dl & 0xff)));
    }

    private void c_loop() {
        r.al = 1;
        pushs();
    }

    private void pushs() {
        r.push(r.di);
        pushs_main();
        loopcnt[r.di & 0xffff] = r.al;
        r.di = r.pop();
        r.al = data_buff[(r.getSi() & 0xffff) + RA];
        if ((r.al & 0xff) < 15) {
            r.al = (byte) ((r.al & 0xff) + 1);
        }
        data_buff[(r.getSi() & 0xffff) + RA] = r.al;
    }

    private void pops() {
        r.al = data_buff[(r.getSi() & 0xffff) + RA];
        if (r.al != 0) {
            r.al = (byte) ((r.al & 0xff) - 1);
        }
        data_buff[(r.getSi() & 0xffff) + RA] = r.al;

        r.push(r.di);
        pushs_main();
        r.al = loopcnt[r.di & 0xffff];
        r.di = r.pop();
    }

    private void gets() {
        if ((r.al & 0xf) >= 6) {
            getx();
            return;
        }
        int sibk = (data_buff[(r.getSi() & 0xffff) + RA] & 0xff) + (data_buff[(r.getSi() & 0xffff) + RA + 1] & 0xff) * 0x100;
        pops();
        data_buff[(r.getSi() & 0xffff) + RA] = (byte) sibk;
        data_buff[(r.getSi() & 0xffff) + RA + 1] = (byte) (sibk >> 8);
    }

    private void getx() {
        short dibk = r.di;
        getx_adrs();
        r.al = loopcnt[r.di & 0xffff];
        r.di = dibk;
    }

    private void getx_adrs() {
        r.ah = 0;
        r.al = (byte) ((r.al & 0xf) - 6);
        r.di = (short) (r.getAx() & 0xffff);
        r.al = realch;
        r.setAx((short) ((r.getAx() & 0xffff) << 4));
        r.di = (short) ((r.di & 0xffff) + (r.getAx() & 0xffff));
    }

    private void pushs_main() {
        r.push(r.getAx());
        r.setAx((short) 0);
        r.al = realch;
        r.setAx((short) ((r.getAx() & 0xffff) << 4));
        r.setAx((short) ((r.getAx() & 0xffff) + 15));
        r.di = r.getAx();
        r.setAx((short) 0);
        r.al = data_buff[(r.getSi() & 0xffff) + RA];
        r.di = (short) ((r.di & 0xffff) - (r.getAx() & 0xffff));
        r.setAx(r.pop());
    }

    private void value() {
        r.push(r.di);
        r.push(r.getAx());
        getx_adrs();
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        getx();
        r.dh = r.al;
        r.setAx(r.pop());
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.dl = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) ((r.al & 0xff) >> 4);
        if (r.al != 0) {
            r.al = (byte) ((r.al & 0xff) - 1);
            if (r.al != 0) {
                r.dl = (byte) -r.dl;
            }
            r.dh = (byte) ((r.dh & 0xff) + (r.dl & 0xff));
            r.dl = r.dh;
        }
        loopcnt[r.di & 0xffff] = r.dl;
        r.di = r.pop();
    }

    private void call_to() {
        r.setAx(r.getBx());
        r.setAx((short) ((r.getAx() & 0xffff) + 2));
        pushs();
        r.al = r.ah;
        pushs();
        jump_to();
    }

    private void jump_to() {
        r.setDx((short) ((nax.objBuf[0][r.getBx() & 0xffff].dat & 0xff) + (nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat & 0xff) * 0x100));
        r.setDx((short) ((r.getDx() & 0xffff) - 1));
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getDx() & 0xffff)));

        if (labelPtr == null) return;

        int ptr = (realch & 0xff) * 40;
        for (int i = 0; i < 40; i++) {
            if ((r.getBx() & 0xffff) == labelPtr[ptr + i] && labelPassCnt[ptr + i] < 255) {
                labelPassCnt[ptr + i]++;
                if (labelPassCnt[ptr + i] == 0) labelPassCnt[ptr + i]++;
            }
        }
    }

    private void ret_to() {
        pops();
        r.bh = r.al;
        pops();
        r.bl = r.al;
    }

    private void if_jump() {
        if_main();
        if (!r.carry) {
            jump_to();
            return;
        }
        not_exit();
    }

    private void if_exit() {
        if_main();
        if (r.carry) {
            not_exit();
            return;
        }
        pops();
        jump_to();
    }

    private void if_call() {
        if_main();
        if (!r.carry) {
            call_to();
            return;
        }
        not_exit();
    }

    private void not_exit() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void if_main() {
        r.dl = r.al;
        gets();
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.ah = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.dl &= 0xf0;

        if (r.dl == 0) {
            r.zero = ((r.al & 0xff) - (r.ah & 0xff) == 0);
            r.carry = false;
            if (!r.zero) r.carry = true;
            return;
        }
        if (r.dl == 0x10) {
            r.zero = ((r.al & 0xff) - (r.ah & 0xff) == 0);
            r.carry = ((r.al & 0xff) - (r.ah & 0xff) < 0);
            if (r.zero) r.carry = true;
            return;
        }
        if (r.dl == 0x20) {
            r.zero = ((r.al & 0xff) - (r.ah & 0xff) == 0);
            r.carry = ((r.al & 0xff) - (r.ah & 0xff) < 0);
            r.carry = !r.carry;
            return;
        }

        r.zero = ((r.al & 0xff) - (r.ah & 0xff) == 0);
        r.carry = false;
        if (r.zero) r.carry = true;
    }

    private void add_vol() {
        int val = (r.al & 0xff) + (data_buff[(r.getSi() & 0xffff) + VS] & 0xff);
        if (val > 127) {
            r.al = 127;
        } else {
            r.al = (byte) val;
        }
    }

    private void sub_vol() {
        int val = (data_buff[(r.getSi() & 0xffff) + VS] & 0xff);
        r.carry = val < (r.ah & 0xff);
        val -= (r.ah & 0xff);
        r.al = (byte) val;
        if (r.carry) r.al = 0;
    }

    private void voladd1() {
        add_vol();
        vol_change();
    }

    private void volsub1() {
        sub_vol();
        vol_change();
    }

    private void vol_change() {
        volset();
        volchgm(data_buff);
    }

    private void volchgm(byte[] siBuf) {
        short axbk = r.getAx();
        short bxbk = r.getBx();
        short cxbk = r.getCx();
        short dxbk = r.getDx();
        short dibk = r.di;
        short sibk = r.getSi();

        short dsbk = r.ds;

        r.al = siBuf[(r.getSi() & 0xffff) + TS];
        byte[] toneBuf = tone_adrs();
        r.al = toneBuf[(r.getBx() & 0xffff) + 0x18];
        r.al &= 7;
        r.al = con_data[0 + (r.al & 0xff)];

        r.setBx((short) ((r.getBx() & 0xffff) + 4));
        r.di = 0;
        r.cl = 4;
        do {
            r.dl = siBuf[(r.getSi() & 0xffff) + VS];
            r.carry = (r.al & 1) != 0;
            r.al = (byte) ((r.al & 0xff) >> 1);
            if (r.carry) {
                operate(toneBuf, out_data);
            } else {
                short axbk2 = r.getAx();
                r.al = out_data[r.di & 0xffff];
                r.al += 0x40;
                r.al += (r.ch & 0xff); // ch in ASM style
                r.ah = toneBuf[r.getBx() & 0xffff];
                outdataa();
                r.setAx(axbk2);
            }
            r.di++;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.cl--;
            if (work.crntMmlDatum != null) {
                MmlDatum md = new MmlDatum(work.crntMmlDatum.dat);
                work.crntMmlDatum = md;
            }
        } while ((r.cl & 0xff) > 0);

        r.ds = dsbk;

        r.setSi(sibk);
        r.di = dibk;
        r.setDx(dxbk);
        r.setCx(cxbk);
        r.setBx(bxbk);
        r.setAx(axbk);
    }

    private void operate(byte[] toneBuf, byte[] diBuf) {
        short dxbk = r.getDx();
        short axbk = r.getAx();

        r.al = toneBuf[r.getBx() & 0xffff];
        multi();
        r.al = r.ah;

        r.setDx((short) fade_count);
        if (r.getDx() != 0) {
            r.setDx((short) ((r.getDx() & 0xffff) >> 1));
            r.dl = r.ah;
            r.dl = (byte) ~(r.dl & 0xff);
            r.dl -= 0x80;
            multi();
        }

        r.al = diBuf[r.di & 0xffff];
        r.al += 0x40;
        r.al += (r.ch & 0xff); // ch in ASM style
        outdataa();

        r.setAx(axbk);
        r.setDx(dxbk);
    }

    private void multi() {
        r.al = (byte) ~(r.al & 0xff);
        r.al -= 0x80;
        int res = (r.al & 0xff) * (r.dl & 0xff);
        r.setAx((short) res);
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getAx() & 0xffff)));
        r.al = r.ah;
        r.ah = (byte) ~(r.ah & 0xff);
        r.ah -= 0x80;
    }

    private void tone2608() {
        tone2608(data_buff);
    }

    private void tone2608(byte[] siBuf) {
        short axbk = r.getAx();
        short bxbk = r.getBx();
        short cxbk = r.getCx();
        short dxbk = r.getDx();
        short sibk = r.getSi();
        short dibk = r.di;

        short dsbk = r.ds;

        siBuf[(r.getSi() & 0xffff) + TS] = r.al;
        byte[] tonebuff = tone_adrs();
        r.al = 0x30;
        set_tone(tonebuff);
        volchgm(siBuf);
        r.setBx((short) ((r.getBx() & 0xffff) + 4));
        r.al = 0x50;
        r.cl = 4;
        do {
            set_tone(tonebuff);
            r.al += 0x10;
            r.cl--;
        } while ((r.cl & 0xff) > 0);

        r.al = (byte) 0xb0;
        r.al += (r.ch & 0xff); // ch in ASM style
        r.ah = tonebuff[r.getBx() & 0xffff];
        outdataa();

        r.ds = dsbk;

        r.di = dibk;
        r.setSi(sibk);
        r.setDx(dxbk);
        r.setCx(cxbk);
        r.setBx(bxbk);
        r.setAx(axbk);

        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void set_tone(byte[] tonebuff) {
        short cxbk = r.getCx();
        r.di = 0;
        byte cl = 4;
        do {
            short axbk = r.getAx();
            r.al += out_data[r.di & 0xffff];
            r.al += (r.ch & 0xff); // ch in ASM style
            r.ah = tonebuff[r.getBx() & 0xffff];
            outdataa();
            r.di++;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.setAx(axbk);
            cl--;
        } while (cl > 0);
        r.setCx(cxbk);
    }

    private byte[] tone_adrs() {
        r.setBx((short) (25 * (r.al & 0xff)));
        r.ds = (short) nax.tone;
        return nax.toneBuff;
    }

    private void last_set() {
        r.push(r.getBx());
        r.push(r.getSi());
        calc_nowwork();
        r.al = data_buff[(r.getSi() & 0xffff) + TS];
        tone2608();
        r.al = data_buff[(r.getSi() & 0xffff) + VS];
        volchgm(data_buff);
        r.ah = data_buff[(r.getSi() & 0xffff) + PN];
        pan();
        r.al = data_buff[(r.getSi() & 0xffff) + FD];
        r.ah = data_buff[(r.getSi() & 0xffff) + FD + 1];
        setfreq1();
        r.setSi(r.pop());
        r.setBx(r.pop());
    }

    private void calc_nowwork() {
        r.setSi((short) 0);
        r.setAx((short) 0);
        r.al = (byte) (r.ch & 0xff); // real ch based logic
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getAx() & 0xffff)));
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getAx() & 0xffff)));
    }

    private void hlfo_speed() {
        r.al = 0x22;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        outdata6a();
    }

    private void hlfo_data() {
        r.al = data_buff[(r.getSi() & 0xffff) + PN];
        r.al &= 0xc0;
        r.ah |= r.al;
        data_buff[(r.getSi() & 0xffff) + PN] = r.ah;
        r.al = (byte) 0xb4;
        r.al += (r.ch & 0xff); // ch logic
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        outdataa();
        r.dl = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.pushA();
        r.push(r.ds);
        r.al = data_buff[(r.getSi() & 0xffff) + TS];
        byte[] toneBuff = tone_adrs();
        r.setBx((short) ((r.getBx() & 0xffff) + 12));
        r.al = 0x60;
        r.di = 0;
        r.cl = 4;
        do {
            r.push(r.getAx());
            r.al += out_data[r.di & 0xffff];
            r.al += (r.ch & 0xff); // ch logic
            r.ah = toneBuff[r.getBx() & 0xffff];
            r.ah <<= 1;
            r.carry = ((r.dl & 1) != 0);
            r.dl >>= 1;
            r.ah = r.rcr(r.ah, (byte) 1);
            outdataa();
            r.di++;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.setAx(r.pop());
            r.cl--;
        } while (r.cl != 0);
        r.ds = r.pop();
        r.popA();
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void stopm() {
        r.ch = realch; // ch in ASM style
        if (r.al != r.ch) {
            short cxbk = r.getCx();
            short bxbk = r.getBx();

            r.al = r.ch;
            int bx_ptr = 0; // ofs:chtbl2

            r.al = chtbl2[bx_ptr + (r.al & 0xff)];
            r.ch = r.al; // ch logic
            calcbit();
            r.setAx((short) ~(r.getAx() & 0xffff));
            r.dl = (byte) ~(r.dl & 0xff);
            shflag1 &= (r.getAx() & 0xffff);
            shflag2 &= (r.dl & 0xff);
            r.setBx(bxbk);
            r.setCx(cxbk);
        }

        calcbit();
        skip_data1 |= (r.getAx() & 0xffff);
        skip_data2 |= (r.dl & 0xff);
        if ((r.ch & 0xff) == 16) {
            check_wait();
            if (r.zero) {
                initia0();
                return;
            }
        }

        r.al = 1;
        recovwFlg = true;
    }

    private void set_reg() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        outdata0a();
    }

    private void usr_tone() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        short dsbk = r.ds;
        short dibk = r.di;
        short cxbk = r.getCx();
        short bxbk = r.getBx();

        byte[] toneBuff = tone_adrs();
        r.di = r.getBx();
        r.setBx(bxbk);
        r.setCx((short) 25);
        movtone();

        r.setCx(cxbk);
        r.di = dibk;
        r.ds = dsbk;
    }

    private void movtone() {
        short esbk = r.es;
        short sibk = r.getSi();

        r.setSi(r.getBx());
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getCx() & 0xffff)));

        short tmp = r.ds;
        r.ds = r.es;
        r.es = tmp;

        do {
            nax.toneBuff[r.di & 0xffff] = (byte) nax.objBuf[0][r.getSi() & 0xffff].dat;
            r.di++;
            r.setSi((short) ((r.getSi() & 0xffff) + 1));
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);

        r.setSi(sibk);
        r.es = esbk;
    }

    private void wait_r() {
        r.ch = realch; // ch in ASM style
        calcbit();
        wait_flg1 |= (r.getAx() & 0xffff);
        wait_flg2 |= (r.dl & 0xff);
        check_wait();
        if (r.zero) {
            initia0Flg = true;
            return;
        }
        r.setAx(r.pop());
        r.al = 1;
        recovwFlg = true;
    }

    private void initia0() {
        data_buff[(r.getSi() & 0xffff) + LC] = 1;
        data_buff[(r.getSi() & 0xffff) + AD] = r.bl;
        data_buff[(r.getSi() & 0xffff) + AD + 1] = r.bh;
        r.setAx((short) 0);
        wait_flg1 = (r.getAx() & 0xffff);
        wait_flg2 = r.al;
        initia0Flg = true;
        replay();
    }

    private void calcbit() {
        short cxbk = r.getCx();
        r.setAx((short) 0);
        r.cl = r.ch; // ch in ASM style
        if ((r.cl & 0xff) < 16) {
            r.setAx((short) ((r.getAx() & 0xffff) + 1));
            r.setAx((short) ((r.getAx() & 0xffff) << (r.cl & 0xff)));
            r.dl = 0;
        } else {
            r.dl = 1;
        }
        r.setCx(cxbk);
    }

    private void check_wait() {
        r.setAx((short) (wait_flg1 | skip_data1 | song_flg1));
        r.dl = (byte) (wait_flg2 | skip_data2 | song_flg2);

        r.zero = ((r.getAx() & 0xffff) == 0xffff);
        if (r.zero) {
            r.zero = ((r.dl & 0xff) == 1);
        }
    }

    private void initia() {
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.ah = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat;
        skip_data1 &= (r.getAx() & 0xffff);
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        skip_data2 &= (r.al & 0xff);
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        initia0Flg = true;
    }

    private void lfopara() {
        data_buff[(r.getSi() & 0xffff) + TI] &= 0x73;

        r.al--;
        if (r.al == 0) {
            lfop2();
            return;
        }

        r.al--;
        if (r.al == 0) {
            lfop4();
            return;
        }

        r.al--;
        if (r.al != 0) {
            lfop3();
            return;
        }

        data_buff[(r.getSi() & 0xffff) + TI] |= 4;
        lfop2();
    }

    private void lfop4() {
        data_buff[(r.getSi() & 0xffff) + TI] |= 8;
        lfop2();
    }

    private void lfop2() {
        data_buff[(r.getSi() & 0xffff) + TI] |= (byte) 0x80;
        lfop3();
    }

    private void lfop3() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.ah = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat;
        data_buff[(r.getSi() & 0xffff) + LL] = r.al;
        data_buff[(r.getSi() & 0xffff) + LF] = r.ah;
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.ah = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat;
        data_buff[(r.getSi() & 0xffff) + LD] = r.al;
        data_buff[(r.getSi() & 0xffff) + LI] = r.ah;
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        r.ah = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.ah &= 3;
        r.al = data_buff[(r.getSi() & 0xffff) + LB];
        r.al &= 0xfc;
        r.al |= r.ah;
        data_buff[(r.getSi() & 0xffff) + LB] = r.al;
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.al = (byte) ((r.al & 0xff) >> 2);
        data_buff[(r.getSi() & 0xffff) + LR] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void lfoset() {
        r.ah = data_buff[(r.getSi() & 0xffff) + TI];
        if (r.al == 0) {
            lfostop();
            return;
        }
        r.al--;
        if (r.al == 0) {
            lfoamd();
            return;
        }
        r.al--;
        if (r.al == 0) {
            lfopmd();
            return;
        }
        sync_reset();
        lfos0();
        return;
    }

    private void lfopmd() {
        r.ah |= 0x60;
        lfos1();
    }

    private void lfoamd() {
        r.ah |= 0x40;
        r.ah &= 0xdf;
        lfos1();
    }

    private void lfostop() {
        r.al = data_buff[(r.getSi() & 0xffff) + VS];
        if ((r.ch & 0xff) < 3) { // ch logic
            lstop1();
            return;
        }
        if ((r.ch & 0xff) < 6) { // ch logic
            lstops();
            return;
        }
        if ((r.ch & 0xff) != 10) { // ch logic
            lstop1();
            return;
        }
        volchgr(data_buff);
        lstop();
        return;
    }

    private void lstop1() {
        volchgm(data_buff);
        lstop();
        return;
    }

    private void lstops() {
        lstop();
    }

    private void lstop() {
        r.ah &= 0xbf;
        lfos1();
    }

    private void lfos1() {
        data_buff[(r.getSi() & 0xffff) + TI] = r.ah;
        lfos0();
    }

    private void lfos0() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void sync() {
        r.push(r.getAx());
        r.al = data_buff[(r.getSi() & 0xffff) + TI];
        if ((r.al & 2) != 0) {
            r.setAx(r.pop());
            return;
        }
        if ((r.al & 0x80) == 0) {
            r.setAx(r.pop());
            return;
        }
        sync2();
    }

    private void sync2() {
        data_buff[(r.getSi() & 0xffff) + TI] &= 0xef;
        r.setAx((short) 0);
        data_buff[(r.getSi() & 0xffff) + FC] = r.al;
        data_buff[(r.getSi() & 0xffff) + FC + 1] = r.ah;
        data_buff[(r.getSi() & 0xffff) + RC] = r.al;

        r.setAx(r.pop());
    }

    private void sync_reset() {
        r.push(r.getAx());
        sync2();
    }

    private void sysdetune() {
        data_buff[(r.getSi() & 0xffff) + SE] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void pan() {
        r.al = data_buff[(r.getSi() & 0xffff) + PN];
        r.al &= 0x3f;
        r.ah |= r.al;
        data_buff[(r.getSi() & 0xffff) + PN] = r.ah;
        r.al = (byte) 0xb4;
        r.al = (byte) (r.al + r.ch); // ch logic
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        outdataa();
    }

    private void key_mask() {
        r.push(r.getAx());
        r.al = data_buff[(r.getSi() & 0xffff) + LB];
        r.al &= 0x1f;
        r.ah &= 0xe0;
        r.al |= r.ah;
        data_buff[(r.getSi() & 0xffff) + LB] = r.al;
        r.setAx(r.pop());
        r.al &= 1;
        if (r.al != 0) {
            calcbit();
            keymask1 |= (r.getAx() & 0xffff);
            keymask2 |= (r.dl & 0xff);
        } else {
            calcbit();
            r.setAx((short) ~(r.getAx() & 0xffff));
            r.dl = (byte) ~(r.dl & 0xff);
            keymask1 &= (r.getAx() & 0xffff);
            keymask2 &= (r.dl & 0xff);
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void pcm_change() {
        r.ah = data_buff[(r.getSi() & 0xffff) + PM];
        r.ah &= 0x80;
        r.al |= r.ah;
        data_buff[(r.getSi() & 0xffff) + PM] = r.al;
        ch_change();
    }

    private void setkeyon() {
        data_buff[(r.getSi() & 0xffff) + PM] |= (byte) 0x80;
        data_buff[(r.getSi() & 0xffff) + LI] &= 0x7f;
    }

    private void clearrest() {
        data_buff[(r.getSi() & 0xffff) + LI] &= 0x7f;
    }

    private void main_ssg() {
        getentry();
        int dx_ret = 0;
        do {
            recovFlg = false;
            recovwFlg = false;
            another_ssg1Flg = false;
            dx_ret = 0;

            work.crntMmlDatum = nax.objBuf[0][r.getBx() & 0xffff];
            checkJumpMode(work.crntMmlDatum);
            r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
            r.ah = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat;
            if ((r.al & 0xff) < 0xce) {
                another_ssg();
                if (recovFlg) {
                    dx_ret = 1;
                    break;
                }
                return;
            }
            r.di = 0; // ofs:jmp_table2
            getadrs();
            check_ret();
            if (r.carry) {
                r.setDx((short) 2);
            }
            dx_ret = r.getDx() & 0xffff;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = r.ah;
            jump_table2[(r.di & 0xffff) / 2].run();
            if (another_ssg1Flg) {
                another_ssg1();
            }
            if (recovFlg) {
                dx_ret = 1;
                break;
            }
            if (recovwFlg) {
                dx_ret = 3;
                break;
            }
        } while (dx_ret == 2);

        if (dx_ret == 1) recov();
        else if (dx_ret == 3) recovw();
    }

    private Runnable[] jump_table2;

    private void SetJumptable2() {
        jump_table2 = new Runnable[] {
                this::rest_ssg, this::quit,
                this::quit2, this::stopm,
                this::kwait, this::continue_,
                this::continue_, this::addfreq2,
                this::n_loop, this::noise,
                this::tempoa, this::mlength,
                this::wait_r, this::sdecay,
                this::set_reg, this::sysdetune,
                this::set_env, this::env_speed,
                this::env_type, this::key_mask,
                this::mixer, this::jump_to,
                this::call_to, this::ret_to,
                this::symbol, this::continue_,
                this::sinit, this::if_jump,
                this::if_call, this::volset,
                this::tie2, this::c_loop,
                this::tie_tone, this::ratio_change,
                this::setcomlen, this::initia,
                this::mcomment, this::value,
                this::lfopara, this::lfoset,
                this::voladds, this::volsubs,
                this::attack, this::continue_,
                this::if_exit, this::pops,
                this::fade_outs, this::ssgmode,
                this::channel, this::last_setssg
        };
    }

    private void another_ssg() {
        byte tmp = r.ah;
        r.ah = r.al;
        r.al = tmp;
        data_buff[(r.getSi() & 0xffff) + FB] = 0;
        r.setDx(r.getAx());
        freq_detune_lfo();
        r.setDx((short) ((r.getDx() & 0xffff) - (r.getAx() & 0xffff)));
        r.setAx(r.getDx());
        another_ssg1();
    }

    private void another_ssg1() {
        setkeyon();
        data_buff[(r.getSi() & 0xffff) + FD] = r.al;
        data_buff[(r.getSi() & 0xffff) + FD + 1] = r.ah;
        setfreqs();
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        sync();

        check_mask();
        if (!r.zero) {
            volssg0();
            recovFlg = true;
            return;
        }
        if ((data_buff[(r.getSi() & 0xffff) + TI] & 2) != 0) {
            recovFlg = true;
            return;
        }
        if (ssgpcm != 0) {
            play_ssgpcm();
            return;
        }

        r.setAx((short) 0);
        data_buff[(r.getSi() & 0xffff) + SC] = r.al;
        data_buff[(r.getSi() & 0xffff) + SC + 1] = r.ah;
        data_buff[(r.getSi() & 0xffff) + DC] = r.al;
        data_buff[(r.getSi() & 0xffff) + DC + 1] = r.ah;

        r.al = data_buff[(r.getSi() & 0xffff) + SV];
        r.al++;
        if (r.al != 0) {
            set_attackrate();
        } else {
            r.al = data_buff[(r.getSi() & 0xffff) + VS];
        }

        volssg();
        if (r.carry) {
            recovFlg = true;
            return;
        }
        if (data_buff[(r.getSi() & 0xffff) + PC] == 0) {
            recovFlg = true;
            return;
        }
        r.ah = data_buff[(r.getSi() & 0xffff) + PT];
        r.al = 0xd;
        outdata1sa();
        r.ah = 16;
        r.al = r.ch; // ch in ASM style
        r.al += 5;
        outdata1sa();
        recovFlg = true;
        return;
    }

    private void play_ssgpcm() {
        r.push(r.getSi());
        r.setAx((short) 0);
        r.al = data_buff[(r.getSi() & 0xffff) + DY];
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getAx() & 0xffff)));
        r.di = 0; // ofs:ssgtable
        r.di = (short) ((r.di & 0xffff) + (r.getAx() & 0xffff));
        calc_pcm1adrs();
        r.setAx((short) ((ssgtable[r.di & 0xffff] & 0xff) + (ssgtable[(r.di & 0xffff) + 1] & 0xff) * 0x100));
        pcmNadrs[(r.getSi() & 0xffff) / 2] = (r.getAx() & 0xffff);
        r.setAx((short) ((ssgtable[(r.di & 0xffff) + 2] & 0xff) + (ssgtable[(r.di & 0xffff) + 3] & 0xff) * 0x100));
        pcmNadrs[(r.getSi() & 0xffff) / 2 + 1] = (r.getAx() & 0xffff);
        r.setAx((short) 0);
        pcmNadrs[(r.getSi() & 0xffff) / 2 + 2] = (r.getAx() & 0xffff);
        r.setDx((short) fade_count);
        if ((r.dh & 0xff) < 30) {
            start_intimer();
        }
        r.setSi(r.pop());
        recovFlg = true;
        return;
    }

    private void calc_pcm1adrs() {
        r.setSi((short) 0); // ofs:pcm1adrs
        r.setAx((short) 0);
        r.al = r.ch; // ch logic
        r.al -= 3;
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getAx() & 0xffff)));
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getAx() & 0xffff)));
    }

    private void setfreqs() {
        short dxbk = r.getDx();
        r.dl = r.al;
        r.al = r.ch; // ch logic
        r.al = (byte) ((r.al & 0xff) + (r.al & 0xff));
        r.al -= 5;
        outdata1sa();
        r.al--;
        r.ah = r.dl;
        outdata1sa();
        r.setDx(dxbk);
        return;
    }

    private void addfreq2() {
        addf_main();
        another_ssg1Flg = true;
    }

    private void addf_main() {
        r.setAx((short) ((data_buff[(r.getSi() & 0xffff) + FD] & 0xff) + (data_buff[(r.getSi() & 0xffff) + FD + 1] & 0xff) * 0x100));
        r.dl = r.ah;
        r.ah = r.al;
        r.al = data_buff[(r.getSi() & 0xffff) + FB];
        int ans = (r.getAx() & 0xffff) + (nax.objBuf[0][r.getBx() & 0xffff].dat & 0xff) + (nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat & 0xff) * 0x100;
        r.setAx((short) ans);
        r.carry = ans > 0xffff;
        r.dl += (byte) ((nax.objBuf[0][(r.getBx() & 0xffff) + 2].dat & 0xff) + (r.carry ? 1 : 0));
        data_buff[(r.getSi() & 0xffff) + FB] = r.al;
        r.al = r.ah;
        r.ah = r.dl;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        data_buff[(r.getSi() & 0xffff) + TI] |= 1;
        return;
    }

    private void rest_ssg() {
        if (ssgpcm == 0) {
            volssg0();
            rest0();
            return;
        }

        short sibk = r.getSi();
        calc_pcm1adrs();
        r.setAx((short) 0);
        pcmNadrs[(r.getSi() & 0xffff) / 2] = (r.getAx() & 0xffff);
        pcmNadrs[(r.getSi() & 0xffff) / 2 + 1] = (r.getAx() & 0xffff);
        r.setSi(sibk);
        rest0();
        return;
    }

    private void voladds() {
        add_vol();
        volset();
    }

    private void volsubs() {
        sub_vol();
        volset();
    }

    private void volset() {
        data_buff[(r.getSi() & 0xffff) + VS] = r.al;
        nax.pc98.outportBDummy(port1);
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void volssg() {
        if (data_buff[(r.getSi() & 0xffff) + PC] != 0) {
            volenv();
            return;
        }
        volssg_set();
    }

    private void volssg_set() {
        data_buff[(r.getSi() & 0xffff) + LV] = r.al;
        volssg1();
    }

    private void volssg1() {
        r.setDx((short) fade_count);
        r.ah = r.al;

        r.dl = r.al;
        r.al = r.dh;
        if (r.al != 0) {
            multi();
            r.ah = (byte) ~(r.ah & 0xff);
            r.ah -= 0x80;
        }

        r.ah = (byte) ((r.ah & 0xff) >> 3);
        r.al = r.ch; // ch logic
        r.al += 5;
        outdata1sa();
        r.carry = false;
    }

    private void volssg0() {
        r.al = 0;
        volssg_set();
    }

    private void volenv() {
        r.setDx((short) fade_count);
        r.carry = ((r.dh & 0xff) < 30);
        r.carry = !r.carry;
        if (!r.carry) {
            return;
        }
        r.setAx((short) 0);
        r.al = r.ch; // ch logic
        r.al += 5;
        outdata1sa();
        r.carry = true;
        return;
    }

    private void set_env() {
        data_buff[(r.getSi() & 0xffff) + PC] = r.al;
        r.al = r.ch; // ch logic
        r.al += 5;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        outdata1sa();
    }

    private void env_speed() {
        r.al = 0x0b;
        outdata1sa();
        r.setAx((short) (nax.objBuf[0][r.getBx() & 0xffff].dat & 0xff));
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = 0x0c;
        outdata1sa();
    }

    private void env_type() {
        data_buff[(r.getSi() & 0xffff) + PT] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void noise() {
        if (ssgpcm != 0) {
            set_tonenum();
            return;
        }
        noisef = r.ah;
        r.al = 6;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        outdata1sa();
        return;
    }

    private void set_tonenum() {
        data_buff[(r.getSi() & 0xffff) + DY] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void mixer() {
        r.ah = mixsave;
        r.cl = r.ch; // ch logic
        r.cl -= 3;
        r.al = 0b1001;
        r.al = (byte) ((r.al & 0xff) << (r.cl & 0xff));
        r.ah |= r.al;
        r.ah &= (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = 7;
        outdata1sa();
        r.push(r.getAx());
        calcbit();
        int ans = (r.getAx() & 0xffff) & shflag1;
        r.setAx(r.pop());
        if (ans != 0) {
            return;
        }
        byte ansb = (byte) ((r.dl & 0xff) & shflag2);
        if (ansb != 0) {
            return;
        }
        mixsave = r.ah;
        return;
    }

    private void sdecay() {
        data_buff[(r.getSi() & 0xffff) + SD] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.setAx((short) ((nax.objBuf[0][r.getBx() & 0xffff].dat & 0xff) + (nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat & 0xff) * 0x100));
        data_buff[(r.getSi() & 0xffff) + DV] = r.al;
        data_buff[(r.getSi() & 0xffff) + DY] = r.ah;
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        return;
    }

    private void attack() {
        data_buff[(r.getSi() & 0xffff) + SV] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        data_buff[(r.getSi() & 0xffff) + AR] = r.al;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void symbol() {
        r.setAx((short) ((nax.objBuf[0][r.getBx() & 0xffff].dat & 0xff) + (nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat & 0xff) * 0x100));
        data_buff[(r.getSi() & 0xffff) + SY] = r.al;
        data_buff[(r.getSi() & 0xffff) + SY + 1] = r.ah;
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        return;
    }

    private void mcomment() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if ((r.al & 0xff) == 0xff) {
            comment1();
            return;
        }
        gets();
        if (r.al == (byte) nax.objBuf[0][r.getBx() & 0xffff].dat) {
            comment1();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.ah = 0;
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getAx() & 0xffff)));
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void comment1() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.push(r.di);
        r.di = 0; // ofs:comdata
        for (int i = 0; i < comdataBuf.length; i++) comdataBuf[i] = 0;
        int len = r.al & 0xff;
        while (len > 0) {
            r.ah = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
            comdataBuf[r.di] = r.ah;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.di++;
            len--;
        }
        comdataBuf[r.di] = 0;
        comdata = new String(comdataBuf, nax.myEnc);
        logger.log(Level.INFO, comdata.substring(0, comdata.contains("\0") ? comdata.indexOf("\0") : 0));
        nax.lyric = comdata;
        r.di = r.pop();
        return;
    }

    private void setcomlen() {
        comlength = r.al;
        nax.comlength = comlength;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        return;
    }

    private void ssgmode() {
        if ((r.al & 0x80) == 0) {
            pcm_change();
            return;
        }
        r.al &= 0x7f;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if (r.al == 0) {
            ssgpcm = r.al;
            return;
        }

        r.push(r.getCx());
        r.ch = 3; // ch logic
        do {
            r.setAx((short) 3);
            setfreqs();
            r.ch = (byte) ((r.ch & 0xff) + 1);
        } while ((r.ch & 0xff) != 6);
        r.setCx(r.pop());

        r.setAx((short) 0xb807);
        outdata1a();
        r.al = 1;
        if ((nax.m_mode[0] & 0x80) != 0) {
            r.al++;
        }

        ssgpcm = r.al;
        return;
    }

    private void last_setssg() {
        r.push(r.getBx());
        r.push(r.getSi());
        calc_nowwork();
        r.al = data_buff[(r.getSi() & 0xffff) + TS];
        tone2608();
        r.al = data_buff[(r.getSi() & 0xffff) + VS];
        volssg();
        r.al = data_buff[(r.getSi() & 0xffff) + PC];
        set_env();
        r.al = data_buff[(r.getSi() & 0xffff) + FD];
        r.ah = data_buff[(r.getSi() & 0xffff) + FD + 1];
        setfreqs();
        r.setSi(r.pop());
        r.setBx(r.pop());
        r.ah = mixsave;
        r.al = 7;
        outdata1sa();
    }

    private void main_rhythm() {
        main_pcm();
    }

    private void main_pcm() {
        getentry();
        if (!r.zero) {
            r.al = data_buff[(r.getSi() & 0xffff) + VS];
            volchgr(data_buff);
        }

        int dx_ret;
        do {
            recovFlg = false;
            recovwFlg = false;
            initia0Flg = false;
            dx_ret = 0;

            work.crntMmlDatum = nax.objBuf[0][r.getBx() & 0xffff];
            checkJumpMode(work.crntMmlDatum);
            r.setAx((short) ((nax.objBuf[0][r.getBx() & 0xffff].dat & 0xff) | (nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat << 8)));
            if ((r.al & 0xff) < 0xce) {
                recov();
                return;
            }

            r.di = 0; // ofs:jump_table3
            getadrs();
            check_ret();
            if (r.carry || (r.al & 0xff) == 0xf9)
                r.setDx((short) 2);

            dx_ret = r.getDx() & 0xffff;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = r.ah;
            jump_table3[(r.di & 0xffff) / 2].run();
            if (recovFlg) {
                dx_ret = 1;
                break;
            }
            if (recovwFlg) {
                dx_ret = 3;
                break;
            }
            if (initia0Flg) {
                dx_ret = 4;
                break;
            }
        } while (dx_ret == 2);

        if (dx_ret == 1) recov();
        else if (dx_ret == 3) recovw();
    }

    private Runnable[] jump_table3;
    private boolean jumpMode;

    private void setJumpTable3() {
        jump_table3 = new Runnable[] {
                this::rest2, this::quit,
                this::quit2, this::stopmp,
                this::kwait, this::continue_,
                this::rhythm_on, this::addfreq_pcm,
                this::n_loop, this::pan2,
                this::tempoa, this::mlength,
                this::wait_r, this::dsp_set,
                this::set_reg, this::rhythm_keyon,
                this::rhythm_dump, this::rhythm_pan,
                this::continue_, this::key_mask,
                this::tonepcm, this::jump_to,
                this::call_to, this::ret_to,
                this::symbol, this::continue_,
                this::sinit, this::if_jump,
                this::if_call, this::vol_change2,
                this::tie2, this::c_loop,
                this::repeat, this::ratio_change,
                this::setcomlen, this::initia,
                this::mcomment, this::value,
                this::lfopara, this::lfoset,
                this::voladd2, this::volsub2,
                this::pcm_keyon, this::pcm_adrs,
                this::if_exit, this::pops,
                this::fade_outs, this::pcm_change,
                this::channel, this::last_setpcm
        };
    }

    private void rhythm_keyon() {
        data_buff[(r.getSi() & 0xffff) + TS] = r.al;
        setkeyon();
        check_mask();
        if (r.zero) {
            r.al = 0x10;
            outdata1a();
            r.al = 100;
            rhythm_wait();
        }
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        data_buff[(r.getSi() & 0xffff) + FD] = 1;
        data_buff[(r.getSi() & 0xffff) + FD + 1] = 0;
        return;
    }

    private void rhythm_dump() {
        r.al = 0x10;
        r.ah |= 0x80;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        outdata1a();
        r.al = 100;
        rhythm_wait();
    }

    private void rhythm_pan() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.al = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.di = 0; // ofs:rhytbl
        r.setDx((short) 0);
        r.dl = r.al;
        r.dl -= 0x18;
        r.di = (short) ((r.di & 0xffff) + (r.getDx() & 0xffff));
        r.carry = ((r.di & 0xffff) < 6);
        if (r.carry) {
            rhytbl[r.di & 0xffff] = r.ah;
            outdata1a();
        }
        r.al = 25;
        rhythm_wait();
    }

    private void voladd2() {
        add_vol();
        vol_change2();
    }

    private void volsub2() {
        sub_vol();
        vol_change2();
    }

    private void vol_change2() {
        volset();
        volchgr(data_buff);
    }

    private void volchgr(byte[] siBuf) {
        r.setDx((short) fade_count);
        if (r.getDx() != 0) {
            r.setDx((short) ((r.getDx() & 0xffff) >> 1));
            r.carry = (r.al & 0xff) < (r.dh & 0xff);
            r.al = (byte) (r.al - r.dh);
            if (r.carry) r.al = 0;
        }

        if (check_extpcm(siBuf)) {
            volchgp(siBuf);
            return;
        }

        if ((r.ch & 0xff) != 9) { // realch logic
            volchgp(siBuf);
            return;
        }

        r.al = (byte) ((r.al & 0xff) >> 1);
        r.ah = r.al;
        r.al = 0x11;
        outdata1a();

        r.al = 25;
        rhythm_wait();
    }

    private void rhythm_wait() {
        do {
            nax.pc98.outportB(0x5f, r.al);
            r.al--;
        } while (r.al != 0);
    }

    private void volchgp(byte[] siBuf) {
        calc_pcmwork(siBuf);
        r.carry = (r.al & 0xff) < (r.dh & 0xff);
        r.al = (byte) (r.al - r.dh);
        if (r.carry) r.al = 0;

        nax.pc98.OutportC4231_Volume((byte) (r.di & 0xffff), (byte) 0, r.al);
        r.al = (byte) ((r.al & 0xff) << 1);
        r.al++;
        r.ah = r.al;
        r.al = 0xb;
        outdata2a();
    }

    private Pcm0work[] calc_pcmwork(byte[] siBuf) {
        short axbk = r.getAx();
        r.al = siBuf[(r.getSi() & 0xffff) + PM];
        r.al &= 0x1f;
        if ((r.al & 0xff) > 16) r.al = 16;

        r.ah = 1;
        r.setAx((short) ((r.al & 0xff) * (r.ah & 0xff)));
        r.di = 0; // ofs:pcm0work
        r.di = (short) ((r.di & 0xffff) + (r.getAx() & 0xffff));
        r.setAx(axbk);

        return pcm0work;
    }

    private void pcm_keyon() {
        data_buff[(r.getSi() & 0xffff) + FB] = 0;
        r.ah = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat;
        another_pcm1();
    }

    private void another_pcm1() {
        data_buff[(r.getSi() & 0xffff) + FD] = r.al;
        data_buff[(r.getSi() & 0xffff) + FD + 1] = r.ah;
        setkeyon();
        sync();
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        if (check_86pcm()) {
            extpcm_play();
            return;
        }
        if (realch != 10) { // realch logic
            recovFlg = true;
            return;
        }
        if ((nax.m_mode[3] & 4) != 0) {
            r.setAx((short) ((r.getAx() & 0xffff) << 3));
            short axbk = r.getAx();
            r.ah = r.al;
            r.al = 9;
            outdata2a();
            r.setAx(axbk);
            r.al = 0xa;
            outdata2a();
            if ((data_buff[(r.getSi() & 0xffff) + TI] & 2) == 0) {
                r.setAx((short) 0x100);
                outdata2a();
                check_mask();
                if (r.zero) {
                    r.al = data_buff[(r.getSi() & 0xffff) + VS];
                    volchgr(data_buff);
                    r.setAx((short) 0xa000);
                    r.ah |= data_buff[(r.getSi() & 0xffff) + PT];
                    outdata2a();
                }
            }
        }

        data_buff[(r.getSi() & 0xffff) + PT] = 0;
        rhythm_on();
    }

    private void rhythm_on() {
        recovFlg = true;
    }

    private void extpcm_play() {
        calc_pcmwork(data_buff);
        nax.pc98.OutportC4231_Freq((byte) (r.di & 0xffff), (byte) 1, r.getAx() & 0xffff);
        if ((data_buff[(r.getSi() & 0xffff) + TI] & 2) == 0) {
            check_mask();
            if (r.zero) {
                extpcm_keyon();
            }
        }

        data_buff[(r.getSi() & 0xffff) + PT] = 0;
        recovFlg = true;
    }

    private void extpcm_keyon() {
        r.setAx((short) 0);
        r.al = data_buff[(r.getSi() & 0xffff) + TS];
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getAx() & 0xffff)));
        r.di = (short) (r.getAx() & 0xffff);
        r.setAx((short) ((pcmtable[r.di & 0xffff] & 0xff) + (pcmtable[(r.di & 0xffff) + 1] & 0xff) * 0x100));
        r.setDx((short) ((pcmtable[(r.di & 0xffff) + 2] & 0xff) + (pcmtable[(r.di & 0xffff) + 3] & 0xff) * 0x100));
        r.carry = ((r.getDx() & 0xffff) - (r.getAx() & 0xffff)) < 0;
        r.setDx((short) ((r.getDx() & 0xffff) - (r.getAx() & 0xffff)));
        if (r.carry) {
            r.setDx((short) 0);
        }
        short bxbk = r.getBx();
        r.setBx((short) 0);

        r.carry = (r.getDx() & 0x8000) != 0;
        r.setDx((short) ((r.getDx() & 0xffff) << 1));
        r.setBx(r.rcl(r.getBx(), (byte) 1));

        r.carry = (r.getDx() & 0x8000) != 0;
        r.setDx((short) ((r.getDx() & 0xffff) << 1));
        r.setBx(r.rcl(r.getBx(), (byte) 1));

        r.carry = (r.getDx() & 0x8000) != 0;
        r.setDx((short) ((r.getDx() & 0xffff) << 1));
        r.setBx(r.rcl(r.getBx(), (byte) 1));

        r.carry = (r.getDx() & 0x8000) != 0;
        r.setDx((short) ((r.getDx() & 0xffff) << 1));
        r.setBx(r.rcl(r.getBx(), (byte) 1));

        short cxbk = r.getCx();
        calc_extpcmadr();
        short dibk = r.di;
        short axbk = r.getAx();
        r.setAx(r.di);
        calc_pcmwork(data_buff);
        nax.pc98.OutportC4231_Adrs((byte) (r.di & 0xffff), (byte) 0, r.getAx() & 0xffff);
        nax.pc98.OutportC4231_Adrs((byte) (r.di & 0xffff), (byte) 1, r.getCx() & 0xffff);
        nax.pc98.OutportC4231_Cnt((byte) (r.di & 0xffff), (byte) 0, r.getDx() & 0xffff);
        nax.pc98.OutportC4231_Cnt((byte) (r.di & 0xffff), (byte) 1, r.getBx() & 0xffff);
        r.setAx(axbk);
        r.di = dibk;
        r.setCx(cxbk);
        r.setBx(bxbk);

        if ((fifo_exec & 1) != 0) {
            return;
        }

        short dxbk2 = r.getDx();
        cxbk = r.getCx();
        pcm_start();
        r.setCx(cxbk);
        r.setDx(dxbk2);
    }

    public void calc_extpcmadr() {
        r.setCx((short) 0);

        r.carry = (r.getAx() & 0x8000) != 0;
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        r.setCx(r.rcl(r.getCx(), (byte) 1));

        r.carry = (r.getAx() & 0x8000) != 0;
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        r.setCx(r.rcl(r.getCx(), (byte) 1));

        r.carry = (r.getAx() & 0x8000) != 0;
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        r.setCx(r.rcl(r.getCx(), (byte) 1));

        r.carry = (r.getAx() & 0x8000) != 0;
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        r.setCx(r.rcl(r.getCx(), (byte) 1));

        r.di = r.getAx();
        r.di &= 0x3fff;

        r.carry = (r.getAx() & 0x8000) != 0;
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        r.setCx(r.rcl(r.getCx(), (byte) 1));

        r.carry = (r.getAx() & 0x8000) != 0;
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        r.setCx(r.rcl(r.getCx(), (byte) 1));
    }

    private void addfreq_pcm() {
        addf_main();
        another_pcm1();
    }

    private void repeat() {
        data_buff[(r.getSi() & 0xffff) + PT] = 0x10;
        return;
    }

    private void rest2() {
        rest2_main();
        rest0();
    }

    private void rest2_main() {
        if (!check_extpcm(data_buff)) {
            if ((r.ch & 0xff) == 9) // realch logic
            {
                nax.pc98.outportBDummy(port1);
                return;
            }
            r.setAx((short) 0xb);
            outdata2a();
        }
        calc_pcmwork(data_buff);
        r.setAx((short) 0);
        nax.pc98.OutportC4231_Cnt((byte) (r.di & 0xffff), (byte) 0, r.getAx() & 0xffff);
        nax.pc98.OutportC4231_Cnt((byte) (r.di & 0xffff), (byte) 1, r.getAx() & 0xffff);
    }

    private void stopmp() {
        rest2();
        stopm();
    }

    private void pcm_adrs() {
        r.push(r.getCx());
        r.setCx((short) 4);
        r.al = 2;
        do {
            r.ah = (byte) nax.objBuf[0][r.getBx() & 0xffff].dat;
            outdata2a();
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al++;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while ((r.getCx() & 0xffff) > 0);
        r.setCx(r.pop());
        return;
    }

    private void pan2() {
        data_buff[(r.getSi() & 0xffff) + PN] = r.ah;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if (!check_86pcm()) {
            r.ah &= 0xc0;
            r.al = 1;
            outdata2a();
            return;
        }

        calc_pcmwork(data_buff);

        r.setDx((short) 0xd8f6);
        if ((r.ah & 4) == 0) {
            r.setDx((short) 0x3e3e);
            if ((r.ah & 0x80) == 0) {
                r.setDx((short) 0xf8d0);
                if ((r.ah & 1) == 0) {
                    r.setDx((short) 0xc030);
                }
            }
        }
        nax.pc98.OutportC4231_Pan((byte) (r.di & 0xffff), (byte) 0, r.getDx() & 0xffff);

        r.setDx((short) 0xdcf6);
        if ((r.ah & 8) == 0) {
            r.setDx((short) 0x3e3e);
            if ((r.ah & 0x40) == 0) {
                r.setDx((short) 0xfcd0);
                if ((r.ah & 2) == 0) {
                    r.setDx((short) 0xe430);
                }
            }
        }
        nax.pc98.OutportC4231_Pan((byte) (r.di & 0xffff), (byte) 1, r.getDx() & 0xffff);
    }

    private void tonepcm() {
        data_buff[(r.getSi() & 0xffff) + TS] = r.al;
        tonepcmm();
    }

    private void tonepcmm() {
        int di = (r.al & 0xff) * 2;
        r.setDx((short) ((pcmtable[di + 0] & 0xff) + (pcmtable[di + 1] & 0xff) * 0x100));
        r.setDx((short) ((r.getDx() & 0xffff) << 1));

        r.al = 2;
        r.ah = r.dl;
        outdata2a();
        r.al++;
        r.ah = r.dh;
        outdata2a();

        r.al++;
        r.setDx((short) ((pcmtable[di + 2] & 0xff) + (pcmtable[di + 3] & 0xff) * 0x100));
        r.setDx((short) ((r.getDx() & 0xffff) << 1));
        r.setDx((short) ((r.getDx() & 0xffff) - 1));

        r.ah = r.dl;
        outdata2a();
        r.al++;
        r.ah = r.dh;
        outdata2a();

        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void dsp_set() {
        mode_change();
        r.al = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 1].dat;
        set_mul_level();
        r.al = (byte) nax.objBuf[0][(r.getBx() & 0xffff) + 2].dat;
        r.setBx((short) ((r.getBx() & 0xffff) + 3));
        set_buffer_num();
    }

    private void mode_change() {
        short dsbk = r.ds;
        r.ds = (short) (farjmp1_ >> 16);
        mode_change_sub();
        r.ds = (short) (farjmp2_ >> 16);
        mode_change_sub();
        r.ds = dsbk;
    }

    private void mode_change_sub() {
        short axbk = r.getAx();

        dsp_mode = r.al;
        jump1_ = 0x3e3e;
        nax.pc98.OutportC4231_Jump1(jump1_);
        r.al--;
        if (r.al == 0) {
            jump2_ = 0;
            nax.pc98.OutportC4231_Jump2((byte) jump2_);
            r.setAx(axbk);
            return;
        }
        r.al--;
        if (r.al == 0) {
            jump2_ = 1;
            nax.pc98.OutportC4231_Jump2((byte) jump2_);
            r.setAx(axbk);
            return;
        }
        r.al--;
        if (r.al == 0) {
            jump2_ = 2;
            nax.pc98.OutportC4231_Jump2((byte) jump2_);
            r.setAx(axbk);
            return;
        }

        jump1_ = (0xeb + (dsp_exit_ - jump1_ - 2) * 256);
        nax.pc98.OutportC4231_Jump1(jump1_);

        r.setAx(axbk);
    }

    private void set_mul_level() {
        dsp_level = r.al;
        if ((r.al & 0xff) > 127) {
            r.al = 127;
        }
        short dsbk = r.ds;
        r.ds = (short) farjmp1_;
        set_mul_sub();
        r.ds = (short) farjmp2_;
        set_mul_sub();
        r.ds = dsbk;
    }

    private void set_mul_sub() {
        level1_ = (r.al & 0xff);
        level2_ = r.al;
        level3_ = r.al;
    }

    private void set_buffer_num() {
        r.al += 2;
        if ((r.al & 0xff) >= Nax.MAXBUF) {
            r.al = (byte) Nax.MAXBUF;
        }

        r.ah = 0;
        r.setDx((short) (Nax.FIFO_SIZE * 2));
        long ans = (long) (r.getAx() & 0xffff) * (r.getDx() & 0xffff);
        r.setAx((short) ans);
        r.setDx((short) (ans >> 16));

        fifofin = r.getAx() & 0xffff;
        fifoptr1 = 0;
        fifoend1 = Nax.FIFO_SIZE * 2;
        fifoptr2 = Nax.FIFO_SIZE * 2;
        fifoend2 = Nax.FIFO_SIZE * 4;
    }

    private void last_setpcm() {
        r.push(r.getBx());
        r.push(r.getSi());
        calc_nowwork();
        r.al = data_buff[(r.getSi() & 0xffff) + TS];
        tonepcm();
        r.al = data_buff[(r.getSi() & 0xffff) + VS];
        volchgr(data_buff);
        r.ah = data_buff[(r.getSi() & 0xffff) + PN];
        pan2();
        r.al = data_buff[(r.getSi() & 0xffff) + FD];
        r.ah = data_buff[(r.getSi() & 0xffff) + FD + 1];
        r.push(r.getAx());
        r.ah = r.al;
        r.al = 9;
        outdata2a();
        r.setAx(r.pop());
        r.al = 0x0a;
        outdata2a();
        r.setSi(r.pop());
        r.setBx(r.pop());
        return;
    }

    private void channel() {
        r.push(r.getAx());
        r.setAx((short) 0);
        r.al = realch;
        r.di = 0; // ofs:chtbl2
        r.di = (short) ((r.di & 0xffff) + (r.getAx() & 0xffff));
        r.setAx(r.pop());
        r.al--;
        chtbl2[r.di & 0xffff] = r.al;
        r.ch = r.al; // ch logic
        calcbit();
        shflag1 |= (r.getAx() & 0xffff);
        shflag2 |= (r.dl & 0xff);
        ch_change();
    }

    private void ch_change() {
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        data_buff[(r.getSi() + LC) & 0xffff] = 1;
        data_buff[(r.getSi() + AD) & 0xffff] = r.bl;
        data_buff[(r.getSi() + AD + 1) & 0xffff] = r.bh;
        r.sp = (short) ((r.sp & 0xffff) + 4); // [back_fm],[play_fm]
        rechannelFlg = true;
    }

    public boolean check_86pcm() {
        return (nax.m_mode[3] & 0x10) != 0;
    }

    public boolean check_wsspcm() {
        return ((nax.m_mode[3] & 0x1) != 0);
    }

    private boolean check_emsuse() {
        return ((nax.m_mode[3] & 0x8) != 0);
    }

    private boolean check_extpcm(byte[] siBuf) {
        return (siBuf[(r.getSi() + PM) & 0xffff] & 0x1f) != 0;
    }
}
