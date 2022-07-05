package com.thycotic.secrets.jenkins;

import com.thycotic.secrets.vault.spring.Secret;
import com.thycotic.secrets.vault.spring.SecretsVault;
import com.thycotic.secrets.vault.spring.SecretsVaultFactoryBean;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import javax.annotation.Nonnull;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

public class VaultSecretStep extends Step implements Serializable {
    private String tenant;
    private String secretPath;
    private String environmentVariable;
    private String secretDataKey;
    private String credentialsId;
    private String tld;

    @DataBoundConstructor
    public VaultSecretStep() {

    }

    @DataBoundSetter
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getTenant() {
        return tenant;
    }

    @DataBoundSetter
    public void setSecretPath(String secretPath) {
        this.secretPath = secretPath;
    }

    public String getSecretPath() {
        return secretPath;
    }

    @DataBoundSetter
    public void setEnvironmentVariable(String environmentVariable) {
        this.environmentVariable = environmentVariable;
    }

    public String getEnvironmentVariable() {
        return environmentVariable;
    }

    @DataBoundSetter
    public void setSecretDataKey(String secretDataKey) {
        this.secretDataKey = secretDataKey;
    }

    public String getSecretDataKey() {
        return secretDataKey;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setTld(String tld) {
        this.tld = tld;
    }

    public String getTld() {
        return tld;
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        return new VaultSecretStepExecution(this, stepContext);

    }

    private static final class VaultSecretStepExecution extends StepExecution {
        private static final String CLIENT_ID_PROPERTY = "secrets_vault.client_id";
        private static final String CLIENT_SECRET_PROPERTY = "secrets_vault.client_secret";
        private static final String TENANT_PROPERTY = "secrets_vault.tenant";
        private static final String TLD_PROPERTY = "secrets_vault.tld";

        private static final long serialVersionUID = 1L;
        private transient final VaultSecretStep step;

        private VaultSecretStepExecution(VaultSecretStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() throws Exception {
            Run build = getContext().get(Run.class);
            TaskListener taskListener = getContext().get(TaskListener.class);

            final ClientSecret clientSecret = ClientSecret.get(step.getCredentialsId(), null);
            final VaultConfiguration configuration = VaultConfiguration.get();
            final Map<String, Object> properties = new HashMap<>();

            final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
            properties.put(CLIENT_ID_PROPERTY, clientSecret.getClientId());
            properties.put(CLIENT_SECRET_PROPERTY, clientSecret.getSecret());
            properties.put(TENANT_PROPERTY, StringUtils.defaultIfBlank(step.getTenant(), configuration.getTenant()));
            properties.put(TLD_PROPERTY, StringUtils.defaultIfBlank(step.getTld(), configuration.getTld()));
            applicationContext.getEnvironment().getPropertySources()
                    .addLast(new MapPropertySource("properties", properties));

            // Register the factoryBean from secrets-java-sdk
            applicationContext.registerBean(SecretsVaultFactoryBean.class);
            applicationContext.refresh();

            // Fetch the secret
            final Secret secret = applicationContext.getBean(SecretsVault.class).getSecret(step.getSecretPath());

            try {
                EnvVars environment = build.getEnvironment(taskListener);
                // Prepend the the environment variable prefix
                environment.override(StringUtils.trimToEmpty(configuration.getEnvironmentVariablePrefix() + step.getEnvironmentVariable()), secret.getData().get(step.getSecretDataKey()));
            } catch (Exception e) {
                getContext().onFailure(e);
            }
            applicationContext.close();
            return true;
        }

        @Override
        public void stop(@Nonnull Throwable throwable) throws Exception {
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet<Class<?>>() {{
                add(Run.class);
                add(TaskListener.class);
            }};
        }

        @Override
        public String getFunctionName() {
            return "vaultSecretStep";
        }
    }
}