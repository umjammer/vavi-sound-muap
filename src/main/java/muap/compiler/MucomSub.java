//
// Music Macro Assembler for MUAP98 <for Extended command>
//
// Copyright (c) 1987,1989-1995 by Packen Software [dec.29.1995]
//

package muap.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import muap.common.X86Register;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.LinePos;
import musicDriverInterface.MetaData;
import musicDriverInterface.MetaData.Tag;
import musicDriverInterface.MmlDatum;
import musicDriverInterface.MmlDatum.MMLType;
import vavi.util.compat.Tuple;


/** */
public class MucomSub {

    private final X86Register r;
    public final Mucom2 mucom2;
    public final Muap98 muap98;
    /** Area to save label addresses (40 labels x 17 channels) */
    public final short[] labelAdrs = new short[40 * 17];
    private final Work work;

    public MucomSub(X86Register r, Mucom2 mucom2, Muap98 muap98, Work work) {
        this.r = r;
        this.mucom2 = mucom2;
        this.muap98 = muap98;
        this.work = work;

        initCmdData();
        initCmdJump();
        InitExCmdTbl();
    }

    //
    // Branch processing for extended command @xxxx
    //
    public void exp_cmd() {
        r.setSi((short) 0); // ofs:cmdData
        r.cl = 0;

//cmd5:
        do {
            r.push(r.getBx());

//cmd2:
            do {
                r.al = (byte) cmdData.charAt(r.getSi());
                r.setSi((short) (r.getSi() + 1));

                if ((r.al & 0xff) == 255) {
                    if (SearchExtendCommand()) return;

                    cmd3(); // Not found
                    return;
                }

                if (r.al == ' ') {
                    cmd1(); // Found
                    return;
                }

                r.dl = (byte) ((r.getBx() & 0xffff) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[r.getBx() & 0xffff]);
                r.setBx((short) ((r.getBx() & 0xffff) + 1));

                if ((r.dl & 0xff) >= 'a') {
                    // Convert to uppercase
                    r.dl -= ' ';
                }

            } while (r.al == r.dl);

//cmd4:
            do {
                r.al = (byte) cmdData.charAt(r.getSi()); // Move to next search character
                r.setSi((short) (r.getSi() + 1));
            } while (r.al != ' ');

            r.setBx(r.pop()); // Restore source address
            r.cl++;
        } while (true);
    }

    private void cmd1() {
        r.setDx(r.pop());
        r.al = r.cl;
        r.ah = 0;
        r.setAx((short) (r.getAx() + r.getAx()));
        r.setSi((short) 0); // ofs:cmdjump
        r.setSi((short) (r.getSi() + r.getAx()));
        r.dh = 0; // For flat/sharp
        cmdjump[(r.getSi() & 0xffff) / 2].run();
    }

    private void cmd3() {
        r.setBx(r.pop());
        tonex(); // To @xx command
    }

    private String cmdData;

    /**
     * Extended command initialization
     */
    private void initCmdData() {
        cmdData = "V W JUMP CALL " // 0
                + "RET LABEL XASM POR " // 4
                + "## ++ -- _ " // 8
                + "+ # - % " // 12
                + "IF SI SO MANU& " // 16
                + "TRN XTRN MTRN XMTRN " // 20
                + "MOR XMOR CAD XCAD " // 24
                + "IDM XIDM TRI TRS " // 28
                + "SACF SACS XMMTRN ACS " // 32
                + "ACF AMTRN XAMTRN RP " // 36
                + "CODEIN CODEOUT HARM ARP " // 40
                + "NOOUT CODE BASS XCOM " // 44
                + "< > RT COM " // 48
                + "SC ACCEL RIT DEBUG " // 52
                + "STOP MAX START RS " // 56
                + "LFO DT ENDIF PAN " // 60
                + "HLS HL PA POP " // 64
                + "DS INIT QS QL " // 68
                + "QX CSI CSO CSC " // 72
                + "SLS SL FON F+ " // 76
                + "F- KM KD @ " // 80
                + "SD / FO AV " // 84
                + "PV SSG PCM Q " // 88
                + "CH SRET REV " // 92
                + "L M R AP " + (char) 255; // 95
    }

    private Runnable[] cmdjump;

    /**
     * Extended command jump table initialization
     */
    private void initCmdJump() {
        cmdjump = new Runnable[] {
                this::finevol, this::kwait, this::jump_to, this::call_to, // 0
                this::ret_to, this::label_to, mucom2::theend, this::porta, // 4
                this::setf4, this::setf4, this::setf5, this::set_flat, // 8
                this::setf1, this::setf1, this::setf2, this::setf3, // 12
                this::if_jump, this::slur_in, this::slur_out, this::manual_tie, // 16
                this::trn_ent, this::xtrn_ent, this::mtrn_ent, this::xmtrn_ent, // 20
                this::mor_ent, this::xmor_ent, this::cad_ent, this::xcad_ent, // 24
                this::idm_ent, this::xidm_ent, this::tri_ent, this::trspeed, // 28
                this::sacf_ent, this::sacs_ent, this::xmmtrn_ent, this::acc_ent, // 32
                this::xacc_ent, this::amtrn_ent, this::xamtrn_ent, this::rhythm_pat, // 36
                mucom2::codein, mucom2::codeout, this::harm, mucom2::arpeggio, // 40
                this::wait_mode, mucom2::code_exe, mucom2::bass, this::comment_mode, // 44
                this::cresc, this::decresc, this::rhythm_set, this::mcomment, // 48
                mucom2::same_code, this::accel, this::ritard, this::set_debug, // 52
                mucom2::stopm, mucom2::set_max, this::initia, this::rhythm_pan, // 56
                this::lfo_set, this::detune, this::mendif, this::pan, // 60
                this::hlfo_speed, this::hlfo_data, this::pcm_adrs, this::pops, // 64
                this::detune_shift, this::sinit, this::macro_small, this::macro_large, // 68
                this::macro_exit, this::comstepin, this::comstepout, this::comstepcut, // 72
                this::slide_set, this::slide, this::clear_mode, this::freq_add, // 76
                this::freq_sub, this::key_maskset, this::key_maskreset, MucomSub::ifch1, // 80
                this::sysdetune, this::if_abort, this::fade_out, this::acc_vol, // 84
                this::down_vol, this::ssgmode, this::pcmmode, this::subratio, // 88
                this::channel, this::last_set, this::reverve, // 92
                this::pan_left, this::pan_mono, this::pan_right, this::auto_pan, // 95
        };
    }

    /**
     * Tone setting command processing
     */
    private void tonex() {
        mucom2.rednums(); // Read numeric value from text
        r.dl = r.al; // DL = tone number
        mucom2.chktxt();
        if (r.al == '=') {
            // Is it a replacement specification?
            tone_change();
            return;
        }
        if (r.al == 0x22) {
            // Is it a user PCM specification?
            pcm_load();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        xchg_tone(); // Replacement of tone number
        if ((r.ch & 0xff) >= 4) {
            if ((r.ch & 0xff) < 7) {
                // Skip if SSG
                retssg();
                return;
            }
            if ((r.ch & 0xff) == 10) {
                // Rhythm is @1-@63
                save_rhythm();
                return;
            }
            if ((r.ch & 0xff) >= 10) {
                if ((r.ch & 0xff) <= 11) {
                    r.cl = 6;
                    if ((r.al & 0xff) > Mucom2.MAXPCM - 1) {
                        // PCM is @0-@89
                        mucom2.error();
                        return;
                    }
                }
            }
        }

//nowfm_tone:
        r.test(mucom2.optimiz, (byte) 1);
        if (r.zero || r.al != mucom2.opt_tne) {
            // If the same as the previously set tone, do not set
//tonex1:
            mucom2.opt_tne = r.al; // Store the set tone number
            mucom2.optimiz |= 1; // Flag indicating it was set
            r.ah = r.al;

            r.al = (byte) 0xeb; // Set tone number
            LinePos linePos = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
            linePos.chip = work.crntChip;
            linePos.chipNumber = 0;
            linePos.ch = (byte) work.crntChannel;
            linePos.part = work.crntPart;
            MmlDatum md = new MmlDatum(r.al & 0xff, MMLType.Instrument, linePos, 0, r.ah & 0xff);
            md = work.FlashLstMd(md);
            muap98.objectBuf.set(r.di, md);

            muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah & 0xff));
            r.di += 2;
            return;
        }

