//
// Music Macro Assembler for MUAP98 <for Extended command>
//
// Copyright (c) 1987,1989-1995 by Packen Software [dec.29.1995]
//

package muap.compiler;

import java.util.ArrayList;
import java.util.Arrays;

import dotnet4j.util.compat.Tuple;
import muap.common.X86Register;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.GD3Tag;
import musicDriverInterface.LinePos;
import musicDriverInterface.MMLType;
import musicDriverInterface.MmlDatum;
import musicDriverInterface.Tag;


/**
 * Handles extended command parsing, macro expansions, and musical data conversion.
 */
public class MucomSub {

    private X86Register r;
    public Mucom2 mucom2;
    public Muap98 muap98;
    public short[] labelAdrs = new short[40 * 17]; // Area to save label addresses (40 labels x 17 channels)
    private Work work;

    public MucomSub(X86Register r, Mucom2 mucom2, Muap98 muap98, Work work) {
        this.r = r;
        this.mucom2 = mucom2;
        this.muap98 = muap98;
        this.work = work;

        InitCmddata();
        initCmdJump();
        InitExCmdTbl();
    }

    //
    // Branch processing for extended command @xxxx
    //
    public void exp_cmd() {
        r.setSi((short) 0); // ofs:cmddata
        r.cl = 0;

//cmd5:
        do {
            r.push(r.getBx());

//cmd2:
            do {
                r.al = (byte) cmddata.charAt(r.getSi());
                r.setSi((short) (r.getSi() + 1));

                if ((r.al & 0xff) == 255) {
                    if (SearchExtendCommand()) return;

                    cmd3(); // Not found
                    return;
                }

                if (r.al == (byte) ' ') {
                    cmd1(); // Found
                    return;
                }

                r.dl = (byte) ((r.getBx() & 0xffff) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[r.getBx() & 0xffff]);
                r.setBx((short) ((r.getBx() & 0xffff) + 1));

                if (r.dl >= (byte) 'a') {
                    // Convert to uppercase
                    r.dl -= (byte) ' ';
                }

            } while (r.al == r.dl);

//cmd4:
            do {
                r.al = (byte) cmddata.charAt(r.getSi()); // Move to next search character
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
        int idx = (r.getAx() & 0xffff);
        r.dh = 0; // For flat/sharp
        cmdjump[idx].run();
    }

    private void cmd3() {
        r.setBx(r.pop());
        tonex(); // To @xx command
    }

    private String cmddata;

    private void InitCmddata() {
        cmddata = "V W JUMP CALL " // 0
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
                this::freq_sub, this::key_maskset, this::key_maskreset, this::ifch1, // 80
                this::sysdetune, this::if_abort, this::fade_out, this::acc_vol, // 84
                this::down_vol, this::ssgmode, this::pcmmode, this::subratio, // 88
                this::channel, this::last_set, this::reverve, // 92
                this::pan_left, this::pan_mono, this::pan_right, this::auto_pan, // 95
        };
    }

    //
    // Instrument setting command processing
    //

    private void tonex() {
        mucom2.rednums(); // Read numbers from text
        r.dl = r.al; // DL = Tone number
        mucom2.chktxt();
        if (r.al == (byte) '=') {
            // Replace specification
            tone_change();
            return;
        }
        if (r.al == 0x22) {
            // User PCM specification
            pcm_load();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        xchg_tone(); // Replace tone number
        if ((r.ch & 0xff) >= 4) {
            if ((r.ch & 0xff) < 7) {
                retssg(); // Skip for SSG
                return;
            }
            if ((r.ch & 0xff) == 10) {
                save_rhythm(); // Rhythm is @1-@63
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
        if ((mucom2.optimiz & 1) == 0 || r.al != mucom2.opt_tne) {
            // Same as before, don't set
//tonex1:
            mucom2.opt_tne = r.al; // Save set tone number
            mucom2.optimiz |= 1; // Set flag
            r.ah = r.al;

            r.al = (byte) 0xeb; // Set tone number
            LinePos linePos = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
            linePos.chip = work.crntChip;
            linePos.chipNumber = 0;
            linePos.ch = (byte) work.crntChannel;
            linePos.part = work.crntPart;
            MmlDatum md = new MmlDatum(r.al, MMLType.Instrument, linePos, new Object[] {0, (int) (r.ah & 0xff)});
            md = work.FlashLstMd(md);
            muap98.objectBuf.set(r.di, md);

            muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
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

        r.ah = r.al; // AH = Tone number
        r.al = (byte) 0xf6; // Reuse n command
        muap98.objectBuf.set(r.di, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;
    }

    private void save_rhythm() {
        r.cl = 6;
        if (r.al == 0 || (r.al & 0xff) > 63) {
            mucom2.error();
            return;
        }

        mucom2.rhydata = r.al; // Key on next time 'K' is encountered
    }

    private void xchg_tone() {
        r.push(r.ds);
        r.push(r.getSi());
        r.ds = (short) muap98.text;
        int idx = (r.getDx() & 0xffff);
        r.al = mucom2.TONEOFSbuf[idx]; // AL = Converted tone number
        r.setSi(r.pop());
        r.ds = r.pop();
    }

    //
    // Tone number replacement specification
    //
    private void tone_change() {
        mucom2.chktxt();
        if (r.al != (byte) '@') {
            // Also allow @x=@y
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
        }
//tonec1:
        mucom2.rednums(); // AL = New tone number
        r.push(r.ds);
        r.push(r.getSi());
        r.ds = (short) muap98.text;
        int idx = (r.getDx() & 0xffff); // SI = index of corresponding tone
        mucom2.TONEOFSbuf[idx] = r.al;
        r.setSi(r.pop());
        r.ds = r.pop();
    }

    //
    // Switching SSG/PCM mode
    //

    private void ssgmode() {
        mucom2.ssgpcmm = 0;
        r.setAx((short) 0x80d0);
        mucom2.stoswObjBufAX2DI();
    }

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
        mucom2.ssgpcmm = 1; // SSGPCM
        r.setAx((short) 0x81d0);
        mucom2.stoswObjBufAX2DI();
    }

    //
    // Switching to extended PCM
    //

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
        r.dl = (byte) (mucom2.MAXBUF - 2);
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

    //
    // Single character replacement specification
    //

    private void macro_small() {
        r.al = 1; // Lowercase mode
        mac_set();
    }

    private void macro_large() {
        r.al = 2; // Uppercase mode
        mac_set();
    }

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
        mucom2.rednums(); // Read numbers from text
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

    //
    // PCM read specification
    // entry	DL = Tone number
    //

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
            if ((r.al & 0xff) <= (byte) ' ') {
                r.cl = 39;
                mucom2.error();
                return;
            }
            if (r.al == 0x22) {
                // End specification
//_load5
                r.setCx((short) ((r.getCx() & 0xffff) + 3)); // Include extension part
                _load7();
                return;
            }
            if (r.al == (byte) '.') {
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
        if (r.al != (byte) '.') {
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
                xsmall();
            }
            if ((r.al & 0xff) <= (byte) ' ') {
                r.cl = 39;
                mucom2.error();
                return;
            }
            if (r.al == 0x22) {
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
        r.zero = (r.al == (byte) ','); // Volume specified? (for SSGPCM)
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
        r.al = (byte) 0xf8;
        mucom2.stosbObjBufAL2DI();
        r.setAx(r.pop());
        mucom2.stoswObjBufAX2DI();
        r.al = r.dl;
        r.ah = (byte) 0xe1; // Also store tie
        mucom2.stoswObjBufAX2DI();
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
        muap98.objectBuf.set(r.di, new MmlDatum((byte) 0xcf));
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
        if (r.al == (byte) '#') {
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
        if (r.al == (byte) '=') {
            if_match();
            return;
        }
        r.dh = 0x10;
        if (r.al == (byte) '>') {
            if_match();
            return;
        }
        r.dh = 0x20;
        if (r.al == (byte) '<') {
            if_match();
            return;
        }
        r.dh = 0x30;
        if (r.al == (byte) '!') {
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
        mucom2.ifflag |= 1;
        mucom2.rednums(); // AL = condition value
        r.dl = r.al;
        r.ah = mucom2.nesting; // Nest value
        r.carry = ((r.ah & 0xff) < 1);
        r.ah = (byte) ((r.ah & 0xff) - 1);
        r.cl = 25; // IF instruction outside ()
        if (r.carry) {
            mucom2.error();
            return;
        }
        if_value();
    }

    private void if_value() {
        mucom2.chktxt(); // Get next character
        xsmall(); // Uppercase conversion
        r.cl = 32;
        if (r.al == (byte) 'J') {
            if_jump0();
            return;
        }
        if (r.al == (byte) 'C') {
            if_call0();
            return;
        }
        if (r.al == (byte) 'T') {
            if_then0();
            return;
        }
        if (r.al == (byte) 'E') {
            if_exit0();
            return;
        }
//error6:
        mucom2.error();
    }

    private void if_jump0() {
        mucom2.ifflag |= 1;
        r.al = (byte) 0xe4;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        r.al = r.dl; // Jump condition value
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        chgax("UMP");
        if_quit();
    }

    private void if_quit() {
        get_val(); // Label number
        if_quit1();
    }

    private void if_quit1() {
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
    }

    private void if_call0() {
        mucom2.ifflag |= 1;
        r.al = (byte) 0xe3;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        r.al = r.dl;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        chgax("ALL");
        if_quit();
    }

    private void if_then0() {
        mucom2.ifflag |= 1;
        r.al = (byte) 0x80; // AL = identifier (path 2 rewritten to e4)
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
        r.al = (byte) 0x81; // path 2 rewritten to d3
        if_sub();
        chgax("XIT");
        if_exit1();
    }

    private void if_exit1() {
        r.dh = mucom2.nesting;
        if (r.dh == 0) {
            // Error outside loop
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
            mucom2.error();
            return;
        }
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        r.al = r.dl;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
    }

    //
    // Channel conditional branch
    //
    private void if_channel() {
        r.dl = 0; // Match Flag
        while (true) {
            mucom2.rednums();
            r.dh = r.al;
            if (r.al == mucom2.ope_no) {
                r.dl = 1;
            }
            do {
                mucom2.chktxt();
                if (r.al == (byte) ',') {
                    // Specified multiple
                    break;
                }
                if (r.al != (byte) '-') {
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
        do {
            mucom2.chktxt(); // Search @@
            if (r.al != (byte) '@') {
                continue;
            }
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            if (r.al != (byte) '@') {
                continue; // Do not assemble until @@
            }
            break;
        } while (true);
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
    }

    private void ifch1() {
        return;
    }

    //
    // Loop exit processing at the end (@/)
    //
    private void if_abort() {
        r.al = (byte) 0x81;
        if_sub();
        r.push(r.getBx());
        r.al = mucom2.nesting;
        r.ah = 0;
        int val = (r.getAx() & 0xffff) - 1;
        val <<= 2;
        int bxIdx = (2 + val) / 2;
        mucom2.stttbl[bxIdx] = (short) (r.di - 1); // Store address to store counter loop value
        r.setBx(r.pop());
        if_exit1();
    }

    //
    // Grammar check for Jump,Call,Then,Exit
    //
    private void chgax(String str) {
        int siIdx = 0;
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.ah = muap98.sourceBuf[(r.getBx() & 0xffff) + 1];
        r.cl = 32;
        r.setBx((short) ((r.getBx() & 0xffff) + 2));
        r.dl = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        xsmall();
        if (r.ah >= (byte) 'a') r.ah -= (byte) ' ';
        if (r.dl >= (byte) 'a') r.dl -= (byte) ' ';

        if (r.al != (byte) str.charAt(siIdx) || r.ah != (byte) str.charAt(siIdx + 1)) {
            mucom2.error();
            return;
        }
        siIdx += 2;
        if (r.dl != (byte) str.charAt(siIdx)) {
            mucom2.error();
            return;
        }
    }

    //
    // Push to IF stack
    //
    private void pushif() {
        r.push(r.ds);
        r.push(r.getBx());
        int bxBase = (mucom2.jumpnes & 0xff) << 2;
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
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        r.di++; // Leave area for offset
    }

    private void call_to() {
        get_val2();
        r.ah = r.al;
        r.al = (byte) 0xe9;
        mucom2.stoswObjBufAX2DI();
        r.di++;
        r.al = mucom2.chglen;
        mucom2.setrat();
        mucom2.optimiz = 0;
    }

    private void ret_to() {
        r.al = (byte) 0xe8;
        mucom2.stosbObjBufAL2DI();
        mucom2.optimiz = 0;
    }

    private void label_to() {
        get_val2();
        r.push(r.getSi());
        int labelIdx = (r.al & 0xff) + ((r.ch & 0xff) - 1) * 40;
        labelAdrs[labelIdx] = (short) r.di; // Save label address
        int bxIdx = (r.al & 0xff) * 2;
        muap98.bufbuf[bxIdx] = (byte) r.di; // Store start address
        muap98.bufbuf[bxIdx + 1] = (byte) (r.di >> 8);
        r.setSi(r.pop());
        r.al = mucom2.chglen;
        mucom2.setrat();
        mucom2.optimiz = 0;
    }

    private void get_val2() {
        mucom2.rednums();
        r.zero = (r.al == 39);
        r.carry = ((r.al & 0xff) < 39);
        getv1();
    }

    private void getv1() {
        r.cl = 6;
        if (!r.zero && !r.carry) {
            mucom2.error();
        }
    }

    private void get_val() {
        mucom2.rednums();
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
        r.ah = (byte) 0x80;
        if (!r.carry) {
            r.ah = (byte) 0x82;
            if (r.zero) r.ah = 6;
        }
        pan_set();
    }

    private void pan_right() {
        check_panm();
        r.ah = (byte) 0x40;
        if (!r.carry) {
            r.ah = (byte) 0x41;
            if (r.zero) r.ah = 9;
        }
        pan_set();
    }

    private void pan_mono() {
        check_panm();
        r.ah = (byte) 0xc0;
        if (!r.carry) {
            r.ah = (byte) 0xc4;
            if (r.zero) r.ah = (byte) 0xcc;
        }
        pan_set();
    }

    private void pan_set() {
        int chVal = (r.ch & 0xff);
        if (chVal < 4) {
            set_pan();
            pan_init();
            return;
        }
        if (chVal < 7) {
            pan_init();
            return;
        }
        if (chVal == 10) {
            pan2();
            return;
        }
        set_pan();
        pan_init();
    }

    private void check_panm() {
        if (mucom2.sendch != 11) {
            r.zero = false;
            r.carry = true;
            return;
        }
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if (muap98.sourceBuf[(r.getBx() & 0xffff) - 2] >= (byte) 'a') {
            r.al -= (byte) ' ';
        }

        if (r.al == (byte) 'M') {
            r.carry = false;
            r.zero = (r.al == 0);
            return;
        }
        if (r.al == (byte) 'K') {
            r.al = 0;
            r.carry = false;
            r.zero = (r.al == 0);
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.zero = false;
        r.carry = true;
    }

    private void pan2() {
        chkcm();
        mucom2.chktxt();
        xsmall();
        r.dl = r.ah;
        setrt();
        if (r.al == 0) {
            // error21
        }
        r.ch = 0;
        int idx = (r.getCx() & 0xffff);
        r.dl |= mucom2.rhyvol[idx];
        r.cl += 0x18;
        r.al = r.dl;
        r.ah = r.cl;
        set_rhythmpan();
    }

    private void set_pan() {
        if ((mucom2.optimiz & 2) == 0 || r.ah != mucom2.opt_pan) {
            mucom2.opt_pan = r.ah;
            mucom2.optimiz |= 2;
            r.al = (byte) 0xf6;
            LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
            lp.chip = work.crntChip;
            lp.ch = (byte) work.crntChannel;
            lp.part = work.crntPart;
            MmlDatum md = new MmlDatum(r.al, MMLType.Pan, lp, new Object[] {(int) (r.ah & 0xff)});
            md = work.FlashLstMd(md);
            muap98.objectBuf.set(r.di++, md);
            muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        }
    }

    private void set_rhythmpan() {
        if ((mucom2.optimiz & 8) != 0) {
            if ((r.getAx() & 0xffff) == (mucom2.opt_rhy & 0xffff)) return;
        }
        mucom2.opt_rhy = (short) r.getEax();
        mucom2.optimiz |= 8;
        r.push(r.getAx());
        r.al = (byte) 0xee;
        mucom2.stosbObjBufAL2DI();
        r.setAx(r.pop());
        mucom2.stoswObjBufAX2DI();
    }

    public void pan_init() {
        r.setSi((short) 0);
        mucom2.panadrs = r.getSi();
        mucom2.pandata[0] = 0;
    }

    private void auto_pan() {
        int sendCh = (mucom2.sendch & 0xff);
        if (sendCh >= 4 && (sendCh < 7 || sendCh == 10)) return;

        mucom2.chktxt();
        if (r.al != (byte) '(') {
            r.cl = 32;
            mucom2.error();
            return;
        }
        pan_init();
        int siVal = (r.getSi() & 0xffff);
        do {
            mucom2.chktxt();
            xsmall();
            r.ah = (byte) 0x80;
            if (r.al != (byte) 'L') {
                r.ah = (byte) 0xc0;
                if (r.al != (byte) 'M') {
                    r.ah = (byte) 0x40;
                    if (r.al != (byte) 'R') {
                        if (r.al == (byte) ')') {
                            mucom2.pandata[siVal] = 0;
                            return;
                        }
                        r.cl = 32;
                        mucom2.error();
                        return;
                    }
                }
            }
            mucom2.pandata[siVal++] = r.ah;
        } while (siVal < 16);

        r.cl = 37;
        mucom2.error();
    }

    public void pan_check() {
        r.push(r.getSi());
        r.push(r.getAx());
        int siVal = (mucom2.panadrs & 0xffff);
        r.al = mucom2.pandata[siVal];
        if (r.al == 0) {
            siVal = 0;
            mucom2.panadrs = (short) siVal;
        }
        r.al = mucom2.pandata[siVal];
        if (r.al != 0) {
            siVal++;
            mucom2.panadrs = (short) siVal;
            r.ah = r.al;
            set_pan();
        }
        r.setAx(r.pop());
        r.setSi(r.pop());
    }

    private void mcomment() {
        mucom2.chkval();

        if (!r.carry) {
            mucom2.chktxt();
            r.zero = (r.al == (byte) '=');
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

        chknum();
        if (r.carry) {
            r.ah = (byte) 0xff;
            comment5();
            return;
        }
        mucom2.rednums();
        r.dl = r.al;
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.ah = (byte) (mucom2.nesting - 1);
        comment5();
    }

    private void comment5() {
        r.cl = 32;
        if (r.al != 0x22) mucom2.error();
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if ((mucom2.commode & 1) == 0) {
            comment_set();
            return;
        }
        r.push(r.di);
        comment_set();
        r.di = r.pop();
    }

    private void comment_set() {
        if ((mucom2.commode & 2) != 0) {
            mucom2.comcnt = 0;
            r.push(r.getAx());
            r.setAx((short) 0xdd);
            muap98.objectBuf.set(r.di, new MmlDatum(r.al));
            muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
            r.di += 2;
            r.setAx(r.pop());
        }

        r.al = (byte) 0xdb;
        muap98.objectBuf.set(r.di, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di + 1, new MmlDatum(r.ah));
        r.di += 2;

        r.al = r.dl;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        r.setDx(r.di); // Offset for string length
        r.di++;

        r.ah = 0;
        r.cl = 33;

        if (work.compilerInfo == null) work.compilerInfo = new CompilerInfo();
        if (work.compilerInfo.additionalInfo == null) work.compilerInfo.additionalInfo = new GD3Tag();
        GD3Tag tag = (GD3Tag) work.compilerInfo.additionalInfo;
        if (!tag.items.containsKey(Tag.Lyric)) tag.items.put(Tag.Lyric, new String[] {"MUS:UseLyric"});

        do {
            r.al = (byte) ((r.getBx() & 0xffff) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[r.getBx() & 0xffff]);
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) < (byte) ' ') mucom2.error();
            if (r.al == 0x22) {
                r.push(r.di);
                r.di = r.getDx();
                r.al = r.ah;
                muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
                r.di = r.pop();
                return;
            }
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
            r.ah++;
        } while ((r.ah & 0xff) != 73);

        mucom2.error();
    }

    private void comment_mode() {
        mucom2.commode |= 1;
    }

    private void comstepin() {
        mucom2.commode |= 2;
    }

    private void comstepout() {
        mucom2.commode &= 0xfd;
        stepcut2();
    }

    private void comstepcut() {
        mucom2.chktxt();
        chknum();
        if (r.carry) {
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            stepcut2();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        mucom2.rednums();
        r.al += mucom2.comcnt;
        mucom2.comcnt = r.al;
        r.ah = r.al;
        r.al = (byte) 0xdd;
        mucom2.stoswObjBufAX2DI();
    }

    private void stepcut2() {
        r.setAx((short) 0xffdd);
        mucom2.stoswObjBufAX2DI();
    }

    private void set_flat() {
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        xsmall();
        if (r.al == (byte) 'I') {
            flat_init();
            return;
        }

        flat_param();
        r.push(r.getBx());
        int flatIdx = (r.dl & 0xff);
        mucom2.flatdata[flatIdx] = r.dh;
        r.setBx(r.pop());
    }

    private void flat_init() {
        r.push(r.getBx());
        for (int i = 0; i < 7; i++) mucom2.flatdata[i] = 0;
        r.setBx(r.pop());
    }

    private void setf5() {
        r.dh++;
        setf4();
    }

    private void setf4() {
        r.dh++;
        setf2();
    }

    private void setf2() {
        r.dh++;
        setf1();
    }

    private void setf1() {
        r.dh++;
        setf3();
    }

    private void setf3() {
        if ((mucom2.mode[0] & 4) != 0) {
            oct_exe();
            return;
        }
        r.push(r.getBx());
        r.push((short) (mucom2.octdata & 0xff));
        mucom2.chktxt();
        if (r.al != (byte) '{') r.setBx((short) ((r.getBx() & 0xffff) - 1));
        skipoct();
        r.al -= (byte) 'A';
        r.dl = r.al;
        calc_fpara();
        mucom2.octdata = (byte) (r.pop() & 0xff);
        r.setBx(r.pop());
    }

    private void oct_exe() {
        skipoct();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        r.push(r.getBx());
        r.al -= (byte) 'A';
        r.dl = r.al;
        calc_fpara();
        r.setBx(r.pop());
        mucom2.mode[0] &= 0xfb;
    }

    public void skipoct() {
        do {
            mucom2.chktxt();
            if (r.al == (byte) '<') {
                mucom2.octdown();
                continue;
            }
            if (r.al == (byte) '>') {
                mucom2.octup();
                continue;
            }
            break;
        } while (true);
        xsmall();
    }

    private void calc_fpara() {
        r.dh |= 0x80;
        int bxBase = (mucom2.octdata & 0xff) * 7;
        int alVal = (r.dl & 0xff);
        mucom2.flatdata2[bxBase + alVal] = r.dh;
    }

    private void flat_param() {
        r.al -= (byte) 'A';
        r.dl = r.al;
        r.cl = 28;
        if ((r.al & 0xff) >= 7) mucom2.error();

        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        r.dh = (byte) 0xff;
        if (r.al == (byte) '-') return;
        r.dh = 1;
        if (r.al == (byte) '+' || r.al == (byte) '#') return;
        r.dh = 0;
        if (r.al == (byte) '%') return;
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
    }

    private void flat_clear() {
        Arrays.fill(mucom2.flatdata2, (byte) 0);
    }

    private void cresc() {
        r.ah = (byte) '<';
        cres_ent();
        mucom2.creslen = (short) (r.getAx() & 0xffff);
        mucom2.crescnt = 0;
        mucom2.dcrelen = 0;
    }

    private void decresc() {
        r.ah = (byte) '>';
        cres_ent();
        mucom2.dcrelen = (short) (r.getAx() & 0xffff);
        mucom2.crescnt = 0;
        mucom2.creslen = 0;
    }

    private void cres_ent() {
        r.al = mucom2.volsave;
        r.cl = 31;
        if ((r.al & 0xff) > 127) mucom2.error();
        mucom2.volstt = r.al;
        r.dl = 1;
        while (true) {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            if (r.al != r.ah) break;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.dl++;
        }
        r.dh = r.dl;
        r.dl += r.dl;
        r.dl += r.dh;
        mucom2.cresvol = (short) (r.dl & 0xff);
        r.cl = 14;
        chknum();
        if (r.carry) mucom2.error();
        mucom2.tnelnm();
    }

    public void cres_check() {
        rit_check();
        r.push(r.getCx());
        r.setCx((short) mucom2.creslen);
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
            r.setAx(r.pop());
            r.setCx(r.pop());
            return;
        }
        r.al = mucom2.volstt;
        int val = (r.al & 0xff) + (r.cl & 0xff);
        if (val > 127) val = 127;
        r.al = (byte) val;
        r.zero = (r.al == mucom2.volsave);
        r.ah = r.al;
        if (!r.zero) mucom2.retvol3();
        r.setAx(r.pop());
        r.setCx(r.pop());
    }

    private void exe_decre() {
        r.push(r.getAx());
        cres_main();
        if (r.carry) {
            r.setAx(r.pop());
            r.setCx(r.pop());
            return;
        }
        int val = (mucom2.volstt & 0xff) - (r.cl & 0xff);
        if (val < 0) val = 0;
        r.al = (byte) val;
        r.zero = (r.al == mucom2.volsave);
        r.ah = r.al;
        if (!r.zero) mucom2.retvol3();
        r.setAx(r.pop());
        r.setCx(r.pop());
    }

    private void cres_main() {
        r.push(r.getDx());
        r.ah = 0;
        int axVal = (mucom2.chglen & 0xff) + (mucom2.crescnt & 0xffff);
        mucom2.crescnt = (short) axVal;
        if (axVal >= (r.getCx() & 0xffff)) {
            init_cres();
            r.carry = true;
            r.setDx(r.pop());
            return;
        }
        r.mul((byte) mucom2.cresvol);
        r.div((byte) r.getCx());
        int finalAx = (r.getAx() & 0xffff) + 1;
        r.setCx((short) finalAx);
        r.setDx(r.pop());
        r.carry = false;
    }

    public void init_cres() {
        mucom2.creslen = 0;
        mucom2.dcrelen = 0;
    }

    private void accel() {
        mucom2.rednum();
        accel1();
    }

    private void accel1() {
        mucom2.tmpdata = (short) r.getAx();
        chkcm();
        mucom2.tnelnm();
        mucom2.tmplen = (short) r.getAx();
        mucom2.tmpstt = (short) mucom2.tempos;
        mucom2.tmpcnt = 0;
    }

    private void ritard() {
        mucom2.rednum();
        r.setAx((short) -(r.getAx() & 0xffff));
        accel1();
    }

    private void rit_check() {
        r.push(r.getCx());
        r.setCx((short) mucom2.tmplen);
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
            int currentT = (mucom2.tmpstt & 0xffff) + (r.getCx() & 0xffff);
            if (currentT < 16) currentT = 16;
            if (currentT > 3907) currentT = 3907;
            r.push((short) currentT);
            r.setAx((short) currentT);
            mucom2.calct();
            r.setCx(r.getAx());
            r.setAx((short) mucom2.tempos);
            mucom2.calct();
            r.zero = (r.getAx() == r.getCx());
            r.setAx(r.pop());
            if (!r.zero) mucom2.calc_tempo();
        }
        r.setAx(r.pop());
        r.setCx(r.pop());
    }

    private void rit_main() {
        r.push(r.getDx());
        int axVal = (mucom2.chglen & 0xff) + (mucom2.tmpcnt & 0xffff);
        mucom2.tmpcnt = (short) axVal;
        if (axVal >= (r.getCx() & 0xffff)) {
            mucom2.tmplen = 0;
            r.carry = true;
            r.setDx(r.pop());
            return;
        }
        int ans = (short) axVal * (short) mucom2.tmpdata;
        int quo = ans / (short) r.getCx();
        r.setCx((short) quo);
        r.setDx(r.pop());
        r.carry = false;
    }

    private void lfo_set() {
        mucom2.chktxt();
        xsmall();
        r.cl = 32;
        if (r.al == (byte) 'P') lfo_pmd();
        else if (r.al == (byte) 'A') lfo_amd();
        else if (r.al == (byte) 'S') lfo_stop();
        else if (r.al == (byte) 'R') lfo_reset();
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
        if ((r.al & 0xff) > 3) mucom2.error();
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
        if ((r.al & 0xff) > 127) mucom2.error();
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
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
            if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) ',') {
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) ',') {
                    mucom2.rednums();
                    if ((r.al & 0xff) > 15) {
                        r.cl = 6;
                        mucom2.error();
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
                        }
                        r.dh = r.al;
                    }
                }
            }
        } else {
            r.al = 0;
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        }

        r.carry = cf;
        r.al = r.dl;
        if (r.carry) r.dh++;
        r.al |= (byte) ((r.dh & 0xff) << 4);
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) ',') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if (muap98.sourceBuf[r.getBx() & 0xffff] != (byte) ',') {
                mucom2.rednums();
                if ((r.al & 0xff) > 3) {
                    r.cl = 6;
                    mucom2.error();
                }
                r.cl = r.al;
            }
            if (muap98.sourceBuf[r.getBx() & 0xffff] == (byte) ',') {
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                mucom2.rednums();
                if ((r.al & 0xff) > 63) {
                    r.cl = 6;
                    mucom2.error();
                }
                r.cl |= (byte) ((r.al & 0xff) << 2);
            }
        }
        r.al = r.cl;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
    }

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
        if (r.carry) mucom2.error();
        r.al = (byte) (((r.al & 0xff) - 1) | 8);
        r.ah = r.al;
        r.al = (byte) 0xef;
        mucom2.stoswObjBufAX2DI();
    }

