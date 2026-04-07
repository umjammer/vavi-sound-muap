package muap.common;

import java.util.Stack;


public class X86Register {

    //public PW pw = null;

    public byte al;
    public byte ah;
    private short eaxh;

    public short getAx() {
        // Return (ah * 256 + al) cast to ushort equivalent (short in Java)
        return (short) (((ah & 0xff) << 8) | (al & 0xff));
    }

    public void setAx(short value) {
        ah = (byte) ((value >> 8) & 0xff);
        al = (byte) (value & 0xff);
    }

    public int getEax() {
        // Return (eaxh * 65536 + ax) cast to uint equivalent (int in Java)
        return ((eaxh & 0xffff) << 16) | (getAx() & 0xffff);
    }

    public void setEax(int value) {
        eaxh = (short) (value >> 16);
        setAx((short) (value & 0xffff));
    }

    public byte bl;
    public byte bh;

    public short getBx() {
        return (short) (((bh & 0xff) << 8) | (bl & 0xff));
    }

    public void setBx(short value) {
        //if (pw != null && pw.checkJumpIndexBX) {
        //    if (value == pw.jumpIndex)
        //        pw.jumpIndex = -1;
        //}

        bh = (byte) ((value >> 8) & 0xff);
        bl = (byte) (value & 0xff);
    }

    public byte cl;
    public byte ch;

    public short getCx() {
        return (short) (((ch & 0xff) << 8) | (cl & 0xff));
    }

    public void setCx(short value) {
        ch = (byte) ((value >> 8) & 0xff);
        cl = (byte) (value & 0xff);
    }

    public byte dl;
    public byte dh;
    private short edxh;

    public short getDx() {
        return (short) (((dh & 0xff) << 8) | (dl & 0xff));
    }

    public void setDx(short value) {
        dh = (byte) ((value >> 8) & 0xff);
        dl = (byte) (value & 0xff);
    }

    public int getEdx() {
        return ((edxh & 0xffff) << 16) | (getDx() & 0xffff);
    }

    public void setEdx(int value) {
        edxh = (short) (value >> 16);
        setDx((short) (value & 0xffff));
    }

    public short di;
    public short cs;
    public short es;
    public short ds;

    public short fs;

    private short _si;

    public short getSi() {
        return _si;
    }

    public void setSi(short value) {
        //if (pw != null && pw.checkJumpIndexSI) {
        //    if (value == pw.jumpIndex)
        //        pw.jumpIndex = -1;
        //}
        _si = value;
    }

    public short bp;

    public short sp;
    public short ss;

    public boolean carry;

    public boolean sign;

    public boolean zero;
    public boolean overflow;

    public Stack<Short> stack = new Stack<>();

    public Object lockobj = new Object();

    private int[] bitMask = {0x00, 0x01, 0x03, 0x07, 0x0f, 0x1f, 0x3f, 0x7f, 0xff};

    public byte rol(byte r, int n) {
        n &= 7;
        int val = r & 0xff;
        byte ans = (byte) ((val << n) | (val >> (8 - n))); // & bitMask[n]));
        carry = ((ans & 0x01) != 0);
        return ans;
    }

    public byte ror(byte r, int n) {
        n &= 7;
        int val = r & 0xff;
        byte ans = (byte) ((val << (8 - n)) | (val >> n)); // & bitMask[8 - n]));
        carry = ((ans & 0x80) != 0);
        return ans;
    }

    public byte rcl(byte r, int n) {
        n &= 7;
        int val = r & 0xff;
        byte ans = (byte) (
                (val << n)
                        | ((carry ? 1 : 0) << n)
                        | (n < 2 ? 0 : (val >> (9 - n)))
        ); // & bitMask[n]));
        carry = ((val & (0x100 >> n)) != 0);
        return ans;
    }

