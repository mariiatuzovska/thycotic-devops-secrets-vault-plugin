import com.thycotic.secrets.vault.spring.SecretsVaultFactoryBean;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Launcher;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import jenkins.task.SimpleBuildStep;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class VaultBuildStep extends Builder implements SimpleBuildStep {
    private static final String CLIENT_ID_PROPERTY = "secrets_vault.client_id";
    private static final String CLIENT_SECRET_PROPERTY = "secrets_vault.client_secret";
    private static final String TENANT_PROPERTY = "secrets_vault.tenant";
    private static final String TLD_PROPERTY = "secrets_vault.tld";

    private List<VaultSecret> secrets;
    private ClientSecret clientCredentials;
    private String tenant;
    
    @DataBoundConstructor
    public VaultBuildStep(String tenant, List<VaultSecret> secrets, List<ClientSecret> clientCredentials) {
        this.tenant = tenant;
        this.secrets = secrets;
        this.clientCredentials = clientCredentials;
    }

    @DataBoundSetter
    public void setSecrets(final List<VaultSecret> secrets) {
        this.secrets = secrets;
    }

    @DataBoundSetter
    public void setClientCredentials(final ClientSecret clientCredentials) {
        this.clientCredentials = clientCredentials;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
        final Map<String, Object> properties = new HashMap<>();
        assert (clientCredentials != null);

        secrets.forEach(vaultSecret -> {
            final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
            // create a new Spring ApplicationContext using a Map as the PropertySource
            properties.put(CLIENT_ID_PROPERTY, clientCredentials.getClientId());
            properties.put(CLIENT_SECRET_PROPERTY, clientSecret.getSecret());
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
                context.env(StringUtils.trimToEmpty(
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