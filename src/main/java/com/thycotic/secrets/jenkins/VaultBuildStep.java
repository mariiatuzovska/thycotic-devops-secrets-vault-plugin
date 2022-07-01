package com.thycotic.secrets.jenkins;

import com.thycotic.secrets.vault.spring.Secret;
import com.thycotic.secrets.vault.spring.SecretsVault;
import com.thycotic.secrets.vault.spring.SecretsVaultFactoryBean;

import hudson.Extension;
import hudson.Launcher;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import jenkins.tasks.SimpleBuildStep;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

public class VaultBuildStep extends Builder implements SimpleBuildStep {
    private static final String CLIENT_ID_PROPERTY = "secrets_vault.client_id";
    private static final String CLIENT_SECRET_PROPERTY = "secrets_vault.client_secret";
    private static final String TENANT_PROPERTY = "secrets_vault.tenant";
    private static final String TLD_PROPERTY = "secrets_vault.tld";

    private List<VaultSecret> secrets;

    @DataBoundConstructor
    public VaultBuildStep(String tenant, final List<VaultSecret> secrets) {
        this.secrets = secrets;
    }

    @DataBoundSetter
    public void setSecrets(List<VaultSecret> secrets) {
        this.secrets = secrets;
    }

    public List<VaultSecret> getSecrets() {
        return secrets;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        secrets.forEach(vaultSecret -> {
            final ClientSecret clientSecret = ClientSecret.get(vaultSecret.getCredentialId(), null);
            final VaultConfiguration configuration = VaultConfiguration.get();
            final Map<String, Object> properties = new HashMap<>();

            final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
            properties.put(CLIENT_ID_PROPERTY, clientSecret.getClientId());
            properties.put(CLIENT_SECRET_PROPERTY, clientSecret.getSecret());
            properties.put(TENANT_PROPERTY, StringUtils.defaultIfBlank(vaultSecret.getTenant(), configuration.getTenant()));
            properties.put(TLD_PROPERTY, StringUtils.defaultIfBlank(vaultSecret.getTld(), configuration.getTld()));
            applicationContext.getEnvironment().getPropertySources()
                    .addLast(new MapPropertySource("properties", properties));

            // Register the factoryBean from secrets-java-sdk
            applicationContext.registerBean(SecretsVaultFactoryBean.class);
            applicationContext.refresh();

            // Fetch the secret
            final Secret secret = applicationContext.getBean(SecretsVault.class).getSecret(vaultSecret.getPath());
            vaultSecret.getMappings().forEach(mapping -> {
                // Prepend the the environment variable prefix
                listener.getLogger().println("mapping " + mapping.getEnvironmentVariable() + " " + secret.getData().get(mapping.getDataField()));
            });
            applicationContext.close();
        });
    }

    @Symbol("thycoticDevOpsSecretVault")
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public String getDisplayName() {
            return "Use Thycotic DevOps Secrets Vault Secrets For Build Step";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> t) {
            return true;
        }
    }
}