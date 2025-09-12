package com.chen.qa;

import hudson.model.ParameterValue;
import java.util.Objects;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

public class DynamicChoiceUrlParameterValue extends ParameterValue {

    @Exported(visibility = 4)
    public final String value;

    @DataBoundConstructor
    public DynamicChoiceUrlParameterValue(String name, String value) {
        super(name);
        this.value = value;
    }

    public DynamicChoiceUrlParameterValue(String name, String value, String description) {
        super(name, description);
        this.value = value;
    }

    /**
     * Expose the value to the script environment.
     * @return the value
     */
    @Override
    @Exported
    public Object getValue() {
        return value;
    }

    @Override
    public void buildEnvironment(hudson.model.Run<?, ?> run, hudson.EnvVars env) {
        env.put(getName(), value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DynamicChoiceUrlParameterValue that = (DynamicChoiceUrlParameterValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value);
    }
}
