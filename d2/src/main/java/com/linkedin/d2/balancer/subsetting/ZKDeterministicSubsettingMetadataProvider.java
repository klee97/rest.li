package com.linkedin.d2.balancer.subsetting;

import com.linkedin.common.callback.FutureCallback;
import com.linkedin.d2.balancer.LoadBalancerState;
import com.linkedin.d2.balancer.properties.PropertyKeys;
import com.linkedin.d2.balancer.properties.UriProperties;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;


/**
 * Listens to the peer cluster in ZooKeeper and provides deterministic subsetting strategy with
 * the metadata needed
 */
public class ZKDeterministicSubsettingMetadataProvider implements DeterministicSubsettingMetadataProvider
{
  private final String _clusterName;
  private final URI _nodeUri;
  private final LoadBalancerState _loadBalancerState;
  private final long _timeout;
  private final TimeUnit _unit;

  public ZKDeterministicSubsettingMetadataProvider(String clusterName,
                                      URI nodeUri,
                                      LoadBalancerState loadBalancerState,
                                      long timeout,
                                      TimeUnit unit)
  {
    _clusterName = clusterName;
    _nodeUri = nodeUri;
    _loadBalancerState = loadBalancerState;
    _timeout = timeout;
    _unit = unit;
  }

  @Override
  public DeterministicSubsettingMetadata getSubsettingMetadata()
  {
    FutureCallback<DeterministicSubsettingMetadata> metadataFutureCallback = new FutureCallback<>();

    _loadBalancerState.listenToCluster(_clusterName, (type, name) ->
    {
      UriProperties uriProperties = _loadBalancerState.getUriProperties(_clusterName).getProperty();
      if (uriProperties != null)
      {
        // Sort the URIs so each client sees the same ordering
        List<URI> sortedUris = uriProperties.getPartitionDesc().keySet().stream()
            .filter(uri -> uri.getScheme().equals(_nodeUri.getScheme()))
            .sorted()
            .collect(Collectors.toList());

        int instanceId = sortedUris.indexOf(_nodeUri);

        if (instanceId >= 0)
        {
          metadataFutureCallback.onSuccess(new DeterministicSubsettingMetadata(instanceId, sortedUris.size()));
          return;
        }
      }
      metadataFutureCallback.onSuccess(null);
    });

    try
    {
      return metadataFutureCallback.get(_timeout, _unit);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return null;
    }
  }
}
