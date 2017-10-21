package com.cognodyne.dw.cdi.weld;

import org.jboss.weld.bootstrap.api.Service;

public interface DeployableWeldService {
    public Class<Service> getType();

    public Service getService();
}
