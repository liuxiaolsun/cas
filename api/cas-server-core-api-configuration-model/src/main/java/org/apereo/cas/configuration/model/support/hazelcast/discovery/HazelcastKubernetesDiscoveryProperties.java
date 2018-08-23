package org.apereo.cas.configuration.model.support.hazelcast.discovery;

import org.apereo.cas.configuration.support.RequiresModule;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * This is {@link HazelcastKubernetesDiscoveryProperties}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@RequiresModule(name = "cas-server-support-hazelcast-discovery-kubernetes")
@Getter
@Setter
public class HazelcastKubernetesDiscoveryProperties implements Serializable {
    private static final long serialVersionUID = 8590530159392472509L;

    /**
     * Defines the DNS service lookup domain. This is defined as something similar
     * to <tt>my-svc.my-namespace.svc.cluster.local</tt>.<br>
     * For more information please refer to the official documentation of the Kubernetes DNS addon,
     */
    private String serviceDns;

    /**
     * Defines the DNS service lookup timeout in seconds. Defaults to: 5 secs.
     */
    private int serviceDnsTimeout = -1;

    /**
     * Defines the service name of the POD to lookup through the Service Discovery REST API of Kubernetes.
     */
    private String serviceName;

    /**
     * Defines the service label to lookup through the Service Discovery REST API of Kubernetes.
     */
    private String serviceLabelName;

    /**
     * Defines the service label value to lookup through the Service Discovery REST API of Kubernetes.
     */
    private String serviceLabelValue;

    /**
     * Defines the namespace of the application POD through the Service Discovery REST API of Kubernetes.
     */
    private String namespace;

    /**
     *  Defines if not ready addresses should be evaluated to be discovered on startup.
     */
    private boolean resolveNotReadyAddresses;

    /**
     * Defines an alternative address for the kubernetes master. Defaults to: <tt>https://kubernetes.default.svc</tt>
     */
    private String kubernetesMaster;

    /**
     * Defines an oauth token for the kubernetes client to access the kubernetes REST API. Defaults to reading the
     * token from the auto-injected file at: <tt>/var/run/secrets/kubernetes.io/serviceaccount/token</tt>
     */
    private String apiToken;
}
