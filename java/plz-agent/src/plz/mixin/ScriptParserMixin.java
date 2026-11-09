package plz.mixin;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.plz.PLZPerf;
import zombie.plz.PLZScriptText;
import zombie.scripting.ScriptParser;

@Mixin(ScriptParser.class)
abstract class ScriptParserMixin {
    @Inject(method = "stripComments", at = @At("HEAD"), cancellable = true)
    private static void plz$fastStripComments(String totalFile, CallbackInfoReturnable<String> cir) {
        if (PLZPerf.SCRIPT_PARSER_FAST) {
            String fast = PLZScriptText.stripComments(totalFile);
            if (fast != null) {
                cir.setReturnValue(fast);
            }
        }
    }

    @Inject(method = "parseTokens", at = @At("HEAD"), cancellable = true)
    private static void plz$fastParseTokens(String totalFile, CallbackInfoReturnable<ArrayList<String>> cir) {
        if (PLZPerf.SCRIPT_PARSER_FAST) {
            cir.setReturnValue(PLZScriptText.parseTokens(totalFile));
        }
    }
}
