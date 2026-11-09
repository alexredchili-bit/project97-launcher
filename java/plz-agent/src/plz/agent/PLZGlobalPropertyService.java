package plz.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

public final class PLZGlobalPropertyService implements IGlobalPropertyService {
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    private record Key(String name) implements IPropertyKey {
    }

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) this.values.get(((Key) key).name());
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        this.values.put(((Key) key).name(), value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        Object value = this.values.get(((Key) key).name());
        return value != null ? (T) value : defaultValue;
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = this.values.get(((Key) key).name());
        return value != null ? value.toString() : defaultValue;
    }
}
