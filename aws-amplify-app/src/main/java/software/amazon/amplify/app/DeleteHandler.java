package software.amazon.amplify.app;

import software.amazon.amplify.common.utils.ClientWrapper;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.amplify.AmplifyClient;
import software.amazon.awssdk.services.amplify.model.DeleteAppResponse;
import software.amazon.awssdk.services.amplify.model.GetAppRequest;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<AmplifyClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        final ResourceModel model = request.getDesiredResourceState();
        logger.log("INFO: requesting with model: " + model);

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress ->
                proxy.initiate("AWS-Amplify-App::Delete", proxyClient, model, callbackContext)
                    .translateToServiceRequest(Translator::translateToDeleteRequest)
                    .makeServiceCall((deleteAppRequest, proxyInvocation) -> (DeleteAppResponse) ClientWrapper.execute(
                            proxy,
                            deleteAppRequest,
                            proxyInvocation.client()::deleteApp,
                            ResourceModel.TYPE_NAME,
                            model.getArn(),
                            logger
                    )).stabilize((awsRequest, awsResponse, client, resourceModel, context) -> isStabilized(proxy, proxyClient,
                        model, logger))
                    .progress()
            )
            .then(progress -> ProgressEvent.defaultSuccessHandler(null));
    }

    private boolean isStabilized(final AmazonWebServicesClientProxy proxy,
                                 final ProxyClient<AmplifyClient> proxyClient,
                                 final ResourceModel model,
                                 final Logger logger) {
        final String appInfo = String.format("%s - %s", model.getAppId(), model.getAppName());

        try {
            final GetAppRequest getAppRequest = GetAppRequest.builder()
                    .appId(model.getAppId())
                    .build();
            ClientWrapper.execute(
                    proxy,
                    getAppRequest,
                    proxyClient.client()::getApp,
                    ResourceModel.TYPE_NAME,
                    model.getArn(),
                    logger);
            logger.log(String.format("%s DELETE stabilization still in progress", appInfo));
            return false;
        } catch (final CfnNotFoundException e) {
            logger.log(String.format("%s DELETE stabilization complete", appInfo));
            return true;
        } catch (final AwsServiceException e) {
            logger.log(String.format("%s DELETE stabilization failed: %s", appInfo, e));
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getArn());
        }
    }
}
