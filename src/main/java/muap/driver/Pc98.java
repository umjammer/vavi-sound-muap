package muap.driver;

import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import muap.common.X86Register;
import musicDriverInterface.ChipDatum;
import org.apache.tools.ant.types.LogLevel;


/**
 * Hardware I/O for a NEC PC-9801 system.
 */
public class Pc98 {

    private static final Logger logger = System.getLogger(Pc98.class.getName());

    private int lastPort = 0;
    private int lastData = 0;

    private int cs4231dmaInt = 0;
    private int cs4231IdxAdr = 0;
    private int cs4231IdxDat = 0;
    private int cs4231INTRst = 0;
    private byte[] cs4231_Reg = new byte[32];
    private int _86PcmFifo = 0;

    private Work work;
    private X86Register reg;
    private Consumer<ChipDatum> writeOPNAP;
    private Consumer<ChipDatum> writeOPN2P;
    private Consumer<ChipDatum> writeCS4231;
    private Function<Byte, Byte> readCS4231;
    private BiFunction<Byte, Byte, Boolean> write8253;
    private int sdm = 0;

    // FM sound source connection state
    // 0: None, 1: YM2203, 2: YM3438, 3: YM2608+ADPCM, 4: YM2608+WSS, 5: YM2608+86B
    private int[][] connectFMDevice = new int[][] {
            new int[] {
                    0, // 0x088~ None
                    4, // 0x188~ 98CanBe(YM2608+WSS)
                    2  // 0x288~ YM3438
            },
            new int[] {
                    0, // 0x088~ None
                    3, // 0x188~ Otomi-chan(YM2608+ADPCM)
                    2  // 0x288~ YM3438
            }
    };

    private byte[] fmAdr = new byte[6];
    private byte[][] fmReg = new byte[6][];

    public Pc98(Work work, X86Register reg,
                Consumer<ChipDatum> writeOPNAP,
                Consumer<ChipDatum> writeOPN2P,
                Consumer<ChipDatum> writeCS4231,
                Function<Byte, Byte> readCS4231,
                BiFunction<Byte, Byte, Boolean> write8253,
                int soundDeviceMode /* = 0 */) {
        this.work = work;
        this.reg = reg;
        this.writeOPNAP = writeOPNAP;
        this.writeOPN2P = writeOPN2P;
        this.writeCS4231 = writeCS4231;
        this.readCS4231 = readCS4231;
        this.write8253 = write8253;
        this.sdm = soundDeviceMode;

        for (int i = 0; i < fmReg.length; i++) {
            fmReg[i] = new byte[256];
        }
    }

    public byte inportB(int dx) {
        // Interrupt controller
        if (dx == 2) {
            return readCS4231.apply((byte) 5); // IMR
        }

        int m = (dx >> 8) & 0xff;
        int l = dx & 0xff;

        if (m == 0) {
            if (l == 0x42) {
                // Status port (bit 5: 1=8MHz machine, 0=5/10MHz)
                return 0x20; // 8MHz machine
            }
        }

        if (m == 0xa4) {
            // Model detection
            if (l == 0x60) {
                if (connectFMDevice[sdm][1] == 4) return (byte) 0x80; // PC9821Cx
                if (connectFMDevice[sdm][1] == 5) return 0x40; // 86B
                return 0;
            } else if (l == 0x68) {
                // 86BPCM FIFO control
                return 0;
            }
        }

        // CS4231 related
        if (m == 0x0f) {
            if (l == 0x40) return readCS4231.apply((byte) 4);
            if (l == 0x44) return readCS4231.apply((byte) 0); // bit7: 1=Initializing
            if (l == 0x45) return readCS4231.apply((byte) 1);
            if (l == 0x46) return readCS4231.apply((byte) 2);
        }

        // FM sound source port reading
        if (m <= connectFMDevice[sdm].length - 1) {
            if (connectFMDevice[sdm][m] == 1) { // YM2203
                if (l == 0x88) return 0;
                if (l == 0x8a) {
                    if ((fmAdr[m] & 0xff) < 0x10) return fmReg[m][fmAdr[m] & 0xff];
                }
            } else if (connectFMDevice[sdm][m] == 2) { // YM3438
                if (l == 0x88) return (byte) 0xff;
                if (l == 0x8a) return 0;
            } else if (connectFMDevice[sdm][m] == 3 || connectFMDevice[sdm][m] == 4 || connectFMDevice[sdm][m] == 5) { // YM2608 series
                if (l == 0x88) {
                    return (byte) (work.timerOPNA1.statReg | 0x80);
                } else if (l == 0x8a) {
                    if (lastPort == 0x88 && lastData == 0xff) return 1; // Identify as 2608
                    if ((fmAdr[m] & 0xff) < 0x10) return fmReg[m][fmAdr[m] & 0xff];
                    return 0;
                } else if (l == 0x8c) {
                    return (byte) 0x88; // bit7: busy, bit3: pcm
                } else if (l == 0x8d) {
                    return 0x00; // YM3438 check port (bit7=1 would imply NOT 2608)
                }
            }
        }

        return 0;
    }

