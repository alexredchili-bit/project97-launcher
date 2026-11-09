package zombie.plz.access;

import java.sql.Connection;
import zombie.network.ServerWorldDatabase;

public interface PLZServerWorldDatabaseAccess {
    Connection plzConnection();

    static Connection connection() {
        Object db = ServerWorldDatabase.instance;
        return db instanceof PLZServerWorldDatabaseAccess access ? access.plzConnection() : null;
    }
}
