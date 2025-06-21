package com.chen.qa;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;

public class DynamicChoiceUrlParameterValue extends ParameterValue {
    private final String value;

    @DataBoundConstructor
    public DynamicChoiceUrlParameterValue(String name, String value) {
        super(name);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        if (value != null) {
            env.put(getName(), value);
        }
    }

    @Override
    public String toString() {
        return "DynamicChoiceUrlParameterValue: " + getName() + "=" + value;
    }
}
