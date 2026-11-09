package plz.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Made public after Mixin merges it: Mixin refuses non-private statics, but Lua calls these by name. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface LuaPublic {
}