    private void hlfo_data() {
        mucom2.rednums();
        if ((r.al & 0xff) > 7) {
            r.cl = 6;
            mucom2.error();
        }
        r.dh = r.al;
        chkcm();
        mucom2.rednums();
        if ((r.al & 0xff) > 3) {
            r.cl = 6;
            mucom2.error();
        }
        r.dl = r.al;
        chkcm();
        mucom2.rednums();
        if ((r.al & 0xff) > 15) {
            r.cl = 6;
            mucom2.error();
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

    private void slur_in() {
        r.cl = 12;
        if (mucom2.slurmod != 0) mucom2.error();
        r.al = (byte) (mucom2.ratdata | 0x80);
        mucom2.slurmod = r.al;
        r.al = 0;
        mucom2.chgrat();
    }

    private void slur_out() {
        r.cl = 12;
        if (mucom2.slurmod == 0) mucom2.error();
        r.al = (byte) (mucom2.slurmod & 0x7f);
        mucom2.chgrat();
        mucom2.slurmod = 0;
    }

    private void slide_set() {
        mucom2.chktxt();
        if (r.al == (byte) '-') {
            mucom2.rednums();
            r.al = (byte) -r.al;
        } else {
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            mucom2.rednums();
        }
        mucom2.slbase = r.al;
        chkcm();
        mucom2.tnelnmx();
        mucom2.slspeed = r.al;
    }

    private void slide() {
        r.ch = mucom2.sendch;
        r.al = 1;
        mucom2.setrat();
        r.push(r.di);
        if (r.ch == 11) r.di++;
        r.di += 16;
        porta_sub();
        mucom2.porta2 = (short) r.getAx();
        if (r.ch == 11) {
            byte tmp = r.al;
            r.al = r.ah;
            r.ah = tmp;
        }
        mucom2.por_end = (short) r.di;
        r.di = r.pop();
        r.setDx(r.getAx());
        r.ah = mucom2.slbase;
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

        mucom2.porta1 = (short) r.getAx();
        byte tmp = r.al;
        r.al = r.ah;
        r.ah = tmp;
        mucom2.stoswObjBufAX2DI();
        r.al = mucom2.slspeed;
        mucom2.porcnt = (short) (r.al & 0xff);
        porta_main();
        r.di = (short) r.getAx();
        mucom2.tnelnmx();
        mucom2.add_tlen(r.al & 0xff);
        r.al++;
        r.cl = 15;
        r.carry = ((r.al & 0xff) < (mucom2.slspeed & 0xff));
        r.al = (byte) ((r.al & 0xff) - (mucom2.slspeed & 0xff));
        if (r.carry) mucom2.error();
        mucom2.setrat();
        r.di = (short) mucom2.por_end;
    }

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
        chknum();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        if (r.carry) {
            r.al = 1;
        } else {
            mucom2.tnelnmx();
            r.setAx((short) ((r.getAx() & 0xffff) + 1));
        }

        r.push(r.getAx());
        r.push((short) (mucom2.ratdata & 0xff));
        mucom2.ratdata = 0;
        mucom2.setrat();
        mucom2.ratdata = (byte) (r.pop() & 0xff);
        porta_sub();
        work.md = null;
        mucom2.porta1 = (short) r.getAx();
        r.setDx(r.pop());

        r.push(r.getDx());
        r.push(r.di);
        if (r.dl != 1) r.di += 3;
        r.di += 11;
        porta_sub();
        mucom2.porta2 = (short) r.getAx();
        mucom2.tnelnmx();
        mucom2.add_tlen(r.al & 0xff);
        mucom2.por_end = (short) r.di;
        r.di = r.pop();
        r.setDx(r.pop());

        r.dl = (byte) ((r.getDx() & 0xff) - 1);
        if (r.dl != 0) {
            r.push(r.getAx());
            r.al = 1;
            mucom2.setrat();
            r.setAx(r.pop());
        }

        boolean flg = ((r.al & 0xff) <= (r.dl & 0xff));
        r.al = (byte) ((r.al & 0xff) - (r.dl & 0xff));
        if (flg) {
            r.cl = 18;
            mucom2.error();
        }
        mucom2.porcnt = (short) (r.al & 0xff);
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

    private void por_2203() {
        pre_por();
        r.setAx((short) mucom2.porta1);
        calc_exp1();
        mucom2.freqsv1 = (short) r.getAx();
        mucom2.freqsv2 = (short) r.getDx();
        r.setAx((short) mucom2.porta2);
        calc_exp1();
        r.carry = (r.getAx() & 0xffff) < (mucom2.freqsv1 & 0xffff);
        r.setAx((short) ((r.getAx() & 0xffff) - (mucom2.freqsv1 & 0xffff)));
        int dxVal = (r.getDx() & 0xffff) - ((mucom2.freqsv2 & 0xffff) + (r.carry ? 1 : 0));
        r.setDx((short) dxVal);
        if ((dxVal & 0x8000) != 0) {
            r.setAx((short) -(r.getAx() & 0xffff));
            r.setDx((short) ~(r.getDx() & 0xffff));
            div64();
            r.setDx((short) ~(r.getDx() & 0xffff));
            r.setAx((short) -(r.getAx() & 0xffff));
        } else {
            div64();
        }
        after_por();
    }

    private void por_ssg() {
        pre_por();
        r.setAx((short) mucom2.porta2);
        r.carry = (r.getAx() & 0xffff) < (mucom2.porta1 & 0xffff);
        r.setAx((short) ((r.getAx() & 0xffff) - (mucom2.porta1 & 0xffff)));
        if (r.carry) {
            r.setAx((short) -(r.getAx() & 0xffff));
            div32();
            r.setDx((short) ~(r.getDx() & 0xffff));
            r.setAx((short) -(r.getAx() & 0xffff));
        } else {
            div32();
        }
        after_por();
    }

    private void por_pcm() {
        pre_por();
        r.setCx((short) 1);
        r.setDx((short) mucom2.porta1);
        byte tmp = r.dl;
        r.dl = r.dh;
        r.dh = tmp;
        r.setAx((short) mucom2.porta2);
        tmp = r.al;
        r.al = r.ah;
        r.ah = tmp;
        r.carry = (r.getAx() & 0xffff) < (r.getDx() & 0xffff);
        r.setAx((short) ((r.getAx() & 0xffff) - (r.getDx() & 0xffff)));
        if (r.carry) {
            r.setAx((short) -(r.getAx() & 0xffff));
            div32();
            r.setDx((short) ~(r.getDx() & 0xffff));
            r.setAx((short) -(r.getAx() & 0xffff));
        } else {
            div32();
        }
        after_por();
    }

    private void calc_exp1() {
        r.push(r.getCx());
        int cxVal = r.getAx() & 0xffff;
        r.setAx((short) (cxVal & 0x7ff));
        cxVal >>= 11;
        r.setCx((short) cxVal);
        r.setDx((short) 0);
        if (cxVal == 0) {
            r.setCx(r.pop());
            return;
        }
        do {
            r.carry = (r.getAx() & 0x8000) != 0;
            r.setAx((short) ((r.getAx() & 0xffff) << 1));
            r.setDx(r.rcl(r.getDx(), 1));
            cxVal--;
            r.setCx((short) cxVal);
        } while (cxVal != 0);
        r.setCx(r.pop());
    }

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
        r.div((byte) porCntVal);
        r.setCx(r.getAx());
        r.setAx(r.pop());
        r.ah = r.al;
        r.al = 0;
        r.div((byte) porCntVal);
        r.setDx(r.getCx());
    }

    private void pre_por() {
        r.setAx((short) 0xe0e1);
        mucom2.stoswObjBufAX2DI();
        r.al = (byte) 0xf8;
        mucom2.stosbObjBufAL2DI();
    }

    private void after_por() {
        after_por_main();
        r.setAx((short) r.di);
        r.di = (short) mucom2.por_end;
    }

    private void after_por_main() {
        mucom2.stoswObjBufAX2DI();
        r.al = r.dl;
        r.ah = (byte) 0xe1;
        mucom2.stoswObjBufAX2DI();
        r.setAx((short) 0x5f7);
        mucom2.stoswObjBufAX2DI();
        r.setAx((short) 0);
        r.ah = (byte) mucom2.porcnt;
        r.carry = ((r.ah & 0xff) <= 2);
        r.ah = (byte) ((r.ah & 0xff) - 2);
        if (r.carry) {
            r.cl = 18;
            mucom2.error();
        }
        mucom2.stoswObjBufAX2DI();
    }

    private void porta_sub() {
        while (true) {
            skipoct();
            if (r.al == (byte) '@') {
                mucom2.mode[0] |= 4;
                exp_cmd();
                continue;
            }
            break;
        }
        r.cl = 17;
        check_onpu();
        r.push(r.getAx());
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucom2.gethenon();
        r.setAx(r.pop());
        read();
        r.al = (byte) muap98.objectBuf.get(r.di - 2).dat;
        r.ah = (byte) muap98.objectBuf.get(r.di - 1).dat;
        byte tmp = r.al;
        r.al = r.ah;
        r.ah = tmp;
    }

    private void set_debug() {
        mucom2.tnelnmx();
        mucom2.debug = r.al;
    }

    private void clear_mode() {
        mucom2.mode[0] |= 0x10;
    }

    public void check_flatclear() {
        if ((mucom2.mode[0] & 0x10) == 0) return;
        if ((mucom2.mode[1] & 1) == 0) return;
        wait_r();
    }

    public void wait_r() {
        flat_clear();
        init_rhythm();
        if ((mucom2.mode[0] & 0x40) != 0) return;
        if (mucom2.debug != 0) {
            mucom2.tnelnx();
            r.al = (byte) 0xff;
        }
        r.al = (byte) 0xf3;
    }

    private void wait_mode() {
        mucom2.mode[0] |= 0x40;
    }

    private void fade_out() {
        r.al = (byte) 0xd1;
        mucom2.stosbObjBufAL2DI();
    }

    private void initia() {
        r.setSi((short) 0);
        r.setDx((short) 0);
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        chknum();
        if (!r.carry) {
            r.setSi((short) (r.getSi() - 1));
            r.setDx((short) (r.getDx() - 1));
            do {
                mucom2.rednums();
                r.cl = 6;
                r.al--;
                if ((r.al & 0xff) >= 17) {
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
            } while (r.al == (byte) ',');
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
        }
        r.al = (byte) 0xdc;
        mucom2.stosbObjBufAL2DI();
        r.setAx(r.getSi());
        mucom2.stoswObjBufAX2DI();
        r.al = r.dl;
        mucom2.stosbObjBufAL2DI();
    }

    private void rhythm_pan() {
        mucom2.chktxt();
        xsmall();
        setrt();
        if (r.al == 0) {
            r.cl = 32;
            mucom2.error();
            return;
        }
        int cxVal = (r.getCx() & 0xffff);
        r.dh = (byte) ((r.cl & 0xff) + 0x18);
        chkcm();
        mucom2.chktxt();
        xsmall();
        r.dl = (byte) 0x80;
        if (r.al != (byte) 'L') {
            r.dl = (byte) 0x40;
            if (r.al != (byte) 'R') {
                r.dl = (byte) 0xc0;
                if (r.al != (byte) 'M') {
                    r.cl = 32;
                    mucom2.error();
                    return;
                }
            }
        }
        chkcm();
        mucom2.rednums();
        r.carry = ((r.al & 0xff) > 31);
        r.cl = 6;
        if (r.carry) mucom2.error();
        mucom2.rhyvol[cxVal] = r.al;
        r.al |= r.dl;
        r.ah = r.dh;
        set_rhythmpan();
    }

    public void rhyexp() {
        mucom2.set_symbol2();
        mucom2.tnelnmx();
        mucom2.rtm_max = r.al;
        rhyexp_code();
    }

    public void rhyexp_code() {
        r.push(r.getAx());
        if (mucom2.rhydata != 0) {
            set_dump();
            return;
        }
        r.setAx(r.pop());
        while (true) {
            r.dl = mucom2.rtm_max;
            getrp_table();
            if ((r.dl & 0xff) >= (r.al & 0xff)) {
                reend5();
                return;
            }
            r.dl = (byte) ((r.dl & 0xff) - (r.al & 0xff));
            mucom2.rtm_max = r.dl;
            reend4();
        }
    }

    private void reend5() {
        r.al = r.dl;
        reend4();
    }

    private void reend4() {
        mucom2.tnelnx();
        if ((r.cl & 0xff) == 255) {
            r.al = (byte) 0xf9;
            mucom2.stosbObjBufAL2DI();
            mucom2.onpucnt++;
            return;
        }
        mucom2.dionpu = r.di;
        if ((r.getCx() & 0xffff) == 0) {
            r.al = (byte) 0xff;
            mucom2.stosbObjBufAL2DI();
            mucom2.onpucnt++;
            return;
        }
        if (r.ch != 0) {
            r.ah = r.ch;
            r.al = (byte) 0xef;
            mucom2.stoswObjBufAX2DI();
        }
        if (r.cl != 0) {
            r.ah = r.cl;
            r.al = (byte) 0xf0;
            mucom2.stoswObjBufAX2DI();
        }
        r.al = (byte) 0xf9;
        mucom2.stosbObjBufAL2DI();
        mucom2.onpucnt++;
    }

    private void set_dump() {
        r.dl = mucom2.rhydata;
        r.setAx(r.pop());
        mucom2.tnelnx();
        mucom2.dionpu = r.di;
        r.ah = r.dl;
        r.al = (byte) 0xf0;
        if (work.md == null) {
            mucom2.stoswObjBufAX2DI();
        } else {
            work.md.args.set(work.mdArgsStep + 0, (int) (r.ah & 0xff));
            work.md.args.set(work.mdArgsStep + 1, work.otoLength);
            mucom2.stoswObjBufAX2DI(work.md);
        }
        r.al = (byte) 0xf9;
        mucom2.stosbObjBufAL2DI();
        mucom2.onpucnt++;
    }

    private void getrp_table() {
        getrp_main();
        if ((r.cl & 0xff) != 255) return;
        init_rhythm();
        getrp_main();
    }

    private void getrp_main() {
        r.push(r.getSi());
        int siVal = (mucom2.rhyadrs & 0xffff);
        r.cl = mucom2.rhythmdta[siVal];
        r.ch = mucom2.rhythmdta[siVal + 1];
        r.al = mucom2.rhythmdta[siVal + 2];
        if ((r.cl & 0xff) != 255) {
            mucom2.rhyadrs = (short) (siVal + 3);
        }
        r.setSi(r.pop());
    }

    private void rhythm_pat() {
        if ((r.ch & 0xff) != 10) return;
        mucom2.rhydata = 0;
        mucom2.chktxt();
        r.zero = (r.al == (byte) '(');
        r.cl = 32;
        if (!r.zero) mucom2.error();
        int siVal = 0;
        int dlVal = 0;
        do {
            r.setAx((short) 0);
            mucom2.rhythmdta[siVal] = r.al;
            mucom2.rhythmdta[siVal + 1] = r.ah;
            while (true) {
                mucom2.chktxt();
                xsmall();
                if (r.al == (byte) '*') {
                    int last = (mucom2.lastrp & 0xffff);
                    mucom2.rhythmdta[siVal] = (byte) last;
                    mucom2.rhythmdta[siVal + 1] = (byte) (last >> 8);
                    continue;
                }
                if (r.al == (byte) '-') break;
                setrt();
                if (r.al == 0) {
                    r.setBx((short) ((r.getBx() & 0xffff) - 1));
                    mucom2.tnelnmx();
                    mucom2.rhythmdta[siVal + 2] = r.al;
                    int finalVal = (mucom2.rhythmdta[siVal] & 0xff) | ((mucom2.rhythmdta[siVal + 1] & 0xff) << 8);
                    mucom2.lastrp = (short) finalVal;
                    siVal += 3;
                    dlVal++;
                    r.carry = (dlVal < 16);
                    r.cl = 8;
                    if (!r.carry) mucom2.error();
                    mucom2.chktxt();
                    if (r.al == (byte) ')') {
                        mucom2.rhythmdta[siVal] = (byte) 0xff;
                        init_rhythm();
                        return;
                    }
                    if (r.al == (byte) ',') break;
                    r.cl = 32;
                    mucom2.error();
                }
                mucom2.rhythmdta[siVal] |= r.ah;
            }
        } while (true);
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

    private void rhythm_set() {
        mucom2.chktxt();
        r.zero = (r.al == (byte) '(');
        r.cl = 32;
        if (!r.zero) mucom2.error();
        int siVal = 0;
        int dlVal = 0;
        do {
            mucom2.chktxt();
            if (r.al != (byte) '0') {
                xsmall();
                if (r.al == (byte) 'R') {
                    mucom2.rhythmdta[siVal++] = (byte) 0xff;
                    dlVal++;
                    mucom2.chktxt();
                }
                r.setBx((short) ((r.getBx() & 0xffff) - 1));
                mucom2.tnelnmx();
                mucom2.rhythmdta[siVal++] = r.al;
            }
            while (true) {
                mucom2.chktxt();
                if (r.al == (byte) '?') {
                    siVal--;
                    byte saved = mucom2.rhythmdta[siVal];
                    mucom2.rhythmdta[siVal++] = (byte) 0xfd;
                    mucom2.rhythmdta[siVal++] = saved;
                    continue;
                }
                if (r.al == (byte) 0x22) {
                    siVal--;
                    byte saved = mucom2.rhythmdta[siVal];
                    mucom2.rhythmdta[siVal++] = (byte) 0xfc;
                    mucom2.rhythmdta[siVal++] = saved;
                    continue;
                }
                if (r.al == (byte) '!') {
                    siVal--;
                    byte saved = mucom2.rhythmdta[siVal];
                    mucom2.rhythmdta[siVal++] = (byte) 0xfe;
                    mucom2.rhythmdta[siVal++] = saved;
                    continue;
                }
                if (r.al == (byte) ')') {
                    mucom2.rhythmdta[siVal] = 0;
                    return;
                }
                r.setBx((short) ((r.getBx() & 0xffff) - 1));
                chkcm();
                dlVal--;
                r.carry = (dlVal < 48);
                r.cl = 8;
                if (!r.carry) mucom2.error();
                break;
            }
        } while (true);
    }

    public void init_rhythm() {
        mucom2.rhyadrs = 0;
    }

    private void mendif() {
        r.cl = 35;
        r.al = (byte) (mucom2.jumpnes & 0xff);
        if (r.al == 0) mucom2.error();
        r.dl = 1;
        set_exit();
    }

    private void key_maskset() {
        r.setAx((short) 0x1ec);
        mucom2.stoswObjBufAX2DI();
    }

    private void key_maskreset() {
        mucom2.chktxt();
        chknum();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        if (r.carry) {
            r.setAx((short) 0xec);
            mucom2.stoswObjBufAX2DI();
            return;
        }
        mucom2.rednums();
        r.cl = 6;
        if (r.al == 0 || (r.al & 0xff) > 6) mucom2.error();
        r.al = (byte) ((r.al & 0xff) << 5);
        r.ah = r.al;
        r.al = (byte) 0xec;
        mucom2.stoswObjBufAX2DI();
    }

    public void nloop1() {
        r.push(r.getBx());
        mucom2.nesting++;
        r.al = mucom2.nesting;
        r.carry = ((r.al & 0xff) > 15);
        r.cl = 3;
        if (r.carry) mucom2.error();
        r.al--;
        r.push(r.getAx());
        r.al = (byte) 0xe0;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        r.setAx(r.pop());
        int bxIdx = (r.getAx() & 0xffff) << 2;
        mucom2.stttbl[bxIdx / 2] = (short) r.di;
        mucom2.stttbl[(bxIdx + 2) / 2] = 0;
        r.setBx(r.pop());
        r.al = mucom2.chglen;
        mucom2.setrat();
        mucom2.optimiz = 0;
        mucom2.init_looplen();
    }

    public void nloop2() {
        mucom2.rednums();
        r.zero = (r.al == 0);
        r.cl = 1;
        if (r.zero) mucom2.error();
        mucom2.set_looplen();
        r.push(r.getBx());
        r.push(r.getAx());
        r.al = mucom2.nesting;
        r.carry = ((r.al & 0xff) < 1);
        r.al--;
        mucom2.nesting = r.al;
        r.cl = 13;
        if (r.carry) mucom2.error();
        int bxIdx = ((r.getAx() & 0xff) << 2);
        r.setDx((short) (mucom2.stttbl[bxIdx / 2] - r.di));
        r.setDx((short) -(r.getDx() & 0xffff));
        r.al = (byte) 0xf7;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        r.setAx(r.getDx());
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        r.setAx(r.pop());
        r.setBx((short) mucom2.stttbl[(bxIdx + 2) / 2]);
        if (r.getBx() != 0) {
            muap98.objectBuf.get(r.getBx() & 0xffff).dat = r.al;
        }
        r.setBx(r.pop());
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        r.al = mucom2.nesting;
        r.setAx((short) ((r.getAx() & 0xffff) + 1));
        r.dh = r.al;
        r.cl = 35;
        r.al = (byte) (mucom2.jumpnes & 0xff);
        if (r.al == 0) return;
        r.dl = 2;
        set_exit();
    }

    private void set_exit() {
        r.push(r.ds);
        r.push(r.getBx());
        r.ah = 0;
        int val = (r.getAx() & 0xffff) - 1;
        int bxIdx = val << 2;
        r.setAx((short) ((mucom2.IFSTACKbuf[bxIdx] & 0xff) | ((mucom2.IFSTACKbuf[bxIdx + 1] & 0xff) << 8)));
        if (r.al == r.dl) {
            boolean flg = false;
            if (r.dl != 1) {
                if (r.ah != r.dh) flg = true;
            }
            if (!flg) {
                r.setAx((short) r.di);
                r.setSi((short) ((mucom2.IFSTACKbuf[bxIdx + 2] & 0xff) | ((mucom2.IFSTACKbuf[bxIdx + 3] & 0xff) << 8)));
                r.setBx(r.pop());
                r.ds = r.pop();
                int siVal = (r.getSi() & 0xffff);
                int axVal = (r.getAx() & 0xffff) - siVal + 1;
                muap98.objectBuf.get(siVal).dat = (byte) axVal;
                muap98.objectBuf.get(siVal + 1).dat = (byte) (axVal >> 8);
                mucom2.jumpnes--;
                r.al = mucom2.chglen;
                mucom2.setrat();
                mucom2.optimiz = 0;
                return;
            }
        }
        r.setBx(r.pop());
        r.ds = r.pop();
    }

    private boolean check_ssg() {
        int sendCh = (mucom2.sendch & 0xff);
        if (sendCh < 4) return false;
        if (sendCh < 7) return true;
        return false;
    }

    private boolean skip_ssg() {
        int sendCh = (mucom2.sendch & 0xff);
        if (sendCh < 4) return false;
        if (sendCh >= 7) return false;
        return true;
    }

    public void noise() {
        mucom2.rednums();
        if (!check_ssg()) return;
        r.carry = ((r.al & 0xff) < 32);
        r.cl = 6;
        if (!r.carry) mucom2.error();
        r.ah = r.al;
        r.al = (byte) 0xf6;
        mucom2.stoswObjBufAX2DI();
    }

    public void env_type() {
        mucom2.rednums();
        if (!check_ssg()) return;
        r.carry = ((r.al & 0xff) < 16);
        r.cl = 6;
        if (!r.carry) mucom2.error();
        r.ah = r.al;
        r.al = (byte) 0xed;
        mucom2.stoswObjBufAX2DI();
    }

    public void env_speed() {
        mucom2.rednum();
        if (!check_ssg()) return;
        muap98.objectBuf.set(r.di++, new MmlDatum((byte) 0xee));
        mucom2.stoswObjBufAX2DI();
    }

    public void envelope() {
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        xsmall();
        if (r.al == (byte) 'S') {
            sdecay();
            return;
        }
        if (r.al == (byte) 'M') {
            mixer();
            return;
        }
        if (r.al == (byte) 'A') {
            attack();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        mucom2.rednums();
        if (!check_ssg()) return;
        r.carry = ((r.al & 0xff) < 2);
        r.cl = 6;
        if (!r.carry) mucom2.error();
        r.ah = (byte) ((r.al & 0xff) << 4);
        r.al = (byte) 0xef;
        mucom2.stoswObjBufAX2DI();
    }

    private void mixer() {
        mucom2.rednums();
        if (!check_ssg()) return;
        r.cl = 6;
        if ((r.al & 0xff) >= 3) mucom2.error();
        r.dl = r.al;
        r.al &= 1;
        r.dl &= 2;
        r.dl <<= 1;
        r.dl <<= 1;
        r.al |= r.dl;
        r.al |= (byte) 0b11110110;

        r.cl = (byte) ((mucom2.sendch & 0xff) - 4);
        r.al = r.rol(r.al, (r.cl & 0xff));
        r.al &= (byte) 0b10111111;
        r.ah = r.al;
        r.al = (byte) 0xeb;
        mucom2.stoswObjBufAX2DI();
    }

    private void sdecay() {
        mucom2.rednums();
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

    private void attack() {
        mucom2.rednums();
        r.cl = 6;
        if ((r.al & 0xff) != 255) {
            if ((r.al & 0xff) > 127) mucom2.error();
        }
        r.ah = r.al;
        r.al = (byte) 0xd5;
        mucom2.stoswObjBufAX2DI();
        chkcm();
        mucom2.rednums();
        mucom2.stosbObjBufAL2DI();
        mucom2.chktxt();
        if (r.al == (byte) ',') {
            sdecay();
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
    }

    public void reg() {
        mucom2.rednums();
        r.ch = r.al;
        chkcm();
        mucom2.rednums();
        r.dl = r.al;
        set16();
    }

    public void reghex() {
        get2hex();
        r.ch = r.dl;
        chkcm();
        get2hex();
        set16();
    }

    private void set16() {
        r.al = (byte) 0xf1;
        mucom2.stosbObjBufAL2DI();
        r.al = r.dl;
        r.ah = r.ch;
        if (r.ah == 7) {
            r.al |= 0x80;
            r.al &= 0xbf;
        }
        mucom2.stoswObjBufAX2DI();
    }

    private void get2hex() {
        gethex();
        r.dl <<= 4;
        r.dh = r.dl;
        gethex();
        r.dl |= r.dh;
    }

    private void gethex() {
        r.dl = muap98.sourceBuf[r.getBx() & 0xffff];
        r.setBx((short) ((r.getBx() & 0xffff) + 1));
        if (r.dl >= (byte) 'a') r.dl -= (byte) ' ';
        r.dl -= (byte) '0';
        if (r.dl >= 10) r.dl -= 7;
    }

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
        get2hex();
        r.ah = r.dl;
        get2hex();
        r.al = r.dl;
        mucom2.stoswObjBufAX2DI();
    }

    public void usr_tone() {
        gettpara();
        mucom2.from_no = r.al;
        chkcm();
        gettpara();
        mucom2.to_no = r.al;
        r.push(r.getCx());
        r.push(r.es);
        copy_tone();
        r.setSi(r.getDx());

        chkcm2();
        if (r.carry) {
            usr_tone_cut();
            return;
        }

        mucom2.mode[0] &= 0xdf;
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        xsmall();
        if (r.al == (byte) 'I') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            chkcm();
            mucom2.mode[0] |= 0x20;
            r.bp = 0;
            r.setDx((short) 24);
            r.setCx((short) 0);
        } else if (r.al != (byte) 'E') {
            r.bp = 0;
            r.setDx((short) 24);
            r.setCx((short) 0);
        } else {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            chkcm();
            r.bp = 0;
            r.setDx((short) 24);
            r.setCx((short) 0x703);
            paramain();
            chkcm();
            r.setCx((short) 0x3800);
        }

        paramain();
        r.setCx((short) 4);

        do {
            chkcm2();
            if (r.carry) {
                usr_tone_cut();
                return;
            }
            r.push(r.getCx());

            r.setCx((short) 3);
            r.push(r.getSi());
            do {
                r.push(r.getCx());
                r.setDx((short) 0x1f08);
                r.setCx((short) 0xe000);
                paramain();
                chkcm();
                r.setSi((short) ((r.getSi() & 0xffff) + 4));
                r.setCx(r.pop());
                r.setCx((short) ((r.getCx() & 0xffff) - 1));
            } while (r.getCx() != 0);
            r.setSi(r.pop());

            r.setDx((short) 0xf14);
            r.setCx((short) 0xf000);
            paramain();
            chkcm();

            r.setDx((short) 0xf14);
            r.setCx((short) 0xf04);
            paramain();
            chkcm();

            r.setDx((short) 0x7f04);
            r.setCx((short) 0);
            paramain();
            chkcm();

            r.setDx((short) 8);
            r.setCx((short) 0x3f06);
            paramain();
            chkcm();

            r.setDx((short) 0);
            r.setCx((short) 0xf000);
            paramain();
            chkcm();

            mucom2.chktxt();
            if (r.al == (byte) '-') {
                getnum();
                r.al |= 4;
                r.al <<= 4;
                r.al |= 0xf;
                int siIdx = (r.getSi() & 0xffff) + (r.bp & 0xffff);
                muap98.toneBuff[siIdx] |= 0xf0;
                muap98.toneBuff[siIdx] &= r.al;
            } else {
                r.setBx((short) ((r.getBx() & 0xffff) - 1));
                getnum();
                if (!r.carry) {
                    r.al <<= 4;
                    r.al |= 0xf;
                    int siIdx = (r.getSi() & 0xffff) + (r.bp & 0xffff);
                    muap98.toneBuff[siIdx] |= 0xf0;
                    muap98.toneBuff[siIdx] &= r.al;
                }
            }
            r.setCx(r.pop());
            r.bp++;
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);

        chkcm3();
        usr_tone_cut();
    }

    private void usr_tone_cut() {
        r.setDx(r.es);
        r.es = r.pop();
        r.al = (byte) 0xe6;
        r.ah = mucom2.to_no;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        r.setCx((short) 25);
        r.push(r.ds);
        r.ds = r.getDx();
        int siVal = (r.getSi() & 0xffff);
        do {
            muap98.objectBuf.set(r.di++, new MmlDatum(muap98.toneBuff[siVal++]));
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);
        r.ds = r.pop();
        r.setCx(r.pop());
    }

    private void chkcm2() {
        r.carry = false;
        mucom2.chktxt();
        if (r.al == (byte) ':') {
            r.carry = true;
            return;
        }
        if (r.al != (byte) ',') {
            r.cl = 29;
            mucom2.error();
        }
    }

    private void chkcm3() {
        mucom2.chktxt();
        if (r.al == (byte) ',' || r.al == (byte) ':') return;
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
    }

    private void getnum() {
        mucom2.chktxt();
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        if (r.al == (byte) ',' || r.al == (byte) ':') {
            r.carry = true;
            return;
        }
        mucom2.rednums();
        r.carry = false;
    }

    private void gettpara() {
        mucom2.chktxt();
        if (r.al != (byte) '@') {
            r.cl = 29;
            mucom2.error();
        }
        mucom2.rednums();
    }

    private void paramain() {
        getnum();
        if (r.carry) return;
        r.push(r.getSi());
        r.push(r.getDx());
        r.push(r.getCx());

        if (r.dh != 0) {
            if ((mucom2.mode[0] & 0x20) != 0) {
                r.dh = (byte) ((r.dh & 0xff) - (r.al & 0xff));
                r.al = r.dh;
            }
        }

        r.dh = 0;
        r.setSi((short) ((r.getSi() & 0xffff) + (r.getDx() & 0xffff)));
        r.al = (byte) ((r.al & 0xff) << (r.cl & 0xff));
        r.al |= r.ch;
        r.ch = (byte) ~(r.ch & 0xff);
        int siIdx = (r.getSi() & 0xffff) + (r.bp & 0xffff);
        muap98.toneBuff[siIdx] |= r.ch;
        muap98.toneBuff[siIdx] &= r.al;
        r.setCx(r.pop());
        r.setDx(r.pop());
        r.setSi(r.pop());
    }

    private void copy_tone() {
        r.push(r.getAx());
        r.push(r.getBx());
        r.push(r.getCx());
        r.push(r.getSi());
        r.push(r.di);
        r.push(r.ds);
        r.al = mucom2.from_no;
        byte[] tbuf = mucom2.tone_adrs();
        int siVal = (r.getBx() & 0xffff);
        r.al = mucom2.to_no;
        mucom2.tone_adrs();
        int diVal = (r.getBx() & 0xffff);
        r.setDx((short) diVal);
        r.es = r.ds;
        r.setCx((short) 25);
        do {
            tbuf[diVal++] = tbuf[siVal++];
            r.setCx((short) ((r.getCx() & 0xffff) - 1));
        } while (r.getCx() != 0);
        r.ds = r.pop();
        r.di = r.pop();
        r.setSi(r.pop());
        r.setCx(r.pop());
        r.setBx(r.pop());
        r.setAx(r.pop());
    }

    public void macro_exec() {
        mucom2.mode[1] |= 2;
        mucom2.set_symbol2();
        mucom2.symbol2 |= 2;
        r.push(r.di);
        mucom2.macroflg = 0;
        mucom2.wordbuf[0] = r.al;
        r.dl = 1;
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        if (r.al == (byte) ',') {
            getword4();
            return;
        }
        chknum();
        if (!r.carry) {
            getword4();
            return;
        }
        getword2();
    }

    public void dtcall() {
        mucom2.mode[1] &= 0xfd;
        r.push(r.di);
        mucom2.macroflg = 0;
        int diVal = 0;
        r.dl = 0;
        do {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if (r.al == (byte) '$' || r.al == (byte) ' ' || (r.al & 0xff) == 9) {
                getword2();
                return;
            }
            if ((r.al & 0xff) == 0xfe) {
                getword9();
                return;
            }
            if (r.al == (byte) ',') {
                getword4();
                return;
            }
            r.cl = 21;
            r.dl++;
            if (r.dl == 33) break;
            mucom2.wordbuf[diVal++] = r.al;
        } while (true);
        mucom2.error();
    }

    private void getword4() {
        mucom2.macrov[18 / 2] = (short) (r.getBx() & 0xffff);
        r.push(r.getSi());
        r.push(r.getDx());
        r.setDx((short) 1);
        int siIdx = 0;
        while (true) {
            chkcall();
            r.setBx((short) ((r.getBx() & 0xffff) - 1));
            if (r.al != (byte) ',') {
                mucom2.macrov[(siIdx + 18) / 2] = (short) (r.getBx() & 0xffff);
                mucom2.rednum();
                mucom2.macroflg |= (r.getDx() & 0xffff);
                mucom2.macrov[siIdx / 2] = (short) (r.getAx() & 0xffff);
                skiplen();
            }
            siIdx += 2;
            int dxVal = (r.getDx() & 0xffff) << 1;
            r.setDx((short) dxVal);
            if (siIdx < 18) {
                chkcall();
                if (r.al == (byte) ',') continue;
                r.setBx((short) ((r.getBx() & 0xffff) - 1));
            }
            break;
        }
        r.setDx(r.pop());
        r.setSi(r.pop());
        getword8();
    }

    private void getword9() {
        check_flatclear();
        getword2();
    }

    private void getword2() {
        mucom2.macrov[18 / 2] = (short) (r.getBx() & 0xffff);
        getword8();
    }

    private void getword8() {
        r.di = r.pop();
        mucom2.cal_num = r.dl;
        skiplen();
        mucom2.nest2++;
        r.cl = 20;
        if ((mucom2.nest2 & 0xff) > 10) mucom2.error();

        r.push((short) mucom2.linedta);
        int oldCol = work.col;
        r.push(r.getBx());
        if ((mucom2.mode[1] & 2) != 0) {
            r.push(r.ds);
            r.push(r.getBx());
            calc_macache();
            int cacheVal = (mucom2.MACACHEbuf[r.getBx() & 0xffff] & 0xff) | ((mucom2.MACACHEbuf[(r.getBx() & 0xffff) + 1] & 0xff) << 8);
            r.setBx(r.pop());
            r.ds = r.pop();
            if (cacheVal != 0) {
                r.setBx((short) cacheVal);
                chk_dc5();
                return;
            }
        }

        mucom2.linedta = 1;
        work.row = 1;
        work.oldbx = 0;
        r.setBx((short) 0);
        while (true) {
            do {
                chkcall();
            } while (r.al != (byte) '$');
            r.push(r.di);
            int dlVal = (mucom2.cal_num & 0xff);
            int diVal = 0;
            boolean match = true;
            while (dlVal != 0) {
                r.al = muap98.sourceBuf[r.getBx() & 0xffff];
                r.setBx((short) ((r.getBx() & 0xffff) + 1));
                if ((r.al & 0xff) == 0xff || (r.getBx() & 0xffff) >= muap98.buflens) {
                    chk_err0();
                    return;
                }
                if (r.al != mucom2.wordbuf[diVal++]) {
                    match = false;
                    break;
                }
                dlVal--;
            }
            r.di = r.pop();
            if (match) {
                chkcall();
                if (r.al == (byte) '[' || r.al == (byte) '$') {
                    if (r.al == (byte) '$') {
                        chkcall();
                        if (r.al != (byte) '[') continue;
                    }
                    break;
                }
            }
        }

        if ((mucom2.mode[1] & 2) != 0) {
            r.push(r.ds);
            r.push(r.getBx());
            r.setDx(r.getBx());
            calc_macache();
            mucom2.MACACHEbuf[r.getBx() & 0xffff] = r.dl;
            mucom2.MACACHEbuf[(r.getBx() & 0xffff) + 1] = r.dh;
            r.setBx(r.pop());
            r.ds = r.pop();
        }

        chk_dc5();
    }

    private void chk_dc5() {
        while (true) {
            chkcall();
            if (r.al == (byte) ']') break;
            r.push(r.getCx());
            r.push((short) mucom2.macroflg);
            r.push((short) mucom2.macrov[0]);
            r.push((short) mucom2.macrov[1]);
            r.push((short) mucom2.macrov[2]);
            r.push((short) mucom2.macrov[9]);
            mucom2.com_main();
            mucom2.macrov[9] = r.pop();
            mucom2.macrov[2] = r.pop();
            mucom2.macrov[1] = r.pop();
            mucom2.macrov[0] = r.pop();
            mucom2.macroflg = r.pop();
            r.push(r.getAx());
            r.setAx((short) (muap98.bufleno - 0x10));
            r.carry = (r.di < (r.getAx() & 0xffff));
            r.cl = 2;
            r.setAx(r.pop());
            if (!r.carry) mucom2.error();
            r.setCx(r.pop());
        }
        r.setBx(r.pop());
        mucom2.linedta = r.pop();
        work.row = mucom2.linedta;
        mucom2.symbol2 &= 0xfd;
        mucom2.nest2--;
    }

    private void calc_macache() {
        r.setAx((short) 0);
        r.al = mucom2.wordbuf[0];
        xsmall();
        int val = (r.al & 0xff) - 0x41;
        r.setAx((short) (val << 1));
        r.setBx(r.getAx());
    }

    private void skiplen() {
        while (true) {
            chkcall();
            if (r.al == (byte) '.' || r.al == (byte) '^' || r.al == (byte) '=') continue;
            chknum();
            if (r.carry) break;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
    }

    private void chkcall() {
        while (true) {
            if ((r.getBx() & 0xffff) >= muap98.buflens) {
                chk_err0();
                return;
            }
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if ((r.al & 0xff) == 0xff) {
                chk_err0();
                return;
            }
            if (r.al == (byte) ' ' || (r.al & 0xff) == 9) continue;
            if (r.al != 0xfe) {
                if (r.al == (byte) ';') {
                    while (true) {
                        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
                        r.setBx((short) ((r.getBx() & 0xffff) + 1));
                        if ((r.al & 0xff) == 0xff) {
                            chk_err0();
                            return;
                        }
                        if ((r.al & 0xff) == 0xfe) break;
                    }
                } else return;
            }
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
        r.cl = 19;
        mucom2.error();
    }

    private void trspeed() {
        trspeed0();
        mucom2.trildef = r.al;
    }

    private void trspeed0() {
        mucom2.tnelnmx();
        r.zero = (r.al == 0);
        r.cl = 6;
        if (r.zero) mucom2.error();
    }

    private int trillsub() {
        mucom2.tridta1 = 0;
        mucom2.tridta2 = 0;
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        if (r.al == (byte) '%') {
            mucom2.tridta1 = 3;
            mucom2.tridta2 = 3;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        }
        if (r.al == (byte) '-') {
            mucom2.tridta1 = 2;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        }
        if (r.al == (byte) '+') {
            mucom2.tridta2 = 1;
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        }
        chknum();
        if (r.carry) r.al = mucom2.trildef;
        else trspeed0();
        mucom2.trillen = r.al;

        while (true) {
            skipoct();
            if (r.al == (byte) '@') {
                mucom2.mode[0] |= 4;
                exp_cmd();
                continue;
            }
            break;
        }
        r.cl = 17;
        check_onpu();
        mucom2.trionpu = r.al;
        save_oct();
        r.al = muap98.sourceBuf[r.getBx() & 0xffff];
        mucom2.gethenon();
        mucom2.tridta0 = r.dl;
        mucom2.tnelnmx();
        mucom2.add_tlen(r.al & 0xff);
        mucom2.totalen = r.al;
        if (mucom2.harmno != 0) {
            if (tr_harm_mode() != 0) return 1;
        }
        r.al = mucom2.trillen;
        mucom2.setrat();
        return 0;
    }

    public void check_onpu() {
        mucom2.set_symbol2(); // Store source address
        if ((r.al & 0xFF) < (byte) 'A') {
            // Check if musical note exists
            mucom2.error();
            return;
        }
        if ((r.al & 0xFF) > (byte) 'G') {
            mucom2.error();
            return;
        }
    }

    private int tr_harm_mode() {
        mucom2.harm_main();
        if (!r.carry) {
            mucom2.get_harm();
            mucom2.trionpu = r.al;
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            mucom2.gethenon();
            mucom2.tridta0 = r.dl;
            harm_onpu();
            return 0;
        }
        r.dl = mucom2.totalen;
        mucom2.kyufu();
        r.setAx(r.pop());
        r.setAx(r.pop());
        return 1;
    }

    private void save_oct() {
        r.push(r.getAx());
        r.al = mucom2.octdata;
        mucom2.octsave = r.al;
        r.al = mucom2.ratdata;
        mucom2.slursav = r.al;
        r.al = 0;
        mucom2.chgrat();
        r.setAx(r.pop());
    }

    private void load_oct() {
        r.push(r.getAx());
        r.al = mucom2.octsave;
        mucom2.octdata = r.al;
        r.al = mucom2.slursav;
        mucom2.chgrat();
        r.setAx(r.pop());
    }

    private void addonpu() {
        r.al++;
        if (r.al == (byte) 'C') mucom2.octdata++;
        if (r.al == (byte) 'H') r.al = (byte) 'A';
        read();
    }

    private void subonpu() {
        r.al--;
        if (r.al == (byte) 'B') mucom2.octdata--;
        if (r.al == (byte) '@') r.al = (byte) 'G';
        read();
    }

    private void calcrest() {
        r.push(r.getAx());
        r.al = mucom2.trillen;
        r.mul(r.dl);
        r.dl = r.al;
        r.al = mucom2.totalen;
        r.carry = ((r.al & 0xff) <= (r.dl & 0xff));
        r.al = (byte) ((r.al & 0xff) - (r.dl & 0xff));
        r.cl = 15;
        if (r.carry) mucom2.error();
        mucom2.setrat();
        r.setAx(r.pop());
    }

    private void sacf_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x85});
        r.dl = 1;
        calcrest();
        triexe(new byte[] {(byte) 0x82});
        load_oct();
    }

    private void sacs_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x8a});
        r.dl = 1;
        calcrest();
        triexe(new byte[] {(byte) 0x81});
        load_oct();
    }

