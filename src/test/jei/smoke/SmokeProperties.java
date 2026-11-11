package smoke;

import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/** Local blackboard used by Mixin and MixinExtras; no loader or game is emulated. */
public final class SmokeProperties implements IGlobalPropertyService {
    private record Key(String name) implements IPropertyKey {}
    private final Map<IPropertyKey, Object> values = new HashMap<>();
    @Override public IPropertyKey resolveKey(String name) { return new Key(name); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
    @Override public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
    @Override public <T> T getProperty(IPropertyKey key, T fallback) {
        T value = getProperty(key);
        return value == null ? fallback : value;
    }
    @Override public String getPropertyString(IPropertyKey key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value.toString();
    }
}
