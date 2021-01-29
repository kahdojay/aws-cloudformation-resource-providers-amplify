package software.amazon.amplify.domain;

import java.time.Duration;
import software.amazon.awssdk.services.amplify.AmplifyClient;
import software.amazon.awssdk.services.amplify.model.CreateDomainAssociationRequest;
import software.amazon.awssdk.services.amplify.model.CreateDomainAssociationResponse;
import software.amazon.awssdk.services.amplify.model.DomainAssociation;
import software.amazon.awssdk.services.amplify.model.DomainStatus;
import software.amazon.awssdk.services.amplify.model.GetDomainAssociationRequest;
import software.amazon.awssdk.services.amplify.model.GetDomainAssociationResponse;
import software.amazon.awssdk.services.amplify.model.NotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<AmplifyClient> proxyClient;

    @Mock
    AmplifyClient sdkClient;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(AmplifyClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
    }

    @AfterEach
    public void tear_down() {
        verify(sdkClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        stubProxyClient();
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .appId(APP_ID)
                .domainName(DOMAIN_NAME)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
                new CallbackContext(), proxyClient, logger);

        final ResourceModel expected = ResourceModel.builder()
                .appId(APP_ID)
                .arn(DOMAIN_ASSOCIATION_ARN)
                .domainName(DOMAIN_NAME)
                .domainStatus(DomainStatus.AVAILABLE.toString())
                .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(expected);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    private void stubProxyClient() {
        when(proxyClient.client().createDomainAssociation(any(CreateDomainAssociationRequest.class)))
                .thenReturn(CreateDomainAssociationResponse.builder()
                        .domainAssociation(DomainAssociation.builder()
                                .domainAssociationArn(DOMAIN_ASSOCIATION_ARN)
                                .domainName(DOMAIN_NAME)
                                .domainStatus(DomainStatus.CREATING)
                                .build())
                        .build());
        when(proxyClient.client().getDomainAssociation(any(GetDomainAssociationRequest.class)))
                .thenThrow(NotFoundException.builder().build())
                .thenReturn(GetDomainAssociationResponse.builder()
                        .domainAssociation(DomainAssociation.builder()
                                .domainAssociationArn(DOMAIN_ASSOCIATION_ARN)
                                .domainName(DOMAIN_NAME)
                                .domainStatus(DomainStatus.AVAILABLE)
                                .build())
                        .build());
    }
}