        retssg();
    }

    private void retssg() {
        if (mucom2.ssgpcmm == 0) {
//ssgexit:
            return;
        }

        r.cl = 6;
        if ((r.al & 0xff) > 19) {
            mucom2.error();
            return;
        }

        r.ah = r.al; // AH = tone number
        r.al = (byte) 0xf6; // Reuse n command
        muap98.objectBuf.set(r.di, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah & 0xff));
        r.di += 2;
    }

    private void save_rhythm() {
        r.cl = 6;
        if (r.al == 0 || (r.al & 0xff) > 63) {
            mucom2.error();
            return;
        }

        mucom2.rhydata = r.al; // Key-on when next K is encountered
    }

    private void xchg_tone() {
        r.push(r.ds);
        r.push(r.getSi());
        r.ds = (short) Muap98.text;
        r.setSi((short) 0); // TONEOFS // DS:SI = address of tone replacement table
        r.dh = 0;
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getDx() & 0xffff)));
        r.al = mucom2.TONEOFSbuf[r.getSi() & 0xffff]; // AL = Converted tone number
        r.setSi(r.pop());
        r.ds = r.pop();
    }

    //
    // Tone number replacement specification
    //
    private void tone_change() {
        mucom2.chktxt();
        if (r.al != '@') {
            // Also allow @x=@y notation
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
        }
//tonec1:
        mucom2.rednums(); // AL = Destination tone number
        r.push(r.ds);
        r.push(r.getSi());
        r.ds = (short) Muap98.text;
        r.setSi((short) 0); // DS:SI = address of tone replacement table
        r.dh = 0;
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getDx() & 0xffff))); // SI = Address of the corresponding tone
        mucom2.TONEOFSbuf[r.getSi() & 0xffff] = r.al;
        r.setSi(r.pop());
        r.ds = r.pop();
    }

    /**
     * Switching SSG/PCM mode
     */
    private void ssgmode() {
        mucom2.ssgpcmm = 0;
        r.setAx((short) 0x80d0);
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * Switching to PCM mode
     */
    private void pcmmode() {
        r.al = mucom2.ope_no; // AL = 1-17
        r.cl = 41;
        if (r.al == 11) {
            mucom2.error();
            return;
        }
        if ((r.al & 0xff) <= 3) {
            pcm4_mode();
            return;
        }
        if ((r.al & 0xff) > 6) {
            pcm4_mode();
            return;
        }
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        chknum(); // Number check
        if (!r.carry) {
            pcm4_mode();
            return;
        }
        mucom2.ssgpcmm = 1; // SSGPCM mode
        r.setAx((short) 0x81d0);
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * Switching to extended PCM
     */
    private void pcm4_mode() {
        mucom2.rednums();
        r.cl = 6;
        if ((r.al & 0xff) > 6) {
            // @PCM0,1-16
            mucom2.error();
            return;
        }
        r.ah = r.al;
        r.al = (byte) 0xd0;
        mucom2.stoswObjBufAX2DI();
        if (r.ah != 0) {
            // pcm4mode1
            mucom2.sendch = 11; // Assemble as PCM
            return;
        }
        r.al = mucom2.ope_no; // Restore
        mucom2.sendch = r.al;
    }

    //
    // DSP command specification
    //

    private void reverve() {
        r.zero = (mucom2.sendch == 11); // PCM mode?
        r.cl = 32;
        if (!r.zero) {
            mucom2.error();
            return;
        }
        r.al = (byte) 0xf2;
        mucom2.stosbObjBufAL2DI();
        r.dl = 3; // Range
        read_check(); // DSP mode
        chkcm();
        r.dl = 127;
        read_check(); // Level
        chkcm();
        r.dl = (byte) (Mucom2.MAXBUF - 2);
        read_check(); // Delay time
    }

    private void read_check() {
        mucom2.rednums();
        r.cl = 6;
        if ((r.al & 0xff) > (r.dl & 0xff)) {
            mucom2.error();
            return;
        }
        mucom2.stosbObjBufAL2DI();
    }

    //
    // @Q command processing
    //
    private void subratio() {
        mucom2.rednums();
        mucom2.ratdata = r.al;
        mucom2.ratmode |= 1;
        r.al = mucom2.chglen;
        mucom2.setrat();
    }

    /**
     * Single character replacement specification
     */
    private void macro_small() {
        r.al = 1; // Lowercase mode
        mac_set();
    }

    /**
     * Uppercase conversion mode
     */
    private void macro_large() {
        r.al = 2; // Uppercase mode
        mac_set();
    }

    /**
     * Release replacement
     */
    private void macro_exit() {
        r.al = 0; // Release
        mac_set();
    }

    private void mac_set() {
        mucom2.mac_mod = r.al;
    }

    //
    // Setting accent level
    //

    private void acc_vol() {
        mucom2.rednums();
        mucom2.accbase = r.al;
    }

    //
    // Setting temporary volume down level
    //

    private void down_vol() {
        mucom2.rednums();
        mucom2.dwnbase = r.al;
    }

    //
    // @V command (Fine volume setting)
    //

    private void finevol() {
        mucom2.rednums(); // Read numeric value from text
        r.carry = ((r.getAx() & 0xffff) < 128); // Range check
        r.cl = 6;
        if (!r.carry) {
//error5:
            mucom2.error();
            return;
        }
        r.ah = r.al;
        mucom2.retvol2();
    }

    //
    // @W command processing
    //
    private void kwait() {
        mucom2.set_symbol2();
        cres_check(); // Crescendo check
        check_tiemode(); // Auto-tie specification?
        if (r.zero) {
            r.al = (byte) 0xe1; // Store tie command
            mucom2.stosbObjBufAL2DI();
        }
//kwtie0:
        mucom2.tnelnmx(); // Read and convert note length from text
        mucom2.tnelnx();
        kwait0();
    }

    private void kwait0() {
        r.al = (byte) 0xfb;
        mucom2.dionpu = r.di; // Save note playing address
        mucom2.stosbObjBufAL2DI(); // Dummy data
    }

    //
    // @HARM command processing
    //
    private void harm() {
        mucom2.rednums(); // Get chord skip number
        r.cl = 10;
        if ((r.al & 0xff) > 15) {
//error5:
            mucom2.error();
            return;
        }
        mucom2.harmno = r.al; // Save chord skip data
    }

    /**
     * PCM read specification
     * entry DL = Tone number
     */
    private void pcm_load() {
        r.setSi((short) 0x24); // ES:SI = UsrPCM header address
        if ((byte) muap98.objectBuf.get(r.di - 1).dat == (byte) 0x88) {
            // Is it specified just before?
//_load1:
            r.di--; // Crush identification code if continuous specification
            int val = (muap98.objectBuf.get(r.getSi() & 0xffff).dat & 0xff) | ((muap98.objectBuf.get((r.getSi() & 0xffff) + 1).dat & 0xff) << 8);
            r.setSi((short) val);
            int ans = (muap98.objectBuf.get((r.getSi() & 0xffff) - 2).dat & 0xff) | ((muap98.objectBuf.get((r.getSi() & 0xffff) - 1).dat & 0xff) << 8);
            ans += 14;
            muap98.objectBuf.get((r.getSi() & 0xffff) - 2).dat = (byte) ans;
            muap98.objectBuf.get((r.getSi() & 0xffff) - 1).dat = (byte) (ans >> 8);
        } else {
            r.setAx((short) 0x13ea); // Store @jump instruction
            mucom2.stoswObjBufAX2DI();
            r.al = 0;
            mucom2.stosbObjBufAL2DI();
            muap98.objectBuf.get(r.getSi() & 0xffff).dat = (byte) r.di;
            muap98.objectBuf.get((r.getSi() & 0xffff) + 1).dat = (byte) (r.di >> 8); // Store command identification code start address
            r.al = (byte) 0x88;
            mucom2.stosbObjBufAL2DI(); // Store identification code
        }
//_load10:
        r.al = r.dl; // AL = read tone number (0-19, 50-69)
        r.cl = 6;
        int alVal = (r.al & 0xff);
        if (alVal >= 20 && alVal < 50) {
            mucom2.error();
            return;
        } else if (alVal > 89) {
            // Range check
            mucom2.error();
            return;
        }
//_load11:
        mucom2.stosbObjBufAL2DI();
        r.setCx((short) 8);
//_load3:
        do {
            mucom2.chktxt();
            if (!r.carry) {
                xsmall(); // Lowercase conversion for non-kanji
            }
            if ((r.al & 0xff) <= ' ') {
                r.cl = 39;
                mucom2.error();
                return;
            }
            if (r.al == 0x22) {
                // End specification
                // _load5
                r.setCx((short) ((r.getCx() & 0xffff) + 3)); // Include extension part
                _load7();
                return;
            }
            if (r.al == '.') {
                // Extension follows
//_load2:
                do {
                    r.al = 0x20;
                    mucom2.stosbObjBufAL2DI(); // Fill missing parts of 8-character filename
                    r.setCx((short) ((r.getCx() & 0xffff) - 1));
                } while (r.getCx() != 0);
                _load4();
                return;
            }
            mucom2.stosbObjBufAL2DI();
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        mucom2.chktxt();
        if (r.al != '.') {
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
        }
        _load4();
    }

    private void _load4() {
        r.setCx((short) 3);
//_load8:
        do {
            mucom2.chktxt();
            if (!r.carry) {
                xsmall(); // Lowercase conversion for non-kanji
            }
            if ((r.al & 0xff) <= ' ') {
                r.cl = 39;
                mucom2.error();
                return;
            }
            if (r.al == 0x22) {
                // End specification
                _load7();
                return;
            }
            mucom2.stosbObjBufAL2DI();
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        mucom2.chktxt();
        if (r.al != 0x22) {
            r.cl = 32;
            mucom2.error();
            return;
        }
        _load9();
    }

    private void _load7() {
        r.al = 0x20;
//_load6:
        do {
            mucom2.stosbObjBufAL2DI(); // Fill the rest if no extension
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        _load9();
    }

    private void _load9() {
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.zero = (r.al == ','); // Volume specified? (for SSGPCM)
        r.al = 16; // Default volume
        if (r.zero) {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            mucom2.rednums(); // AX = volume (0-127)
            r.cl = 6;
            r.al += mucom2.volsft;
            if ((r.al & 0xff) > 127) {
                mucom2.error();
                return;
            }
        }

//_load12:
        r.ah = 0;
        r.setAx((short) ((r.getAx() & 0xffff) << 7));
        if ((r.dl & 0xff) >= 20) {
            // Not SSGPCM, so 256
            r.setAx((short) 256);
        }
        mucom2.stoswObjBufAX2DI(); // Store volume
        r.al = (byte) 0x88;
        mucom2.stosbObjBufAL2DI(); // Store final identifier
    }

    //
    // Chord specification skip by "/"
    //

    public void harm_onpu() {
        do {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.cl = 32;
            if ((r.al & 0xff) >= 0xfe) {
                // CR, LF, error end
//error24:
                mucom2.error();
                return;
            }
            if (r.al == (byte) ':') {
//harmon1:
                return;
            }
            if (r.al == (byte) '@') {
                r.push((short) (mucom2.octdata & 0xff));
                mucom2.mode[0] |= 4;
                exp_cmd(); // Extended command @+, @-, @%
                mucom2.octdata = (byte) (r.pop() & 0xff);
            }
        } while (true);
    }

    // Disable auto-tie for &

    private void manual_tie() {
        mucom2.mode[0] |= 8;
    }

    //
    // Frequency addition/subtraction
    //

    private void freq_add() {
        mucom2.rednum();
        r.setDx((short) 0);
        fadd3();
    }

    private void fadd3() {
        r.ch = mucom2.sendch;
        if ((r.ch & 0xff) == 10) {
//fadd2:
            return;
        }
        if ((r.ch & 0xff) >= 4) {
            if ((r.ch & 0xff) < 7) {
                r.setAx((short) -(r.getAx() & 0xffff)); // Invert only for SSG
                r.setDx((short) ~(r.getDx() & 0xffff));
            }
        }
//fadd1:
        r.push(r.getAx());
        r.al = (byte) 0xf8; // +++
        mucom2.stosbObjBufAL2DI();
        r.setAx(r.pop());
        mucom2.stoswObjBufAX2DI();
        r.al = r.dl;
        r.ah = (byte) 0xe1; // Also store tie +++
        mucom2.stoswObjBufAX2DI();
//fadd2:
        return;
    }

    private void freq_sub() {
        mucom2.rednum();
        r.setDx((short) 0);
        r.setDx((short) (r.getDx() - 1));
        r.setAx((short) -(r.getAx() & 0xffff));
        fadd3();
    }

    //
    // Pop playing stack
    //

    private void pops() {
        r.al = (byte) 0xd2;
        mucom2.stosbObjBufAL2DI();
    }

    //
    // Initialize playing stack
    //

    private void sinit() {
        r.al = (byte) 0xe5;
        mucom2.stosbObjBufAL2DI();
    }

    //
    // Switching transmission channel
    //

    private void channel() {
        mucom2.rednums();
        r.cl = 6;
        if ((r.al & 0xff) > 17) {
            mucom2.error();
            return;
        }
        if (r.al == 0) {
            mucom2.error();
            return;
        }
        mucom2.sendch = r.al; // Save send channel (1-17)
        muap98.objectBuf.set(r.di, new MmlDatum(0xcf));
        r.di++;
        mucom2.stosbObjBufAL2DI();
        mucom2.mode[1] |= 8; // Output tone number before notes every time
    }

    //
    // Switch to previous tone/volume
    //

    private void last_set() {
        r.al = (byte) 0xce;
        mucom2.stosbObjBufAL2DI();
    }

    //
    // Processing of @if jump/call/then/exit
    //

    private void if_jump() {
        mucom2.chktxt();
        if (r.al == '#') {
            // Channel condition?
            if_channel();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        mucom2.chkval(); // Variable check
        if (r.carry) {
            if_norm(); // AH = 6-F (Variable area specification)
            return;
        }
        mucom2.chktxt();
        r.cl = 32;
        r.dh = 0;
        if (r.al == '=') {
            if_match();
            return;
        }
        r.dh = 0x10;
        if (r.al == '>') {
            if_match();
            return;
        }
        r.dh = 0x20;
        if (r.al == '<') {
            if_match();
            return;
        }
        r.dh = 0x30;
        if (r.al == '!') {
            if_match();
            return;
        }

        mucom2.error();
    }

    private void if_match() {
        mucom2.ifflag |= 1; // Execution of @if.. (for total length)
        r.push(r.getAx());
        mucom2.rednums(); // AL = variable condition value
        r.dl = r.al;
        r.setAx(r.pop());
        r.ah |= r.dh;

        if_value();
    }

    private void if_norm() {
        mucom2.ifflag |= 1; // Execution of @if.. (for total length)
        mucom2.rednums(); // AL = condition value
        r.dl = r.al;
        r.ah = mucom2.nesting; // Nest value
        r.carry = ((r.ah & 0xff) < 1);
        r.ah = (byte) ((r.ah & 0xff) - 1);
        r.cl = 25; // IF command outside ()
        if (r.carry) {
            mucom2.error();
            return;
        }
        if_value();
    }

    private void if_value() {
        mucom2.chktxt(); // Get next character
        xsmall(); // AL uppercase conversion
        r.cl = 32;
        if (r.al == 'J') {
            // Processing of @IF ... JUMP
            if_jump0();
            return;
        }
        if (r.al == 'C') {
            // Processing of @IF ... CALL
            if_call0();
            return;
        }
        if (r.al == 'T') {
            // Processing of @IF ... THEN
            if_then0();
            return;
        }
        if (r.al == 'E') {
            // Processing of @IF ... EXIT
            if_exit0();
            return;
        }
//error6:
        mucom2.error();
    }

    private void if_jump0() {
        mucom2.ifflag |= 1; // Execution of @if.. (for total length)
        r.al = (byte) 0xe4;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
        r.al = r.dl; // Jump condition value
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        chgax("UMP");
        if_quit();
    }

    private void if_quit() {
        get_val(); // Label number
        if_quit1();
    }

    private void if_quit1() {
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
    }

    private void if_call0() {
        mucom2.ifflag |= 1; // Execution of @if.. (for total length)
        r.al = (byte) 0xe3;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
        r.al = r.dl; // Jump condition value
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        chgax("ALL");
        if_quit();
    }

    private void if_then0() {
        mucom2.ifflag |= 1; // Execution of @if.. (for total length)
        r.al = (byte) 0x80; // AL = @if then identifier (path 2 rewritten to e4)
        if_sub();
        r.ah ^= 0x30;
        muap98.objectBuf.get(r.di - 2).dat = r.ah; // Invert condition value
        r.ah &= 0x30;
        if (r.ah == 0x10) {
            muap98.objectBuf.get(r.di - 1).dat = (byte) ((muap98.objectBuf.get(r.di - 1).dat & 0xff) - 1);
        }
        if (r.ah == 0x20) {
            muap98.objectBuf.get(r.di - 1).dat = (byte) ((muap98.objectBuf.get(r.di - 1).dat & 0xff) + 1);
        }
        chgax("HEN");
        r.dh = mucom2.nesting;
        r.dl = 1; // @if then mode
        pushif();
        if_quit1();
    }

    private void if_exit0() {
        r.al = (byte) 0x81; // AL = @if exit identifier (path 2 rewritten to d3)
        if_sub();
        chgax("XIT");
        if_exit1();
    }

    private void if_exit1() {
        r.dh = mucom2.nesting;
        if (r.dh == 0) {
            // Error outside loop
//error16:
            mucom2.error();
            return;
        }
        r.dl = 2; // @if exit mode
        pushif();
        mucom2.exit_looplen();
        if_quit1();
    }

    private void if_sub() {
        r.cl = 35;
        if ((mucom2.jumpnes & 0xff) >= 32) {
//error16:
            mucom2.error();
            return;
        }
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
        r.al = r.dl; // Jump condition value
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
    }

    //
    // Channel conditional branch
    //
    private void if_channel() {
        r.dl = 0; // Match flag
//ifch5:
        while (true) {
            mucom2.rednums();
            r.dh = r.al;
            if (r.al == mucom2.ope_no) {
                r.dl = 1;
            }
//ifch2:
            do {
                mucom2.chktxt();
                if (r.al == ',') {
                    // Multiple specified
                    break;
                }
                if (r.al != '-') {
                    ifch3();
                    return;
                }
                mucom2.rednums(); // Continuous specification
                if ((r.al & 0xff) < (mucom2.ope_no & 0xff)) {
                    continue;
                }
                if ((r.dh & 0xff) > (mucom2.ope_no & 0xff)) {
                    continue;
                }
                r.dl = 1;
            } while (true);
        }
    }

    private void ifch3() {
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        if (r.dl != 0) {
            // Consistent with current channel
            return;
        }
//ifch4:
        do {
            mucom2.chktxt(); // Search @@
            if (r.al != '@') {
                continue;
            }
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            if (r.al != '@') {
                continue; // Do not assemble until @@
            }
            break;
        } while (true);
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
//ifch1:
        return;
    }

    private static void ifch1() {
        return;
    }

    //
    // Loop exit processing at the end (@/)
    //
    private void if_abort() {
        r.al = (byte) 0x81; // AL = @if exit identifier (path 2 rewritten to d3)
        if_sub();
        r.push(r.getBx());
        r.al = mucom2.nesting;
        r.ah = 0;
        int val = (r.getAx() & 0xffff) - 1;
        val <<= 2;
        int bxIdx = (2 + val);
        mucom2.stttbl[bxIdx / 2] = (short) (r.di - 1); // Store address to store counter loop value
        r.setBx(r.pop());
        if_exit1();
    }

    //
    // Grammar check for Jump,Call,Then,Exit
    //
    private void chgax(String str) {
        int siIdx = 0;
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.ah = muap98.sourceBuf[(r.getBx() & 0xffff) + 1]; // AX, DL uppercase conversion
        r.cl = 32;
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        r.dl = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        xsmall(); // AL uppercase conversion
        if ((r.ah & 0xff) >= 'a') r.ah -= (byte) ' ';
        if ((r.dl & 0xff) >= 'a') r.dl -= (byte) ' ';

        if (r.al != (byte) str.charAt(siIdx) || r.ah != (byte) str.charAt(siIdx + 1)) {
//error16:
            mucom2.error();
            return;
        }
        siIdx += 2;
        if (r.dl != (byte) str.charAt(siIdx)) {
//error16:
            mucom2.error();
        }
    }

    //
    // Push to IF stack
    // entry	DL = mode
    //       	DH = nest value
    //       	DI = address
    //
    private void pushif() {
        r.push(r.ds);
        r.push(r.getBx());
        int bxBase = (mucom2.jumpnes & 0xff) << 2; // DS:BX = address of if stack
        // r.ds = muap98.text;
        mucom2.IFSTACKbuf[bxBase] = r.dl;
        mucom2.IFSTACKbuf[bxBase + 1] = r.dh;
        mucom2.IFSTACKbuf[bxBase + 2] = (byte) r.di;
        mucom2.IFSTACKbuf[bxBase + 3] = (byte) (r.di >> 8);
        r.setBx(r.pop());
        r.ds = r.pop();
        mucom2.jumpnes++;
    }

    //
    // Processing of @jump,@call,@ret,@label
    //
    private void jump_to() {
        get_val2();
        r.ah = r.al;
        r.al = (byte) 0xea;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
        r.di++; // Leave area for offset
    }

    private void call_to() {
        get_val2();
        r.ah = r.al;
        r.al = (byte) 0xe9;
        mucom2.stoswObjBufAX2DI();
        r.di++;
        r.al = mucom2.chglen; // Set previous note length and ratio
        mucom2.setrat();
        mucom2.optimiz = 0; // Clear optimization flag
    }

    private void ret_to() {
        r.al = (byte) 0xe8;
        mucom2.stosbObjBufAL2DI();
        mucom2.optimiz = 0; // Clear optimization flag
    }

    private void label_to() {
        get_val2();
        r.push(r.getSi());
        int labelIdx = (r.al & 0xff) + ((r.ch & 0xff) - 1) * 40;
        labelAdrs[labelIdx] = r.di; // Save label address
        int bxIdx = (r.al & 0xff) * 2;
        muap98.bufbuf[bxIdx] = (byte) r.di; // Store start address
        muap98.bufbuf[bxIdx + 1] = (byte) (r.di >> 8);
        r.setSi(r.pop());
        r.al = mucom2.chglen; // Set previous note length and ratio
        mucom2.setrat();
        mucom2.optimiz = 0; // Clear optimization flag
    }

    private void get_val2() {
        mucom2.rednums(); // Get label variable (for absolute jump)
        r.zero = (r.al == 39);
        r.carry = ((r.al & 0xff) < 39);
        getv1();
    }

    private void getv1() {
        r.cl = 6;
        if (!r.zero && !r.carry) {
            mucom2.error();
            return;
        }
    }

    private void get_val() {
        mucom2.rednums(); // Get label variable (for @if)
        r.zero = (r.al == 31);
        r.carry = ((r.al & 0xff) < 31);
        getv1();
    }

    //
    // Pan setting
    //
    private void pan() {
        mucom2.chktxt();
        xsmall();
        if (r.al == (byte) 'L') pan_left();
        else if (r.al == (byte) 'R') pan_right();
        else if (r.al == (byte) 'M') pan_mono();
        else {
            r.cl = 32;
            mucom2.error();
        }
    }

    private void pan_left() {
        check_panm();
        r.ah = (byte) 0x80; // @L
        if (!r.carry) {
            r.ah = (byte) 0x82; // @LM
            if (r.zero) r.ah = 6; // @LK
        }
        pan_set();
    }

    private void pan_right() {
        check_panm();
        r.ah = (byte) 0x40; // @R
        if (!r.carry) {
            r.ah = (byte) 0x41; // @RM
            if (r.zero) r.ah = 9; // @RK
        }
        pan_set();
    }

    private void pan_mono() {
        check_panm();
        r.ah = (byte) 0xc0; // @M
        if (!r.carry) {
            r.ah = (byte) 0xc4; // @MK
            if (r.zero) r.ah = (byte) 0xcc; // @MM
        }
        pan_set();
    }

    //
    // Pan bit definition
    // b0 = left 1/2   b1 = right 1/2
    // b2 = left neg   b3 = right neg
    // b7 = left out   b6 = right out
    private void pan_set() {
        int chVal = (r.ch & 0xff);
        if (chVal < 4) {
//pan1:
            set_pan();
//pan0:
            pan_init(); // Auto-pan initialization
            return;
        }
        if (chVal < 7) {
            // SSG is ignored
//pan0:
            pan_init(); // Auto-pan initialization
            return;
        }
        if (chVal == 10) {
            // For rhythm
            pan2();
            return;
        }
//pan1:
        set_pan();
//pan0:
        pan_init(); // Auto-pan initialization
    }

    //
    // Pan specification check
    // exit	@L = CY
    //      @LM = NC,NZ
    //      @LK = NC,Z
    private void check_panm() {
        if (mucom2.sendch != 11) {
            // Permitted only in PCM mode
//chkm3:
            r.zero = false;
            r.carry = true;
            return;
        }
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if (muap98.sourceBuf[(r.getBx() & 0xffff) - 2] >= 'a') {
            // Was the previous character lowercase?
            r.al = (byte) ((r.al & 0xff) - ' ');
        }

        if (r.al == (byte) 'M') {
            // @LM
//chkm1:
            r.carry = false;
            r.zero = (r.al == 0);
            return;
        }
        if (r.al == 'K') {
            // @LK
//chkm2:
            r.al = 0;
//chkm1:
            r.carry = false;
            r.zero = (r.al == 0);
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
//chkm3:
        r.zero = false;
        r.carry = true;
    }

    //
    // Pan specification for rhythm sound source
    private void pan2() {
        chkcm(); // Comma check
        mucom2.chktxt(); // Take one character
        xsmall(); // Uppercase conversion
        r.dl = r.ah;
        setrt(); // Rhythm type check
        if (r.al == 0) {
            // error21
        }
        r.ch = 0;
        int idx = (r.getCx() & 0xffff); // SI = rhythm volume buffer
        r.dl |= mucom2.rhyvol[idx]; // DL = pan + volume
        r.cl += 0x18; // CL = output address
        r.al = r.dl;
        r.ah = r.cl;
        set_rhythmpan(); // Store rhythm pan command
    }

    //
    // Pan setting (with optimization)
    // entry	AH = pan data
    //
    private void set_pan() {
        r.test(mucom2.optimiz, (byte) 2); // Pan already set?
        if (r.zero || r.ah != mucom2.opt_pan) {
//span1:
            mucom2.opt_pan = r.ah; // Store set pan
            mucom2.optimiz |= 2;
            r.al = (byte) 0xf6; // Store pan specification command
            LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
            lp.chip = work.crntChip;
            lp.ch = (byte) work.crntChannel;
            lp.part = work.crntPart;
            MmlDatum md = new MmlDatum(r.al & 0xff, MMLType.Pan, lp, r.ah & 0xff);
            md = work.FlashLstMd(md);
            muap98.objectBuf.set(r.di++, md); // Store rest
            muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
            return;
        }
        // Same as previously set pan
        // Do not store command if same
//span2:
    }

    //
    // Rhythm sound source pan setting
    // entry	AX = parameter
    //
    private void set_rhythmpan() {
        r.test(mucom2.optimiz, (byte) 8); // Rhythm pan already set?
        if (!r.zero) {
            if ((r.getAx() & 0xffff) == (mucom2.opt_rhy & 0xffff)) {
                // Same as previously specified pan
                // Do not store command if same
//srpan2:
                return;
            }
        }
//srpan1:
        mucom2.opt_rhy = r.getAx(); // Store set pan
        mucom2.optimiz |= 8;
        r.push(r.getAx());
        r.al = (byte) 0xee;
        mucom2.stosbObjBufAL2DI(); // Command specification
        r.setAx(r.pop());
        mucom2.stoswObjBufAX2DI();
//srpan2:
    }

    //
    // Auto-pan initialization
    //
    public void pan_init() {
        r.setSi((short) 0); // ofs:pandata
        mucom2.panadrs = r.getSi();
        mucom2.pandata[0] = 0;
    }

    //
    // Auto-swaying pan
    //
    private void auto_pan() {
        int sendCh = (mucom2.sendch & 0xff);
        if (sendCh >= 4) {
            if (sendCh < 7) {
//apan_skip:
                return;
            }
            if (sendCh == 10) {
//apan_skip:
                return;
            }
        }
//apan_exe:
        mucom2.chktxt();
        if (r.al != '(') {
            r.cl = 32;
            mucom2.error();
            return;
        }
        pan_init(); // SI = pan pattern storage address
        r.setSi((short) (r.getSi() & 0xffff));
//apan_loop:
        do {
            mucom2.chktxt();
            xsmall();
            r.ah = (byte) 0x80;
            if (r.al != 'L') {
                r.ah = (byte) 0xc0;
                if (r.al != 'M') {
                    r.ah = (byte) 0x40;
                    if (r.al != 'R') {
                        if (r.al == ')') {
//apan_exit:
                            mucom2.pandata[r.getSi() & 0xffff] = 0; // Store end code
//apan_skip:
                            return;
                        }
                        r.cl = 32;
                        mucom2.error();
                        return;
                    }
                }
            }
//apan_set:
            mucom2.pandata[(r.getSi() + 1) & 0xffff] = r.ah;
        } while ((r.getSi() & 0xffff) < 16); // ofs:pandata+16 // Up to 16 patterns can be specified

        r.cl = 37;
        mucom2.error();
    }

    //
    // Auto-pan check
    //
    public void pan_check() {
        r.push(r.getSi());
        r.push(r.getAx());
        int siVal = (mucom2.panadrs & 0xffff);
        r.al = mucom2.pandata[siVal];
        if (r.al == 0) {
            siVal = 0; // ofs:pandata
            mucom2.panadrs = (short) siVal; // Pan pattern address initialization
        }
//panchk1:
        r.al = mucom2.pandata[siVal];
        if (r.al != 0) {
            r.setSi((short) ((r.getSi() + 1) & 0xffff));
            mucom2.panadrs = (short) siVal;
            r.ah = r.al;
            set_pan(); // Pan specification
        }
//panchk2:
        r.setAx(r.pop());
        r.setSi(r.pop());
    }

    //
    // Lyric display function
    //
    private void mcomment() {
        mucom2.chkval(); // Variable check

        if (!r.carry) {
            mucom2.chktxt();
            r.zero = (r.al == '='); // @com x= specification
            r.cl = 32;
            if (!r.zero) mucom2.error();

            r.push(r.getAx());
            mucom2.rednums();
            r.dl = r.al;
            r.setAx(r.pop());
            mucom2.chktxt();
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            comment5();
            return;
        }

//com_norm:
        chknum(); // Number check
        if (r.carry) {
//comment1:
            r.ah = (byte) 0xff; // If no condition specified
            comment5();
            return;
        }
        mucom2.rednums(); // Read argument
        r.dl = r.al;
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
//comment2:
        r.ah = (byte) (mucom2.nesting - 1); // Command (DBH), nesting (FFH, 0-5)
        comment5();
    }

    private void comment5() {
        r.cl = 32;
        if (r.al != 0x22) mucom2.error();
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if ((mucom2.commode & 1) == 0) {
            // Lyric output prohibited?
            comment_set();
            return;
        }
        r.push(r.di);
        comment_set();
        r.di = r.pop(); // Restore performance address
    }

    private void comment_set() {
        if ((mucom2.commode & 2) != 0) {
            // Color changing mode?
            mucom2.comcnt = 0;
            r.push(r.getAx());
            r.setAx((short) 0xdd);
            // Initialize digit position
            muap98.objectBuf.set(r.di, new MmlDatum(r.al & 0xff));
            muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah & 0xff));
            r.di += 2;
            r.setAx(r.pop());
        }

//comment6:
        r.al = (byte) 0xdb;
        muap98.objectBuf.set(r.di, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah & 0xff));
        r.di += 2;

        r.al = r.dl; // Condition data
        muap98.objectBuf.set(r.di, new MmlDatum(r.al & 0xff));
        r.di++;
        r.setDx(r.di); // Offset for string length storage address
        r.di++;

        r.ah = 0; // String length
        r.cl = 33; // Lyric data error

        if (work.compilerInfo == null) work.compilerInfo = new CompilerInfo();
        if (work.compilerInfo.additionalInfo == null) work.compilerInfo.additionalInfo = new MetaData();
        MetaData tag = (MetaData) work.compilerInfo.additionalInfo;
        tag.add(Tag.Lyric, "MUS:UseLyric"); // Set lyric use flag

//comment4:
        do {
            r.al = (byte) ((r.getBx() & 0xffff) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[r.getBx() & 0xffff]);
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) < ' ') mucom2.error(); // Control codes not allowed
            if (r.al == 0x22) {
//comment3:
                r.push(r.di);
                r.di = r.getDx();
                r.al = r.ah;
                // Store string length
                muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
                r.di = r.pop();
                return;
            }
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
            r.ah++;
        } while ((r.ah & 0xff) != 73); // Up to 72 characters

//error7:
        mucom2.error();
    }

    //
    // Lyric output prohibition
    //
    private void comment_mode() {
        mucom2.commode |= 1;
    }

    //
    // Setting for color changing lyric display
    //
    private void comstepin() {
        mucom2.commode |= 2; // Color changing lyric mode on
    }

    private void comstepout() {
        mucom2.commode &= 0xfd;
        stepcut2();
    }

    private void comstepcut() {
        mucom2.chktxt();
        chknum();
        if (r.carry) {
//stepcut1:
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            stepcut2();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        mucom2.rednums();
        r.al += mucom2.comcnt;
        mucom2.comcnt = r.al;
        r.ah = r.al;
        r.al = (byte) 0xdd; // Add only specified value
        mucom2.stoswObjBufAX2DI();
    }

    private void stepcut2() {
        r.setAx((short) 0xffdd); // Make all white at once
        mucom2.stoswObjBufAX2DI();
    }

    //
    // @_D- Transposition key signature set
    //
    private void set_flat() {
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        xsmall(); // AL uppercase conversion
        if (r.al == 'I') {
            // For initialization
            flat_init();
            return;
        }

        flat_param();
        r.push(r.getBx());
        r.setSi((short) 0); // ofs:flatdata
        r.ah = 0;
        r.al = r.dl; // Note data (0-6)
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getAx() & 0xffff)));
        mucom2.flatdata[r.getSi() & 0xffff] = r.dh; // Set 0, 1, -1
        r.setBx(r.pop());
    }

    //
    // Clear key signature of current channel
    //
    private void flat_init() {
        r.push(r.getBx());
        r.setSi((short) 0); // ofs:flatdata
        r.setCx((short) 7);
//finit1:
        do {
            mucom2.flatdata[r.getSi() & 0xffff] = 0;
            r.setSi((short) ((r.getSi() & 0xffff) + 1));
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);
        r.setBx(r.pop());
    }

    //
    // @+,@-,@% Temporary transposition
    //
    private void setf5() {
        r.dh++; // Double flat (DH=4)
        setf4();
    }

    private void setf4() {
        r.dh++; // Double sharp (DH=3)
        setf2();
    }

    private void setf2() {
        r.dh++; // Flat (DH=2)
        setf1();
    }

    private void setf1() {
        r.dh++; // Sharp (DH=1)
        setf3();
    }

    private void setf3() {
        // Natural (DH=0)
        r.test(mucom2.mode[0], (byte) 4); // Whether to execute <> (for chord, trill)
        if (!r.zero) {
            oct_exe();
            return;
        }
        r.push(r.getBx());
        r.push((short) (mucom2.octdata & 0xff));
        mucom2.chktxt();
        if (r.al != '{') {
            // Check for @+ { cde }4
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
        }
        skipoct(); // AL = note code
        r.al -= 'A';
        r.dl = r.al;
        calc_fpara(); // Output temporary transposition code
        mucom2.octdata = (byte) (r.pop() & 0xff);
        r.setBx(r.pop());
    }

    private void oct_exe() {
        skipoct(); // Execute octave shift
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.push(r.getBx());
        r.al -= 'A';
        r.dl = r.al;
        calc_fpara();
        r.setBx(r.pop());
        mucom2.mode[0] &= 0xfb;
    }

    //
    // Skip <> and get note code
    //
    public void skipoct() {
        do {
            mucom2.chktxt();
            if ((r.al & 0xff) == '<') {
//skipoct1:
                mucom2.octdown();
                continue;
            }
            if ((r.al & 0xff) == '>') {
//skipoct2:
                mucom2.octup();
                continue;
            }
            break;
        } while (true);
        xsmall(); // AL uppercase conversion
    }

    //
    // Create temporary transposition generation code
    // entry	DH = transposition data (0,1,2,3,4)
    //      	DL = note data (0-6)
    //
    private void calc_fpara() {
        r.dh |= 0x80; // b7 = 1
        r.setSi((short) 0); // ofs:flatdata2
        r.al = mucom2.octdata; // AL = current octave value
        r.ah = 7;
        r.mul(r.ah);
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getAx() & 0xffff)));
        r.al = r.dl; // AL = note data (0-6)
        // Generation code (b7:temporary exists,
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getAx() & 0xffff))); // b2-b0:000=%,001=#,010=-,011=##,100=--)
        mucom2.flatdata2[r.getSi() & 0xffff] = r.dh; // Set 0, 1, -1
    }

    //
    // @_ parameter check
    // entry	AL = text character
    // exit 	DH = %,#,- : 0,1,-1
    //      	DL = ABCDEFG : 0-6
    //
    private void flat_param() {
        r.al -= 'A';
        r.dl = r.al; // DL = note data (0-6)
        r.carry = ((r.al & 0xff) < 7);
        r.cl = 28;
        if (!r.carry) {
            mucom2.error();
            return;
        }

        r.al = muap98.sourceBuf[r.getBx() & 0xffff]; // Check next code
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.dh = (byte) 0xff; // Initial flat
        if (r.al == '-') {
//findflat:
            return;
        }
        r.dh = 1; // Initial sharp
        if (r.al == '+' || r.al == '#') {
//findflat:
            return;
        }
        r.dh = 0; // Restore
        if (r.al == '%') {
//findflat:
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1)); // Back off if not
//findflat:
    }

    //
    // Clear temporary key signature at end of bar
    //
    private void flat_clear() {
        r.push(r.es);
        r.push(r.di);
        r.push(r.getCx());
        r.es = r.cs;
        r.setCx((short) (56 / 2));
        r.setAx((short) 0);
        r.di = 0; // ofs:flatdata2
        do {
            mucom2.flatdata2[(r.di & 0xffff) + 0] = r.al;
            mucom2.flatdata2[(r.di & 0xffff) + 1] = r.ah;
            r.di += 2;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);
        r.setCx(r.pop());
        r.di = r.pop();
        r.es = r.pop();
    }

    //
    // Crescendo processing
    //
    private void cresc() {
        r.ah = '<';
        cres_ent();
        mucom2.creslen = (short) (r.getAx() & 0xffff);
        r.setAx((short) 0);
        mucom2.crescnt = (short) (r.getAx() & 0xffff); // Clear note length counter
        mucom2.dcrelen = (short) (r.getAx() & 0xffff);
    }

    //
    // Decrescendo processing
    //
    private void decresc() {
        r.ah = '>';
        cres_ent();
        mucom2.dcrelen = (short) (r.getAx() & 0xffff);
        r.setAx((short) 0);
        mucom2.crescnt = (short) (r.getAx() & 0xffff); // Clear note length counter
        mucom2.creslen = (short) (r.getAx() & 0xffff);
    }

    //
    // Initial processing for crescendo
    // entry	AH = comparison sign (<>)
    //
    private void cres_ent() {
        r.al = mucom2.volsave;
        r.cl = 31;
        if ((r.al & 0xff) > 127) {
            mucom2.error();
            return;
        }
        mucom2.volstt = r.al; // Save start volume
        r.dl = 1; // Amount of volume increase
//cres2:
        do {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            if (r.al != r.ah) {
                break;
            }
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.dl++;
        } while (true);
//cres1:
        r.dh = r.dl;
        r.dl += r.dl;
        r.dl += r.dh;
        mucom2.cresvol = (short) (r.dl & 0xff); // Value multiplied by 3
        r.cl = 14;
        chknum();
        if (r.carry) {
            mucom2.error();
            return;
        }
        mucom2.tnelnm(); // Note length analysis (AX)
    }

    //
    // Crescendo check
    //
    public void cres_check() {
        rit_check(); // @ACC, @RIT check
        r.push(r.getCx());
        r.setCx((short) mucom2.creslen); // CX = total note length
        if (r.getCx() != 0) {
            exe_cresc();
            return;
        }
        r.setCx((short) mucom2.dcrelen);
        if (r.getCx() != 0) {
            exe_decre();
            return;
        }
        r.setCx(r.pop());
    }

    private void exe_cresc() {
        r.push(r.getAx());
        cres_main();
        if (r.carry) {
//cres0:
            r.setAx(r.pop());
            r.setCx(r.pop());
            return;
        }
        r.al = mucom2.volstt; // Volume value at start

        r.al = (byte) ((r.al & 0xff) + (r.cl & 0xff));
        if ((r.al & 0xff) > 127) {
            r.al = 127;
        }
//cres4:
        r.zero = (r.al == mucom2.volsave); // Matches current volume?
        r.ah = r.al;
        if (!r.zero) {
            mucom2.retvol3(); // Specify volume
        }
//cres0:
        r.setAx(r.pop());
        r.setCx(r.pop());
    }

    private void exe_decre() {
        r.push(r.getAx());
        cres_main();
        if (r.carry) {
//cres0:
            r.setAx(r.pop());
            r.setCx(r.pop());
            return;
        }
        r.al = mucom2.volstt;
        r.carry = (r.al & 0xff) < (r.cl & 0xff);
        r.al = (byte) ((r.al & 0xff) - (r.cl & 0xff));
        if (r.carry) {
            r.al = 0;
        }

//cres4:
        r.zero = (r.al == mucom2.volsave); // Matches current volume?
        r.ah = r.al;
        if (!r.zero) {
            mucom2.retvol3(); // Specify volume
        }
//cres0:
        r.setAx(r.pop());
        r.setCx(r.pop());
    }

    //
    // Crescendo subroutine
    // entry	CX = total note length
    //      	chglen = note length
    // exit 	CX = current volume shift value (@v)
    //      	CY = already finished
    //
    private void cres_main() {
        r.push(r.getDx());
        r.ah = 0; // CX = crescendo length
        r.al = mucom2.chglen; // AX = current note length
        r.setAx((short) ((r.getAx() & 0xffff) + (mucom2.crescnt & 0xffff))); // AX = note length from start
        mucom2.crescnt = (short) (r.getAx() & 0xffff);
        if ((r.getAx() & 0xffff) >= (r.getCx() & 0xffff)) {
            // Check if outside note length period
//cres_end:
            init_cres();
            r.carry = true;
            r.setDx(r.pop());
            return;
        }
        r.mul((byte) mucom2.cresvol);
        r.div((byte) r.getCx()); // AX = current volume shift value
        r.setAx((short) ((r.getAx() & 0xffff) + 1)); // Add 1
        r.setCx(r.getAx());
        r.setDx(r.pop());
        r.carry = false;
    }

    public void init_cres() {
        r.push(r.getAx());
        r.setAx((short) 0);
        mucom2.creslen = (short) (r.getAx() & 0xffff);
        mucom2.dcrelen = (short) (r.getAx() & 0xffff);
        r.setAx(r.pop());
    }

    //
    // Accelerando processing
    //
    private void accel() {
        mucom2.rednum(); // AX = value of tempo to increase
        accel1();
    }

    private void accel1() {
        mucom2.tmpdata = r.getAx();
        chkcm(); // Comma check
        mucom2.tnelnm(); // AX = note length period (max=65535)
        mucom2.tmplen = r.getAx();
        r.setAx((short) mucom2.tempos);
        mucom2.tmpstt = (short) (r.getAx() & 0xffff); // Save tempo at start
        r.setAx((short) 0);
        mucom2.tmpcnt = (short) (r.getAx() & 0xffff); // Initialize counter
    }

    //
    // Ritardando processing
    //
    private void ritard() {
        mucom2.rednum();
        r.setAx((short) -(r.getAx() & 0xffff)); // Make it negative
        accel1();
    }

    //
    // Accelerando, ritardando check
    //
    private void rit_check() {
        r.push(r.getCx());
        r.setCx((short) mucom2.tmplen); // CX = total note length
        if (r.getCx() != 0) {
            exe_rit();
            return;
        }
        r.setCx(r.pop());
    }

    private void exe_rit() {
        r.push(r.getAx());
        rit_main();
        if (!r.carry) {
            r.setAx((short) mucom2.tmpstt); // Tempo value at start (like T120)
            r.setAx((short) ((r.getAx() & 0xffff) + (r.getCx() & 0xffff)));
            if ((r.getAx() & 0xffff) < 16) {
                r.setAx((short) 16);
            }
            if ((r.getAx() & 0xffff) > 3907) {
                r.setAx((short) 3907);
            }
            r.push(r.getAx());
            mucom2.calct(); // AL = 3907/AX
            r.setCx(r.getAx());
            r.setAx((short) mucom2.tempos);
            mucom2.calct();
            r.zero = (r.getAx() == r.getCx()); // Does tempo match internally?
            r.setAx(r.pop());
            if (!r.zero) {
                mucom2.calc_tempo(); // Tempo specification command (CDxxxx)
            }
        }
//rit_abort:
        r.setAx(r.pop());
        r.setCx(r.pop());
    }

    //
    // @ACC,@RIT check subroutine
    // entry	CX = total note length
    //      	chglen = note length
    // exit 	CX = current tempo shift value (+-)
    //      	CY = already finished
    //
    /**
     * @ACC,@RIT check subroutine
     * entry CX = total note length
     *       chglen = note length
     * exit  CX = current tempo shift value (+-)
     *       CY = already finished
     */
    private void rit_main() {
        r.push(r.getDx());
        r.ah = 0; // CX = crescendo length
        r.al = mucom2.chglen; // AX = current note length
        r.setAx((short) ((r.getAx() & 0xffff) + (mucom2.tmpcnt & 0xffff))); // AX = note length from start
        mucom2.tmpcnt = (short) (r.getAx() & 0xffff);
        if ((r.getAx() & 0xffff) >= (r.getCx() & 0xffff)) {
            // Check if outside note length period
//rit_end:
            mucom2.tmplen = 0; // Abort @ACC, @RIT
            r.carry = true;
            r.setDx(r.pop());
            return;
        }

        short a = r.getAx();
        short b = (short) mucom2.tmpdata;
        int ans = a * b;
        r.setDx((short) (ans >> 16));
        r.setAx((short) (ans & 0xffff));
        short quo = (short) (ans / r.getCx());
        short re = (short) (ans % r.getCx());
        r.setAx(quo);
        r.setDx(re); // AX = current tempo shift value
        r.setCx(r.getAx());
        r.setDx(r.pop());
        r.carry = false;
    }

    /**
     * LFO data setting
     */
    private void lfo_set() {
        mucom2.chktxt();
        xsmall();
        r.cl = 32;
        if (r.al == 'P') lfo_pmd();
        else if (r.al == 'A') lfo_amd();
        else if (r.al == 'S') lfo_stop();
        else if (r.al == 'R') lfo_reset();
        else mucom2.error();
    }

    private void lfo_stop() {
        r.setAx((short) 0x00d8);
        mucom2.stoswObjBufAX2DI();
    }

    private void lfo_reset() {
        r.setAx((short) 0x03d8);
        mucom2.stoswObjBufAX2DI();
    }

    private void lfo_pmd() {
        r.setAx((short) 0x02d8);
        lfo1();
    }

    private void lfo_amd() {
        r.setAx((short) 0x01d8);
        lfo1();
    }

    private void lfo1() {
        mucom2.stoswObjBufAX2DI();
        if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) ',') return;
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        mucom2.rednums();
        r.cl = 6;
        if ((r.al & 0xff) > 3) {
            mucom2.error();
            return;
        }
        r.ah = r.al;
        r.al = (byte) 0xd9;
        mucom2.stoswObjBufAX2DI();
        chkcm();
        r.dh = 0;
        if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) '-') {
            r.dh++;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
        }

        mucom2.rednums();
        r.cl = 6;
        if ((r.al & 0xff) > 127) {
            mucom2.error();
            return;
        }
        if (r.dh != 0) r.al = (byte) -r.al;
        r.dh = r.al;
        chkcm();
        mucom2.rednums();
        boolean cf = ((muap98.objectBuf.get(r.di - 3).dat & 0xff) < 1);
        if ((muap98.objectBuf.get(r.di - 3).dat & 0xff) != 1) {
            if (mucom2.mmlver < 0x26) r.al &= 0xfe;
            cf = ((r.al & 0xff) < 128);
        }
        if (!cf) r.al >>= 1;
        r.ah = r.dh;
        mucom2.stoswObjBufAX2DI();
        r.cl = 1; // Speed base
        r.setDx((short) 0x200); // DL = increment
        if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) ',') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) ',') mucom2.rednums();
            else r.al = 0;
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
            if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) ',') {
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) ',') {
                    mucom2.rednums();
                    if ((r.al & 0xff) > 15) {
                        r.cl = 6;
                        mucom2.error();
                        return;
                    }
                } else r.al = 0;
                r.dl = r.al;
                if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) ',') {
                    r.setBx((short) ((r.getBx() & 0xffff) + 1));
                    if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) ',') {
                        mucom2.rednums();
                        if ((r.al & 0xff) > 6) {
                            r.cl = 6;
                            mucom2.error();
                            return;
                        }
                        r.dh = r.al;
                    }
                }
            }
        } else {
            r.al = 0;
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        }

        r.carry = cf;
        r.al = r.dl;
        if (r.carry) r.dh++;
        r.al |= (byte) ((r.dh & 0xff) << 4);
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) ',') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) ',') {
                mucom2.rednums();
                if ((r.al & 0xff) > 3) {
                    r.cl = 6;
                    mucom2.error();
                    return;
                }
                r.cl = r.al;
            }
            if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) ',') {
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                mucom2.rednums();
                if ((r.al & 0xff) > 63) {
                    r.cl = 6;
                    mucom2.error();
                    return;
                }
                r.cl |= (byte) ((r.al & 0xff) << 2);
            }
        }
        r.al = r.cl;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
    }

    /**
     * Hard LFO speed setting
     */
    private void hlfo_speed() {
        mucom2.rednums();
        if (skip_ssg()) return;
        if (r.al == 0) {
            r.setAx((short) 0xef);
            mucom2.stoswObjBufAX2DI();
            return;
        }
        r.carry = ((r.al & 0xff) > 8);
        r.cl = 6;
        if (r.carry) {
            mucom2.error();
            return;
        }
        r.al = (byte) (((r.al & 0xff) - 1) | 8);
        r.ah = r.al;
        r.al = (byte) 0xef;
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * Hardware LFO data setting
     */
    private void hlfo_data() {
        mucom2.rednums();
        if ((r.al & 0xff) > 7) {
//error17:
            r.cl = 6;
            mucom2.error();
            return;

        }
        r.dh = r.al;
        chkcm();
        mucom2.rednums();
        if ((r.al & 0xff) > 3) {
            r.cl = 6;
            mucom2.error();
            return;
        }
        r.dl = r.al;
        chkcm();
        mucom2.rednums();
        if ((r.al & 0xff) > 15) {
            r.cl = 6;
            mucom2.error();
            return;
        }
        if (skip_ssg()) return;
        r.push(r.getAx());
        r.dl = (byte) (((r.dl & 0xff) << 4) | (r.dh & 0xff));
        r.ah = r.dl;
        r.al = (byte) 0xee;
        mucom2.stoswObjBufAX2DI();
        r.setAx(r.pop());
        mucom2.stosbObjBufAL2DI();
    }

    /**
     * Start of slur
     */
    private void slur_in() {
        r.cl = 12;
        if (mucom2.slurmod != 0) {
//error12:
            mucom2.error();
            return;
        }
        r.al = (byte) (mucom2.ratdata | 0x80); // Save original Q
        mucom2.slurmod = r.al;
        r.al = 0; // Set to Q8
        mucom2.chgrat();
    }

    /**
     * End of slur
     */
    private void slur_out() {
        r.cl = 12;
        if (mucom2.slurmod == 0) {
//error12:
            mucom2.error();
            return;
        }
        r.al = (byte) (mucom2.slurmod & 0x7f);
        mucom2.chgrat(); // Restore Q
        mucom2.slurmod = 0; // Release @si mode
    }

    /**
     * @SL slide process
     */
    private void slide_set() {
        mucom2.chktxt();
        if (r.al == '-') {
//slneg1:
            mucom2.rednums();
            r.al = (byte) -r.al;
        } else {
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            mucom2.rednums();
        }
//slneg2:
        mucom2.slbase = r.al;
        chkcm();
        mucom2.tnelnmx();
        mucom2.slspeed = r.al;
    }

    private void slide() {
        r.ch = mucom2.sendch;
        r.al = 1;
        mucom2.setrat(); // Set to L192
        r.push(r.di);
        if (r.ch == 11) {
            // Frequency code is 3 bytes for PCM
            r.di++;
        }
        r.di += 16; // freq,&(@f+&)n Len
        porta_sub();
        mucom2.porta2 = r.getAx() & 0xffff; // Save end frequency
        if (r.ch == 11) {
            byte tmp = r.al;
            r.al = r.ah;
            r.ah = tmp;
        }
        mucom2.por_end = r.di & 0xffff; // End address
        r.di = r.pop();
        r.setDx(r.getAx());
        r.ah = mucom2.slbase; // AH = displacement value
        mucom2.freq_lfo();
        int axVal = (r.getAx() & 0xffff) + (r.getDx() & 0xffff);
        r.setAx((short) axVal);
        if (r.ch == 11) {
            byte tmp = r.al;
            r.al = r.ah;
            r.ah = tmp;
            r.push(r.getAx());
            r.al = (byte) 0xd5;
            mucom2.stosbObjBufAL2DI();
            r.setAx(r.pop());
        }

//slide1:
        mucom2.porta1 = r.getAx() & 0xffff; // Save start frequency
        byte tmp = r.al;
        r.al = r.ah;
        r.ah = tmp;
        mucom2.stoswObjBufAX2DI(); // Store start frequency
        r.al = mucom2.slspeed;
        mucom2.porcnt = (short) (r.al & 0xff); // Number of divisions
        porta_main(); // Entrust to portamento process
        r.di = r.getAx(); // Do not restore performance address
        mucom2.tnelnmx(); // Acquisition of note length data (AL)
        mucom2.add_tlen();
        r.al++;
        r.cl = 15;
        r.carry = (r.al & 0xff) < (mucom2.slspeed & 0xff);
        r.setAx((short) ((r.al & 0xff) - (mucom2.slspeed & 0xff)));
        if (r.carry) {
            // error12: // total length exceeded
            mucom2.error();
            return;
        }
        mucom2.setrat();
        r.di = (short) mucom2.por_end;
    }

    /**
     * @POR portamento process
     */
    public void porta() {
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        lp.chip = work.crntChip;
        lp.ch = (byte) work.crntChannel;
        lp.part = work.crntPart;
        MmlDatum md = new MmlDatum(MMLType.Note, new ArrayList<>(Arrays.asList(0, 0)), lp, 0);
        md = work.FlashLstMd(md);
        work.md = md;

        r.ch = mucom2.sendch;
        mucom2.chktxt();
        chknum(); // Check if delay value is specified
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        if (r.carry) {
//portas1:
            r.al = 1;
        } else {
            mucom2.tnelnmx();
            r.setAx((short) ((r.getAx() & 0xffff) + 1)); // AL = delay time
        }

//portas2:
        r.push(r.getAx());
        r.push((short) (mucom2.ratdata & 0xff));
        mucom2.ratdata = 0; // Set to Q8
        mucom2.setrat(); // delay time + L192 wait
        mucom2.ratdata = (byte) (r.pop() & 0xff);
        porta_sub();
        work.md = null;
        mucom2.porta1 = r.getAx() & 0xffff; // Save start frequency
        r.setDx(r.pop());

        r.push(r.getDx());
        r.push(r.di);
        if (r.dl != 1) {
            r.di += 3;
        }
        r.di += 11; // add 11 bytes for &(@f+&)n
        porta_sub();
        mucom2.porta2 = r.getAx() & 0xffff; // Save end frequency
        mucom2.tnelnmx(); // Acquisition of note length data (AL)
        mucom2.add_tlen();
        mucom2.por_end = r.di; // End address
        r.di = r.pop();
        r.setDx(r.pop());

        r.dl--; // DL = delay time
        if (r.dl != 0) {
            r.push(r.getAx());
            r.al = 1;
            mucom2.setrat();
            r.setAx(r.pop());
        }

//portas3:
        boolean flg = (r.al & 0xff) <= (r.dl & 0xff);
        r.al = (byte) ((r.al & 0xff) - (r.dl & 0xff));
        if (flg) {
//por_error:
            r.cl = 18; // portamento range too wide
            mucom2.error();
            return;
        }
        mucom2.porcnt = (short) (r.al & 0xff); // Number of divisions
        porta_main();
    }

    private void porta_main() {
        int chVal = (r.ch & 0xff);
        if (chVal <= 3) {
            por_2203();
            return;
        }
        if (chVal <= 6) {
            por_ssg();
            return;
        }
        r.cl = 22;
        if (chVal == 10) {
            r.cl = 6;
            mucom2.error();
            return;
        }
        if (chVal == 11) {
            por_pcm();
            return;
        }
        por_2203();
    }

    /**
     * YM2203 Portamento
     */
    private void por_2203() {
        pre_por();
        r.setAx((short) mucom2.porta1);
        calc_exp1(); // Expand start frequency
        mucom2.freqsv1 = r.getAx();
        mucom2.freqsv2 = r.getDx();
        r.setAx((short) mucom2.porta2);
        calc_exp1(); // Expand end frequency
        r.carry = (r.getAx() & 0xffff) < (mucom2.freqsv1 & 0xffff);
        r.setAx((short) ((r.getAx() & 0xffff) - (mucom2.freqsv1 & 0xffff)));
        int dxVal = (r.getDx() & 0xffff) - ((mucom2.freqsv2 & 0xffff) + (r.carry ? 1 : 0));
        r.setDx((short) dxVal); // DXAX = End - Start frequency
        if ((dxVal & 0x8000) != 0) {
//por_fm1:
            r.setAx((short) -(r.getAx() & 0xffff));
            r.setDx((short) ~(r.getDx() & 0xffff));
            div64();
            r.setDx((short) ~(r.getDx() & 0xffff));
            r.setAx((short) -(r.getAx() & 0xffff));
        } else {
            div64();
        }
//por_fm2:
        after_por();
    }

    /**
     * SSG Portamento
     */
    private void por_ssg() {
        pre_por();
        r.setAx((short) mucom2.porta2);
        r.carry = (r.getAx() & 0xffff) < (mucom2.porta1 & 0xffff);
        r.setAx((short) ((r.getAx() & 0xffff) - (mucom2.porta1 & 0xffff))); // AX = End - Start
        if (r.carry) {
//por_ssg1:
            r.setAx((short) -(r.getAx() & 0xffff));
            div32();
            r.setDx((short) ~(r.getDx() & 0xffff));
            r.setAx((short) -(r.getAx() & 0xffff));
        } else {
            div32();
        }
//por_ssg2:
        after_por();
    }

    /**
     * PCM Portamento
     */
    private void por_pcm() {
        pre_por();
        r.setCx((short) 1); // value of n
        r.setDx((short) mucom2.porta1);
        byte tmp = r.dl;
        r.dl = r.dh;
        r.dh = tmp; // DX = Start DELTA-N
        r.setAx((short) mucom2.porta2);
        tmp = r.al;
        r.al = r.ah;
        r.ah = tmp; // AX = End DELTA-N
        r.carry = (r.getAx() & 0xffff) < (r.getDx() & 0xffff);
        r.setAx((short) ((r.getAx() & 0xffff) - (r.getDx() & 0xffff))); // AX = End - Start DELTA-N
        if (r.carry) {
//por_pcm1:
            r.setAx((short) -(r.getAx() & 0xffff));
            div32();
            r.setDx((short) ~(r.getDx() & 0xffff));
            r.setAx((short) -(r.getAx() & 0xffff));
        } else {
            div32();
        }
//por_pcm2:
        after_por();
    }

    /**
     * Frequency expansion/restoration routine for YM2203
     * entry DXAX = 2203 frequency data
     * exit  DXAX = real frequency data
     */
    private void calc_exp1() {
        r.push(r.getCx());
        int cxVal = r.getAx() & 0xffff; // AX = frequency component below octave
        r.setAx((short) (cxVal & 0x7ff));
        cxVal >>= 11; // CX = octave (0-7)
        r.setCx((short) cxVal);
        r.setDx((short) 0);
        if (cxVal == 0) {
//por5:
            r.setCx(r.pop());
            return;
        }
//por1:
        do {
            r.carry = (r.getAx() & 0x8000) != 0;
            r.setAx((short) ((r.getAx() & 0xffff) << 1));
            r.setDx(r.rcl(r.getDx(), 1));
            cxVal--;
            r.setCx((short) cxVal);
        } while (cxVal != 0); // DXAX = expanded frequency data
//por5:
        r.setCx(r.pop());
    }

    /**
     * Division for portamento
     * entry DXAX = difference data (positive only)
     * exit  DXAX = AX*256/(porcnt)
     */
    private void div32() {
        r.setDx((short) 0);
        div64();
    }

    private void div64() {
        r.push(r.getAx());
        r.al = r.ah;
        r.ah = r.dl;
        r.dl = r.dh;
        r.dh = 0;
        int porCntVal = (mucom2.porcnt & 0xffff);
        r.div((byte) porCntVal); // Divide upper 16bit
        r.setCx(r.getAx());
        r.setAx(r.pop());
        r.ah = r.al;
        r.al = 0;
        r.div((byte) porCntVal); // Divide lower 16bit
        r.setDx(r.getCx()); // DXAX = quotient
    }

    /**
     * Portamento pre-processing
     */
    /**
     * Portamento pre-processing
     */
    private void pre_por() {
        r.setAx((short) 0xe0e1); // Loop start command +++
        mucom2.stoswObjBufAX2DI();
        r.al = (byte) 0xf8; // Frequency addition +++
        mucom2.stosbObjBufAL2DI();
    }

    /**
     * Portamento post-processing
     * entry DLAX = difference value
     */
    /**
     * Portamento post-processing
     * entry DLAX = difference value
     */
    private void after_por() {
        after_por_main();
        r.setAx(r.di);
        r.di = (short) mucom2.por_end; // Restore to last address
    }

    private void after_por_main() {
        mucom2.stoswObjBufAX2DI();
        r.al = r.dl;
        r.ah = (byte) 0xe1; // +++
        mucom2.stoswObjBufAX2DI(); // Store change amount and tie
        r.setAx((short) 0x5f7);
        mucom2.stoswObjBufAX2DI(); // End of loop
        r.setAx((short) 0);
        r.ah = (byte) mucom2.porcnt;
        r.carry = ((r.ah & 0xff) <= 2); // jbe
        r.ah = (byte) ((r.ah & 0xff) - 2);
        if (r.carry) {
            // L96 or less is impossible
//por_error:
            r.cl = 18; // portamento range too wide
            mucom2.error();
            return;
        }
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * Portamento processing subroutine
     */
    private void porta_sub() {
        while (true) {
            skipoct(); // Read next text and execute <>
            if (r.al == '@') {
                // @ extended command also allowed
//por_set:
                mucom2.mode[0] |= 4; // flag to execute <>
                exp_cmd(); // execute @xx extended command
                continue;
            }
            break;
        }
        r.cl = 17;
        check_onpu(); // Must be a note next
        r.push(r.getAx()); // AL = note data (A-G)
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucom2.gethenon(); // check #+-% and return in DL
        r.setAx(r.pop());
        read(); // Store frequency data
        r.al = (byte) (muap98.objectBuf.get(r.di - 2).dat);
        r.ah = (byte) (muap98.objectBuf.get(r.di - 1).dat);
        byte tmp = r.al;
        r.al = r.ah;
        r.ah = tmp;
    }

    /**
     * Processing of @DEBUG command
     */
    private void set_debug() {
        mucom2.tnelnmx(); // Acquisition of rest length
        mucom2.debug = r.al;
    }

    /**
     * Check for clearing temporary key signature at line break
     */
    private void clear_mode() {
        mucom2.mode[0] |= 0x10;
    }

    public void check_flatclear() {
        if ((mucom2.mode[0] & 0x10) == 0) return;
        if ((mucom2.mode[1] & 1) == 0) {
            // Do not check outside []
            return;
        }
        // Data alignment mark processing (')
        wait_r();
    }

    public void wait_r() {
        flat_clear(); // Clear temporary key signature
        init_rhythm(); // Initialize rhythm table address
        if ((mucom2.mode[0] & 0x40) != 0) {
            // Is @NOOUT effective?
            return;
        }
        if (mucom2.debug != 0) {
            // Output rest?
            mucom2.tnelnx(); // Store note length
            r.al = (byte) 0xff;
            // stosb ; Store rest (not compressed)
        }
//wait_rr:
        r.al = (byte) 0xf3;
        // stosb
//nop_wait:
    }

    private void wait_mode() {
        mucom2.mode[0] |= 0x40; // Set to non-output mode
    }

    /**
     * Execution of fade out
     */
    private void fade_out() {
        r.al = (byte) 0xd1;
        mucom2.stosbObjBufAL2DI();
    }

    /**
     * Enable stopped channels
     */
    private void initia() {
        r.setSi((short) 0);
        r.setDx((short) 0); // DXSI = data to AND
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1)); // Back off text
        chknum(); // CY=1 if not a number (revive all channels)
        if (!r.carry) {
            r.setSi((short) (r.getSi() - 1));
            r.setDx((short) (r.getDx() - 1)); // DXSI = FFFFFFFF
//initi2:
            do {
                mucom2.rednums();
                r.cl = 6;
                r.al--; // AL = 0-16
                if ((r.al & 0xff) >= 17) {
                    // jnb error23
                }
                r.ch = r.al;
                r.push(r.getDx());
                mucom2.calcbit();
                r.setAx((short) ~(r.getAx() & 0xffff));
                int siVal = (r.getSi() & 0xffff) & (r.getAx() & 0xffff);
                r.setSi((short) siVal);
                r.al = (byte) ~(r.dl & 0xff);
                r.setDx(r.pop());
                r.dl &= r.al;
                mucom2.chktxt();
                r.cl = 32;
            } while (r.al == ',');
            // comma check for @init3,7,9,10
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
        }
//initi1:
        r.al = (byte) 0xdc;
        mucom2.stosbObjBufAL2DI(); // Clear channel stop by "***"
        r.setAx(r.getSi());
        mucom2.stoswObjBufAX2DI(); // Store AND data (24bit)
        r.al = r.dl;
        mucom2.stosbObjBufAL2DI();
    }

    /**
     * Pan/volume specification for each rhythm instrument
     */
    private void rhythm_pan() {
        mucom2.chktxt();
        xsmall();
        setrt();
        if (r.al == 0) {
            // AH = specified rhythm bit (1-20h)
            r.cl = 32;
//error23:
            mucom2.error();
            return;
            // CL = rhythm number (0-5)
        }
//rpan1:
        int cxVal = (r.getCx() & 0xffff); // SI = rhythm volume buffer
        r.dh = (byte) ((r.cl & 0xff) + 0x18); // DH = output address
        chkcm();
        mucom2.chktxt();
        xsmall();
        r.dl = (byte) 0x80;
        if (r.al != 'L') {
            // left specification
            r.dl = (byte) 0x40;
            if (r.al != 'R') {
                // right specification
                r.dl = (byte) 0xc0;
                if (r.al != 'M') {
                    // mono specification
                    r.cl = 32;
                    mucom2.error();
                    return;
                }
            }
        }
//rpan_set:
        chkcm(); // Comma check
        mucom2.rednums();
        r.carry = ((r.al & 0xff) > 31); // Volume range check
        r.cl = 6;
        if (r.carry) {
//error23:
            mucom2.error();
            return;
        }
        mucom2.rhyvol[cxVal] = r.al; // Save to rhythm volume buffer
        r.al |= r.dl;
        r.ah = r.dh;
        set_rhythmpan(); // Store rhythm pan command
    }

    /**
     * Expansion of rhythm pattern
     */
    public void rhyexp() {
        mucom2.set_symbol2();
        mucom2.tnelnmx(); // Analysis of note length
        mucom2.rtm_max = r.al; // AL = note length
        rhyexp_code();
    }

    public void rhyexp_code() {
        r.push(r.getAx());
        if (mucom2.rhydata != 0) {
            // Specified by @?
            set_dump();
            return;
        }
        r.setAx(r.pop());
//rhyexp1:
        while (true) {
            r.dl = mucom2.rtm_max;
            getrp_table(); // AL = note length
            if ((r.dl & 0xff) >= (r.al & 0xff)) {
                reend5();
                return;
            }
            r.dl = (byte) ((r.dl & 0xff) - (r.al & 0xff));
            mucom2.rtm_max = r.dl; // Remaining note length
            reend4();
        }
    }

    private void reend5() {
        r.al = r.dl; // Use overall remainder as note length
        reend4();
    }

    private void reend4() {
        mucom2.tnelnx(); // Store note length
        if ((r.cl & 0xff) == 255) {
            // Pattern undefined
//reend6:
            r.al = (byte) 0xf9; // +++
            mucom2.stosbObjBufAL2DI(); // Output command end sign
            mucom2.onpucnt++;
//re_abort:
            return;
        }
        mucom2.dionpu = r.di;
        if ((r.getCx() & 0xffff) == 0) {
            // No key-on/dump specification
//reend8:
            r.al = (byte) 0xff; // Output rest
            mucom2.stosbObjBufAL2DI();
            mucom2.onpucnt++;
            return;
        }
        if (r.ch != 0) {
            r.ah = r.ch;
            r.al = (byte) 0xef; // Dump
            mucom2.stoswObjBufAX2DI();
        }
//reend7:
        if (r.cl != 0) {
            r.ah = r.cl;
            r.al = (byte) 0xf0; // Key-on
            mucom2.stoswObjBufAX2DI();
        }
//reend6:
        r.al = (byte) 0xf9; // +++
        mucom2.stosbObjBufAL2DI(); // Output command end sign
        mucom2.onpucnt++;
    }

    private void set_dump() {
        r.dl = mucom2.rhydata;
        r.setAx(r.pop());
        mucom2.tnelnx(); // Store note length
        mucom2.dionpu = r.di;
        r.ah = r.dl;
        r.al = (byte) 0xf0; // Key-on
        if (work.md == null) {
            mucom2.stoswObjBufAX2DI();
        } else {
            work.md.args.set(work.mdArgsStep + 0, r.ah & 0xff);
            work.md.args.set(work.mdArgsStep + 1, work.otoLength);
            mucom2.stoswObjBufAX2DI(work.md);
        }
        r.al = (byte) 0xf9; // +++
        mucom2.stosbObjBufAL2DI();
        mucom2.onpucnt++;
    }

    private void getrp_table() {
        getrp_main();
        if ((r.cl & 0xff) != 255) return;
//getrp_init:
        init_rhythm();
        getrp_main();
    }

    private void getrp_main() {
        r.push(r.getSi());
        int siVal = (mucom2.rhyadrs & 0xffff); // SI = rhythm table address
        r.cl = mucom2.rhythmdta[siVal];
        r.ch = mucom2.rhythmdta[siVal + 1]; // CX = key-on/dump data
        r.al = mucom2.rhythmdta[siVal + 2]; // AL = rhythm length
        if ((r.cl & 0xff) != 255) {
            mucom2.rhyadrs = (short) (siVal + 3);
        }
//getrp1:
        r.setSi(r.pop());
    }

    /**
     * Setting of rhythm pattern (for rhythm sound source)
     */
    private void rhythm_pat() {
        if ((r.ch & 0xff) != 10) return;
        mucom2.rhydata = 0; // Clear rhythm instrument
        mucom2.chktxt();
        r.zero = (r.al == '(');
        r.cl = 32;
        if (!r.zero) {
            mucom2.error();
            return;
        }
        int siVal = 0; // ofs:rhythmdta
        int dlVal = 0; // DL = total number of data
//rp_loop:
        do {
            r.setAx((short) 0);
            mucom2.rhythmdta[siVal] = r.al;
            mucom2.rhythmdta[siVal + 1] = r.ah; // [si] = rhythm bits to key-on/dump
//rp_next:
            do {
                mucom2.chktxt();
                xsmall();
                if (r.al == '*') {
                    // Previous pattern specification
//rp_same:
                    int last = (mucom2.lastrp & 0xffff); // AX = previous key-on/dump data
                    mucom2.rhythmdta[siVal] = (byte) last;
                    mucom2.rhythmdta[siVal + 1] = (byte) (last >> 8);
                    continue; // goto rp_next
                }
                if (r.al == '-') {
//rp_dump:
                    mucom2.chktxt();
                    xsmall();
                    setrt(); // Contains the bits to dump
                    if (r.al != 0) {
                        mucom2.rhythmdta[(r.getSi() + 1) & 0xffff] |= r.ah;
                        continue; // goto rp_next;
                    }
                    break;
                }
                setrt(); // Store bits to key-on
                if (r.al == 0) {
                    break; // goto rp_length;
                }
                mucom2.rhythmdta[siVal] |= r.ah;
            } while (true);
//rp_length:
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            mucom2.tnelnmx(); // AL = note length data (default if not specified)
            mucom2.rhythmdta[siVal + 2] = r.al;
            int finalVal = (mucom2.rhythmdta[siVal] & 0xff) | ((mucom2.rhythmdta[siVal + 1] & 0xff) << 8);
            mucom2.lastrp = (short) finalVal; // Save as previous data
            siVal += 3;
            dlVal++;
            r.carry = (dlVal < 16);
            r.cl = 8; // Rhythm pattern buffer shortage
            if (!r.carry) {
                mucom2.error();
                return;
            }
            mucom2.chktxt();
            if (r.al == ')') {
//rp_exit:
                mucom2.rhythmdta[siVal] = (byte) 0xff; // Write end code
                init_rhythm(); // Initialize rhythm table address
//rp_abort:
                return;
            }
        } while (r.al == ',');
        r.cl = 32;
        mucom2.error();
    }

    private void setrt() {
        r.ah = 1;
        r.cl = 0;
        String ptn = "BSCHTR";
        int p = ptn.indexOf((char) r.al);
        if (p < 0) {
            r.cl = 5;
            r.al = 0;
            return;
        }
        r.cl = (byte) p;
        r.ah = (byte) ((r.ah & 0xff) << (r.cl & 0xff));
    }

    /**
     * Rhythm pattern setting (for chord name)
     */
    private void rhythm_set() {
        mucom2.chktxt();
        r.zero = (r.al == '(');
        r.cl = 32;
        if (!r.zero) {
            mucom2.error();
            return;
        }
        r.setSi((short) 0); // ofs:rhythmdta
        r.dl = 0; // total number of data
//rhy_loop:
        do {
            mucom2.chktxt();
            if (r.al != '0') {
                xsmall();
                if (r.al == 'R') {
                    // rest specification (FFxx)
                    mucom2.rhythmdta[r.getSi() & 0xffff] = (byte) 0xff;
                    r.setSi((short) ((r.getSi() + 1) & 0xffff));
                    r.dl++;
                    mucom2.chktxt();
                }

//rhyrest:
                r.setBx((short) ((r.getBx() & 0xffff) - 1));
                mucom2.tnelnmx(); // Acquisition of rhythm note length (AL)

//rhy2:
                mucom2.rhythmdta[r.getSi() & 0xffff] = r.al;
                r.setSi((short) ((r.getSi() + 1) & 0xffff));
            }

            while (true) {
                // Reset Mode
//rhy0:
                mucom2.chktxt();
                if (r.al == '?') {
                    // reverse accent specification
                    r.setSi((short) ((r.getSi() - 1) & 0xffff));

                    byte saved = mucom2.rhythmdta[r.getBx() & 0xffff];
                    mucom2.rhythmdta[(r.getSi() + 1) & 0xffff] = (byte) 0xfd;
                    mucom2.rhythmdta[(r.getSi() + 1) & 0xffff] = saved;
                    continue;
                }
//rhy3:
                if (r.al == (byte) 0x22) {
                    // staccato specification
                    r.setSi((short) ((r.getSi() - 1) & 0xffff));
                    byte saved = mucom2.rhythmdta[r.getSi() & 0xffff];
                    mucom2.rhythmdta[(r.getSi() + 1) & 0xffff] = (byte) 0xfc;
                    mucom2.rhythmdta[(r.getSi() + 1) & 0xffff] = saved;
                    continue;
                }
//rhy4:
                if (r.al == '!') {
                    // accent specification
                    r.setSi((short) ((r.getSi() - 1) & 0xffff));
                    byte saved = mucom2.rhythmdta[r.getSi() & 0xffff];
                    mucom2.rhythmdta[(r.getSi() + 1) & 0xffff] = (byte) 0xfe;
                    mucom2.rhythmdta[(r.getSi() + 1) & 0xffff] = saved;
                    continue;
                }
//rhy1:
                if (r.al == ')') {
//rhythm_end:
                    mucom2.rhythmdta[r.getSi() & 0xffff] = 0;
                    return;
                }
                r.setBx((short) ((r.getBx() & 0xffff) - 1));
                chkcm(); // "," check
                r.dl--;
                r.carry = (r.dl & 0xff) < 48;
                r.cl = 8;
                break;
            }
        } while (r.carry);

        mucom2.error();
    }

    public void init_rhythm() {
        r.push(r.getAx());
        r.setAx((short) 0); // ofs:rhythmdta
        mucom2.rhyadrs = r.getAx() & 0xffff; // Initialize rhythm address
        r.setAx(r.pop());
    }

    /**
     * @ENDIF process
     */
    private void mendif() {
        r.cl = 35;
        r.al = (byte) (mucom2.jumpnes & 0xff);
        if (r.al == 0) {
            mucom2.error();
            return;
        }
        r.dl = 1; // check for if then
        set_exit();
    }

    /**
     * Keyboard display mask set/reset
     */
    private void key_maskset() {
        r.setAx((short) 0x1ec);
        mucom2.stoswObjBufAX2DI();
    }

    private void key_maskreset() {
        mucom2.chktxt();
        chknum();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        if (r.carry) {
//maskr1:
            r.setAx((short) 0xec);
//maskr2:
            mucom2.stoswObjBufAX2DI();
            return;
        }
        mucom2.rednums(); // AL = color code (1-6)
        r.cl = 6;
        if (r.al == 0) {
            mucom2.error();
            return;
        }
        if ((r.al & 0xff) > 6) {
            mucom2.error();
            return;
        }
        r.al = (byte) ((r.al & 0xff) << 5);
        r.ah = r.al;
        r.al = (byte) 0xec;
//maskr2:
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * Repeat loop command processing in ()
     */
    public void nloop1() {
        r.push(r.getBx());
        mucom2.nesting++; // increment nest count
        r.al = mucom2.nesting;
        r.carry = ((r.al & 0xff) > 15); // nest overflow check
        r.cl = 3;
        if (r.carry) {
            mucom2.error();
            return;
        }

        r.al--;
        r.push(r.getAx());
        r.al = (byte) 0xe0;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff)); // E0 : clear loop counter
        r.setAx(r.pop());
        r.ah = 0; // clear high byte before shift (C# parity)
        int bxIdx = (r.getAx() & 0xffff) << 2; // ax * 4
        mucom2.stttbl[bxIdx / 2] = r.di; // Store address where "(" started
        mucom2.stttbl[(bxIdx + 2) / 2] = 0;
        r.setBx(r.pop());
        r.al = mucom2.chglen; // Set previous note length and ratio
        mucom2.setrat();
        mucom2.optimiz = 0; // Clear optimization flag
        mucom2.init_looplen();
    }

    /**
     * () loop end processing
     */
    public void nloop2() {
        mucom2.rednums(); // Read repeat count
        r.zero = (r.al == 0);
        r.cl = 1; // error if 0
        if (r.zero) {
            mucom2.error();
            return;
        }
        mucom2.set_looplen();
        r.push(r.getBx());
        r.push(r.getAx());
        r.al = mucom2.nesting; // nest count -1
        r.carry = ((r.al & 0xff) < 1);
        r.al--;
        mucom2.nesting = r.al;
        r.cl = 13; // more ")" than "("
        if (r.carry) {
            mucom2.error();
            return;
        }
        int bxIdx = ((r.getAx() & 0xff) << 2);
        r.setDx((short) (mucom2.stttbl[bxIdx / 2] - r.di)); // Calculate offset from current to start
        r.setDx((short) -(r.getDx() & 0xffff));
        r.al = (byte) 0xf7;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        r.setAx(r.getDx());
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff)); // Set offset to return address
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
        r.setAx(r.pop());
        r.setBx((short) mucom2.stttbl[(bxIdx + 2) / 2]); // BX = @if exit address
        if (r.getBx() != 0) {
            muap98.objectBuf.get(r.getBx() & 0xffff).dat = r.al; // Store final number
        }
        r.setBx(r.pop());
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff)); // Set loop count
        r.al = mucom2.nesting;
        r.setAx((short) ((r.getAx() & 0xffff) + 1));
        r.dh = r.al; // DH = nest value
        r.cl = 35;
        r.al = (byte) (mucom2.jumpnes & 0xff); // if exit exists?
        if (r.al == 0) {
//nloop4:
            return;
        }
        r.dl = 2; // for checking if exit
        set_exit();
    }

    private void set_exit() {
        r.push(r.getDx()); // Save DX since it's used below
        r.push(r.getBx());
        r.ah = 0;
        r.setAx((short) ((r.getAx() - 1) & 0xffff));
        r.setBx(r.getAx());
        r.setBx((short) ((r.getBx() & 0xffff) << 2));
        r.setBx((short) ((r.getBx() + 0) & 0xffff));// MUCOM2.IFSTACK;
        r.ds = (short) Muap98.text; // mov ds,text ; DS:BX = address of IF stack
        r.setAx((short) ((mucom2.IFSTACKbuf[r.getBx() & 0xffff] & 0xff) | ((mucom2.IFSTACKbuf[(r.getBx() + 1) & 0xffff] & 0xff) << 8)));
        if (r.al == r.dl) {
            boolean flg = false;
            if (r.dl != 1) { // Do not check nest level for @if then
                if (r.ah != r.dh) { // Consistent with nest?
                    flg = true;
                }
            }
            if (!flg) {
//nloop5:
                r.setAx(r.di);
                r.setSi((short) ((mucom2.IFSTACKbuf[(r.getBx() + 2) & 0xffff] & 0xff) | ((mucom2.IFSTACKbuf[(r.getBx() + 3) & 0xffff] & 0xff) << 8)));
                r.setBx(r.pop());
                r.setDx(r.pop());
                r.setAx((short) ((r.getAx() & 0xffff) - (r.getSi() & 0xffff)));
                r.setAx((short) ((r.getAx() + 1) & 0xffff));
                muap98.objectBuf.add(r.getSi() & 0xffff, new MmlDatum(r.al & 0xff));
                muap98.objectBuf.add(r.getSi() & 0xffff, new MmlDatum(r.ah & 0xff));
                mucom2.jumpnes--;
                r.al = mucom2.chglen; // Set previous note length and ratio
                mucom2.setrat();
                mucom2.optimiz = 0; // Clear optimization flag
                return;
            }
        }
//nloop3:
        r.setBx(r.pop());
        r.setDx(r.pop());
//nloop4:
    }

    /**
     * SSG channel check
     */
    private boolean check_ssg() {
        int sendCh = (mucom2.sendch & 0xff);
        if (sendCh < 4) return false; // ignore and return if not channels 4-6
        if (sendCh < 7) return true;
        return false;
    }

    private boolean skip_ssg() {
        int sendCh = (mucom2.sendch & 0xff);
        if (sendCh < 4) return false;
        if (sendCh >= 7) return false; // ignore and return if channels 4-6
        return true;
    }

    /**
     * Noise frequency set
     */
    public void noise() {
        mucom2.rednums(); // Read numeric value from text
        if (!check_ssg()) return; // Ignore if not SSG

        r.carry = ((r.al & 0xff) < 32); // range check
        r.cl = 6;
        if (!r.carry) {
//error13:
            mucom2.error();
            return;
        }
        r.ah = r.al;
        r.al = (byte) 0xf6;
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * Envelope shape setting
     */
    public void env_type() {
        mucom2.rednums(); // Read numeric value from text
        if (!check_ssg()) return; // Ignore if not SSG
        r.carry = ((r.al & 0xff) < 16);
        r.cl = 6;
        if (!r.carry) {
//error13:
            mucom2.error();
            return;
        }
        r.ah = r.al;
        r.al = (byte) 0xed;
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * Envelope speed setting
     */
    public void env_speed() {
        mucom2.rednum(); // Read numeric value from text
        if (!check_ssg()) return;
        muap98.objectBuf.set(r.di++, new MmlDatum(0xee));
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * Envelope use/unuse process (Px)
     */
    public void envelope() {
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        xsmall();
        if (r.al == 'S') {
            // PSxx,xx,xx command
            sdecay();
            return;
        }
        if (r.al == 'M') {
            // PMx command
            mixer();
            return;
        }
        if (r.al == 'A') {
            // PAxx,xx command
            attack();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        mucom2.rednums(); // P0/P1 digit
        if (!check_ssg()) return; // Ignore if not SSG
        r.carry = ((r.al & 0xff) < 2); // range check
        r.cl = 6;
        if (!r.carry) {
//error13:
            mucom2.error();
            return;
        }
        r.ah = (byte) ((r.al & 0xff) << 4); // ENV On/Off (16/0)
        r.al = (byte) 0xef;
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * SSG tone, noise selection command 'PMx'
     */
    private void mixer() {
        mucom2.rednums(); // Read numeric value from text
        if (!check_ssg()) return;
        r.cl = 6; // range check
        if ((r.al & 0xff) >= 3) {
            mucom2.error();
            return;
        }
        r.dl = r.al;
        r.al &= 1; // b0 = 1 TONE Cancel
        r.dl &= 2; // b1 = 1 NOISE Cancel
        r.dl <<= 1;
        r.dl <<= 1;
        r.al |= r.dl; // AL = b4,b0 data exists
        r.al |= (byte) 0b11110110; // Mask unnecessary bits

        r.cl = (byte) ((mucom2.sendch & 0xff) - 4);
        r.al = r.rol(r.al, (r.cl & 0xff)); // rotate by channel
        r.al &= (byte) 0b10111111; // set b6=0
        r.ah = r.al;
        r.al = (byte) 0xeb; // +++
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * SSG initial decay speed setting 'PSx'
     */
    private void sdecay() {
        mucom2.rednums(); // Read numeric value from text
        if (!check_ssg()) return;
        r.ah = r.al;
        r.al = (byte) 0xf2;
        mucom2.stoswObjBufAX2DI();
        chkcm();
        mucom2.rednums();
        mucom2.stosbObjBufAL2DI();
        chkcm();
        mucom2.rednums();
        mucom2.stosbObjBufAL2DI();
    }

    /**
     * Start volume, attack specification 'PAx,x'
     */
    private void attack() {
        mucom2.rednums(); // AL = start volume
        r.cl = 6;
        if ((r.al & 0xff) != 255) {
            if ((r.al & 0xff) > 127) {
                // error27: ; only 0-127, 255 allowed
                mucom2.error();
                return;
            }
        }
//attack1:
        r.ah = r.al;
        r.al = (byte) 0xd5;
        mucom2.stoswObjBufAX2DI();
        chkcm();
        mucom2.rednums();
        mucom2.stosbObjBufAL2DI();
        mucom2.chktxt();
        if (r.al == ',') {
            sdecay();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
    }

    /**
     * Direct register data output command
     */
    public void reg() {
        mucom2.rednums(); // Read numeric value from text
        r.ch = r.al; // CH = address
        chkcm(); // "," check
        mucom2.rednums(); // DL = data
        r.dl = r.al;
        set16();
    }

    /**
     * Direct output hexadecimal version
     */
    public void reghex() {
        get2hex(); // Read 2-digit hex data
        r.ch = r.dl; // CH = address
        chkcm(); // "," check
        get2hex(); // DL = data
        set16();
    }

    private void set16() {
        r.al = (byte) 0xf1; // DH = ADRS , DL = DATA
        mucom2.stosbObjBufAL2DI();
        r.al = r.dl;
        r.ah = r.ch;
        if (r.ah == 7) {
            // Y7, specification?
            r.al |= 0x80;
            r.al &= 0xbf; // force b7,b6 = 10
        }
//set17:
        mucom2.stoswObjBufAX2DI();
    }

    private void get2hex() {
        gethex(); // Read hex data from text
        r.dl <<= 4; // 0x to x0
        r.dh = r.dl;
        gethex();
        r.dl |= r.dh; // DL = get xx
    }

    private void gethex() {
        r.dl = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if ((r.dl & 0xff) >= 'a') r.dl -= ' ';
        r.dl -= (byte) '0';
        if ((r.dl & 0xff) >= 10) r.dl -= 7;
    }

    /**
     * PCM address specification
     */
    private void pcm_adrs() {
        r.al = (byte) 0xd4;
        mucom2.stosbObjBufAL2DI();
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        set4hex();
        chkcm();
        set4hex();
    }

    private void set4hex() {
        get2hex(); // DL = 2-digit hex data
        r.ah = r.dl;
        get2hex();
        r.al = r.dl;
        mucom2.stoswObjBufAX2DI(); // Store start address
    }

    /**
     * YM-2203 user tone setting
     * Z@x,@x(:),[I,][E,FB,],CN(:),
     *              AR,DR,SR,RR,SL,TL,KR,MP,DT(:),
     *                 (same data op2-op4)
     */
    public void usr_tone() {
        gettpara(); // AL = @xx tone number
        mucom2.from_no = r.al; // Copy source tone number
        chkcm(); // "," check
        gettpara();
        mucom2.to_no = r.al; // Copy destination tone number
        r.push(r.getCx());
        r.push(r.es);
        copy_tone(); // Copy tone parameters
        r.setSi(r.getDx()); // ES:SI = address of tone number to change

        chkcm2(); // ",:" check
        if (r.carry) {
//cut_param:
            usr_tone_cut();
            return;
        }

        // Check for inversion mode (I)
        mucom2.mode[0] &= 0xdf; // Clear inversion mode
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        xsmall();
        if (r.al == 'I') {
            // n88basic(86) mode (I)?
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            chkcm();
            mucom2.mode[0] |= 0x20; // Set inversion mode
//next_param1:
            r.bp = 0; // operator number
            r.setDx((short) 24); // storage offset (SI+BP+DL), no inversion
            r.setCx((short) 0); // OR DATA=0(CH), SHIFT=0(CL)
        } else if (r.al != 'E') {
//next_param1:
            r.bp = 0;
            r.setDx((short) 24);
            r.setCx((short) 0);
        } else {
            // FB, CN separate specification
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            chkcm();
            r.bp = 0; // OP number
            r.setDx((short) 24); // storage offset (SI+BP+DL), DH=0
            r.setCx((short) 0x703); // CH=or data, CL=shift counts
            paramain(); // parameter process
            chkcm();
            r.setCx((short) 0x3800); // CN
        }

        // Acquisition of connection data
//next_param2:
        paramain();
        r.setCx((short) 4); // Loop for operator 1-4

//getloop:
        do {
            chkcm2();
            if (r.carry) {
//cut_param:
                usr_tone_cut();
                return;
            }
            r.push(r.getCx());

            // Acquisition of AR, DR, SR
            r.setCx((short) 3);
            r.push(r.getSi());
//setar:
            do {
                r.push(r.getCx());
                r.setDx((short) 0x1f08); // storage offset (SI+BP+DL), DH=inversion data
                r.setCx((short) 0xe000); // OR DATA(CH), SHIFT(CL)
                paramain();
                chkcm();
                r.setSi((short) ((r.getSi() & 0xffff) + 4)); // $50,$60,$70 follows
                r.setCx(r.pop());
                r.setCx((short) ((r.getCx() & 0xffff) - 1));
            } while (r.getCx() != 0);
            r.setSi(r.pop());

            // Acquisition of RELEASE RATE
            r.setDx((short) 0xf14);
            r.setCx((short) 0xf000);
            paramain();
            chkcm();

            // Acquisition of SUSTAIN LEVEL
            r.setDx((short) 0xf14);
            r.setCx((short) 0xf04);
            paramain();
            chkcm();

            // Acquisition of TOTAL LEVEL
            r.setDx((short) 0x7f04);
            r.setCx((short) 0);
            paramain();
            chkcm();

            // Acquisition of KEY SCALE RATE
            r.setDx((short) 8);
            r.setCx((short) 0x3f06);
            paramain();
            chkcm();

            // Acquisition of MULTIPLE
            r.setDx((short) 0);
            r.setCx((short) 0xf000);
            paramain();
            chkcm();

            // Acquisition of DETUNE
            mucom2.chktxt();
            if (r.al == '-') {
                getnum(); // negative data
                r.al |= 4;
//minusdta:
                r.al <<= 4; // move to b6-b4
                r.al |= 0xf; // b3-b0 as is
                int siIdx = (r.getSi() & 0xffff) + (r.bp & 0xffff);
                muap98.toneBuff[siIdx] |= 0xf0;
                muap98.toneBuff[siIdx] &= r.al; // Change only DT
            } else {
//plusdta:
                r.setBx((short) ((r.getBx() & 0xffff) - 1));
                getnum();
                if (!r.carry) {
//minusdta:
                    r.al <<= 4;
                    r.al |= 0xf;
                    int siIdx = (r.getSi() & 0xffff) + (r.bp & 0xffff);
                    muap98.toneBuff[siIdx] |= 0xf0;
                    muap98.toneBuff[siIdx] &= r.al;
                }
            }
//skipdt:
            r.setCx(r.pop());
            r.bp++; // To next operator
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        chkcm3(); // Last comma can be either
        // Store as performance data
        usr_tone_cut();
    }

    private void usr_tone_cut() {
        // r.es was pushed in usr_tone (saving the parser's ES); preserve tone seg in DX
        r.setDx(r.es);
        r.es = r.pop();
        r.al = (byte) 0xe6;
        r.ah = mucom2.to_no; // copy destination tone number
        muap98.objectBuf.set(r.di++ & 0xffff, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di++ & 0xffff, new MmlDatum(r.ah & 0xff)); // 27-byte instruction
        r.setCx((short) 25); // tone data length
        r.push(r.ds);
        r.ds = r.getDx(); // DS:SI = configured tone data address
        do {
            muap98.objectBuf.set(r.di++ & 0xffff, new MmlDatum(muap98.toneBuff[r.getSi() & 0xffff] & 0xff)); // ES:DI = performance data address
            r.setSi((short) ((r.getSi() & 0xffff) + 1));
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);
        r.ds = r.pop();
        r.setCx(r.pop());
    }

    /**
     * Comma check in text
     */
    private void chkcm2() {
        r.carry = false;
        mucom2.chktxt(); // check for "," ":"
        if (r.al == ':') {
//syorya:
            r.carry = true; // CY if subsequent parameters omitted (:)
            return;
        }
        if (r.al != ',') {
//zerr:
            r.cl = 29;
            mucom2.error();
        }
    }

    private void chkcm3() {
        mucom2.chktxt(); // for final check
        if (r.al == ',' || r.al == ':') {
//chkcm4:
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
//chkcm4:
    }

    /**
     * Check for parameter omission
     * exit CY = 1 : no parameter
     *      AX = numeric data if present
     */
    private void getnum() {
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        if (r.al == ',' || r.al == ':') {
//no_num:
            r.carry = true;
            return;
        }
        mucom2.rednums();
        r.carry = false;
    }

    /**
     * Acquisition of @xx tone number (AL)
     */
    private void gettpara() {
        mucom2.chktxt(); // Acquisition of next character
        if (r.al != '@') {
//zerr:
            r.cl = 29;
            mucom2.error();
            return;
        }
        mucom2.rednums(); // tone number
    }

    /**
     * Acquisition and storage of other parameters
     * entry CL = left shift level
     *       CH = or data
     *       DL = SI offset
     *       DH = 0: no inversion check
     *            <>0: maximum value for inversion
     *       ES:SI = tone param address
     *       BP = operator number (0-)
     */
    private void paramain() {
        getnum();
        if (r.carry) {
            // parameter omitted
            return;
        }
        r.push(r.getSi());
        r.push(r.getDx());
        r.push(r.getCx());

        // inversion check needed?
        if (r.dh != 0) {
            if ((mucom2.mode[0] & 0x20) != 0) {
                // inversion mode?
                r.dh = (byte) ((r.dh & 0xff) - (r.al & 0xff));
                r.al = r.dh; // invert AL data
            }
        }

//parainv:
        r.dh = 0;
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getDx() & 0xffff))); // add offset to SI
        r.al = (byte) ((r.al & 0xff) << (r.cl & 0xff));
        r.al |= r.ch;
        r.ch = (byte) ~(r.ch & 0xff);
        int siIdx = (r.getSi() & 0xffff) + (r.bp & 0xffff);
        muap98.toneBuff[siIdx] |= r.ch;
        muap98.toneBuff[siIdx] &= r.al;
        r.setCx(r.pop());
        r.setDx(r.pop());
        r.setSi(r.pop());
//skip_para:
    }

    /**
     * Copy tone parameters (YM2203)
     * entry from_no = source number
     *       to_no   = destination number
     * exit  ES:DX = destination parameter address
     */
    private void copy_tone() {
        r.push(r.getAx());
        r.push(r.getBx());
        r.push(r.getCx());
        r.push(r.getSi());
        r.push(r.di);

        r.push(r.ds);
        r.al = mucom2.from_no;
        byte[] tbuf = mucom2.tone_adrs(); // DS:SI = source tone data address
        r.setSi((short) (r.getBx() & 0xffff));
        r.al = mucom2.to_no;
        mucom2.tone_adrs();
        r.di = (short) (r.getBx() & 0xffff); // DS:DI = destination data address (DI,DX)
        r.setDx((short) (r.di & 0xffff));
        r.push(r.ds); // DS = tone data segment
        r.es = r.pop();
        r.setCx((short) 25);
        do {
            tbuf[r.di & 0xffff] = tbuf[r.getSi() & 0xffff]; // Transfer tone data
            r.di = (short) ((r.di & 0xffff) + 1);
            r.setSi((short) ((r.getSi() & 0xffff) + 1));
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);
        r.ds = r.pop();

        r.di = r.pop();
        r.setSi(r.pop());
        r.setCx(r.pop());
        r.setBx(r.pop());
        r.setAx(r.pop());
    }

    /**
     * Expansion of single character macro
     * entry AL = character
     */
    public void macro_exec() {
        mucom2.mode[1] |= 2; // single character macro flag
        mucom2.set_symbol2();
        mucom2.symbol2 |= 2; // do not output single character macro internal
        r.push(r.di); // search string, store
        mucom2.macroflg = 0; // clear variable specification flag
        mucom2.wordbuf[0] = r.al; // store search character
        r.dl = 1; // specify string length
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        if (r.al == ',') {
            getword4();
            return;
        }
        chknum(); // is macro variable specified?
        if (!r.carry) {
            getword4(); // store variable
            return;
        }
        getword2(); // execute search
    }

    /**
     * $word source replacement command processing
     */
    public void dtcall() {
        mucom2.mode[1] &= 0xfd; // clear single character macro flag
        r.push(r.di); // search string, store
        mucom2.macroflg = 0; // clear variable specification flag
        int diVal = 0; // ofs:wordbuf
        r.dl = 0; // length of string
//getword1:
        do {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if (r.al == '$' || r.al == ' ' || r.al == 9) {
                // separator character found?
                getword2();
                return;
            }
            if ((r.al & 0xff) == 0xfe) {
                getword9();
                return;
            }
            if (r.al == (byte) ',') {
                // check for macro variable specification
                getword4();
                return;
            }
            r.cl = 21; // error code
            r.dl++;
            if (r.dl == 33) {
                // string up to 32 characters
                break;
            }
            mucom2.wordbuf[diVal++] = r.al; // store string in buffer
        } while (true);
//derror:
        mucom2.error();
    }

    /**
     * Check for macro variables
     */
    private void getword4() {
        mucom2.macrov[18 / 2] = (short) (r.getBx() & 0xffff); // Save parameter text start address
        r.push(r.getSi());
        r.push(r.getDx());
        r.setDx((short) 1); // SI = variable value storage address
        int siIdx = 0; // ofs:macrov ; SI+18 = stored text address of specified variable
//getword6:
        while (true) {
            chkcall();
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            if (r.al != ',') {
                // was the parameter omitted?
                mucom2.macrov[(siIdx + 18) / 2] = (short) (r.getBx() & 0xffff); // store text start address
                mucom2.rednum(); // read variable value to set
                mucom2.macroflg |= (r.getDx() & 0xffff); // set specified flag
                mucom2.macrov[siIdx / 2] = (short) (r.getAx() & 0xffff);
                skiplen(); // skip note length parameter
            }
//getword7:
            siIdx += 2; // to next variable number
            int dxVal = (r.getDx() & 0xffff) << 1;
            r.setDx((short) dxVal);
            if (siIdx < 18) {
                chkcall();
                if (r.al == ',') {
                    // is there a next variable?
                    continue;
                }
                r.setBx((short) ((r.getBx() & 0xffff) - 1));
            }
//getword5:
            break;
        }
        r.setDx(r.pop());
        r.setSi(r.pop());
        getword8();
    }

    private void getword9() {
        check_flatclear(); // Clear temporary key signature on line break
        getword2();
    }

    private void getword2() {
        mucom2.macrov[18 / 2] = (short) (r.getBx() & 0xffff); // Save parameter text start address
        getword8();
    }

    private void getword8() {
        r.di = r.pop();
        mucom2.cal_num = r.dl; // Save string length
        skiplen(); // Skip note length parameter
        mucom2.nest2++;
        r.carry = (mucom2.nest2 & 0xff) > 10; // Nest up to 10 levels
        r.cl = 20;
        if (r.carry) {
            mucom2.error();
            return;
        }

        //
        // Check single character macro cache
        //
        r.push((short) mucom2.linedta); // Save line number and source position
        int oldCol = work.col;
        r.push(r.getBx());
        if ((mucom2.mode[1] & 2) != 0) {
            // single character macro?
            r.push(r.ds);
            r.push(r.getBx());
            calc_macache();
            int cacheVal = (mucom2.MACACHEbuf[r.getBx() & 0xffff] & 0xff) | ((mucom2.MACACHEbuf[(r.getBx() & 0xffff) + 1] & 0xff) << 8); // AX = cache data
            r.setBx(r.pop());
            r.ds = r.pop();
            if (cacheVal != 0) {
                // is it a hit?
                r.setBx((short) cacheVal);
                // skip search and execute (hit)
                chk_dc5();
                return;
            }
        }

//cache1:
        mucom2.linedta = 1; // search call destination from start
        work.row = 1;
        work.oldbx = 0;
        r.setBx((short) 0);
//chk_dc1:
        while (true) {
            do {
                chkcall();
            } while (r.al != '$'); // was there original data for replacement?

            r.push(r.di); // string comparison
            int dlVal = (mucom2.cal_num & 0xff); // DL = string length
            int diVal = 0; // ofs:wordbuf
            boolean match = true;
//chk_dc4:
            while (dlVal != 0) {
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                if ((r.al & 0xff) == 0xff || (r.getBx() & 0xffff) >= Muap98.buflens) {
                    // EOF? or source buffer exceeded?
                    chk_err0();
                    return;
                }
                if (r.al != mucom2.wordbuf[diVal++]) {
                    // compare with replacement string
//chk_dc3:
                    match = false;
                    break;
                }
                dlVal--;
            }
            r.di = r.pop();
            if (match) {
                chkcall();
                if (r.al == '[' || r.al == '$') {
                    // check final character ($, [)
                    if (r.al == '$') {
                        chkcall();
                        if (r.al != '[') continue;
                    }
                    break; // found
                }
            }
        }

//chk_dc2:
        if ((mucom2.mode[1] & 2) != 0) {
            // single character macro?
            r.push(r.ds);
            r.push(r.getBx());
            r.setDx(r.getBx());
            calc_macache();
            mucom2.MACACHEbuf[r.getBx() & 0xffff] = r.dl;
            mucom2.MACACHEbuf[(r.getBx() & 0xffff) + 1] = r.dh; // Save as cache data
            r.setBx(r.pop());
            r.ds = r.pop();
        }

        chk_dc5();
    }

    private void chk_dc5() {
        while (true) {
            chkcall();
            if (r.al == ']') {
                // note data end mark?
                break;
            }

            r.push(r.getCx());
            r.push((short) mucom2.macroflg); // save specified flag only
            r.push((short) mucom2.macrov[0]); // save up to 3 variables
            r.push((short) mucom2.macrov[1]);
            r.push((short) mucom2.macrov[2]);
            r.push((short) mucom2.macrov[9]);
            mucom2.com_main(); // conversion of replacement data
            mucom2.macrov[9] = r.pop();
            mucom2.macrov[2] = r.pop();
            mucom2.macrov[1] = r.pop();
            mucom2.macrov[0] = r.pop();
            mucom2.macroflg = r.pop();

            r.push(r.getAx());
            r.setAx((short) (Muap98.bufleno - 0x10)); // is performance buffer exceeded?
            r.carry = (r.di & 0xffff) < (r.getAx() & 0xffff);
            r.cl = 2;
            r.setAx(r.pop());
            if (!r.carry) {
                mucom2.error();
                return;
            }
            r.setCx(r.pop());
        }
//chk_end0:
        r.setBx(r.pop()); // restore source address
        mucom2.linedta = r.pop();
        work.row = mucom2.linedta;
        mucom2.symbol2 &= 0xfd;
        mucom2.nest2--;
    }

    /**
     * Calculation of macro cache address
     * exit DS:BX = macro cache address
     */
    private void calc_macache() {
        r.setAx((short) 0);
        r.al = mucom2.wordbuf[0];
        xsmall(); // AL = 41-5Ah
        int val = (r.al & 0xff) - 0x41;
        r.setAx((short) (val << 1));
        r.setBx(r.getAx());
        // add bx, MACACHE ; DS:BX = macro cache address
    }

    /**
     * Skip processing of note length parameters
     * entry DS:BX = source text address
     * exit  DS:BX = skipped address
     * break AL
     */
    private void skiplen() {
        while (true) {
            chkcall(); // read text
            if (r.al == '.' || r.al == '^' || r.al == '=') continue;
            chknum();
            if (r.carry) break;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
    }

    /**
     * Text reading routine for replacement
     * entry DS:BX = text address
     * exit  DS:BX = next text address
     *       AL = extracted character
     */
    private void chkcall() {
        while (true) {
            if ((r.getBx() & 0xffff) >= Muap98.buflens) {
                // source buffer END?
                chk_err0();
                return;
            }
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) == 0xff) {
                // EOF
                chk_err0();
                return;
            }

            if (r.al == ' ' || r.al == 9) continue;
            if (r.al != (byte) 0xfe) {
                // CR,LF code?
//dtcall3:
                if (r.al == ';') {
                    // is it a comment?
//dtcall4:
                    while (true) {
                        r.al = muap98.sourceBuf[r.getBx() & 0xffff]; // skip REM
                        r.setBx((short) ((r.getBx() & 0xffff) + 1));
                        if ((r.al & 0xff) == 0xff) {
                            // EOF check
                            chk_err0();
                            return;
                        }
                        if ((r.al & 0xff) == 0xfe) break; // ignore until CR,LF
                    }
                } else return;
            }
//chkcall1:
            mucom2.linedta++;
            work.row++;
            work.oldbx = (r.getBx() & 0xffff);
        }
    }

    private void chk_err0() {
        r.setAx(r.pop());
        r.setBx(r.pop());
        mucom2.linedta = r.pop();
        work.row = mucom2.linedta;
        work.col = 1;
        r.cl = 19; // replacement data not found
        mucom2.error();
    }

    /**
     * Specify speed of trill
     */
    private void trspeed() {
        trspeed0();
        mucom2.trildef = r.al;
    }

    /**
     * Trill speed acquisition
     */
    private void trspeed0() {
        mucom2.tnelnmx();
        r.zero = (r.al == 0);
        r.cl = 6;
        if (r.zero) mucom2.error();
    }

    /**
     * Triller command processing
     */
    private int trillsub() {
        mucom2.tridta1 = 0;
        mucom2.tridta2 = 0; // Clear +/- specification during trill

        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        if (r.al == '%') {
            mucom2.tridta1 = 3;
            mucom2.tridta2 = 3; // Make it natural
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        }
//trill0:
        if (r.al == '-') {
            mucom2.tridta1 = 2; // Specify flat for note above
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        }
//trill1:
        if (r.al == '+') {
            mucom2.tridta2 = 1; // Specify sharp for note below
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        }
//trill2:
        chknum(); // Number check
        if (r.carry) {
            r.al = mucom2.trildef; // Default triller is L32
        } else {
//num_set:
            trspeed0(); // Acquire trill note length
        }
//num_not:
        mucom2.trillen = r.al;

        // Check <> and note
        while (true) {
            skipoct(); // Read next text and execute <>
            if (r.al == '@') {
                // @ extended command also allowed
//tr_set:
                mucom2.mode[0] |= 4; // flag to execute <>
                exp_cmd(); // execute @xx extended command
                continue;
            }
            break;
        }

        r.cl = 17;
        check_onpu(); // Must be a note next
        mucom2.trionpu = r.al; // Save note
        save_oct(); // Save octave

        // Check accidental and note length
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucom2.gethenon(); // check #+-% and return in DL
        mucom2.tridta0 = r.dl;
        mucom2.tnelnmx(); // Acquisition of note length data (AL)
        mucom2.add_tlen();
        mucom2.totalen = r.al;
        if (mucom2.harmno != 0) {
            // Check if chord mode
            if (tr_harm_mode() != 0) return 1;
        }
        r.al = mucom2.trillen; // Specify trill note length
        mucom2.setrat();
        return 0;
    }

    public void check_onpu() {
        mucom2.set_symbol2(); // Store source address
        if ((r.al & 0xff) < 'A') {
            // Check if note exists
            mucom2.error();
            return;
        }
        if ((r.al & 0xff) > 'G') {
            mucom2.error();
        }
    }

    /**
     * Chord processing during trill
     */
    private int tr_harm_mode() {
        mucom2.harm_main(); // Check for chord existence
        if (!r.carry) {
            mucom2.get_harm(); // Acquisition of chord note (execution of @+, <>)
            mucom2.trionpu = r.al;
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            mucom2.gethenon();
            mucom2.tridta0 = r.dl; // Specification of accidental
            harm_onpu(); // Skip past ":"
            return 0;
        }
//tr_noharm:
        r.dl = mucom2.totalen; // No chord, so specify rest
        mucom2.kyufu();
        // Note: in trill process, trill_sub is called directly.
        r.setAx(r.pop());
        r.setAx(r.pop());
        return 1;
    }

    /**
     * Save/restore octave
     */
    private void save_oct() {
        r.push(r.getAx());
        r.al = mucom2.octdata;
        mucom2.octsave = r.al;
        r.al = mucom2.ratdata;
        mucom2.slursav = r.al; // ratio preservation
        r.al = 0;
        mucom2.chgrat(); // set to Q8
        r.setAx(r.pop());
    }

    private void load_oct() {
        r.push(r.getAx());
        r.al = mucom2.octsave;
        mucom2.octdata = r.al;
        r.al = mucom2.slursav;
        mucom2.chgrat(); // Restore Q
        r.setAx(r.pop());
    }

    /**
     * Play one note above/below
     * entry AL = note code
     *       DL = accidental specification
     */
    private void addonpu() {
        r.al++;
        if (r.al == 'C') {
            // B-C then one octave UP
            mucom2.octdata++;
        }
        if (r.al == 'H') {
            r.al = 'A';
        }
        read();
    }

    /**
     * Play one note below
     */
    private void subonpu() {
        r.al--;
        if (r.al == 'B') {
            // C-B then one octave DOWN
            mucom2.octdata--;
        }
        if (r.al == '@') {
            r.al = 'G';
        }
        read();
    }

    /**
     * Specify remainder note length from total length
     * entry DL = number of trill soundings
     */
    private void calcrest() {
        r.push(r.getAx());
        r.al = mucom2.trillen;
        r.mul(r.dl);
        r.dl = r.al;
        r.al = mucom2.totalen;
        r.carry = ((r.al & 0xff) <= (r.dl & 0xff));
        r.al = (byte) ((r.al & 0xff) - (r.dl & 0xff));
        r.cl = 15; // length does not remain
        if (r.carry) {
            mucom2.error();
            return;
        }
        mucom2.setrat(); // specify remainder length
        r.setAx(r.pop());
    }

    /**
     * @SACF short appoggiatura process
     */
    private void sacf_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu; // fundamental note
        triexe(new byte[] {(byte) 0x85}); // 1, add
        r.dl = 1; // count
        calcrest(); // specify remainder length
        triexe(new byte[] {(byte) 0x82}); // 0, sub
        load_oct(); // restore octave
    }

    /**
     * @SACS short appoggiatura process
     */
    private void sacs_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu; // fundamental note
        triexe(new byte[] {(byte) 0x8a}); // 2, sub
        r.dl = 1; // count
        calcrest(); // specify remainder length
        triexe(new byte[] {(byte) 0x81}); // 0, add
        load_oct(); // restore octave
    }

    /**
     * @TRN turn process
     */
    private void trn_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu; // fundamental note
        triexe(new byte[] {5, 2, (byte) 0x8a}); // 1, add 0, sub 2, sub
        r.dl = 3; // count
        calcrest(); // specify remainder length
        triexe(new byte[] {(byte) 0x81}); // 0, add
        load_oct(); // restore octave
    }

    /**
     * @XTRN reverse turn process
     */
    private void xtrn_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu; // fundamental note
        triexe(new byte[] {0x0a, 0x01, (byte) 0x85}); // 2, sub 0, add 1, add
        r.dl = 3; // count
        calcrest(); // specify remainder length
        triexe(new byte[] {(byte) 0x82}); // 0, sub
        load_oct(); // restore octave
    }

    /**
     * @MTRN middle turn process
     */
    private void mtrn_ent() {
        if (trillsub() != 0) return;
        r.dl = 4;
        calcrest(); // specify remainder length
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x80}); // fundamental note (0, read)
        r.push(r.getAx());
        r.al = mucom2.trillen;
        mucom2.setrat(); // specify trill note length
        r.setAx(r.pop());
        triexe(new byte[] {0x05, 0x02, 0x0a, (byte) 0x81}); // 1, add 0, sub 2, sub 0, add
        load_oct(); // restore octave
    }

    /**
     * @XMTRN middle reverse turn process
     */
    private void xmtrn_ent() {
        if (trillsub() != 0) return;
        r.dl = 4;
        calcrest(); // specify remainder length
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x80}); // fundamental note
        r.push(r.getAx());
        r.al = mucom2.trillen;
        mucom2.setrat(); // specify trill note length
        r.setAx(r.pop());
        triexe(new byte[] {0x0a, 0x01, 0x05, (byte) 0x82}); // 2, sub 0, add 1, add 0, sub
        load_oct(); // restore octave
    }

    /**
     * @MOR mordent process
     */
    private void mor_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu; // fundamental note
        triexe(new byte[] {0x00, (byte) 0x85}); // 0, read 1, add
        r.dl = 2; // count
        calcrest(); // specify remainder length
        triexe(new byte[] {(byte) 0x82}); // 0, sub
        load_oct(); // restore octave
    }

    /**
     * @XMOR reverse mordent process
     */
    private void xmor_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu; // fundamental note
        triexe(new byte[] {0x00, (byte) 0x8a}); // 0, read 2, sub
        r.dl = 2; // count
        calcrest(); // specify remainder length
        triexe(new byte[] {(byte) 0x81}); // 0, add
        load_oct(); // restore octave
    }

    /**
     * @CAD cadence process
     */
    private void cad_ent() {
        if (trillsub() != 0) return;
        calc_tri(); // calculate number of trills
        if ((r.cl & 0xff) < 2) {
//terror:
            r.cl = 15; // does not remain
            mucom2.error();
            return;
        }
        r.cl--; // reduce count for first and last notes
        set_tri1();
//rest_cad:
        triexe(new byte[] {(byte) 0x82}); // 0, sub
        rest_tri();
    }

    /**
     * @XCAD reverse cadence process
     */
    private void xcad_ent() {
        if (trillsub() != 0) return;
        calc_tri(); // calculate number of trills
        if ((r.cl & 0xff) < 3) {
//terror:
            r.cl = 15; // does not remain
            mucom2.error();
            return;
        }
        r.cl -= 2;
        set_tri1();
//rest_xcad:
        triexe(new byte[] {0x02, 0x0a, (byte) 0x81}); // 0, sub 2, sub 0, add
        rest_tri();
    }

    /**
     * @IDM idem process
     */
    private void idm_ent() {
        if (trillsub() != 0) return;
        calc_tri(); // calculate number of trills
        if ((r.cl & 0xff) < 3) {
//terror:
            r.cl = 15; // does not remain
            mucom2.error();
            return;
        }
        r.cl -= 2;
        set_tri2();
//rest_cad:
        triexe(new byte[] {(byte) 0x82}); // 0, sub
        rest_tri();
    }

    /**
     * @XIDM reverse idem process
     */
    private void xidm_ent() {
        if (trillsub() != 0) return;
        calc_tri(); // calculate number of trills
        if ((r.cl & 0xff) < 4) {
//terror:
            r.cl = 15; // does not remain
            mucom2.error();
            return;
        }
        r.cl -= 3;
        set_tri2();
//rest_xcad:
        triexe(new byte[] {0x02, 0x0a, (byte) 0x81}); // 0, sub 2, sub 0, add
        rest_tri();
    }

    /**
     * @TRI trill process
     */
    private void tri_ent() {
        if (trillsub() != 0) return;
        calc_tri(); // CL = number of trills, CH = remaining length
        set_tri(); // Repeat fundamental and note above CL times
        rest_tri(); // remaining length process
    }

    /**
     * Calculation of trill count
     * exit CL = number of trills
     *      CH = remaining length
     *      AL = trionpu
     */
    private void calc_tri() {
        int axVal = (mucom2.totalen & 0xff); // total length
        r.cl = (byte) ((mucom2.trillen & 0xff) * 2); // trill length
        r.ah = 0;
        r.setAx((short) axVal);
        r.div(r.cl); // AL=AX/CL, trill count
        r.setCx(r.getAx()); // AH=remaining length
        if (r.cl == 0) {
//terror:
            r.cl = 15; // does not remain
            mucom2.error();
            return;
        }
        r.al = mucom2.trionpu; // fundamental note
    }

    /**
     * Repeat fundamental/note above
     * entry CL = repeat count
     */
    private void set_tri2() {
        triexe(new byte[] {0x05, (byte) 0x82}); // 1, add 0, sub (@idm, @xidm)
        set_tri1();
    }

    private void set_tri1() {
        triexe(new byte[] {0x0a, 0x01, (byte) 0x85}); // 2, sub 0, add 1, add (@cad, @xcad)
        while (true) {
//tri0:
            r.cl--;
            if (r.cl == 0) return;
//tri_loop1:
            triexe(new byte[] {0x02, (byte) 0x85}); // use this from second time
        }
    }

    private void set_tri() {
        triexe(new byte[] {0x00, (byte) 0x85}); // 0, read 1, add (@tri)
        while (true) {
//tri0:
            r.cl--;
            if (r.cl == 0) return;
//tri_loop1:
            triexe(new byte[] {0x02, (byte) 0x85}); // use this from second time
        }
    }

    /**
     * Trill remaining length process
     * entry CH = remaining length
     */
    private void rest_tri() {
        if (r.ch == 0) {
//tri_end:
            load_oct(); // restore octave
            return;
        }
        r.al = r.ch;
        mucom2.setrat(); // set remaining length
        kwait0(); // store @W command
//tri_end:
        load_oct(); // restore octave
    }

    /**
     * @XMMTRN Combination of reverse mordent and middle turn
     */
    private void xmmtrn_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu;
        r.dl = mucom2.tridta0;
        read(); // sound fundamental note
        triexe(new byte[] {(byte) 0x8a});
        r.dl = 6;

        calcrest(); // specify remaining length
        triexe(new byte[] {(byte) 0x81}); // 0, add

        r.push(r.getAx());
        r.al = mucom2.trillen;
        mucom2.setrat(); // specify trill length
        r.setAx(r.pop());

        triexe(new byte[] {0x05, 0x02, 0x0a, (byte) 0x81}); // 1, add 0, sub 2, sub 0, add
        load_oct(); // restore octave
    }

    /**
     * @ACS accent steigend
     */
    private void acc_ent() {
        if (trillsub() != 0) return;
        accsub(); // set initial length
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x8a}); // 2, sub
        r.dl = 1;

        calcrest(); // specify remaining length
        triexe(new byte[] {(byte) 0x81}); // 0, add
        load_oct(); // restore octave
    }

    /**
     * @ACF accent fallend
     */
    private void xacc_ent() {
        if (trillsub() != 0) return;
        accsub(); // set initial length
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x85}); // 1, add
        r.dl = 1;

        calcrest(); // specify remaining length
        triexe(new byte[] {(byte) 0x82}); // 0, sub
        load_oct(); // restore octave
    }

    /**
     * accent fallend subroutine
     */
    private void accsub() {
        r.al = mucom2.totalen; // total length
        r.dl = r.al;
        r.dh = 9;
        r.ah = 0;
        r.div(r.dh); // is it dotted note?
        r.al = r.dl;
        if (r.ah == 0) {
//futen:
            r.dh = 3;
            r.ah = 0;
            r.div(r.dh);
            r.al = (byte) ((r.al & 0xff) + (r.al & 0xff)); // make it 2/3 length
        } else {
            r.al = (byte) ((r.al & 0xff) >> 1); // halve if normal note
        }

//accsub1:
        mucom2.trillen = r.al;
        mucom2.setrat();
        r.al = (byte) 0xdf; // +++
        mucom2.stosbObjBufAL2DI(); // set to Q8
    }

    /**
     * @AMTRN process
     */
    private void amtrn_ent() {
        if (trillsub() != 0) return;
        r.dl = 3;
        calcrest();
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x85});
        r.push(r.getAx());
        r.al = mucom2.trillen;
        mucom2.setrat();
        r.setAx(r.pop());
        triexe(new byte[] {0x02, 0x05, (byte) 0x82});
        load_oct();
    }

    /**
     * @XAMTRN process
     */
    private void xamtrn_ent() {
        if (trillsub() != 0) return;
        r.dl = 3;
        calcrest();
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x8a});
        r.push(r.getAx());
        r.al = mucom2.trillen;
        mucom2.setrat();
        r.setAx(r.pop());
        triexe(new byte[] {0x01, 0x0a, (byte) 0x81});
        load_oct();
    }

    /**
     * Trilling subroutine
     * entry AL = note code
     *       call triexe
     *       db ..., b7=1
     * data  b3,b2 : 00 = accidental No.0
     *               01 = accidental No.1
     *               10 = accidental No.2
     *       b1,b0 : 00 = same note
     *               01 = note above
     *               10 = note below
     */
    private void triexe(byte[] dat) {
        r.setSi((short) 0); // r.pop(); // return address acquisition
        r.push(r.getCx());

//triexe1:
        do {
            r.cl = dat[r.getSi() & 0xffff];
            r.cl = (byte) (r.cl & 0x0c); // save b3,b2
            r.dl = mucom2.tridta0;
            if (r.cl == 4) {
                // 00 = fundamental, 01 = above, 10 = below
                r.dl = mucom2.tridta1;
            }
            if (r.cl == 8) {
                r.dl = mucom2.tridta2;
            }

            r.cl = dat[r.getSi() & 0xffff];
            r.cl = (byte) (r.cl & 3); // save b1,b0
            if (r.cl == 0) {
                read();
            }
            r.cl--;
            if (r.cl == 0) {
                addonpu();
            }
            r.cl--;
            if (r.cl == 0) {
                subonpu();
            }
            r.setSi((short) ((r.getSi() & 0xffff) + 1));
            r.test(dat[(r.getSi() - 1) & 0xffff], (byte) 0x80);
        } while (r.zero); // return to address after parameters

        r.setCx(r.pop());
        //r.push(r.si);
    }

    /**
     * Transposition command processing
     * entry BX = Source address
     * exit  ichosav = Transposition data
     */
    public void icho() {
        r.dl = 0;
//icho4:
        do {
            do {
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
                r.ah = muap98.sourceBuf[(r.getBx() & 0xffff) + 1];
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                if (r.al != '<') {
                    break;
                }
                r.dl -= 12; // Transpose 1 octave down
            } while (true);
//icho5:
            if (r.al != '>') {
                break;
            }
            r.dl += 12; // Transpose 1 octave up
        } while (true);
//icho6:
        xsmall(); // AL uppercase conversion
        r.al -= 'A' & 0xff; // Read transposition note
        r.carry = (r.al & 0xff) < 7;
        r.cl = 23;
        if (!r.carry) {
//error28:
            mucom2.error();
            return;
        }

        r.push(r.getBx());
        r.push(r.getAx());
        r.setBx((short) 0); // ofs:ichodta ; Obtain actual shift value from note
        r.ah = 0;
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getAx() & 0xffff)));
        r.dl += mucom2.ichodta[r.getBx() & 0xffff]; // Add transposition data by note
        r.setAx(r.pop());
        r.setBx(r.pop());

        if (r.ah == '-') {
            // Check for flat
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.dl--;
//icho2:
            mucom2.ichosav = r.dl; // Save transposition data
            return;
        }
