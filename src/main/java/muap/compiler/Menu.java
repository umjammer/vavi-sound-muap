package muap.compiler;

import muap.common.X86Register;


public class Menu {

    private final X86Register r;
    private final Muap98 muap98;

    public Menu(X86Register reg, Muap98 muap98) {
        this.r = reg;
        this.muap98 = muap98;
    }

    public byte crFlag = 0;
    public byte inMode = 0;

    public void checkCalPlay() {
        r.zero = ((muap98.m_mode[0] & 2) == 0);
    }

    public void checkVisualPlay() {
        r.zero = ((inMode & 8) == 0);
    }
}