    public void outportB(int dx, byte al) {
        int m = (dx >> 8) & 0xff;
        int l = dx & 0xff;

        if (m == 0x00) {
            if (dx == 0x00) {
                // EOI if al == 0x20
                return;
            } else if (dx == 0x02) {
                ChipDatum cd = new ChipDatum(1, 2, al & 0xff, 0, work.crntMmlDatum);
                writeCS4231.accept(cd); // IMR
                return;
            } else if (l == 0x5 || l == 0x7) {
                ChipDatum cd = new ChipDatum(1, l, al & 0xff, 0, work.crntMmlDatum);
                writeCS4231.accept(cd);
                return;
            } else if (dx == 0x15 || dx == 0x17 || dx == 0x19 || dx == 0x5f) {
                return;
            } else if (dx >= 0x71 && dx <= 0x77) {
                write8253.apply((byte) dx, al);
                return;
            }
        }

        // CS4231 related
        if (m == 0x0f) {
            int addr = -1;
            if (l == 0x40) addr = 4;
            else if (l == 0x44) addr = 0;
            else if (l == 0x45) addr = 1;
            else if (l == 0x46) addr = 2;

            if (addr != -1) {
                ChipDatum cd = new ChipDatum(0, addr, al & 0xff, 0, work.crntMmlDatum);
                writeCS4231.accept(cd);
                return;
            }
        }

        if (m == 0xa4) {
            if (l == 0x60) return;
            else if (l == 0x6c) {
                _86PcmFifo = al & 0xff;
                return;
            }
        }

        lastPort = dx;
        lastData = al & 0xff;

        if (m > connectFMDevice[sdm].length - 1) return;

        if (l == 0x88) {
            fmAdr[m * 2] = al;
        } else if (l == 0x8a) {
            fmReg[m * 2][fmAdr[m * 2] & 0xff] = al;
            ChipDatum dat = new ChipDatum(0, fmAdr[m * 2] & 0xff, al & 0xff, 0, work.crntMmlDatum);
            if (m == 1) writeOPNAP.accept(dat);
            else writeOPN2P.accept(dat);
        } else if (l == 0x8c) {
            fmAdr[m * 2 + 1] = al;
        } else if (l == 0x8e) {
            fmReg[m * 2 + 1][fmAdr[m * 2 + 1] & 0xff] = al;
            ChipDatum dat = new ChipDatum(1, fmAdr[m * 2 + 1] & 0xff, al & 0xff, 0, work.crntMmlDatum);
            if (m == 1) writeOPNAP.accept(dat);
            else writeOPN2P.accept(dat);
        }
    }

    public void outportBDummy(int dx) {
        int m = (dx >> 8) & 0xff;
        int l = dx & 0xff;
        if (l == 0x88) {
            ChipDatum dat = new ChipDatum(-1, 0, 0, 0, work.crntMmlDatum);
            if (m == 1) writeOPNAP.accept(dat);
            else writeOPN2P.accept(dat);
        }
    }

    public void OutportC4231_Adrs(byte channel, int index, int val) {
        ChipDatum cd = new ChipDatum(2, channel * 10 + index, val & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
        cd = new ChipDatum(2, channel * 10 + index, (val >> 8) & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
    }

    public void OutportC4231_Cnt(byte channel, int index, int val) {
        ChipDatum cd = new ChipDatum(2, channel * 10 + 2 + index, val & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
        cd = new ChipDatum(2, channel * 10 + 2 + index, (val >> 8) & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
    }

    public void OutportC4231_Freq(byte channel, int index, int val) {
        ChipDatum cd = new ChipDatum(2, channel * 10 + 4 + index, val & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
        cd = new ChipDatum(2, channel * 10 + 4 + index, (val >> 8) & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
    }

    public void OutportC4231_Pan(byte channel, int index, int val) {
        ChipDatum cd = new ChipDatum(2, channel * 10 + 6 + index, val & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
        cd = new ChipDatum(2, channel * 10 + 6 + index, (val >> 8) & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
    }

    public void OutportC4231_Volume(byte channel, int index, int val) {
        ChipDatum cd = new ChipDatum(2, channel * 10 + 8 + index, val & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
        cd = new ChipDatum(2, channel * 10 + 8 + index, (val >> 8) & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
    }

    public void OutportC4231_Freq2(int val) {
        ChipDatum cd = new ChipDatum(2, 200, val & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
        cd = new ChipDatum(2, 200, (val >> 8) & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
    }

    public void OutportC4231_Jump1(int val) {
        ChipDatum cd = new ChipDatum(2, 201, val & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
        cd = new ChipDatum(2, 201, (val >> 8) & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
    }

    public void OutportC4231_Jump2(byte val) {
        ChipDatum cd = new ChipDatum(2, 202, val & 0xff, 0, work.crntMmlDatum);
        writeCS4231.accept(cd);
    }

    public byte[] ReadOpnaPCMMemory(int port34, int v1, int v2) {
        return new byte[] {(byte) 'M', (byte) 'P', (byte) '2', (byte) '3'};
    }

    //
    // Open File(実際は存在確認のみ)
    //
    public void Int21_3d(String path) {
        logger.log(Level.DEBUG, "INT21H AH:0x3d Open File: {0}", path);

        if (new File(path).exists()) {
            reg.carry = false; // success
            reg.setAx((short) 0); // Filehandle
            return;
        }

        reg.carry = true;
    }
}