//icho1:
        if (r.ah != '#') {
            // Check for sharp
            if (r.ah != '+') {
//icho2:
                mucom2.ichosav = r.dl; // Save transposition data
                return;
            }
        }
//icho3:
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.dl++;
//icho2:
        mucom2.ichosav = r.dl; // Save transposition data
    }

    public void makeDatum(MMLType type) {
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        lp.chip = work.crntChip;
        lp.ch = (byte) work.crntChannel;
        lp.part = work.crntPart;
        work.md = new MmlDatum(type, new ArrayList<>(), lp, 0);
    }

    /**
     * Detune setting
     */
    private void detune() {
        makeDatum(MMLType.Detune);

        r.setSi((short) 0); // ofs:dtdata
        get_detune();
        mucom2.dtdata[r.getSi() & 0xffff] = r.al;

        work.md.args.add("D"); // Normal detune
        work.md.args.add((int) (byte) r.al); // Detune value (int) - sign extended
        // Kuma: Since @DT is managed by the compiler, information is entrusted to the next command
        work.lstMd.add(work.copy(work.md, 0xff));

        r.setSi((short) ((r.getSi() & 0xffff) + 1));
        mucom2.chktxt();
        if (r.al == ',') {
            r.cl = 16;
            check_314();
            if (!r.zero) {
                // Multiple parameters can be used only on ch 3, 14
                mucom2.error();
                return;
            }

            r.dl = 2;
//detune2:
            do {
                get_detune();
                mucom2.dtdata[r.getSi() & 0xffff] = r.al;
                r.setSi((short) ((r.getSi() & 0xffff) + 1));
                chkcm();
                r.dl--;
            } while (r.dl != 0);

            get_detune();
            mucom2.dtdata[r.getSi() & 0xffff] = r.al;
            mucom2.dt2mode = 1; // Specification of multiple detune
            if (mucom2.codemod == 1) {
                // Chord output mode is disabled
                mucom2.codemod = 0;
            }
            r.setAx((short) 0x40ed);
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
            muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff)); // Set to sound effect mode
            return;
        }

