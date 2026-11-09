package plz.agent;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

public final class PLZMixinServiceBootstrap implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "PLZAgent";
    }

    @Override
    public String getServiceClassName() {
        return PLZMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
    }
}
