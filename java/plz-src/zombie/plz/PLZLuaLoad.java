package zombie.plz;

import java.io.IOException;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaUtil;
import se.krka.kahlua.vm.LuaCallFrame;

public final class PLZLuaLoad implements JavaFunction {
    private static final int LOADSTRING = 0;
    private static final int LOADSTREAM = 1;
    private static final String[] NAMES = new String[]{"loadstring", "loadstream"};
    private final int index;

    private PLZLuaLoad(int index) {
        this.index = index;
    }

    public static void register(KahluaTable environment) {
        for (int i = 0; i < NAMES.length; i++) {
            environment.rawset(NAMES[i], new PLZLuaLoad(i));
        }
    }

    @Override
    public int call(LuaCallFrame callFrame, int nArguments) {
        switch (this.index) {
            case LOADSTRING:
                return loadstring(callFrame, nArguments);
            case LOADSTREAM:
                return LuaCompiler.loadstream(callFrame, nArguments);
            default:
                return 0;
        }
    }

    private static int loadstring(LuaCallFrame callFrame, int nArguments) {
        try {
            KahluaUtil.luaAssert(nArguments >= 1, "not enough arguments");
            String source = (String)callFrame.get(0);
            KahluaUtil.luaAssert(source != null, "No source given");
            String name = null;
            if (nArguments >= 2) {
                name = (String)callFrame.get(1);
            }

            return callFrame.push(LuaCompiler.loadstring(source, name, callFrame.getEnvironment()));
        } catch (RuntimeException | IOException e) {
            return callFrame.push(null, e.getMessage());
        }
    }

    @Override
    public String toString() {
        return NAMES[this.index];
    }
}
