package zombie.plz.access;

public interface PLZIsoChunkAccess {
    void recalcLoop1();

    void recalcPooled();

    long[] plzOccluderMask();

    long plzOccluderMaskSet();

    void plzSetOccluderMaskSet(long mask);

    int[][] plzLightInfoFrame();

    void plzSetLightInfoFrame(int[][] stamps);

    int[] plzTreeExportFp();

    void plzSetTreeExportFp(int[] fp);
}