//detune1:
        mucom2.dt2mode = 0; // Standard detune
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        check_314();
        if (r.zero) {
//detune3:
            r.setAx((short) 0x00ed);
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
            muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff)); // Set to standard mode
        }
//detune3:
    }

    /**
     * Detune value acquisition
     */
    private void get_detune() {
        mucom2.chktxt();
        if (r.al == '-') {
            mucom2.rednums();
            r.setAx((short) -(r.getAx() & 0xffff));
            return;
        }
//deplus:
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        mucom2.rednums();
    }

    /**
     * System detune setting
     */
    private void sysdetune() {
        r.cl = 38;
        if ((r.ch & 0xff) == 10) {
            // PCM, Rhythm prohibited
//error28:
            mucom2.error();
            return;
        }
        if ((r.ch & 0xff) == 11) {
//error28:
            mucom2.error();
            return;
        }
        get_detune();
        r.ah = r.al;
        r.al = (byte) 0xf0;
        mucom2.stoswObjBufAX2DI();
    }

    /**
     * ch3, 14 only check
     * entry CH = ch number (1-17)
     * exit  Z = ch 3, 14
     */
    public void check_314() {
        if ((r.ch & 0xff) == 3) {
            r.zero = true;
            return;
        }
        r.zero = ((r.ch & 0xff) == 14);
//cch1:
    }

    /**
     * Setting of detune shift value
     */
    private void detune_shift() {
        r.setSi((short) 0);
        detune_sub();
        mucom2.chktxt();
        if (r.al == ',') {
            check_314();
            if (!r.zero) {
                mucom2.error();
                return;
            }
            r.dl = 2;
            do {
                detune_sub();
                chkcm();
                r.dl--;
            } while (r.dl != 0);
            detune_sub();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
    }

    /**
     * Detune shift value setting subroutine
     */
    private void detune_sub() {
        mucom2.rednums();
        r.cl = 6;
        if ((r.al & 0xff) > 31) {
            mucom2.error();
            return;
        }
        mucom2.dtshift[r.getSi() & 0xffff] = r.al;
        r.setSi((short) ((r.getSi() & 0xffff) + 1));
    }

    /**
     * Detune calculation
     */
    private void calcdt() {
        int p = (r.getSi() & 0xffff) + 4;
        r.cl = (p < 4) ? mucom2.dtdata[p] : mucom2.dtshift[p - 4];
        if ((r.cl & 0xff) >= 16) {
            // @ds16-31 is increasing direction
//calcdt1:
            r.cl = (byte) ((r.cl & 0xff) - 16);
            r.setAx((short) ((r.getAx() & 0xffff) << (r.cl & 0xff)));
            return;
        }

        r.setAx((short) (r.getAx() >> (r.cl & 0xff))); // sar ax,cl
    }

    /**
     * Calculate actual output value from note data
     * DL = para data (0,1,2,3,4,5)=( ,#,-,%,##,--)
     * DI = object offset
     * AL = music chr code (CDEFGAB)
     */
    public void read() {
autotie0: {
        cres_check(); // Crescendo check
        pan_check(); // Auto-pan check
        read_main(); // Main conversion
        r.push(r.getAx());
        r.push(r.getDx());
        check_tiemode();
        if (!r.zero) {
            break autotie0;
        }
        r.setDx((short) mucom2.lastfrq); // Consistent with previous frequency?
        r.zero = (r.getDx() == ((muap98.objectBuf.get(r.di - 2).dat & 0xff) | ((muap98.objectBuf.get(r.di - 1).dat & 0xff) << 8))); // Compare with current frequency
        if (!r.zero) {
            break autotie0;
        }
        r.setAx(r.di);
        r.setAx((short) ((r.getAx() - 2) & 0xffff));
        // AX = Address where the note was just stored
        r.setDx((short) 1);
        mucom2.move_obj(); // Move performance data (1 byte)
        r.push(r.di);
        r.di = (short) (r.getAx() & 0xffff);
        r.al = (byte) 0xe1; // Store tie command there +++
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        r.di = r.pop();
}
//autotie0: ↑
        r.setDx(r.pop());
        r.setAx(r.pop());
    }

    /**
     * Auto-tie check
     * exit Z = auto-tie mode
     */
    private void check_tiemode() {
        r.push(r.getAx());
        r.al = mucom2.mode[0];
        mucom2.mode[0] &= 0x7f; // Clear auto-tie flag
        if (mucom2.sendch == 11) {
            r.setAx((short) 0);
            r.setAx((short) (r.getAx() + 1));
            r.zero = false;
            // Prohibit auto-tie only for PCM
        } else {
//ctie1:
            r.al ^= (byte) 0x80;
            r.test(r.al, (byte) 0x88); // Is auto-tie specified (Zero)?
        }
//ctie2:
        r.setAx(r.pop());
    }

    /**
     * Note intermediate code analysis processing
     */
    private void read_main() {
        mucom2.dionpu = r.di; // Save perform address of note
        r.push(r.getSi());
        r.push(r.getDx());
        r.push(r.getCx());
        r.push(r.getBx());
        r.push(r.getAx());

        r.al -= (byte) 'A';
        r.setBx((short) 0); // ofs:musdata
        r.ah = 0;
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getAx() & 0xffff))); // Convert note to intermediate code
        r.cl = mucom2.musdata[r.getBx() & 0xffff]; // CDEFGAB to 0-11
        if (r.dl == 3) {
            // Natural (%)
            natural();
            return;
        }
        if (r.dl != 0) {
            // Ignore key signature when #, - is specified
            natural();
            return;
        }

        r.setBx((short) 0); // ofs:flatdata ; Incorporate key signature into intermediate code
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getAx() & 0xffff)));
        r.al = mucom2.octdata;
        r.ah = 7;
        r.mul(r.ah);
        r.setSi(r.getAx()); // SI = octave * 7
        int p = (r.getBx() & 0xffff) + (r.getSi() & 0xffff) + 7;
        r.al = (p < 7) ? mucom2.flatdata[p] : mucom2.flatdata2[p - 7];
        if (r.al == 0) {
            // Does temporary accidental exist? (0 if none)
            nature1();
            return;
        }

        r.al &= 7; // Keep only b2-b0
        if (r.al == 2) r.al = (byte) 0xff; // Convert to flat
        if (r.al == 3) r.al = 2; // Convert to double sharp
        if (r.al == 4) r.al = (byte) 0xfe; // Convert to double flat
        r.cl += r.al; // AL = 0,1,-1(%,#,-)

        // Incorporate only temporary accidental
        natural();
    }

    /**
     * Entry for code conversion
     * entry AL = intermediate code (0-11)
     */
    public void read2() {
        r.push(r.getSi());
        r.push(r.getDx());
        r.push(r.getCx());
        r.push(r.getBx());
        r.push(r.getAx());
        natent();
    }

    /**
     * Add key signature
     */
    private void nature1() {
        r.cl += mucom2.flatdata[r.getBx() & 0xffff]; // Incorporate key signature
        natural();
    }

    private void natural() {
        r.al = r.cl;
        r.dl--;
        if (r.dl == 0) r.al++; // DL=1 : sharp
        r.dl--;
        if (r.dl == 0) r.al--; // DL=2 : flat
        r.dl--;
        r.dl--;
        if (r.dl == 0) r.al += 2; // DL=4 : double sharp
        r.dl--;
        if (r.dl == 0) r.al -= 2; // DL=5 : double flat
        natent();
    }

    private void natent() {
        r.push((short) ((mucom2.octdata & 0xff) | ((mucom2.octsave & 0xff) << 8)));
        r.al += mucom2.ichosav; // Intermediate code incorporating transposition
//icho_loop:
        do {
            if ((r.al & 0xff) < 12) {
                // Became data for 1 octave up
                break;
            }
            r.test(r.al, (byte) 0x80); // Became data for 1 octave down
            if (r.zero) {
//icho_octup:
                r.al -= 12;
                mucom2.octdata++;
                continue;
            }
            r.al += 12;
            mucom2.octdata--;
        } while (true);

//icho_oct:
        r.carry = ((mucom2.octdata & 0xff) < 9); // Is octave value out of range?
        r.cl = 24;
        if (!r.carry) {
            mucom2.error();
            return;
        }
        r.ah = 0;

        // Note: final pitch determined in r.ax (0-11)
        work.ontei = r.getAx();
        work.oct = mucom2.octdata;

        r.setAx((short) ((r.getAx() & 0xffff) + (r.getAx() & 0xffff)));

        mucom2.onpucnt++; // Tuplet counter
        r.setSi((short) 0); // ofs:dtdata ; SI = detune storage address
        r.ch = mucom2.sendch;
        int sendChVal = (r.ch & 0xff);
        if (sendChVal < 4) {
            // FM/SSG check
            readf();
            return;
        }
        if (sendChVal < 7) {
            reads();
            return;
        }
        if (sendChVal < 10) {
            readf();
            return;
        }
        if (sendChVal == 10) {
            read_exit();
            return;
        }
        if (sendChVal >= 12) {
            readf();
            return;
        }

        // PCM conversion routine
        // entry	AX = 0-22 (CDEFGAB*2)
        //      	octdata = octave value

        r.setBx((short) 0); // ofs:data3 ; BX = PCM O4 delta-N table
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getAx() & 0xffff)));
        r.al = mucom2.octdata; // AL = octave(0-7)
        short ans = r.pop();
        mucom2.octdata = (byte) (ans & 0xff);
        mucom2.octsave = (byte) (ans >> 8);
        r.setDx((short) ((mucom2.data3[r.getBx() & 0xffff] & 0xff) | ((mucom2.data3[(r.getBx() & 0xffff) + 1] & 0xff) << 8))); // DX = DELTA-N data
        if ((r.al & 0xff) > 7) {
//error18:
            r.cl = 36;
            mucom2.error();
            return;
        }
        if ((r.al & 0xff) != 7) {
            r.cl = 6;
            r.cl = (byte) ((r.cl & 0xff) - (r.al & 0xff));
            r.setDx((short) ((r.getDx() & 0xffff) >> (r.cl & 0xff)));
        } else {
//octave5:
            r.carry = ((r.getDx() & 0xffff) << 1) > 0xffff;
            r.setDx((short) ((r.getDx() & 0xffff) << 1));
            if (r.carry) {
//error18:
                r.cl = 36;
                mucom2.error();
                return;
            }
        }

