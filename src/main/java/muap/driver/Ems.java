package muap.driver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import dotnet4j.util.compat.QuadConsumer;
import dotnet4j.util.compat.TriConsumer;
import muap.common.X86Register;


public class Ems {

    private int crntEmsHandle = 0;
    private int crntPageMap = 0;
    private int pPageNo = 0;
    private int lPageNo = 0;
    private Map<Integer, Boolean> useEMSList = new HashMap<>();
    private Map<Integer, String> handleName = new HashMap<>();
    private Map<Integer, byte[][]> emsBuff = new HashMap<>();
    private Map<Integer, int[]> mappedPage = new HashMap<>();

    public interface EMS_Map extends QuadConsumer<Integer, byte[], Integer, Integer> {
    }

    public interface EMS_GetHandleName extends TriConsumer<byte[], Integer, String[]> {
    }

    public interface EMS_SetHandleName extends TriConsumer<byte[], Integer, String> {
    }

    public interface EMS_AllocMemory extends TriConsumer<byte[], int[], Integer> {
    }

    public Supplier<byte[]> cS4231EMS_GetCurrentMapBuf;
    public EMS_Map cS4231EMS_Map;
    public Supplier<Integer> cS4231EMS_GetPageMap;
    public EMS_GetHandleName cS4231EMS_GetHandleName;
    public EMS_SetHandleName cS4231EMS_SetHandleName;
    public EMS_AllocMemory cS4231EMS_AllocMemory;

    public Ems() {
        crntEmsHandle = 0;
        useEMSList = new HashMap<>();
        handleName = new HashMap<>();
        emsBuff = new HashMap<>();
        mappedPage = new HashMap<>();
    }

    public Ems(Supplier<byte[]> cS4231EMS_GetCurrentMapBuf,
               EMS_Map cS4231EMS_Map,
               Supplier<Integer> cS4231EMS_GetPageMap,
               EMS_GetHandleName cS4231EMS_GetHandleName,
               EMS_SetHandleName cS4231EMS_SetHandleName,
               EMS_AllocMemory cS4231EMS_AllocMemory) {
        this.cS4231EMS_GetCurrentMapBuf = cS4231EMS_GetCurrentMapBuf;
        this.cS4231EMS_Map = cS4231EMS_Map;
        this.cS4231EMS_GetPageMap = cS4231EMS_GetPageMap;
        this.cS4231EMS_GetHandleName = cS4231EMS_GetHandleName;
        this.cS4231EMS_SetHandleName = cS4231EMS_SetHandleName;
        this.cS4231EMS_AllocMemory = cS4231EMS_AllocMemory;
    }

    public void getHandleName(X86Register reg, String[] sbuf) {
        reg.ah = 0;
        int dx = reg.getDx() & 0xFFFF;
        if (handleName.containsKey(dx)) {
            sbuf[0] = handleName.get(dx);
            return;
        }

        sbuf[0] = ""; // Kuma: It seems AH becomes 0 even if there is no match
    }

    public void setHandleName(X86Register reg, String emsname2) {
        reg.ah = 0;
        int dx = reg.getDx() & 0xFFFF;
        if (!handleName.containsKey(dx))
            handleName.put(dx, emsname2);
        else
            handleName.put(dx, emsname2);
    }

    public void allocMemory(X86Register reg) {
        // Search for an unused handle
        int cnt = 0;
        while (cnt < 0x10000) {
            if (!useEMSList.containsKey(crntEmsHandle) || !useEMSList.get(crntEmsHandle)) break;
            crntEmsHandle++;
            crntEmsHandle &= 0xffff;
            cnt++;
        }

        if (cnt == 0x10000) {
            reg.ah = 1;
            return;
        }

        reg.setDx((short) crntEmsHandle);
        if (!useEMSList.containsKey(crntEmsHandle)) useEMSList.put(crntEmsHandle, true);
        else useEMSList.put(crntEmsHandle, true);

        int bx = reg.getBx() & 0xFFFF;
        if (!emsBuff.containsKey(crntEmsHandle)) emsBuff.put(crntEmsHandle, null);
        emsBuff.put(crntEmsHandle, new byte[bx][]);
        if (!mappedPage.containsKey(crntEmsHandle)) mappedPage.put(crntEmsHandle, null);
        mappedPage.put(crntEmsHandle, new int[bx]);

        byte[][] buffer = emsBuff.get(crntEmsHandle);
        int[] mapping = mappedPage.get(crntEmsHandle);

        for (int i = 0; i < bx; i++) {
            buffer[i] = new byte[16 * 1024]; // alloc 16Kbyte
            for (int j = 0; j < 16 * 1024; j++) buffer[i][j] = (byte) 0x80;
            mapping[i] = 0xffff; // Unmapped state
        }
        reg.ah = 0;
    }

    public int getPageMap() {
        return crntPageMap;
    }

    public void map(X86Register reg) {
        pPageNo = reg.al & 0xFF; // Physical page number
        lPageNo = reg.getBx() & 0xFFFF; // Logical page number

        try {
            // Map
            int dx = reg.getDx() & 0xFFFF;
            mappedPage.get(dx)[pPageNo] = lPageNo; // Unmapped state if 0xffff

            reg.ah = 0x00; // Normal execution
        } catch (Exception e) {
            reg.ah = (byte) 0x80;
        }
    }

    public byte map(byte al, int bx, int dx) {
        pPageNo = al & 0xFF; // Physical page number
        lPageNo = bx; // Logical page number

        try {
            // Map
            mappedPage.get(dx)[pPageNo] = lPageNo; // Unmapped state if 0xffff

            return 0x00; // Normal execution
            // put into AH
        } catch (Exception e) {
            return (byte) 0x80;
            // put into AH
        }
    }

    public void setPageMap(int si, byte[] pemsbuf) {
        crntPageMap = pemsbuf[si] & 0xFF;
    }

    public byte[] getCrntMapBuf() {
        int logicalIdx = mappedPage.get(crntEmsHandle)[crntPageMap];
        return emsBuff.get(crntEmsHandle)[logicalIdx];
    }

    public byte[] getEmsArray(int stPage, int endPage) {
        List<Byte> lst = new ArrayList<>();
        byte[][] buffer = emsBuff.get(crntEmsHandle);
        for (int i = stPage; i < endPage; i++) {
            for (byte b : buffer[i]) {
                lst.add(b);
            }
        }
        byte[] result = new byte[lst.size()];
        for (int i = 0; i < lst.size(); i++) {
            result[i] = lst.get(i);
        }
        return result;
    }
}
