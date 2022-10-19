package com.thycotic.secrets.jenkins;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;

import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;

// borrowed from https://github.com/jenkinsci/azure-keyvault-plugin/blob/master/src/main/java/org/jenkinsci/plugins/azurekeyvaultplugin/MaskingConsoleLogFilter.java
public class VaultConsoleLogFilter extends ConsoleLogFilter implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String charsetName;
    private final List<String> valuesToMask;

    public VaultConsoleLogFilter(final String charsetName, final List<String> valuesToMask) {
        this.charsetName = charsetName;
        this.valuesToMask = valuesToMask;
    }

    @Override
    public OutputStream decorateLogger(Run run, final OutputStream logger) {
        return new SecretPatterns.MaskingOutputStream(logger, () -> SecretPatterns.getAggregateSecretPattern(valuesToMask), charsetName);
    }
}