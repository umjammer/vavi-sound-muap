package muap.compiler;

import muap.common.X86Register;


class Menu {

    private final X86Register r;
    private final Muap98 muap98;

    Menu(X86Register reg, Muap98 muap98) {
        this.r = reg;
        this.muap98 = muap98;
    }

    byte crFlag = 0;
    private byte inMode = 0;

    void checkCalPlay() {
        r.zero = ((muap98.m_mode[0] & 2) == 0);
    }

    void checkVisualPlay() {
        r.zero = ((inMode & 8) == 0);
    }
}