    private void trn_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu;
        triexe(new byte[] {5, 2, (byte) 0x8a});
        r.dl = 3;
        calcrest();
        triexe(new byte[] {(byte) 0x81});
        load_oct();
    }

    private void xtrn_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu;
        triexe(new byte[] {0x0a, 0x01, (byte) 0x85});
        r.dl = 3;
        calcrest();
        triexe(new byte[] {(byte) 0x82});
        load_oct();
    }

    private void mtrn_ent() {
        if (trillsub() != 0) return;
        r.dl = 4;
        calcrest();
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x80});
        r.push(r.getAx());
        r.al = mucom2.trillen;
        mucom2.setrat();
        r.setAx(r.pop());
        triexe(new byte[] {0x05, 0x02, 0x0a, (byte) 0x81});
        load_oct();
    }

    private void xmtrn_ent() {
        if (trillsub() != 0) return;
        r.dl = 4;
        calcrest();
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x80});
        r.push(r.getAx());
        r.al = mucom2.trillen;
        mucom2.setrat();
        r.setAx(r.pop());
        triexe(new byte[] {0x0a, 0x01, 0x05, (byte) 0x82});
        load_oct();
    }

    private void mor_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu;
        triexe(new byte[] {0x00, (byte) 0x85});
        r.dl = 2;
        calcrest();
        triexe(new byte[] {(byte) 0x82});
        load_oct();
    }

    private void xmor_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu;
        triexe(new byte[] {0x00, (byte) 0x8a});
        r.dl = 2;
        calcrest();
        triexe(new byte[] {(byte) 0x81});
        load_oct();
    }

    private void cad_ent() {
        if (trillsub() != 0) return;
        calc_tri();
        if ((r.cl & 0xff) < 2) {
            r.cl = 15;
            mucom2.error();
        }
        r.cl--;
        set_tri1();
        triexe(new byte[] {(byte) 0x82});
        rest_tri();
    }

    private void xcad_ent() {
        if (trillsub() != 0) return;
        calc_tri();
        if ((r.cl & 0xff) < 3) {
            r.cl = 15;
            mucom2.error();
        }
        r.cl -= 2;
        set_tri1();
        triexe(new byte[] {0x02, 0x0a, (byte) 0x81});
        rest_tri();
    }

    private void idm_ent() {
        if (trillsub() != 0) return;
        calc_tri();
        if ((r.cl & 0xff) < 3) {
            r.cl = 15;
            mucom2.error();
        }
        r.cl -= 2;
        set_tri2();
        triexe(new byte[] {(byte) 0x82});
        rest_tri();
    }

    private void xidm_ent() {
        if (trillsub() != 0) return;
        calc_tri();
        if ((r.cl & 0xff) < 4) {
            r.cl = 15;
            mucom2.error();
        }
        r.cl -= 3;
        set_tri2();
        triexe(new byte[] {0x02, 0x0a, (byte) 0x81});
        rest_tri();
    }

    private void tri_ent() {
        if (trillsub() != 0) return;
        calc_tri();
        set_tri();
        rest_tri();
    }

    private void calc_tri() {
        int axVal = (mucom2.totalen & 0xff);
        r.cl = (byte) ((mucom2.trillen & 0xff) * 2);
        r.ah = 0;
        r.setAx((short) axVal);
        r.div(r.cl);
        r.setCx(r.getAx());
        if (r.cl == 0) {
            r.cl = 15;
            mucom2.error();
        }
        r.al = mucom2.trionpu;
    }

    private void set_tri2() {
        triexe(new byte[] {0x05, (byte) 0x82});
        set_tri1();
    }

    private void set_tri1() {
        triexe(new byte[] {0x0a, 0x01, (byte) 0x85});
        while (true) {
            r.cl--;
            if (r.cl == 0) return;
            triexe(new byte[] {0x02, (byte) 0x85});
        }
    }

    private void set_tri() {
        triexe(new byte[] {0x00, (byte) 0x85});
        while (true) {
            r.cl--;
            if (r.cl == 0) return;
            triexe(new byte[] {0x02, (byte) 0x85});
        }
    }

    private void rest_tri() {
        if (r.ch == 0) {
            load_oct();
            return;
        }
        r.al = r.ch;
        mucom2.setrat();
        kwait0();
        load_oct();
    }

    private void xmmtrn_ent() {
        if (trillsub() != 0) return;
        r.al = mucom2.trionpu;
        r.dl = mucom2.tridta0;
        read();
        triexe(new byte[] {(byte) 0x8a});
        r.dl = 6;
        calcrest();
        triexe(new byte[] {(byte) 0x81});
        r.push(r.getAx());
        r.al = mucom2.trillen;
        mucom2.setrat();
        r.setAx(r.pop());
        triexe(new byte[] {0x05, 0x02, 0x0a, (byte) 0x81});
        load_oct();
    }

    private void acc_ent() {
        if (trillsub() != 0) return;
        accsub();
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x8a});
        r.dl = 1;
        calcrest();
        triexe(new byte[] {(byte) 0x81});
        load_oct();
    }

    private void xacc_ent() {
        if (trillsub() != 0) return;
        accsub();
        r.al = mucom2.trionpu;
        triexe(new byte[] {(byte) 0x85});
        r.dl = 1;
        calcrest();
        triexe(new byte[] {(byte) 0x82});
        load_oct();
    }

    private void accsub() {
        int axVal = (mucom2.totalen & 0xff);
        r.dl = (byte) axVal;
        r.dh = 9;
        r.ah = 0;
        r.setAx((short) axVal);
        r.div(r.dh);
        r.al = r.dl;
        if (r.ah == 0) {
            r.dh = 3;
            r.ah = 0;
            r.setAx((short) (r.dl & 0xff));
            r.div(r.dh);
            r.al += r.al;
        } else {
            r.al >>= 1;
        }
        mucom2.trillen = r.al;
        mucom2.setrat();
        r.al = (byte) 0xdf;
        mucom2.stosbObjBufAL2DI();
    }

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

    private void triexe(byte[] dat) {
        int siVal = 0;
        r.push(r.getCx());
        do {
            r.cl = (byte) (dat[siVal] & 0x0c);
            r.dl = mucom2.tridta0;
            if (r.cl == 4) r.dl = mucom2.tridta1;
            if (r.cl == 8) r.dl = mucom2.tridta2;

            r.cl = (byte) (dat[siVal] & 3);
            if (r.cl == 0) read();
            r.cl--;
            if (r.cl == 0) addonpu();
            r.cl--;
            if (r.cl == 0) subonpu();
            siVal++;
        } while ((dat[siVal - 1] & 0x80) == 0);
        r.setCx(r.pop());
    }

    public void icho() {
        r.dl = 0;
        while (true) {
            r.al = muap98.sourceBuf[r.getBx() & 0xffff];
            r.ah = muap98.sourceBuf[(r.getBx() & 0xffff) + 1];
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            if (r.al == (byte) '<') {
                r.dl -= 12;
                continue;
            }
            if (r.al == (byte) '>') {
                r.dl += 12;
                continue;
            }
            break;
        }
        xsmall();
        r.al -= (byte) 'A';
        r.carry = ((r.al & 0xff) < 7);
        r.cl = 23;
        if (!r.carry) mucom2.error();
        r.push(r.getBx());
        r.push(r.getAx());
        int bxIdx = (r.getAx() & 0xff);
        r.dl += mucom2.ichodta[bxIdx];
        r.setAx(r.pop());
        r.setBx(r.pop());
        if (r.ah == (byte) '-') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.dl--;
        } else if (r.ah == (byte) '#' || r.ah == (byte) '+') {
            r.setBx((short) ((r.getBx() & 0xffff) + 1));
            r.dl++;
        }
        mucom2.ichosav = r.dl;
    }

    public void MakeDatum(MMLType type) {
        LinePos lp = new LinePos(null, work.sourceFileName, work.row, work.col, -1, "", "", 0, 0, -1);
        lp.chip = work.crntChip;
        lp.ch = (byte) work.crntChannel;
        lp.part = work.crntPart;
        work.md = new MmlDatum(type, new ArrayList<>(), lp, 0);
    }

    private void detune() {
        MakeDatum(MMLType.Detune);
        r.setSi((short) 0);
        get_detune();
        mucom2.dtdata[r.getSi() & 0xffff] = r.al;
        work.md.args.add("D");
        work.md.args.add((int) (byte) r.al);
        work.lstMd.add(work.copy(work.md, -1));
        r.setSi((short) ((r.getSi() & 0xffff) + 1));
        mucom2.chktxt();
        if (r.al == (byte) ',') {
            r.cl = 16;
            check_314();
            if (!r.zero) mucom2.error();
            r.dl = 2;
            do {
                get_detune();
                mucom2.dtdata[r.getSi() & 0xffff] = r.al;
                r.setSi((short) ((r.getSi() & 0xffff) + 1));
                chkcm();
                r.dl--;
            } while (r.dl != 0);
            get_detune();
            mucom2.dtdata[r.getSi() & 0xffff] = r.al;
            mucom2.dt2mode = 1;
            if (mucom2.codemod == 1) mucom2.codemod = 0;
            r.setAx((short) 0x40ed);
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
            muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
            return;
        }
        mucom2.dt2mode = 0;
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        check_314();
        if (r.zero) {
            r.setAx((short) 0x00ed);
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
            muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        }
    }

    private void get_detune() {
        mucom2.chktxt();
        if (r.al == (byte) '-') {
            mucom2.rednums();
            r.setAx((short) -(r.getAx() & 0xffff));
            return;
        }
        r.setBx((short) ((r.getBx() & 0xffff) - 1));
        mucom2.rednums();
    }

    private void sysdetune() {
        r.cl = 38;
        if ((r.ch & 0xff) == 10 || (r.ch & 0xff) == 11) mucom2.error();
        get_detune();
        r.ah = r.al;
        r.al = (byte) 0xf0;
        mucom2.stoswObjBufAX2DI();
    }

    public void check_314() {
        if ((r.ch & 0xff) == 3) {
            r.zero = true;
            return;
        }
        r.zero = ((r.ch & 0xff) == 14);
    }

    private void detune_shift() {
        r.setSi((short) 0);
        detune_sub();
        mucom2.chktxt();
        if (r.al == (byte) ',') {
            check_314();
            if (!r.zero) mucom2.error();
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

    private void detune_sub() {
        mucom2.rednums();
        r.cl = 6;
        if ((r.al & 0xff) > 31) mucom2.error();
        mucom2.dtshift[r.getSi() & 0xffff] = r.al;
        r.setSi((short) ((r.getSi() & 0xffff) + 1));
    }

    private void calcdt() {
        int p = (r.getSi() & 0xffff) + 4;
        r.cl = (p < 4) ? mucom2.dtdata[p] : mucom2.dtshift[p - 4];
        if ((r.cl & 0xff) >= 16) {
            r.cl = (byte) ((r.cl & 0xff) - 16);
            r.setAx((short) ((r.getAx() & 0xffff) << (r.cl & 0xff)));
            return;
        }
        r.setAx((short) ((short) r.getAx() >> (r.cl & 0xff)));
    }

    public void read() {
        cres_check();
        pan_check();
        read_main();
        r.push(r.getAx());
        r.push(r.getDx());
        check_tiemode();
        if (!r.zero) {
            r.setDx(r.pop());
            r.setAx(r.pop());
            return;
        }
        r.setDx((short) mucom2.lastfrq);
        r.zero = (r.getDx() == (short) ((muap98.objectBuf.get(r.di - 2).dat & 0xff) | ((muap98.objectBuf.get(r.di - 1).dat & 0xff) << 8)));
        if (!r.zero) {
            r.setDx(r.pop());
            r.setAx(r.pop());
            return;
        }
        r.setAx((short) (r.di - 2));
        r.setDx((short) 1);
        mucom2.move_obj(1);
        r.push(r.di);
        r.di = (short) (r.getAx() & 0xffff);
        r.al = (byte) 0xe1;
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        r.di = r.pop();
        r.setDx(r.pop());
        r.setAx(r.pop());
    }

    private void check_tiemode() {
        r.push(r.getAx());
        r.al = mucom2.mode[0];
        mucom2.mode[0] &= 0x7f;
        if (mucom2.sendch == 11) {
            r.setAx((short) 1);
            r.zero = false;
        } else {
            r.al ^= 0x80;
            r.test(r.al, (byte) 0x88);
        }
        r.setAx(r.pop());
    }

    private void read_main() {
        mucom2.dionpu = r.di;
        r.push(r.getSi());
        r.push(r.getDx());
        r.push(r.getCx());
        r.push(r.getBx());
        r.push(r.getAx());
        r.al -= (byte) 'A';
        r.ah = 0;
        int bxIdx = (r.getAx() & 0xff);
        r.cl = mucom2.musdata[bxIdx];
        if (r.dl == 3 || r.dl != 0) {
            natural();
            return;
        }
        int octIdx = (mucom2.octdata & 0xff) * 7;
        int p = bxIdx + octIdx + 7;
        r.al = (p < 7) ? mucom2.flatdata[p] : mucom2.flatdata2[p - 7];
        if (r.al == 0) {
            nature1();
            return;
        }
        r.al &= 7;
        if (r.al == 2) r.al = (byte) 0xff;
        else if (r.al == 3) r.al = 2;
        else if (r.al == 4) r.al = (byte) 0xfe;
        r.cl += r.al;
        natural();
    }

    public void read2() {
        r.push(r.getSi());
        r.push(r.getDx());
        r.push(r.getCx());
        r.push(r.getBx());
        r.push(r.getAx());
        natent();
    }

    private void nature1() {
        r.cl += mucom2.flatdata[(r.getAx() & 0xff)];
        natural();
    }

    private void natural() {
        r.al = r.cl;
        r.dl--;
        if (r.dl == 0) r.al++;
        r.dl--;
        if (r.dl == 0) r.al--;
        r.dl--;
        r.dl--;
        if (r.dl == 0) r.al += 2;
        r.dl--;
        if (r.dl == 0) r.al -= 2;
        natent();
    }

    private void natent() {
        r.push((short) ((mucom2.octdata & 0xff) | ((mucom2.octsave & 0xff) << 8)));
        r.al += mucom2.ichosav;
        while (true) {
            if ((r.al & 0xff) < 12) break;
            r.test(r.al, (byte) 0x80);
            if (r.zero) {
                r.al -= 12;
                mucom2.octdata++;
                continue;
            }
            r.al += 12;
            mucom2.octdata--;
        }
        r.carry = ((mucom2.octdata & 0xff) < 9);
        r.cl = 24;
        if (!r.carry) {
            mucom2.error();
            return;
        }
        r.ah = 0;
        work.ontei = r.getAx();
        work.oct = mucom2.octdata;
        r.setAx((short) ((r.getAx() & 0xffff) << 1));
        mucom2.onpucnt++;
        r.setSi((short) 0);
        r.ch = mucom2.sendch;
        int sendChVal = (r.ch & 0xff);
        if (sendChVal < 4) {
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

        int bxIdx = (r.getAx() & 0xffff);
        r.al = mucom2.octdata;
        short ans = r.pop();
        mucom2.octdata = (byte) (ans & 0xff);
        mucom2.octsave = (byte) (ans >> 8);
        r.setDx((short) ((mucom2.data3[bxIdx] & 0xff) | ((mucom2.data3[bxIdx + 1] & 0xff) << 8)));
        if ((r.al & 0xff) > 7) {
            r.cl = 36;
            mucom2.error();
            return;
        }
        if ((r.al & 0xff) != 7) {
            r.cl = (byte) (6 - (r.al & 0xff));
            r.setDx((short) ((r.getDx() & 0xffff) >> (r.cl & 0xff)));
        } else {
            r.carry = ((r.getDx() & 0xffff) << 1) > 0xffff;
            r.setDx((short) ((r.getDx() & 0xffff) << 1));
            if (r.carry) {
                r.cl = 36;
                mucom2.error();
                return;
            }
        }
        r.ah = mucom2.dtdata[0];
        mucom2.freq_lfo();
        calcdt();
        r.setDx((short) ((r.getDx() & 0xffff) + (r.getAx() & 0xffff)));
        r.al = (byte) 0xd5;
        if (work.md == null) muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        else {
            work.md.dat = r.al;
            work.md.args.set(work.mdArgsStep + 0, (int) (work.ontei + work.oct * 12));
            work.md.args.set(work.mdArgsStep + 1, work.otoLength);
            work.md.linePos.chip = work.crntChip;
            work.md.linePos.ch = (byte) work.crntChannel;
            work.md.linePos.part = work.crntPart;
            muap98.objectBuf.set(r.di++, work.md);
        }
        r.setAx(r.getDx());
        muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        read_exit();
    }

    private void readf() {
        int bxIdx = (r.getAx() & 0xffff);
        r.al = mucom2.octdata;
        short ans = r.pop();
        mucom2.octdata = (byte) (ans & 0xff);
        mucom2.octsave = (byte) (ans >> 8);
        if (r.al == 8) {
            r.al = 0x38;
            r.setDx((short) (((mucom2.data1[bxIdx] & 0xff) | ((mucom2.data1[bxIdx + 1] & 0xff) << 8)) << 1));
            r.dh += r.al;
            if ((r.getDx() & 0xffff) > 0x3fff) r.setDx((short) 0x3fff);
        } else {
            r.al = (byte) (((r.al & 0xff) << 3) + (mucom2.data1[bxIdx + 1] & 0xff));
            r.dh = r.al;
            r.dl = mucom2.data1[bxIdx];
        }
        if (mucom2.dt2mode != 0) {
            r.al = (byte) 0xfa;
            muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
            r.setCx((short) 3);
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
        set_dtfreq();
        read_exit();
    }

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
        r.ah = mucom2.dtdata[r.getSi() & 0xffff];
        mucom2.freq_lfo();
        calcdt();
        int finalAx = (r.getAx() & 0xffff) + (r.getDx() & 0xffff);
        r.setDx(r.pop());
        int dxFixed = (r.getDx() & 0xf800);
        r.setAx((short) (finalAx + dxFixed));
        byte tmp = r.al;
        r.al = r.ah;
        r.ah = tmp;
        if (work.md == null) muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        else {
            work.md.dat = r.al;
            work.md.args.set(work.mdArgsStep + 0, (int) (work.ontei + work.oct * 12));
            work.md.args.set(work.mdArgsStep + 1, work.otoLength);
            work.md.linePos.chip = work.crntChip;
            work.md.linePos.ch = (byte) work.crntChannel;
            work.md.linePos.part = work.crntPart;
            muap98.objectBuf.set(r.di++, work.md);
        }
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
    }

    private void reads() {
        int bxIdx = (r.getAx() & 0xffff);
        r.setDx((short) ((mucom2.data2[bxIdx] & 0xff) | ((mucom2.data2[bxIdx + 1] & 0xff) << 8)));
        r.al = mucom2.octdata;
        short ans = r.pop();
        mucom2.octdata = (byte) (ans & 0xff);
        mucom2.octsave = (byte) (ans >> 8);
        while (r.al != 0) {
            r.setDx((short) ((r.getDx() & 0xffff) >> 1));
            r.al--;
        }
        r.ah = mucom2.dtdata[0];
        mucom2.freq_lfo();
        calcdt();
        r.setDx((short) ((r.getDx() & 0xffff) - (r.getAx() & 0xffff)));
        r.al = r.dh;
        r.ah = r.dl;
        if (work.md == null) muap98.objectBuf.set(r.di++, new MmlDatum(r.al));
        else {
            work.md.dat = r.al;
            work.md.args.set(work.mdArgsStep + 0, (int) (work.ontei + work.oct * 12));
            work.md.args.set(work.mdArgsStep + 1, work.otoLength);
            work.md.linePos.chip = work.crntChip;
            work.md.linePos.ch = (byte) work.crntChannel;
            work.md.linePos.part = work.crntPart;
            muap98.objectBuf.set(r.di++, work.md);
        }
        muap98.objectBuf.set(r.di++, new MmlDatum(r.ah));
        read_exit();
    }

    public void xsmall() {
        if (r.al > (byte) 'z') return;
        if (r.al >= (byte) 'a') r.al -= (byte) ' ';
    }

    public void chknum() {
        if (r.al != (byte) '\\') {
            chknum2();
            return;
        }
        r.push(r.getAx());
        r.al = (byte) (((r.getBx() & 0xffff) + 1) >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[(r.getBx() & 0xffff) + 1]);
        chknum2();
        if (r.carry) {
            r.setAx(r.pop());
            return;
        }
        r.carry = ((r.al & 0xff) < (byte) '1');
        r.al = (byte) ((r.al & 0xff) - (byte) '1');
        if (r.carry) {
            r.setAx(r.pop());
            return;
        }
        r.push(r.getCx());
        r.cl = r.al;
        r.setAx((short) (1 << (r.cl & 0xff)));
        r.zero = ((mucom2.macroflg & (r.getAx() & 0xffff)) != 0);
        r.setCx(r.pop());
        r.carry = false;
        if (r.zero) {
            r.setBx((short) ((r.getBx() & 0xffff) + 2));
            r.carry = true;
        }
        r.setAx(r.pop());
    }

    private void chknum2() {
        if ((r.al & 0xff) < (byte) '0') {
            r.carry = true;
            return;
        }
        r.carry = !((r.al & 0xff) < (byte) '9' + 1);
    }

    private void chkcm() {
        mucom2.chktxt();
        r.zero = (r.al == (byte) ',');
        r.cl = 32;
        if (!r.zero) mucom2.error();
    }

    private Tuple<String, Runnable>[] exCmdTbl;

    private void InitExCmdTbl() {
        exCmdTbl = new Tuple[] {new Tuple<String, Runnable>("J", this::ExcmdJump)};
    }

    private boolean SearchExtendCommand() {
        for (Tuple<String, Runnable> cmd : exCmdTbl) {
            int ptr = (r.getBx() & 0xffff);
            int cmdPtr = 0;
            while (true) {
                byte ch = (byte) (ptr >= muap98.sourceBuf.length ? 0 : muap98.sourceBuf[ptr]);
                if (ch == 0) break;
                if (ch >= (byte) 'a') ch -= (byte) ' ';
                if (ch != (byte) cmd.getItem1().charAt(cmdPtr)) break;
                ptr++;
                cmdPtr++;
                if (cmdPtr < cmd.getItem1().length()) continue;
                r.setBx((short) ptr);
                cmd.getItem2().run();
                return true;
            }
        }
        return false;
    }

    private void ExcmdJump() {
        work.lstMd.add(new MmlDatum(MMLType.SkipPlay, new ArrayList<>(Arrays.asList(1)), null, -1));
        if (muap98.objectBuf.size() > 0) {
            MmlDatum md = muap98.objectBuf.get(0);
            if (md.args == null) md.args = new ArrayList<>();
            md.args.add(new MmlDatum(MMLType.SkipPlay, null, null, -1));
        }
    }
}
