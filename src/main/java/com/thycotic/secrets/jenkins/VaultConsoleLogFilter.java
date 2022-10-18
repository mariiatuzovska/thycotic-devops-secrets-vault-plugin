package com.thycotic.secrets.jenkins;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;

// Borrowed from https://github.com/jenkinsci/credentials-binding-plugin/
public class VaultConsoleLogFilter extends ConsoleLogFilter
    implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String charsetName;
    private final List<String> valuesToMask;

    public VaultConsoleLogFilter(final String charsetName,
        List<String> valuesToMask) {
        this.charsetName = charsetName;
        this.valuesToMask = valuesToMask;
    }

    @Override
    public OutputStream decorateLogger(Run run,
        final OutputStream logger) throws IOException, InterruptedException {
        return new SecretPatterns.MaskingOutputStream(logger,
            () -> {
                List<String> values = valuesToMask.stream().filter(Objects::nonNull).collect(Collectors.toList());
                if (!values.isEmpty()) {
                    return SecretPatterns.getAggregateSecretPattern(values);
                } else {
                    return null;
                }
            }, charsetName);
    }
}
