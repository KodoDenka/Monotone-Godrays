package dev.manasnow.monotonegodrays;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedFloat;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.util.Identifier;

/** {@code config/monotone_godrays/client.toml}. Read every frame, so changes show at once. */
public class GodraysConfig extends Config {
    private static GodraysConfig instance;

    public GodraysConfig() {
        super(Identifier.of(MonotoneGodrays.MOD_ID, "client"), MonotoneGodrays.MOD_ID, "", "client");
    }

    static void register() {
        instance = ConfigApiJava.registerAndLoadConfig(GodraysConfig::new, RegisterType.CLIENT);
    }

    /** The live config, or defaults if something reads it before the client entrypoint ran. */
    public static GodraysConfig get() {
        if (instance == null) {
            return new GodraysConfig();
        }
        return instance;
    }

    public ValidatedBoolean enabled = new ValidatedBoolean(true);
    public ValidatedFloat sunIntensity = new ValidatedFloat(0.5F, 2.0F, 0.0F);
    public ValidatedFloat moonIntensity = new ValidatedFloat(0.75F, 2.0F, 0.0F);
    /** Steps each ray takes toward its light. */
    public ValidatedInt quality = new ValidatedInt(30, 60, 4);
    /** How far toward the light the steps reach, as a share of the way. */
    public ValidatedFloat density = new ValidatedFloat(0.92F, 1.0F, 0.5F);
    /** How much each step keeps of the one before. */
    public ValidatedFloat decay = new ValidatedFloat(0.99F, 1.0F, 0.85F);
    public ValidatedFloat lensFlare = new ValidatedFloat(0.5F, 1.0F, 0.0F);
    public ValidatedBoolean fullResolution = new ValidatedBoolean(false);
}
