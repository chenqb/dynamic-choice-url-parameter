package com.chen.qa;

import hudson.model.StringParameterValue;
import org.kohsuke.stapler.DataBoundConstructor;

public class DynamicChoiceUrlParameterValue extends StringParameterValue {

    @DataBoundConstructor
    public DynamicChoiceUrlParameterValue(String name, String value) {
        super(name, value);
    }

    public DynamicChoiceUrlParameterValue(String name, String value, String description) {
        super(name, value, description);
    }
}