//readpcm1:
        r.ah = mucom2.dtdata[0]; // Set detune
        mucom2.freq_lfo(); // Frequency correction
        calcdt();
        r.setDx((short) ((r.getDx() & 0xffff) + (r.getAx() & 0xffff)));
        r.al = (byte) 0xd5;

        if (work.md == null) muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        else {
            work.md.dat = r.al;
            work.md.args.set(work.mdArgsStep + 0, work.ontei + work.oct * 12);
            work.md.args.set(work.mdArgsStep + 1, work.otoLength);
            work.md.linePos.chip = work.crntChip;
            work.md.linePos.ch = (byte) work.crntChannel;
            work.md.linePos.part = work.crntPart;
            muap98.objectBuf.set(r.di++, work.md);
        }

        r.setAx(r.getDx());
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
        read_exit();
    }

    /**
     * Conversion routine for FM OPN
     */
    private void readf() {
        r.setBx((short) 0); // ofs:data1
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getAx() & 0xffff))); // BX =  music data offset
        r.al = mucom2.octdata; // oct data
        short ans = r.pop();
        mucom2.octdata = (byte) (ans & 0xff);
        mucom2.octsave = (byte) (ans >> 8);

        // Exception processing for O9
        if (r.al == 8) {
            r.al = 0x38;
            r.setDx((short) ((mucom2.data1[r.getBx() & 0xffff] & 0xff) | ((mucom2.data1[(r.getBx() & 0xffff) + 1] & 0xff) << 8)));
            r.setDx((short) ((r.getDx() & 0xffff) + (r.getDx() & 0xffff)));
            r.dh = (byte) ((r.dh & 0xff) + (r.al & 0xff));
            if ((r.getDx() & 0xffff) > 0x3fff) r.setDx((short) 0x3fff);
        } else {
//not_o9:
            r.al = (byte) ((r.al << 3) + mucom2.data1[(r.getBx() & 0xffff) + 1]); // set F-Number1 & Block
            r.dh = r.al;
            r.dl = mucom2.data1[r.getBx() & 0xffff];
        }

