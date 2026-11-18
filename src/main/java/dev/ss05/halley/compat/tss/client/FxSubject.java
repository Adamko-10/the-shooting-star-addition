package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.client.cinematic.Subject;
import cyou.rimuru.shootingstardemo.client.fx.SpellFx;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A spell effect's film subject (the caster, the strike's target and the way they faced). The Shooting Star 1.6.0 gave
 * SpellFx a second no-argument method under the same obfuscated name (its seed, a long), so Java source can't call the
 * Subject one by name any more: it is found once by its return type and called through reflection.
 */
final class FxSubject {
    private static Method method;

    private FxSubject() {
    }

    static Subject of(SpellFx fx) {
        try {
            if (method == null) {
                for (Method m : SpellFx.class.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == Subject.class && !Modifier.isStatic(m.getModifiers())) {
                        m.setAccessible(true);
                        method = m;
                        break;
                    }
                }
                if (method == null) {
                    throw new IllegalStateException("The Shooting Star's SpellFx has no film subject method");
                }
            }
            return (Subject) method.invoke(fx);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read a spell effect's film subject", e);
        }
    }
}
