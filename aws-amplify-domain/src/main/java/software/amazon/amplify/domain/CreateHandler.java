package software.amazon.amplify.domain;

import org.apache.commons.lang3.ObjectUtils;
import software.amazon.amplify.common.utils.ClientWrapper;
import software.amazon.awssdk.services.amplify.AmplifyClient;
import software.amazon.awssdk.services.amplify.model.CreateDomainAssociationResponse;
import software.amazon.awssdk.services.amplify.model.DomainAssociation;
import software.amazon.awssdk.services.amplify.model.DomainStatus;
import software.amazon.awssdk.services.amplify.model.GetDomainAssociationRequest;
import software.amazon.awssdk.services.amplify.model.GetDomainAssociationResponse;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;

public class CreateHandler extends BaseHandlerStd {
    private Logger logger;
    // Custom domain creation involves CloudFront distribution creation which takes additional stabilization time
    private static final Duration HANDLER_CALLBACK_DELAY_SECONDS = Duration.ofMinutes(3L);
    private static final Duration HANDLER_TIMEOUT_MINUTES = Duration.ofMinutes(10L);
    private static final Constant BACKOFF_STRATEGY = Constant.of().timeout(HANDLER_TIMEOUT_MINUTES).delay(HANDLER_CALLBACK_DELAY_SECONDS).build();

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<AmplifyClient> proxyClient,
        final Logger logger) {

        this.logger = logger;
        final ResourceModel model = request.getDesiredResourceState();
        logger.log("INFO: requesting with model: " + model);

        // Make sure the user isn't trying to assign values to read-only properties
        String disallowedVal = checkReadOnlyProperties(model);
        if (disallowedVal != null) {
            throw new CfnInvalidRequestException(String.format("Attempted to provide value to a read-only property: %s", disallowedVal));
        }

        return ProgressEvent.progress(model, callbackContext)
                .then(progress ->
                    proxy.initiate("AWS-Amplify-Domain::Create", proxyClient,progress.getResourceModel(),
                            progress.getCallbackContext())
                        .translateToServiceRequest(Translator::translateToCreateRequest)
                        .backoffDelay(BACKOFF_STRATEGY)
                        .makeServiceCall((createDomainAssociationRequest, proxyInvocation) -> {
                            checkIfResourceExists(model, proxyClient, logger);
                            CreateDomainAssociationResponse createDomainAssociationResponse = (CreateDomainAssociationResponse) ClientWrapper.execute(
                                    proxy,
                                    createDomainAssociationRequest,
                                    proxyInvocation.client()::createDomainAssociation,
                                    ResourceModel.TYPE_NAME,
                                    model.getArn(),
                                    logger
                            );
                            setResourceModelId(model, createDomainAssociationResponse.domainAssociation());
                            return createDomainAssociationResponse;
                        })
                        .stabilize((awsRequest, awsResponse, client, resourceModel, context) -> isStabilized(proxy, proxyClient,
                                model, logger))
                        .progress())
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private String checkReadOnlyProperties(final ResourceModel model) {
        return ObjectUtils.firstNonNull(model.getDomainStatus(), model.getStatusReason(), model.getCertificateRecord());
    }

    private boolean isStabilized(final AmazonWebServicesClientProxy proxy,
                                final ProxyClient<AmplifyClient> proxyClient,
                                final ResourceModel model,
                                final Logger logger) {
        final GetDomainAssociationRequest getDomainAssociationRequest = GetDomainAssociationRequest.builder()
                .appId(model.getAppId())
                .domainName(model.getDomainName())
                .build();
        final GetDomainAssociationResponse getDomainAssociationResponse = (GetDomainAssociationResponse) ClientWrapper.execute(
                proxy,
                getDomainAssociationRequest,
                proxyClient.client()::getDomainAssociation,
                ResourceModel.TYPE_NAME,
                model.getArn(),
                logger);

        final String domainInfo = String.format("%s - %s", model.getAppId(), model.getDomainName());
        final DomainAssociation domainAssociation = getDomainAssociationResponse.domainAssociation();
        final DomainStatus domainStatus = domainAssociation.domainStatus();

        switch (domainStatus) {
            case CREATING:
            case REQUESTING_CERTIFICATE:
            case IN_PROGRESS:
                logger.log(String.format("%s CREATE stabilization domainStatus: %s", domainInfo, domainStatus));
                return false;
            case PENDING_VERIFICATION:
            case PENDING_DEPLOYMENT:
            case AVAILABLE:
            case UPDATING:
                logger.log(String.format("%s CREATE has been stabilized.", domainInfo));
                Translator.translateFromCreateOrUpdateResponse(model, getDomainAssociationResponse.domainAssociation());
                return true;
            case FAILED:
                final String FAILURE_REASON = domainAssociation.statusReason();
                logger.log(String.format("%s CREATE stabilization failed: %s", domainInfo, FAILURE_REASON));
                throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getArn(), new CfnGeneralServiceException(FAILURE_REASON));
            default:
                logger.log(String.format("%s CREATE stabilization failed thrown due to invalid status: %s", domainInfo, domainStatus));
                throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getArn());
        }
    }
}