//set_o9:
        if (mucom2.dt2mode != 0) {
            // Multiple detune specification?
            r.al = (byte) 0xfa; // +++
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff)); // Output for sound effect mode
            r.setCx((short) 3);
//multi_dt:
            do {
                r.push(r.getDx());
                r.push(r.getCx());
                set_dtfreq();
                r.setSi((short) ((r.getSi() & 0xffff) + 1));
                r.setCx(r.pop());
                r.setDx(r.pop());
                r.setCx((short) ((r.getCx() & 0xffff) - 1));
            } while (r.getCx() != 0);
        }
//norm_dt:
        set_dtfreq();
        read_exit();
    }

    /**
     * Read exit
     */
    private void read_exit() {
        r.setAx(r.pop());
        r.setBx(r.pop());
        r.setCx(r.pop());
        r.setDx(r.pop());
        r.setSi(r.pop());
    }

    private void set_dtfreq() {
        r.push(r.getDx());
        r.dh &= 7;
        r.ah = mucom2.dtdata[r.getSi() & 0xffff]; // Set detune
        mucom2.freq_lfo(); // Frequency correction
        calcdt();
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getDx() & 0xffff)));
        r.setDx(r.pop());
        r.setDx((short) (r.getDx() & 0xf800));
        r.setAx((short) ((r.getAx() & 0xffff) + (r.getDx() & 0xffff)));
        byte tmp = r.al;
        r.al = r.ah;
        r.ah = tmp;

        if (work.md == null) muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        else {
            work.md.dat = r.al;
            work.md.args.set(work.mdArgsStep + 0, work.ontei + work.oct * 12);
            work.md.args.set(work.mdArgsStep + 1, work.otoLength);
            work.md.linePos.chip = work.crntChip;
            work.md.linePos.ch = (byte) work.crntChannel;
            work.md.linePos.part = work.crntPart;
            muap98.objectBuf.set(r.di++, work.md);
        }

        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
        // set F-Number2
    }

    /** Conversion routine for SSG */
    private void reads() {
        r.setBx((short) 0); // ofs:data2
        r.setBx((short) ((r.getBx() & 0xffff) + (r.getAx() & 0xffff)));
        r.setDx((short) ((mucom2.data2[r.getBx() & 0xffff] & 0xff) | ((mucom2.data2[(r.getBx() & 0xffff) + 1] & 0xff) << 8)));

        r.al = mucom2.octdata; // load oct
        short ans = r.pop();
        mucom2.octdata = (byte) (ans & 0xff);
        mucom2.octsave = (byte) (ans >> 8);

//loop16:
        while (r.al != 0) {
            r.setDx((short) ((r.getDx() & 0xffff) >> 1));
            r.al--;
        }

//exit8:
        r.ah = mucom2.dtdata[0]; // Set detune
        mucom2.freq_lfo(); // Frequency correction
        calcdt();
        r.setDx((short) ((r.getDx() & 0xffff) - (r.getAx() & 0xffff)));
        r.al = r.dh;
        r.ah = r.dl;

        if (work.md == null) muap98.objectBuf.set(r.di++, new MmlDatum(r.al & 0xff));
        else {
            work.md.dat = r.al;
            work.md.args.set(work.mdArgsStep + 0, work.ontei + work.oct * 12);
            work.md.args.set(work.mdArgsStep + 1, work.otoLength);
            work.md.linePos.chip = work.crntChip;
            work.md.linePos.ch = (byte) work.crntChannel;
            work.md.linePos.part = work.crntPart;
            muap98.objectBuf.set(r.di++, work.md);
        }

        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah & 0xff));
        read_exit();
    }

    //
    // AL lowercase -> uppercase conversion
    //
    public void xsmall() {
        if ((r.al & 0xff) > 'z') return;
        if ((r.al & 0xff) >= 'a') r.al -= (byte) ' ';
//nxsmall:
    }

    //
    // AL digit check (\ ok)
    // exit	CY = non-digit
    //
    public void chknum() {
        if (r.al != (byte) '\\') { // Macro variable?
            chknum2();
            return;
        }
        r.push(r.getAx());
        r.al = (byte) (((r.getBx() & 0xffff) + 1) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[(r.getBx() & 0xffff) + 1]); // Check next character
        chknum2();
        if (r.carry) {
            // Out of range digit is no good
//chknum3:
            r.setAx(r.pop());
            return;
        }
        r.carry = ((r.al & 0xff) < '1');
        r.al = (byte) ((r.al & 0xff) - '1'); // AL = macro variable number (0-8)
        if (r.carry) {
//chknum3:
            r.setAx(r.pop());
            return;
        }
        r.push(r.getCx());
        r.cl = r.al;
        r.setAx((short) 1);
        r.setAx((short) (r.getAx() << (r.cl & 0xff))); // AX = bit value for specified variable
        r.zero = ((mucom2.macroflg & (r.getAx() & 0xffff)) != 0); // Is it stored?
        r.setCx(r.pop());
        r.carry = false;
        if (r.zero) {
            r.setBx((short) ((r.getBx() & 0xffff) + 2)); // Delete if not specified
            r.carry = true;
        }

//chknum3:
        r.setAx(r.pop());
    }

    /**
     * Digit check
     */
    private void chknum2() {
        if ((r.al & 0xff) < '0') {
            r.carry = true;
            return;
        }
        r.carry = ((r.al & 0xff) < '9' + 1);
        r.carry = !r.carry;
//chknum1:
    }

    /**
     * Comma check
     */
    private void chkcm() {
        mucom2.chktxt();
        r.zero = (r.al == ',');
        r.cl = 32;
        if (!r.zero) mucom2.error();
    }

    private Tuple<String, Runnable>[] exCmdTbl;
    /**
     * Define in descending order of command name length
     * Define command names in uppercase
     */
    private void InitExCmdTbl() {
        exCmdTbl = new Tuple[] {new Tuple<String, Runnable>("J", this::ExcmdJump)};
    }

    /**
     * Searches for extended commands from current buffer position and executes related processing if present.
     * <remarks>If matching command found, buffer position proceeds past command and related action is executed.
     * Command matching is case-insensitive.</remarks>
     * @return true if extended command found and executed, false otherwise.
     */
    private boolean SearchExtendCommand() {
        for (Tuple<String, Runnable> cmd : exCmdTbl) {
            int cmdPtr = 0;
            int ptr = (r.getBx() & 0xffff);
            while (true) {
                byte ch = (byte) (ptr >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[ptr]);
                if (ch == 0) break;
                if (ch >= (byte) 'a') ch -= (byte) ' '; // Uppercase conversion
                if (ch != (byte) cmd.getItem1().charAt(cmdPtr)) break;
                ptr++;
                cmdPtr++;
                if (cmdPtr < cmd.getItem1().length()) continue;

                // Command found
                r.setBx((short) ptr);
                cmd.getItem2().run();
                return true;
            }
        }
        return false;
    }

    /**
     * @J command processing
     * Marks next playback position as jump point, indicating playback will skip to next note or rest.
     */
    private void ExcmdJump() {
        // Mark skip position as next note or rest
        work.lstMd.add(new MmlDatum(MMLType.SkipPlay, new ArrayList<>(List.of(1)), null, -1));

        // Indicate jump instruction at start of performance data
        if (!muap98.objectBuf.isEmpty()) {
            MmlDatum md = muap98.objectBuf.getFirst();
            if (md.args == null) md.args = new ArrayList<>();
            md.args.add(new MmlDatum(MMLType.SkipPlay, null, null, -1));
        }
    }
}
