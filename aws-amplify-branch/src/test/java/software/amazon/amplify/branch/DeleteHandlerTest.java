package software.amazon.amplify.branch;

import java.time.Duration;
import software.amazon.awssdk.services.amplify.AmplifyClient;
import software.amazon.awssdk.services.amplify.model.Branch;
import software.amazon.awssdk.services.amplify.model.DeleteBranchRequest;
import software.amazon.awssdk.services.amplify.model.DeleteBranchResponse;
import software.amazon.awssdk.services.amplify.model.GetBranchRequest;
import software.amazon.awssdk.services.amplify.model.GetBranchResponse;
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
public class DeleteHandlerTest extends AbstractTestBase {

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
        stubProxyClient();
    }

    @AfterEach
    public void tear_down() {
        verify(sdkClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
                .appId(APP_ID)
                .branchName(BRANCH_NAME)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
                new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    private void stubProxyClient() {
        when(proxyClient.client().deleteBranch(any(DeleteBranchRequest.class)))
                .thenReturn(DeleteBranchResponse.builder().build());
        when(proxyClient.client().getBranch(any(GetBranchRequest.class)))
                .thenReturn(GetBranchResponse.builder()
                        .branch(Branch.builder()
                                .branchName(BRANCH_NAME)
                                .build())
                        .build())
                .thenThrow(NotFoundException.builder().message("Resource Not Found").build());
    }
}
