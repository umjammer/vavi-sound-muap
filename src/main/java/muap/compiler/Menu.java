package muap.compiler;

import muap.common.X86Register;


public class Menu {

    private X86Register r;
    private Muap98 muap98;

    public Menu(X86Register reg, Muap98 muap98) {
        this.r = reg;
        this.muap98 = muap98;
    }

    public byte crflag = 0;
    public byte inmode = 0;

    public void check_calplay() {
        // Sets the zero flag based on the m_mode calculation
        r.zero = ((muap98.m_mode[0] & 2) == 0);
    }

    public void check_visualplay() {
        // Sets the zero flag based on the inmode calculation
        r.zero = ((inmode & 8) == 0);
    }
}