    public short rcl(short r, int n) {
        n &= 15;
        int val = r & 0xffff;
        short ans = (short) (
                (val << n)
                        | ((carry ? 1 : 0) << (n - 1))
                        | (n < 2 ? 0 : (val >> (17 - n)))
        ); // & bitMask[n]));
        carry = ((val & (0x10000 >> n)) != 0);
        return ans;
    }

    public byte rcr(byte r, int n) {
        n &= 7;
        int val = r & 0xff;
        byte ans = (byte) (
                (n < 2 ? 0 : (val << (9 - n)))
                        | ((carry ? 0x100 : 0) >> n)
                        | (val >> n)
        ); // & bitMask[n]));
        carry = ((val & (0x1 >> (n - 1))) != 0);
        return ans;
    }

    public short rcr(short r, int n) {
        n &= 0xf;
        int val = r & 0xffff;
        short ans = (short) (
                (n < 2 ? 0 : (val << (17 - n)))
                        | ((carry ? 0x10000 : 0) >> n)
                        | (val >> n)
        ); // & bitMask[n]));
        carry = ((val & (0x1 << (n - 1))) != 0);
        return ans;
    }

    public void test(byte src, byte bbit) {
        zero = ((src & bbit) == 0);
        carry = false;
    }

    public void test(short src, short ubit) {
        zero = ((src & ubit) == 0);
        carry = false;
    }

    public void mul(byte a) {
        int ans = (al & 0xff) * (a & 0xff);
        setAx((short) (ans & 0xffff));
    }

    public void mul(short a) {
        long ans = (long) (getAx() & 0xffff) * (a & 0xffff);
        setDx((short) ((ans >> 16) & 0xffff));
        setAx((short) (ans & 0xffff));
    }

    public void div(byte a) {
        int dividend = getAx() & 0xffff;
        int divisor = a & 0xff;
        byte q = (byte) (dividend / divisor);
        byte r = (byte) (dividend % divisor);
        al = q;
        ah = r;
    }

    public void div(short a) {
        long b = ((long) (getDx() & 0xffff) << 16) + (getAx() & 0xffff);
        int divisor = a & 0xffff;
        short q = (short) (b / divisor);
        short r = (short) (b % divisor);
        setAx(q);
        setDx(r);
    }

    /**
     * compare  What would the result flags be if a-b was performed?
     * ex
     * ja     (a>b)  c=0&z=0
     * jna    (a<=b) c=1|z=1
     * jb/jc  (a<b)  c=1
     * jnb/jnc(a>=b) c=0
     * je/jz  (a=b)      z=1
     * jne/jnz(a!=b)     z=0
     */
    public void cmp(byte a, byte b) {
        zero = (a == b);
        carry = ((a & 0xff) < (b & 0xff));
    }

    /**
     * compare  What would the result flags be if a-b was performed?
     * ex
     * ja     (a>b)  c=0&z=0
     * jna    (a<=b) c=1|z=1
     * jb/jc  (a<b)  c=1
     * jnb/jnc(a>=b) c=0
     * je/jz  (a=b)      z=1
     * jne/jnz(a!=b)     z=0
     */
    public void cmp(short a, short b) {
        zero = (a == b);
        carry = ((a & 0xffff) < (b & 0xffff));
    }

    private final Stack<Byte> stackMem = new Stack<>();

    public void push(short v) {
        sp = (short) ((sp & 0xffff) - 2);
        stackMem.push((byte) (v & 0xff));
        stackMem.push((byte) ((v >> 8) & 0xff));
    }

    public short pop() {
        sp = (short) ((sp & 0xffff) + 2);
        int high = stackMem.pop() & 0xff;
        int low = stackMem.pop() & 0xff;
        return (short) ((high << 8) | low);
    }

    public void pushA() {
        push(getAx());
        push(getCx());
        push(getDx());
        push(getBx());
        push(bp);
        push(getSi());
        push(di);
    }

    public void popA() {
        di = pop();
        setSi(pop());
        bp = pop();
        setBx(pop());
        setDx(pop());
        setCx(pop());
        setAx(pop());
    }

    public byte[] toByteArray() {
        return new byte[30];
    }
}
