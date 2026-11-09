package plz.mixin.server;

import java.sql.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.network.ServerWorldDatabase;
import zombie.plz.access.PLZServerWorldDatabaseAccess;

@Mixin(ServerWorldDatabase.class)
abstract class ServerWorldDatabaseAccessMixin implements PLZServerWorldDatabaseAccess {
    @Shadow
    Connection conn;

    @Override
    public Connection plzConnection() {
        return this.conn;
    }
}
