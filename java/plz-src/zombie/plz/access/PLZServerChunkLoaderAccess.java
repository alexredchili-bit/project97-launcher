package zombie.plz.access;

public interface PLZServerChunkLoaderAccess {
    PLZSaveChunkThreadAccess plzThreadSave();

    void plzSetThreadSave(PLZSaveChunkThreadAccess threadSave);
}
