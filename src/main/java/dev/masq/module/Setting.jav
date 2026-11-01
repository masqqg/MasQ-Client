
package dev.masq.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    private final String name;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void showIf(BooleanSupplier condition) {
        visibility = condition != null ? condition : () -> true;
    }

    public boolean visible() {
        return visibility.getAsBoolean();
    }

    public abstract T get();

    public void set(T value) {
    }

    public static final class Bool extends Setting<Boolean> {
        private boolean value;

        public Bool(String name, boolean initial) {
            super(name);
            value = initial;
        }

        @Override
        public Boolean get() {
            return value;
        }

        public boolean getValue() {
            return value;
        }

        @Override
        public void set(Boolean value) {
            if (value != null) this.value = value;
        }

        public void toggle() {
            value = !value;
        }
    }

    public static final class Num extends Setting<Float> {
        private float value;
        public final float min;
        public final float max;
        public final float step;

        public Num(String name, float initial, float min, float max, float step) {
            super(name);
            this.min = min;
            this.max = max;
            this.step = step > 0 ? step : 0.1f;
            set(initial);
        }

        @Override
        public Float get() {
            return value;
        }

        public float f() {
            return value;
        }

        public int i() {
            return Math.round(value);
        }

        public float frac() {
            if (max <= min) return 0f;
            return (value - min) / (max - min);
        }

        public void setFrac(float fraction) {
            float f = Math.max(0f, Math.min(1f, fraction));
            set(min + f * (max - min));
        }

        @Override
        public void set(Float next) {
            if (next == null) return;
            float clamped = Math.max(min, Math.min(max, next));
            value = min + Math.round((clamped - min) / step) * step;
            value = Math.max(min, Math.min(max, value));
        }
    }

    public static final class Mode extends Setting<String> {
        private final List<String> modes = new ArrayList<>();
        private int index;

        public Mode(String name, String initial, String... options) {
            super(name);

            if (options != null) {
                for (String option : options) {
                    if (option != null && !modes.contains(option)) {
                        modes.add(option);
                    }
                }
            }

            if (modes.isEmpty()) {
                modes.add(initial == null ? "Default" : initial);
            }

            index = modes.indexOf(initial);
            if (index < 0) index = 0;
        }

        @Override
        public String get() {
            return modes.get(index);
        }

        @Override
        public void set(String value) {
            int found = modes.indexOf(value);
            if (found >= 0) index = found;
        }

        public boolean is(String value) {
            return get().equals(value);
        }

        public void next() {
            index = (index + 1) % modes.size();
        }

        public List<String> modes() {
            return List.copyOf(modes);
        }
    }
}
