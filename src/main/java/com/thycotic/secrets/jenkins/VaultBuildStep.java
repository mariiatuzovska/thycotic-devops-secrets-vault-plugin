package com.thycotic.secrets.jenkins;

import com.thycotic.secrets.vault.spring.Secret;
import com.thycotic.secrets.vault.spring.SecretsVault;
import com.thycotic.secrets.vault.spring.SecretsVaultFactoryBean;

import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Launcher;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import jenkins.tasks.SimpleBuildStep;
// import jenkins.tasks.SimpleBuildWrapper;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

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
    private ClientSecret clientCredentials;
    private String tenant;
    
    @DataBoundConstructor
    public VaultBuildStep(String tenant, List<VaultSecret> secrets, ClientSecret clientCredentials) {
        this.tenant = tenant;
        this.secrets = secrets;
        this.clientCredentials = clientCredentials;
    }

    @DataBoundSetter
    public void setSecrets(List<VaultSecret> secrets) {
        this.secrets = secrets;
    }

    public List<VaultSecret> getSecrets() {
        return secrets;
    }

    @DataBoundSetter
    public void setClientCredentials(ClientSecret clientCredentials) {
        this.clientCredentials = clientCredentials;
    }

    public ClientSecret getClientCredentials() {
        return clientCredentials;
    }

    @DataBoundSetter
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getTenant() {
        return tenant;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        Map<String, String> envs = run instanceof AbstractBuild ? ((AbstractBuild<?,?>) run).getBuildVariables() : Collections.emptyMap();
        EnvVars env = run.getEnvironment(listener);
        env.overrideAll(envs);

        final Map<String, Object> properties = new HashMap<>();
        assert (clientCredentials != null);

        secrets.forEach(vaultSecret -> {
            final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
            // create a new Spring ApplicationContext using a Map as the PropertySource
            properties.put(CLIENT_ID_PROPERTY, clientCredentials.getClientId());
            properties.put(CLIENT_SECRET_PROPERTY, clientCredentials.getSecret());
            properties.put(TENANT_PROPERTY, StringUtils.defaultIfBlank(vaultSecret.getTenant(), tenant));
            properties.put(TLD_PROPERTY, vaultSecret.getTld());
            applicationContext.getEnvironment().getPropertySources()
                    .addLast(new MapPropertySource("properties", properties));
            // Register the factoryBean from secrets-java-sdk
            applicationContext.registerBean(SecretsVaultFactoryBean.class);
            applicationContext.refresh();
            // Fetch the secret
            final Secret secret = applicationContext.getBean(SecretsVault.class).getSecret(vaultSecret.getPath());
            // Add each of the dataFields to the environment         
            vaultSecret.getMappings().forEach(mapping -> {
                // Prepend the the environment variable prefix
                env.override(StringUtils.trimToEmpty(
                                ExtensionList.lookupSingleton(VaultConfiguration.class).getEnvironmentVariablePrefix())
                                + mapping.getEnvironmentVariable(), secret.getData().get(mapping.getDataField()));
            });
            applicationContext.close();
        });
    }

    @Symbol("thycoticDevOpsSecretVault")
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Use Thycotic DevOps Secrets Vault Secrets for declaretive pipeline";
        }
    }
}